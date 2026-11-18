"""Test assembly: eligibility, random item draw per candidate, question and option shuffling."""
from __future__ import annotations

import random
import secrets
from collections import defaultdict
from datetime import timedelta

from sqlalchemy import func, select
from sqlalchemy.orm import Session, selectinload

from ..models import Attempt, AttemptItem, Employee, Item, Test, utcnow
from .framework import employee_profile


class AssemblyError(Exception):
    pass


def is_eligible(test: Test, emp: Employee) -> bool:
    if test.status != "published" or not emp.active:
        return False
    now = utcnow()
    if test.start_at and now < test.start_at:
        return False
    if test.end_at and now > test.end_at:
        return False
    t = test.target or {}
    if t.get("emp_ids") and emp.emp_id in t["emp_ids"]:
        return True
    checks = [
        ("role_families", emp.role_family_code),
        ("grades", emp.grade_code),
        ("departments", emp.department),
        ("locations", emp.location),
    ]
    for key, value in checks:
        allowed = t.get(key) or []
        if allowed and value not in allowed:
            return False
    if t.get("emp_ids") and not any(t.get(k) for k, _ in checks):
        return False  # test restricted to a named list only
    return True


def attempts_used(db: Session, test: Test, emp: Employee) -> int:
    return db.scalar(
        select(func.count(Attempt.id)).where(
            Attempt.test_id == test.id, Attempt.employee_id == emp.id, Attempt.status != "void"
        )
    ) or 0


def can_start(db: Session, test: Test, emp: Employee) -> tuple[bool, str]:
    if not is_eligible(test, emp):
        return False, "not_eligible"
    if test.max_attempts and attempts_used(db, test, emp) >= test.max_attempts:
        return False, "no_attempts_left"
    if not emp.role_family_code:
        return False, "no_role_family"
    return True, ""


def _pool_filter(test: Test):
    if test.is_practice:
        return Item.pool.in_(["practice", "both"])
    return Item.pool.in_(["exam", "both"])


def _plan(db: Session, test: Test, emp: Employee) -> list[dict]:
    """How many items to draw per competency (and optionally Bloom level)."""
    if test.blueprint and test.blueprint.rules:
        return [r for r in test.blueprint.rules if r.get("count", 0) > 0]
    plan = []
    for p in employee_profile(db, emp):
        if test.competency_filter and p.competency.code not in test.competency_filter:
            continue
        if test.category_filter and p.competency.category not in test.category_filter:
            continue
        per = test.items_per_competency
        # more items where more depth is required (level 4-5), fewer at level 1
        n = max(1, per + (1 if p.required_level >= 4 else 0) - (1 if p.required_level <= 1 else 0))
        plan.append({"competency": p.competency.code, "bloom": None, "count": n,
                     "target_level": p.required_level})
    return plan


def assemble(db: Session, test: Test, emp: Employee, seed: int | None = None) -> tuple[list[Item], dict[int, dict], int]:
    """Returns (ordered items, option orders by item id, seed). Deterministic for a given seed."""
    seed = seed if seed is not None else secrets.randbelow(2**31 - 1)
    rng = random.Random(seed)
    fam = emp.role_family_code
    candidates = db.scalars(
        select(Item)
        .options(selectinload(Item.options))
        .where(Item.status == "approved", _pool_filter(test))
    ).all()
    candidates = [i for i in candidates if "ALL" in (i.role_families or []) or fam in (i.role_families or [])]
    by_comp: dict[str, list[Item]] = defaultdict(list)
    for it in candidates:
        by_comp[it.competency_code].append(it)
    passage_members: dict[int, list[Item]] = defaultdict(list)
    for it in candidates:
        if it.passage_id:
            passage_members[it.passage_id].append(it)

    chosen: dict[int, Item] = {}
    for rule in _plan(db, test, emp):
        pool = [i for i in by_comp.get(rule["competency"], []) if i.id not in chosen]
        if rule.get("bloom"):
            pool = [i for i in pool if i.bloom == rule["bloom"]]
        tl = rule.get("target_level")
        # Weighted draw: prefer items near the required level and, for official tests, less exposed items
        def weight(i: Item) -> float:
            w = 1.0
            if tl:
                w /= 1 + abs((i.target_level or 3) - tl)
            if not test.is_practice:
                w /= 1 + (i.exposure_count or 0) / 10
            return w
        need = rule["count"]
        while need > 0 and pool:
            pick = rng.choices(pool, weights=[weight(i) for i in pool], k=1)[0]
            group = sorted(passage_members[pick.passage_id], key=lambda x: x.passage_order) if pick.passage_id else [pick]
            for g in group:
                if g.id not in chosen:
                    chosen[g.id] = g
            need -= len([g for g in group if g.competency_code == rule["competency"]]) or 1
            pool = [i for i in pool if i.id not in chosen]
        if len(chosen) >= test.max_items:
            break

    if not chosen:
        raise AssemblyError("no_items")

    # Units keep passage sets together
    units: list[list[Item]] = []
    seen_passages: set[int] = set()
    for it in chosen.values():
        if it.passage_id:
            if it.passage_id in seen_passages:
                continue
            seen_passages.add(it.passage_id)
            units.append(sorted([c for c in chosen.values() if c.passage_id == it.passage_id],
                                key=lambda x: x.passage_order))
        else:
            units.append([it])
    if test.shuffle_questions:
        rng.shuffle(units)
    else:
        units.sort(key=lambda u: (u[0].competency_code, u[0].code))
    ordered = [i for u in units for i in u][: max(test.max_items, 1)]

    orders: dict[int, dict] = {}
    for it in ordered:
        ids = [o.id for o in it.options]
        if it.qtype == "sequence":
            rng.shuffle(ids)  # always shuffled, otherwise the answer would be shown
            # guarantee it is not already in the correct order
            correct = [o.id for o in sorted(it.options, key=lambda o: o.seq_position or 0)]
            if len(ids) > 1 and ids == correct:
                ids = ids[1:] + ids[:1]
            orders[it.id] = {"options": ids}
        elif it.qtype == "match":
            left = ids[:]
            right = ids[:]
            if test.shuffle_options and it.shuffle_options:
                rng.shuffle(left)
            rng.shuffle(right)
            orders[it.id] = {"options": left, "right": right}
        else:
            if test.shuffle_options and it.shuffle_options and it.qtype != "truefalse":
                rng.shuffle(ids)
            orders[it.id] = {"options": ids}
    return ordered, orders, seed


def start_attempt(db: Session, test: Test, emp: Employee, lang: str, client_info: str = "") -> Attempt:
    ok, reason = can_start(db, test, emp)
    if not ok:
        raise AssemblyError(reason)
    items, orders, seed = assemble(db, test, emp)
    duration = test.duration_min * (100 + (emp.extra_time_pct or 0)) / 100
    now = utcnow()
    att = Attempt(test_id=test.id, employee_id=emp.id, kind=test.kind, seed=seed, lang=lang,
                  started_at=now, deadline_at=now + timedelta(minutes=duration),
                  client_info=client_info[:300])
    db.add(att)
    db.flush()
    for pos, it in enumerate(items, start=1):
        db.add(AttemptItem(attempt_id=att.id, item_id=it.id, position=pos,
                           option_order=orders[it.id], max_score=it.marks or 1.0))
        if not test.is_practice:
            it.exposure_count = (it.exposure_count or 0) + 1
    db.commit()
    db.refresh(att)
    return att


def active_attempt(db: Session, test: Test, emp: Employee) -> Attempt | None:
    return db.scalar(
        select(Attempt).where(Attempt.test_id == test.id, Attempt.employee_id == emp.id,
                              Attempt.status == "in_progress").order_by(Attempt.id.desc())
    )

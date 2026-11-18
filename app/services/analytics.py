"""Aggregates for dashboards and psychometric item analysis."""
from __future__ import annotations

import math
import statistics
from collections import Counter, defaultdict

from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from ..models import Attempt, AttemptItem, CompetencyScore, Employee, Item
from .framework import FrameworkCache
from .scoring import Profile, build_profile


def profiles_for(db: Session, emps: list[Employee]) -> dict[int, Profile]:
    if not emps:
        return {}
    ctx = FrameworkCache(db)
    by_emp: dict[int, list[CompetencyScore]] = defaultdict(list)
    ids = [e.id for e in emps]
    for i in range(0, len(ids), 500):
        for s in db.scalars(select(CompetencyScore).where(CompetencyScore.employee_id.in_(ids[i:i + 500]))
                            .order_by(CompetencyScore.recorded_at, CompetencyScore.id)).all():
            by_emp[s.employee_id].append(s)
    return {e.id: build_profile(db, e, ctx=ctx, scores=by_emp.get(e.id, [])) for e in emps}


def quartiles(vals: list[float]) -> tuple[float | None, float | None, float | None]:
    if not vals:
        return None, None, None
    if len(vals) == 1:
        return vals[0], vals[0], vals[0]
    q = statistics.quantiles(sorted(vals), n=4, method="inclusive")
    return round(q[0], 1), round(statistics.median(vals), 1), round(q[2], 1)


def summarise(emps: list[Employee], profiles: dict[int, Profile]) -> dict:
    """Headline numbers for a group of employees."""
    records = meets = gates = 0
    rag = Counter()
    gap_by_comp: Counter = Counter()
    comp_names: dict[str, tuple[str, str]] = {}
    closure = []
    assessed_people = 0
    for e in emps:
        p = profiles.get(e.id)
        if not p:
            continue
        rag[p.readiness] += 1
        if p.assessed:
            assessed_people += 1
        if p.gap_closure is not None:
            closure.append(p.gap_closure)
        for r in p.rows:
            if r.latest is None:
                continue
            records += 1
            meets += 1 if r.gap == 0 else 0
            gates += 1 if r.status == "Safety gate" else 0
            if r.gap > 0:
                gap_by_comp[r.code] += 1
                comp_names[r.code] = (r.name_en, r.name_mr)
    top = [{"code": c, "name_en": comp_names[c][0], "name_mr": comp_names[c][1], "count": n}
           for c, n in gap_by_comp.most_common(5)]
    return {
        "people": len(emps), "assessed": assessed_people,
        "coverage": round(100 * assessed_people / len(emps), 1) if emps else 0,
        "records": records, "meets_pct": round(100 * meets / records, 1) if records else None,
        "safety_gates": gates, "rag": {k: rag.get(k, 0) for k in ("Red", "Amber", "Green", "None")},
        "top_gaps": top, "gap_closure": round(sum(closure) / len(closure), 1) if closure else None,
    }


def competency_benchmarks(emps: list[Employee], profiles: dict[int, Profile]) -> list[dict]:
    """Per competency: P25/median/P75 latest score, % meeting requirement, avg baseline and gain."""
    vals: dict[str, list[float]] = defaultdict(list)
    base: dict[str, list[float]] = defaultdict(list)
    meets: Counter = Counter()
    names = {}
    req: dict[str, list[int]] = defaultdict(list)
    for e in emps:
        p = profiles.get(e.id)
        if not p:
            continue
        for r in p.rows:
            names[r.code] = (r.name_en, r.name_mr, r.category, r.safety)
            req[r.code].append(r.required)
            if r.latest is None:
                continue
            vals[r.code].append(r.latest)
            if r.baseline is not None:
                base[r.code].append(r.baseline)
            meets[r.code] += 1 if r.gap == 0 else 0
    out = []
    for code in sorted(names):
        v = vals.get(code, [])
        p25, med, p75 = quartiles(v)
        b = base.get(code, [])
        out.append({
            "code": code, "name_en": names[code][0], "name_mr": names[code][1],
            "category": names[code][2], "safety": names[code][3],
            "n": len(v), "p25": p25, "median": med, "p75": p75,
            "meets_pct": round(100 * meets[code] / len(v), 1) if v else None,
            "baseline": round(sum(b) / len(b), 1) if b else None,
            "latest": round(sum(v) / len(v), 1) if v else None,
            "required_avg": round(sum(req[code]) / len(req[code]), 1) if req[code] else None,
        })
    return out


def score_distribution(emps: list[Employee], profiles: dict[int, Profile]) -> list[int]:
    """Histogram of mean latest score per employee in 10-point bins."""
    bins = [0] * 10
    for e in emps:
        p = profiles.get(e.id)
        if not p:
            continue
        v = [r.latest for r in p.rows if r.latest is not None]
        if v:
            bins[min(9, int((sum(v) / len(v)) // 10))] += 1
    return bins


# ------------------------------------------------------------------ item analysis
def _point_biserial(xs: list[float], ys: list[float]) -> float | None:
    n = len(xs)
    if n < 5:
        return None
    mx, my = sum(xs) / n, sum(ys) / n
    sx = math.sqrt(sum((x - mx) ** 2 for x in xs) / n)
    sy = math.sqrt(sum((y - my) ** 2 for y in ys) / n)
    if sx == 0 or sy == 0:
        return None
    return round(sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / (n * sx * sy), 3)


def run_item_analysis(db: Session) -> dict:
    """Classical test theory on official, scored attempts: p-value and corrected point-biserial."""
    atts = db.scalars(select(Attempt).options(selectinload(Attempt.items))
                      .where(Attempt.kind != "PRACTICE", Attempt.status == "scored")).all()
    per_item: dict[int, list[tuple[float, float]]] = defaultdict(list)
    for a in atts:
        total = sum((ai.final_score or 0) for ai in a.items)
        mx = sum(ai.max_score for ai in a.items) or 1
        for ai in a.items:
            s = max(0.0, (ai.final_score or 0) / (ai.max_score or 1))
            rest = (total - (ai.final_score or 0)) / max(mx - ai.max_score, 1)
            per_item[ai.item_id].append((s, rest))
    updated = flagged = 0
    for item_id, pairs in per_item.items():
        it = db.get(Item, item_id)
        if not it:
            continue
        xs = [p[0] for p in pairs]
        ys = [p[1] for p in pairs]
        it.stats_n = len(pairs)
        it.p_value = round(sum(xs) / len(xs), 3)
        it.point_biserial = _point_biserial(xs, ys)
        updated += 1
        if item_flag(it):
            flagged += 1
    db.commit()
    return {"attempts": len(atts), "items": updated, "flagged": flagged}


def item_flag(it: Item) -> str:
    if it.stats_n < 20 or it.p_value is None:
        return ""
    if it.p_value < 0.30:
        return "too hard"
    if it.p_value > 0.85:
        return "too easy"
    if it.point_biserial is not None and it.point_biserial < 0.20:
        return "weak discrimination"
    return ""


def kr20(db: Session, test_id: int) -> float | None:
    """Reliability (KR-20 / alpha) of a test from its scored attempts."""
    atts = db.scalars(select(Attempt).options(selectinload(Attempt.items))
                      .where(Attempt.test_id == test_id, Attempt.status == "scored")).all()
    if len(atts) < 10:
        return None
    by_item: dict[int, list[float]] = defaultdict(list)
    totals = []
    for a in atts:
        totals.append(sum(max(0.0, (ai.final_score or 0) / (ai.max_score or 1)) for ai in a.items))
        for ai in a.items:
            by_item[ai.item_id].append(max(0.0, (ai.final_score or 0) / (ai.max_score or 1)))
    k = len(by_item)
    var_t = statistics.pvariance(totals) if len(totals) > 1 else 0
    if k < 2 or var_t == 0:
        return None
    sum_var = sum(statistics.pvariance(v) for v in by_item.values() if len(v) > 1)
    return round((k / (k - 1)) * (1 - sum_var / var_t), 3)


def bank_coverage(db: Session) -> list[dict]:
    rows = db.execute(select(Item.competency_code, Item.pool, Item.status)).all()
    cov: dict[str, Counter] = defaultdict(Counter)
    for code, pool, status in rows:
        if status == "approved":
            cov[code][pool] += 1
        cov[code][status] += 1
    return [{"code": c, **dict(v)} for c, v in sorted(cov.items())]


def manual_queue(db: Session) -> list[AttemptItem]:
    return db.scalars(select(AttemptItem).options(selectinload(AttemptItem.item), selectinload(AttemptItem.attempt))
                      .join(Attempt).where(AttemptItem.needs_manual.is_(True), AttemptItem.manual_score.is_(None),
                                           Attempt.status == "pending_manual")
                      .order_by(AttemptItem.id)).all()

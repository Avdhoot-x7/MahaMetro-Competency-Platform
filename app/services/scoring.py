"""Item scoring, attempt finalisation and competency profile calculation."""
from __future__ import annotations

import re
import unicodedata
from collections import defaultdict
from dataclasses import dataclass, field

from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from ..models import (
    AUTO_SCORED, CYCLE_ORDER, Attempt, AttemptItem, Competency, CompetencyScore, Employee, Item,
    utcnow,
)
from . import framework as fw


# ------------------------------------------------------------------ item level
def _norm(s: str) -> str:
    s = unicodedata.normalize("NFC", str(s or "")).strip().lower()
    s = re.sub(r"[\s​‌‍]+", " ", s)
    return s.strip(" .,;:!?।")


def score_item(item: Item, response: dict | None) -> tuple[float | None, bool | None, bool]:
    """Returns (score, is_correct, needs_manual). Score in marks (can be negative)."""
    marks = item.marks or 1.0
    neg = item.negative_marks or 0.0
    q = item.qtype
    if q in ("short_text", "audio_response"):
        has = bool(response and (response.get("text") or response.get("media_id")))
        return (None, None, True) if has else (0.0, False, False)
    if not response:
        return 0.0, False, False
    opts = {o.id: o for o in item.options}

    if q in ("single", "truefalse"):
        choice = response.get("choice")
        if choice is None:
            return 0.0, False, False
        ok = bool(opts.get(int(choice)) and opts[int(choice)].is_correct)
        return (marks if ok else -neg), ok, False

    if q == "sjt":
        choice = response.get("choice")
        if choice is None or int(choice) not in opts:
            return 0.0, False, False
        o = opts[int(choice)]
        w = o.weight if o.weight is not None else (1.0 if o.is_correct else 0.0)
        return round(marks * w, 3), w >= 0.999, False

    if q == "multi":
        chosen = {int(c) for c in (response.get("choices") or []) if int(c) in opts}
        if not chosen:
            return 0.0, False, False
        correct = {i for i, o in opts.items() if o.is_correct}
        if chosen == correct:
            return marks, True, False
        if not item.partial_credit:
            return -neg, False, False
        # partial credit: +1/k per right choice, -1/k per wrong choice, floored at 0
        k = max(len(correct), 1)
        raw = (len(chosen & correct) - len(chosen - correct)) / k
        return round(max(0.0, raw) * marks, 3), False, False

    if q == "sequence":
        order = [int(x) for x in (response.get("order") or [])]
        correct = [o.id for o in sorted(item.options, key=lambda o: o.seq_position or 0)]
        if not order:
            return 0.0, False, False
        if order == correct:
            return marks, True, False
        if not item.partial_credit:
            return 0.0, False, False
        hits = sum(1 for a, b in zip(order, correct) if a == b)
        return round(marks * hits / len(correct), 3), False, False

    if q == "match":
        pairs = {int(k): int(v) for k, v in (response.get("pairs") or {}).items() if v not in (None, "")}
        if not pairs:
            return 0.0, False, False
        hits = sum(1 for k, v in pairs.items() if k == v)
        n = len(opts)
        if hits == n:
            return marks, True, False
        if not item.partial_credit:
            return 0.0, False, False
        return round(marks * hits / n, 3), False, False

    if q == "numeric":
        try:
            v = float(str(response.get("value", "")).replace(",", "").strip())
        except ValueError:
            return 0.0, False, False
        if item.answer_numeric is None:
            return None, None, True
        ok = abs(v - item.answer_numeric) <= (item.numeric_tolerance or 0) + 1e-9
        return (marks if ok else -neg), ok, False

    if q == "fill_blank":
        text = _norm(response.get("text", ""))
        if not text:
            return 0.0, False, False
        accepted = {_norm(a) for a in (item.accepted_answers or [])}
        ok = text in accepted
        return (marks if ok else -neg), ok, False

    return None, None, True


def method_for(item: Item) -> str:
    return "sjt" if item.qtype == "sjt" else "knowledge"


# ------------------------------------------------------------------ attempt level
def finalize_attempt(db: Session, att: Attempt, auto: bool = False) -> Attempt:
    """Score every response, then write competency scores for official cycles."""
    att = db.scalar(
        select(Attempt).options(selectinload(Attempt.items).selectinload(AttemptItem.item).selectinload(Item.options))
        .where(Attempt.id == att.id)
    )
    if att.status not in ("in_progress",):
        return att
    pending = False
    for ai in att.items:
        s, ok, manual = score_item(ai.item, ai.response)
        ai.auto_score = s
        ai.is_correct = ok
        ai.needs_manual = manual
        ai.final_score = s if not manual else None
        if manual and att.kind == "PRACTICE":
            # practice: written/spoken answers are self-checked against the rubric, not queued for the Cell
            ai.max_score = 0.0
            continue
        pending = pending or manual
    att.submitted_at = utcnow()
    if auto:
        att.flag_reason = (att.flag_reason + " auto-submitted at time-out").strip()
    max_tabs = (att.test.proctoring or {}).get("max_tab_switches")
    flagged = (not att.test.is_practice) and max_tabs is not None and att.tab_switches > int(max_tabs)
    if flagged:
        att.flag_reason = (att.flag_reason + f" tab switches {att.tab_switches} > {max_tabs}").strip()
    if not att.test.is_practice:
        from .timing import analyse  # rapid-guessing check (answers too fast to have been read)
        tm = analyse(db, att)
        if tm["low_effort"]:
            flagged = True
            att.flag_reason = (att.flag_reason + f" rapid answers {tm['rapid']} of {tm['answered']}"
                               f" (response-time effort {tm['rte']}%)").strip()
    att.status = "pending_manual" if pending else ("flagged" if flagged else "scored")
    _summarise(att)
    db.commit()
    if att.status == "scored":
        publish_scores(db, att)
    return att


def _summarise(att: Attempt) -> None:
    per: dict[str, dict] = defaultdict(lambda: {"score": 0.0, "max": 0.0, "n": 0, "correct": 0,
                                                "knowledge": [0.0, 0.0], "sjt": [0.0, 0.0]})
    total = mx = 0.0
    for ai in att.items:
        if ai.max_score == 0:
            continue
        c = per[ai.item.competency_code]
        s = max(ai.final_score or 0.0, 0.0) if ai.final_score is not None else 0.0
        raw = ai.final_score or 0.0
        c["score"] += raw
        c["max"] += ai.max_score
        c["n"] += 1
        c["correct"] += 1 if ai.is_correct else 0
        m = method_for(ai.item)
        c[m][0] += raw
        c[m][1] += ai.max_score
        total += raw
        mx += ai.max_score
        _ = s
    summary = {}
    for code, c in per.items():
        pct = max(0.0, 100.0 * c["score"] / c["max"]) if c["max"] else 0.0
        summary[code] = {
            "percent": round(pct, 1), "n": c["n"], "correct": c["correct"],
            "knowledge": round(max(0.0, 100 * c["knowledge"][0] / c["knowledge"][1]), 1) if c["knowledge"][1] else None,
            "sjt": round(max(0.0, 100 * c["sjt"][0] / c["sjt"][1]), 1) if c["sjt"][1] else None,
        }
    att.summary = summary
    att.score = round(total, 2)
    att.max_score = round(mx, 2)
    att.percent = round(max(0.0, 100 * total / mx), 1) if mx else 0.0


def complete_manual_scoring(db: Session, att: Attempt) -> None:
    if any(ai.needs_manual and ai.manual_score is None for ai in att.items):
        return
    for ai in att.items:
        if ai.needs_manual:
            ai.final_score = ai.manual_score
            ai.is_correct = ai.manual_score is not None and ai.manual_score >= ai.max_score * 0.999
    _summarise(att)
    flagged = bool(att.flag_reason) and ("tab switches" in att.flag_reason or "rapid answers" in att.flag_reason) \
        and "review: accept" not in att.flag_reason
    att.status = "flagged" if flagged else "scored"
    db.commit()
    if att.status == "scored":
        publish_scores(db, att)


def publish_scores(db: Session, att: Attempt) -> None:
    """Official tests write knowledge/SJT scores per competency; practice tests never do."""
    if att.kind == "PRACTICE":
        return
    db.query(CompetencyScore).filter(CompetencyScore.attempt_id == att.id).delete()
    for code, s in (att.summary or {}).items():
        for method in ("knowledge", "sjt"):
            if s.get(method) is not None:
                db.add(CompetencyScore(employee_id=att.employee_id, competency_code=code, cycle=att.kind,
                                       method=method, score=s[method], attempt_id=att.id,
                                       recorded_by="system"))
    db.commit()
    from .prescription import generate_plan  # avoid import cycle

    generate_plan(db, att.employee, att.kind)


# ------------------------------------------------------------------ profile
@dataclass
class CompRow:
    code: str
    name_en: str
    name_mr: str
    category: str
    safety: bool
    required: int
    scores: dict = field(default_factory=dict)  # cycle -> composite
    methods: dict = field(default_factory=dict)  # cycle -> {method: score}
    latest: float | None = None
    latest_cycle: str | None = None
    baseline: float | None = None
    level: int = 0
    baseline_level: int = 0
    gap: int = 0
    baseline_gap: int = 0
    status: str = "Not assessed"

    def name(self, lang: str) -> str:
        return self.name_mr if lang == "mr" and self.name_mr else self.name_en


@dataclass
class Profile:
    rows: list[CompRow]
    readiness: str
    index: float | None
    gap_total: int
    baseline_gap_total: int
    gap_closure: float | None
    assessed: int
    safety_gates: int

    @property
    def gaps(self) -> list[CompRow]:
        return [r for r in self.rows if r.gap > 0]


def status_for(row: CompRow) -> str:
    if row.latest is None:
        return "Not assessed"
    if row.gap <= 0:
        return "Meets"
    if row.safety:
        return "Safety gate"
    if row.gap >= 2:
        return "Priority"
    return "Developing"


def readiness_for(rows: list[CompRow], amber_index: float = 70.0) -> tuple[str, float | None]:
    assessed = [r for r in rows if r.latest is not None]
    if not assessed:
        return "None", None
    index = round(100.0 * sum(1 for r in assessed if r.gap == 0) / len(assessed), 1)
    if any(r.safety and r.gap > 0 for r in assessed):
        return "Red", index
    if any(r.gap >= 2 for r in assessed) or index < amber_index:
        return "Amber", index
    return "Green", index


def build_profile(db: Session, emp: Employee, amber_index: float = 70.0,
                  override: dict[str, dict] | None = None,
                  ctx: "fw.FrameworkCache | None" = None,
                  scores: list[CompetencyScore] | None = None) -> Profile:
    """Composite score per competency per cycle; latest vs baseline; gaps and readiness.
    `override` = {competency: {method: score}} is used for practice-test estimates.
    `ctx` / `scores` let dashboards pass preloaded data for many employees."""
    ctx = ctx or fw.FrameworkCache(db)
    prof = ctx.profile(emp)
    rows: dict[str, CompRow] = {}
    for p in prof:
        c = p.competency
        rows[c.code] = CompRow(c.code, c.name_en, c.name_mr, c.category, c.safety_critical, p.required_level)
    if scores is None:
        scores = db.scalars(
            select(CompetencyScore).where(CompetencyScore.employee_id == emp.id)
            .order_by(CompetencyScore.recorded_at, CompetencyScore.id)
        ).all()
    if override is None:
        for s in scores:
            r = rows.get(s.competency_code)
            if r:
                r.methods.setdefault(s.cycle, {})[s.method] = s.score  # latest record per method wins
    for code, r in rows.items():
        w = ctx.weights_for(emp, ctx.comps[code])
        if override is not None and code in override:
            r.methods = {"PRACTICE": override[code]}
        for cycle, m in r.methods.items():
            r.scores[cycle] = fw.composite(m, w)
        cycles = sorted([c for c in r.scores if r.scores[c] is not None],
                        key=lambda c: CYCLE_ORDER.get(c, 9))
        if cycles:
            r.latest_cycle = cycles[-1]
            r.latest = r.scores[cycles[-1]]
            r.baseline = r.scores.get("T0", r.scores[cycles[0]])
            r.level = fw.level_for(r.latest, ctx.cuts)
            r.baseline_level = fw.level_for(r.baseline, ctx.cuts)
            r.gap = max(0, r.required - r.level)
            r.baseline_gap = max(0, r.required - r.baseline_level)
        r.status = status_for(r)
    lst = list(rows.values())
    readiness, index = readiness_for(lst, amber_index)
    gap_total = sum(r.gap for r in lst if r.latest is not None)
    base_total = sum(r.baseline_gap for r in lst if r.latest is not None)
    re_measured = any(r.latest is not None and r.latest_cycle not in (None, "T0") and "T0" in r.scores for r in lst)
    closure = round(100 * (1 - gap_total / base_total), 1) if base_total and re_measured else None
    return Profile(lst, readiness, index, gap_total, base_total, closure,
                   sum(1 for r in lst if r.latest is not None),
                   sum(1 for r in lst if r.status == "Safety gate"))


def practice_profile(db: Session, emp: Employee, att: Attempt) -> Profile:
    override = {}
    for code, s in (att.summary or {}).items():
        m = {}
        if s.get("knowledge") is not None:
            m["knowledge"] = s["knowledge"]
        if s.get("sjt") is not None:
            m["sjt"] = s["sjt"]
        override[code] = m
    return build_profile(db, emp, override=override)


def record_rating(db: Session, emp: Employee, comp: Competency, cycle: str, method: str,
                  level: int | None, score: float | None, by: str, comment: str = "") -> CompetencyScore:
    if score is None and level is not None:
        score = fw.RATING_TO_SCORE.get(int(level))
    cs = CompetencyScore(employee_id=emp.id, competency_code=comp.code, cycle=cycle, method=method,
                         score=float(score), recorded_by=by, comment=comment)
    db.add(cs)
    db.commit()
    return cs

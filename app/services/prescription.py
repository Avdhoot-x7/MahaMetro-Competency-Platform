"""Turns measured gaps into an Individual Development Plan (prescription)."""
from __future__ import annotations

from datetime import timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import DevelopmentPlan, DevelopmentPlanItem, Employee, Intervention, utcnow
from .scoring import CompRow, Profile, build_profile

# days allowed to close each kind of gap (design note section 7)
TARGET_DAYS = {"safety": 30, "major": 60, "minor": 90, "behavioural": 90, "strength": 180}
PRIORITY = {"safety": 1, "major": 2, "minor": 3, "behavioural": 4, "strength": 9}


def gap_type(row: CompRow) -> str | None:
    if row.latest is None:
        return None
    if row.gap <= 0:
        return "strength" if row.level >= 4 else None
    if row.safety:
        return "safety"
    if row.category == "Behavioural":
        return "behavioural"
    return "major" if row.gap >= 2 else "minor"


def pick_intervention(db: Session, row: CompRow, gtype: str) -> Intervention | None:
    specific = db.scalars(
        select(Intervention).where(Intervention.competency_code == row.code, Intervention.gap_type == gtype)
    ).first()
    if specific:
        return specific
    specific_any = db.scalars(select(Intervention).where(Intervention.competency_code == row.code)).first()
    if specific_any and gtype != "strength":
        return specific_any
    return db.scalars(
        select(Intervention).where(Intervention.competency_code.is_(None),
                                   Intervention.gap_type == gtype,
                                   (Intervention.category == row.category) | Intervention.category.is_(None))
        .order_by(Intervention.category.desc())
    ).first()


def recommendations(db: Session, profile: Profile) -> list[dict]:
    """Prescription lines without saving (used for practice tests)."""
    out = []
    for r in profile.rows:
        g = gap_type(r)
        if not g:
            continue
        iv = pick_intervention(db, r, g)
        out.append({"row": r, "gap_type": g, "priority": PRIORITY[g], "intervention": iv,
                    "days": TARGET_DAYS[g]})
    gaps = sorted([x for x in out if x["gap_type"] != "strength"], key=lambda x: (x["priority"], -x["row"].gap, x["row"].code))
    # design note Annex C: top 3 strengths only (nominate as mentor / SME author)
    strengths = sorted([x for x in out if x["gap_type"] == "strength"], key=lambda x: -(x["row"].latest or 0))[:3]
    return gaps + strengths


def generate_plan(db: Session, emp: Employee, cycle: str) -> DevelopmentPlan:
    profile = build_profile(db, emp)
    # supersede any open proposal for the same cycle
    for old in db.scalars(select(DevelopmentPlan).where(
            DevelopmentPlan.employee_id == emp.id, DevelopmentPlan.cycle == cycle,
            DevelopmentPlan.status == "proposed")).all():
        db.delete(old)
    plan = DevelopmentPlan(employee_id=emp.id, cycle=cycle, readiness=profile.readiness,
                           competency_index=profile.index or 0)
    db.add(plan)
    db.flush()
    now = utcnow()
    for rec in recommendations(db, profile):
        r = rec["row"]
        db.add(DevelopmentPlanItem(
            plan_id=plan.id, competency_code=r.code, required_level=r.required, attained_level=r.level,
            score=r.latest or 0, gap=r.gap, priority=rec["priority"], gap_type=rec["gap_type"],
            intervention_id=rec["intervention"].id if rec["intervention"] else None,
            target_date=None if rec["gap_type"] == "strength" else now + timedelta(days=rec["days"]),
        ))
    db.commit()
    return plan


def latest_plan(db: Session, emp: Employee) -> DevelopmentPlan | None:
    return db.scalars(select(DevelopmentPlan).where(DevelopmentPlan.employee_id == emp.id)
                      .order_by(DevelopmentPlan.created_at.desc(), DevelopmentPlan.id.desc())).first()

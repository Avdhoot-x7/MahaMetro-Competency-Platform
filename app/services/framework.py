"""Competency framework rules: required level, score-to-level, composite weights."""
from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import Competency, CutScore, Employee, Grade, RoleFamily, RoleProfile, Setting

DEFAULT_WEIGHTS = {
    "OPS": {"knowledge": 35, "sjt": 20, "practical": 35, "rating": 10},
    "MNT": {"knowledge": 40, "sjt": 15, "practical": 35, "rating": 10},
    "PJS": {"knowledge": 45, "sjt": 30, "practical": 10, "rating": 15},
    "NT": {"knowledge": 45, "sjt": 30, "practical": 10, "rating": 15},
    "BEHAVIOURAL": {"knowledge": 0, "sjt": 60, "practical": 0, "rating": 40},
}
DEFAULT_CUTS = [(1, 0.0), (2, 40.0), (3, 55.0), (4, 70.0), (5, 85.0)]
# Supervisor/practical ratings entered on the 1-5 scale are converted to the middle of each band
RATING_TO_SCORE = {1: 25.0, 2: 47.0, 3: 62.0, 4: 77.0, 5: 92.0}


@dataclass
class ProfileEntry:
    competency: Competency
    base_level: int
    required_level: int


def cut_scores(db: Session) -> list[tuple[int, float]]:
    rows = db.scalars(select(CutScore).order_by(CutScore.level)).all()
    return [(r.level, r.min_score) for r in rows] or DEFAULT_CUTS


def level_for(score: float | None, cuts: list[tuple[int, float]]) -> int:
    if score is None:
        return 0
    lvl = 1
    for level, minimum in cuts:
        if score >= minimum:
            lvl = level
    return lvl


def grade_offset(grade: Grade | None, category: str) -> int:
    if grade is None:
        return 0
    return {
        "Core": grade.core_offset,
        "Behavioural": grade.behavioural_offset,
        "Domain": grade.domain_offset,
    }.get(category, 0)


def required_level(base: int, grade: Grade | None, category: str) -> int:
    if base <= 0:
        return 0
    return max(1, min(5, base + grade_offset(grade, category)))


def employee_profile(db: Session, emp: Employee) -> list[ProfileEntry]:
    """Competencies an employee is assessed on, with required levels (0 = not assessed)."""
    if not emp.role_family_code:
        return []
    grade = db.get(Grade, emp.grade_code) if emp.grade_code else None
    rows = db.execute(
        select(RoleProfile, Competency)
        .join(Competency, Competency.code == RoleProfile.competency_code)
        .where(RoleProfile.role_family_code == emp.role_family_code, RoleProfile.base_level > 0)
        .order_by(Competency.code)
    ).all()
    out = []
    for rp, comp in rows:
        out.append(ProfileEntry(comp, rp.base_level, required_level(rp.base_level, grade, comp.category)))
    order = {"Core": 0, "Behavioural": 1, "Domain": 2}
    out.sort(key=lambda p: (order.get(p.competency.category, 3), p.competency.code))
    return out


def weights(db: Session) -> dict:
    s = db.get(Setting, "composite_weights")
    return s.value if s and s.value else DEFAULT_WEIGHTS


def weights_for(db: Session, emp: Employee, comp: Competency) -> dict:
    w = weights(db)
    if comp.category == "Behavioural":
        return w.get("BEHAVIOURAL", DEFAULT_WEIGHTS["BEHAVIOURAL"])
    group = "NT"
    if emp.role_family_code:
        rf = db.get(RoleFamily, emp.role_family_code)
        if rf:
            group = rf.group
    return w.get(group, DEFAULT_WEIGHTS["NT"])


def composite(method_scores: dict[str, float], w: dict) -> float | None:
    """Weighted average of the methods available; missing methods' weight is redistributed."""
    avail = {m: s for m, s in method_scores.items() if s is not None and w.get(m, 0) > 0}
    if not avail:
        # fall back to whatever was measured (e.g. only knowledge test for a behavioural item)
        vals = [s for s in method_scores.values() if s is not None]
        return round(sum(vals) / len(vals), 1) if vals else None
    total_w = sum(w[m] for m in avail)
    return round(sum(avail[m] * w[m] for m in avail) / total_w, 1)


class FrameworkCache:
    """Loads the framework once so dashboards can build hundreds of profiles cheaply."""

    def __init__(self, db: Session):
        self.cuts = cut_scores(db)
        self.weights = weights(db)
        self.comps = {c.code: c for c in db.scalars(select(Competency)).all()}
        self.grades = {g.code: g for g in db.scalars(select(Grade)).all()}
        self.groups = {r.code: r.group for r in db.scalars(select(RoleFamily)).all()}
        self.base: dict[str, dict[str, int]] = {}
        for rp in db.scalars(select(RoleProfile).where(RoleProfile.base_level > 0)).all():
            self.base.setdefault(rp.role_family_code, {})[rp.competency_code] = rp.base_level
        self._prof: dict[tuple, list[ProfileEntry]] = {}

    def profile(self, emp: Employee) -> list[ProfileEntry]:
        key = (emp.role_family_code, emp.grade_code)
        if key not in self._prof:
            g = self.grades.get(emp.grade_code)
            order = {"Core": 0, "Behavioural": 1, "Domain": 2}
            out = [ProfileEntry(self.comps[c], b, required_level(b, g, self.comps[c].category))
                   for c, b in self.base.get(emp.role_family_code or "", {}).items() if c in self.comps]
            out.sort(key=lambda p: (order.get(p.competency.category, 3), p.competency.code))
            self._prof[key] = out
        return self._prof[key]

    def weights_for(self, emp: Employee, comp: Competency) -> dict:
        if comp.category == "Behavioural":
            return self.weights.get("BEHAVIOURAL", DEFAULT_WEIGHTS["BEHAVIOURAL"])
        return self.weights.get(self.groups.get(emp.role_family_code or "", "NT"), DEFAULT_WEIGHTS["NT"])

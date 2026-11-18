"""Role-based dashboards: employee, supervisor (team), HoD (department), management (organisation)."""
from __future__ import annotations

from collections import defaultdict

from fastapi import APIRouter, Depends, Form, HTTPException, Request
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from ..db import get_db
from ..models import (
    CYCLE_ORDER, Attempt, Competency, DevelopmentPlan, DevelopmentPlanItem, Employee, RoleFamily, Test, utcnow,
)
from ..security import audit, can_view_employee, check_csrf, current_user, require
from ..services import analytics, engine, prescription, scoring
from ..web import flash, render

router = APIRouter(dependencies=[Depends(check_csrf)])


@router.get("/")
def root(request: Request):
    return RedirectResponse("/me" if request.session.get("uid") else "/login", status_code=303)


def _employee_context(db: Session, emp: Employee) -> dict:
    profile = scoring.build_profile(db, emp)
    plan = prescription.latest_plan(db, emp)
    if plan:
        plan = db.scalar(select(DevelopmentPlan).options(
            selectinload(DevelopmentPlan.items).selectinload(DevelopmentPlanItem.intervention),
            selectinload(DevelopmentPlan.items).selectinload(DevelopmentPlanItem.competency),
        ).where(DevelopmentPlan.id == plan.id))
    history = db.scalars(select(Attempt).options(selectinload(Attempt.test))
                         .where(Attempt.employee_id == emp.id).order_by(Attempt.started_at.desc()).limit(25)).all()
    cycles = sorted({c for r in profile.rows for c in r.scores if r.scores[c] is not None},
                    key=lambda c: CYCLE_ORDER.get(c, 9))
    return {"emp": emp, "profile": profile, "plan": plan, "history": history, "cycles": cycles,
            "rf": emp.role_family, "grade": emp.grade}


@router.get("/me")
def my_dashboard(request: Request, user: Employee = Depends(current_user), db: Session = Depends(get_db)):
    tests = db.scalars(select(Test).where(Test.status == "published").order_by(Test.start_at)).all()
    official, practice = [], []
    for t in tests:
        if not engine.is_eligible(t, user):
            continue
        info = {"test": t, "used": engine.attempts_used(db, t, user), "active": engine.active_attempt(db, t, user)}
        info["left"] = (t.max_attempts - info["used"]) if t.max_attempts else None
        (practice if t.is_practice else official).append(info)
    ctx = _employee_context(db, user)
    ctx.update({"user": user, "official": official, "practice": practice, "self_view": True})
    return render(request, "me.html", ctx)


@router.get("/employee/{emp_id}")
def employee_report(emp_id: str, request: Request, print: int = 0, user: Employee = Depends(current_user),
                    db: Session = Depends(get_db)):
    emp = db.scalar(select(Employee).where(Employee.emp_id == emp_id))
    if not emp or not can_view_employee(user, emp):
        raise HTTPException(404)
    ctx = _employee_context(db, emp)
    comps = fw_profile = None
    if user.has("supervisor", "cell") and (emp.reporting_officer == user.emp_id or user.has("cell")):
        from ..services.framework import employee_profile

        fw_profile = employee_profile(db, emp)
    peers = [e for e in db.scalars(select(Employee).where(Employee.role_family_code == emp.role_family_code,
                                                          Employee.grade_code == emp.grade_code,
                                                          Employee.active.is_(True))).all()]
    bench = {b["code"]: b for b in analytics.competency_benchmarks(peers, analytics.profiles_for(db, peers))}
    ctx.update({"user": user, "self_view": emp.id == user.id, "rate_profile": fw_profile, "comps": comps,
                "bench": bench, "print": print})
    return render(request, "report.html", ctx)


@router.get("/me/report")
def my_report(request: Request, user: Employee = Depends(current_user)):
    return RedirectResponse(f"/employee/{user.emp_id}?print=1", status_code=303)


# ------------------------------------------------------------------ supervisor
@router.get("/team")
def team(request: Request, user: Employee = Depends(require("supervisor", "cell")), db: Session = Depends(get_db)):
    members = db.scalars(select(Employee).where(Employee.reporting_officer == user.emp_id, Employee.active.is_(True))
                         .order_by(Employee.name_en)).all()
    profiles = analytics.profiles_for(db, members)
    codes: list[str] = []
    for m in members:
        for r in profiles[m.id].rows:
            if r.code not in codes:
                codes.append(r.code)
    order = {"C": 0, "B": 1, "D": 2}
    codes.sort(key=lambda c: (order.get(c[0], 3), c))
    comps = {c.code: c for c in db.scalars(select(Competency)).all()}
    plans = {}
    for m in members:
        p = prescription.latest_plan(db, m)
        if p:
            plans[m.id] = p
    summary = analytics.summarise(members, profiles)
    return render(request, "team.html", {"user": user, "members": members, "profiles": profiles, "codes": codes,
                                         "comps": comps, "plans": plans, "summary": summary})


@router.post("/team/{emp_id}/rating")
def add_rating(emp_id: str, request: Request, competency: str = Form(...), method: str = Form(...),
               cycle: str = Form(...), level: int = Form(0), score: str = Form(""), comment: str = Form(""),
               user: Employee = Depends(require("supervisor", "cell")), db: Session = Depends(get_db)):
    emp = db.scalar(select(Employee).where(Employee.emp_id == emp_id))
    if not emp or not (emp.reporting_officer == user.emp_id or user.has("cell")):
        raise HTTPException(404)
    comp = db.get(Competency, competency)
    if not comp or method not in ("rating", "practical") or cycle not in CYCLE_ORDER:
        raise HTTPException(400, "Invalid rating")
    val = float(score) if score.strip() else None
    if val is not None and not 0 <= val <= 100:
        raise HTTPException(400, "Score must be 0–100")
    if val is None and not 1 <= level <= 5:
        raise HTTPException(400, "Choose a level 1–5 or enter a score")
    scoring.record_rating(db, emp, comp, cycle, method, level or None, val, user.emp_id, comment[:1000])
    prescription.generate_plan(db, emp, cycle)
    audit(db, request, user.emp_id, f"{method}_recorded", "employee", emp.emp_id, competency=competency,
          cycle=cycle, level=level, score=val)
    flash(request, "Saved. The competency profile and development plan have been updated.", "ok")
    return RedirectResponse(f"/employee/{emp_id}", status_code=303)


@router.post("/plan/{plan_id}/confirm")
def confirm_plan(plan_id: int, request: Request, user: Employee = Depends(current_user), db: Session = Depends(get_db)):
    plan = db.get(DevelopmentPlan, plan_id)
    if not plan:
        raise HTTPException(404)
    emp = plan.employee
    if not (emp.id == user.id or emp.reporting_officer == user.emp_id or user.has("cell")):
        raise HTTPException(403)
    plan.status = "confirmed"
    plan.confirmed_by = user.emp_id
    plan.confirmed_at = utcnow()
    db.commit()
    audit(db, request, user.emp_id, "plan_confirmed", "development_plan", plan.id)
    flash(request, "Development plan confirmed.", "ok")
    return RedirectResponse(request.headers.get("referer") or "/me", status_code=303)


@router.post("/plan/item/{item_id}/status")
def plan_item_status(item_id: int, request: Request, status: str = Form(...), user: Employee = Depends(current_user),
                     db: Session = Depends(get_db)):
    it = db.get(DevelopmentPlanItem, item_id)
    if not it or status not in ("open", "scheduled", "done"):
        raise HTTPException(404)
    emp = it.plan.employee
    if not (emp.reporting_officer == user.emp_id or user.has("cell")):
        raise HTTPException(403)
    it.status = status
    db.commit()
    return RedirectResponse(request.headers.get("referer") or "/team", status_code=303)


# ------------------------------------------------------------------ HoD and management
def _filtered(db: Session, department: str | None, family: str | None, location: str | None) -> list[Employee]:
    q = select(Employee).where(Employee.active.is_(True), Employee.role_family_code.is_not(None))
    if department:
        q = q.where(Employee.department == department)
    if family:
        q = q.where(Employee.role_family_code == family)
    if location:
        q = q.where(Employee.location == location)
    return db.scalars(q).all()


def _breakdown(emps: list[Employee], profiles, key) -> list[dict]:
    groups: dict[str, list[Employee]] = defaultdict(list)
    for e in emps:
        groups[key(e) or "–"].append(e)
    rows = []
    for k, members in sorted(groups.items()):
        s = analytics.summarise(members, profiles)
        s["key"] = k
        rows.append(s)
    return rows


@router.get("/department")
def department(request: Request, dept: str = "", family: str = "", location: str = "",
               user: Employee = Depends(require("hod", "cell", "management")), db: Session = Depends(get_db)):
    if not user.has("cell", "management"):
        dept = user.department  # an HoD sees only their own department
    depts = sorted({d for (d,) in db.execute(select(Employee.department).distinct()) if d})
    dept = dept or (depts[0] if depts else "")
    emps = _filtered(db, dept, family or None, location or None)
    profiles = analytics.profiles_for(db, emps)
    families = {r.code: r for r in db.scalars(select(RoleFamily)).all()}
    locations = sorted({e.location for e in _filtered(db, dept, None, None) if e.location})
    return render(request, "department.html", {
        "user": user, "dept": dept, "depts": depts, "family": family, "location": location,
        "families": families, "locations": locations, "summary": analytics.summarise(emps, profiles),
        "bench": analytics.competency_benchmarks(emps, profiles),
        "dist": analytics.score_distribution(emps, profiles),
        "by_family": _breakdown(emps, profiles, lambda e: e.role_family_code),
    })


@router.get("/org")
def organisation(request: Request, user: Employee = Depends(require("management", "cell")),
                 db: Session = Depends(get_db)):
    emps = _filtered(db, None, None, None)
    profiles = analytics.profiles_for(db, emps)
    families = {r.code: r for r in db.scalars(select(RoleFamily)).all()}
    return render(request, "org.html", {
        "user": user, "summary": analytics.summarise(emps, profiles), "families": families,
        "by_family": _breakdown(emps, profiles, lambda e: e.role_family_code),
        "by_location": _breakdown(emps, profiles, lambda e: e.location),
        "by_grade": _breakdown(emps, profiles, lambda e: e.grade_code),
        "dist": analytics.score_distribution(emps, profiles),
        "bench": analytics.competency_benchmarks(emps, profiles),
    })

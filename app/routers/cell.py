"""CoE Assessment Cell console: tests, scheduling, manual scoring, proctoring review, imports,
staff and roles, framework, interventions, item analysis, exports and audit."""
from __future__ import annotations

import io
import re
from datetime import datetime

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import RedirectResponse, StreamingResponse
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session, selectinload

from ..db import get_db
from ..models import (
    ROLES, TEST_KINDS, Attempt, AttemptItem, AuditLog, Blueprint, Competency, CutScore, DesignationMap,
    DevelopmentPlan, DevelopmentPlanItem, Employee, Grade, Intervention, Item, ProctorEvent, RoleFamily, RoleProfile, Setting, Test,
    utcnow,
)
from ..security import audit, check_csrf, hash_password, require
from ..services import analytics, engine, importers, prescription, scoring, timing
from ..web import flash, render

router = APIRouter(prefix="/cell", dependencies=[Depends(check_csrf)])
CELL = require("cell")


@router.get("")
def overview(request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    n_emp = db.scalar(select(func.count(Employee.id)).where(Employee.active.is_(True))) or 0
    unmapped = db.scalar(select(func.count(Employee.id)).where(Employee.active.is_(True),
                         (Employee.role_family_code.is_(None)) | (Employee.grade_code.is_(None)))) or 0
    assessed = db.scalar(select(func.count(func.distinct(Attempt.employee_id))).where(
        Attempt.kind != "PRACTICE", Attempt.status == "scored")) or 0
    practiced = db.scalar(select(func.count(func.distinct(Attempt.employee_id))).where(Attempt.kind == "PRACTICE")) or 0
    tests = db.scalars(select(Test).order_by(Test.created_at.desc()).limit(10)).all()
    return render(request, "cell/overview.html", {
        "user": user, "n_emp": n_emp, "unmapped": unmapped, "assessed": assessed, "practiced": practiced,
        "manual_n": len(analytics.manual_queue(db)),
        "flag_n": db.scalar(select(func.count(Attempt.id)).where(Attempt.status == "flagged")) or 0,
        "in_progress": db.scalar(select(func.count(Attempt.id)).where(Attempt.status == "in_progress")) or 0,
        "item_counts": dict(db.execute(select(Item.status, func.count()).group_by(Item.status)).all()),
        "coverage": analytics.bank_coverage(db), "tests": tests,
        "comps": {c.code: c for c in db.scalars(select(Competency)).all()},
    })


# ------------------------------------------------------------------ tests
@router.get("/tests")
def tests(request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    rows = db.scalars(select(Test).order_by(Test.created_at.desc())).all()
    stats = {t.id: dict(db.execute(select(Attempt.status, func.count()).where(Attempt.test_id == t.id)
                                   .group_by(Attempt.status)).all()) for t in rows}
    return render(request, "cell/tests.html", {"user": user, "tests": rows, "stats": stats})


def _test_form(request, db, user, test):
    return render(request, "cell/test_edit.html", {
        "user": user, "test": test, "kinds": TEST_KINDS,
        "families": db.scalars(select(RoleFamily)).all(), "grades": db.scalars(select(Grade)).all(),
        "blueprints": db.scalars(select(Blueprint)).all(),
        "comps": db.scalars(select(Competency).order_by(Competency.code)).all(),
        "departments": sorted({d for (d,) in db.execute(select(Employee.department).distinct()) if d}),
        "locations": sorted({d for (d,) in db.execute(select(Employee.location).distinct()) if d}),
    })


@router.get("/tests/new")
def test_new(request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    return _test_form(request, db, user, None)


@router.get("/tests/{test_id}/edit")
def test_edit(test_id: int, request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    t = db.get(Test, test_id)
    if not t:
        raise HTTPException(404)
    return _test_form(request, db, user, t)


def _dt(v: str | None) -> datetime | None:
    return datetime.fromisoformat(v) if v else None


@router.post("/tests/save")
async def test_save(request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    f = await request.form()
    tid = f.get("id")
    t = db.get(Test, int(tid)) if tid else None
    code = (f.get("code") or "").strip().upper()
    if not code:
        flash(request, "Test code is required.", "error")
        return RedirectResponse("/cell/tests/new", status_code=303)
    if t is None:
        if db.scalar(select(Test.id).where(Test.code == code)):
            flash(request, f"Test code {code} already exists.", "error")
            return RedirectResponse("/cell/tests/new", status_code=303)
        t = Test(code=code, title_en="", created_by=user.emp_id)
        db.add(t)
    t.code = code
    t.title_en = f.get("title_en") or code
    t.title_mr = f.get("title_mr") or ""
    t.instructions_en = f.get("instructions_en") or ""
    t.instructions_mr = f.get("instructions_mr") or ""
    t.kind = f.get("kind") if f.get("kind") in TEST_KINDS else "T0"
    t.blueprint_id = int(f["blueprint_id"]) if f.get("blueprint_id") else None
    t.items_per_competency = int(f.get("items_per_competency") or 3)
    t.max_items = int(f.get("max_items") or 80)
    t.duration_min = int(f.get("duration_min") or 60)
    t.max_attempts = int(f.get("max_attempts") or 0)
    t.start_at = _dt(f.get("start_at"))
    t.end_at = _dt(f.get("end_at"))
    t.shuffle_questions = bool(f.get("shuffle_questions"))
    t.shuffle_options = bool(f.get("shuffle_options"))
    t.show_feedback = bool(f.get("show_feedback")) or t.kind == "PRACTICE"
    t.allow_back = bool(f.get("allow_back"))
    t.competency_filter = f.getlist("competency_filter")
    t.category_filter = f.getlist("category_filter")
    t.target = {
        "role_families": f.getlist("t_families"), "grades": f.getlist("t_grades"),
        "departments": f.getlist("t_departments"), "locations": f.getlist("t_locations"),
        "emp_ids": [e.strip() for e in re.split(r"[\s,;]+", f.get("t_emp_ids") or "") if e.strip()],
    }
    t.proctoring = {
        "fullscreen": bool(f.get("p_fullscreen")), "webcam": bool(f.get("p_webcam")),
        "max_tab_switches": int(f["p_max_tabs"]) if f.get("p_max_tabs") else None,
        "snapshot_seconds": int(f.get("p_snapshot") or 60),
    }
    db.commit()
    audit(db, request, user.emp_id, "test_saved", "test", t.code)
    flash(request, "Test saved.", "ok")
    return RedirectResponse(f"/cell/tests/{t.id}", status_code=303)


@router.post("/tests/{test_id}/status")
def test_status(test_id: int, request: Request, status: str = Form(...), user: Employee = Depends(CELL),
                db: Session = Depends(get_db)):
    t = db.get(Test, test_id)
    if not t or status not in ("draft", "published", "closed"):
        raise HTTPException(404)
    t.status = status
    db.commit()
    audit(db, request, user.emp_id, f"test_{status}", "test", t.code)
    return RedirectResponse(f"/cell/tests/{t.id}", status_code=303)


@router.get("/tests/{test_id}")
def test_detail(test_id: int, request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    t = db.get(Test, test_id)
    if not t:
        raise HTTPException(404)
    atts = db.scalars(select(Attempt).options(selectinload(Attempt.employee),
                                              selectinload(Attempt.items).selectinload(AttemptItem.item))
                      .where(Attempt.test_id == t.id).order_by(Attempt.started_at.desc()).limit(500)).all()
    norms = timing.item_norms(db, list({ai.item_id for a in atts for ai in a.items}))
    timings = {a.id: timing.analyse(db, a, norms) for a in atts if a.status != "in_progress"}
    eligible = [e for e in db.scalars(select(Employee).where(Employee.active.is_(True))).all()
                if engine.is_eligible(t, e)] if t.status == "published" else []
    done = {a.employee_id for a in atts if a.status in ("scored", "pending_manual", "flagged")}
    return render(request, "cell/test_detail.html", {
        "user": user, "test": t, "atts": atts, "eligible_n": len(eligible),
        "not_taken": [e for e in eligible if e.id not in done][:200], "kr20": analytics.kr20(db, t.id),
        "timings": timings,
    })


# ------------------------------------------------------------------ manual scoring
@router.get("/scoring")
def scoring_queue(request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    queue = analytics.manual_queue(db)
    # a Cell member never marks their own answers (conflict of interest): hide them, say how many
    own = [ai for ai in queue if ai.attempt.employee_id == user.id]
    queue = [ai for ai in queue if ai.attempt.employee_id != user.id]
    return render(request, "cell/scoring.html", {"user": user, "queue": queue[:100], "own_n": len(own)})


@router.post("/scoring/{ai_id}")
def score_one(ai_id: int, request: Request, score: float = Form(...), comment: str = Form(""),
              user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    ai = db.get(AttemptItem, ai_id)
    if not ai or not ai.needs_manual:
        raise HTTPException(404)
    if ai.attempt.employee.emp_id == user.emp_id:
        raise HTTPException(403, "You cannot score your own answers")
    ai.manual_score = max(0.0, min(float(score), ai.max_score))
    ai.scored_by = user.emp_id
    ai.scorer_comment = comment[:1000]
    db.commit()
    audit(db, request, user.emp_id, "manual_scored", "attempt_item", ai.id, score=ai.manual_score)
    scoring.complete_manual_scoring(db, ai.attempt)
    return RedirectResponse("/cell/scoring", status_code=303)


# ------------------------------------------------------------------ proctoring review
@router.get("/flags")
def flags(request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    atts = db.scalars(select(Attempt).options(selectinload(Attempt.employee), selectinload(Attempt.test))
                      .where(Attempt.status == "flagged").order_by(Attempt.submitted_at)).all()
    return render(request, "cell/flags.html", {"user": user, "atts": atts})


@router.get("/attempts/{attempt_id}")
def attempt_detail(attempt_id: int, request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    att = db.scalar(select(Attempt).options(selectinload(Attempt.employee), selectinload(Attempt.test),
                                            selectinload(Attempt.items).selectinload(AttemptItem.item))
                    .where(Attempt.id == attempt_id))
    if not att:
        raise HTTPException(404)
    events = db.scalars(select(ProctorEvent).where(ProctorEvent.attempt_id == att.id).order_by(ProctorEvent.at)).all()
    return render(request, "cell/attempt.html", {"user": user, "att": att, "events": events,
                                                 "timing": timing.analyse(db, att)})


@router.post("/attempts/{attempt_id}/decision")
def attempt_decision(attempt_id: int, request: Request, decision: str = Form(...), note: str = Form(""),
                     user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    att = db.get(Attempt, attempt_id)
    if not att or att.status not in ("flagged", "scored", "pending_manual"):
        raise HTTPException(404)
    att.reviewed_by = user.emp_id
    att.flag_reason = (att.flag_reason + f" | review: {decision} {note}").strip()[:2000]
    if decision == "accept":
        att.status = "scored"
        db.commit()
        scoring.publish_scores(db, att)
    elif decision == "void":
        att.status = "void"
        db.query(scoring.CompetencyScore).filter(scoring.CompetencyScore.attempt_id == att.id).delete()
        db.commit()
        if att.kind != "PRACTICE":
            prescription.generate_plan(db, att.employee, att.kind)
    audit(db, request, user.emp_id, f"attempt_{decision}", "attempt", att.id, note=note[:200])
    flash(request, f"Attempt {att.id}: {decision}", "ok")
    return RedirectResponse("/cell/flags", status_code=303)


# ------------------------------------------------------------------ imports and templates
@router.get("/imports")
def imports(request: Request, user: Employee = Depends(CELL)):
    return render(request, "cell/imports.html", {"user": user, "report": request.session.pop("import_report", None)})


@router.post("/imports/employees")
async def import_emps(request: Request, file: UploadFile = File(...), user: Employee = Depends(CELL),
                      db: Session = Depends(get_db)):
    rep = importers.import_employees(db, file.filename or "upload.csv", await file.read(), user.emp_id)
    audit(db, request, user.emp_id, "employees_imported", "employee", "", created=rep.created, updated=rep.updated)
    request.session["import_report"] = {"title": "Employee import", **rep.__dict__,
                                        "errors": rep.errors[:200], "warnings": rep.warnings[:200]}
    return RedirectResponse("/cell/imports", status_code=303)


@router.post("/imports/items")
async def import_items(request: Request, file: UploadFile = File(...), status: str = Form("review"),
                       user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    rep = importers.import_items(db, file.filename or "items.xlsx", await file.read(), user.emp_id,
                                 status if status in ("draft", "review", "approved") else "review")
    audit(db, request, user.emp_id, "items_imported", "item", "", created=rep.created, updated=rep.updated)
    request.session["import_report"] = {"title": "Question bank import", **rep.__dict__,
                                        "errors": rep.errors[:200], "warnings": rep.warnings[:200]}
    return RedirectResponse("/cell/imports", status_code=303)


def _xlsx_response(wb: Workbook, name: str) -> StreamingResponse:
    buf = io.BytesIO()
    wb.save(buf)
    buf.seek(0)
    return StreamingResponse(buf, media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                             headers={"Content-Disposition": f'attachment; filename="{name}"'})


def _header(ws, cols: list[str], width: int = 18) -> None:
    ws.append(cols)
    for c in ws[1]:
        c.font = Font(bold=True, color="FFFFFF")
        c.fill = PatternFill("solid", fgColor="1F4E79")
        c.alignment = Alignment(wrap_text=True, vertical="top")
    for i in range(1, len(cols) + 1):
        ws.column_dimensions[ws.cell(1, i).column_letter].width = width
    ws.freeze_panes = "A2"


ITEM_COLS = (["Item ID", "Competency", "Role families", "Target level", "Bloom", "Item type", "Pool",
              "Stem (EN)", "Stem (MR)", "Passage ID", "Passage order", "Media file"]
             + [f"Option {L} (EN)" for L in "ABCDEF"] + [f"Option {L} (MR)" for L in "ABCDEF"]
             + ["Key", "SJT weights", "Tolerance", "Marks", "Negative marks", "Partial credit", "Shuffle options",
                "Rationale (EN)", "Rationale (MR)", "Rubric (EN)", "Rubric (MR)", "Source reference", "Author",
                "Status"])


@router.get("/templates/items.xlsx")
def item_template(user: Employee = Depends(CELL)):
    wb = Workbook()
    ws = wb.active
    ws.title = "Items"
    _header(ws, ITEM_COLS)
    blank = {c: "" for c in ITEM_COLS}
    samples = [
        {"Item ID": "C01-9001", "Competency": "C01", "Role families": "ALL", "Target level": 2, "Bloom": "Apply",
         "Item type": "single", "Pool": "exam", "Stem (EN)": "Which extinguisher is used first on a fire in a live electrical panel?",
         "Stem (MR)": "चालू विद्युत पॅनेलमधील आगीवर प्रथम कोणते अग्निशामक वापरावे?", "Option A (EN)": "Water",
         "Option B (EN)": "Foam", "Option C (EN)": "CO2", "Option D (EN)": "Sand only", "Option A (MR)": "पाणी",
         "Option B (MR)": "फोम", "Option C (MR)": "CO2", "Option D (MR)": "फक्त वाळू", "Key": "C",
         "Rationale (EN)": "CO2 does not conduct electricity.", "Source reference": "Fire safety manual"},
        {"Item ID": "D13-9001", "Competency": "D13", "Role families": "TPS,EM,RS", "Item type": "sequence",
         "Stem (EN)": "Put the steps in order before working on isolated equipment.", "Option A (EN)": "Apply earthing",
         "Option B (EN)": "Obtain permit", "Option C (EN)": "Test for absence of voltage", "Option D (EN)": "Isolate and lock out",
         "Key": "B,D,C,A", "Partial credit": "Y"},
        {"Item ID": "C04-9001", "Competency": "C04", "Item type": "sjt", "Stem (EN)": "A contractor leaves a gift ...",
         "Option A (EN)": "Keep it", "Option B (EN)": "Return it and report it", "Option C (EN)": "Share it",
         "SJT weights": "0,1,0"},
        {"Item ID": "D11-9001", "Competency": "D11", "Item type": "match", "Stem (EN)": "Match each system to its function",
         "Option A (EN)": "TVS => Tunnel smoke extraction", "Option B (EN)": "BMS => Building monitoring",
         "Option C (EN)": "FAS => Fire detection"},
        {"Item ID": "D19-9001", "Competency": "D19", "Item type": "numeric", "Stem (EN)": "Compute ...", "Key": "1250",
         "Tolerance": "0"},
        {"Item ID": "C06-9001", "Competency": "C06", "Item type": "audio_response", "Stem (EN)": "Record a PA announcement for a 10-minute delay.",
         "Rubric (EN)": "Apology, reason, expected delay, alternative, clear diction (2 marks each)", "Marks": 10},
    ]
    for s in samples:
        ws.append([{**blank, **s}[c] for c in ITEM_COLS])
    ws2 = wb.create_sheet("Passages")
    _header(ws2, ["Passage ID", "Title (EN)", "Title (MR)", "Text (EN)", "Text (MR)", "Media file"], 30)
    ws2.append(["P-0001", "Incident report", "घटना अहवाल", "At 08:42 ...", "०८:४२ वाजता ...", ""])
    ws3 = wb.create_sheet("Guide")
    for line in [
        "Item type: single, multi, truefalse, sjt, sequence, match, numeric, fill_blank, short_text, audio_response",
        "Key: letters of correct options (C or A,C); for sequence the correct order (B,D,C,A); numeric value; "
        "fill_blank accepted answers separated by ;",
        "SJT weights: credit per option in order, e.g. 1,0.5,0,0",
        "Match: write each pair as 'left => right' in the option cells",
        "Pool: exam (official tests only), practice (practice test only), both",
        "Media file: exact file name already uploaded in Question bank > Media & passages",
        "Role families: ALL or codes such as SC,OCC,TO",
        "Status 'SME validation' marks items whose key must be checked against SR/SOPs",
    ]:
        ws3.append([line])
    ws3.column_dimensions["A"].width = 140
    return _xlsx_response(wb, "item_bank_template.xlsx")


@router.get("/templates/employees.xlsx")
def employee_template(user: Employee = Depends(CELL)):
    wb = Workbook()
    ws = wb.active
    ws.title = "Employees"
    cols = ["Employee ID", "Name", "Name (Marathi)", "Designation", "Department", "Location", "Grade",
            "Role family", "Reporting Officer ID", "Email", "Mobile", "Extra time %", "Active"]
    _header(ws, cols)
    ws.append(["100234", "A. B. Patil", "अ. ब. पाटील", "Station Controller", "Operations", "Pune – Line 1", "G2", "",
               "100120", "ab.patil@mahametro.org", "", 0, "Y"])
    return _xlsx_response(wb, "employee_master_template.xlsx")


# ------------------------------------------------------------------ staff and roles
@router.get("/employees")
def employees(request: Request, q: str = "", family: str = "", unmapped: int = 0, page: int = 1,
              user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    stmt = select(Employee)
    if q:
        stmt = stmt.where((Employee.emp_id.ilike(f"%{q}%")) | (Employee.name_en.ilike(f"%{q}%"))
                          | (Employee.designation.ilike(f"%{q}%")))
    if family:
        stmt = stmt.where(Employee.role_family_code == family)
    if unmapped:
        stmt = stmt.where((Employee.role_family_code.is_(None)) | (Employee.grade_code.is_(None)))
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = db.scalars(stmt.order_by(Employee.emp_id).offset((page - 1) * 50).limit(50)).all()
    return render(request, "cell/employees.html", {
        "user": user, "rows": rows, "total": total, "page": page, "q": q, "family": family, "unmapped": unmapped,
        "families": db.scalars(select(RoleFamily)).all(), "grades": db.scalars(select(Grade)).all(), "roles": ROLES,
    })


@router.post("/employees/{emp_pk}")
async def employee_update(emp_pk: int, request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    e = db.get(Employee, emp_pk)
    if not e:
        raise HTTPException(404)
    f = await request.form()
    e.role_family_code = f.get("role_family_code") or None
    e.grade_code = f.get("grade_code") or None
    e.extra_time_pct = int(f.get("extra_time_pct") or 0)
    e.reporting_officer = (f.get("reporting_officer") or "").strip()
    e.department = (f.get("department") or e.department).strip()
    e.active = bool(f.get("active"))
    roles = [r for r in f.getlist("roles") if r in ROLES]
    if "superadmin" in roles and not user.has("superadmin"):
        roles.remove("superadmin")
    if "superadmin" in (e.roles or []) and not user.has("superadmin"):
        roles.append("superadmin")
    e.roles = sorted(set(roles) | {"employee"})
    if f.get("new_password"):
        if len(f["new_password"]) < 10:
            flash(request, "Password must be at least 10 characters.", "error")
        else:
            e.password_hash = hash_password(f["new_password"])
    db.commit()
    audit(db, request, user.emp_id, "employee_updated", "employee", e.emp_id, roles=e.roles,
          family=e.role_family_code, grade=e.grade_code)
    flash(request, f"{e.emp_id} updated.", "ok")
    return RedirectResponse(request.headers.get("referer") or "/cell/employees", status_code=303)


# ------------------------------------------------------------------ framework
@router.get("/framework")
def framework(request: Request, family: str = "SC", user: Employee = Depends(require("cell", "hod")),
              db: Session = Depends(get_db)):
    comps = db.scalars(select(Competency).order_by(Competency.code)).all()
    prof = {rp.competency_code: rp for rp in db.scalars(select(RoleProfile).where(RoleProfile.role_family_code == family)).all()}
    return render(request, "cell/framework.html", {
        "user": user, "family": family, "families": db.scalars(select(RoleFamily)).all(), "comps": comps,
        "prof": prof, "cuts": db.scalars(select(CutScore).order_by(CutScore.level)).all(),
        "grades": db.scalars(select(Grade)).all(),
        "dmap": db.scalars(select(DesignationMap).order_by(DesignationMap.designation)).all(),
        "weights": (db.get(Setting, "composite_weights").value if db.get(Setting, "composite_weights") else {}),
    })


@router.post("/framework/profile")
async def save_profile(request: Request, user: Employee = Depends(require("cell", "hod")), db: Session = Depends(get_db)):
    f = await request.form()
    family = f.get("family")
    if not db.get(RoleFamily, family):
        raise HTTPException(404)
    changed = {}
    for c in db.scalars(select(Competency)).all():
        v = f.get(f"lvl_{c.code}")
        if v is None:
            continue
        lvl = max(0, min(5, int(v or 0)))
        rp = db.get(RoleProfile, (family, c.code))
        if rp is None:
            rp = RoleProfile(role_family_code=family, competency_code=c.code, base_level=0)
            db.add(rp)
        if rp.base_level != lvl:
            changed[c.code] = [rp.base_level, lvl]
            rp.base_level = lvl
        rp.approved_by, rp.approved_at = user.emp_id, utcnow()
    db.commit()
    audit(db, request, user.emp_id, "role_profile_saved", "role_family", family, changes=changed)
    flash(request, f"Role profile {family} saved ({len(changed)} changes).", "ok")
    return RedirectResponse(f"/cell/framework?family={family}", status_code=303)


@router.post("/framework/cuts")
async def save_cuts(request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    f = await request.form()
    vals = []
    for c in db.scalars(select(CutScore).order_by(CutScore.level)).all():
        c.min_score = float(f.get(f"cut_{c.level}", c.min_score))
        vals.append(c.min_score)
    if vals != sorted(vals):
        db.rollback()
        flash(request, "Cut scores must increase with level.", "error")
    else:
        db.commit()
        audit(db, request, user.emp_id, "cut_scores_saved", "cut_score", "", values=vals)
        flash(request, "Cut scores saved.", "ok")
    return RedirectResponse("/cell/framework", status_code=303)


@router.post("/framework/designation")
def save_designation(request: Request, designation: str = Form(...), role_family_code: str = Form(""),
                     grade_code: str = Form(""), user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    d = db.scalar(select(DesignationMap).where(DesignationMap.designation == designation.strip()))
    if d is None:
        d = DesignationMap(designation=designation.strip())
        db.add(d)
    d.role_family_code = role_family_code or None
    d.grade_code = grade_code or None
    db.commit()
    # apply to unmapped employees with that designation
    for e in db.scalars(select(Employee).where(Employee.designation == d.designation)).all():
        e.role_family_code = e.role_family_code or d.role_family_code
        e.grade_code = e.grade_code or d.grade_code
    db.commit()
    flash(request, "Designation mapping saved.", "ok")
    return RedirectResponse("/cell/framework", status_code=303)


# ------------------------------------------------------------------ interventions
@router.get("/interventions")
def interventions(request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    return render(request, "cell/interventions.html", {
        "user": user, "rows": db.scalars(select(Intervention).order_by(Intervention.competency_code,
                                                                        Intervention.gap_type)).all(),
        "comps": db.scalars(select(Competency).order_by(Competency.code)).all()})


@router.post("/interventions")
def add_intervention(request: Request, competency_code: str = Form(""), category: str = Form(""),
                     gap_type: str = Form(...), title_en: str = Form(...), title_mr: str = Form(""),
                     provider: str = Form("CoE"), mode: str = Form("Classroom"), duration_hours: float = Form(4),
                     link: str = Form(""), user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    db.add(Intervention(competency_code=competency_code or None, category=category or None, gap_type=gap_type,
                        title_en=title_en, title_mr=title_mr, provider=provider, mode=mode,
                        duration_hours=duration_hours, link=link))
    db.commit()
    flash(request, "Intervention added to the catalogue.", "ok")
    return RedirectResponse("/cell/interventions", status_code=303)


@router.post("/interventions/{iid}/delete")
def del_intervention(iid: int, request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    iv = db.get(Intervention, iid)
    if iv:
        db.execute(update(DevelopmentPlanItem).where(DevelopmentPlanItem.intervention_id == iid)
                   .values(intervention_id=None))
        db.delete(iv)
        db.commit()
    return RedirectResponse("/cell/interventions", status_code=303)


# ------------------------------------------------------------------ analysis, exports, audit
@router.post("/analysis")
def run_analysis(request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    res = analytics.run_item_analysis(db)
    audit(db, request, user.emp_id, "item_analysis_run", "item", "", **res)
    flash(request, f"Item analysis done: {res['items']} items from {res['attempts']} attempts, {res['flagged']} flagged.", "ok")
    return RedirectResponse("/cell/item-stats", status_code=303)


@router.get("/item-stats")
def item_stats(request: Request, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    items = db.scalars(select(Item).where(Item.stats_n > 0).order_by(Item.p_value)).all()
    return render(request, "cell/item_stats.html", {"user": user, "items": items, "flag": analytics.item_flag})


@router.get("/export/results.xlsx")
def export_results(user: Employee = Depends(require("cell", "hod", "management")), db: Session = Depends(get_db)):
    emps = db.scalars(select(Employee).where(Employee.active.is_(True), Employee.role_family_code.is_not(None))).all()
    if user.has("hod") and not user.has("cell", "management"):
        emps = [e for e in emps if e.department == user.department]
    profiles = analytics.profiles_for(db, emps)
    wb = Workbook()
    ws = wb.active
    ws.title = "Competency results"
    _header(ws, ["Employee ID", "Name", "Designation", "Department", "Location", "Role family", "Grade",
                 "Competency", "Competency name", "Category", "Safety-critical", "Required level", "Baseline (T0)",
                 "Latest score", "Latest cycle", "Attained level", "Gap", "Status", "Readiness", "Competency Index %"])
    for e in emps:
        p = profiles[e.id]
        for r in p.rows:
            ws.append([e.emp_id, e.name_en, e.designation, e.department, e.location, e.role_family_code, e.grade_code,
                       r.code, r.name_en, r.category, "Y" if r.safety else "N", r.required, r.baseline, r.latest,
                       r.latest_cycle, r.level or None, r.gap if r.latest is not None else None, r.status,
                       p.readiness, p.index])
    ws2 = wb.create_sheet("Training needs (TNA)")
    _header(ws2, ["Competency", "Name", "Safety gates", "Priority (2+ levels)", "Developing (1 level)", "Total with gap"], 22)
    agg: dict[str, list] = {}
    for e in emps:
        for r in profiles[e.id].rows:
            if r.gap > 0:
                a = agg.setdefault(r.code, [r.name_en, 0, 0, 0])
                a[{"Safety gate": 1, "Priority": 2, "Developing": 3}[r.status]] += 1
    for code, a in sorted(agg.items(), key=lambda kv: -(kv[1][1] + kv[1][2] + kv[1][3])):
        ws2.append([code, a[0], a[1], a[2], a[3], a[1] + a[2] + a[3]])
    ws3 = wb.create_sheet("Response times")
    _header(ws3, ["Attempt", "Employee ID", "Name", "Test", "Cycle", "Question", "Competency", "Position",
                  "Seconds spent", "Rapid limit (s)", "Rapid answer", "Answered", "Correct"], 16)
    atts = db.scalars(select(Attempt).options(selectinload(Attempt.employee), selectinload(Attempt.test),
                                              selectinload(Attempt.items).selectinload(AttemptItem.item))
                      .where(Attempt.kind != "PRACTICE", Attempt.status.in_(["scored", "flagged", "pending_manual"]),
                             Attempt.employee_id.in_([e.id for e in emps]))).all()
    norms = timing.item_norms(db, list({ai.item_id for a in atts for ai in a.items}))
    for a in atts:
        per = timing.analyse(db, a, norms)["per"]
        for ai in a.items:
            sec, lim, rapid = per[ai.id]
            ws3.append([a.id, a.employee.emp_id, a.employee.name_en, a.test.code, a.kind, ai.item.code,
                        ai.item.competency_code, ai.position, sec, lim, "Y" if rapid else "",
                        "Y" if ai.response else "N", "Y" if ai.is_correct else ("" if ai.is_correct is None else "N")])
    return _xlsx_response(wb, f"competency_results_{utcnow():%Y%m%d}.xlsx")


@router.get("/audit")
def audit_log(request: Request, q: str = "", page: int = 1, user: Employee = Depends(CELL), db: Session = Depends(get_db)):
    stmt = select(AuditLog)
    if q:
        stmt = stmt.where((AuditLog.actor.ilike(f"%{q}%")) | (AuditLog.action.ilike(f"%{q}%"))
                          | (AuditLog.entity_id.ilike(f"%{q}%")))
    rows = db.scalars(stmt.order_by(AuditLog.at.desc()).offset((page - 1) * 100).limit(100)).all()
    return render(request, "cell/audit.html", {"user": user, "rows": rows, "q": q, "page": page})

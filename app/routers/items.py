"""Question bank: authoring, two-level review, media library, reading passages, preview."""
from __future__ import annotations

import html
import re
from pathlib import Path

from markupsafe import Markup

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import RedirectResponse
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session, selectinload

from ..db import get_db
from ..models import BLOOM, QTYPES, Competency, Employee, Item, ItemOption, Media, Passage, RoleFamily
from ..security import audit, check_csrf, require
from ..services.analytics import item_flag
from ..services.importers import quality_warnings, validate_item
from ..web import flash, render
from .testing import save_upload

router = APIRouter(dependencies=[Depends(check_csrf)])
AUTHOR = require("sme", "reviewer", "cell")


@router.get("/items")
def item_list(request: Request, q: str = "", competency: str = "", status: str = "", qtype: str = "",
              pool: str = "", mine: int = 0, page: int = 1, user: Employee = Depends(AUTHOR),
              db: Session = Depends(get_db)):
    stmt = select(Item).options(selectinload(Item.competency))
    if q:
        stmt = stmt.where(or_(Item.code.ilike(f"%{q}%"), Item.stem_en.ilike(f"%{q}%"), Item.stem_mr.ilike(f"%{q}%")))
    if competency:
        stmt = stmt.where(Item.competency_code == competency)
    if status:
        stmt = stmt.where(Item.status == status)
    if qtype:
        stmt = stmt.where(Item.qtype == qtype)
    if pool:
        stmt = stmt.where(Item.pool == pool)
    if mine:
        stmt = stmt.where(Item.author_emp_id == user.emp_id)
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    items = db.scalars(stmt.options(selectinload(Item.options)).order_by(Item.code).offset((page - 1) * 50).limit(50)).all()
    counts = dict(db.execute(select(Item.status, func.count()).group_by(Item.status)).all())
    review_n = db.scalar(select(func.count(Item.id)).where(Item.status == "review",
                                                           Item.author_emp_id != user.emp_id))
    return render(request, "items.html", {
        "user": user, "items": items, "total": total, "page": page, "counts": counts, "review_n": review_n,
        "comps": db.scalars(select(Competency).order_by(Competency.code)).all(), "qtypes": QTYPES,
        "f": {"q": q, "competency": competency, "status": status, "qtype": qtype, "pool": pool, "mine": mine},
        "item_flag": item_flag, "quality": {i.id: quality_warnings(i) for i in items},
    })


def _editor(request: Request, db: Session, user: Employee, item: Item | None, errors: list[str] | None = None):
    return render(request, "item_edit.html", {
        "user": user, "item": item, "errors": errors or [], "quality": quality_warnings(item) if item else [],
        "comps": db.scalars(select(Competency).order_by(Competency.code)).all(),
        "families": db.scalars(select(RoleFamily)).all(), "qtypes": QTYPES, "bloom": BLOOM,
        "media": db.scalars(select(Media).where(Media.kind.in_(["audio", "video", "image"]))
                            .order_by(Media.id.desc()).limit(300)).all(),
        "passages": db.scalars(select(Passage).order_by(Passage.code)).all(),
    })


@router.get("/items/new")
def item_new(request: Request, user: Employee = Depends(AUTHOR), db: Session = Depends(get_db)):
    return _editor(request, db, user, None)


@router.get("/items/{item_id}/edit")
def item_edit(item_id: int, request: Request, user: Employee = Depends(AUTHOR), db: Session = Depends(get_db)):
    item = db.get(Item, item_id)
    if not item:
        raise HTTPException(404)
    return _editor(request, db, user, item)


def _f(v, default=None, cast=float):
    try:
        return cast(v) if v not in (None, "") else default
    except ValueError:
        return default


@router.post("/items/save")
async def item_save(request: Request, user: Employee = Depends(AUTHOR), db: Session = Depends(get_db)):
    form = await request.form()
    item_id = _f(form.get("id"), None, int)
    item = db.get(Item, item_id) if item_id else None
    if item and item.status == "approved" and not user.has("cell"):
        raise HTTPException(403, "Approved items can only be changed by the Assessment Cell")
    code = (form.get("code") or "").strip().upper()
    if not code:
        comp = form.get("competency_code") or "X"
        n = db.scalar(select(func.count(Item.id)).where(Item.competency_code == comp)) or 0
        code = f"{comp}-{n + 1:04d}"
        while db.scalar(select(Item.id).where(Item.code == code)):
            n += 1
            code = f"{comp}-{n + 1:04d}"
    dup = db.scalar(select(Item).where(Item.code == code))
    if dup and (not item or dup.id != item.id):
        return _editor(request, db, user, item, [f"Item ID {code} already exists"])
    if item is None:
        item = Item(code=code, author_emp_id=user.emp_id, stem_en="")
        db.add(item)
    else:
        if item.status == "approved":
            item.version += 1
        item.options.clear()
        db.flush()
    item.code = code
    item.competency_code = form.get("competency_code")
    item.qtype = form.get("qtype") if form.get("qtype") in QTYPES else "single"
    item.role_families = form.getlist("role_families") or ["ALL"]
    if "ALL" in item.role_families:
        item.role_families = ["ALL"]
    item.target_level = _f(form.get("target_level"), 3, int)
    item.bloom = form.get("bloom") if form.get("bloom") in BLOOM else "Understand"
    item.pool = form.get("pool") if form.get("pool") in ("exam", "practice", "both") else "exam"
    for fld in ("stem_en", "stem_mr", "rationale_en", "rationale_mr", "rubric_en", "rubric_mr", "source_ref"):
        setattr(item, fld, (form.get(fld) or "").strip())
    item.media_id = _f(form.get("media_id"), None, int)
    item.passage_id = _f(form.get("passage_id"), None, int)
    item.passage_order = _f(form.get("passage_order"), 0, int)
    item.marks = _f(form.get("marks"), 1.0)
    item.negative_marks = _f(form.get("negative_marks"), 0.0)
    item.partial_credit = bool(form.get("partial_credit"))
    item.shuffle_options = bool(form.get("shuffle_options"))
    item.answer_numeric = _f(form.get("answer_numeric"))
    item.numeric_tolerance = _f(form.get("numeric_tolerance"), 0.0)
    item.accepted_answers = [a.strip() for a in (form.get("accepted_answers") or "").splitlines() if a.strip()]
    item.needs_sme_validation = bool(form.get("needs_sme_validation"))
    up = form.get("media_file")
    if up is not None and getattr(up, "filename", ""):
        kind = (up.content_type or "").split("/")[0]
        if kind in ("audio", "video", "image"):
            m = await save_upload(db, up, kind, user.emp_id)
            item.media_id = m.id
    en, mr = form.getlist("opt_en"), form.getlist("opt_mr")
    men, mmr = form.getlist("match_en"), form.getlist("match_mr")
    weights, seqs = form.getlist("opt_weight"), form.getlist("opt_seq")
    correct = set(form.getlist("opt_correct"))
    for i, text in enumerate(en):
        if not text.strip():
            continue
        o = ItemOption(ord=i, text_en=text.strip(), text_mr=(mr[i] if i < len(mr) else "").strip(),
                       is_correct=str(i) in correct)
        if item.qtype == "sjt":
            o.weight = _f(weights[i] if i < len(weights) else None, 0.0)
            o.is_correct = (o.weight or 0) >= 0.999
        if item.qtype == "sequence":
            o.seq_position = _f(seqs[i] if i < len(seqs) else None, i + 1, int)
        if item.qtype == "match":
            o.match_en = (men[i] if i < len(men) else "").strip()
            o.match_mr = (mmr[i] if i < len(mmr) else "").strip()
        item.options.append(o)
    action = form.get("action", "save")
    problems = validate_item(item) if item.stem_en else ["English question text is required"]
    if action == "submit" and not problems:
        item.status = "review"
    elif item.status not in ("review",) or problems:
        item.status = "draft"
    db.commit()
    audit(db, request, user.emp_id, f"item_{action}", "item", item.code, version=item.version)
    quality = quality_warnings(item)
    if problems:
        flash(request, "Saved as draft. Fix before review: " + "; ".join(problems), "error")
    else:
        flash(request, "Submitted for review." if action == "submit" else "Saved.", "ok")
    return RedirectResponse(f"/items/{item.id}/edit", status_code=303)


@router.get("/items/{item_id}/preview")
def item_preview(item_id: int, request: Request, user: Employee = Depends(AUTHOR), db: Session = Depends(get_db)):
    item = db.scalar(select(Item).options(selectinload(Item.options), selectinload(Item.media),
                                          selectinload(Item.passage)).where(Item.id == item_id))
    if not item:
        raise HTTPException(404)
    return render(request, "item_preview.html", {"user": user, "item": item, "flag": item_flag(item),
                                                 "quality": quality_warnings(item)})


@router.get("/items/review")
def review_queue(request: Request, user: Employee = Depends(require("reviewer", "cell")), db: Session = Depends(get_db)):
    items = db.scalars(select(Item).options(selectinload(Item.options), selectinload(Item.media),
                                            selectinload(Item.passage), selectinload(Item.competency))
                       .where(Item.status == "review").order_by(Item.updated_at).limit(100)).all()
    items = [i for i in items if i.author_emp_id != user.emp_id or user.has("superadmin")]
    return render(request, "review.html", {"user": user, "items": items,
                                           "quality": {i.id: quality_warnings(i) for i in items}})


@router.post("/items/{item_id}/review")
def review_decision(item_id: int, request: Request, decision: str = Form(...), comment: str = Form(""),
                    user: Employee = Depends(require("reviewer", "cell")), db: Session = Depends(get_db)):
    item = db.get(Item, item_id)
    if not item:
        raise HTTPException(404)
    if item.author_emp_id == user.emp_id and not user.has("superadmin"):
        raise HTTPException(403, "Authors cannot approve their own items")
    if decision == "approve":
        if validate_item(item):
            flash(request, "Item has errors and cannot be approved.", "error")
            return RedirectResponse("/items/review", status_code=303)
        item.status = "approved"
    elif decision == "return":
        item.status = "draft"
    elif decision == "retire" and user.has("cell"):
        item.status = "retired"
    item.reviewer_emp_id = user.emp_id
    item.review_comment = comment[:2000]
    db.commit()
    audit(db, request, user.emp_id, f"item_{decision}", "item", item.code, comment=comment[:200])
    return RedirectResponse(request.headers.get("referer") or "/items/review", status_code=303)


_GUIDE = Path(__file__).resolve().parent.parent / "content" / "question_writing_guide.md"


def _inline(text: str) -> str:
    text = html.escape(text, quote=False)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    return re.sub(r"(?<![*\w])\*(?!\s)(.+?)(?<!\s)\*(?![*\w])", r"<i>\1</i>", text)


def guide_html(md: str) -> str:
    """Very small Markdown subset (headings, paragraphs, lists, tables, bold/italic) – no extra dependency."""
    out, para, lst, table = [], [], None, []

    def flush():
        nonlocal para, lst, table
        if para:
            out.append("<p>" + "<br>".join(_inline(x) for x in para) + "</p>")
        if lst:
            tag, rows = lst
            out.append(f"<{tag}>" + "".join(f"<li>{_inline(r)}</li>" for r in rows) + f"</{tag}>")
        if table:
            rows = [[c.strip() for c in r.strip().strip("|").split("|")] for r in table if not re.match(r"^\|[-| :]+\|$", r.strip())]
            head, body = rows[0], rows[1:]
            out.append('<table class="tbl"><thead><tr>' + "".join(f"<th>{_inline(c)}</th>" for c in head) + "</tr></thead><tbody>"
                       + "".join("<tr>" + "".join(f"<td>{_inline(c)}</td>" for c in r) + "</tr>" for r in body) + "</tbody></table>")
        para, lst, table = [], None, []

    for line in md.splitlines():
        s = line.rstrip()
        if not s.strip():
            flush(); continue
        m = re.match(r"^(#{1,3})\s+(.*)", s)
        if m:
            flush(); n = len(m.group(1)) + 1
            out.append(f"<h{n}>{_inline(m.group(2))}</h{n}>"); continue
        if s.startswith("|"):
            if para or lst: flush()
            table.append(s); continue
        m = re.match(r"^(\d+)\.\s+(.*)", s) or re.match(r"^[-*]\s+(.*)", s)
        if m:
            tag = "ol" if s[0].isdigit() else "ul"
            if para or table or (lst and lst[0] != tag): flush()
            if lst is None: lst = (tag, [])
            lst[1].append(m.group(m.lastindex)); continue
        if lst and s.startswith("   "):
            lst[1][-1] += " " + s.strip(); continue
        if lst or table: flush()
        para.append(s)
    flush()
    return Markup("\n".join(out))


@router.get("/items/guide")
def writing_guide(request: Request, user: Employee = Depends(AUTHOR)):
    md = _GUIDE.read_text(encoding="utf-8") if _GUIDE.exists() else "# Guide not found"
    return render(request, "item_guide.html", {"user": user, "guide": guide_html(md)})


# ------------------------------------------------------------------ media and passages
@router.get("/library")
def library(request: Request, user: Employee = Depends(AUTHOR), db: Session = Depends(get_db)):
    return render(request, "library.html", {
        "user": user,
        "media": db.scalars(select(Media).where(Media.kind.in_(["audio", "video", "image"]))
                            .order_by(Media.id.desc()).limit(500)).all(),
        "passages": db.scalars(select(Passage).order_by(Passage.code)).all(),
    })


@router.post("/library/upload")
async def library_upload(request: Request, file: UploadFile = File(...), user: Employee = Depends(AUTHOR),
                         db: Session = Depends(get_db)):
    kind = (file.content_type or "").split("/")[0]
    if kind not in ("audio", "video", "image"):
        flash(request, "Only audio, video and image files can be uploaded.", "error")
        return RedirectResponse("/library", status_code=303)
    m = await save_upload(db, file, kind, user.emp_id)
    audit(db, request, user.emp_id, "media_uploaded", "media", m.id, filename=m.filename)
    flash(request, f"Uploaded {m.filename}", "ok")
    return RedirectResponse("/library", status_code=303)


@router.post("/library/passage")
def passage_save(request: Request, code: str = Form(...), title_en: str = Form(""), title_mr: str = Form(""),
                 body_en: str = Form(...), body_mr: str = Form(""), media_id: str = Form(""),
                 user: Employee = Depends(AUTHOR), db: Session = Depends(get_db)):
    code = code.strip().upper()
    p = db.scalar(select(Passage).where(Passage.code == code)) or Passage(code=code)
    p.title_en, p.title_mr, p.body_en, p.body_mr = title_en, title_mr, body_en, body_mr
    p.media_id = int(media_id) if media_id.isdigit() else None
    db.add(p)
    db.commit()
    audit(db, request, user.emp_id, "passage_saved", "passage", code)
    flash(request, f"Passage {code} saved.", "ok")
    return RedirectResponse("/library", status_code=303)

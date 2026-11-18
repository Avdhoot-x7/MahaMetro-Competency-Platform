"""Candidate side: start a test, answer with autosave, proctoring events, submit, results, media."""
from __future__ import annotations

import mimetypes
import secrets
from datetime import timedelta

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse, JSONResponse, RedirectResponse
from starlette.requests import ClientDisconnect
from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from ..config import get_settings
from ..db import get_db
from ..models import Attempt, AttemptItem, Employee, Item, Media, ProctorEvent, Test, utcnow
from ..security import audit, can_view_employee, check_csrf, current_user
from ..services import engine, prescription, scoring
from ..web import flash, lang_of, render

router = APIRouter(dependencies=[Depends(check_csrf)])
settings = get_settings()

ALLOWED_MIME = {
    "audio": {"audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/ogg", "audio/webm", "audio/mp4", "audio/aac"},
    "video": {"video/mp4", "video/webm", "video/ogg"},
    "image": {"image/png", "image/jpeg", "image/gif", "image/webp", "image/svg+xml"},
}


async def save_upload(db: Session, up: UploadFile, kind: str, owner: str, restricted: bool = False) -> Media:
    data = await up.read()
    if len(data) > settings.max_upload_mb * 1024 * 1024:
        raise HTTPException(413, "File too large")
    mime = (up.content_type or mimetypes.guess_type(up.filename or "")[0] or "").split(";")[0]
    if kind in ALLOWED_MIME and mime not in ALLOWED_MIME[kind]:
        raise HTTPException(415, f"File type {mime} not allowed for {kind}")
    ext = mimetypes.guess_extension(mime) or ""
    stored = f"{kind}-{secrets.token_hex(12)}{ext}"
    settings.media_dir.mkdir(parents=True, exist_ok=True)
    (settings.media_dir / stored).write_bytes(data)
    m = Media(kind=kind, filename=(up.filename or stored)[:200], stored_name=stored, mime=mime, size=len(data),
              restricted=restricted, owner_emp_id=owner)
    db.add(m)
    db.commit()
    return m


@router.get("/media/{media_id}")
def get_media(media_id: int, request: Request, user: Employee = Depends(current_user), db: Session = Depends(get_db)):
    m = db.get(Media, media_id)
    if not m:
        raise HTTPException(404)
    if m.restricted and not (user.has("cell") or m.owner_emp_id == user.emp_id):
        raise HTTPException(403)
    if not m.restricted and not user.has("sme", "reviewer", "cell") and not _candidate_may_see(db, user, m):
        raise HTTPException(403)
    path = settings.media_dir / m.stored_name
    if not path.exists():
        raise HTTPException(404)
    return FileResponse(path, media_type=m.mime, headers={"Cache-Control": "private, max-age=3600",
                                                          "Content-Disposition": "inline"})


def _candidate_may_see(db: Session, user: Employee, m: Media) -> bool:
    """Question media is only served to candidates who have that question in one of their attempts."""
    from ..models import Passage

    q = (select(AttemptItem.id).join(Attempt).join(Item, Item.id == AttemptItem.item_id)
         .outerjoin(Passage, Passage.id == Item.passage_id)
         .where(Attempt.employee_id == user.id, (Item.media_id == m.id) | (Passage.media_id == m.id)).limit(1))
    return db.scalar(q) is not None


# ------------------------------------------------------------------ start
def _owned_attempt(db: Session, attempt_id: int, user: Employee) -> Attempt:
    att = db.scalar(select(Attempt).options(selectinload(Attempt.test)).where(Attempt.id == attempt_id))
    if not att or att.employee_id != user.id:
        raise HTTPException(404)
    return att


@router.get("/tests/{test_id}/start")
def start_page(test_id: int, request: Request, user: Employee = Depends(current_user), db: Session = Depends(get_db)):
    test = db.get(Test, test_id)
    if not test:
        raise HTTPException(404)
    running = engine.active_attempt(db, test, user)
    if running:
        return RedirectResponse(f"/attempt/{running.id}", status_code=303)
    ok, reason = engine.can_start(db, test, user)
    return render(request, "start.html", {"user": user, "test": test, "ok": ok, "reason": reason,
                                          "used": engine.attempts_used(db, test, user)})


@router.post("/tests/{test_id}/start")
def start(test_id: int, request: Request, lang: str = Form("en"), user: Employee = Depends(current_user),
          db: Session = Depends(get_db)):
    test = db.get(Test, test_id)
    if not test:
        raise HTTPException(404)
    running = engine.active_attempt(db, test, user)
    if running:
        return RedirectResponse(f"/attempt/{running.id}", status_code=303)
    try:
        att = engine.start_attempt(db, test, user, lang if lang in ("en", "mr") else "en",
                                   request.headers.get("user-agent", ""))
    except engine.AssemblyError as e:
        flash(request, {"no_items": "No approved questions are available for your role yet. Please contact the "
                        "CoE Assessment Cell.", "not_eligible": "This test is not open for you.",
                        "no_attempts_left": "You have used all attempts for this test.",
                        "no_role_family": "Your role family is not set. Please contact the CoE Assessment Cell."
                        }.get(str(e), str(e)), "error")
        return RedirectResponse(f"/tests/{test_id}/start", status_code=303)
    request.session["lang"] = att.lang
    audit(db, request, user.emp_id, "attempt_started", "attempt", att.id, test=test.code, seed=att.seed)
    return RedirectResponse(f"/attempt/{att.id}", status_code=303)


def _expire_if_due(db: Session, att: Attempt) -> bool:
    if att.status == "in_progress" and utcnow() > att.deadline_at + timedelta(seconds=20):
        scoring.finalize_attempt(db, att, auto=True)
        return True
    return False


def _serialise(ai: AttemptItem, lang: str) -> dict:
    """What the browser receives: never keys, weights or correct positions."""
    it = ai.item

    def tx(o, f):
        return (getattr(o, f + "_mr") if lang == "mr" and getattr(o, f + "_mr") else getattr(o, f + "_en"))

    opts = {o.id: o for o in it.options}
    order = ai.option_order.get("options") or list(opts)
    d = {
        "id": ai.id, "pos": ai.position, "qtype": it.qtype, "marks": ai.max_score,
        "stem": {"en": it.stem_en, "mr": it.stem_mr},
        "options": [{"id": oid, "en": opts[oid].text_en, "mr": opts[oid].text_mr} for oid in order if oid in opts],
        "response": ai.response, "flagged": ai.flagged_for_review, "time": ai.time_spent_s or 0,
        "media": ({"id": it.media.id, "kind": it.media.kind} if it.media else None),
        "competency": it.competency_code,
    }
    if it.qtype == "match":
        right = ai.option_order.get("right") or list(opts)
        d["right"] = [{"id": oid, "en": opts[oid].match_en, "mr": opts[oid].match_mr} for oid in right if oid in opts]
    if it.passage:
        p = it.passage
        d["passage"] = {"id": p.id, "title": {"en": p.title_en, "mr": p.title_mr},
                        "body": {"en": p.body_en, "mr": p.body_mr},
                        "media": ({"id": p.media.id, "kind": p.media.kind} if p.media else None)}
    return d


@router.get("/attempt/{attempt_id}")
def player(attempt_id: int, request: Request, user: Employee = Depends(current_user), db: Session = Depends(get_db)):
    att = _owned_attempt(db, attempt_id, user)
    if _expire_if_due(db, att) or att.status != "in_progress":
        return RedirectResponse(f"/attempt/{att.id}/result", status_code=303)
    ais = db.scalars(select(AttemptItem).options(
        selectinload(AttemptItem.item).selectinload(Item.options),
        selectinload(AttemptItem.item).selectinload(Item.media),
        selectinload(AttemptItem.item).selectinload(Item.passage),
    ).where(AttemptItem.attempt_id == att.id).order_by(AttemptItem.position)).all()
    remaining = max(0, int((att.deadline_at - utcnow()).total_seconds()))
    db.add(ProctorEvent(attempt_id=att.id, kind="page_load", detail=request.headers.get("user-agent", "")[:300]))
    db.commit()
    data = {
        "attempt": att.id, "remaining": remaining, "lang": att.lang, "practice": att.test.is_practice,
        "allow_back": att.test.allow_back, "autosave": settings.autosave_seconds,
        "proctoring": att.test.proctoring or {}, "questions": [_serialise(ai, att.lang) for ai in ais],
    }
    return render(request, "player.html", {"user": user, "att": att, "test": att.test, "data": data})


@router.post("/api/attempt/{attempt_id}/answer")
async def save_answer(attempt_id: int, request: Request, user: Employee = Depends(current_user),
                      db: Session = Depends(get_db)):
    att = _owned_attempt(db, attempt_id, user)
    if att.status != "in_progress" or utcnow() > att.deadline_at + timedelta(seconds=20):
        _expire_if_due(db, att)
        return JSONResponse({"ok": False, "closed": True}, status_code=409)
    try:
        body = await request.json()
    except (ClientDisconnect, ValueError):  # page closed or reloaded mid-request
        return JSONResponse({"ok": False}, status_code=400)
    answers = body.get("answers") or [body]
    saved = 0
    for a in answers:
        ai = db.get(AttemptItem, int(a.get("id", 0)))
        if not ai or ai.attempt_id != att.id:
            continue
        if "response" in a:
            r = a["response"]
            ai.response = r if isinstance(r, dict) and len(str(r)) < 20000 else None
            ai.answered_at = utcnow()
        if "flagged" in a:
            ai.flagged_for_review = bool(a["flagged"])
        if "time" in a:
            ai.time_spent_s = min(int(a["time"] or 0), 24 * 3600)
        saved += 1
    db.commit()
    return {"ok": True, "saved": saved, "remaining": max(0, int((att.deadline_at - utcnow()).total_seconds()))}


@router.post("/api/attempt/{attempt_id}/event")
async def proctor_event(attempt_id: int, request: Request, user: Employee = Depends(current_user),
                        db: Session = Depends(get_db)):
    att = _owned_attempt(db, attempt_id, user)
    if att.status != "in_progress":
        return {"ok": False}
    try:
        body = await request.json()
    except (ClientDisconnect, ValueError):  # page closed or reloaded mid-request
        return JSONResponse({"ok": False}, status_code=400)
    kind = str(body.get("kind", ""))[:30]
    if kind not in ("tab_hidden", "fullscreen_exit", "copy", "paste", "context_menu", "resume", "blur", "devtools"):
        return {"ok": False}
    db.add(ProctorEvent(attempt_id=att.id, kind=kind, detail=str(body.get("detail", ""))[:300]))
    if kind in ("tab_hidden",):
        att.tab_switches += 1
    db.commit()
    limit = (att.test.proctoring or {}).get("max_tab_switches")
    return {"ok": True, "tab_switches": att.tab_switches, "limit": limit}


@router.post("/api/attempt/{attempt_id}/snapshot")
async def snapshot(attempt_id: int, request: Request, file: UploadFile = File(...),
                   user: Employee = Depends(current_user), db: Session = Depends(get_db)):
    att = _owned_attempt(db, attempt_id, user)
    if att.status != "in_progress":
        return {"ok": False}
    m = await save_upload(db, file, "image", user.emp_id, restricted=True)
    m.kind = "webcam"
    db.add(ProctorEvent(attempt_id=att.id, kind="snapshot", media_id=m.id))
    db.commit()
    return {"ok": True}


@router.post("/api/attempt/{attempt_id}/audio/{ai_id}")
async def audio_answer(attempt_id: int, ai_id: int, request: Request, file: UploadFile = File(...),
                       user: Employee = Depends(current_user), db: Session = Depends(get_db)):
    att = _owned_attempt(db, attempt_id, user)
    ai = db.get(AttemptItem, ai_id)
    if att.status != "in_progress" or not ai or ai.attempt_id != att.id:
        raise HTTPException(409, "closed")
    m = await save_upload(db, file, "audio", user.emp_id, restricted=True)
    m.kind = "response"
    ai.response = {"media_id": m.id}
    ai.answered_at = utcnow()
    db.commit()
    return {"ok": True, "media_id": m.id}


@router.post("/attempt/{attempt_id}/submit")
def submit(attempt_id: int, request: Request, user: Employee = Depends(current_user), db: Session = Depends(get_db)):
    att = _owned_attempt(db, attempt_id, user)
    if att.status == "in_progress":
        scoring.finalize_attempt(db, att)
        audit(db, request, user.emp_id, "attempt_submitted", "attempt", att.id)
    return RedirectResponse(f"/attempt/{att.id}/result", status_code=303)


@router.get("/attempt/{attempt_id}/result")
def result(attempt_id: int, request: Request, user: Employee = Depends(current_user), db: Session = Depends(get_db)):
    att = db.scalar(select(Attempt).options(
        selectinload(Attempt.items).selectinload(AttemptItem.item).selectinload(Item.options),
        selectinload(Attempt.test), selectinload(Attempt.employee)).where(Attempt.id == attempt_id))
    if not att or not can_view_employee(user, att.employee):
        raise HTTPException(404)
    if att.status == "in_progress" and not _expire_if_due(db, att):
        return RedirectResponse(f"/attempt/{att.id}", status_code=303)
    db.refresh(att)
    profile = recs = None
    if att.kind == "PRACTICE":
        profile = scoring.practice_profile(db, att.employee, att)
        recs = prescription.recommendations(db, profile)
    from ..models import Competency

    comps = {c.code: c for c in db.scalars(select(Competency)).all()}
    return render(request, "result.html", {"user": user, "att": att, "test": att.test, "profile": profile,
                                           "recs": recs, "comps": comps, "lang": lang_of(request)})

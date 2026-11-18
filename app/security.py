"""Session handling, role checks, CSRF protection and audit logging."""
from __future__ import annotations

import secrets

import bcrypt
from fastapi import Depends, HTTPException, Request
from sqlalchemy.orm import Session

from .db import get_db
from .models import AuditLog, Employee


class LoginRequired(Exception):
    pass


def hash_password(pw: str) -> str:
    return bcrypt.hashpw(pw.encode(), bcrypt.gensalt(rounds=12)).decode()


def verify_password(pw: str, hashed: str | None) -> bool:
    if not hashed:
        return False
    try:
        return bcrypt.checkpw(pw.encode(), hashed.encode())
    except ValueError:
        return False


def csrf_token(request: Request) -> str:
    tok = request.session.get("csrf")
    if not tok:
        tok = secrets.token_urlsafe(24)
        request.session["csrf"] = tok
    return tok


async def check_csrf(request: Request) -> None:
    if request.method in ("GET", "HEAD", "OPTIONS"):
        return
    sent = request.headers.get("x-csrf-token")
    if not sent:
        ctype = request.headers.get("content-type", "")
        if "form" in ctype:
            form = await request.form()
            sent = form.get("csrf")
    if not sent or not secrets.compare_digest(str(sent), request.session.get("csrf", "")):
        raise HTTPException(status_code=403, detail="CSRF check failed – reload the page and try again")


def login_user(request: Request, emp: Employee, method: str) -> None:
    request.session.clear()
    request.session["uid"] = emp.id
    request.session["auth"] = method
    request.session["lang"] = emp.preferred_lang or "en"
    csrf_token(request)


def current_user_optional(request: Request, db: Session = Depends(get_db)) -> Employee | None:
    uid = request.session.get("uid")
    if not uid:
        return None
    emp = db.get(Employee, uid)
    if not emp or not emp.active:
        request.session.clear()
        return None
    return emp


def current_user(request: Request, db: Session = Depends(get_db)) -> Employee:
    emp = current_user_optional(request, db)
    if emp is None:
        raise LoginRequired()
    return emp


def require(*roles: str):
    def dep(user: Employee = Depends(current_user)) -> Employee:
        if not user.has(*roles):
            raise HTTPException(status_code=403, detail="You do not have access to this page")
        return user
    return dep


def audit(db: Session, request: Request | None, actor: str, action: str, entity: str = "",
          entity_id: str | int = "", **detail) -> None:
    ip = ""
    if request is not None:
        ip = request.headers.get("x-forwarded-for", request.client.host if request.client else "")[:60]
    db.add(AuditLog(actor=actor, action=action, entity=entity, entity_id=str(entity_id), detail=detail, ip=ip))
    db.commit()


def can_view_employee(viewer: Employee, emp: Employee) -> bool:
    """Individual results: the employee, their reporting officer, the HoD of the department, the Cell."""
    if viewer.id == emp.id or viewer.has("cell"):
        return True
    if viewer.has("supervisor") and emp.reporting_officer == viewer.emp_id:
        return True
    if viewer.has("hod") and viewer.department and viewer.department == emp.department:
        return True
    return False

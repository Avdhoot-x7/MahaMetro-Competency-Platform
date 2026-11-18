"""Login: Keycloak/OIDC single sign-on, signed hand-off token from the Maha Metro portal,
local break-glass passwords, and (demo only) a one-click role picker."""
from __future__ import annotations

import logging
from pathlib import Path

import jwt
from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..config import get_settings
from ..db import get_db
from ..i18n import LANGUAGES
from ..models import Employee, utcnow
from ..security import audit, check_csrf, current_user, login_user, verify_password
from ..web import flash, render

log = logging.getLogger("auth")
router = APIRouter()
settings = get_settings()

_oauth = None


def oauth_client():
    global _oauth
    if _oauth is None and "oidc" in settings.auth_modes and settings.oidc_issuer:
        from authlib.integrations.starlette_client import OAuth

        _oauth = OAuth()
        _oauth.register(
            "sso", client_id=settings.oidc_client_id, client_secret=settings.oidc_client_secret,
            server_metadata_url=settings.oidc_issuer.rstrip("/") + "/.well-known/openid-configuration",
            client_kwargs={"scope": "openid profile email"},
        )
    return _oauth


def _safe_next(nxt: str | None) -> str:
    return nxt if nxt and nxt.startswith("/") and not nxt.startswith("//") else "/me"


def _after_login(request: Request, db: Session, emp: Employee, method: str, nxt: str | None):
    emp.last_login = utcnow()
    db.commit()
    login_user(request, emp, method)
    audit(db, request, emp.emp_id, "login", "employee", emp.emp_id, method=method)
    if not emp.consent_at:
        return RedirectResponse(f"/consent?next={_safe_next(nxt)}", status_code=303)
    return RedirectResponse(_safe_next(nxt), status_code=303)


@router.get("/login")
def login_page(request: Request, next: str = "/me", db: Session = Depends(get_db)):
    demo_users = []
    if settings.demo_mode:
        demo_users = db.scalars(select(Employee).where(Employee.emp_id.like("DEMO-%")).order_by(Employee.emp_id)).all()
    return render(request, "login.html", {"next": next, "modes": settings.auth_modes, "demo_users": demo_users,
                                          "oidc_ready": bool(oauth_client())})


@router.post("/login", dependencies=[Depends(check_csrf)])
def login_local(request: Request, emp_id: str = Form(...), password: str = Form(...), next: str = Form("/me"),
                db: Session = Depends(get_db)):
    if "local" not in settings.auth_modes:
        flash(request, "Password login is disabled. Use single sign-on.", "error")
        return RedirectResponse("/login", status_code=303)
    emp = db.scalar(select(Employee).where(Employee.emp_id == emp_id.strip()))
    if not emp or not emp.active or not verify_password(password, emp.password_hash):
        audit(db, request, emp_id[:30], "login_failed", "employee", emp_id[:30])
        flash(request, "Employee ID or password is incorrect.", "error")
        return RedirectResponse(f"/login?next={_safe_next(next)}", status_code=303)
    if settings.is_production and not emp.has(*settings.local_login_roles):
        flash(request, "Please sign in with single sign-on.", "error")
        return RedirectResponse("/login", status_code=303)
    return _after_login(request, db, emp, "password", next)


@router.post("/login/demo", dependencies=[Depends(check_csrf)])
def login_demo(request: Request, emp_id: str = Form(...), db: Session = Depends(get_db)):
    if not settings.demo_mode or settings.is_production:
        return RedirectResponse("/login", status_code=303)
    emp = db.scalar(select(Employee).where(Employee.emp_id == emp_id))
    if not emp:
        return RedirectResponse("/login", status_code=303)
    return _after_login(request, db, emp, "demo", "/me")


@router.get("/auth/oidc")
async def oidc_start(request: Request, next: str = "/me"):
    client = oauth_client()
    if not client:
        flash(request, "Single sign-on is not configured yet.", "error")
        return RedirectResponse("/login", status_code=303)
    request.session["next"] = _safe_next(next)
    redirect_uri = settings.oidc_redirect_uri or str(request.url_for("oidc_callback"))
    return await client.sso.authorize_redirect(request, redirect_uri)


def _emp_from_claims(db: Session, claims: dict) -> Employee | None:
    emp_id = str(claims.get(settings.oidc_emp_id_claim) or claims.get(settings.oidc_fallback_claim) or "").strip()
    if not emp_id:
        return None
    emp = db.scalar(select(Employee).where(Employee.emp_id.ilike(emp_id)))
    if emp is None and settings.oidc_auto_create:
        emp = Employee(emp_id=emp_id, name_en=claims.get("name") or emp_id, email=claims.get("email", ""),
                       roles=["employee"], source="sso")
        db.add(emp)
        db.commit()
    if emp is not None and settings.oidc_role_map:
        groups = claims.get(settings.oidc_roles_claim) or []
        if isinstance(groups, str):
            groups = [groups]
        groups = {g.strip("/").split("/")[-1] for g in groups}
        mapped = {app_role for idp, app_role in settings.oidc_role_map.items() if idp in groups}
        if mapped:
            keep = {r for r in (emp.roles or []) if r in ("employee", "supervisor")}
            emp.roles = sorted(keep | mapped | {"employee"})
            db.commit()
    return emp


@router.get("/auth/callback", name="oidc_callback")
async def oidc_callback(request: Request, db: Session = Depends(get_db)):
    client = oauth_client()
    if not client:
        return RedirectResponse("/login", status_code=303)
    try:
        token = await client.sso.authorize_access_token(request)
    except Exception as e:  # noqa: BLE001
        log.warning("OIDC callback failed: %s", e)
        flash(request, "Single sign-on failed. Please try again.", "error")
        return RedirectResponse("/login", status_code=303)
    claims = dict(token.get("userinfo") or {})
    emp = _emp_from_claims(db, claims)
    if not emp or not emp.active:
        audit(db, request, str(claims.get("preferred_username", ""))[:30], "sso_unknown_user")
        return render(request, "error.html", {"code": 403, "detail":
                      "Your account is not in the competency system yet. Please contact the CoE Assessment Cell."},
                      status_code=403)
    return _after_login(request, db, emp, "oidc", request.session.get("next"))


@router.get("/auth/portal")
def portal_handoff(request: Request, token: str, next: str = "/me", db: Session = Depends(get_db)):
    """The Maha Metro portal signs a short-lived JWT (sub = employee ID) and links here."""
    if "portal_jwt" not in settings.auth_modes:
        return RedirectResponse("/login", status_code=303)
    key, algs = settings.portal_jwt_secret, ["HS256"]
    if settings.portal_jwt_public_key_file:
        key, algs = Path(settings.portal_jwt_public_key_file).read_text(), ["RS256", "ES256"]
    try:
        claims = jwt.decode(token, key, algorithms=algs, audience=settings.portal_jwt_audience,
                            issuer=settings.portal_jwt_issuer or None,
                            options={"require": ["exp", "iat"], "verify_iss": bool(settings.portal_jwt_issuer)},
                            leeway=30)
    except jwt.PyJWTError as e:
        log.warning("portal token rejected: %s", e)
        return render(request, "error.html", {"code": 401, "detail": "The sign-in link has expired. Open the "
                      "competency portal again from the Maha Metro portal."}, status_code=401)
    if claims.get("exp", 0) - claims.get("iat", 0) > 600:
        return render(request, "error.html", {"code": 401, "detail": "Sign-in token lifetime too long."},
                      status_code=401)
    emp = db.scalar(select(Employee).where(Employee.emp_id == str(claims.get(settings.portal_jwt_emp_claim))))
    if not emp or not emp.active:
        return render(request, "error.html", {"code": 403, "detail":
                      "Your account is not in the competency system yet. Please contact the CoE Assessment Cell."},
                      status_code=403)
    return _after_login(request, db, emp, "portal_jwt", next)


@router.get("/logout")
def logout(request: Request):
    request.session.clear()
    return RedirectResponse("/login", status_code=303)


@router.get("/lang/{code}")
def set_lang(request: Request, code: str, next: str = "/me", db: Session = Depends(get_db)):
    if code in LANGUAGES:
        request.session["lang"] = code
        uid = request.session.get("uid")
        if uid:
            emp = db.get(Employee, uid)
            if emp:
                emp.preferred_lang = code
                db.commit()
    return RedirectResponse(_safe_next(next) if next != "/login" else "/login", status_code=303)


@router.get("/consent")
def consent_page(request: Request, next: str = "/me", user: Employee = Depends(current_user)):
    return render(request, "consent.html", {"user": user, "next": _safe_next(next)})


@router.post("/consent", dependencies=[Depends(check_csrf)])
def consent_accept(request: Request, next: str = Form("/me"), user: Employee = Depends(current_user),
                   db: Session = Depends(get_db)):
    user.consent_at = utcnow()
    db.commit()
    audit(db, request, user.emp_id, "consent_accepted", "employee", user.emp_id)
    return RedirectResponse(_safe_next(next), status_code=303)

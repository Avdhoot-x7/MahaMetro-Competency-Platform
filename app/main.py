"""Application entry point: uvicorn app.main:app"""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.exceptions import HTTPException
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from starlette.middleware.sessions import SessionMiddleware

from .config import get_settings
from .db import init_db
from .routers import auth, cell, dashboards, items, testing
from .security import LoginRequired
from .web import render

settings = get_settings()
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

if settings.is_production and settings.secret_key.startswith("change-me"):
    raise RuntimeError("Set SECRET_KEY before running in production")
if settings.is_production and settings.demo_mode:
    raise RuntimeError("DEMO_MODE must be off in production")

@asynccontextmanager
async def lifespan(_app):
    settings.media_dir.mkdir(parents=True, exist_ok=True)
    init_db()
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan, docs_url=None if settings.is_production else "/api/docs",
              redoc_url=None)
app.add_middleware(SessionMiddleware, secret_key=settings.secret_key, session_cookie="mm_session",
                   max_age=settings.session_max_age, same_site="lax", https_only=settings.https_only_cookies)

app.mount("/static", StaticFiles(directory=str(Path(__file__).parent / "static")), name="static")
for r in (auth.router, dashboards.router, testing.router, items.router, cell.router):
    app.include_router(r)


@app.middleware("http")
async def security_headers(request: Request, call_next):
    resp = await call_next(request)
    resp.headers.setdefault("X-Content-Type-Options", "nosniff")
    resp.headers.setdefault("X-Frame-Options", "DENY")
    resp.headers.setdefault("Referrer-Policy", "same-origin")
    resp.headers.setdefault("Permissions-Policy", "camera=(self), microphone=(self), geolocation=()")
    resp.headers.setdefault(
        "Content-Security-Policy",
        "default-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; "
        "style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; frame-ancestors 'none'",
    )
    return resp


@app.exception_handler(LoginRequired)
async def _login_required(request: Request, exc: LoginRequired):
    if request.url.path.startswith("/api/"):
        return JSONResponse({"detail": "login required"}, status_code=401)
    return RedirectResponse(f"/login?next={request.url.path}", status_code=303)


@app.exception_handler(HTTPException)
async def _http_error(request: Request, exc: HTTPException):
    if request.url.path.startswith("/api/") or "application/json" in request.headers.get("accept", ""):
        return JSONResponse({"detail": exc.detail}, status_code=exc.status_code)
    from .security import current_user_optional
    from .db import SessionLocal

    db = SessionLocal()
    try:
        user = current_user_optional(request, db)
        return render(request, "error.html", {"user": user, "code": exc.status_code, "detail": exc.detail},
                      status_code=exc.status_code)
    finally:
        db.close()


@app.get("/healthz")
def healthz():
    return {"status": "ok"}

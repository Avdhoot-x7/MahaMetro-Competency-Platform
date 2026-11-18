"""Template rendering helpers shared by all routers."""
from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path

from fastapi import Request
from markupsafe import Markup
from fastapi.templating import Jinja2Templates

from .config import get_settings
from .i18n import LANGUAGES, pick, translator
from .security import csrf_token

templates = Jinja2Templates(directory=str(Path(__file__).parent / "templates"))

STATUS_CLASS = {"Meets": "ok", "Developing": "dev", "Priority": "pri", "Safety gate": "gate",
                "Not assessed": "na", "Green": "ok", "Amber": "pri", "Red": "gate", "None": "na"}


def _fmt_dt(v: datetime | None, with_time: bool = True) -> str:
    if not v:
        return "–"
    return v.strftime("%d %b %Y %H:%M" if with_time else "%d %b %Y")


templates.env.filters["dt"] = _fmt_dt
templates.env.filters["d"] = lambda v: _fmt_dt(v, False)
templates.env.filters["tojson_safe"] = lambda v: Markup(
    json.dumps(v, ensure_ascii=False, default=str).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026"))
templates.env.filters["num"] = lambda v, nd=1: "–" if v is None else (f"{v:.{nd}f}".rstrip("0").rstrip(".") if nd else f"{v:.0f}")


def lang_of(request: Request) -> str:
    lang = request.session.get("lang") or get_settings().default_lang
    return lang if lang in LANGUAGES else "en"


def nav_for(user) -> list[tuple[str, str]]:
    if not user:
        return []
    items = [("/me", "My dashboard")]
    if user.has("supervisor"):
        items.append(("/team", "My team"))
    if user.has("hod"):
        items.append(("/department", "Department"))
    if user.has("management"):
        items.append(("/org", "Organisation"))
    if user.has("sme", "reviewer"):
        items.append(("/items", "Question bank"))
    if user.has("cell"):
        items.append(("/cell", "Assessment Cell"))
    return items


def render(request: Request, name: str, ctx: dict | None = None, status_code: int = 200):
    lang = lang_of(request)
    t = translator(lang)
    base = {
        "request": request, "lang": lang, "t": t, "languages": LANGUAGES,
        "L": lambda obj, field: pick(obj, field, lang),
        "csrf": csrf_token(request), "settings": get_settings(), "status_class": STATUS_CLASS,
    }
    base.update(ctx or {})
    base.setdefault("user", None)
    base["nav"] = nav_for(base["user"])
    return templates.TemplateResponse(request, name, base, status_code=status_code)


def flash(request: Request, message: str, kind: str = "info") -> None:
    request.session.setdefault("flash", []).append([kind, message])


def pop_flash(request: Request) -> list:
    return request.session.pop("flash", [])


templates.env.globals["pop_flash"] = pop_flash

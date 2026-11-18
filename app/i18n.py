"""English/Marathi interface strings. The English text is the key; mr.json maps it to Marathi.
Add a language by dropping <code>.json next to mr.json and listing it in LANGUAGES."""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path

LANGUAGES = {"en": "English", "mr": "मराठी"}
_DIR = Path(__file__).parent / "locales"


@lru_cache
def catalogue(lang: str) -> dict[str, str]:
    f = _DIR / f"{lang}.json"
    if lang == "en" or not f.exists():
        return {}
    return json.loads(f.read_text(encoding="utf-8"))


def translator(lang: str):
    cat = catalogue(lang)

    def t(text: str, **kw) -> str:
        s = cat.get(text, text)
        return s.format(**kw) if kw else s

    return t


def pick(obj, field: str, lang: str) -> str:
    """Bilingual content field with English fallback: pick(item, 'stem', 'mr')."""
    if obj is None:
        return ""
    if lang != "en":
        v = getattr(obj, f"{field}_{lang}", "") if not isinstance(obj, dict) else obj.get(f"{field}_{lang}", "")
        if v:
            return v
    return getattr(obj, f"{field}_en", "") if not isinstance(obj, dict) else obj.get(f"{field}_en", "")

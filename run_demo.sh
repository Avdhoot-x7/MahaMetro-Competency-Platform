#!/usr/bin/env bash
# Maha Metro CoE - Competency Assessment : local DEMO (Linux/macOS). Needs Python 3.11+. Uses SQLite.
set -e
cd "$(dirname "$0")"
[ -d .venv ] || { python3 -m venv .venv && .venv/bin/pip install -q -r requirements-demo.txt; }
export DATABASE_URL=sqlite:///./demo.db DEMO_MODE=true AUTH_MODES=local MEDIA_DIR="$PWD/data/media"
[ -f demo_v1.3.txt ] || rm -f demo.db
[ -f demo.db ] || { .venv/bin/python -m seed.seed --demo && echo v1.3 > demo_v1.3.txt; }
echo "Demo at http://localhost:8000 - pick a demo role; password Demo@2027"
exec .venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8000

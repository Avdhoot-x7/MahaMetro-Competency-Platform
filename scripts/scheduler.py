"""Nightly jobs: import the newest SAP HR export dropped in IMPORT_DIR/sap, then refresh item statistics.
Processed files move to IMPORT_DIR/sap/processed. Runs forever (docker 'scheduler' service)."""
import os
import shutil
import time
from datetime import datetime, timedelta
from pathlib import Path

from app.db import SessionLocal, init_db
from app.services.analytics import run_item_analysis
from scripts.import_employees import run as import_file

IMPORT_DIR = Path(os.getenv("IMPORT_DIR", "/data/import"))
AT = os.getenv("NIGHTLY_AT", "01:30")


def nightly() -> None:
    sap = IMPORT_DIR / "sap"
    done = sap / "processed"
    done.mkdir(parents=True, exist_ok=True)
    for f in sorted(sap.glob("*"), key=lambda x: x.stat().st_mtime):
        if f.is_file() and f.suffix.lower() in (".csv", ".xlsx"):
            import_file(str(f))
            shutil.move(str(f), done / f"{datetime.now():%Y%m%d%H%M}_{f.name}")
    db = SessionLocal()
    try:
        print("item analysis:", run_item_analysis(db))
    finally:
        db.close()


def main() -> None:
    init_db()
    hh, mm = (int(x) for x in AT.split(":"))
    while True:
        now = datetime.now()
        nxt = now.replace(hour=hh, minute=mm, second=0, microsecond=0)
        if nxt <= now:
            nxt += timedelta(days=1)
        time.sleep((nxt - now).total_seconds())
        try:
            nightly()
        except Exception as e:  # keep the scheduler alive
            print("nightly job failed:", e)


if __name__ == "__main__":
    main()

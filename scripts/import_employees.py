"""Import an SAP HR export (CSV or Excel) from the command line.

    python -m scripts.import_employees /data/import/sap/HR_EXPORT_20270115.csv
"""
import sys
from pathlib import Path

from app.db import SessionLocal, init_db
from app.services.importers import import_employees


def run(path: str) -> int:
    init_db()
    p = Path(path)
    db = SessionLocal()
    try:
        rep = import_employees(db, p.name, p.read_bytes(), "scheduler")
    finally:
        db.close()
    print(f"{p.name}: created {rep.created}, updated {rep.updated}, skipped {rep.skipped}")
    for line in rep.errors[:50] + rep.warnings[:50]:
        print("  ", line)
    return 0 if not rep.errors else 1


if __name__ == "__main__":
    sys.exit(run(sys.argv[1]))

"""Bulk imports: employee master (SAP HR export) and question bank (Excel template)."""
from __future__ import annotations

import csv
import io
import re
from dataclasses import dataclass, field

from openpyxl import load_workbook
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import (
    BLOOM, QTYPES, Competency, DesignationMap, Employee, Grade, Item, ItemOption, Media, Passage,
    RoleFamily,
)


@dataclass
class ImportReport:
    created: int = 0
    updated: int = 0
    skipped: int = 0
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)


def _rows(filename: str, data: bytes, sheet: str | None = None) -> list[dict]:
    name = filename.lower()
    if name.endswith((".xlsx", ".xlsm")):
        wb = load_workbook(io.BytesIO(data), read_only=True, data_only=True)
        ws = wb[sheet] if sheet and sheet in wb.sheetnames else wb.worksheets[0]
        it = ws.iter_rows(values_only=True)
        header = None
        out = []
        for r in it:
            if header is None:
                if r and sum(1 for c in r if c not in (None, "")) >= 2:
                    header = [str(c).strip() if c is not None else "" for c in r]
                continue
            if not any(c not in (None, "") for c in r):
                continue
            out.append({header[i]: ("" if v is None else str(v).strip()) for i, v in enumerate(r) if i < len(header)})
        return out
    text = data.decode("utf-8-sig", errors="replace")
    dialect = csv.Sniffer().sniff(text[:4096], delimiters=",;\t|") if text.strip() else csv.excel
    return [{(k or "").strip(): (v or "").strip() for k, v in row.items()}
            for row in csv.DictReader(io.StringIO(text), dialect=dialect)]


def _pick(row: dict, *aliases: str) -> str:
    low = {re.sub(r"[^a-z0-9]", "", k.lower()): v for k, v in row.items()}
    for a in aliases:
        v = low.get(re.sub(r"[^a-z0-9]", "", a.lower()))
        if v not in (None, ""):
            return str(v).strip()
    return ""


# ------------------------------------------------------------------ employees
EMP_ALIASES = {
    "emp_id": ("Employee ID", "emp_id", "Personnel Number", "Pers.No.", "PERNR", "Employee No", "Emp Code"),
    "name_en": ("Name", "Employee Name", "Name (English)", "Full Name"),
    "name_mr": ("Name (Marathi)", "Marathi Name", "name_mr"),
    "designation": ("Designation", "Position", "Position Text", "Job"),
    "department": ("Department", "Org Unit", "Organizational Unit", "Dept"),
    "location": ("Location", "Personnel Area", "Personnel Subarea", "Work Location"),
    "grade": ("Grade", "Grade Band", "Pay Level", "Employee Subgroup"),
    "role_family": ("Role family", "Role Family Code", "role_family"),
    "reporting_officer": ("Reporting Officer", "Reporting Officer ID", "Supervisor ID", "Manager ID", "RO"),
    "email": ("Email", "E-mail", "Email ID", "Official Email"),
    "mobile": ("Mobile", "Mobile No", "Phone"),
    "extra_time_pct": ("Extra time %", "Extra Time"),
    "active": ("Active", "Status", "Employment Status"),
}


def import_employees(db: Session, filename: str, data: bytes, actor: str = "") -> ImportReport:
    rep = ImportReport()
    families = {r.code for r in db.scalars(select(RoleFamily)).all()}
    grades = {g.code for g in db.scalars(select(Grade)).all()}
    dmap = {d.designation.lower(): d for d in db.scalars(select(DesignationMap)).all()}
    for n, row in enumerate(_rows(filename, data), start=2):
        v = {k: _pick(row, *a) for k, a in EMP_ALIASES.items()}
        if not v["emp_id"] or not v["name_en"]:
            rep.skipped += 1
            rep.errors.append(f"Row {n}: employee ID and name are required")
            continue
        emp = db.scalar(select(Employee).where(Employee.emp_id == v["emp_id"]))
        new = emp is None
        if new:
            emp = Employee(emp_id=v["emp_id"], name_en=v["name_en"], roles=["employee"], source="sap_hr")
            db.add(emp)
        emp.name_en = v["name_en"]
        for k in ("name_mr", "designation", "department", "location", "email", "mobile", "reporting_officer"):
            if v[k]:
                setattr(emp, k, v[k])
        rf = v["role_family"].upper()
        gr = v["grade"].upper()
        dm = dmap.get(emp.designation.lower()) if emp.designation else None
        if rf in families:
            emp.role_family_code = rf
        elif dm and dm.role_family_code:
            emp.role_family_code = dm.role_family_code
        elif new or not emp.role_family_code:
            rep.warnings.append(f"Row {n} ({emp.emp_id}): designation '{emp.designation}' not mapped to a role family")
        if gr in grades:
            emp.grade_code = gr
        elif dm and dm.grade_code:
            emp.grade_code = dm.grade_code
        elif new or not emp.grade_code:
            rep.warnings.append(f"Row {n} ({emp.emp_id}): grade '{v['grade']}' not recognised")
        if v["extra_time_pct"].replace(".", "").isdigit():
            emp.extra_time_pct = int(float(v["extra_time_pct"]))
        if v["active"]:
            emp.active = v["active"].strip().lower() not in ("0", "n", "no", "inactive", "withdrawn", "left", "false")
        rep.created += 1 if new else 0
        rep.updated += 0 if new else 1
    db.commit()
    # supervisors: anyone who is somebody's reporting officer gets the supervisor role
    ros = {e.reporting_officer for e in db.scalars(select(Employee)).all() if e.reporting_officer}
    for e in db.scalars(select(Employee).where(Employee.emp_id.in_(ros))).all():
        if "supervisor" not in (e.roles or []):
            e.roles = list(e.roles or []) + ["supervisor"]
    db.commit()
    return rep


# ------------------------------------------------------------------ items
TYPE_ALIASES = {
    "mcq": "single", "single": "single", "single correct": "single", "single best answer": "single",
    "multi": "multi", "multiple": "multi", "multiple response": "multi", "multiple correct": "multi",
    "truefalse": "truefalse", "true/false": "truefalse", "tf": "truefalse",
    "sjt": "sjt", "sjt (best response)": "sjt", "sjt (rank)": "sjt", "situational judgement": "sjt",
    "sequence": "sequence", "sequencing": "sequence", "ordering": "sequence",
    "match": "match", "matching": "match", "match the following": "match",
    "numeric": "numeric", "number": "numeric", "fill_blank": "fill_blank", "fill in the blank": "fill_blank",
    "short_text": "short_text", "written": "short_text", "short written response": "short_text",
    "audio_response": "audio_response", "spoken": "audio_response", "oral": "audio_response",
}
LETTERS = "ABCDEF"


def _letters(s: str) -> list[int]:
    return [LETTERS.index(x) for x in re.findall(r"[A-F]", s.upper())]


def import_items(db: Session, filename: str, data: bytes, actor: str = "", status: str = "review") -> ImportReport:
    rep = ImportReport()
    comps = {c.code for c in db.scalars(select(Competency)).all()}
    # passages sheet first (optional)
    if filename.lower().endswith(".xlsx") and "Passages" in load_workbook(io.BytesIO(data), read_only=True).sheetnames:
        for row in _rows(filename, data, sheet="Passages"):
            code = _pick(row, "Passage ID")
            if not code:
                continue
            p = db.scalar(select(Passage).where(Passage.code == code)) or Passage(code=code)
            p.title_en = _pick(row, "Title (EN)", "Title")
            p.title_mr = _pick(row, "Title (MR)")
            p.body_en = _pick(row, "Text (EN)", "Text")
            p.body_mr = _pick(row, "Text (MR)")
            mf = _pick(row, "Media file")
            if mf:
                m = db.scalar(select(Media).where(Media.filename == mf))
                p.media_id = m.id if m else None
            db.add(p)
        db.flush()
    for n, row in enumerate(_rows(filename, data, sheet="Items"), start=2):
        code = _pick(row, "Item ID", "code")
        comp = _pick(row, "Competency", "Competency code").upper()[:3]
        stem = _pick(row, "Stem (EN)", "Stem", "Question")
        if not code or comp not in comps or not stem:
            rep.skipped += 1
            rep.errors.append(f"Row {n}: needs Item ID, a valid competency code and an English stem")
            continue
        qtype = TYPE_ALIASES.get(_pick(row, "Item type", "Type").lower(), "")
        if qtype not in QTYPES:
            rep.skipped += 1
            rep.errors.append(f"Row {n} ({code}): unknown item type '{_pick(row, 'Item type')}'")
            continue
        key = _pick(row, "Key", "Answer")
        opts_en = [_pick(row, f"Option {L} (EN)", f"Option {L}") for L in LETTERS]
        opts_mr = [_pick(row, f"Option {L} (MR)") for L in LETTERS]
        n_opts = max([i + 1 for i, o in enumerate(opts_en) if o] or [0])
        if qtype == "sequence" and len(_letters(key)) <= 1 and n_opts:
            qtype = "single"  # legacy template encodes sequencing as an MCQ of orders
        if qtype == "truefalse" and n_opts == 0:
            opts_en[:2], opts_mr[:2], n_opts = ["True", "False"], ["बरोबर", "चूक"], 2
        item = db.scalar(select(Item).where(Item.code == code))
        new = item is None
        if new:
            item = Item(code=code, stem_en=stem, competency_code=comp)
            db.add(item)
        else:
            item.version += 1
            item.options.clear()
        item.competency_code = comp
        item.qtype = qtype
        item.stem_en = stem
        item.stem_mr = _pick(row, "Stem (MR)")
        fams = _pick(row, "Role families") or "ALL"
        item.role_families = [f.strip().upper() for f in re.split(r"[,;/ ]+", fams) if f.strip()]
        lvl = _pick(row, "Target level")
        item.target_level = int(float(lvl)) if lvl.replace(".", "").isdigit() else 3
        bloom = _pick(row, "Bloom").capitalize()
        item.bloom = bloom if bloom in BLOOM else ("Analyse" if bloom.startswith("Analy") else "Understand")
        pool = _pick(row, "Pool").lower()
        item.pool = pool if pool in ("exam", "practice", "both") else "exam"
        item.rationale_en = _pick(row, "Rationale (EN)", "Rationale")
        item.rationale_mr = _pick(row, "Rationale (MR)")
        item.rubric_en = _pick(row, "Rubric (EN)", "Rubric")
        item.rubric_mr = _pick(row, "Rubric (MR)")
        item.source_ref = _pick(row, "Source reference", "Source")
        item.marks = float(_pick(row, "Marks") or 1)
        item.negative_marks = float(_pick(row, "Negative marks") or 0)
        item.partial_credit = _pick(row, "Partial credit").lower() in ("y", "yes", "1", "true")
        item.shuffle_options = _pick(row, "Shuffle options").lower() not in ("n", "no", "0", "false")
        item.status = status
        item.needs_sme_validation = "validation" in _pick(row, "Status").lower()
        item.author_emp_id = item.author_emp_id or _pick(row, "Author") or actor
        pid = _pick(row, "Passage ID")
        if pid:
            p = db.scalar(select(Passage).where(Passage.code == pid))
            item.passage_id = p.id if p else None
            if not p:
                rep.warnings.append(f"Row {n} ({code}): passage {pid} not found")
            item.passage_order = int(float(_pick(row, "Passage order") or 0))
        mf = _pick(row, "Media file")
        if mf:
            m = db.scalar(select(Media).where(Media.filename == mf))
            item.media_id = m.id if m else None
            if not m:
                rep.warnings.append(f"Row {n} ({code}): media file {mf} not uploaded yet")
        if qtype == "numeric":
            item.answer_numeric = float(key) if key else None
            item.numeric_tolerance = float(_pick(row, "Tolerance") or 0)
        if qtype == "fill_blank":
            item.accepted_answers = [a.strip() for a in re.split(r"\|\||\n|;", key) if a.strip()]
        keys = _letters(key)
        weights = [w.strip() for w in _pick(row, "SJT weights").split(",") if w.strip()]
        for i in range(n_opts):
            text_en, text_mr = opts_en[i], opts_mr[i]
            match_en = match_mr = ""
            if qtype == "match":
                text_en, _, match_en = text_en.partition("=>")
                text_mr, _, match_mr = text_mr.partition("=>")
            o = ItemOption(ord=i, text_en=text_en.strip(), text_mr=text_mr.strip(),
                           match_en=match_en.strip(), match_mr=match_mr.strip())
            if qtype in ("single", "multi", "truefalse", "sjt"):
                o.is_correct = i in keys
            if qtype == "sjt":
                o.weight = float(weights[i]) if i < len(weights) else (1.0 if i in keys else 0.0)
            if qtype == "sequence":
                o.seq_position = keys.index(i) + 1 if i in keys else i + 1
            item.options.append(o)
        problems = validate_item(item)
        if problems:
            rep.warnings.append(f"Row {n} ({code}): " + "; ".join(problems))
            item.status = "draft"
        quality = quality_warnings(item)
        if quality:
            rep.warnings.append(f"Row {n} ({code}) quality: " + "; ".join(quality))
        rep.created += 1 if new else 0
        rep.updated += 0 if new else 1
    db.commit()
    return rep


def validate_item(item: Item) -> list[str]:
    p = []
    q = item.qtype
    opts = item.options
    if q in ("single", "truefalse") and sum(1 for o in opts if o.is_correct) != 1:
        p.append("needs exactly one correct option")
    if q == "multi" and sum(1 for o in opts if o.is_correct) < 1:
        p.append("needs at least one correct option")
    if q in ("single", "multi", "sjt") and len(opts) < 2:
        p.append("needs at least two options")
    if q == "sjt" and not any((o.weight or 0) > 0 for o in opts):
        p.append("SJT needs option weights")
    if q == "sequence" and len(opts) < 3:
        p.append("sequence needs at least three steps")
    if q == "match" and (len(opts) < 2 or any(not o.match_en for o in opts)):
        p.append("each pair needs both sides (left => right)")
    if q == "numeric" and item.answer_numeric is None:
        p.append("numeric answer missing")
    if q == "fill_blank" and not item.accepted_answers:
        p.append("accepted answers missing")
    if q in ("short_text", "audio_response") and not item.rubric_en:
        p.append("scoring rubric missing")
    return p


# ------------------------------------------------------------------ quality checks (warnings, not errors)
_ALL_NONE = re.compile(r"\b(all|none|both|neither) of (the )?(above|these|them)\b|वरील सर्व|वरीलपैकी (कोणतेही|काहीही) नाही|यापैकी (कोणतेही|काहीही) नाही", re.I)


def quality_warnings(item: Item) -> list[str]:
    """Item-writing checks from the SME question-writing guide. They warn the author; they do not block saving."""
    w: list[str] = []
    q = item.qtype
    opts = list(item.options)
    if q in ("single", "sjt", "multi", "truefalse", "sequence", "match"):
        if q in ("single", "sjt") and len(opts) < 4:
            w.append("Fewer than four options – add believable wrong options")
        if q == "multi" and len(opts) < 5:
            w.append("Multiple-correct questions work best with five or more options")
        texts = [(o.text_en or "").strip() for o in opts]
        if any(_ALL_NONE.search(t) for t in texts + [o.text_mr or "" for o in opts]):
            w.append("Avoid 'all of the above' / 'none of the above' – write a real option instead")
        if len({t.lower() for t in texts}) < len(texts):
            w.append("Two options have the same text")
        if q in ("single", "sjt") and len(opts) >= 2:
            def credit(o):
                return o.weight if (q == "sjt" and o.weight is not None) else (1.0 if o.is_correct else 0.0)
            best = max(opts, key=credit)
            others = sorted((len(o.text_en or "") for o in opts if o is not best), reverse=True)
            lb = len(best.text_en or "")
            if others and lb >= others[0] * 1.15 and lb - others[0] >= 8:
                w.append("The correct option is clearly the longest – candidates can guess it by length")
        if q == "multi":
            right = [len(o.text_en or "") for o in opts if o.is_correct]
            wrong = [len(o.text_en or "") for o in opts if not o.is_correct]
            if right and wrong and min(right) > max(wrong):
                w.append("All correct options are longer than every wrong option")
    if not (item.stem_mr or "").strip():
        w.append("Marathi question text is missing")
    if any(not (o.text_mr or "").strip() for o in opts):
        w.append("Marathi text is missing for one or more options")
    if q == "match" and any(not (o.match_mr or "").strip() for o in opts):
        w.append("Marathi text is missing for one or more matching answers")
    if (item.rationale_en or "").strip() and not (item.rationale_mr or "").strip():
        w.append("Marathi explanation is missing")
    if q not in ("short_text", "audio_response") and not (item.rationale_en or "").strip():
        w.append("No explanation – practice candidates learn from it")
    if (item.competency_code or "").startswith("D") and not (item.source_ref or "").strip():
        w.append("No source reference (SOP / SR clause / manual section)")
    return w

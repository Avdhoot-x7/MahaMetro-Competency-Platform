"""Load the competency framework, training catalogue, question bank and tests.

    python -m seed.seed                       # framework + bank + draft tests (production first run)
    python -m seed.seed --admin 900001 --password '...'   # also create a break-glass super-admin
    python -m seed.seed --demo                # add demo accounts, synthetic staff and simulated results

Safe to re-run: existing records are updated, not duplicated.
"""
from __future__ import annotations

import argparse
import json
import random
import shutil
import sys
from datetime import timedelta
from pathlib import Path

from sqlalchemy import select

from app.config import get_settings
from app.db import SessionLocal, init_db
from app.models import (
    Attempt, Blueprint, Competency, CompetencyScore, CutScore, DesignationMap, Employee, Grade, Intervention, Item,
    ItemOption, Media, Passage, RoleFamily, RoleProfile, Setting, Test, utcnow,
)
from app.security import hash_password
from app.services import engine, scoring
from app.services.framework import DEFAULT_WEIGHTS

from .framework_mr import COMPETENCY_MR, CUT_SCORES, DESIGNATION_MAP, GRADES, ROLE_FAMILIES
from .question_bank import ITEMS, PASSAGES

HERE = Path(__file__).parent
settings = get_settings()


def seed_framework(db) -> None:
    ext = json.loads((HERE / "framework_extract.json").read_text(encoding="utf-8"))
    for code, en, mr, group in ROLE_FAMILIES:
        rf = db.get(RoleFamily, code) or RoleFamily(code=code)
        rf.name_en, rf.name_mr, rf.group = en, mr, group
        db.add(rf)
    for code, en, mr, c, b, d in GRADES:
        g = db.get(Grade, code) or Grade(code=code)
        g.band_en, g.band_mr, g.core_offset, g.behavioural_offset, g.domain_offset = en, mr, c, b, d
        db.add(g)
    for c in ext["competencies"]:
        comp = db.get(Competency, c["code"]) or Competency(code=c["code"])
        comp.name_en, comp.category, comp.safety_critical = c["name_en"], c["category"], c["safety_critical"]
        comp.description_en, comp.methods = c["description_en"] or "", c["methods"] or ""
        comp.name_mr = COMPETENCY_MR.get(c["code"], "")
        db.add(comp)
    db.flush()
    for fam, code, lvl in ext["role_profiles"]:
        rp = db.get(RoleProfile, (fam, code)) or RoleProfile(role_family_code=fam, competency_code=code)
        if rp.approved_by is None:  # never overwrite a profile already approved in the system
            rp.base_level = lvl
        db.add(rp)
    for level, minimum, en, mr in CUT_SCORES:
        cs = db.get(CutScore, level) or CutScore(level=level, min_score=minimum)
        cs.label_en, cs.label_mr = en, mr
        db.add(cs)
    if not db.get(Setting, "composite_weights"):
        db.add(Setting(key="composite_weights", value=DEFAULT_WEIGHTS))
    for des, fam, grade in DESIGNATION_MAP:
        d = db.scalar(select(DesignationMap).where(DesignationMap.designation == des)) or DesignationMap(designation=des)
        d.role_family_code, d.grade_code = fam, grade
        db.add(d)
    db.commit()


INTERVENTIONS = [
    # competency-specific, tied to CoE UTKARSH labs and simulators
    ("C01", None, "safety", "Fire safety & emergency response refresher with live extinguisher drill (CoE)", "अग्निसुरक्षा व आपत्कालीन प्रतिसाद उजळणी, प्रत्यक्ष अग्निशामक सरावासह (CoE)", "CoE", "Classroom", 8),
    ("D01", None, "safety", "GR/SR refresher and oral board with Traffic/Safety officer", "GR/SR उजळणी व वाहतूक/सुरक्षा अधिकाऱ्यासमोर मौखिक परीक्षा", "CoE", "Classroom", 12),
    ("D02", None, "safety", "Rolling Stock Simulator: degraded-mode driving and fault handling", "रोलिंग स्टॉक सिम्युलेटर: बिघाड-मोड चालन व दोष हाताळणी", "Simulator", "Simulator", 8),
    ("D03", None, "safety", "SCR operations practical: PA/PIDS, CCTV, ESP, opening/closing", "SCR परिचालन प्रात्यक्षिक: PA/PIDS, CCTV, ESP, उघडणे/बंद करणे", "CoE Lab", "Lab practical", 6),
    ("D04", None, "safety", "OCC simulator: headway regulation and service restoration", "OCC सिम्युलेटर: हेडवे नियमन व सेवा पुनर्स्थापना", "Simulator", "Simulator", 8),
    ("D05", None, "safety", "Emergency & degraded working drill (evacuation, fire, single-line working)", "आपत्कालीन व बिघाडकालीन कार्य सराव (बाहेर काढणे, आग, एकेरी मार्ग)", "CoE", "Workshop", 8),
    ("D06", None, "major", "AFC Lab: gates, TOM, card/QR fault handling and revenue reconciliation", "AFC लॅब: गेट, TOM, कार्ड/QR दोष हाताळणी व महसूल ताळमेळ", "CoE Lab", "Lab practical", 6),
    ("D07", None, "safety", "RS Lab + Simulator: TCMS/DMI faults, brakes, doors, pantograph", "RS लॅब + सिम्युलेटर: TCMS/DMI दोष, ब्रेक, दरवाजे, पॅन्टोग्राफ", "CoE Lab", "Lab practical", 12),
    ("D08", None, "safety", "Signalling Lab: ATC/ATS/CBI fault diagnosis and PSD controller", "सिग्नलिंग लॅब: ATC/ATS/CBI दोष निदान व PSD नियंत्रक", "CoE Lab", "Lab practical", 12),
    ("D09", None, "major", "Telecom Lab: PA/PIDS, CCTV, radio and SCADA practice", "दूरसंचार लॅब: PA/PIDS, CCTV, रेडिओ व SCADA सराव", "CoE Lab", "Lab practical", 8),
    ("D10", None, "safety", "PS & Traction Lab: OHE, switchgear, protection, SCADA switching, earthing", "PS व ट्रॅक्शन लॅब: OHE, स्विचगियर, संरक्षण, SCADA स्विचिंग, अर्थिंग", "CoE Lab", "Lab practical", 12),
    ("D11", None, "safety", "Lift & Escalator Simulator: passenger rescue and fault finding; E&M Lab (HVAC, fire, BMS)", "लिफ्ट व एस्केलेटर सिम्युलेटर: प्रवासी सुटका व दोष शोध; E&M लॅब (HVAC, आग, BMS)", "Simulator", "Simulator", 8),
    ("D12", None, "safety", "Track Lab: geometry, fastenings, turnouts and inspection practice", "ट्रॅक लॅब: भूमिती, फास्टनिंग, टर्नआउट व तपासणी सराव", "CoE Lab", "Lab practical", 8),
    ("D13", None, "safety", "Permit-to-work, isolation, test-for-dead and earthing practical (PS & Traction Lab)", "कार्य-परवाना, विलगीकरण, मृत-चाचणी व अर्थिंग प्रात्यक्षिक (PS व ट्रॅक्शन लॅब)", "CoE Lab", "Lab practical", 6),
    ("D18", None, "safety", "Hazard identification, risk assessment and incident investigation workshop", "धोका ओळख, जोखीम मूल्यांकन व घटना तपास कार्यशाळा", "CoE", "Workshop", 8),
    ("D25", None, "major", "Public procurement: GFR 2017, CVC guidelines, GeM and e-tendering", "सार्वजनिक खरेदी: GFR 2017, CVC मार्गदर्शक सूचना, GeM व ई-निविदा", "iGOT Karmayogi", "E-learning", 6),
    ("D30", None, "minor", "e-Office noting and drafting clinic with RTI reply practice", "ई-ऑफिस टिप्पणी व मसुदा लेखन सत्र, RTI उत्तर सरावासह", "CoE", "Workshop", 4),
    ("C05", None, "minor", "Cyber hygiene micro-module and phishing simulation", "सायबर स्वच्छता लघु-मॉड्यूल व फिशिंग सिम्युलेशन", "iGOT Karmayogi", "E-learning", 2),
    # generic by category and gap type
    (None, "Domain", "safety", "Mandatory refresher at CoE (classroom + simulator/practical) within 30 days; re-assess before independent duty", "30 दिवसांत CoE मध्ये अनिवार्य उजळणी (वर्ग + सिम्युलेटर/प्रात्यक्षिक); स्वतंत्र कामापूर्वी पुनर्मूल्यांकन", "CoE", "Classroom", 12),
    (None, "Core", "safety", "Mandatory safety refresher at CoE within 30 days", "30 दिवसांत CoE मध्ये अनिवार्य सुरक्षा उजळणी", "CoE", "Classroom", 8),
    (None, "Domain", "major", "Structured course (CoE or OEM) followed by mentored OJT with checklist", "संरचित अभ्यासक्रम (CoE किंवा OEM) व त्यानंतर तपासणी-सूचीसह मार्गदर्शित OJT", "CoE", "Classroom", 16),
    (None, "Domain", "minor", "Targeted e-learning modules, toolbox talks and OJT tasks", "लक्ष्यित ई-लर्निंग मॉड्यूल, टूलबॉक्स चर्चा व OJT कामे", "OJT", "OJT with checklist", 6),
    (None, "Core", "major", "CoE core-skills course with practice assignments", "सराव असाइनमेंटसह CoE मूलभूत-कौशल्य अभ्यासक्रम", "CoE", "Classroom", 8),
    (None, "Core", "minor", "iGOT Karmayogi module and supervisor coaching", "iGOT कर्मयोगी मॉड्यूल व पर्यवेक्षकाचे मार्गदर्शन", "iGOT Karmayogi", "E-learning", 4),
    (None, "Behavioural", "behavioural", "Behavioural workshop, coaching and iGOT Karmayogi behavioural modules", "वर्तनविषयक कार्यशाळा, मार्गदर्शन व iGOT कर्मयोगी वर्तनविषयक मॉड्यूल", "iGOT Karmayogi", "Workshop", 8),
    (None, None, "strength", "Nominate as SME item author, OJT mentor or internal trainer; consider for multi-skilling", "SME प्रश्नलेखक, OJT मार्गदर्शक किंवा अंतर्गत प्रशिक्षक म्हणून नामनिर्देशन; बहुकौशल्यासाठी विचार", "CoE", "Mentoring", 0),
]


def seed_interventions(db) -> None:
    if db.scalar(select(Intervention.id).limit(1)):
        return
    for comp, cat, gap, en, mr, prov, mode, hours in INTERVENTIONS:
        db.add(Intervention(competency_code=comp, category=cat, gap_type=gap, title_en=en, title_mr=mr,
                            provider=prov, mode=mode, duration_hours=hours))
    db.commit()


BLUEPRINTS = {
    "Station Controller (SC, G2) – Papers A+B": ("SC", "G2", {
        "C01": (2, 2, 2, 0), "C02": (1, 2, 0, 0), "C03": (0, 1, 2, 1), "C04": (1, 1, 1, 0), "C05": (1, 1, 1, 0), "C06": (0, 1, 2, 0),
        "B01": (0, 0, 1, 1), "B02": (0, 0, 2, 1), "B03": (0, 0, 2, 1), "B04": (0, 0, 1, 1), "B05": (0, 0, 1, 0), "B06": (0, 0, 1, 0), "B07": (0, 0, 1, 0),
        "D01": (3, 3, 3, 1), "D03": (2, 3, 3, 1), "D04": (1, 1, 1, 0), "D05": (1, 2, 3, 2), "D06": (1, 2, 2, 0), "D09": (1, 1, 0, 0),
        "D11": (1, 1, 1, 0), "D18": (1, 1, 1, 0)}),
    "Assistant Manager Finance (FA, G3) – Papers A+B": ("FA", "G3", {
        "C01": (1, 1, 0, 0), "C02": (1, 1, 0, 0), "C03": (0, 1, 1, 0), "C04": (1, 1, 2, 1), "C05": (1, 1, 1, 0), "C06": (0, 1, 2, 0),
        "B01": (0, 0, 1, 1), "B02": (0, 0, 2, 1), "B03": (0, 0, 1, 0), "B04": (0, 0, 1, 1), "B05": (0, 0, 1, 0), "B06": (0, 0, 1, 1), "B07": (0, 0, 1, 0),
        "D06": (1, 1, 1, 0), "D19": (2, 3, 3, 2), "D20": (1, 2, 3, 2), "D21": (2, 2, 3, 1), "D22": (1, 1, 1, 0), "D25": (1, 2, 2, 1),
        "D26": (1, 1, 1, 0), "D28": (1, 1, 0, 0), "D30": (0, 1, 2, 0)}),
}


def seed_blueprints(db) -> None:
    blooms = ["Remember", "Understand", "Apply", "Analyse"]
    for name, (fam, grade, rows) in BLUEPRINTS.items():
        bp = db.scalar(select(Blueprint).where(Blueprint.name == name)) or Blueprint(name=name)
        bp.role_family_code, bp.grade_code = fam, grade
        bp.rules = [{"competency": c, "bloom": blooms[i], "count": n} for c, counts in rows.items()
                    for i, n in enumerate(counts) if n]
        bp.notes = "From MahaMetro_Competency_Framework_and_Tracker.xlsx, Test_Blueprint sheet (v0.1)."
        db.add(bp)
    db.commit()


def seed_media(db) -> dict[str, int]:
    settings.media_dir.mkdir(parents=True, exist_ok=True)
    out = {}
    mimes = {".mp3": ("audio", "audio/mpeg"), ".png": ("image", "image/png"), ".mp4": ("video", "video/mp4")}
    for f in sorted((HERE / "media").iterdir()):
        kind, mime = mimes[f.suffix]
        m = db.scalar(select(Media).where(Media.filename == f.name))
        if not m:
            stored = f"seed-{f.name}"
            shutil.copy(f, settings.media_dir / stored)
            m = Media(kind=kind, filename=f.name, stored_name=stored, mime=mime, size=f.stat().st_size, owner_emp_id="seed")
            db.add(m)
            db.flush()
        elif not (settings.media_dir / m.stored_name).exists():
            shutil.copy(f, settings.media_dir / m.stored_name)
        out[f.name] = m.id
    db.commit()
    return out


def seed_items(db, media: dict[str, int]) -> int:
    pmap = {}
    for p in PASSAGES:
        ps = db.scalar(select(Passage).where(Passage.code == p["code"])) or Passage(code=p["code"])
        for k in ("title_en", "title_mr", "body_en", "body_mr"):
            setattr(ps, k, p[k])
        db.add(ps)
        db.flush()
        pmap[p["code"]] = ps.id
    n = 0
    for d in ITEMS:
        it = db.scalar(select(Item).where(Item.code == d["code"]))
        if it and it.version > 1:
            continue  # edited in the system since seeding: leave it alone
        if it is None:
            it = Item(code=d["code"], stem_en=d["en"], competency_code=d["comp"])
            db.add(it)
        else:
            it.options.clear()
            db.flush()
        it.competency_code, it.qtype, it.stem_en, it.stem_mr = d["comp"], d["qtype"], d["en"], d["mr"]
        it.role_families, it.target_level, it.bloom, it.pool = d["fam"], d["lvl"], d["bloom"], d["pool"]
        it.rationale_en, it.rationale_mr, it.source_ref = d["why"], d["why_mr"], d["src"]
        it.needs_sme_validation = d["sme"]
        it.marks = float(d.get("marks", 1))
        it.partial_credit = bool(d.get("partial"))
        it.media_id = media.get(d.get("media", ""))
        it.passage_id = pmap.get(d.get("passage", ""))
        it.passage_order = d.get("porder", 0)
        it.status = "approved"
        it.author_emp_id = it.author_emp_id or "CoE-seed"
        it.reviewer_emp_id = it.reviewer_emp_id or "CoE-seed"
        q = d["qtype"]
        if q in ("single", "multi", "truefalse"):
            for i, (en, mr, ok) in enumerate(d["opts"]):
                it.options.append(ItemOption(ord=i, text_en=en, text_mr=mr, is_correct=ok))
            it.shuffle_options = q != "truefalse"
        elif q == "sjt":
            for i, (en, mr, w) in enumerate(d["opts"]):
                it.options.append(ItemOption(ord=i, text_en=en, text_mr=mr, weight=w, is_correct=w >= 0.999))
        elif q == "sequence":
            for i, (en, mr) in enumerate(d["steps"]):
                it.options.append(ItemOption(ord=i, text_en=en, text_mr=mr, seq_position=i + 1))
        elif q == "match":
            for i, ((len_, lmr), (ren, rmr)) in enumerate(d["pairs"]):
                it.options.append(ItemOption(ord=i, text_en=len_, text_mr=lmr, match_en=ren, match_mr=rmr))
        elif q == "numeric":
            it.answer_numeric, it.numeric_tolerance = float(d["value"]), float(d.get("tol", 0))
        elif q == "fill_blank":
            it.accepted_answers = d["accepted"]
        elif q in ("short_text", "audio_response"):
            it.rubric_en, it.rubric_mr = d["rubric"], d.get("rubric_mr", "")
        n += 1
    # v1.3: starter questions that were replaced by the rewritten bank are retired (kept for past results)
    codes = {d["code"] for d in ITEMS}
    for old in db.scalars(select(Item).where(Item.author_emp_id == "CoE-seed", Item.version <= 1, Item.status != "retired")):
        if old.code not in codes:
            old.status = "retired"
    db.commit()
    return n


def seed_tests(db, publish: bool) -> None:
    status = "published" if publish else "draft"
    now = utcnow()
    defs = [
        dict(code="PRACTICE-ALL", title_en="Practice test – my role profile", title_mr="सराव चाचणी – माझे भूमिका प्रोफाइल", kind="PRACTICE",
             items_per_competency=1, max_items=40, duration_min=30, max_attempts=0, show_feedback=True,
             instructions_en="A short practice test drawn from your role profile. A new set of questions is drawn each time.",
             instructions_mr="तुमच्या भूमिका प्रोफाइलवर आधारित छोटी सराव चाचणी. प्रत्येक वेळी प्रश्नांचा नवा संच निवडला जातो.",
             proctoring={}, status="published"),
        dict(code="T0-2027-BASELINE", title_en="Baseline competency assessment (T0) 2027", title_mr="आधाररेषा क्षमता मूल्यांकन (T0) 2027", kind="T0",
             items_per_competency=2, max_items=80, duration_min=90, max_attempts=1, start_at=now - timedelta(days=1),
             end_at=now + timedelta(days=60),
             instructions_en="Official baseline assessment. Results are developmental and are not used for appraisal.",
             instructions_mr="अधिकृत आधाररेषा मूल्यांकन. निकाल विकासात्मक आहेत व मूल्यांकन-अहवालासाठी वापरले जात नाहीत.",
             proctoring={"fullscreen": True, "webcam": False, "max_tab_switches": 5, "snapshot_seconds": 60}, status=status),
        dict(code="T1-2027-RETEST", title_en="Post-training re-test (T1)", title_mr="प्रशिक्षणोत्तर पुनर्चाचणी (T1)", kind="T1",
             items_per_competency=2, max_items=40, duration_min=45, max_attempts=1, start_at=now - timedelta(days=1),
             end_at=now + timedelta(days=90), proctoring={"fullscreen": True, "max_tab_switches": 5}, status="draft",
             instructions_en="Re-test on the competencies targeted by your training.", instructions_mr="तुमच्या प्रशिक्षणातील लक्ष्य क्षमतांवर पुनर्चाचणी."),
    ]
    for d in defs:
        t = db.scalar(select(Test).where(Test.code == d["code"]))
        if t:
            continue
        t = Test(created_by="CoE-seed", **d)
        db.add(t)
    db.commit()


# ------------------------------------------------------------------ demo data
FIRST = ["Aarti", "Abhijit", "Aditya", "Amol", "Anagha", "Anil", "Ashwini", "Chetan", "Deepa", "Ganesh", "Gauri", "Harshad",
         "Jyoti", "Kiran", "Ketan", "Madhuri", "Mahesh", "Manisha", "Milind", "Neha", "Nilesh", "Pallavi", "Prakash", "Prasad",
         "Priya", "Rahul", "Rajesh", "Rupali", "Sachin", "Sagar", "Sandeep", "Sanjay", "Sayali", "Shital", "Snehal", "Sunil",
         "Swati", "Tushar", "Vaishali", "Vinay", "Vivek", "Yogesh"]
LAST = ["Patil", "Deshmukh", "Kulkarni", "Joshi", "Pawar", "Jadhav", "Shinde", "More", "Gaikwad", "Wagh", "Kale", "Bhosale",
        "Chavan", "Deshpande", "Thakare", "Mane", "Sawant", "Kadam", "Naik", "Gokhale"]
MR_FIRST = dict(zip(FIRST, ["आरती", "अभिजित", "आदित्य", "अमोल", "अनघा", "अनिल", "अश्विनी", "चेतन", "दीपा", "गणेश", "गौरी", "हर्षद",
                            "ज्योती", "किरण", "केतन", "माधुरी", "महेश", "मनीषा", "मिलिंद", "नेहा", "नीलेश", "पल्लवी", "प्रकाश", "प्रसाद",
                            "प्रिया", "राहुल", "राजेश", "रुपाली", "सचिन", "सागर", "संदीप", "संजय", "सायली", "शीतल", "स्नेहल", "सुनील",
                            "स्वाती", "तुषार", "वैशाली", "विनय", "विवेक", "योगेश"]))
MR_LAST = dict(zip(LAST, ["पाटील", "देशमुख", "कुलकर्णी", "जोशी", "पवार", "जाधव", "शिंदे", "मोरे", "गायकवाड", "वाघ", "काळे", "भोसले",
                          "चव्हाण", "देशपांडे", "ठाकरे", "माने", "सावंत", "कदम", "नाईक", "गोखले"]))
DEPT = {"TO": "Operations", "SC": "Operations", "OCC": "Operations", "RS": "Rolling Stock", "STA": "Systems", "TPS": "Electrical",
        "EM": "Electrical", "CT": "Civil", "PJ": "Projects", "SFS": "Safety", "FA": "Finance", "HRA": "HR & Administration",
        "PCS": "Procurement & Contracts", "COM": "Commercial", "IT": "IT", "CORP": "Corporate"}
DESIG = {"TO": [("Train Operator", "G2")], "SC": [("Station Controller", "G2"), ("Customer Relations Assistant", "G1")],
         "OCC": [("Traffic Controller", "G2")], "RS": [("Technician (Rolling Stock)", "G1"), ("Section Engineer (Rolling Stock)", "G2")],
         "STA": [("Technician (Signalling)", "G1"), ("Section Engineer (Telecom)", "G2")], "TPS": [("Technician (Traction)", "G1"), ("Section Engineer (Traction)", "G2")],
         "EM": [("Technician (E&M)", "G1"), ("Section Engineer (E&M)", "G2")], "CT": [("Technician (Track)", "G1"), ("Section Engineer (Civil)", "G2")],
         "PJ": [("Planning Engineer", "G3")], "SFS": [("Safety Officer", "G3"), ("Security Supervisor", "G2")],
         "FA": [("Accounts Assistant", "G1"), ("Assistant Manager (Finance)", "G3")], "HRA": [("Office Assistant", "G1"), ("Assistant Manager (HR)", "G3")],
         "PCS": [("Stores Assistant", "G1"), ("Manager (Contracts)", "G3")], "COM": [("Commercial Assistant", "G1")],
         "IT": [("IT Assistant", "G1"), ("Assistant Manager (IT)", "G3")], "CORP": [("Assistant Manager (Legal)", "G3")]}
COUNTS = {"SC": 14, "TO": 8, "OCC": 5, "RS": 8, "STA": 6, "TPS": 5, "EM": 5, "CT": 4, "PJ": 3, "SFS": 3, "FA": 6, "HRA": 3,
          "PCS": 3, "COM": 2, "IT": 2, "CORP": 2}
LOCS = ["Nagpur – Line 1", "Nagpur – Line 2", "Pune – Line 1", "Pune – Line 2", "Nagpur – Mihan Depot", "Pune – Range Hills Depot"]

DEMO_USERS = [
    ("DEMO-CELL", "CoE Assessment Cell (demo)", "CoE मूल्यांकन कक्ष (डेमो)", "Manager (Training)", "CoE", "HRA", "G3", "", ["cell", "sme", "reviewer"]),
    ("DEMO-MGT", "Director (demo)", "संचालक (डेमो)", "Director", "Management", None, "G5", "", ["management"]),
    ("DEMO-HOD-OPS", "HoD Operations (demo)", "परिचालन विभागप्रमुख (डेमो)", "General Manager (Operations)", "Operations", "OCC", "G5", "", ["hod"]),
    ("DEMO-SUP-SC", "Station Manager (demo)", "स्टेशन मॅनेजर (डेमो)", "Station Manager", "Operations", "SC", "G3", "DEMO-HOD-OPS", ["supervisor"]),
    ("DEMO-EMP-SC", "Station Controller (demo)", "स्टेशन कंट्रोलर (डेमो)", "Station Controller", "Operations", "SC", "G2", "DEMO-SUP-SC", []),
    ("DEMO-FA", "Accounts Assistant (demo)", "लेखा सहाय्यक (डेमो)", "Accounts Assistant", "Finance", "FA", "G1", "DEMO-HOD-FIN", []),
    ("DEMO-HOD-FIN", "HoD Finance (demo)", "वित्त विभागप्रमुख (डेमो)", "General Manager (Finance)", "Finance", "FA", "G5", "", ["hod", "supervisor"]),
    ("DEMO-SME", "SME author – Traction (demo)", "SME लेखक – ट्रॅक्शन (डेमो)", "Section Engineer (Traction)", "Electrical", "TPS", "G2", "", ["sme"]),
    ("DEMO-REV", "SME reviewer – Safety (demo)", "SME समीक्षक – सुरक्षा (डेमो)", "Safety Officer", "Safety", "SFS", "G3", "", ["reviewer"]),
]


def simulated_response(item: Item, ok: bool, rng: random.Random) -> dict:
    opts = list(item.options)
    q = item.qtype
    if q in ("single", "truefalse", "sjt"):
        if q == "sjt":
            best = max(opts, key=lambda o: o.weight or 0)
            others = [o for o in opts if o.id != best.id]
            return {"choice": best.id if ok or not others else rng.choice(others).id}
        right = [o for o in opts if o.is_correct]
        wrong = [o for o in opts if not o.is_correct]
        return {"choice": (right[0] if ok or not wrong else rng.choice(wrong)).id}
    if q == "multi":
        right = [o.id for o in opts if o.is_correct]
        if ok:
            return {"choices": right}
        return {"choices": right[: max(1, len(right) - 1)] + [o.id for o in opts if not o.is_correct][:1]}
    if q == "sequence":
        order = [o.id for o in sorted(opts, key=lambda o: o.seq_position)]
        if not ok:
            order[0], order[1] = order[1], order[0]
        return {"order": order}
    if q == "match":
        pairs = {str(o.id): o.id for o in opts}
        if not ok and len(opts) > 1:
            a, b = opts[0].id, opts[1].id
            pairs[str(a)], pairs[str(b)] = b, a
        return {"pairs": pairs}
    if q == "numeric":
        return {"value": str(item.answer_numeric if ok else (item.answer_numeric or 0) * 1.5 + 1)}
    if q == "fill_blank":
        return {"text": item.accepted_answers[0] if ok else "?"}
    if q == "short_text":
        return {"text": "Demo written answer for evaluation."}
    return {}


def run_simulated_attempt(db, test: Test, emp: Employee, ability: float, rng: random.Random, manual_by: str) -> Attempt | None:
    try:
        att = engine.start_attempt(db, test, emp, emp.preferred_lang or "en")
    except engine.AssemblyError:
        return None
    for ai in att.items:
        it = ai.item
        p = max(0.05, min(0.97, ability - 0.08 * ((it.target_level or 3) - 3) + rng.gauss(0, 0.1)))
        ai.response = simulated_response(it, rng.random() < p, rng)
        ai.answered_at = utcnow()
    db.commit()
    att = scoring.finalize_attempt(db, att)
    if att.status == "pending_manual":
        for ai in att.items:
            if ai.needs_manual:
                ai.manual_score = round(ai.max_score * max(0, min(1, ability + rng.gauss(0, 0.15))) * 4) / 4
                ai.scored_by = manual_by
        db.commit()
        scoring.complete_manual_scoring(db, att)
    if att.status == "flagged":
        att.status = "scored"
        db.commit()
        scoring.publish_scores(db, att)
    return att


def seed_demo(db) -> None:
    rng = random.Random(2027)
    pw = hash_password("Demo@2027")
    for emp_id, en, mr, des, dept, fam, grade, ro, roles in DEMO_USERS:
        e = db.scalar(select(Employee).where(Employee.emp_id == emp_id)) or Employee(emp_id=emp_id, name_en=en)
        e.name_en, e.name_mr, e.designation, e.department = en, mr, des, dept
        e.role_family_code, e.grade_code, e.reporting_officer = fam, grade, ro
        e.roles = sorted(set(["employee"] + roles))
        e.location = "Nagpur – Line 1" if "FA" not in emp_id else "Nagpur – HQ"
        e.password_hash, e.consent_at, e.source = pw, utcnow(), "demo"
        db.add(e)
    db.commit()
    staff = []
    n = 0
    for fam, count in COUNTS.items():
        for i in range(count):
            n += 1
            fn, ln = rng.choice(FIRST), rng.choice(LAST)
            des, grade = rng.choice(DESIG[fam])
            emp_id = f"MM{100000 + n}"
            e = db.scalar(select(Employee).where(Employee.emp_id == emp_id)) or Employee(emp_id=emp_id, name_en="")
            e.name_en, e.name_mr = f"{fn} {ln}", f"{MR_FIRST[fn]} {MR_LAST[ln]}"
            e.designation, e.grade_code, e.role_family_code, e.department = des, grade, fam, DEPT[fam]
            e.location = rng.choice(LOCS)
            e.roles, e.consent_at, e.source = ["employee"], utcnow(), "demo"
            e.reporting_officer = "DEMO-SUP-SC" if fam == "SC" and i < 8 else ("DEMO-HOD-FIN" if fam == "FA" else "")
            db.add(e)
            staff.append(e)
    db.commit()
    t0 = db.scalar(select(Test).where(Test.code == "T0-2027-BASELINE"))
    t0.status = "published"
    db.commit()
    already = {a.employee_id for a in db.scalars(select(Attempt).where(Attempt.test_id == t0.id)).all()}
    sim = [e for e in staff if rng.random() < 0.85] + [db.scalar(select(Employee).where(Employee.emp_id == "DEMO-EMP-SC"))]
    for e in sim:
        if e.id in already:
            continue
        ability = rng.gauss(0.68, 0.13)
        run_simulated_attempt(db, t0, e, ability, rng, "DEMO-CELL")
        # practical / supervisor evidence for some operations and maintenance staff
        if e.role_family_code in ("SC", "TO", "OCC", "RS", "TPS", "EM") and rng.random() < 0.6:
            for code in ("C01", "D05", "D13", "D03", "D07", "D10", "D11"):
                comp = db.get(Competency, code)
                if comp and db.get(RoleProfile, (e.role_family_code, code)) and db.get(RoleProfile, (e.role_family_code, code)).base_level:
                    lvl = max(1, min(5, round(rng.gauss(3.2, 0.8))))
                    db.add(CompetencyScore(employee_id=e.id, competency_code=code, cycle="T0", method="practical",
                                           score=scoring.fw.RATING_TO_SCORE[lvl], recorded_by="DEMO-SUP-SC", comment="Demo practical check"))
            db.commit()
        if rng.random() < 0.5:
            for code in ("B01", "B03", "B04"):
                db.add(CompetencyScore(employee_id=e.id, competency_code=code, cycle="T0", method="rating",
                                       score=scoring.fw.RATING_TO_SCORE[max(1, min(5, round(rng.gauss(3.3, 0.7))))],
                                       recorded_by=e.reporting_officer or "DEMO-CELL", comment="Demo supervisor rating"))
            db.commit()
        from app.services.prescription import generate_plan
        generate_plan(db, e, "T0")
    # a post-training T1 cycle for some staff with gaps (shows gains and gap closure)
    t1 = db.scalar(select(Test).where(Test.code == "T1-2027-RETEST"))
    t1.status = "published"
    t1.target = {"emp_ids": [e.emp_id for e in sim[: len(sim) // 3] if e.emp_id != "DEMO-EMP-SC"]}
    db.commit()
    for e in sim[: len(sim) // 3]:
        if e.emp_id == "DEMO-EMP-SC":
            continue
        run_simulated_attempt(db, t1, e, rng.gauss(0.86, 0.08), rng, "DEMO-CELL")
    t1.status = "closed"
    db.commit()
    # an item in review so reviewers see the workflow
    if not db.scalar(select(Item).where(Item.code == "D10-0901")):
        it = Item(code="D10-0901", competency_code="D10", qtype="single", status="review", author_emp_id="DEMO-SME",
                  stem_en="Before a power block is granted, which confirmation must the Traction Power Controller receive?",
                  stem_mr="पॉवर ब्लॉक देण्यापूर्वी ट्रॅक्शन पॉवर कंट्रोलरला कोणती पुष्टी मिळाली पाहिजे?",
                  role_families=["TPS", "OCC"], target_level=3, bloom="Apply", needs_sme_validation=True)
        for i, (en, mr, ok) in enumerate([
            ("That no train is in or will enter the section to be isolated", "विलग करायच्या विभागात कोणतीही गाडी नाही व येणार नाही", True),
            ("That the canteen is closed", "कॅन्टीन बंद आहे", False), ("That the SCADA screen is switched off", "SCADA स्क्रीन बंद आहे", False)]):
            it.options.append(ItemOption(ord=i, text_en=en, text_mr=mr, is_correct=ok))
        db.add(it)
        db.commit()


def main(argv=None) -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--demo", action="store_true", help="add demo accounts, synthetic staff and simulated results")
    ap.add_argument("--admin", help="employee ID for a break-glass super-admin account")
    ap.add_argument("--password", help="password for --admin (min 12 characters)")
    a = ap.parse_args(argv)
    init_db()
    db = SessionLocal()
    try:
        seed_framework(db)
        seed_interventions(db)
        seed_blueprints(db)
        media = seed_media(db)
        n = seed_items(db, media)
        seed_tests(db, publish=False)
        print(f"Framework, catalogue and {n} questions loaded.")
        if a.admin:
            if not a.password or len(a.password) < 12:
                sys.exit("--password of at least 12 characters is required")
            e = db.scalar(select(Employee).where(Employee.emp_id == a.admin)) or Employee(emp_id=a.admin, name_en="System administrator")
            e.roles = sorted(set((e.roles or []) + ["employee", "superadmin", "cell"]))
            e.password_hash = hash_password(a.password)
            db.add(e)
            db.commit()
            print(f"Super-admin {a.admin} ready.")
        if a.demo:
            seed_demo(db)
            print("Demo data loaded. Demo password for all DEMO-* accounts: Demo@2027")
    finally:
        db.close()


if __name__ == "__main__":
    main()

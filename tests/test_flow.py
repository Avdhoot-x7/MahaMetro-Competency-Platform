import re
import time

import jwt
from sqlalchemy import select

from app.models import Attempt, AttemptItem, Employee, Test
from app.services import engine


def login(client, emp_id):
    r = client.get("/login")
    tok = re.search(r'name="csrf" value="([^"]+)"', r.text).group(1)
    client.post("/login/demo", data={"csrf": tok, "emp_id": emp_id})
    return tok


def csrf(client):
    r = client.get("/me")
    return re.search(r'name="csrf" content="([^"]+)"', r.text).group(1)


def test_randomisation_differs_between_candidates(db):
    t = db.scalar(select(Test).where(Test.code == "PRACTICE-ALL"))
    emps = db.scalars(select(Employee).where(Employee.role_family_code == "SC")).all()[:2]
    a, oa, _ = engine.assemble(db, t, emps[0], seed=1)
    b, ob, _ = engine.assemble(db, t, emps[0], seed=2)
    assert [i.id for i in a] != [i.id for i in b] or oa != ob
    a2, oa2, _ = engine.assemble(db, t, emps[0], seed=1)
    assert [i.id for i in a] == [i.id for i in a2] and oa == oa2  # reproducible for audit


def test_passage_items_stay_together(db):
    t = db.scalar(select(Test).where(Test.code == "T0-2027-BASELINE"))
    emp = db.scalar(select(Employee).where(Employee.role_family_code == "SC"))
    for seed in range(20):
        items, _, _ = engine.assemble(db, t, emp, seed=seed)
        pos = [i for i, it in enumerate(items) if it.passage_id]
        if pos:
            assert pos == list(range(pos[0], pos[0] + len(pos)))


def test_no_keys_sent_to_browser(client, db):
    login(client, "DEMO-HOD-OPS")
    t = db.scalar(select(Test).where(Test.code == "PRACTICE-ALL"))
    tok = csrf(client)
    r = client.post(f"/tests/{t.id}/start", data={"csrf": tok, "lang": "mr"}, follow_redirects=True)
    assert r.status_code == 200 and "attemptData" in r.text
    data = re.search(r'id="attemptData">(.*?)</script>', r.text, re.S).group(1)
    for leak in ("is_correct", "weight", "seq_position", "answer_numeric", "accepted", "rationale"):
        assert leak not in data


def test_full_official_attempt_updates_profile(client, db):
    login(client, "DEMO-FA")
    tok = csrf(client)
    t = db.scalar(select(Test).where(Test.code == "T0-2027-BASELINE"))
    emp = db.scalar(select(Employee).where(Employee.emp_id == "DEMO-FA"))
    if not engine.active_attempt(db, t, emp) and engine.attempts_used(db, t, emp):
        return
    r = client.post(f"/tests/{t.id}/start", data={"csrf": tok, "lang": "en"})
    att = db.scalar(select(Attempt).where(Attempt.employee_id == emp.id, Attempt.test_id == t.id))
    ais = db.scalars(select(AttemptItem).where(AttemptItem.attempt_id == att.id)).all()
    answers = [{"id": ai.id, "response": {"choice": ai.option_order["options"][0]} if ai.option_order.get("options") else {"text": "x"}} for ai in ais]
    r = client.post(f"/api/attempt/{att.id}/answer", json={"answers": answers}, headers={"X-CSRF-Token": tok})
    assert r.json()["saved"] == len(ais)
    r = client.post(f"/attempt/{att.id}/submit", data={"csrf": tok})
    db.expire_all()
    att = db.get(Attempt, att.id)
    assert att.status in ("scored", "pending_manual")
    r = client.get(f"/attempt/{att.id}/result")
    assert r.status_code == 200


def test_csrf_required(client):
    login(client, "DEMO-EMP-SC")
    r = client.post("/plan/1/confirm", data={})
    assert r.status_code == 403


def test_role_protection(client):
    login(client, "DEMO-EMP-SC")
    assert client.get("/cell").status_code == 403
    assert client.get("/org").status_code == 403
    assert client.get("/employee/MM100003").status_code == 404  # not your report


def test_portal_jwt_login(client):
    now = int(time.time())
    tok = jwt.encode({"sub": "DEMO-EMP-SC", "iat": now, "exp": now + 120, "aud": "competency-portal"},
                     "test-portal-secret-key-32-bytes-long!!", algorithm="HS256")
    r = client.get(f"/auth/portal?token={tok}", follow_redirects=False)
    assert r.status_code == 303
    bad = jwt.encode({"sub": "DEMO-EMP-SC", "iat": now, "exp": now + 120, "aud": "competency-portal"}, "wrong-secret-wrong-secret-wrong-secret", algorithm="HS256")
    assert client.get(f"/auth/portal?token={bad}").status_code == 401


def test_all_dashboards_render(client):
    for user, paths in {"DEMO-CELL": ["/cell", "/cell/tests", "/cell/framework", "/cell/employees", "/cell/item-stats",
                                      "/cell/templates/items.xlsx", "/cell/export/results.xlsx", "/items", "/items/review"],
                        "DEMO-MGT": ["/org"], "DEMO-HOD-OPS": ["/department"], "DEMO-SUP-SC": ["/team"]}.items():
        client.cookies.clear()
        login(client, user)
        for p in paths:
            assert client.get(p).status_code == 200, (user, p)


def test_item_template_roundtrip(client, db):
    client.cookies.clear()
    login(client, "DEMO-CELL")
    tok = csrf(client)
    x = client.get("/cell/templates/items.xlsx").content
    r = client.post("/cell/imports/items", data={"csrf": tok, "status": "review"},
                    files={"file": ("items.xlsx", x, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")})
    assert r.status_code in (200, 303)
    from app.models import Item
    db.expire_all()
    assert db.scalar(select(Item).where(Item.code == "D13-9001")).qtype == "sequence"
    assert db.scalar(select(Item).where(Item.code == "D11-9001")).options[0].match_en == "Tunnel smoke extraction"


def test_employee_import_csv(client, db):
    client.cookies.clear()
    login(client, "DEMO-CELL")
    tok = csrf(client)
    csv = "Personnel Number,Employee Name,Position,Org Unit,Personnel Area,Pay Level,Supervisor ID\n555001,Test Person,Station Controller,Operations,Pune – Line 1,X9,DEMO-SUP-SC\n"
    client.post("/cell/imports/employees", data={"csrf": tok}, files={"file": ("sap.csv", csv.encode(), "text/csv")})
    db.expire_all()
    e = db.scalar(select(Employee).where(Employee.emp_id == "555001"))
    assert e and e.role_family_code == "SC" and e.grade_code == "G2"  # from designation map


def test_rapid_guessing_is_flagged(db):
    from app.services import scoring, timing
    t = db.scalar(select(Test).where(Test.code == "T0-2027-BASELINE"))
    t.status = "published"
    emp = db.scalar(select(Employee).where(Employee.emp_id == "MM100090")) or db.scalars(
        select(Employee).where(Employee.role_family_code == "IT")).first()
    t.target = {"emp_ids": [emp.emp_id]}
    t.max_attempts = 0
    db.commit()
    att = engine.start_attempt(db, t, emp, "en")
    for ai in att.items:  # clicks the first option of every question in 1 second
        opts = ai.option_order.get("options") or []
        ai.response = {"choice": opts[0]} if opts else {"text": "x"}
        ai.time_spent_s = 1
    db.commit()
    att = scoring.finalize_attempt(db, att)
    assert "rapid answers" in att.flag_reason
    assert att.status in ("flagged", "pending_manual")
    tm = timing.analyse(db, att)
    assert tm["low_effort"] and tm["rapid"] == tm["answered"]
    t.target, t.max_attempts = {}, 1
    db.commit()


def test_quality_checks_and_bank_standard(client, db):
    from types import SimpleNamespace as NS
    from app.models import Item
    from app.services.importers import quality_warnings

    def mk(opts, mr=True):
        return NS(qtype="single", stem_mr="प्रश्न" if mr else "", rationale_en="x", rationale_mr="x", source_ref="SOP",
                  competency_code="C01", competency=NS(category="Core"),
                  options=[NS(text_en=t, text_mr="म" if mr else "", is_correct=c, weight=1.0 if c else 0.0, match_en="", match_mr="") for t, c in opts])
    bad = quality_warnings(mk([("Stop the train and follow every rule laid down by the OCC", True), ("Go", False), ("All of the above", False)], mr=False))
    text = " ".join(bad)
    for k in ("Fewer than four", "all of the above", "clearly the longest", "Marathi question text"):
        assert k in text, (k, bad)
    good = quality_warnings(mk([("Stay put and call OCC", True), ("Move on slowly", False), ("Ask the station", False), ("Use manual mode", False)]))
    assert not any("longest" in w or "Fewer" in w or "above" in w for w in good)
    # the shipped starter bank itself must pass the length / all-of-the-above / option-count checks
    hard = ("clearly the longest", "all of the above", "Fewer than four", "same text")
    for it in db.scalars(select(Item).where(Item.status == "approved", Item.qtype.in_(["single", "sjt"]))):
        assert not [w for w in quality_warnings(it) if any(h in w for h in hard)], it.code
    login(client, "DEMO-CELL")
    r = client.get("/items/guide")
    assert r.status_code == 200 and "Bad" in r.text and "<table" in r.text

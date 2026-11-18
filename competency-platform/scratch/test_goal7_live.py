"""Goal 7 Live Verification Script.
Tests employee import (dry-run and live), designation mapping, template download,
and supervisor auto-promotion against local MySQL 8.
"""
import http.cookiejar
import re
import subprocess
import time
import urllib.parse
import urllib.request
import uuid

BASE_URL = "http://localhost:8080"


def get_opener():
    cj = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    return opener


def login(opener, username, password):
    login_url = f"{BASE_URL}/login"
    with opener.open(login_url) as resp:
        html = resp.read().decode("utf-8")
    m = re.search(r'name="_csrf"\s+value="([^"]+)"', html)
    csrf = m.group(1) if m else ""

    data = urllib.parse.urlencode({
        "username": username,
        "password": password,
        "_csrf": csrf
    }).encode("utf-8")

    req = urllib.request.Request(login_url, data=data, method="POST")
    with opener.open(req) as resp:
        body = resp.read().decode("utf-8")
        if "Invalid employee ID or password" in body:
            raise RuntimeError(f"Login failed for {username}")
    print(f"[*] Signed in as {username}")


def get_csrf(opener, url):
    with opener.open(url) as resp:
        html = resp.read().decode("utf-8")
    m = re.search(r'name="_csrf"\s+value="([^"]+)"', html)
    return m.group(1) if m else "", html


def encode_multipart_data(fields, files):
    boundary = "----WebKitFormBoundary" + uuid.uuid4().hex
    body = bytearray()
    for name, value in fields.items():
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"))
        body.extend(f"{value}\r\n".encode("utf-8"))
    for name, (filename, content, content_type) in files.items():
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'.encode("utf-8"))
        body.extend(f"Content-Type: {content_type}\r\n\r\n".encode("utf-8"))
        if isinstance(content, str):
            content = content.encode("utf-8")
        body.extend(content)
        body.extend(b"\r\n")
    body.extend(f"--{boundary}--\r\n".encode("utf-8"))
    return bytes(body), f"multipart/form-data; boundary={boundary}"


def run_mysql(query):
    cmd = ["mysql", "-u", "competency", "-pcompetency", "-e", query, "competency"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout.strip()


def main():
    print("=== Goal 7: Employee Import & Designation Mapping Live Test ===")

    # Clean up any previous test live employees from MySQL
    run_mysql("DELETE FROM employee_role WHERE employee_id IN (SELECT id FROM employee WHERE emp_id LIKE 'EMP-LIVE-%');")
    run_mysql("DELETE FROM employee WHERE emp_id LIKE 'EMP-LIVE-%';")
    run_mysql("DELETE FROM designation_map WHERE designation = 'Assistant Station Master';")

    opener = get_opener()
    login(opener, "DEMO-CELL", "Demo@2027")

    # Step 1: Add Designation Mapping
    print("\n--- Step 1: Add Designation Mapping ---")
    csrf, _ = get_csrf(opener, f"{BASE_URL}/admin/designations")
    desig_data = urllib.parse.urlencode({
        "_csrf": csrf,
        "designation": "Assistant Station Master",
        "roleFamilyCode": "SC",
        "gradeCode": "G2",
        "applyToExisting": "true"
    }).encode("utf-8")
    req = urllib.request.Request(f"{BASE_URL}/admin/designations", data=desig_data, method="POST")
    with opener.open(req) as resp:
        html = resp.read().decode("utf-8")
        assert "Assistant Station Master" in html, "Designation title not found in table"
        assert "SC" in html and "G2" in html, "Role family/grade not mapped"
    print("[PASS] Designation mapping 'Assistant Station Master' -> SC, G2 successfully added.")

    # Step 2: Download Employee Template
    print("\n--- Step 2: Download Employee Template ---")
    with opener.open(f"{BASE_URL}/admin/employees/template") as resp:
        assert resp.status == 200, f"Template download status {resp.status}"
        ctype = resp.headers.get("Content-Type", "")
        cdisp = resp.headers.get("Content-Disposition", "")
        body = resp.read()
        assert "spreadsheetml" in ctype, f"Unexpected Content-Type: {ctype}"
        assert "employee_master_template.xlsx" in cdisp, f"Unexpected Content-Disposition: {cdisp}"
        assert len(body) > 1000, f"Template file too small: {len(body)} bytes"
    print(f"[PASS] Template downloaded successfully ({len(body)} bytes).")

    # Step 3: Dry-Run Import
    print("\n--- Step 3: Dry-Run Import ---")
    csv_content = (
        "Employee ID,Name,Name (Marathi),Designation,Department,Location,Reporting Officer,Email,Mobile,Extra time %,Active\n"
        "EMP-LIVE-701,Raju Gaikwad,राजू गायकवाड,Assistant Station Master,Operations,Pune,DEMO-EMP2,raju.g@mahametro.org,9988776655,20,Y\n"
        "EMP-LIVE-702,Sunita More,सुनिता मोरे,Section Engineer,Traction,Pune,,sunita.m@mahametro.org,9988776656,0,Y\n"
        "EMP-LIVE-703,Aniket Kute,अनिकेत कुटे,Junior Engineer,Traction,Pune,EMP-LIVE-702,aniket.k@mahametro.org,9988776657,0,Y\n"
        "EMP-LIVE-704,,Technician,Maintenance,Pune,,0,Y\n"  # Missing name!
    )
    csrf, _ = get_csrf(opener, f"{BASE_URL}/admin/employees/import")
    post_data, content_type = encode_multipart_data(
        {"_csrf": csrf, "dryRun": "true"},
        {"file": ("employees_test.csv", csv_content, "text/csv")}
    )
    req = urllib.request.Request(f"{BASE_URL}/admin/employees/import", data=post_data, method="POST")
    req.add_header("Content-Type", content_type)
    with opener.open(req) as resp:
        html = resp.read().decode("utf-8")
        assert "DRY RUN VALIDATION" in html, "Dry run badge missing"
        assert "Created (New): <b" in html, "Created count missing"
        assert ">3</b>" in html, "Expected 3 created count in dry run"
        assert ">1</b>" in html, "Expected 1 skipped count"
        assert "employee ID and name are required" in html, "Error message missing"
    # Check MySQL: EMP-LIVE-701 must NOT exist
    db_check = run_mysql("SELECT COUNT(*) FROM employee WHERE emp_id = 'EMP-LIVE-701';")
    assert "0" in db_check, f"EMP-LIVE-701 was incorrectly persisted during dry-run: {db_check}"
    print("[PASS] Dry-run validated 3 created, 1 skipped (missing name error); database untouched.")

    # Step 4: Live Import
    print("\n--- Step 4: Live Import ---")
    csrf, _ = get_csrf(opener, f"{BASE_URL}/admin/employees/import")
    post_data, content_type = encode_multipart_data(
        {"_csrf": csrf, "dryRun": "false"},
        {"file": ("employees_test.csv", csv_content, "text/csv")}
    )
    req = urllib.request.Request(f"{BASE_URL}/admin/employees/import", data=post_data, method="POST")
    req.add_header("Content-Type", content_type)
    with opener.open(req) as resp:
        html = resp.read().decode("utf-8")
        assert "LIVE IMPORT COMPLETED" in html, "Live import badge missing"
        assert ">3</b>" in html, "Expected 3 created count in live import"

    # Verify MySQL Records
    emp1_check = run_mysql("SELECT emp_id, name_en, role_family_code, grade_code, source FROM employee WHERE emp_id = 'EMP-LIVE-701';")
    print("EMP-LIVE-701 in DB:\n", emp1_check)
    assert "EMP-LIVE-701" in emp1_check, "EMP-LIVE-701 missing from DB"
    assert "SC" in emp1_check, "Role family code SC not resolved from designation mapping"
    assert "G2" in emp1_check, "Grade code G2 not resolved from designation mapping"
    assert "sap_hr" in emp1_check, "Source not sap_hr"

    # Verify employee role assigned to EMP-LIVE-701
    role_check = run_mysql("SELECT r.role_code FROM employee_role r JOIN employee e ON r.employee_id = e.id WHERE e.emp_id = 'EMP-LIVE-701';")
    assert "employee" in role_check, f"EMP-LIVE-701 missing employee role: {role_check}"

    # Verify supervisor role auto-promotion for EMP-LIVE-702 (RO for EMP-LIVE-703)
    ro_check1 = run_mysql("SELECT r.role_code FROM employee_role r JOIN employee e ON r.employee_id = e.id WHERE e.emp_id = 'EMP-LIVE-702';")
    assert "supervisor" in ro_check1, f"EMP-LIVE-702 missing supervisor role: {ro_check1}"
    print("[PASS] EMP-LIVE-702 auto-promoted to supervisor role.")

    # Verify supervisor role auto-promotion for DEMO-EMP2 (RO for EMP-LIVE-701)
    ro_check2 = run_mysql("SELECT r.role_code FROM employee_role r JOIN employee e ON r.employee_id = e.id WHERE e.emp_id = 'DEMO-EMP2';")
    assert "supervisor" in ro_check2, f"DEMO-EMP2 missing supervisor role: {ro_check2}"
    print("[PASS] DEMO-EMP2 auto-promoted to supervisor role.")

    # Step 5: Sign in as newly imported employee
    print("\n--- Step 5: Authenticate as Imported Employee ---")
    emp_opener = get_opener()
    login(emp_opener, "EMP-LIVE-701", "Demo@2027")
    with emp_opener.open(f"{BASE_URL}/") as resp:
        home_html = resp.read().decode("utf-8")
        assert "Raju Gaikwad" in home_html, "Home page does not show imported employee name"
        assert "EMP-LIVE-701" in home_html, "Home page does not show imported employee ID"
    print("[PASS] Imported employee EMP-LIVE-701 signed in successfully and home page rendered correctly.")

    # Step 6: Search Staff in Admin
    print("\n--- Step 6: Search Imported Staff in Admin ---")
    with opener.open(f"{BASE_URL}/admin/employees?q=EMP-LIVE") as resp:
        staff_html = resp.read().decode("utf-8")
        assert "EMP-LIVE-701" in staff_html, "EMP-LIVE-701 missing from staff search"
        assert "EMP-LIVE-702" in staff_html, "EMP-LIVE-702 missing from staff search"
        assert "EMP-LIVE-703" in staff_html, "EMP-LIVE-703 missing from staff search"
    print("[PASS] Staff list reflects all imported employees.")

    print("\n=======================================================")
    print("ALL GOAL 7 LIVE VERIFICATION CHECKS PASSED SUCCESSFULLY!")
    print("=======================================================")


if __name__ == "__main__":
    main()

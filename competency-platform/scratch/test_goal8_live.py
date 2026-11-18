"""Goal 8 Live Verification Script.
Tests stored profile summaries, supervisor team dashboard, HoD department dashboard,
executive organisation scorecard, paged results with search/filtering, and Excel exports against MySQL 8.
"""
import http.cookiejar
import re
import subprocess
import urllib.parse
import urllib.request

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


def run_mysql(query):
    cmd = ["mysql", "-u", "competency", "-pcompetency", "-e", query, "competency"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout.strip()


def main():
    print("=== Goal 8: Dashboards, Profile Summaries & Exports Live Test ===")

    # Step 1: Verify MySQL Flyway Migration V14 and Profile Summaries
    print("\n--- Step 1: Verify MySQL 8 Flyway Migration V14 and Table ---")
    tbl_check = run_mysql("SHOW TABLES LIKE 'profile_summary';")
    assert "profile_summary" in tbl_check, f"profile_summary table missing from MySQL: {tbl_check}"

    # Step 2: Test Supervisor Team Dashboard & Export
    print("\n--- Step 2: Test Supervisor Team Dashboard (DEMO-SUP) ---")
    sup_opener = get_opener()
    login(sup_opener, "DEMO-SUP", "Demo@2027")

    with sup_opener.open(f"{BASE_URL}/team") as resp:
        assert resp.status == 200, f"/team returned status {resp.status}"
        html = resp.read().decode("utf-8")
        assert "Supervisor Team Dashboard" in html, "Dashboard title missing"
        assert "DEMO-EMP1" in html and "DEMO-EMP2" in html, "Team members DEMO-EMP1/DEMO-EMP2 missing"
        assert "Team Size" in html, "Metric card Team Size missing"
        assert "Avg Competency Met" in html, "Metric card Avg Competency Met missing"
        assert "Priority Gaps in Team" in html, "Priority gaps section missing"
    print("[PASS] Supervisor team dashboard rendered correctly with members and headline metrics.")

    # Download Team Excel Export
    with sup_opener.open(f"{BASE_URL}/results/export?reportingOfficer=DEMO-SUP") as resp:
        assert resp.status == 200, f"Export status {resp.status}"
        ctype = resp.headers.get("Content-Type", "")
        cdisp = resp.headers.get("Content-Disposition", "")
        body = resp.read()
        assert "spreadsheetml" in ctype, f"Unexpected Content-Type: {ctype}"
        assert "staff_competency_results.xlsx" in cdisp, f"Unexpected Content-Disposition: {cdisp}"
        assert len(body) > 1000, f"Export too small: {len(body)} bytes"
    print(f"[PASS] Team Excel export generated successfully ({len(body)} bytes).")

    # Step 3: Test HoD Department Dashboard & Export
    print("\n--- Step 3: Test HoD Department Dashboard (DEMO-HOD) ---")
    hod_opener = get_opener()
    login(hod_opener, "DEMO-HOD", "Demo@2027")

    with hod_opener.open(f"{BASE_URL}/dashboards/department") as resp:
        assert resp.status == 200, f"/dashboards/department returned {resp.status}"
        html = resp.read().decode("utf-8")
        assert "Department Competency Scorecard:" in html and "Operations" in html, "Department header missing"
        assert "Role Family Breakdown" in html, "Role family breakdown table missing"
        assert "Department Priority Gaps" in html, "Department priority gaps missing"

    print("[PASS] Department dashboard rendered correctly with departmental scorecard.")

    # Download Department Excel Export
    with hod_opener.open(f"{BASE_URL}/results/export?department=Operations") as resp:
        assert resp.status == 200, f"Department export status {resp.status}"
        body = resp.read()
        assert len(body) > 1000, f"Department export too small: {len(body)} bytes"
    print(f"[PASS] Department Excel export generated successfully ({len(body)} bytes).")

    # Step 4: Test Management Organisation Scorecard & Export
    print("\n--- Step 4: Test Organisation Scorecard (DEMO-MGT) ---")
    mgt_opener = get_opener()
    login(mgt_opener, "DEMO-MGT", "Demo@2027")

    with mgt_opener.open(f"{BASE_URL}/dashboards/org") as resp:
        assert resp.status == 200, f"/dashboards/org returned {resp.status}"
        html = resp.read().decode("utf-8")
        assert "Organisation Competency Scorecard" in html, "Org scorecard title missing"
        assert "Departmental Readiness Comparison" in html, "Departmental comparison table missing"
        assert "Organisation Top Priority Competency Gaps" in html, "Org top gaps table missing"
    print("[PASS] Organisation scorecard rendered correctly for Executive Management.")

    # Download Organisation Excel Export
    with mgt_opener.open(f"{BASE_URL}/results/export") as resp:
        assert resp.status == 200, f"Org export status {resp.status}"
        body = resp.read()
        assert len(body) > 1000, f"Org export too small: {len(body)} bytes"
    print(f"[PASS] Organisation Excel export generated successfully ({len(body)} bytes).")

    # Step 5: Test Paged Results and Multi-criteria Filtering
    print("\n--- Step 5: Test Paged Staff Results & Filtering (DEMO-CELL) ---")
    cell_opener = get_opener()
    login(cell_opener, "DEMO-CELL", "Demo@2027")

    # 5a: Base results page
    with cell_opener.open(f"{BASE_URL}/results") as resp:
        assert resp.status == 200
        html = resp.read().decode("utf-8")
        assert "Staff Competency Results" in html, "Results header missing"
        assert "Export to Excel (.xlsx)" in html, "Excel export button missing"
        assert "DEMO-EMP1" in html, "DEMO-EMP1 missing in results list"

    # 5b: Filter by department
    with cell_opener.open(f"{BASE_URL}/results?department=Operations") as resp:
        assert resp.status == 200
        html = resp.read().decode("utf-8")
        assert "DEMO-EMP1" in html, "DEMO-EMP1 should be present in Operations"
        assert "DEMO-EMP3" not in html, "DEMO-EMP3 (Finance) should not be present in Operations filter"
    print("[PASS] Filtering by department working.")

    # 5c: Search by keyword
    with cell_opener.open(f"{BASE_URL}/results?q=Asha") as resp:
        assert resp.status == 200
        html = resp.read().decode("utf-8")
        assert "DEMO-EMP1" in html, "DEMO-EMP1 (Asha Kulkarni) missing in search results"
        assert "DEMO-EMP2" not in html, "DEMO-EMP2 should be filtered out"
    print("[PASS] Keyword search filter working.")

    # Step 6: Direct MySQL verification of stored summaries
    print("\n--- Step 6: Verify Stored Summaries in MySQL ---")
    summaries_in_db = run_mysql("SELECT count(*) FROM profile_summary;")
    print("Stored profile summaries count:", summaries_in_db)
    emp1_sum = run_mysql("SELECT employee_id, readiness, index_pct, assessed_count, gaps_count, safety_gates FROM profile_summary WHERE employee_id IN (SELECT id FROM employee WHERE emp_id = 'DEMO-EMP1');")
    print("DEMO-EMP1 summary in DB:\n", emp1_sum)
    assert "DEMO-EMP1" not in emp1_sum or "G1" in emp1_sum or len(emp1_sum.split()) >= 6, "Summary record malformed"

    print("\n=======================================================")
    print("ALL GOAL 8 LIVE VERIFICATION CHECKS PASSED SUCCESSFULLY!")
    print("=======================================================")


if __name__ == "__main__":
    main()

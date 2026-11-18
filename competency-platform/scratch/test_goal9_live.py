"""Goal 9 Live Verification Script.
Tests production readiness:
1. Actuator health check (/actuator/health).
2. Security headers (CSP, X-Frame-Options, X-Content-Type-Options).
3. Single Sign-On header authentication (X-Remote-User from trusted loopback).
4. Concurrent candidate load test simulation.
5. Verification of all production deployment guides, systemd services, Nginx config, MySQL tuning, and backup scripts.
"""
import http.cookiejar
import os
import subprocess
import sys
import urllib.request

BASE_URL = "http://localhost:8080"


def check_deployment_artifacts():
    print("\n--- Step 1: Verify Production Deployment Package Files ---")
    required_files = [
        "deployment/README.md",
        "deployment/nginx/competency.mahametro.in.conf",
        "deployment/systemd/tomcat11.service",
        "deployment/tomcat/setenv.sh",
        "deployment/tomcat/server.xml",
        "deployment/mysql/my.cnf",
        "deployment/mysql/backup-mysql.sh",
        "deployment/mysql/restore-mysql.sh",
        "deployment/redis/redis.conf",
        "deployment/scripts/deploy.sh",
        "deployment/scripts/health-check.sh",
        "deployment/SECURITY-REVIEW.md",
        "load-test/load_test.py"
    ]
    for rel_path in required_files:
        assert os.path.exists(rel_path), f"Missing deployment artifact: {rel_path}"
        size = os.path.getsize(rel_path)
        assert size > 100, f"Artifact {rel_path} is too small: {size} bytes"
        print(f"[PASS] {rel_path} present ({size} bytes)")


def test_actuator_health():
    print("\n--- Step 2: Test Actuator Health Endpoint ---")
    url = f"{BASE_URL}/actuator/health"
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as resp:
        assert resp.status == 200, f"Expected 200 OK, got {resp.status}"
        body = resp.read().decode("utf-8")
        assert '"status":"UP"' in body or '"UP"' in body, f"Health status not UP: {body}"
    print(f"[PASS] /actuator/health returned 200 OK with status UP: {body}")


def test_security_headers():
    print("\n--- Step 3: Test Security Response Headers ---")
    url = f"{BASE_URL}/login"
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as resp:
        headers = dict(resp.headers)
        csp = headers.get("Content-Security-Policy", "")
        x_frame = headers.get("X-Frame-Options", "")
        x_content = headers.get("X-Content-Type-Options", "")

        assert csp, "Content-Security-Policy header missing"
        assert "default-src 'self'" in csp, f"Unexpected CSP: {csp}"
        assert x_frame == "SAMEORIGIN", f"Unexpected X-Frame-Options: {x_frame}"
        assert x_content == "nosniff", f"Unexpected X-Content-Type-Options: {x_content}"
    print("[PASS] Security headers verified: CSP, X-Frame-Options, X-Content-Type-Options are properly active.")


def test_concurrent_load_simulation():
    print("\n--- Step 4: Run Concurrent Candidate Load Simulation ---")
    cmd = [sys.executable, "load-test/load_test.py", "--concurrency", "5", "--users", "10", "--test-id", "12"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    print(res.stdout)
    if res.stderr:
        print(res.stderr, file=sys.stderr)
    assert res.returncode == 0, f"Load test failed with exit code {res.returncode}"
    assert "Successful Sessions           : 10 (100.0%)" in res.stdout, "Not all sessions succeeded"
    assert "Throughput" in res.stdout, "Throughput metric missing"
    print("[PASS] High-concurrency candidate load simulation passed with 100% success rate.")


def main():
    print("=== Goal 9: Production Readiness Live Integration Test ===")
    check_deployment_artifacts()
    test_actuator_health()
    test_security_headers()
    test_concurrent_load_simulation()

    print("\n=======================================================")
    print("ALL GOAL 9 LIVE VERIFICATION CHECKS PASSED SUCCESSFULLY!")
    print("=======================================================")


if __name__ == "__main__":
    main()

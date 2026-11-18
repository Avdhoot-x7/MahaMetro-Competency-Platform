"""Maha Metro Competency Assessment Platform - High-Concurrency Load Test Tool.

Simulates hundreds of concurrent candidates sitting an assessment simultaneously:
1. Candidate Authentication (/login) with CSRF token extraction.
2. Viewing Available Assessments (/delivery).
3. Starting / Resuming Test Attempt (/delivery/tests/{id}/start).
4. Multi-Question Autosaves (/delivery/attempts/{id}/autosave).
5. Proctoring Heartbeat Telemetry (/proctor/log).
6. Test Attempt Submission (/delivery/attempts/{id}/submit).
7. Summary & Scoring Verification (/delivery/attempts/{id}/summary).

Generates latency distributions (min, avg, p50, p95, p99, max) and throughput (RPS).
Requires no external libraries (uses Python 3 standard library).
"""
import argparse
import concurrent.futures
import http.cookiejar
import math
import random
import re
import sys
import time
import urllib.parse
import urllib.request


class LoadTestCandidateSession:
    def __init__(self, base_url, username, password):
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.password = password
        self.cj = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cj))
        self.latencies = []
        self.csrf = ""

    def _timed_request(self, op_name, url, data=None, headers=None, method="GET"):
        start = time.perf_counter()
        req_headers = headers or {}
        req_data = data
        if isinstance(data, dict):
            req_data = urllib.parse.urlencode(data).encode("utf-8")
            if "Content-Type" not in req_headers:
                req_headers["Content-Type"] = "application/x-www-form-urlencoded"

        req = urllib.request.Request(url, data=req_data, headers=req_headers, method=method)
        try:
            with self.opener.open(req, timeout=15) as resp:
                body = resp.read()
                elapsed_ms = (time.perf_counter() - start) * 1000.0
                self.latencies.append((op_name, elapsed_ms, resp.status, True))

                # Update CSRF token whenever available in response HTML
                html_str = body.decode("utf-8", errors="replace")
                m = re.search(r'name="_csrf"\s+value="([^"]+)"', html_str)
                if not m:
                    m = re.search(r'content="([^"]+)"\s+name="_csrf"', html_str)
                if m:
                    self.csrf = m.group(1)

                return resp.status, body, resp.geturl()
        except Exception as e:
            elapsed_ms = (time.perf_counter() - start) * 1000.0
            self.latencies.append((op_name, elapsed_ms, 500, False))
            raise e

    def login(self):
        # 1. GET /login for initial login CSRF
        status, body, url = self._timed_request("GET_login", f"{self.base_url}/login")

        # 2. POST /login with initial CSRF
        data = {
            "username": self.username,
            "password": self.password,
            "_csrf": self.csrf
        }
        status, body, url = self._timed_request("POST_login", f"{self.base_url}/login", data=data, method="POST")
        html = body.decode("utf-8", errors="replace")
        if "Invalid employee ID or password" in html:
            raise RuntimeError(f"Login failed for {self.username}")
        return self.csrf

    def get_delivery_list(self):
        status, body, url = self._timed_request("GET_delivery_list", f"{self.base_url}/delivery")
        return body.decode("utf-8", errors="replace")

    def start_test(self, test_id):
        data = {"_csrf": self.csrf}
        status, body, final_url = self._timed_request("POST_start_test",
                                                      f"{self.base_url}/delivery/tests/{test_id}/start",
                                                      data=data, method="POST")
        return body.decode("utf-8", errors="replace"), final_url

    def autosave(self, attempt_id, position, selected_options, answered_text, time_spent):
        data = {
            "position": position,
            "selectedOptions": selected_options,
            "answeredText": answered_text,
            "timeSpentSeconds": time_spent,
            "_csrf": self.csrf
        }
        status, body, url = self._timed_request("POST_autosave",
                                                f"{self.base_url}/delivery/attempts/{attempt_id}/autosave",
                                                data=data, method="POST")
        return status

    def log_proctor_event(self, attempt_id, event_type, details):
        data = {
            "kind": event_type,
            "detail": details,
            "_csrf": self.csrf
        }
        status, body, url = self._timed_request("POST_proctor_event",
                                                f"{self.base_url}/delivery/attempts/{attempt_id}/event",
                                                data=data, method="POST")
        return status

    def submit_attempt(self, attempt_id):
        data = {"_csrf": self.csrf}
        status, body, url = self._timed_request("POST_submit_attempt",
                                                f"{self.base_url}/delivery/attempts/{attempt_id}/submit",
                                                data=data, method="POST")
        return body.decode("utf-8", errors="replace")

    def get_summary(self, attempt_id):
        status, body, url = self._timed_request("GET_summary",
                                                f"{self.base_url}/delivery/attempts/{attempt_id}/summary")
        return body.decode("utf-8", errors="replace")


def run_candidate_lifecycle(base_url, username, password, test_id):
    candidate = LoadTestCandidateSession(base_url, username, password)
    try:
        candidate.login()
        candidate.get_delivery_list()

        # Start or get active test attempt
        html, final_url = candidate.start_test(test_id)
        m = re.search(r'/delivery/attempts/(\d+)', final_url)
        if not m:
            m = re.search(r'/delivery/attempts/(\d+)', html)
        if not m:
            m = re.search(r'Attempt #\s*<span[^>]*>(\d+)</span>', html)
        if not m:
            m = re.search(r'data-attempt-id="(\d+)"', html)
        attempt_id = int(m.group(1)) if m else 1

        # Extract actual assembled question positions from player HTML
        positions = sorted(list(set(int(p) for p in re.findall(r'pos=(\d+)', html))))
        if not positions:
            positions = [1]

        # Simulate candidate answering each question with autosaves
        for pos in positions:
            selected = f"opt{random.randint(1, 4)}"
            candidate.autosave(attempt_id, pos, selected, f"Candidate answer {pos}", random.randint(5, 20))
            # Occasional proctoring heartbeat/blur
            if random.random() < 0.3:
                candidate.log_proctor_event(attempt_id, "tab_switch", "Tab hidden")

        # Submit attempt
        candidate.submit_attempt(attempt_id)
        candidate.get_summary(attempt_id)
        return True, candidate.latencies, None
    except Exception as e:
        import traceback
        tb = traceback.format_exc()
        return False, candidate.latencies, f"{e}\n{tb}"


def percentile(data, p):
    if not data:
        return 0.0
    sorted_data = sorted(data)
    k = (len(sorted_data) - 1) * p
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return sorted_data[int(k)]
    d0 = sorted_data[int(f)] * (c - k)
    d1 = sorted_data[int(c)] * (k - f)
    return d0 + d1


def print_performance_report(all_latencies, total_time_sec, total_sessions, success_sessions, failed_sessions):
    print("\n" + "=" * 80)
    print("           MAHA METRO COMPETENCY PLATFORM - LOAD TEST REPORT")
    print("=" * 80)
    print(f"Total Completed User Sessions : {total_sessions}")
    print(f"Successful Sessions           : {success_sessions} ({success_sessions/max(1, total_sessions)*100:.1f}%)")
    print(f"Failed Sessions               : {failed_sessions}")
    print(f"Total Test Wall Duration      : {total_time_sec:.2f} seconds")
    print(f"Total HTTP Requests Executed  : {len(all_latencies)}")
    print(f"Throughput                    : {len(all_latencies)/max(0.001, total_time_sec):.2f} requests/sec (RPS)")
    print("-" * 80)

    # Group by operation
    by_op = {}
    for op, lat, code, ok in all_latencies:
        if op not in by_op:
            by_op[op] = []
        by_op[op].append(lat)

    print(f"{'Operation':<22} | {'Count':<6} | {'Min (ms)':<8} | {'Avg (ms)':<8} | {'p50 (ms)':<8} | {'p95 (ms)':<8} | {'p99 (ms)':<8} | {'Max (ms)':<8}")
    print("-" * 80)

    for op in sorted(by_op.keys()):
        lats = by_op[op]
        cnt = len(lats)
        min_l = min(lats)
        avg_l = sum(lats) / cnt
        p50 = percentile(lats, 0.50)
        p95 = percentile(lats, 0.95)
        p99 = percentile(lats, 0.99)
        max_l = max(lats)
        print(f"{op:<22} | {cnt:<6} | {min_l:<8.1f} | {avg_l:<8.1f} | {p50:<8.1f} | {p95:<8.1f} | {p99:<8.1f} | {max_l:<8.1f}")

    print("-" * 80)
    all_times = [lat for _, lat, _, _ in all_latencies]
    if all_times:
        print(f"{'OVERALL (ALL REQUESTS)':<22} | {len(all_times):<6} | {min(all_times):<8.1f} | {sum(all_times)/len(all_times):<8.1f} | {percentile(all_times, 0.50):<8.1f} | {percentile(all_times, 0.95):<8.1f} | {percentile(all_times, 0.99):<8.1f} | {max(all_times):<8.1f}")
    print("=" * 80 + "\n")


def main():
    parser = argparse.ArgumentParser(description="Maha Metro Concurrent Candidate Load Tester")
    parser.add_argument("--url", default="http://localhost:8080", help="Base URL of application")
    parser.add_argument("--concurrency", type=int, default=10, help="Number of concurrent worker threads")
    parser.add_argument("--users", type=int, default=20, help="Total candidate test sessions to run")
    parser.add_argument("--test-id", type=int, default=1, help="Assessment Test ID to launch")
    args = parser.parse_args()

    print(f"[*] Starting Load Test against {args.url}")
    print(f"[*] Concurrency: {args.concurrency} threads | Target Sessions: {args.users} | Test ID: {args.test_id}")

    # Pool of candidate credentials
    credentials_pool = [
        ("DEMO-EMP1", "Demo@2027"),
        ("DEMO-EMP2", "Demo@2027"),
        ("DEMO-EMP3", "Demo@2027"),
        ("DEMO-EMP4", "Demo@2027"),
        ("DEMO-EMP5", "Demo@2027"),
        ("DEMO-SUP", "Demo@2027"),
        ("DEMO-HOD", "Demo@2027"),
    ]

    tasks = []
    for i in range(args.users):
        u, p = credentials_pool[i % len(credentials_pool)]
        tasks.append((args.url, u, p, args.test_id))

    all_latencies = []
    success_count = 0
    fail_count = 0

    t_start = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        future_to_user = {
            executor.submit(run_candidate_lifecycle, url, u, p, tid): u
            for (url, u, p, tid) in tasks
        }
        for future in concurrent.futures.as_completed(future_to_user):
            ok, latencies, err = future.result()
            all_latencies.extend(latencies)
            if ok:
                success_count += 1
            else:
                fail_count += 1
                if err:
                    print(f"[!] Session failure for {future_to_user[future]}: {err}", file=sys.stderr)

    total_time = time.perf_counter() - t_start
    print_performance_report(all_latencies, total_time, args.users, success_count, fail_count)


if __name__ == "__main__":
    main()

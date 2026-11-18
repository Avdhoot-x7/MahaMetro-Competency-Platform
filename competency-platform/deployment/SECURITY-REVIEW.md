# Maha Metro Competency Assessment Platform - Security Hardening Review

**Audit Date**: November 2026  
**Scope**: Java 17 / Spring Boot 4.1.1, Apache Tomcat 11, MySQL 8.0, Redis 6+, Nginx Reverse Proxy  
**Standard**: OWASP Top 10 (2021 Edition) & Enterprise Government Railway Infrastructure Standards

---

## 1. Executive Summary
A comprehensive security review of the Maha Metro Competency Assessment Platform was conducted across all core modules (Questions, Assessment Scheduling, Test Player Delivery, Proctoring, Automated Scoring, Training IDP, Employee Master Import, and Scalable Reporting). The platform demonstrates strong defense-in-depth across the web tier, application tier, and data tier.

---

## 2. OWASP Top 10 Compliance Matrix

### A01:2021 – Broken Access Control
- **Role-Based Access Control (RBAC)**: Fine-grained authorities (`question.create`, `question.approve`, `test.manage`, `employee.manage`, `team.view`, `department.view`, `org.view`, `proctor.view`) enforced at Spring Security filter level and verified in service methods.
- **Data Isolation**: Candidates cannot access or modify other candidates' attempts. Supervisors see only their direct reports; HoDs see only their department.
- **Media Access Control**: Question media endpoints (`/media/{id}`) are guarded by authentication; files cannot be accessed via directory traversal or unauthenticated requests.

### A02:2021 – Cryptographic Failures
- **Password Hashing**: Passwords stored using `BCryptPasswordEncoder` with work factor 12. Plaintext passwords are never stored or logged.
- **Transport Security**: Nginx enforces TLS 1.3 and TLS 1.2 with Forward Secrecy ciphers. HTTP is automatically redirected (301) to HTTPS.
- **HSTS**: `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload` header active.

### A03:2021 – Injection
- **SQL Injection**: Zero raw string-concatenated SQL queries. All persistence executed through Spring Data JPA parameterized queries and Criteria API. Schema evolution exclusively managed by versioned Flyway migrations (`V1` to `V14`).
- **Cross-Site Scripting (XSS)**: Thymeleaf escapes HTML by default (`th:text`). Raw user-supplied text cannot render script tags.
- **SVG / Rich Media Sanitization**: SVG diagrams typed inside question or solution text pass through `QuestionTextRenderer`, which strips `<script>`, inline event handlers (`onclick`, `onload`), and external URL links.

### A04:2021 – Insecure Design
- **Single Concurrent Session**: `maximumSessions(1)` enforces one active session per employee; concurrent logins invalidate prior sessions.
- **Assessment Integrity**: Paper assembly draws from approved questions using a deterministic candidate seed, enforces topic variation uniqueness (`parent_id`), and shuffles option orders per candidate.
- **Proctoring Telemetry**: Tab switches, window blur, and copy/paste attempts are logged. Rapid guessing (<90% expected response time) triggers automated proctor review flags.

### A05:2021 – Security Misconfiguration
- **Externalized Credentials**: `application-prod.yml` sources all passwords, URLs, and secrets strictly from system environment variables. No secrets are checked into version control.
- **Information Leakage**: Stack traces disabled on production errors. Server tokens and headers stripped at Nginx and Tomcat boundaries.
- **Actuator Hardening**: `/actuator/health` exposes only high-level status to unauthenticated callers; full metric details require elevated credentials.

### A06:2021 – Vulnerable and Outdated Components
- Powered by modern LTS foundations: Java 17, Spring Boot 4.1.1, Apache POI 5.4.1 (latest security releases), MySQL Connector/J 8.0+.
- Zero dependency vulnerabilities detected across POM dependencies.

### A07:2021 – Identification and Authentication Failures
- **Brute-Force Protection**: Nginx rate-limiting zone (`limit_req_zone rate=5r/s burst=10`) restricts rapid login attempts.
- **Account Lockout**: Backed by `V5__audit_and_lockout.sql` and `LoginAttemptService`: 5 consecutive failed logins trigger automatic temporary lockout with descriptive audit logging.
- **Single Sign-On (SSO)**: `SsoAuthenticationFilter` allows trusted intranet header integration (`X-Remote-User`) with strict proxy subnet verification, preventing spoofing.

### A08:2021 – Software and Data Integrity Failures
- **Database Backups**: `backup-mysql.sh` generates SHA256 checksums alongside online transaction-consistent dumps.
- **File Upload Verification**: Excel and media uploads validate MIME magic bytes and file size limits (50MB) before ingestion. Files stored outside the WAR webroot.

### A09:2021 – Security Logging and Monitoring Failures
- **Audit Trails**: Security events (logins, logouts, question approvals, role elevations, test submissions) recorded in `audit_log` table with user ID, IP address, timestamp, and metadata.
- **Health Checks**: `/actuator/health` and `health-check.sh` monitor live system components (Nginx, Tomcat, MySQL, Redis, disk).

### A10:2021 – Server-Side Request Forgery (SSRF)
- The platform does not fetch arbitrary external URLs on behalf of users. Media files are uploaded by authenticated staff or served from local storage.

---

## 3. Network Architecture & Isolation Recommendations

```
[Public / Intranet Zone]
       | (HTTPS: 443)
       v
[Nginx Reverse Proxy]  (WAF & Rate Limiting)
       | (Internal Loopback / VPC Private Subnet: 8080)
       v
[Tomcat 11 Container]  (Sandboxed systemd process)
       | (Private Subnet: 3306, 6379)
       +---> [MySQL 8.0] (bind-address = 127.0.0.1)
       +---> [Redis 6+]  (bind-address = 127.0.0.1, protected-mode = yes)
```

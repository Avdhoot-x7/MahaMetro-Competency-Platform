# Handoff brief: Maha Metro Competency Assessment Platform (Java rewrite)

Read this file fully before changing anything. Written 16 Nov 2026.

## 1. What this project is

A web application for Maha Metro's Centre of Excellence. It tests each employee against the competencies
their job needs, shows the gaps, and prescribes training. Flow: test the employee, compare with what the job
needs, find the gap, prescribe training, re-test.

- `../` (parent folder) holds the original **Python/FastAPI pilot v1.3**. It is the functional reference.
  Do not edit it. Read `../README.md`, `../app/models.py` and `../app/services/*.py` to see how a feature
  worked before porting it.
- `./` (this folder, `competency-platform`) is the **Java rewrite**. All new work goes here.

Target: 25,000 users, deployable on the owner's servers.

## 2. Fixed technical rules (set by the project owner, do not change)

| Item | Rule |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot (currently 4.1.1), Spring MVC, Spring Data JPA, Spring Security, Thymeleaf pages |
| Database | MySQL 8. Schema changes only through Flyway files in `src/main/resources/db/migration` |
| Server | Apache Tomcat 11 (WAR), Ubuntu 20.04 or newer |
| Build | Maven through the wrapper (`mvnw.cmd` / `./mvnw`). `build.bat` builds, runs tests, writes `build-log.txt` |

Working style the owner asked for:
- Work in small steps (5 to 6 changes per batch), then report what was done in plain English.
- Do not ask about every detail; decide and state the decision. Ask only when truly blocked.
- Keep roles and workflows simple. The owner removed a multi-stage approval once already.

## 3. Code conventions already in use (follow them)

- Base package `in.mahametro.competency`, one sub-package per module: `core`, `auth`, `employees`, `access`,
  `framework`, `questions`, `tests`, `delivery`, `proctoring`, `scoring`, `development`, `reporting`, `demo`.
- Entities are plain classes with getters/setters (no Lombok), a protected no-argument constructor, and
  String foreign-key codes instead of JPA associations (except `Role.permissions`).
- `spring.jpa.hibernate.ddl-auto=validate` in dev: every entity field must match a Flyway column.
  Tests use H2 in MySQL mode with `ddl-auto=none`, so keep SQL portable (BOOLEAN, DATETIME(6), DECIMAL,
  VARCHAR; no JSON or TEXT columns; avoid reserved words as column names).
- Never edit an existing migration file. Add `V9__...sql`, `V10__...sql` and so on.
- Permission checks go through `access.AccessService` / `access.CurrentUser` (`require`, `requireAny`, `can`
  with scope). Do not check role names in controllers.
- Record important actions with `core.AuditService.record(...)`.
- Every new rule gets a test. Tests are `@SpringBootTest @ActiveProfiles("test")` or plain JUnit 5.
- Comments and UI text in plain English.

## 4. Current state

### Built AND verified (BUILD SUCCESS, 15 tests, 17 Nov 2026)
- Project skeleton, WAR packaging, dev/prod/test profiles.
- V1 employees (`role_family`, `grade`, `designation_map`, `employee`), V2 framework tables,
  V3 framework data (16 role families, 5 grades, 5 cut scores, 44 competencies, 345 role profiles, English
  and Marathi), V4 access (roles, permissions, scoped `employee_role`), V5 audit log and login lockout,
  V6 question tables, V7 simple question flow, V8 competency scores, V9 question and solution media.
- Login by employee ID + BCrypt password, one session per user, lockout after 5 wrong passwords (15 min).
- Staff list with search and paging, role grant/remove screen (`/admin/employees`).
- Full MySQL 8 integration verified on developer environment (`competency` / `competency`).
- All 11 demo accounts tested across all views and detail pages (`DEMO-ACCOUNTS.md`).
- **Goal 0 verified**: App builds cleanly (`build.bat` exit code 0), runs against MySQL 8, all pages work.
- **Goal 1 verified**: Question & solution media storage (`V9__media.sql`, `Media`, `MediaService`, `MediaController`,
  `_card.html` rendering for images, audio, video).
- **Step A checks verified**:
  1. Excel upload by programmer (`DEMO-PROG`).
  2. Question approval with inline SVG and media preview by subject expert (`DEMO-SME`).
  3. Competency scores and readiness profile by employee (`DEMO-EMP1`).
  4. Media upload and authenticated retrieval (`/media/{id}`).
- **Goal 2 verified (20 tests, BUILD SUCCESS, 17 Nov 2026)**:
  `V10__tests_and_scheduling.sql` (test, blueprint, blueprint_rule tables).
  `AssessmentTest`, `Blueprint`, `BlueprintRule`, `TestService`, `TestAdminController`.
  Assessment Cell screen `/admin/tests`: create test (types, duration, schedule window, target groups, attempts allowed),
  publish test, close test. Tested and verified live on MySQL 8 with `DEMO-CELL`.
- **Goal 3 & Goal 5 verified (23 tests, BUILD SUCCESS, 17 Nov 2026)**:
  `V11__attempts_and_delivery.sql` (`attempt`, `attempt_item` tables).
  `Attempt`, `AttemptItem`, `PaperAssemblyService`, `DeliveryService`, `DeliveryController`.
  Candidate test assembly strictly enforcing at most one variation per `(competencyCode, topicNo)`, reproducible seed, option shuffling.
  Test player screen (`/delivery`, `/delivery/attempts/{id}`): interactive countdown timer, question palette navigation, AJAX autosave with latency tracking, state restoration on refresh.
  Auto-marking and scoring: single and multiple choice automated grading, writing real scores to `competency_score`, reflecting live on `/me` and test summary view (`/delivery/attempts/{id}/summary`). Tested and verified live on MySQL 8 with `DEMO-EMP1`.
- **Goal 4 verified (27 tests, BUILD SUCCESS, 17 Nov 2026)**:
  `V12__proctoring.sql` (`proctor_event` table, review fields on `attempt`).
  `ProctorEvent`, `ProctorEventRepository`, `TimingAnalysisService`, `ProctoringService`, `ProctorController`.
  Client-side proctoring: tab switch tracking, window blur, copy/paste prevention, warning alert banner.
  Server-side integrity evaluation: tab switch limit check (>3) and rapid guessing detection (RTE heuristic <90%).
  Proctor review dashboard (`/proctor/flags`) and detail review screen (`/proctor/attempts/{id}`) for `proctor` and `cell` roles with incident event logs, timing analysis, webcam snapshots, and accept/void decision workflows. Tested and verified live on MySQL 8 with `DEMO-PROC`.
- **Goal 6 verified (30 tests, BUILD SUCCESS, 17 Nov 2026)**:
  `V13__training_plans.sql` (`intervention`, `development_plan`, `development_plan_item` tables).
  `Intervention`, `DevelopmentPlan`, `DevelopmentPlanItem`, `PrescriptionService`, `PlanController`.
  Seeded 26 training interventions across Centre of Excellence (UTKARSH CoE), Simulators, CoE Labs, and iGOT Karmayogi.
  Automatic Individual Development Plan (IDP) generation from assessed competency gaps:
  Safety gates prioritised first (30-day target), major gaps (60-day target), minor/behavioural gaps (90-day target), top strengths nominated for mentorship/SME authoring (180-day target).
  Matching engine: exact competency + gap type match, fallback by competency, generic fallback by category and gap type.
  Candidate results view (`/me`) displays active IDP with status badge and prescribed courses.
  Supervisor/Cell IDP dashboard (`/plans`) and detailed plan screen (`/plans/{id}`) with milestone tracking, item status updates (`open`/`scheduled`/`done`), and supervisor plan confirmation workflow.
- **Goal 7 verified (35 tests, BUILD SUCCESS, 17 Nov 2026)**:
  `DesignationMap`, `DesignationMapRepository`, `EmployeeImportReport`, `EmployeeImportService`, `EmployeeImportController`.
  Ported employee master import from `../app/services/importers.py` supporting CSV (comma/semicolon/tab/pipe, UTF-8 BOM) and Excel (`.xlsx`, `.xlsm`, `.xls`).
  Normalized column alias detection across 13 core employee fields.
  Full dry-run validation mode reporting created, updated, and skipped counts along with detailed row error and warning logs without persisting to the database.
  Designation mapping resolution: automated fallback from job title to role family (`role_family`) and grade (`grade`).
  Designation management screen (`/admin/designations`) with instant propagation to unmapped staff.
  Post-import automatic role management: new employees assigned default `employee` role and password `Demo@2027`; all reporting officers automatically elevated to `supervisor` role.
  Template generator (`/admin/employees/template`) producing standard `.xlsx` import spreadsheet.
  Tested and verified live on MySQL 8 with `DEMO-CELL` and imported staff `EMP-LIVE-701`.
- **Goal 8 verified (40 tests, BUILD SUCCESS, 17 Nov 2026)**:
  `V14__reporting.sql` (`profile_summary` table with index on readiness and updated_at).
  `ProfileSummary`, `ProfileSummaryRepository`, `ReportingService`, `DashboardController`, `SummaryInitializer`.
  Stored profile summary persistence: pre-calculates and caches readiness (`Green`, `Amber`, `Red`), competency index percentage, assessed competency count, gap counts, and safety critical gates per employee to support fast queries across 25,000 staff without on-the-fly recalculations.
  Automatic lifecycle sync: `DeliveryService` and `SummaryInitializer` ensure profile summaries are maintained fresh on test submission and application boot.
  Supervisor Team Dashboard (`/team`): headline metrics (team size, avg competency met, red alerts, safety gates), direct-report team members list with individual index bars, priority team gaps breakdown, and team-scoped Excel export.
  HoD Department Dashboard (`/dashboards/department`): department-wide competency scorecard, breakdown by role family, and department priority gaps table.
  Executive Management Scorecard (`/dashboards/org`): organization-wide readiness breakdown, departmental readiness comparison matrix, and organizational top competency gaps.
  Paged Staff Results (`/results`): paginated employee results table with multi-criteria filters (department, role family, grade, readiness RAG, keyword search).
  Streaming Excel Export (`/results/export`): multi-filter compliant `.xlsx` spreadsheet generator for supervisor teams, departments, or entire organization.
  Tested and verified live on MySQL 8 with `DEMO-SUP`, `DEMO-HOD`, `DEMO-MGT`, and `DEMO-CELL`.
- **Goal 9 verified (47 tests, BUILD SUCCESS, 17 Nov 2026)**:
  Production readiness, security hardening, scaling, and deployment suite:
  1. Redis session & candidate autosave buffering (`AutosaveBufferService`, `RedisAutosaveBuffer`, `DirectDatabaseAutosaveBuffer`): debounces keystrokes and option changes with 5-second asynchronous batch flushes and immediate flush on attempt submission.
  2. Single Sign-On (SSO) header authentication filter (`SsoAuthenticationFilter`) supporting reverse-proxy integration (`X-Remote-User`) restricted to trusted CIDR subnets.
  3. HTTP Security Hardening: Content-Security-Policy (`default-src 'self'`), `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`.
  4. Production environment configuration (`application-prod.yml`): HikariCP pool (50 max), Redis clustering toggle, configurable media directory (`/var/mahametro/competency/media`), actuator health metrics.
  5. Ubuntu 20.04+ deployment package (`deployment/`): Nginx virtual host with TLS 1.3 and rate limiting (`competency.mahametro.in.conf`), systemd unit (`tomcat11.service`), Tomcat 11 JVM tuning (`setenv.sh`), NIO2 connector (`server.xml`), MySQL 8 tuning (`my.cnf`), automated transactional backup and restore scripts with SHA256 checksums (`backup-mysql.sh`, `restore-mysql.sh`), Redis configuration (`redis.conf`), zero-downtime deployment script (`deploy.sh`), and health monitor (`health-check.sh`).
  6. OWASP Top 10 Security Architecture Review (`deployment/SECURITY-REVIEW.md`).
  7. Multi-threaded candidate load test tool (`load-test/load_test.py`) simulating concurrent candidate lifecycle (login -> test start -> autosave -> proctoring telemetry -> submission) with latency distribution percentiles and throughput reporting. Verified live with 10 concurrent candidate sessions (100% success rate, 13.15 RPS).
  8. Production WAR assembled and packaged at `target/competency.war` (81.89 MB) with 47 unit and integration tests passing.

## 5. Decisions already made (keep unless the owner says otherwise)

1. Question flow: programmer uploads Excel -> status SUBMITTED -> subject expert approves -> APPROVED (in the
   bank) or REJECTED. No step in between.
2. A subject expert cannot approve or reject a question they authored.
3. Excel columns, in order: Sr. No, Question Type, Answer Type, Topic Number, Question (Text Only),
   Correct Answer 1-4, Wrong Answer 1-3, Time in seconds, Difficulty Level, Question (Image/ Audio/ Video),
   Contributor's Registered mailId, Solution (Text Only), Solution (Image/ Audio/ Video), Variation Number.
4. The competency is chosen on the upload page. "Topic Number" is stored as written; questions with the same
   competency + topic are variations of each other (`parent_id` = first of the family). A test must never give
   one candidate two variations of the same topic.
5. SVG typed inside question or solution text is rendered in place after removing scripts, event handlers
   and external links. Never output user text with `th:utext` unless it went through `QuestionTextRenderer`.
6. Scoring rules are ported from the Python pilot: cut scores 40/55/70/85, grade offsets, method weights by
   job group, any safety-critical gap = Red, a gap of 2+ levels or under 70% met = Amber.

## 6. Next goals, in order

Each goal is done only when `build.bat` shows BUILD SUCCESS and the new tests pass.

**Goal 0 - Make the current code build and run.**
Run the build, fix compile and test failures, start the app on MySQL 8 (`mvnw.cmd spring-boot:run`), sign in
with the accounts in `DEMO-ACCOUNTS.md`, and click through every page. Fix what breaks.

**Goal 1 - Question media.** Upload and serve image, audio and video files for questions and solutions
(the Excel media columns currently hold text only). Store files outside the WAR in a configurable folder,
check type and size, serve only to signed-in users.

**Goal 2 - Tests and scheduling** (`tests` module). Port `Test`, `Blueprint` and eligibility from
`../app/models.py` and `../app/services/engine.py`: test kinds (PRACTICE, T0, T1-T3), time window, duration,
target groups (role family, grade, department, location, named staff), attempts allowed. Screen for the
Assessment Cell to create and publish a test.

**Goal 3 - Test assembly and player** (`delivery` module). Per-candidate random draw from APPROVED questions
for the candidate's role profile, one variation per topic, reproducible seed, shuffled options. Player page:
server-side timer, autosave, resume after refresh, auto-submit at time-out, per-question time recorded.
Tables: `attempt`, `attempt_item`.

**Goal 4 - Proctoring** (`proctoring` module). Log tab switches, full-screen exit, copy/paste; optional webcam
snapshots; flag attempts over the tab-switch limit or with rapid guessing (port `../app/services/timing.py`);
review screen for the `proctor` role with accept/void decisions.

**Goal 5 - Marking and publishing scores.** Auto-mark single and multiple choice (port
`../app/services/scoring.py`), write `competency_score` rows on submit, show the real result on `/me`.
After this the demo scores are no longer needed.

**Goal 6 - Training plans** (`development` module). Port `../app/services/prescription.py`: training
catalogue, automatic Individual Development Plan (safety gaps first; 30/60/90-day targets), supervisor confirms.

**Goal 7 - Employee import.** CSV/Excel import of the employee master with a dry-run report and designation
mapping (port `../app/services/importers.py`).

**Goal 8 - Dashboards and exports** (`reporting` module). Supervisor, HoD and management views; Excel export.
For 25,000 staff, store a profile summary per employee and page every list (the current `/results` page
rebuilds profiles on each visit and reads at most 500 employees).

**Goal 9 - Production readiness.** Tomcat 11 + Nginx deployment guide for Ubuntu, sessions and autosave
buffering in Redis, MySQL tuning and backups, load test at the expected peak of concurrent candidates,
security review, single sign-on.

## 7. Open questions for the project owner

- How many of the 25,000 users sit a test at the same moment? (Decides server sizing for Goals 3 and 9.)
- Allowed values for "Question Type" and "Answer Type" in the Excel file.
- Does "Topic Number" map to the 44 competencies, or is it a separate topic list?
- Proctoring level: logging and snapshots with human review, or live invigilation?
- Should questions also carry Marathi text? The Excel template has no column for it.

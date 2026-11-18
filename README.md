# Maha Metro CoE – Competency Assessment & Benchmarking Platform

Open-source, on-premises system to benchmark the competency of every Maha Metro employee, find gaps by
competency and prescribe training. Built on the CoE design note and tracker of 19 Oct 2026
(44 competencies, 16 role families, 5 grade bands, 5-level scale, safety gate).

**Stack (all open source):** Python 3.12 · FastAPI · PostgreSQL 16 · Keycloak 25 (SSO) · Nginx · Docker Compose.
No internet access is needed at run time (fonts and charts are served locally).

## What it does

| Area | Features |
| --- | --- |
| Sign-in | Keycloak SSO brokering to the Maha Metro portal / Active Directory / LDAP / SAML; signed JWT hand-off from the portal; break-glass passwords; DPDP consent notice |
| Staff data | SAP HR export import (CSV/Excel), designation → role family/grade mapping, nightly automatic import |
| Question bank | 10 question types: single correct, multiple correct (partial/negative marking), true/false, situational judgement (weighted), sequencing, match-the-pairs, numeric, fill-in-the-blank, written answer, spoken (audio) answer. Audio, video and image stems; paragraph (passage) sets; English + Marathi for every item; author → reviewer workflow; Excel bulk import; item statistics |
| Tests | Built automatically from each person's role profile or from a blueprint; random draw, question order and option order per candidate (reproducible seed for audit); passage sets kept together; windows by role family / grade / department / location / named staff; server-side timer, autosave, resume, auto-submit; extra time per person; full-screen, tab-switch, copy/paste logging and optional webcam snapshots |
| Practice test | Separate practice pool, unlimited attempts, answers + explanations, readiness estimate and preparation prescription; never counted officially |
| Scoring | Per-competency score → level → gap; composite of knowledge, SJT, practical/simulator and supervisor rating with role-group weights; safety gate; Red/Amber/Green readiness; Competency Index; gain, normalised gain, gap closure |
| Prescription | Individual Development Plan generated automatically: safety gaps first, mapped to CoE labs/simulators, OEM, OJT and iGOT Karmayogi; confirm by employee/supervisor; T1/T2 re-test |
| Dashboards | Employee, Supervisor (team heat map), HoD (department benchmarks), Management (organisation scorecard), SME/Reviewer (bank), Assessment Cell (scheduling, manual scoring, proctoring review, imports, framework, training catalogue, item analysis, audit) |
| Reports | Printable individual report (PDF via browser), Excel export of results and Training Needs Analysis |

## Quick start (evaluation, demo data)

```bash
# PostgreSQL running locally, database 'competency'
pip install -r requirements.txt
export DATABASE_URL=postgresql+psycopg://user:pass@localhost:5432/competency DEMO_MODE=true AUTH_MODES=local
python -m seed.seed --demo
uvicorn app.main:app --port 8000
# open http://localhost:8000 and pick a demo role on the sign-in page (password Demo@2027)
```

Demo accounts: DEMO-EMP-SC (employee), DEMO-FA (employee, no attempts yet), DEMO-SUP-SC (supervisor),
DEMO-HOD-OPS / DEMO-HOD-FIN (HoD), DEMO-MGT (management), DEMO-SME (author), DEMO-REV (reviewer), DEMO-CELL (Assessment Cell).
Demo staff, names and results are synthetic.

## Production

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md). In short: `cd deploy && cp .env.example .env` (set secrets),
add TLS certificates, `docker compose up -d`, then
`docker compose exec app python -m seed.seed --admin <ID> --password '<12+ chars>'`.

## Tests

```bash
pip install -r requirements-dev.txt && python -m pytest -q
```

## Layout

```
app/            FastAPI application (models, services, routers, templates, static, locales)
  services/     engine (test assembly), scoring, framework rules, prescription, analytics, importers
seed/           framework extracted from the CoE tracker workbook, question bank, demo media, seeding script
scripts/        SAP HR import CLI, nightly scheduler, Marathi locale builder
deploy/         docker-compose, Nginx, Keycloak realm, environment template
docs/           deployment, SSO integration, operations
tests/          automated tests
```

## Important

* Seed questions marked **SME validation** must be checked against Maha Metro GR/SR, SOPs and OEM manuals before
  they are used in an official test. The full bank (about 3,000 items) is authored by departmental SME panels.
* Marathi interface text and seed translations are working translations for review by the CoE language editor.
* Cut scores are provisional (40/55/70/85) until the post-pilot Angoff standard-setting panel.

## Changes

**v1.3 – question quality**
- Starter bank rewritten: 144 bilingual questions across all 44 competencies. They are built on job situations with believable wrong options, four options for single-answer and SJT questions, balanced option lengths, and no "all/none of the above". Every auto-marked question has an English and Marathi explanation.
- Quality checks in the question editor. They appear live as the author types, and also show in the preview, the review queue, the bank list and the Excel import report. They flag: the correct option clearly the longest, all/none of the above, fewer than four options, duplicate options, missing Marathi text, no explanation, and no source reference.
- Question-writing guide for SME panels: in the app at Question bank → Question-writing guide, and as `docs/QUESTION_WRITING_GUIDE.md` / `.pdf` (2 pages).
- Old starter questions are retired automatically on re-seed (past results are kept). The demo database is rebuilt once so the new bank loads.
- Still to do by SME panels: 78 Domain questions carry no Maha Metro SOP / G&SR / OEM manual citation yet. The editor flags each one.

**v1.2** – time per question is stored. Rapid-guessing detection (response-time effort) flags non-serious attempts for Cell review.

**v1.1** – Windows demo launcher hardened. A Cell member's own answers are hidden from their scoring queue.

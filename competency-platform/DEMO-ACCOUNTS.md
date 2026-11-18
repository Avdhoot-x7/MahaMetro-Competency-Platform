# Demo accounts

These accounts are **made up** and exist only on a developer PC. They are created automatically the first time
the app starts in development mode (`app.demo-data: true` in `application-dev.yml`). The names, results and
questions are not real. Never turn this on in production.

Open http://localhost:8080 and sign in with an ID below.

**Password for every DEMO account: `Demo@2027`**

| Employee ID | Name | Role | Job | What to look at after signing in |
| --- | --- | --- | --- | --- |
| `DEMO-EMP1` | Asha Kulkarni | Employee | Station Controller, Operations (SC, G1) | Home > My competency results |
| `DEMO-EMP2` | Rohan Patil | Employee | Train Operator, Operations (TO, G2) | Home > My competency results |
| `DEMO-EMP3` | Meera Joshi | Employee | Accounts Assistant, Finance (FA, G1) | Home > My competency results |
| `DEMO-SUP` | Sunil Deshmukh | Supervisor | Station Manager, Operations (SC, G3) | Staff results: sees DEMO-EMP1 and DEMO-EMP2, who report to him |
| `DEMO-HOD` | Kavita Rao | Head of Department (Operations only) | Head of Operations (OCC, G5) | Staff results: sees everyone in the Operations department |
| `DEMO-MGT` | Anil Mehta | Management | Director, Corporate (CORP, G5) | Staff results: sees all staff |
| `DEMO-PROG` | Neha Shah | Programmer (uploads questions) | Question Programmer, IT (IT, G3) | Question bank > Upload questions (Excel) |
| `DEMO-SME` | Vikram Iyer | Subject expert (approves questions) | Subject Expert, Safety (SFS, G4) | Question bank > Approve questions (2 are waiting, one with an SVG picture) |
| `DEMO-PROC` | Pooja Nair | Proctor | Proctor, HR (HRA, G2) | Nothing yet: proctoring screens are not built |
| `DEMO-CELL` | Rahul Gokhale | Assessment Cell | Assessment Cell Officer, HR (HRA, G4) | Staff results (all staff), Staff and roles, Question bank |
| `ADMIN` | System Administrator | Super administrator | - | Everything. Password: `Admin@12345` |

## What the demo data contains

- Each DEMO employee has a made-up baseline (T0) score for every competency of their job, so every account
  shows a results page with required level, score, level reached, gap and Red / Amber / Green readiness.
- Three sample questions in competency C01: one already approved and in the bank, two waiting for the subject expert.

## To start again with fresh demo data

In the MySQL command line: `DROP DATABASE competency; CREATE DATABASE competency CHARACTER SET utf8mb4;`
then start the app again.

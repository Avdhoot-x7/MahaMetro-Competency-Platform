# Deployment and operations guide

## 1. Server

| Item | Pilot (≈200 staff) | Organisation-wide (700+ staff, 300 concurrent) |
| --- | --- | --- |
| Application + DB | 1 VM: 4 vCPU, 8 GB RAM, 100 GB SSD | 2 VMs: app 8 vCPU / 16 GB; DB 4 vCPU / 16 GB, 200 GB SSD |
| OS | Ubuntu 22.04/24.04 LTS or RHEL 9 | same |
| Software | Docker Engine 24+ with Compose plugin | same |
| Network | Reachable from CoE labs, depots, OCC and offices on the Maha Metro LAN/VPN; HTTPS 443 only | same |
| Certificates | TLS certificate for `competency.<domain>` and `sso.<domain>` | same |

Hosting stays on Maha Metro servers in India (DPDP Act 2023). No outbound internet is needed after the images are pulled.

## 2. Install

```bash
git clone <internal repo> competency && cd competency/deploy
cp .env.example .env          # edit every CHANGE value; generate SECRET_KEY with: openssl rand -hex 32
cp /path/fullchain.pem /path/privkey.pem certs/
# edit server_name lines in nginx.conf and the redirect URI / secret in keycloak/realm-mahametro.json
docker compose build app
docker compose up -d
docker compose exec app python -m seed.seed --admin 900001 --password 'a-long-break-glass-password'
```

Open `https://competency.<domain>` and sign in with the break-glass account, or with SSO once section 3 is done.
`seed.seed` loads the framework (from the CoE tracker workbook), training catalogue, blueprints, starter question bank
and three tests (practice published; T0 and T1 as drafts). It is safe to run again after upgrades.

## 3. Single sign-on (pluggable)

The application trusts one of three sign-in routes; choose with `AUTH_MODES`.

| Maha Metro portal signs users in with … | Configure |
| --- | --- |
| Active Directory / LDAP | Keycloak admin → realm *mahametro* → User federation → LDAP. Map the AD attribute that holds the SAP personnel number (e.g. `employeeID`) to the Keycloak user attribute `employee_id`. Map AD groups to the six `Competency-*` groups. |
| OpenID Connect or SAML (portal is an identity provider) | Keycloak → Identity providers → add OIDC or SAML; add a mapper that copies the employee-number claim to `employee_id`. Optionally set the IdP as default so users never see the Keycloak page. |
| Portal's own login, no standard protocol | Portal links to `https://competency.<domain>/auth/portal?token=<JWT>`. The JWT is signed with `PORTAL_JWT_SECRET` (HS256) or a private key whose public key is in `PORTAL_JWT_PUBLIC_KEY_FILE` (RS256); claims `sub` = employee ID, `aud` = `competency-portal`, `iat`, `exp` (≤ 10 minutes). Example in section 7. |

Roles come from Keycloak groups through `OIDC_ROLE_MAP`; *supervisor* is assigned automatically to anyone who is a
reporting officer in the SAP HR data. Assessment Cell can also assign roles on the Staff & roles page.
Keep `local` in `AUTH_MODES` only for break-glass; in production, password login works only for `LOCAL_LOGIN_ROLES`.

## 4. Employee master from SAP HR

* Manual: Assessment Cell → Imports → Employee master (CSV or Excel). Column names are recognised automatically
  (Personnel Number, Employee Name, Position, Org Unit, Personnel Area, Employee Subgroup/Grade, Supervisor ID, Email…).
* Automatic: SAP (or IT) drops the export into `deploy/import/sap/` every night; the `scheduler` service imports it at
  `NIGHTLY_AT` and moves it to `processed/`.
* Unmapped designations are listed in the import report; map them once in Framework → Designation mapping.

## 5. Running an assessment cycle

1. Cell checks the framework: role profiles approved by each HoD (Framework page), cut scores, training catalogue.
2. SMEs author items (Question bank → New question, or Excel import); reviewers approve. Check **Question bank coverage**
   on the Cell overview: plan three approved items per blueprint position.
3. Cell creates a test (Tests & schedules → New test): type T0, window, target groups, integrity controls; publish.
4. Staff take the test; written/spoken answers appear in **Manual scoring**; flagged attempts in **Proctoring review**.
5. Supervisors record practical/simulator checks and on-job ratings from each team member's report page.
6. Development plans are generated automatically; supervisors confirm them. Export results / TNA to Excel.
7. After the window: Item analysis → Run; revise or retire flagged items; for the pilot, convene the Angoff panel and
   update cut scores.
8. T1 re-test: create a T1 test with *Only these competencies* or named staff; T2 after about 3 months; T3 annually.

## 6. Backup, monitoring, upgrades

* `backup` service writes a daily `pg_dump` to `deploy/backups/` and keeps 30 days; copy that folder to the
  Maha Metro backup system. Media (audio/video/webcam) is in the `media` Docker volume – include it in backups.
* Restore: `docker compose exec -T db pg_restore -U competency -d competency --clean < backups/<file>.dump`.
* Health check: `GET /healthz`. Logs: `docker compose logs -f app`.
* Upgrade: `git pull && docker compose build app && docker compose up -d && docker compose exec app python -m seed.seed`.
* Load test before the baseline window (e.g. 300 simulated candidates with Locust or k6) on the production VM.

## 7. Portal hand-off token example (Python; the .NET equivalent uses System.IdentityModel.Tokens.Jwt)

```python
import jwt, time
now = int(time.time())
token = jwt.encode({"sub": "100234", "aud": "competency-portal", "iat": now, "exp": now + 120},
                   PORTAL_JWT_SECRET, algorithm="HS256")
url = f"https://competency.mahametro.org/auth/portal?token={token}"
```

## 8. Security checklist before go-live

- [ ] `ENVIRONMENT=production`, `DEMO_MODE=false`, unique `SECRET_KEY`, strong DB and Keycloak passwords
- [ ] TLS certificates installed; port 80 redirects to 443; only 443 open on the firewall
- [ ] Keycloak client secret changed in both `realm-mahametro.json`/Keycloak and `.env`
- [ ] Break-glass accounts limited to the Cell and IT; passwords stored in the IT vault
- [ ] Vulnerability scan of the deployment by IT security (C3iHub audit scope)
- [ ] DPDP notice text approved by Legal; retention period confirmed
- [ ] Backups and a test restore verified

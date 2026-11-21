# Maha Metro CoE – Competency Assessment & Benchmarking Platform

Enterprise, on-premises competency evaluation, gap analysis, and training recommendation system for Maharashtra Metro Rail Corporation Limited (Maha Metro). Built in strict compliance with the **UTKARSH Centre of Excellence (CoE) Competency Assessment Framework** and the **Item Bank Generation & Assessment Specification (v1.3)**.

---

## Technology Stack

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Language & Runtime** | Java 17 (Eclipse Temurin / OpenJDK) | Modern, strongly typed backend engine |
| **Framework** | Spring Boot 4.1.1 (Spring MVC, JPA, Security) | Enterprise web application framework |
| **Database** | MySQL 8.0.40 | Relational database with full ACID compliance |
| **Schema Migration** | Flyway 10 (`V1` through `V16`) | Deterministic, version-controlled database migrations |
| **Presentation Layer** | Thymeleaf 3.1 & Vanilla CSS / HTML5 | Responsive, server-rendered views with zero heavy JS bloat |
| **Application Server** | Embedded Apache Tomcat 11.0 / Standalone WAR | Deployable as standalone runnable JAR or Tomcat WAR |
| **Build System** | Apache Maven 3.9 (via bundled `mvnw.cmd` / `mvnw`) | Standard reproducible build orchestration |
| **Cloud / Container** | Multi-stage Dockerfile (`eclipse-temurin:17-jre`) | Ready for Railway, Render, or internal Docker hosts |

---

## Key System Capabilities

### 1. Candidate Learning & Assessment Hub (`/`)
* **Role-Tailored Candidate Portal**: Personalized landing experience for 25,000+ staff across 16 role families.
* **Practice Drills (सराव चाचणी)**: Low-stakes, untimed mock questions with instant bilingual pedagogical feedback.
* **Scheduled Formal Assessments (अधिकृत मूल्यांकन परीक्षा)**: Official timed competency exams with audit logging.
* **Personal Performance Scorecard**: Live readiness status (Ready / Action Required / Safety Gate), Competency Met Index %, and evaluated counts.
* **Weak Point Recommendations Roadmap**: Direct prescription of CoE simulator drills and classroom modules targeting active competency gaps.
* **Interactive Result Heat Map (`/me`)**: Color-coded dot matrix indicators per competency with category filters.
* **Self-Service Profile Modification (`/profile`)**: Direct update of English/Marathi names, age, operational sector/line, department, depot location, and contact numbers.

### 2. AI Question Studio (Spec v1.3) & 10 QC Rules Engine (`/questions/generate`)
* AI-assisted generation of bilingual items adhering to Bloom's taxonomy and 10 question formats.
* Automated validation of CoE rules R1–R10 (distractor plausibility, clueing avoidance, option length balance, positive stems, and pedagogical rationales).
* Automatic safety-critical gate flagging (`needs_sme_validation = true`) for high-consequence operations.

### 3. Psychometric Item Analysis Dashboard (`/questions/analysis`)
* Real-time calculation of **Facility Index ($p$-value)** and **Point-Biserial Discrimination ($r_{pb}$)**.
* Distractor selection distribution analysis and automatic quarantine of negatively discriminating items.

### 4. Integrity & Micro-Interaction Telemetry (`/proctor/flags`)
* Real-time logging of candidate events (item dwell time, option switching, tab switches, and review sweep).
* Confidential behavioral indices (`A5` Rapid Guessing, `E1` Rule Compliance) unmasked only for proctors and assessment cell reviewers without altering competency scores.

### 5. Organizational & Departmental Scorecards (`/dashboards/org`, `/dashboards/department`)
* Macro workforce readiness heat maps across departments (Operations, Traction, Rolling Stock, Signaling) and role families.
* Excel exports styled for executive review and automated Individual Development Plans (IDPs).

---

## Local Development & Setup

### Prerequisites
* **Java Development Kit (JDK) 17** (or Eclipse Temurin 17)
* **MySQL Server 8.0+** running on `localhost:3306`

### Database Setup
```sql
CREATE DATABASE IF NOT EXISTS competency CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'competency'@'localhost' IDENTIFIED BY 'competency';
GRANT ALL PRIVILEGES ON competency.* TO 'competency'@'localhost';
FLUSH PRIVILEGES;
```

### Build & Run
1. Navigate to the project directory:
   ```bash
   cd competency-platform
   ```
2. Run automated tests:
   ```bash
   ./mvnw clean test
   ```
3. Package executable WAR:
   ```bash
   ./mvnw package -DskipTests
   ```
4. Start the application:
   ```bash
   java -jar target/competency.war
   ```
5. Open browser at `http://localhost:8080`.

---

## Default Test Credentials

Universal demo password: **`Demo@2027`** (Admin: **`Admin@12345`**).

| Role | Username | Password |
| :--- | :--- | :--- |
| **Train Operator (Candidate)** | `DEMO-EMP2` | `Demo@2027` |
| **Station Controller (Candidate)** | `DEMO-EMP1` | `Demo@2027` |
| **Station Manager (Supervisor)** | `DEMO-SUP` | `Demo@2027` |
| **Head of Department (HoD)** | `DEMO-HOD` | `Demo@2027` |
| **Assessment Cell / SME** | `DEMO-CELL` | `Demo@2027` |
| **Proctor** | `DEMO-PROC` | `Demo@2027` |
| **Platform Administrator** | `ADMIN` | `Admin@12345` |

# Ubuntu 20.04+ Production Deployment: Known Issues & Solutions Guide
**Target Stack:** Java 17 | Spring Boot (JPA / Spring Security / Thymeleaf) | MySQL 8 | Apache Tomcat 11 | Ubuntu 20.04+ LTS

---

## 1. Overview of Identified Deployment Issues

When deploying the Maha Metro Competency Assessment Platform WAR (`competency.war`) to an Ubuntu 20.04+ server running Apache Tomcat and MySQL 8, the following critical issues must be addressed.

---

### Issue 1: Ubuntu Default `apt` Tomcat Version Incompatibility
* **Risk Severity:** CRITICAL (App will not start)
* **Problem:** 
  Running `sudo apt install tomcat9` or standard Ubuntu repository packages installs Tomcat 9 (or older), which operates on Java EE (`javax.servlet.*`). Modern Spring Boot uses Jakarta EE 10 (`jakarta.servlet.*`). Deploying the WAR on an older Tomcat version fails immediately with `ClassNotFoundException: jakarta.servlet.Filter` or `NoClassDefFoundError`.
* **Solution:**
  Do **not** install Tomcat using Ubuntu's `apt` package manager. Install standalone **Apache Tomcat 11** (or Tomcat 10.1+) directly from Apache archive:
  ```bash
  sudo useradd -r -m -U -d /opt/tomcat11 -s /bin/false tomcat
  wget https://archive.apache.org/dist/tomcat/tomcat-11/v11.0.24/bin/apache-tomcat-11.0.24.tar.gz
  sudo tar -xf apache-tomcat-11.0.24.tar.gz -C /opt/tomcat11 --strip-components=1
  sudo chown -R tomcat:tomcat /opt/tomcat11
  ```

---

### Issue 2: Default Java Version Mismatch on Ubuntu 20.04
* **Risk Severity:** HIGH (Build or runtime failure)
* **Problem:** 
  Ubuntu 20.04 LTS installs OpenJDK 11 when using generic commands like `apt install default-jdk`. Java 17 is strictly required by the Spring Boot compilation target and classes will fail to load (`UnsupportedClassVersionError: 61.0`).
* **Solution:**
  Explicitly install and select OpenJDK 17:
  ```bash
  sudo apt install -y openjdk-17-jdk
  sudo update-alternatives --config java
  java -version # Must verify version 17.x.x
  ```

---

### Issue 3: Character Encoding & Marathi Question Corruption
* **Risk Severity:** HIGH (Data corruption for bilingual content)
* **Problem:** 
  Default MySQL 8 installations or connection pools not explicitly set to Unicode can mangle Marathi Devanagari script into question marks (`????`) or trigger `SQLException: Incorrect string value`.
* **Solution:**
  Create the database using `utf8mb4` encoding:
  ```sql
  CREATE DATABASE competency CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  ```
  Ensure JDBC connection strings enforce UTF-8:
  ```
  jdbc:mysql://localhost:3306/competency?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
  ```

---

### Issue 4: Directory Permissions & Media Storage
* **Risk Severity:** MEDIUM-HIGH (Runtime failure on question media & exports)
* **Problem:** 
  Tomcat runs as an unprivileged service user (`tomcat`). If media storage paths like `/var/mahametro/competency/media` do not exist or lack write permissions, image/audio uploads, question attachments, and Excel report generation will fail with `java.io.IOException: Permission denied`.
* **Solution:**
  Pre-provision directories and assign ownership to the service user:
  ```bash
  sudo mkdir -p /var/mahametro/competency/media
  sudo mkdir -p /var/mahametro/competency/backups
  sudo chown -R tomcat:tomcat /var/mahametro/competency
  sudo chmod -R 750 /var/mahametro/competency
  ```

---

### Issue 5: Missing Environment Variables Under Systemd
* **Risk Severity:** HIGH (App crashes on startup due to missing database credentials)
* **Problem:** 
  Environment variables declared in user profiles (`.bashrc` or shell `export`) are not passed to Tomcat when launched via `systemd`. Spring Boot fails to connect to the database.
* **Solution:**
  Declare all production configurations in `/opt/tomcat11/bin/setenv.sh`:
  ```bash
  export SPRING_PROFILES_ACTIVE=prod
  export DB_URL='jdbc:mysql://localhost:3306/competency?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
  export DB_USER='competency'
  export DB_PASSWORD='YourStrongPassword'
  ```
  Ensure the script is executable:
  ```bash
  sudo chmod +x /opt/tomcat11/bin/setenv.sh
  ```

---

### Issue 6: Memory Out of Memory (OOM) Errors Under Load
* **Risk Severity:** MEDIUM-HIGH (Server unresponsiveness during exams)
* **Problem:** 
  Default Tomcat memory allocations are too low for enterprise concurrent tests and report compilation.
* **Solution:**
  Allocate adequate JVM heap in `/opt/tomcat11/bin/setenv.sh`:
  ```bash
  JAVA_OPTS="-Xms4g -Xmx8g -XX:+UseG1GC -Djava.awt.headless=true"
  ```

---

### Issue 7: Firewall & Port Access (UFW)
* **Risk Severity:** MEDIUM (Tomcat unreachable from clients)
* **Problem:** 
  Ubuntu's firewall (`ufw`) blocks incoming traffic on port 8080 or port 443 by default.
* **Solution:**
  Allow Nginx reverse proxy ports:
  ```bash
  sudo ufw allow 80/tcp
  sudo ufw allow 443/tcp
  sudo ufw allow 22/tcp
  sudo ufw enable
  ```

---

## 2. Summary Checklist Before Deployment

| Step | Item | Status |
|---|---|---|
| 1 | Ubuntu updated & OpenJDK 17 installed | [ ] |
| 2 | Apache Tomcat 11 manually extracted to `/opt/tomcat11` | [ ] |
| 3 | MySQL 8 database created with `utf8mb4` | [ ] |
| 4 | `setenv.sh` created with DB credentials and JVM flags | [ ] |
| 5 | Storage folder `/var/mahametro/competency` created & chowned | [ ] |
| 6 | Application packaged with `./mvnw clean package -DskipTests` | [ ] |
| 7 | `target/competency.war` copied to `/opt/tomcat11/webapps/ROOT.war` | [ ] |
| 8 | Service started via `sudo systemctl start tomcat11` | [ ] |

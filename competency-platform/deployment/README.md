# Maha Metro Competency Assessment Platform - Production Deployment Guide
**Target Operating System**: Ubuntu 20.04+ LTS (Ubuntu 22.04 LTS / 24.04 LTS recommended)  
**Architecture**: Nginx (Reverse Proxy & SSL Termination) -> Apache Tomcat 11 (WAR Runtime) -> MySQL 8.0 & Redis 6+

---

## 1. System Architecture Overview

```
+---------------------------------------------------------------------------------+
|                                 Internet / Intranet                             |
+---------------------------------------------------------------------------------+
                                       |
                                HTTPS (Port 443)
                                       v
+---------------------------------------------------------------------------------+
|                       Nginx Reverse Proxy & Gateway                             |
|  - TLS 1.3 / Modern Ciphers Termination                                         |
|  - Rate Limiting Zones (/login: 5r/s, /delivery/attempts/*/autosave: 20r/s)     |
|  - Static Asset Caching (/css, /js, /favicon.ico) with Gzip                     |
|  - Intranet SSO Header Injection (X-Remote-User) & Proxy IP verification       |
+---------------------------------------------------------------------------------+
                                       |
                             HTTP / NIO2 (Port 8080)
                                       v
+---------------------------------------------------------------------------------+
|                            Apache Tomcat 11 Container                           |
|  - Service: tomcat11.service (Sandboxed systemd unit)                           |
|  - JVM: OpenJDK 17 (-Xms4g -Xmx8g -XX:+UseG1GC)                                 |
|  - Thread Pool: 500 maxThreads, 50 minSpareThreads                              |
|  - Artifact: /opt/tomcat11/webapps/ROOT.war                                     |
+---------------------------------------------------------------------------------+
                     |                                       |
         JDBC / Hikari Connection Pool               Redis Protocol (Port 6379)
                     v                                       v
+---------------------------------------+   +-------------------------------------+
|             MySQL 8.0 Server          |   |          Redis 6+ Server            |
|  - 25,000 Staff Data & Attempts       |   |  - Autosave Debounce Buffer         |
|  - innodb_buffer_pool_size = 12G      |   |  - Clustered HTTP Session Store     |
|  - innodb_flush_log_at_trx_commit = 2 |   |  - maxmemory 2gb (allkeys-lru)      |
|  - Daily automated gzip backup        |   +-------------------------------------+
+---------------------------------------+
```

---

## 2. Server Sizing & Resource Requirements

| Scale Tier | Concurrent Candidates | Recommended CPU | RAM | Storage |
| :--- | :--- | :--- | :--- | :--- |
| **Department Assessment** | Up to 500 | 4 vCPU | 16 GB | 100 GB NVMe |
| **Enterprise Peak** | 1,000 – 2,500 | 8 vCPU | 32 GB | 250 GB NVMe |
| **Full Organisation** | 2,500 – 5,000 | 16 vCPU | 64 GB | 500 GB NVMe |

---

## 3. Step-by-Step Installation Runbook (Ubuntu 20.04+)

### Step 1: Base Operating System & Dependencies
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-17-jdk nginx redis-server mysql-server certbot python3-certbot-nginx ufw curl wget unzip
```

### Step 2: MySQL 8 Configuration & Tuning
1. Copy the optimized configuration:
   ```bash
   sudo cp deployment/mysql/my.cnf /etc/mysql/conf.d/mahametro.cnf
   sudo systemctl restart mysql
   ```
2. Create platform database and user:
   ```sql
   CREATE DATABASE competency CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'competency'@'localhost' IDENTIFIED BY 'StrongProductionPassword2027!';
   GRANT ALL PRIVILEGES ON competency.* TO 'competency'@'localhost';
   FLUSH PRIVILEGES;
   ```

### Step 3: Redis Configuration
```bash
sudo cp deployment/redis/redis.conf /etc/redis/redis.conf
sudo systemctl restart redis-server
sudo systemctl enable redis-server
```

### Step 4: Apache Tomcat 11 Setup
1. Create system user and directory:
   ```bash
   sudo useradd -r -m -U -d /opt/tomcat11 -s /bin/false tomcat
   wget https://archive.apache.org/dist/tomcat/tomcat-11/v11.0.24/bin/apache-tomcat-11.0.24.tar.gz
   sudo tar -xf apache-tomcat-11.0.24.tar.gz -C /opt/tomcat11 --strip-components=1
   sudo chown -R tomcat:tomcat /opt/tomcat11
   ```
2. Install Tomcat server and environment configurations:
   ```bash
   sudo cp deployment/tomcat/setenv.sh /opt/tomcat11/bin/setenv.sh
   sudo chmod +x /opt/tomcat11/bin/setenv.sh
   sudo cp deployment/tomcat/server.xml /opt/tomcat11/conf/server.xml
   sudo cp deployment/systemd/tomcat11.service /etc/systemd/system/tomcat11.service
   sudo systemctl daemon-reload
   sudo systemctl enable tomcat11
   ```

### Step 5: Data Directories & Media Storage
```bash
sudo mkdir -p /var/mahametro/competency/media
sudo mkdir -p /var/mahametro/competency/backups
sudo chown -R tomcat:tomcat /var/mahametro/competency
sudo chmod -R 750 /var/mahametro/competency
```

### Step 6: Deploy Platform Application WAR
```bash
# Build the WAR using Maven
./mvnw clean package -DskipTests

# Copy to Tomcat ROOT webapp
sudo rm -rf /opt/tomcat11/webapps/ROOT /opt/tomcat11/webapps/ROOT.war
sudo cp target/competency.war /opt/tomcat11/webapps/ROOT.war
sudo chown tomcat:tomcat /opt/tomcat11/webapps/ROOT.war
sudo systemctl restart tomcat11
```

### Step 7: Nginx Reverse Proxy & SSL Configuration
1. Install virtual host configuration:
   ```bash
   sudo cp deployment/nginx/competency.mahametro.in.conf /etc/nginx/sites-available/
   sudo ln -s /etc/nginx/sites-available/competency.mahametro.in.conf /etc/nginx/sites-enabled/
   sudo rm -f /etc/nginx/sites-enabled/default
   sudo nginx -t && sudo systemctl reload nginx
   ```
2. Enable SSL Certificate (Let's Encrypt or Corporate PKI):
   ```bash
   sudo certbot --nginx -d competency.mahametro.in
   ```

### Step 8: Firewall Hardening
```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

---

## 4. Operational Maintenance & Health Monitoring

### Service Control
- **Restart App**: `sudo systemctl restart tomcat11`
- **Check Logs**: `sudo journalctl -u tomcat11 -f`
- **Application Health**: `curl -s http://localhost:8080/actuator/health`

### Automated Backups
Schedule daily MySQL dumps in cron (`sudo crontab -e`):
```cron
0 2 * * * /opt/mahametro/deployment/mysql/backup-mysql.sh >> /var/log/competency-backup.log 2>&1
```

#!/usr/bin/env bash
# ==============================================================================
# Maha Metro Competency Assessment Platform - Zero-Downtime Deployment Script
# Builds WAR, backs up previous version, deploys to Tomcat 11, and verifies health.
# ==============================================================================

set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOMCAT_WEBAPPS="/opt/tomcat11/webapps"
TARGET_WAR="${APP_DIR}/target/competency.war"
BACKUP_DIR="/var/mahametro/competency/deploy-backups"
HEALTH_URL="http://localhost:8080/actuator/health"

echo "=== Deploying Maha Metro Competency Assessment Platform ==="

mkdir -p "${BACKUP_DIR}"

# 1. Build the production WAR
echo "[1/4] Building production WAR package..."
cd "${APP_DIR}"
./mvnw clean package -DskipTests

if [ ! -f "${TARGET_WAR}" ]; then
    echo "Error: Build failed, ${TARGET_WAR} not found!"
    exit 1
fi

# 2. Backup currently active WAR
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
if [ -f "${TOMCAT_WEBAPPS}/ROOT.war" ]; then
    echo "[2/4] Backing up current deployment to ${BACKUP_DIR}/ROOT_${TIMESTAMP}.war..."
    cp "${TOMCAT_WEBAPPS}/ROOT.war" "${BACKUP_DIR}/ROOT_${TIMESTAMP}.war"
fi

# 3. Deploy new WAR
echo "[3/4] Deploying new WAR to ${TOMCAT_WEBAPPS}/ROOT.war..."
rm -rf "${TOMCAT_WEBAPPS}/ROOT"
cp "${TARGET_WAR}" "${TOMCAT_WEBAPPS}/ROOT.war"
chown tomcat:tomcat "${TOMCAT_WEBAPPS}/ROOT.war"

# Restart Tomcat 11
echo "Restarting tomcat11 service..."
systemctl restart tomcat11

# 4. Health Check Verification
echo "[4/4] Verifying health at ${HEALTH_URL}..."
MAX_ATTEMPTS=30
ATTEMPT=0
HEALTHY=false

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    ATTEMPT=$((ATTEMPT + 1))
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${HEALTH_URL}" || true)
    if [ "${STATUS}" == "200" ]; then
        HEALTHY=true
        break
    fi
    echo "Waiting for application to boot (Attempt ${ATTEMPT}/${MAX_ATTEMPTS}, HTTP ${STATUS})..."
    sleep 3
done

if [ "$HEALTHY" = true ]; then
    echo "=========================================================="
    echo "SUCCESS: Maha Metro Platform deployed and verified UP!"
    echo "=========================================================="
    exit 0
else
    echo "=========================================================="
    echo "ERROR: Health check failed! Rolling back to previous WAR..."
    echo "=========================================================="
    if [ -f "${BACKUP_DIR}/ROOT_${TIMESTAMP}.war" ]; then
        cp "${BACKUP_DIR}/ROOT_${TIMESTAMP}.war" "${TOMCAT_WEBAPPS}/ROOT.war"
        systemctl restart tomcat11
    fi
    exit 1
fi

#!/usr/bin/env bash
# ==============================================================================
# Maha Metro Competency Assessment Platform - Production Health Check Monitor
# Verifies Nginx, Tomcat, MySQL, Redis, Actuator endpoints, and disk thresholds.
# ==============================================================================

set -euo pipefail

FAILURES=0

check_service() {
    local service="$1"
    if systemctl is-active --quiet "${service}"; then
        echo "[OK] Service '${service}' is active."
    else
        echo "[CRITICAL] Service '${service}' is NOT active!"
        FAILURES=$((FAILURES + 1))
    fi
}

echo "=== Maha Metro Platform Production Health Check ==="

# 1. Check Core Services
check_service nginx
check_service tomcat11
check_service mysql
check_service redis-server

# 2. Check Database Connectivity
if mysqladmin ping -u competency -pcompetency --silent 2>/dev/null; then
    echo "[OK] MySQL connection verified."
else
    echo "[CRITICAL] MySQL connection failed!"
    FAILURES=$((FAILURES + 1))
fi

# 3. Check Redis Ping
if redis-cli ping 2>/dev/null | grep -q "PONG"; then
    echo "[OK] Redis PONG verified."
else
    echo "[CRITICAL] Redis ping failed!"
    FAILURES=$((FAILURES + 1))
fi

# 4. Check Spring Boot Actuator Health
HEALTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health || true)
if [ "${HEALTH_HTTP}" == "200" ]; then
    echo "[OK] Actuator /actuator/health returned HTTP 200 OK."
else
    echo "[CRITICAL] Actuator health check returned HTTP ${HEALTH_HTTP}!"
    FAILURES=$((FAILURES + 1))
fi

# 5. Check Disk Free Space (>10% required)
DISK_FREE_PCT=$(df /var/mahametro/competency --output=pcent | tail -n 1 | tr -d ' %')
if [ "${DISK_FREE_PCT}" -lt 90 ]; then
    echo "[OK] Storage utilization is healthy (${DISK_FREE_PCT}% used)."
else
    echo "[WARNING] Storage space is low (${DISK_FREE_PCT}% used)!"
fi

echo "=========================================================="
if [ $FAILURES -eq 0 ]; then
    echo "STATUS: ALL HEALTH CHECKS PASSED (System Healthy)"
    exit 0
else
    echo "STATUS: ${FAILURES} CRITICAL FAILURES DETECTED!"
    exit 1
fi

#!/usr/bin/env bash
# ==============================================================================
# Maha Metro Competency Assessment Platform - Automated MySQL Backup Script
# Creates non-blocking online dumps with gzip compression and SHA256 verification.
# ==============================================================================

set -euo pipefail

DB_NAME="${DB_NAME:-competency}"
DB_USER="${DB_USER:-competency}"
DB_PASSWORD="${DB_PASSWORD:-competency}"
BACKUP_DIR="${BACKUP_DIR:-/var/mahametro/competency/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.sql.gz"
CHECKSUM_FILE="${BACKUP_FILE}.sha256"

mkdir -p "${BACKUP_DIR}"

echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] Starting MySQL online backup for database '${DB_NAME}'..."

# Run non-blocking mysqldump (InnoDB transactions remain fully read/write)
MYSQL_PWD="${DB_PASSWORD}" mysqldump \
  -u "${DB_USER}" \
  --single-transaction \
  --quick \
  --routines \
  --triggers \
  --events \
  --hex-blob \
  --default-character-set=utf8mb4 \
  "${DB_NAME}" | gzip -9 > "${BACKUP_FILE}"

# Generate SHA256 checksum for audit and integrity verification
sha256sum "${BACKUP_FILE}" > "${CHECKSUM_FILE}"

BACKUP_SIZE=$(du -h "${BACKUP_FILE}" | cut -f1)
echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] Backup completed successfully: ${BACKUP_FILE} (${BACKUP_SIZE})"

# Prune archives older than retention period
echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] Pruning backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_DIR}" -name "${DB_NAME}_*.sql.gz*" -type f -mtime "+${RETENTION_DAYS}" -delete

echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] Backup routine finished."

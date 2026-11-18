#!/usr/bin/env bash
# ==============================================================================
# Maha Metro Competency Assessment Platform - MySQL Database Restore Script
# Restores compressed database dump with checksum verification and safety checks.
# ==============================================================================

set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <path_to_backup_file.sql.gz>"
    exit 1
fi

BACKUP_FILE="$1"
DB_NAME="${DB_NAME:-competency}"
DB_USER="${DB_USER:-competency}"
DB_PASSWORD="${DB_PASSWORD:-competency}"

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "Error: Backup file '${BACKUP_FILE}' not found!"
    exit 1
fi

CHECKSUM_FILE="${BACKUP_FILE}.sha256"
if [ -f "${CHECKSUM_FILE}" ]; then
    echo "Verifying SHA256 checksum..."
    sha256sum -c "${CHECKSUM_FILE}"
else
    echo "Warning: Checksum file '${CHECKSUM_FILE}' not found, skipping checksum verification."
fi

echo "======================================================================"
echo "WARNING: Restoring will overwrite existing data in '${DB_NAME}'!"
echo "Target backup: ${BACKUP_FILE}"
echo "======================================================================"
read -p "Type 'RESTORE' to confirm: " CONFIRM

if [ "${CONFIRM}" != "RESTORE" ]; then
    echo "Restore cancelled by operator."
    exit 0
fi

echo "Restoring database '${DB_NAME}'..."
gunzip -c "${BACKUP_FILE}" | MYSQL_PWD="${DB_PASSWORD}" mysql -u "${DB_USER}" "${DB_NAME}"

echo "Database restore completed successfully!"

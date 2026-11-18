-- Steps 8 and 9: audit log, and account lockout after repeated wrong passwords
CREATE TABLE audit_log (
  id        BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  at        DATETIME(6)   NOT NULL,
  actor     VARCHAR(30)   NOT NULL DEFAULT '',
  action    VARCHAR(60)   NOT NULL,
  entity    VARCHAR(40)   NOT NULL DEFAULT '',
  entity_id VARCHAR(40)   NOT NULL DEFAULT '',
  detail    VARCHAR(2000) NOT NULL DEFAULT ''
);
CREATE INDEX ix_audit_log_at ON audit_log (at);

ALTER TABLE employee ADD COLUMN failed_logins INT NOT NULL DEFAULT 0;
ALTER TABLE employee ADD COLUMN locked_until DATETIME(6);

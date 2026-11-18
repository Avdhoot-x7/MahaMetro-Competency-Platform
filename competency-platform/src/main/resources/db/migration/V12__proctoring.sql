-- Step 18: Proctoring event logging, timing analysis and review
ALTER TABLE attempt ADD COLUMN reviewed_by VARCHAR(50) NULL;
ALTER TABLE attempt ADD COLUMN reviewed_at DATETIME(6) NULL;

CREATE TABLE proctor_event (
  id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  attempt_id  BIGINT NOT NULL,
  kind        VARCHAR(40) NOT NULL,
  detail      VARCHAR(500) NOT NULL DEFAULT '',
  media_id    BIGINT NULL,
  created_at  DATETIME(6) NOT NULL,
  CONSTRAINT fk_pe_attempt FOREIGN KEY (attempt_id) REFERENCES attempt (id) ON DELETE CASCADE
);

CREATE INDEX ix_pe_attempt_created ON proctor_event (attempt_id, created_at);

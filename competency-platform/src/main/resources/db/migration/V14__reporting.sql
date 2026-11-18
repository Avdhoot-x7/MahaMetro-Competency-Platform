-- Goal 8: Stored profile summaries for efficient dashboards and scaling to 25,000 staff
CREATE TABLE profile_summary (
  employee_id    BIGINT       NOT NULL PRIMARY KEY,
  readiness      VARCHAR(20)  NOT NULL DEFAULT 'Not assessed',
  index_pct      DECIMAL(5,1) NULL,
  assessed_count INT          NOT NULL DEFAULT 0,
  gaps_count     INT          NOT NULL DEFAULT 0,
  safety_gates   INT          NOT NULL DEFAULT 0,
  updated_at     DATETIME(6)  NOT NULL,
  CONSTRAINT fk_ps_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE CASCADE
);

CREATE INDEX ix_ps_readiness ON profile_summary (readiness);
CREATE INDEX ix_ps_updated_at ON profile_summary (updated_at);

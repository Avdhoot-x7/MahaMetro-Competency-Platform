-- Step 14: measured scores. One row is one score (0 to 100) for an employee in a competency,
-- for a cycle (T0 baseline, T1..T3 re-tests) and a method (knowledge, sjt, practical, rating).
CREATE TABLE competency_score (
  id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  employee_id     BIGINT       NOT NULL,
  competency_code VARCHAR(8)   NOT NULL,
  cycle           VARCHAR(10)  NOT NULL,
  method          VARCHAR(12)  NOT NULL,
  score           DECIMAL(5,2) NOT NULL,
  recorded_by     VARCHAR(30)  NOT NULL DEFAULT '',
  recorded_at     DATETIME(6)  NOT NULL,
  CONSTRAINT fk_cs_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
  CONSTRAINT fk_cs_competency FOREIGN KEY (competency_code) REFERENCES competency (code)
);
CREATE INDEX ix_cs_employee ON competency_score (employee_id, competency_code, cycle);

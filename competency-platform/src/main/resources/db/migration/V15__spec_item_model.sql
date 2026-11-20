-- Step 21: Spec-compliant Question item model, option metadata, and attempt micro-events
ALTER TABLE question ADD COLUMN difficulty_band VARCHAR(20) NOT NULL DEFAULT 'medium';
ALTER TABLE question ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'sme';
ALTER TABLE question ADD COLUMN gen_model VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE question ADD COLUMN gen_prompt_version VARCHAR(50) NOT NULL DEFAULT '';
ALTER TABLE question ADD COLUMN gen_batch_id VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE question ADD COLUMN qc_warnings TEXT NULL;
ALTER TABLE question ADD COLUMN source_ref VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE question ADD COLUMN rationale_en TEXT NULL;
ALTER TABLE question ADD COLUMN rationale_mr TEXT NULL;
ALTER TABLE question ADD COLUMN answer_numeric DOUBLE NULL;
ALTER TABLE question ADD COLUMN numeric_tolerance DOUBLE NULL;
ALTER TABLE question ADD COLUMN accepted_answers TEXT NULL;
ALTER TABLE question ADD COLUMN rubric_en TEXT NULL;
ALTER TABLE question ADD COLUMN rubric_mr TEXT NULL;
ALTER TABLE question ADD COLUMN needs_sme_validation BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE question ADD COLUMN p_value DOUBLE NULL;
ALTER TABLE question ADD COLUMN point_biserial DOUBLE NULL;
ALTER TABLE question ADD COLUMN stats_n INT NOT NULL DEFAULT 0;

ALTER TABLE question_option ADD COLUMN text_mr TEXT NULL;
ALTER TABLE question_option ADD COLUMN misconception TEXT NULL;
ALTER TABLE question_option ADD COLUMN weight DOUBLE NULL;
ALTER TABLE question_option ADD COLUMN seq_position INT NULL;
ALTER TABLE question_option ADD COLUMN match_en VARCHAR(1000) NOT NULL DEFAULT '';
ALTER TABLE question_option ADD COLUMN match_mr VARCHAR(1000) NOT NULL DEFAULT '';

CREATE TABLE attempt_event (
  id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  attempt_id  BIGINT NOT NULL,
  item_id     BIGINT NULL,
  kind        VARCHAR(40) NOT NULL,
  `value`     VARCHAR(1000) NOT NULL DEFAULT '',
  `at`        DATETIME(6) NOT NULL,
  CONSTRAINT fk_ae_attempt FOREIGN KEY (attempt_id) REFERENCES attempt (id) ON DELETE CASCADE,
  CONSTRAINT fk_ae_question FOREIGN KEY (item_id) REFERENCES question (id) ON DELETE SET NULL
);

CREATE INDEX ix_ae_attempt_at ON attempt_event (attempt_id, `at`);
CREATE INDEX ix_ae_kind ON attempt_event (kind);

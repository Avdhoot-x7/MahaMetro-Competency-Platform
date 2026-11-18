-- Step 17: test attempts, assembled items, answers and delivery
CREATE TABLE attempt (
  id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  test_id      BIGINT        NOT NULL,
  employee_id  BIGINT        NOT NULL,
  kind         VARCHAR(20)   NOT NULL DEFAULT 'T0',
  seed         BIGINT        NOT NULL,
  lang         VARCHAR(10)   NOT NULL DEFAULT 'en',
  started_at   DATETIME(6)   NOT NULL,
  deadline_at  DATETIME(6)   NOT NULL,
  submitted_at DATETIME(6),
  status       VARCHAR(20)   NOT NULL DEFAULT 'IN_PROGRESS',
  score        DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
  max_score    DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
  percent      DECIMAL(5,2),
  tab_switches INT           NOT NULL DEFAULT 0,
  flag_reason  VARCHAR(1000) NOT NULL DEFAULT '',
  client_info  VARCHAR(300)  NOT NULL DEFAULT '',
  CONSTRAINT fk_att_test FOREIGN KEY (test_id) REFERENCES test (id),
  CONSTRAINT fk_att_employee FOREIGN KEY (employee_id) REFERENCES employee (id)
);
CREATE INDEX ix_att_test_emp ON attempt (test_id, employee_id);
CREATE INDEX ix_att_status ON attempt (status);

CREATE TABLE attempt_item (
  id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  attempt_id         BIGINT       NOT NULL,
  question_id        BIGINT       NOT NULL,
  position           INT          NOT NULL DEFAULT 0,
  competency_code    VARCHAR(8)   NOT NULL,
  topic_no           VARCHAR(20)  NOT NULL DEFAULT '',
  qtype              VARCHAR(20)  NOT NULL DEFAULT 'single',
  marks              DECIMAL(5,2) NOT NULL DEFAULT 1.00,
  shuffled_option_ids VARCHAR(500) NOT NULL DEFAULT '',
  selected_options   VARCHAR(500) NOT NULL DEFAULT '',
  answered_text      VARCHAR(2000) NOT NULL DEFAULT '',
  time_spent_seconds INT          NOT NULL DEFAULT 0,
  awarded_score      DECIMAL(5,2),
  is_correct         BOOLEAN,
  answered_at        DATETIME(6),
  CONSTRAINT fk_ai_attempt FOREIGN KEY (attempt_id) REFERENCES attempt (id) ON DELETE CASCADE,
  CONSTRAINT fk_ai_question FOREIGN KEY (question_id) REFERENCES question (id)
);
CREATE INDEX ix_ai_attempt_pos ON attempt_item (attempt_id, position);

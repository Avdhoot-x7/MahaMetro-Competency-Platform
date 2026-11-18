-- Step 11: question bank with assignment, variants and a two-stage approval
-- status: DRAFT, SUBMITTED, REVIEWED, APPROVED, RETIRED
CREATE TABLE question_assignment (
  id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  contributor_emp_id VARCHAR(30) NOT NULL,
  competency_code    VARCHAR(8)  NOT NULL,
  target_level       INT         NOT NULL DEFAULT 3,
  quantity           INT         NOT NULL DEFAULT 1,
  due_date           DATE,
  assigned_by        VARCHAR(30) NOT NULL DEFAULT '',
  status             VARCHAR(12) NOT NULL DEFAULT 'OPEN',
  created_at         DATETIME(6) NOT NULL,
  CONSTRAINT fk_qa_competency FOREIGN KEY (competency_code) REFERENCES competency (code)
);
CREATE INDEX ix_qa_contributor ON question_assignment (contributor_emp_id);

CREATE TABLE question (
  id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  code             VARCHAR(40)   NOT NULL,
  parent_id        BIGINT,
  assignment_id    BIGINT,
  competency_code  VARCHAR(8)    NOT NULL,
  target_level     INT           NOT NULL DEFAULT 3,
  bloom            VARCHAR(12)   NOT NULL DEFAULT 'Understand',
  qtype            VARCHAR(20)   NOT NULL DEFAULT 'single',
  stem_en          VARCHAR(4000) NOT NULL,
  stem_mr          VARCHAR(4000) NOT NULL DEFAULT '',
  pool             VARCHAR(10)   NOT NULL DEFAULT 'exam',
  status           VARCHAR(12)   NOT NULL DEFAULT 'DRAFT',
  author_emp_id    VARCHAR(30)   NOT NULL,
  reviewer_emp_id  VARCHAR(30)   NOT NULL DEFAULT '',
  approver_emp_id  VARCHAR(30)   NOT NULL DEFAULT '',
  version_no       INT           NOT NULL DEFAULT 1,
  created_at       DATETIME(6)   NOT NULL,
  updated_at       DATETIME(6)   NOT NULL,
  CONSTRAINT uq_question_code UNIQUE (code),
  CONSTRAINT fk_q_parent FOREIGN KEY (parent_id) REFERENCES question (id),
  CONSTRAINT fk_q_assignment FOREIGN KEY (assignment_id) REFERENCES question_assignment (id),
  CONSTRAINT fk_q_competency FOREIGN KEY (competency_code) REFERENCES competency (code)
);
CREATE INDEX ix_question_status ON question (status);
CREATE INDEX ix_question_competency ON question (competency_code);

CREATE TABLE question_history (
  id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  question_id BIGINT        NOT NULL,
  at          DATETIME(6)   NOT NULL,
  actor       VARCHAR(30)   NOT NULL,
  from_status VARCHAR(12)   NOT NULL,
  to_status   VARCHAR(12)   NOT NULL,
  note        VARCHAR(2000) NOT NULL DEFAULT '',
  CONSTRAINT fk_qh_question FOREIGN KEY (question_id) REFERENCES question (id)
);
CREATE INDEX ix_qh_question ON question_history (question_id);

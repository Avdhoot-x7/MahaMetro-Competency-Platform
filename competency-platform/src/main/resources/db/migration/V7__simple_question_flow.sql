-- Step 13: simpler question flow. A programmer uploads questions from Excel, a subject expert approves,
-- and the question is then in the bank. The reviewer / approver / contributor / bank admin roles are removed.
DELETE FROM employee_role WHERE role_code IN ('contributor', 'reviewer', 'approver', 'bank_admin');
DELETE FROM role_permission WHERE role_code IN ('contributor', 'reviewer', 'approver', 'bank_admin');
DELETE FROM role_permission WHERE permission_code IN ('question.review', 'question.assign');
DELETE FROM app_role WHERE code IN ('contributor', 'reviewer', 'approver', 'bank_admin');
DELETE FROM app_permission WHERE code IN ('question.review', 'question.assign');

INSERT INTO app_role (code, name_en) VALUES
  ('programmer', 'Programmer (uploads questions)'),
  ('subject_expert', 'Subject expert (approves questions)');
INSERT INTO app_permission (code, description) VALUES
  ('question.manage', 'Retire questions and see the whole bank');
INSERT INTO role_permission (role_code, permission_code) VALUES
  ('programmer', 'question.write'),
  ('subject_expert', 'question.approve'),
  ('cell', 'question.manage');

-- status is now SUBMITTED (waiting for the subject expert), APPROVED (in the bank), REJECTED or RETIRED
ALTER TABLE question ADD COLUMN topic_no VARCHAR(20) NOT NULL DEFAULT '';
ALTER TABLE question ADD COLUMN variation_no INT NOT NULL DEFAULT 1;
ALTER TABLE question ADD COLUMN question_type VARCHAR(40) NOT NULL DEFAULT '';
ALTER TABLE question ADD COLUMN answer_type VARCHAR(40) NOT NULL DEFAULT '';
ALTER TABLE question ADD COLUMN time_seconds INT NOT NULL DEFAULT 60;
ALTER TABLE question ADD COLUMN difficulty VARCHAR(20) NOT NULL DEFAULT '';
ALTER TABLE question ADD COLUMN media_ref VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE question ADD COLUMN solution_text VARCHAR(4000) NOT NULL DEFAULT '';
ALTER TABLE question ADD COLUMN solution_media VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE question ADD COLUMN upload_batch VARCHAR(40) NOT NULL DEFAULT '';
ALTER TABLE question ADD COLUMN approved_at DATETIME(6);
CREATE INDEX ix_question_topic ON question (competency_code, topic_no);

CREATE TABLE question_option (
  id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  question_id BIGINT        NOT NULL,
  ord         INT           NOT NULL DEFAULT 0,
  text_en     VARCHAR(2000) NOT NULL,
  correct     BOOLEAN       NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_qo_question FOREIGN KEY (question_id) REFERENCES question (id)
);
CREATE INDEX ix_qo_question ON question_option (question_id);

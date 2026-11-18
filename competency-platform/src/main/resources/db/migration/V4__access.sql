-- Step 6: roles, permissions and scoped role assignments
CREATE TABLE app_role (
  code    VARCHAR(30)  NOT NULL PRIMARY KEY,
  name_en VARCHAR(120) NOT NULL
);

CREATE TABLE app_permission (
  code        VARCHAR(60)  NOT NULL PRIMARY KEY,
  description VARCHAR(200) NOT NULL DEFAULT ''
);

CREATE TABLE role_permission (
  role_code       VARCHAR(30) NOT NULL,
  permission_code VARCHAR(60) NOT NULL,
  PRIMARY KEY (role_code, permission_code),
  CONSTRAINT fk_rperm_role FOREIGN KEY (role_code) REFERENCES app_role (code),
  CONSTRAINT fk_rperm_perm FOREIGN KEY (permission_code) REFERENCES app_permission (code)
);

-- scope_type: ALL, DEPARTMENT, ROLE_FAMILY, COMPETENCY, LOCATION or TEST. scope_value holds the matching code.
CREATE TABLE employee_role (
  id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  employee_id BIGINT       NOT NULL,
  role_code   VARCHAR(30)  NOT NULL,
  scope_type  VARCHAR(20)  NOT NULL DEFAULT 'ALL',
  scope_value VARCHAR(120) NOT NULL DEFAULT '',
  granted_by  VARCHAR(30)  NOT NULL DEFAULT '',
  granted_at  DATETIME(6)  NOT NULL,
  CONSTRAINT uq_employee_role UNIQUE (employee_id, role_code, scope_type, scope_value),
  CONSTRAINT fk_er_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
  CONSTRAINT fk_er_role FOREIGN KEY (role_code) REFERENCES app_role (code)
);

INSERT INTO app_role (code, name_en) VALUES
  ('employee', 'Employee'),
  ('supervisor', 'Supervisor'),
  ('hod', 'Head of Department'),
  ('management', 'Management'),
  ('contributor', 'Question contributor'),
  ('reviewer', 'Question reviewer'),
  ('approver', 'Question approver'),
  ('bank_admin', 'Question bank administrator'),
  ('proctor', 'Proctor'),
  ('cell', 'Assessment Cell'),
  ('superadmin', 'Super administrator');

INSERT INTO app_permission (code, description) VALUES
  ('test.take', 'Take assigned tests and see own results'),
  ('team.view', 'See results of direct reports'),
  ('team.rate', 'Enter practical and on-the-job ratings'),
  ('department.view', 'See department results and benchmarks'),
  ('org.view', 'See the organisation scorecard'),
  ('question.write', 'Write questions and variants'),
  ('question.review', 'Review submitted questions'),
  ('question.approve', 'Give final approval to reviewed questions'),
  ('question.assign', 'Assign question-writing targets and manage the bank'),
  ('test.manage', 'Create, schedule and publish tests'),
  ('test.proctor', 'Watch live tests and review flagged attempts'),
  ('scoring.manual', 'Mark written and spoken answers'),
  ('employee.manage', 'Import and edit staff records'),
  ('framework.manage', 'Edit role profiles, cut scores and the training catalogue'),
  ('access.manage', 'Grant and remove roles'),
  ('audit.view', 'See the audit log');

INSERT INTO role_permission (role_code, permission_code) VALUES
  ('employee', 'test.take'),
  ('supervisor', 'team.view'), ('supervisor', 'team.rate'),
  ('hod', 'department.view'),
  ('management', 'org.view'), ('management', 'department.view'),
  ('contributor', 'question.write'),
  ('reviewer', 'question.review'),
  ('approver', 'question.approve'),
  ('bank_admin', 'question.assign'), ('bank_admin', 'question.write'),
  ('proctor', 'test.proctor'),
  ('cell', 'test.manage'), ('cell', 'test.proctor'), ('cell', 'scoring.manual'), ('cell', 'employee.manage'),
  ('cell', 'framework.manage'), ('cell', 'question.assign'), ('cell', 'department.view'), ('cell', 'org.view'),
  ('cell', 'team.view'), ('cell', 'audit.view');

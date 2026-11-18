-- Step 3: job structure and employee master
CREATE TABLE role_family (
  code       VARCHAR(8)   NOT NULL PRIMARY KEY,
  name_en    VARCHAR(120) NOT NULL,
  name_mr    VARCHAR(160) NOT NULL DEFAULT '',
  job_group  VARCHAR(8)   NOT NULL DEFAULT 'NT'
);

CREATE TABLE grade (
  code               VARCHAR(4)  NOT NULL PRIMARY KEY,
  band_en            VARCHAR(60) NOT NULL,
  band_mr            VARCHAR(80) NOT NULL DEFAULT '',
  core_offset        INT NOT NULL DEFAULT 0,
  behavioural_offset INT NOT NULL DEFAULT 0,
  domain_offset      INT NOT NULL DEFAULT 0
);

CREATE TABLE designation_map (
  id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  designation      VARCHAR(160) NOT NULL,
  role_family_code VARCHAR(8),
  grade_code       VARCHAR(4),
  CONSTRAINT uq_designation UNIQUE (designation),
  CONSTRAINT fk_dm_family FOREIGN KEY (role_family_code) REFERENCES role_family (code),
  CONSTRAINT fk_dm_grade FOREIGN KEY (grade_code) REFERENCES grade (code)
);

CREATE TABLE employee (
  id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  emp_id            VARCHAR(30)  NOT NULL,
  name_en           VARCHAR(160) NOT NULL,
  name_mr           VARCHAR(200) NOT NULL DEFAULT '',
  email             VARCHAR(160) NOT NULL DEFAULT '',
  mobile            VARCHAR(20)  NOT NULL DEFAULT '',
  designation       VARCHAR(160) NOT NULL DEFAULT '',
  department        VARCHAR(120) NOT NULL DEFAULT '',
  location          VARCHAR(120) NOT NULL DEFAULT '',
  role_family_code  VARCHAR(8),
  grade_code        VARCHAR(4),
  reporting_officer VARCHAR(30)  NOT NULL DEFAULT '',
  preferred_lang    VARCHAR(2)   NOT NULL DEFAULT 'en',
  extra_time_pct    INT          NOT NULL DEFAULT 0,
  password_hash     VARCHAR(100),
  active            BOOLEAN      NOT NULL DEFAULT TRUE,
  consent_at        DATETIME(6),
  last_login        DATETIME(6),
  source            VARCHAR(20)  NOT NULL DEFAULT 'manual',
  created_at        DATETIME(6)  NOT NULL,
  updated_at        DATETIME(6)  NOT NULL,
  CONSTRAINT uq_employee_emp_id UNIQUE (emp_id),
  CONSTRAINT fk_emp_family FOREIGN KEY (role_family_code) REFERENCES role_family (code),
  CONSTRAINT fk_emp_grade FOREIGN KEY (grade_code) REFERENCES grade (code)
);
CREATE INDEX ix_employee_department ON employee (department);
CREATE INDEX ix_employee_reporting_officer ON employee (reporting_officer);

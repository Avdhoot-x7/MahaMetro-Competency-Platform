-- Step 4: competency framework
CREATE TABLE competency (
  code            VARCHAR(8)    NOT NULL PRIMARY KEY,
  name_en         VARCHAR(160)  NOT NULL,
  name_mr         VARCHAR(200)  NOT NULL DEFAULT '',
  category        VARCHAR(20)   NOT NULL,
  safety_critical BOOLEAN       NOT NULL DEFAULT FALSE,
  description_en  VARCHAR(2000) NOT NULL DEFAULT '',
  description_mr  VARCHAR(2000) NOT NULL DEFAULT '',
  methods         VARCHAR(200)  NOT NULL DEFAULT ''
);

CREATE TABLE role_profile (
  id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_family_code VARCHAR(8) NOT NULL,
  competency_code  VARCHAR(8) NOT NULL,
  base_level       INT        NOT NULL DEFAULT 0,
  approved_by      VARCHAR(30),
  approved_at      DATETIME(6),
  CONSTRAINT uq_role_profile UNIQUE (role_family_code, competency_code),
  CONSTRAINT fk_rp_family FOREIGN KEY (role_family_code) REFERENCES role_family (code),
  CONSTRAINT fk_rp_competency FOREIGN KEY (competency_code) REFERENCES competency (code)
);

CREATE TABLE cut_score (
  level_no  INT          NOT NULL PRIMARY KEY,
  min_score DECIMAL(5,2) NOT NULL,
  label_en  VARCHAR(40)  NOT NULL,
  label_mr  VARCHAR(60)  NOT NULL DEFAULT ''
);

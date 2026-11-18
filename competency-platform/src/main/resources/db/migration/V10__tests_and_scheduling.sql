-- Step 16: tests, blueprints, and scheduling
CREATE TABLE blueprint (
  id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name             VARCHAR(160)  NOT NULL,
  role_family_code VARCHAR(8),
  grade_code       VARCHAR(8),
  notes            VARCHAR(2000) NOT NULL DEFAULT '',
  created_at       DATETIME(6)   NOT NULL
);

CREATE TABLE blueprint_rule (
  id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  blueprint_id    BIGINT      NOT NULL,
  competency_code VARCHAR(8)  NOT NULL,
  item_count      INT         NOT NULL DEFAULT 1,
  target_level    INT         NOT NULL DEFAULT 3,
  CONSTRAINT fk_br_blueprint FOREIGN KEY (blueprint_id) REFERENCES blueprint (id) ON DELETE CASCADE
);
CREATE INDEX ix_br_blueprint ON blueprint_rule (blueprint_id);

CREATE TABLE test (
  id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  code                 VARCHAR(40)   NOT NULL UNIQUE,
  title_en             VARCHAR(200)  NOT NULL,
  title_mr             VARCHAR(260)  NOT NULL DEFAULT '',
  instructions_en      VARCHAR(4000) NOT NULL DEFAULT '',
  instructions_mr      VARCHAR(4000) NOT NULL DEFAULT '',
  kind                 VARCHAR(20)   NOT NULL DEFAULT 'T0',
  blueprint_id         BIGINT,
  items_per_competency INT           NOT NULL DEFAULT 3,
  max_items            INT           NOT NULL DEFAULT 80,
  duration_minutes     INT           NOT NULL DEFAULT 90,
  start_at             DATETIME(6),
  end_at               DATETIME(6),
  max_attempts         INT           NOT NULL DEFAULT 1,
  shuffle_questions    BOOLEAN       NOT NULL DEFAULT TRUE,
  shuffle_options      BOOLEAN       NOT NULL DEFAULT TRUE,
  show_feedback        BOOLEAN       NOT NULL DEFAULT FALSE,
  allow_back           BOOLEAN       NOT NULL DEFAULT TRUE,
  target_type          VARCHAR(30)   NOT NULL DEFAULT 'ALL',
  target_value         VARCHAR(1000) NOT NULL DEFAULT '',
  status               VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
  created_by           VARCHAR(30)   NOT NULL DEFAULT '',
  created_at           DATETIME(6)   NOT NULL,
  updated_at           DATETIME(6)   NOT NULL,
  CONSTRAINT fk_test_blueprint FOREIGN KEY (blueprint_id) REFERENCES blueprint (id)
);
CREATE INDEX ix_test_status ON test (status);

-- Step 15: question and solution media files (images, audio, video)
CREATE TABLE media (
  id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  kind         VARCHAR(20)  NOT NULL,
  filename     VARCHAR(200) NOT NULL,
  stored_name  VARCHAR(200) NOT NULL,
  mime         VARCHAR(100) NOT NULL,
  file_size    BIGINT       NOT NULL,
  owner_emp_id VARCHAR(30)  NOT NULL,
  created_at   DATETIME(6)  NOT NULL
);
CREATE INDEX ix_media_owner ON media (owner_emp_id);

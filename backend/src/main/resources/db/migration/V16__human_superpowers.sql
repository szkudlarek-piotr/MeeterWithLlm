-- Flyway V16: human-superpower assignments.
-- MariaDB. Run after a database backup with the backend stopped.
CREATE TABLE IF NOT EXISTS human_superpower (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    human_id BIGINT NOT NULL,
    superpower_id BIGINT NOT NULL,
    add_time DATETIME(6) NOT NULL,
    added_by_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE human_superpower ADD CONSTRAINT uk_human_superpower_pair UNIQUE (human_id, superpower_id);
ALTER TABLE human_superpower ADD CONSTRAINT fk_human_superpower_human FOREIGN KEY (human_id) REFERENCES people (id);
ALTER TABLE human_superpower ADD CONSTRAINT fk_human_superpower_superpower FOREIGN KEY (superpower_id) REFERENCES superpowers (id);
ALTER TABLE human_superpower ADD CONSTRAINT fk_human_superpower_added_by FOREIGN KEY (added_by_id) REFERENCES app_users (id);

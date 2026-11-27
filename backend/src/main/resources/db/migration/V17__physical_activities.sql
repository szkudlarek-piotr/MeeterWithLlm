-- Flyway V17: personal physical activity log (one row per workout, owned by the account).
-- MariaDB. Run after a database backup with the backend stopped.
CREATE TABLE IF NOT EXISTS physical_activities (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uuid UUID NOT NULL DEFAULT UUID(),
    activity_type VARCHAR(32) NOT NULL,
    activity_date DATE NOT NULL,
    duration_minutes INT NOT NULL,
    description VARCHAR(1000) NULL,
    created_by_id BIGINT NOT NULL,
    add_time DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE physical_activities ADD CONSTRAINT uk_physical_activity_uuid UNIQUE (uuid);
ALTER TABLE physical_activities ADD CONSTRAINT fk_physical_activity_owner FOREIGN KEY (created_by_id) REFERENCES app_users (id);
CREATE INDEX idx_physical_activity_owner_date ON physical_activities (created_by_id, activity_date);

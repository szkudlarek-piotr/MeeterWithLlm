-- Flyway V21: family relations between people (parent -> child and spouses), private to the account that recorded them.
-- MariaDB. Run after a database backup with the backend stopped.
CREATE TABLE IF NOT EXISTS family_links (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    link_type VARCHAR(16) NOT NULL,
    person_a_id BIGINT NOT NULL,
    person_b_id BIGINT NOT NULL,
    created_by_id BIGINT NOT NULL,
    add_time DATETIME(6) NOT NULL,
    CONSTRAINT ck_family_links_distinct CHECK (person_a_id <> person_b_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE family_links ADD CONSTRAINT uk_family_links_pair UNIQUE (link_type, person_a_id, person_b_id, created_by_id);
ALTER TABLE family_links ADD CONSTRAINT fk_family_links_a FOREIGN KEY (person_a_id) REFERENCES people (id);
ALTER TABLE family_links ADD CONSTRAINT fk_family_links_b FOREIGN KEY (person_b_id) REFERENCES people (id);
ALTER TABLE family_links ADD CONSTRAINT fk_family_links_owner FOREIGN KEY (created_by_id) REFERENCES app_users (id);

-- Flyway V15: superpower metadata (public UUID, photo, creator, creation time).
-- The table is new in V14, so it is empty when this runs.
ALTER TABLE superpowers ADD COLUMN uuid UUID NOT NULL;
ALTER TABLE superpowers ADD COLUMN superpower_photo_id BIGINT NULL;
ALTER TABLE superpowers ADD COLUMN added_by_id BIGINT NULL;
ALTER TABLE superpowers ADD COLUMN add_time DATETIME(6) NOT NULL;
ALTER TABLE superpowers ADD CONSTRAINT uk_superpowers_uuid UNIQUE (uuid);
ALTER TABLE superpowers ADD CONSTRAINT fk_superpowers_photo FOREIGN KEY (superpower_photo_id) REFERENCES photos (id);
ALTER TABLE superpowers ADD CONSTRAINT fk_superpowers_added_by FOREIGN KEY (added_by_id) REFERENCES app_users (id);

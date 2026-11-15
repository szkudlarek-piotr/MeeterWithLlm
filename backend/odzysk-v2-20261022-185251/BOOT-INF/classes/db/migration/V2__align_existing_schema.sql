-- Older copies can predate portrait metadata and the explicit invitation flag.
-- Existing metadata/flags are preserved. Never infer historical invitations.
CREATE TABLE IF NOT EXISTS photos (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    generation_time DATETIME(6),
    latitude DOUBLE,
    longitude DOUBLE,
    photo_name VARCHAR(255),
    photo_type ENUM('EVENT','PROFILE_PICTURE','SUPERPOWER','TRIP') NOT NULL,
    uuid UUID
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE people ADD COLUMN IF NOT EXISTS profile_photo_id BIGINT;
ALTER TABLE people ADD UNIQUE INDEX IF NOT EXISTS UK90rfvnjh8ec5wmgpbmmeb3pc1 (profile_photo_id);
ALTER TABLE people ADD CONSTRAINT FKpye22v8sm6nunoba4asu6grni
    FOREIGN KEY IF NOT EXISTS FKpye22v8sm6nunoba4asu6grni (profile_photo_id) REFERENCES photos(id);
ALTER TABLE weddings ADD COLUMN IF NOT EXISTS was_i_invited BOOLEAN NOT NULL DEFAULT FALSE;

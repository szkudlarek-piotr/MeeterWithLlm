-- Flyway V14: dedicated superpowers dictionary, compatible with the original Meeter table.
-- MariaDB. Run after a database backup with the backend stopped.
CREATE TABLE IF NOT EXISTS superpowers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    superpower_name VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

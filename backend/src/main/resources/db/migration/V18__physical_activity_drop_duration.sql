-- Flyway V18: physical activity entries no longer store a duration.
-- Runs after V17 (which created the table with duration_minutes). MariaDB.
ALTER TABLE physical_activities DROP COLUMN duration_minutes;

-- Run manually once on the intended database after Flyway V11.
-- Verify that app_users.id = 1 is the intended account before proceeding.
START TRANSACTION;

UPDATE people
SET created_by_id = 1
WHERE EXISTS (SELECT 1 FROM app_users WHERE id = 1);

SELECT ROW_COUNT() AS updated_people;

COMMIT;

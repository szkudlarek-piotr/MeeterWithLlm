-- MariaDB: real_meeter -> vibecoded_meeter. Run the whole file in one session.
-- Before running: stop the application and other writers; back up BOTH databases.
-- First verify that people/place IDs have kept their original meaning.
-- Requires SELECT on real_meeter and routine/table creation rights on the target.
-- All referenced tables must use InnoDB. No foreign-key checks are disabled.
-- This imports meetings only; subsequent runs skip already imported meeting IDs.
-- Changes to source rows after an import are NOT synchronized.
USE vibecoded_meeter;

-- Persistent migration ledger: retain this table to prevent duplicate imports.
-- DDL is deliberately outside the data transaction (MariaDB implicit commits).
CREATE TABLE IF NOT EXISTS vibecoded_meeter.migration_real_meeter_meetings (
    source_meeting_id INT NOT NULL PRIMARY KEY,
    interaction_uuid UUID NOT NULL UNIQUE,
    imported_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

DELIMITER $$
DROP PROCEDURE IF EXISTS vibecoded_meeter.migrate_real_meeter_meetings_v1$$
CREATE PROCEDURE vibecoded_meeter.migrate_real_meeter_meetings_v1()
BEGIN
    DECLARE owner_id BIGINT;
    DECLARE owner_human_id BIGINT;
    DECLARE import_time DATETIME(6);
    DECLARE new_meetings BIGINT DEFAULT 0;
    DECLARE new_links BIGINT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    DROP TEMPORARY TABLE IF EXISTS vibecoded_meeter.migration_new_meeting_ids;
    CREATE TEMPORARY TABLE vibecoded_meeter.migration_new_meeting_ids (
        source_meeting_id INT NOT NULL PRIMARY KEY
    ) ENGINE=InnoDB;
    START TRANSACTION;
    SET import_time = UTC_TIMESTAMP(6);

    IF (SELECT COUNT(*) FROM vibecoded_meeter.app_users WHERE username = 'pszkudlarek') <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected exactly one account pszkudlarek';
    END IF;
    SELECT id, human_id INTO owner_id, owner_human_id
    FROM vibecoded_meeter.app_users WHERE username = 'pszkudlarek';

    IF EXISTS (
        SELECT 1 FROM real_meeter.meetings
        WHERE meeting_date IS NULL OR YEAR(meeting_date) < 1
           OR MONTH(meeting_date) < 1 OR DAYOFMONTH(meeting_date) < 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid meeting date';
    END IF;

    -- Never silently truncate old text to fit the target schema.
    IF EXISTS (
        SELECT 1 FROM real_meeter.meetings
        WHERE CHAR_LENGTH(TRIM(short_description)) > 128
           OR CHAR_LENGTH(long_desc) > 10240
           OR CHAR_LENGTH(place) > 200
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Meeting title >128, description >10240 or place >200 characters';
    END IF;

    IF EXISTS (
        SELECT 1 FROM real_meeter.meetings v
        LEFT JOIN vibecoded_meeter.places p ON p.id = v.place_id
        WHERE v.place_id IS NOT NULL AND p.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing migrated place ID';
    END IF;
    IF EXISTS (
        SELECT 1 FROM real_meeter.meeting_human g
        LEFT JOIN vibecoded_meeter.people p ON p.id = g.human_id
        LEFT JOIN real_meeter.meetings v ON v.ID = g.meeting_id
        WHERE p.id IS NULL OR v.ID IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing migrated guest or source meeting';
    END IF;

    -- Detect a damaged ledger instead of recreating deleted/partial imports.
    IF EXISTS (
        SELECT 1 FROM vibecoded_meeter.migration_real_meeter_meetings m
        LEFT JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
        LEFT JOIN vibecoded_meeter.meetings v ON v.id = i.id
        WHERE i.id IS NULL OR v.id IS NULL
           OR i.created_by_id IS NULL OR i.created_by_id <> owner_id
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Migration ledger does not match existing meetings';
    END IF;

    INSERT INTO vibecoded_meeter.migration_new_meeting_ids (source_meeting_id)
    SELECT v.ID
    FROM real_meeter.meetings v
    LEFT JOIN vibecoded_meeter.migration_real_meeter_meetings m ON m.source_meeting_id = v.ID
    WHERE m.source_meeting_id IS NULL;
    SET new_meetings = ROW_COUNT();

    INSERT INTO vibecoded_meeter.migration_real_meeter_meetings
        (source_meeting_id, interaction_uuid, imported_at)
    SELECT source_meeting_id, UUID(), import_time FROM vibecoded_meeter.migration_new_meeting_ids;

    INSERT INTO vibecoded_meeter.interactions
        (created_at, end_date, long_desc, name, start_date, uuid, created_by_id, place_id)
    SELECT m.imported_at,
           CAST(v.meeting_date AS DATETIME),
           v.long_desc,
           COALESCE(NULLIF(TRIM(v.short_description), ''), CONCAT('Spotkanie #', v.ID)),
           CAST(v.meeting_date AS DATETIME), m.interaction_uuid, owner_id, v.place_id
    FROM real_meeter.meetings v
    JOIN vibecoded_meeter.migration_real_meeter_meetings m ON m.source_meeting_id = v.ID
    LEFT JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
    WHERE i.id IS NULL;

    INSERT INTO vibecoded_meeter.meetings (id, place_string)
    SELECT i.id, v.place
    FROM real_meeter.meetings v
    JOIN vibecoded_meeter.migration_real_meeter_meetings m ON m.source_meeting_id = v.ID
    JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
    LEFT JOIN vibecoded_meeter.meetings existing ON existing.id = i.id
    WHERE existing.id IS NULL;

    -- Only new imports get links, so reruns preserve later participant edits.
    -- UNION deduplicates source guests and adds the account-linked person.
    INSERT INTO vibecoded_meeter.interaction_human (created_at, created_by_id, human_id, interaction_id)
    SELECT m.imported_at, owner_id, guests.human_id, i.id
    FROM (
        SELECT g.meeting_id, g.human_id AS human_id FROM real_meeter.meeting_human g
        UNION
        SELECT v.ID, owner_human_id FROM real_meeter.meetings v
    ) guests
    JOIN vibecoded_meeter.migration_real_meeter_meetings m ON m.source_meeting_id = guests.meeting_id
    JOIN vibecoded_meeter.migration_new_meeting_ids fresh ON fresh.source_meeting_id = guests.meeting_id
    JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
    WHERE NOT EXISTS (
          SELECT 1 FROM vibecoded_meeter.interaction_human ih
          WHERE ih.interaction_id = i.id AND ih.human_id = guests.human_id
      );
    SET new_links = ROW_COUNT();

    IF EXISTS (
        SELECT 1 FROM (
            SELECT g.meeting_id, g.human_id FROM real_meeter.meeting_human g
            UNION
            SELECT s.ID, owner_human_id FROM real_meeter.meetings s
        ) expected
        JOIN vibecoded_meeter.migration_new_meeting_ids fresh
          ON fresh.source_meeting_id = expected.meeting_id
        JOIN vibecoded_meeter.migration_real_meeter_meetings m
          ON m.source_meeting_id = expected.meeting_id
        JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
        LEFT JOIN vibecoded_meeter.interaction_human ih
          ON ih.interaction_id = i.id AND ih.human_id = expected.human_id
        WHERE ih.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Imported participant link missing';
    END IF;

    IF (SELECT COUNT(*) FROM real_meeter.meetings) <>
       (SELECT COUNT(*) FROM real_meeter.meetings s
        JOIN vibecoded_meeter.migration_real_meeter_meetings m ON m.source_meeting_id = s.ID
        JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
        JOIN vibecoded_meeter.meetings v ON v.id = i.id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Imported meeting count mismatch';
    END IF;

    COMMIT;
    SELECT new_meetings AS imported_meetings, new_links AS imported_participant_links;
END$$
DELIMITER ;

CALL vibecoded_meeter.migrate_real_meeter_meetings_v1();
DROP PROCEDURE vibecoded_meeter.migrate_real_meeter_meetings_v1;

-- Verification: counts should match, missing_meetings should be zero.
SELECT
    (SELECT COUNT(*) FROM real_meeter.meetings) AS source_meetings,
    COUNT(v.id) AS migrated_meetings,
    COUNT(*) - COUNT(v.id) AS missing_meetings
FROM real_meeter.meetings s
LEFT JOIN vibecoded_meeter.migration_real_meeter_meetings m ON m.source_meeting_id = s.ID
LEFT JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
LEFT JOIN vibecoded_meeter.meetings v ON v.id = i.id;

-- Old ID -> new ID. No source IDs are forced into the shared interactions key.
SELECT m.source_meeting_id, i.id AS new_interaction_id
FROM vibecoded_meeter.migration_real_meeter_meetings m
JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
ORDER BY m.source_meeting_id;

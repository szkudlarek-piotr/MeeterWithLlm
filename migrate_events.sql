-- MariaDB: real_meeter -> vibecoded_meeter. Run the whole file in one session.
-- Before running: stop the application and other writers; back up BOTH databases.
-- First verify that people/place IDs have kept their original meaning.
-- Requires SELECT on real_meeter and routine/table creation rights on the target.
-- All referenced tables must use InnoDB. No foreign-key checks are disabled.
-- This imports events only; subsequent runs skip already imported event IDs.
-- Changes to source rows after an import are NOT synchronized.
-- Photos are intentionally omitted: Generic_photo is not imported.
-- Local event timestamps are copied without timezone conversion.
USE vibecoded_meeter;

-- Persistent migration ledger: retain this table to prevent duplicate imports.
-- DDL is deliberately outside the data transaction (MariaDB implicit commits).
CREATE TABLE IF NOT EXISTS vibecoded_meeter.migration_real_meeter_events (
    source_event_id INT NOT NULL PRIMARY KEY,
    interaction_uuid UUID NOT NULL UNIQUE,
    imported_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

-- Persistent diagnostics; source date strings survive even for zero dates.
CREATE TABLE IF NOT EXISTS vibecoded_meeter.migration_real_meeter_event_warnings (
    source_event_id INT NOT NULL PRIMARY KEY,
    recorded_at DATETIME(6) NOT NULL,
    action_taken VARCHAR(64) NOT NULL,
    warning_text VARCHAR(255) NOT NULL,
    source_start VARCHAR(32),
    source_stop VARCHAR(32),
    source_coming VARCHAR(32),
    source_leaving VARCHAR(32)
) ENGINE=InnoDB;

DELIMITER $$
DROP PROCEDURE IF EXISTS vibecoded_meeter.migrate_real_meeter_events_v1$$
CREATE PROCEDURE vibecoded_meeter.migrate_real_meeter_events_v1()
BEGIN
    DECLARE owner_id BIGINT;
    DECLARE owner_human_id BIGINT;
    DECLARE import_time DATETIME(6);
    DECLARE new_events BIGINT DEFAULT 0;
    DECLARE new_links BIGINT DEFAULT 0;
    DECLARE skipped_events BIGINT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    DROP TEMPORARY TABLE IF EXISTS vibecoded_meeter.migration_new_event_ids;
    CREATE TEMPORARY TABLE vibecoded_meeter.migration_new_event_ids (
        source_event_id INT NOT NULL PRIMARY KEY
    ) ENGINE=InnoDB;
    START TRANSACTION;
    SET import_time = UTC_TIMESTAMP(6);

    IF (SELECT COUNT(*) FROM vibecoded_meeter.app_users WHERE username = 'pszkudlarek') <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected exactly one account pszkudlarek';
    END IF;
    SELECT id, human_id INTO owner_id, owner_human_id
    FROM vibecoded_meeter.app_users WHERE username = 'pszkudlarek';

    -- No date-range SIGNAL: retain source chronology and record anomalies.
    -- Numeric date checks also detect incomplete dates and impossible days.
    INSERT INTO vibecoded_meeter.migration_real_meeter_event_warnings
        (source_event_id, recorded_at, action_taken, warning_text,
         source_start, source_stop, source_coming, source_leaving)
    SELECT id, import_time,
           CASE WHEN NOT (COALESCE(YEAR(dateStart) >= 1 AND MONTH(dateStart) >= 1
                     AND DAYOFMONTH(dateStart) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(dateStart)), 0)
                  AND COALESCE(YEAR(dateStop) >= 1 AND MONTH(dateStop) >= 1
                     AND DAYOFMONTH(dateStop) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(dateStop)), 0))
                THEN 'SKIPPED_INVALID_EVENT_DATE' ELSE 'IMPORTED_WITH_WARNING' END,
           'Source dates anomalous; ranges preserved; invalid personal dates become NULL',
           CAST(dateStart AS CHAR), CAST(dateStop AS CHAR),
           CAST(meComingDate AS CHAR), CAST(meLeavingDate AS CHAR)
    FROM real_meeter.events
    WHERE NOT (
        COALESCE(YEAR(dateStart) >= 1 AND MONTH(dateStart) >= 1
            AND DAYOFMONTH(dateStart) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(dateStart)), 0)
        AND COALESCE(YEAR(dateStop) >= 1 AND MONTH(dateStop) >= 1
            AND DAYOFMONTH(dateStop) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(dateStop)), 0)
        AND COALESCE(YEAR(meComingDate) >= 1 AND MONTH(meComingDate) >= 1
            AND DAYOFMONTH(meComingDate) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(meComingDate)), 0)
        AND COALESCE(YEAR(meLeavingDate) >= 1 AND MONTH(meLeavingDate) >= 1
            AND DAYOFMONTH(meLeavingDate) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(meLeavingDate)), 0)
        AND COALESCE(dateStop >= dateStart AND meComingDate >= dateStart
            AND meComingDate <= dateStop AND meLeavingDate >= meComingDate
            AND meLeavingDate <= dateStop, 0)
    )
    ON DUPLICATE KEY UPDATE recorded_at = VALUES(recorded_at),
        action_taken = VALUES(action_taken), warning_text = VALUES(warning_text),
        source_start = VALUES(source_start), source_stop = VALUES(source_stop),
        source_coming = VALUES(source_coming), source_leaving = VALUES(source_leaving);

    SELECT COUNT(*) INTO skipped_events FROM real_meeter.events
    WHERE NOT (
        COALESCE(YEAR(dateStart) >= 1 AND MONTH(dateStart) >= 1
            AND DAYOFMONTH(dateStart) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(dateStart)), 0)
        AND COALESCE(YEAR(dateStop) >= 1 AND MONTH(dateStop) >= 1
            AND DAYOFMONTH(dateStop) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(dateStop)), 0)
    );

    -- Never silently truncate old text to fit the target schema.
    IF EXISTS (
        SELECT 1 FROM real_meeter.events
        WHERE CHAR_LENGTH(TRIM(nameOfEvent)) > 128
           OR CHAR_LENGTH(description) > 10240
           OR CHAR_LENGTH(place) > 200
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Event title >128, description >10240 or place >200 characters';
    END IF;

    IF EXISTS (
        SELECT 1 FROM real_meeter.events v
        LEFT JOIN vibecoded_meeter.places p ON p.id = v.place_id
        WHERE v.place_id IS NOT NULL AND p.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing migrated place ID';
    END IF;
    IF EXISTS (
        SELECT 1 FROM real_meeter.event_companion g
        LEFT JOIN vibecoded_meeter.people p ON p.id = g.human_id
        LEFT JOIN real_meeter.events v ON v.id = g.event_id
        WHERE p.id IS NULL OR v.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing migrated guest or source event';
    END IF;

    -- Detect a damaged ledger instead of recreating deleted/partial imports.
    IF EXISTS (
        SELECT 1 FROM vibecoded_meeter.migration_real_meeter_events m
        LEFT JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
        LEFT JOIN vibecoded_meeter.events v ON v.id = i.id
        WHERE i.id IS NULL OR v.id IS NULL
           OR i.created_by_id IS NULL OR i.created_by_id <> owner_id
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Migration ledger does not match existing events';
    END IF;

    INSERT INTO vibecoded_meeter.migration_new_event_ids (source_event_id)
    SELECT v.id
    FROM real_meeter.events v
    LEFT JOIN vibecoded_meeter.migration_real_meeter_events m ON m.source_event_id = v.id
    WHERE m.source_event_id IS NULL
      AND COALESCE(YEAR(v.dateStart) >= 1 AND MONTH(v.dateStart) >= 1
          AND DAYOFMONTH(v.dateStart) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(v.dateStart)), 0)
      AND COALESCE(YEAR(v.dateStop) >= 1 AND MONTH(v.dateStop) >= 1
          AND DAYOFMONTH(v.dateStop) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(v.dateStop)), 0);
    SET new_events = ROW_COUNT();

    INSERT INTO vibecoded_meeter.migration_real_meeter_events
        (source_event_id, interaction_uuid, imported_at)
    SELECT source_event_id, UUID(), import_time FROM vibecoded_meeter.migration_new_event_ids;

    INSERT INTO vibecoded_meeter.interactions
        (created_at, end_date, long_desc, name, start_date, uuid, created_by_id, place_id)
    SELECT m.imported_at,
           v.dateStop,
           v.description,
           COALESCE(NULLIF(TRIM(v.nameOfEvent), ''), CONCAT('Wydarzenie #', v.id)),
           v.dateStart, m.interaction_uuid, owner_id, v.place_id
    FROM real_meeter.events v
    JOIN vibecoded_meeter.migration_real_meeter_events m ON m.source_event_id = v.id
    LEFT JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
    WHERE i.id IS NULL;

    INSERT INTO vibecoded_meeter.events
        (id, place_string, me_coming_date, me_leaving_date, photo_file, photo_name)
    SELECT i.id, v.place,
           CASE WHEN YEAR(v.meComingDate) >= 1 AND MONTH(v.meComingDate) >= 1
                AND DAYOFMONTH(v.meComingDate) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(v.meComingDate))
                THEN v.meComingDate ELSE NULL END,
           CASE WHEN YEAR(v.meLeavingDate) >= 1 AND MONTH(v.meLeavingDate) >= 1
                AND DAYOFMONTH(v.meLeavingDate) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(v.meLeavingDate))
                THEN v.meLeavingDate ELSE NULL END,
           NULL, NULL
    FROM real_meeter.events v
    JOIN vibecoded_meeter.migration_real_meeter_events m ON m.source_event_id = v.id
    JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
    LEFT JOIN vibecoded_meeter.events existing ON existing.id = i.id
    WHERE existing.id IS NULL;

    -- Only new imports get links, so reruns preserve later participant edits.
    -- UNION deduplicates source guests and adds the account-linked person.
    INSERT INTO vibecoded_meeter.interaction_human (created_at, created_by_id, human_id, interaction_id)
    SELECT m.imported_at, owner_id, guests.human_id, i.id
    FROM (
        SELECT g.event_id, g.human_id AS human_id FROM real_meeter.event_companion g
        UNION
        SELECT v.id, owner_human_id FROM real_meeter.events v
    ) guests
    JOIN vibecoded_meeter.migration_real_meeter_events m ON m.source_event_id = guests.event_id
    JOIN vibecoded_meeter.migration_new_event_ids fresh ON fresh.source_event_id = guests.event_id
    JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
    WHERE NOT EXISTS (
          SELECT 1 FROM vibecoded_meeter.interaction_human ih
          WHERE ih.interaction_id = i.id AND ih.human_id = guests.human_id
      );
    SET new_links = ROW_COUNT();

    IF EXISTS (
        SELECT 1 FROM (
            SELECT g.event_id, g.human_id FROM real_meeter.event_companion g
            UNION
            SELECT s.id, owner_human_id FROM real_meeter.events s
        ) expected
        JOIN vibecoded_meeter.migration_new_event_ids fresh
          ON fresh.source_event_id = expected.event_id
        JOIN vibecoded_meeter.migration_real_meeter_events m
          ON m.source_event_id = expected.event_id
        JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
        LEFT JOIN vibecoded_meeter.interaction_human ih
          ON ih.interaction_id = i.id AND ih.human_id = expected.human_id
        WHERE ih.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Imported participant link missing';
    END IF;

    IF (SELECT COUNT(*) FROM real_meeter.events) - skipped_events <>
       (SELECT COUNT(*) FROM real_meeter.events s
        JOIN vibecoded_meeter.migration_real_meeter_events m ON m.source_event_id = s.id
        JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
        JOIN vibecoded_meeter.events v ON v.id = i.id
        WHERE COALESCE(YEAR(s.dateStart) >= 1 AND MONTH(s.dateStart) >= 1
            AND DAYOFMONTH(s.dateStart) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(s.dateStart)), 0)
          AND COALESCE(YEAR(s.dateStop) >= 1 AND MONTH(s.dateStop) >= 1
            AND DAYOFMONTH(s.dateStop) BETWEEN 1 AND DAYOFMONTH(LAST_DAY(s.dateStop)), 0)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Imported event count mismatch';
    END IF;

    COMMIT;
    SELECT new_events AS imported_events, new_links AS imported_participant_links,
           skipped_events AS source_events_with_invalid_boundary_dates;
END$$
DELIMITER ;

CALL vibecoded_meeter.migrate_real_meeter_events_v1();
DROP PROCEDURE vibecoded_meeter.migrate_real_meeter_events_v1;

-- Missing events can be explained by SKIPPED_INVALID_EVENT_DATE warnings.
SELECT
    (SELECT COUNT(*) FROM real_meeter.events) AS source_events,
    COUNT(v.id) AS migrated_events,
    COUNT(*) - COUNT(v.id) AS missing_events
FROM real_meeter.events s
LEFT JOIN vibecoded_meeter.migration_real_meeter_events m ON m.source_event_id = s.id
LEFT JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
LEFT JOIN vibecoded_meeter.events v ON v.id = i.id;

-- Old ID -> new ID. No source IDs are forced into the shared interactions key.
SELECT m.source_event_id, i.id AS new_interaction_id
FROM vibecoded_meeter.migration_real_meeter_events m
JOIN vibecoded_meeter.interactions i ON i.uuid = m.interaction_uuid
ORDER BY m.source_event_id;

SELECT * FROM vibecoded_meeter.migration_real_meeter_event_warnings
ORDER BY source_event_id;

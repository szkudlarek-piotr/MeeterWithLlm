-- Flyway V19: activity types become free-text categories (up to 64 characters).
-- Existing enum values are converted to the Polish labels used by the first panel.
ALTER TABLE physical_activities MODIFY activity_type VARCHAR(64) NOT NULL;
UPDATE physical_activities SET activity_type = CASE activity_type
    WHEN 'RUN' THEN 'Bieg'
    WHEN 'WALK' THEN 'Spacer'
    WHEN 'BIKE' THEN 'Rower'
    WHEN 'GYM' THEN 'Siłownia'
    WHEN 'SWIM' THEN 'Pływanie'
    ELSE 'Inne'
END;

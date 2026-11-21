# Importing trips from the original database

`../migrate_trips.sql` is a separate, manual import from `real_meeter` to `vibecoded_meeter`, not a Flyway migration. Do not add it to `db/migration`. The source is only read. The script assumes MariaDB 10.7+ and InnoDB tables.

## Before running

1. Stop the backend and any other processes that write to either database.
2. Export both databases completely in phpMyAdmin (SQL format, structure and data). Keep the existing photo files.
3. Confirm that the person and place IDs in both databases point to the same records, as in the previous imports. The mere existence of an ID does not confirm identity.
4. The account `pszkudlarek` must exist exactly once in the target database and have a linked person.

## Running in phpMyAdmin

Select **vibecoded_meeter** → **Import** → choose the file `migrate_trips.sql` from the project root → run the import of the whole file. You can also paste the whole content into SQL; the client must support `DELIMITER` and procedures. Do not run the individual INSERT statements separately. The import needs SELECT on the source and write permissions, CREATE TABLE and CREATE ROUTINE/ALTER ROUTINE/EXECUTE on the target.

The script creates the log and the procedure outside a transaction. The trip data, the links and the log entries are written in one transaction. An error causes a ROLLBACK of the data; the empty log table and the procedure may remain. Do not delete the import log. Do not use a client option that continues after errors. An error must be explained before running the script again.

## Mapping

| Source | Target |
| --- | --- |
| `citybreaks.ID` | `migration_real_meeter_trips.source_trip_id`; a new interaction ID is created independently |
| `Place` | name after TRIM; an empty name → `Trip #ID`; the original text is also kept in the log |
| `Date_start`, `Date_stop` | `interactions.start_date`, `end_date`, with no time zone conversion |
| `long_description` | `interactions.long_desc` with the content unchanged |
| `category` | kept in the log; the application's current type is TRIP |
| `start_place_id` | `interactions.place_id` and a place in `trip_place` |
| `citybreak_companion` | `interaction_human`, plus the person linked to the import account |
| `trip_place` | target `trip_place`; duplicates and a repeated start place are merged |

New links receive their own IDs, the UTC import time and the creator `app_users.id`. This time is migration metadata, not a recovered historical time. Maps use the existing places and their coordinates. The Place text field is not geocoded and is not split into new places. Costs (`trip_expenses`, `trip_expenses_summary`) are not imported; the target model has no such feature. The links do not keep the order of places, because the application stores a set.

Validation stops the import on invalid or reversed dates, a name longer than 128 characters, a description that is too long, a missing person or place, orphaned links or a damaged log. It does not truncate texts, invent dates or delete source records.

## Result and repeating

Check `imported_trips`, `imported_participant_links` and `imported_place_links`. The final `missing_trips` must be 0. The last table shows the mapping from the old ID to the new interaction ID.

Running the script again skips the IDs that have already been imported: the `imported_*` counters should be 0. It keeps the later changes of names and links made in the application, with their IDs and metadata. It does not synchronise later changes in the source. A shared lock prevents two trip imports from running at the same time; it does not replace stopping the other processes that write to the database.

After success, start the backend and check the Trips tab and the maps in the participant's profile. No entity changes or Flyway migrations are needed.

## Synthetic test

`backend/Test-TripImport.ps1` creates two unique test databases, using the connection from `set_agent_sandbo_env.ps1`. It uses only synthetic records, and in `finally` deletes only the databases it created. It needs CREATE/DROP DATABASE and the MariaDB client. It checks the first import, the repeat, preservation of later edits, duplicates, local dates, missing links, the title, adding new records and a damaged log. It does not run the application.

```powershell
# From the project root, on the test server:
.\backend\Test-TripImport.ps1
```

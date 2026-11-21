# Flyway migrations (MariaDB 10.7+)

## Backup before an upgrade

After stopping the backend, run `.\backup.ps1` from the project root. The script creates a `backup_DD_MM_YYYY_HH_MM` folder in the system Documents folder. It uses only the `DB_URL`, `DB_USER` and `DB_PASSWORD` variables inherited from the current PowerShell session (the same ones you use to start the backend), without loading the sandbox script. If the configuration is missing, it stops. It exports the selected MariaDB/MySQL database to `database.sql` with `mariadb-dump.exe` or `mysqldump.exe`, then copies the person portraits, event photos, the former-portrait archive, clique photos and category icons. `manifest.json` contains sizes and SHA-256 hashes; missing folders are reported explicitly. It does not copy the script with passwords. The SQL file itself contains personal data and account hashes, so the backup must be stored privately.

The script looks for the dump client on the PATH or in typical installation folders. To point to your own location, use `.\backup.ps1 -DumpExecutable 'C:\Program Files\MariaDB 11.4\bin\mariadb-dump.exe'`. The password is passed through a temporary options file with an ACL limited to the current user, deleted in `finally`, not in the process arguments. The export needs permissions to dump tables, procedures, events and triggers. A JDBC URL with extra options is rejected, so that it cannot skip requirements such as TLS.

The optional `-DestinationRoot`, `-BackendPort` and `-PhotoDirectories @{ people='...'; events='...'; former='...'; cliques='...'; categories='...' }` parameters allow other paths; relative photo paths are resolved from `backend`. The default paths follow `application.properties` and the matching Spring variables `APP_PHOTOS_*`. If the paths are passed at startup as JVM or Spring arguments, or through an extra profile, pass the same paths with `-PhotoDirectories`.

The backend port is read from the `SERVER_PORT` variable (default 8091) and can be overridden with `-BackendPort`. The backend must stay stopped for the whole operation; the script checks for a listener at the start and end, but does not stop processes. Pause any other write sources and DDL changes too. The export uses `--single-transaction` for InnoDB. An existing folder from the same minute is not overwritten. An error leaves an `INCOMPLETE.txt` marker; only a copy without that marker is complete. The script does not run migrations or restores and does not start automatically.

The manual import of private trips from the original database is described in [TRIP_IMPORT.md](TRIP_IMPORT.md); the script `../migrate_trips.sql` stays outside Flyway.

The schema is changed by Flyway 12.4.0, the version managed by Spring Boot 4.1.1. Hibernate uses `ddl-auto=validate`: it checks the mappings but does not create or update tables. Migrations from `src/main/resources/db/migration` run before JPA initialisation when the backend starts. Their numbers, checksums and results are stored in `flyway_schema_history`. `clean` is disabled.

Before the first start on an existing database, stop the backend, save a full database backup and keep the photo files. Check that `DB_URL` points to the new Meeter database, never to the original `real_meeter`. Do not copy the Flyway history between databases by hand.

- `V1__initial_schema.sql`: the complete initial schema for an empty database. It contains no personal data, passwords or identifiers from the sandbox; it was generated from the entity mappings.
- An existing non-empty database without history is automatically baselined at 1. Flyway skips V1 and keeps the records. This assumes the existing schema of this application, not an arbitrary old structure. [Baseline semantics in the Flyway documentation](https://documentation.red-gate.com/flyway/reference/commands/baseline).
- `V2__align_existing_schema.sql`: adds the photo metadata table, the portrait link and the wedding invitation flag to older copies. It does not overwrite existing flags; when the flag is missing, the value is false, without guessing an invitation. It keeps relations and data.
- `V3__product_news.sql`: the news table and its first entry; the fixed slug protects against a duplicate even after an earlier manual import of the entry.
- `V4__clique_photos.sql`: the photo type CLIQUE and `cliques.photo_id`. Importing photos from a folder is run only by the administrator in the panel; details are in [CLIQUES.md](CLIQUES.md).
- `V6__former_human_photos.sql`: extends the `photos.photo_type` enum with `FORMER_HUMAN_PHOTO`. It does not move or modify existing photos; archiving happens only when a portrait is explicitly replaced. Before starting the new version, stop the backend and back up the database and the photo folders.
- `V7__auth_event_history.sql`: the persistent register of sign-ins, failed attempts, limiter blocks, sign-outs and password changes. It has no passwords or tokens and no cascading deletion together with the account or session. Details are in AUTH_REVIEW.md; it does not fill in historical sign-ins.
- `V8__public_entity_uuids.sql`: adds and backfills persistent, unique, non-null UUID columns on places, cliques, game rounds and news. Existing primary/foreign keys and audit metadata are preserved. Browser requests and image URLs use the UUID `/api/v2` contract; see UUID_API.md. Back up and stop the backend before adoption.
- `V14__superpowers.sql`: creates the separate `superpowers` dictionary table (`id`, `superpower_name`) compatible with the original Meeter table. It is not the `photos` table or its `SUPERPOWER` type; importing data from the old database remains a separate, manual operation. Before deployment, stop the backend and make a backup.
- `V15__superpower_metadata.sql`: extends `superpowers` with a public `uuid`, an optional photo (`superpower_photo_id`), the author (`added_by_id`) and the creation time (`add_time`). The table is new in V14, so the migration works on an empty table; it does not move existing data.
- `V16__human_superpowers.sql`: the `human_superpower` link table (`id`, `human_id`, `superpower_id`, `add_time`, `added_by_id`) with a unique pair and foreign keys to `people`, `superpowers` and `app_users`. It does not import old links; before deployment, stop the backend and make a backup.

To run only the migrations (without the server and data initialisers), from the project root:

```powershell
. .\set_agent_sandbo_env.ps1
$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
Push-Location backend
try {
    .\mvnw.cmd flyway:info
    .\mvnw.cmd flyway:migrate
    .\mvnw.cmd flyway:validate
} finally { Pop-Location }
```

The backend can also run the migrations during a normal start, after setting `DB_URL`, `DB_USER` and `DB_PASSWORD` in the same PowerShell session. Point these at your own database. You move the application files and new migrations; you do not need to export existing data just to apply a schema change or a product news entry.

Add new changes in `V5__description.sql`, `V6__description.sql` and so on. Never edit migrations that have already been applied, or their checksums. Do not use `repair` to hide differences. Do not add `USE` statements, sandbox names, account data or credentials. Imports of private visits, meetings and events from the root folder remain separate operations and do not run through Flyway.

MariaDB DDL can commit changes outside a transaction. After an error, check the table state and the history before repeating the operation; a full backup is the basis for recovery. `CREATE TABLE IF NOT EXISTS` alone does not confirm that an existing table is compatible; JPA validation at startup does that as well.

The application tests still use an isolated H2 with `create-drop` and Flyway disabled. `FlywayMigrationTests` checks creation of an empty database and the repeat of V1/V3/V4 on H2 in MariaDB mode; the MariaDB-specific V2 must be checked on the sandbox. `SchemaExportTests` writes only the reference schema to `target/current-mariadb-schema.sql`, without connecting to user data.

# UUID browser API

The browser uses `/api/v2` for every API call. Its record references are persistent UUIDs, including request path variables, JSON relationship fields, nested response DTOs, authentication account/person references, game rounds, leaderboard references and image URLs. Existing field names such as `id`, `humanId`, `participantIds` and `roundId` remain, but their browser-facing values are UUID strings. Numeric counts, positions, durations and pagination are unchanged. Model algorithm names are not database identifiers.

The internal `/api` contract remains available for existing integrations and tests. It is not used by the frontend. Both contracts use the same authentication, CSRF protection, DTO validation, services and record ownership rules. UUIDs do not confer access to another account's records.

## Boundary

`publicapi/UuidApiFilter` removes the version prefix before the existing Spring Security and MVC handlers, marking the request as UUID-based. `UuidPathVariables` resolves UUID path references after authentication. `UuidRequestBody` translates relationship references before existing typed request validation. Numeric references are not accepted by the UUID contract: invalid paths return 404, invalid body identifiers return 400 and unknown records return 404. Missing or inaccessible domain records retain existing 404 semantics.

`UuidResponse` projects the existing record DTOs into the UUID wire format after domain authorization. It has an explicit registry for primary identifiers and typed reference fields, including game/player/round identities. Unknown primary-ID DTOs fail rather than silently exposing a numeric ID. UUIDs are loaded in batches per entity kind for each response, including portrait URLs, rather than in per-card HTTP calls or per-portrait database queries. The Python participant model and its trusted internal account/history data continue to use internal IDs; browser input is resolved before that boundary.

The existing numeric `id` columns remain primary keys. Foreign keys, indexes used for ranking, participant/trip-place identities and audit metadata retain their meaning. No contact merge, re-ownership or ID renumbering occurs.

## Persistent UUIDs and V8

Humans, interactions, quotes, games and accounts already have UUIDs. V8 adds real UUID columns to `places`, `cliques`, `quote_game_rounds` and `app_news`, backfills each existing row independently, and then enforces NOT NULL and uniqueness. New entities generate UUIDs in Java; a database default also supplies UUIDs for manual SQL inserts. UUIDs are immutable. They are not generated from numeric IDs or names. Existing UUIDs on other tables are unchanged.

Stop the backend and back up the database and image directories before adopting V8. MariaDB DDL implicitly commits, so preserve the backup and inspect schema/history after a failed migration; do not blindly rerun or repair it. Applied migrations must not be edited. The agent does not run V8 against user databases as part of tests.

Reload the browser after deploying the backend and frontend together to replace any in-memory objects from the old numeric contract. Numeric integration clients can continue using `/api`; browser UI and image URLs use `/api/v2`. The admin person selector no longer displays `#<database ID>` labels.

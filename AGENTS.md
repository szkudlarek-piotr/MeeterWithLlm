# Agent starting point: Meeter

## Files agents must not read

- `experiments/participants/history.json` contains an exported personal interaction history. Agents must not open, read, parse, search, quote, or otherwise inspect its contents, including through scripts or diagnostic tools. Only an explicit later user instruction authorizing access to this file may override this prohibition. User-run training and export commands may continue to use this file.

Read this file first when working on this project. It applies to the entire project; also check for more specific `AGENTS.md` files in directories you change. Follow the user's current request and preserve unrelated work.

## What this project is

Schema changes are now versioned with Flyway; see `backend/MIGRATIONS.md`. Hibernate uses `ddl-auto=validate`, not update. V1 bootstraps empty MariaDB schemas; existing application databases are baselined at 1 and receive V2/V3 onward. Back up and stop the backend before first adoption. Never edit applied migrations or automatically run personal-data import scripts through Flyway. Application tests disable Flyway on isolated H2; migration tests separately verify bootstrap/repeat behavior.

Current-copy override: `application.properties` now points to a MariaDB/MySQL sandbox and `pom.xml` includes the MariaDB driver. Older H2 development instructions below are historical for this copy. Demo-data and test-account generators have been removed. Existing persisted demo records and ignored credential files are not automatically deleted.

Before database work or backend startup, check the project-root PowerShell script `set_agent_sandbo_env.ps1`, which the user provides to configure the agent's database environment variables. Inspect it before running, then dot-source it in the same PowerShell session as the command that needs those variables (for example, `. .\set_agent_sandbo_env.ps1`). Do not print or copy credentials into chat, logs, or documentation. If database variables are missing, check this script before asking the user for connection configuration.

Meeter is a local personal relationship and interaction tracker. The interface is in Polish and includes people, places, cliques (groups), an interaction feed, and a monthly calendar. Authentication connects an account to a person. Keep existing Polish UI text and date conventions unless the task asks for a language change.

The frontend is React with JavaScript/JSX and Vite. The backend is Java 25, Spring Boot 4.1.1, Spring Security, Spring Data JPA, and file-backed H2. Dependency versions and scripts are defined in `package.json`, `package-lock.json`, and `backend/pom.xml`; check these files before changing the toolchain.

This is a development implementation with its own local database. Do not assume it connects to the original Meeter database or contains imported production data.

## Read next

- `backend/REGISTRATION.md`: publiczny formularz rejestracji z jednorazowym JWT, migracja V11 i jawny skrypt przypisania istniejących osób użytkownikowi 1.

Backup exception requested by the user: root backup.ps1 must use only the caller's DB_URL, DB_USER and DB_PASSWORD and must never dot-source set_agent_sandbo_env.ps1 or silently select a sandbox. Missing database environment must fail. Photo paths use caller APP_PHOTOS_* variables ahead of application.properties/defaults; SERVER_PORT supplies the default backend port. See backend/MIGRATIONS.md. This supersedes the generic sandbox-environment instruction above for this backup script.

- `backend/ADMIN_USERS.md`: admin-only creation of ROLE_USER accounts from existing unlinked Humans, generated passwords, and the explicitly requested local passwords.csv export. Never print, commit or return generated passwords through HTTP.

- `backend/PARTICIPATION_BACKFILL.md`: participation-based ranking and explicit admin self-membership backfill. This current rule supersedes older creator-only ranking descriptions. Never run the bulk write automatically.

- `backend/CLIQUES.md`: ranked owned clique directory, ranked member portraits, admin folder photo import, and Flyway V4. Photo import is explicit, never automatic at startup; back up before schema adoption.

- `backend/README.md`: setup, entity APIs, authentication, persistence, and testing.
- `backend/INTERACTIONS.md`: the six interaction types, their API contracts, date rules, and modeling decisions.
- `backend/PHOTOS.md`: event images, portrait import, storage, remote URL protections, and demo data.
- `backend/RELATIONSHIPS.md`: association entities, relationship metadata, and the legacy H2 schema migration.
- `backend/QUOTES.md`: quote creation, public/author/admin reads, validation, and visibility semantics.
- `backend/QUOTE_GAME.md`: public-only sampling, individual games, server timers, guess persistence, and leaderboard scoring.
- `backend/AUTH_REVIEW.md`: authentication design and current limitations.

These documents are mostly in Polish. Verify behavior against current source code when documentation and implementation differ. External links in them are background references; local code is the starting point for local changes.

## Source map

Aktualności now show database-backed product news (`app_news`), not an interaction feed. See `backend/NEWS.md`; keep news reads authenticated and publication admin-only with CSRF. The standalone quote collection UI and GET collection API are removed; quote creation, single-record visibility, people cards and quote game remain.

| Location | Purpose |
| --- | --- |
| `src/main.jsx` | React entry point |
| `src/App.jsx` | Welcome screen, login, logout, password changes, theme, session state |
| `src/api.js` | Shared API requests, CSRF headers, session expiration handling |
| `src/Dashboard.jsx` | Dashboard, search, feed, and form/calendar coordination |
| `src/CalendarPanel.jsx` | Calendar presentation and person avatars |
| `src/InteractionForm.jsx` | Forms and presentation helpers for interaction types |
| `src/EventPhotoPicker.jsx` | Optional event image from a local file, public URL, or earlier owned event |
| `src/QuoteForm.jsx` | Quote creation with an optional author; quotes appear in people cards and the guessing game |
| `src/QuoteGamePanel.jsx` | Quote guessing setup, visit selection, timer, answers, own history, and leaderboard |
| `src/HumanForm.jsx`, `src/EntityForms.jsx`, `src/EntityPicker.jsx` | People, places, cliques, and relation selection |
| `src/styles/` | Styled-components, shared styles and themes |
| `vite.config.js` | Development proxy from `/api` to backend port 8091 |
| `backend/src/main/java/org/example/meeter/` | Core entities, repositories, DTOs, controller, service, errors, and demo initialization |
| `backend/src/main/java/org/example/meeter/auth/` | Accounts, tokens, authentication, authorization context, CSRF configuration, login throttling |
| `backend/src/main/java/org/example/meeter/quote/` | Quote entity, repository, DTOs, service, and REST controller |
| `backend/src/main/java/org/example/meeter/quote/game/` | Game pool selection, persistent sessions/rounds/guesses, timer enforcement, and ranking |
| `backend/src/main/java/org/example/meeter/interactions/` | Interaction hierarchy, API, validation, queries |
| `backend/src/main/java/org/example/meeter/interactions/RelationshipRecord.java` | Shared identity, creation timestamp, and creator metadata for association rows |
| `backend/src/main/java/org/example/meeter/interactions/InteractionParticipant.java`, `TripPlace.java` | Explicit association entities for `interaction_human` and `trip_place` |
| `backend/src/main/java/org/example/meeter/RelationshipSchemaMigration.java` | Idempotent legacy H2 relationship-table upgrade, executed before Hibernate |
| `backend/src/main/java/org/example/meeter/photos/` | Person and event image endpoints, download policy, HTTP client, and local storage |
| `backend/src/main/java/org/example/meeter/photos/EventPhotoStorage.java` | Event image normalization and rollback cleanup |
| `backend/src/main/resources/application.properties` | Local backend configuration |
| `backend/src/test/java/org/example/meeter/` | Application, HTTP integration, photo URL policy, and relationship migration tests |

`node_modules/`, `dist/`, and `backend/target/` are dependencies or generated output. Edit source files, then rebuild as needed. Do not treat `backend-starter.zip` or compiled classes as authoritative source.

## Run locally

Use two terminals. Commands below are for PowerShell on Windows. Check for existing listeners before starting duplicate servers. Keep backend working directory set to `backend`, because database and photo paths are relative.

Frontend, from the project root:

```powershell
# Only needed when dependencies are missing or need restoring:
npm.cmd ci
npm.cmd run dev -- --host 127.0.0.1
```

The normal frontend address is `http://127.0.0.1:5173`. Vite may choose another port if that port is occupied; read its startup output.

Backend, from `backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

The backend listens at `http://127.0.0.1:8091`. The Maven Wrapper obtains Maven and dependencies when necessary. Java 25 must be available.

If Java needs the Windows certificate store, use the documented trust configuration:

```powershell
$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
.\mvnw.cmd spring-boot:run
```

`backend/start.ps1` checks source/resource, POM and Maven Wrapper content hashes and the packaged JAR before startup. It runs `clean package -DskipTests` on changes or a missing build cache/JAR; otherwise it starts the existing JAR immediately, with that certificate configuration. The first run creates the cache in `backend/target/`. Use `-Rebuild` to force a build. From `backend`:

```powershell
.\start.ps1
# Optional forced rebuild:
.\start.ps1 -Rebuild
```

In a restricted agent environment, Java may fail with `Unable to establish loopback connection` or `Permission denied: connect`, and frontend proxy requests may also be blocked. Request the environment's required execution permission and rerun the affected command; do not disable TLS, CSRF, or authentication to work around sandbox restrictions.

## Verify changes

- Frontend: run `npm.cmd run build` from the root. There is currently no frontend test or lint script in `package.json`.
- Backend: run `.\mvnw.cmd test` from `backend`. Tests use an in-memory H2 database; keep tests isolated from development data.
- For relationship changes, cover generated IDs, server timestamps, authenticated creators, pair uniqueness, legacy-row preservation, migration repeatability, and cascade behavior. `RelationshipSchemaMigrationTests` exercises the legacy schema; `ApiIntegrationTests` covers the application flow.
- For event image changes, cover file and URL import, reuse of an owned event image, rejection of invalid sources, access isolation, and atomic failure. Use temporary image directories in tests.
- For quotes, cover trimmed text length (4–10240), optional and foreign authors, generated metadata, absence of the collection endpoint, public/author/admin visibility, authentication, and CSRF. Delete test quotes before their referenced people/accounts during cleanup.
- For games, cover public-only sampling even for admins, at most two distinct quotes per author, visit/clique scope ownership, stable persisted order/deadlines, duplicate and invalid guesses, timeout/resume, self-quote exclusion, completed-only leaderboard, and foreign-game isolation. Delete games before quotes in test cleanup.
- For UI changes, also inspect the affected flow in a browser when available, including relevant loading/error states and both themes.
- For server startup, verify an HTTP response from the frontend, `GET /api/auth/csrf` on the backend, and that endpoint through the Vite proxy. A successful startup log alone does not prove the proxy works.
- Report what was checked and any remaining limitations. A documentation-only change does not require application test runs.

## Persistence and credentials

The local database is `backend/data/meeter-dev.mv.db`. Portraits and demo assets also live under `backend/data/`. This directory is ignored by Git but contains persistent user data: do not delete, reset, replace, or seed over it as a routine troubleshooting step. Stop the backend before any explicitly authorized direct database maintenance; avoid opening the file concurrently from a second process.

Before a schema upgrade, stop the backend and copy the database to a separate backup file. `RelationshipSchemaMigration` runs before Hibernate initializes, supports H2 only, and preserves existing association rows. H2 DDL is not atomic across the whole upgrade; preserve repeatability of each migration step. Missing legacy parent metadata causes startup to fail rather than deleting rows or inventing an owner. See `backend/RELATIONSHIPS.md` for the backfill rules.

User images are runtime data, stored outside the packaged application:

- Person portraits: `backend/data/photos/people/`, configured by `app.photos.directory`.
- Event images: `backend/data/photos/events/`, configured by `app.photos.events-directory`.

Do not move user images into `src/main/resources/static`: reads must go through controllers that enforce authentication and record ownership. Static resources are suitable for fixed application assets such as a logo or default avatar.

The development bootstrap creates account `pszkudlarek` linked to Piotr Szkudlarek, person ID 77, when needed. It writes an initial random password to `backend/data/initial-login.txt`. That file may be stale after a password reset; it is not an authoritative record of the current password. Do not put passwords, tokens, or database contents in agent documentation or commit them. No current password is recorded here.

`app.dev.bootstrap` controls the primary account bootstrap. Demo-data and test-account generators have been removed; the old sample-data and test-accounts flags no longer create records. Existing records remain persisted.

## People directory

Exclude the authenticated account's linked Human from directory results, search and total counts before pagination, including for admins. Identify that person by Human ID, never by name. Other people with the same name remain visible. Person pickers and photo access rules are unchanged.

Exclude Humans whose clique name normalizes to my_exes from the directory query, total count, search and directory portrait endpoint, including for admins. Keep null-clique people visible. For equal internal scores sort by surname, then first name, then ID before pagination.

Social Score is internal to server-side ordering. Never return its numeric value through APIs (including to admins), log it as user data, or display the score/formula in the UI. The public directory DTO contains person, visitCount, and publicQuote only.

The Ludzie tab requests `directory=true` on `/api/people/browse` and includes only people with Social Score strictly greater than 2. Apply the threshold before pagination and to the search total. Unfiltered API callers retain the existing browse behavior.

Each directory card may include one random public quote by that Human (`publicQuote: {id, quote}` or null). Filter to `is_public=true` before drawing, including for admins; never reuse the broader private/admin quote visibility predicate. Draw only for current-page author IDs, with no per-card HTTP requests.

The directory is now ordered by decayed interaction `points`, not visit count. `PeopleRanking` computes the global order before pagination using interactions containing the authenticated account's linked Human as a participant; see backend/README.md for weights, dates and rounding. Wedding scoring requires `weddings.was_i_invited=true` and applies to groom/bride only: WAGS by normalized clique name gets 3, others 15, half-life 1825 days. Import the original invitation flag; do not infer it from participants or assume the old clique ID 5. Existing schema/data changes require the usual backup; do not start against development data as part of tests.

Explicit user-requested exception: authenticated `GET /api/people/browse` lists ALL Human records, including unowned/imported people and people owned by other accounts. Directory portraits use authenticated `GET /api/people/browse/{id}/photo`. Counts still include only the requesting account's visits. This does not list user accounts or grant mutation rights. Existing person pickers, original person-photo URLs, writes, and unrelated APIs retain ownership checks.

## Design rules to preserve

UUID browser contract: src/api.js and image URLs use /api/v2 with persistent UUID strings for all entity references, including fields still named id/*Id/*Ids. Internal /api and domain/repository Long IDs remain for compatibility. publicapi boundary adapters translate references and project authorized DTOs; never expose a new numeric ID through v2 or bypass domain access checks. V8 adds/backfills actual UUID columns on places, cliques, quote_game_rounds and app_news, retaining all PK/FK and audit metadata. Batch response UUID lookups by entity type, and keep model-worker IDs internal. See backend/UUID_API.md. Changes must cover UUID paths, bodies, images, game rounds, invalid references, CSRF and ownership. Do not run schema upgrades against user data as tests.

Authentication audit: V7 creates auth_events, storing LOGIN_SUCCESS, LOGIN_FAILURE, LOGIN_THROTTLED, LOGOUT and PASSWORD_CHANGED with server time, optional account/session record IDs, normalized login, connection IP and bounded User-Agent. Never store passwords, raw tokens, token hashes or request bodies in audit. Rejected login/throttle writes use a separate REQUIRES_NEW bean; successful changes and audit commit together. History has no account/token FK or cascade, no public API, and must survive token/account deletion. CSRF/body validation rejections and natural expiration are outside this history's current scope. See backend/AUTH_REVIEW.md.

- Use `src/api.js` for frontend API requests. Browser requests use relative `/api` paths, same-origin credentials, and CSRF headers for writes.
- Authentication uses random tokens in HttpOnly cookies, with SHA-256 token hashes in the database. Passwords use BCrypt. Do not move credentials into localStorage or expose account entities in API responses.
- The normal password-change flow validates password length and invalidates existing sessions. A one-off local password reset is not a reason to weaken application validation.
- Derive data ownership from the authenticated account, never from a client-supplied owner ID. Enforce ownership on relation assignments and ordinary entity reads. Quotes have the explicit visibility rules below; an admin role does not bypass ownership for unrelated APIs. Missing and inaccessible records should remain indistinguishable through 404 responses.
- Return DTOs rather than serializing JPA entities. Keep validation and domain behavior in the existing service/model structure.
- The six interaction types are meeting, visit, event, trip (`Citybreak` in Java), flatmate, and wedding. They share `Interaction` using JPA `JOINED` inheritance; they are not all subclasses of `Event`.
- Preserve local date and local date-time semantics. Do not silently convert interaction dates to UTC. Calendar filtering must include overlapping ranges, ongoing periods, and all result pages needed for the month.
- Preserve automatic participation of the account's linked person and applicable wedding participants, deduplication, and owner checks.
- Many-to-many relationships are represented by explicit `InteractionParticipant` and `TripPlace` entities, not bare `@ManyToMany` collections. Each has an identity `id`, immutable `created_at`, and immutable `created_by_id` referring to an account (which links to a person). Preserve pair uniqueness and cascade deletion of relationship rows without deleting their people/places. The API still returns participant and place summaries. Legacy metadata is inferred from the parent interaction; it is not a recovered historical audit trail. Read `backend/RELATIONSHIPS.md` before changing these mappings or their migration.
- `getParticipants()` and `getPlaces()` return read-only endpoint sets. Preserve existing association rows during unrelated edits so their IDs and audit metadata remain stable. If relationship-editing APIs are added, new rows must record the account performing that edit, which may differ from the original interaction owner. Never accept creator metadata from the client.
- Participant editing now uses POST `/api/interactions/{id}/participants` (interaction creator, owned people only) and DELETE `/api/interactions/{id}/participants/{humanId}` (self by linked Human ID or link creator). There is no admin bypass. Mutations lock the parent; existing links retain metadata. Explicit removal may remove automatically added participants; it does not remove their person records or wedding/flatmate domain references. Read/list visibility remains owner-only. Calendar hides FLATMATE without deleting records. See `backend/RELATIONSHIPS.md`.
- Preserve photo download protections against private/local addresses and unsafe redirects, resource limits, image normalization, and transaction-aware cleanup. See `backend/PHOTOS.md` before changing this module.
- Some visible categories can be placeholders (for example, superpowers). Check for actual backend support before describing a feature as implemented.

## Event creation and images

Photo migration compatibility: `org.example.meeter.multimedia.photo.Photo` maps the original `photos` metadata table with string `PhotoType` values PROFILE_PICTURE, EVENT, TRIP, SUPERPOWER. `Human.profilePhoto` uses `people.profile_photo_id`; migrated portrait files belong in `app.photos.directory` under their original `photo_name`. Serve them only through the authenticated person photo endpoint. Keep the legacy `profile_photo_file` fallback and do not cascade-delete Photo metadata. See `backend/PHOTOS.md` for migration limits and file handling.

`InteractionForm.jsx` handles event start/end, optional personal arrival/departure, an optional selected place plus independent `placeString`, description, and selected people. Blank personal dates mean the event boundaries. Keep personal participation inside the event range and validate ordering on both client and server.

Create an event with one `POST /api/events` request through `src/api.js`. The event, participant links, and optional new image are handled within one transaction; do not split participant creation into a second request.

`EventPhotoPicker.jsx` supports no image, a local file, a public URL, or reuse of an earlier owned event's image. The request accepts at most one image source:

- `photoData`: Base64 file contents without a data URL prefix.
- `photoUrl`: a direct public image URL, downloaded by the protected `RemotePhotoClient`.
- `photoEventId`: an owned event with an existing local image.

`photoName` is an optional display label, not a filesystem path. Source files are limited to 4 MB and 16 megapixels. Event images become metadata-free JPEGs with preserved aspect ratio and a longest side of at most 1600 pixels; person portraits are cropped to 512 by 512 pixels. Generated filenames must remain independent of user labels.

Event DTOs expose `details.photoUrl`, `details.photoName`, and `details.placeString`. `GET /api/events/{id}/photo` checks ownership and serves the local JPEG. Reused images share a file: account for all references before introducing image deletion or replacement. Remove newly created files on transaction rollback. The image selector searches earlier events by event name and paginates those events; pages can contain no images.

## Quotes

Person profile exception: authenticated `/api/people/browse/{id}` exposes profile details and `canEditPhoto`; its paginated `/interactions` and `/map` reads include meetings, visits, events and trips involving that Human only when the viewer created the interaction, participates by linked Human ID, or is admin. Map points use the same predicate across all results, before pagination. Ordinary interaction APIs, mutations and event photo reads retain their original ownership rules. Profile `/quotes` paginates that Human's quotes using the existing public/author/admin visibility predicate. Profile photo changes allow the person record owner or an administrator (including unowned/imported and foreign-owned Humans), accepting exactly one URL or Base64 file. This exception applies only to photo writes; it does not reassign ownership or broaden ordinary person/photo reads. No schema migration is needed.

Render quote text through `src/QuoteContent.jsx` in the people directory and quote game. Quotes are centered and support a formatting-only HTML allowlist rebuilt as React elements from an inert template. Do not copy source attributes or mount raw HTML; scripts, embedded content and active markup must stay excluded. Keep database text unchanged.

`Quote` lives in `org.example.meeter.quote` and extends the existing `OwnedEntity`. Use this project's actual `org.example.meeter.Human` and `org.example.meeter.auth.User` packages rather than imports from the original application. The database stores text in `quote_text` (length 10240), an optional author relation, `is_public`, generated `add_time` (`LocalDateTime`), UUID, identity ID, and the authenticated creator. Creation time, UUID, and ownership are server-managed; return DTOs rather than entities.

`POST /api/quotes` accepts `{ quote, authorId, isPublic }`. Text is stripped and must have 4–10240 characters; author ID is optional and must identify an owned person. Keep entity validation, request validation, and the Polish form consistent about the minimum of 4 characters. `isPublic` defaults to false.

`GET /api/quotes/{id}` applies quote visibility: public quotes for authenticated accounts, private quotes for the account linked to their author, all quotes for ROLE_ADMIN, and authorless private quotes for their creator. The standalone collection endpoint GET /api/quotes and its search/pagination service have been removed; preserve the people-card public-only query and game sampling.

`isPublic` controls actual visibility to other authenticated users. There is no anonymous quote endpoint. DTOs expose only an author summary; an author's portrait URL is omitted when the requesting account does not own that person, because the portrait API keeps its existing ownership checks. Do not expose account details or broaden unrelated person/photo APIs.

Roles are persisted enum values (`ROLE_USER`, `ROLE_ADMIN`) in the `user_roles` element collection. `CurrentAccount.Principal` and authentication DTOs include roles; token authentication and `UserDetailsService` use these stored authorities. New accounts default to `ROLE_USER`; `RoleBootstrap` backfills that role for existing accounts. With local `app.dev.bootstrap=true`, it also grants `pszkudlarek` both roles idempotently. This local admin bootstrap must remain disabled outside development. Roles are read from the database during token authentication, not supplied by clients; there is no public role-grant endpoint.

The dashboard has no standalone Cytaty view. Keep the Dodaj cytat action and QuoteForm.jsx with EntityPicker and src/api.js. Saving opens and refreshes the people directory. Quotes appear in people cards and the guessing game, not as dated calendar events.

## Quote guessing game

Each player starts an individual game from one owned visit or up to ten owned cliques. A visit contributes all its participants; selected cliques contribute their union of members. Keep cliques as the existing private organization of contacts unless the user explicitly authorizes a redesign.

`QuoteGamePool` samples **public quotes only**, without an administrator exception: up to two distinct quotes per scoped author, then shuffle the combined list. Use all sampled quotes, without an additional overall question limit. Quotes without authors are excluded. Persist the draw, round order, candidate links, and text/author snapshots; never reroll on reload or resume. Recheck that the source quotes remain public before returning game text or accepting guesses.

The per-question timer defaults to 30 seconds (10–120 configurable). Its persisted server deadline is authoritative. Repeated reads or `next` calls must not reset an active deadline. Start later questions only when the player requests the next question; feedback time should not consume its timer. An expired answer is incorrect even if its author is correct; an expired resumed question records a timeout. Pending question DTOs must omit the quote ID and correct author. Only reveal the author after the guess is saved.

Store guesses in `quote_guesses`, including guessed author (nullable on timeout), correctness, timeout, self-quote flag, elapsed time, creator, and creation timestamp. Do not accept correctness, timing, ownership, or score from the client. Lock the game while answering and retain the unique constraint on the round so retries/concurrent submissions cannot overwrite an answer. Session history and guesses remain owner-only, including for admins.

Rank completed games by percentage of correctly guessed **other people's** quotes. Exclude the account-linked person's own quotes from both numerator and denominator. Zero eligible quotes means percentage null and no leaderboard entry. Show the best completed game per account, at most 100 accounts; ties use shorter total answer time on non-self quotes, then completion time and game ID. The authenticated leaderboard exposes player names and aggregate results, not quote text, private source names, or other players' guesses.

`QuoteGame`, `GameRound`, `QuoteGuess`, `GameCandidate`, and `GameClique` persist the game. Association rows use `RelationshipRecord` for IDs and creator/timestamp metadata. Cascades must delete only game-owned records, not source people, quotes, visits, cliques, or accounts. Read `backend/QUOTE_GAME.md` for endpoint contracts. Games use the shared API/CSRF helper and preserve backend state through refresh by resuming a stored game from history.

Author identity uses the existing `Human` ID linked to each account. Do not infer identity from names or silently merge different owners' contact records. A shared person identity and multi-circle contact organization are future design decisions, not part of this implementation.

## Working approach

Local participant-model inference: the backend automatically starts `experiments/participants/server.py` when `app.dev.bootstrap=true` and its `.venv` and trained artifacts exist. It generates an internal token, never reads `history.json`, and stops its own child on shutdown. Tests with bootstrap disabled must not start Python. See `experiments/participants/README.md` for overrides and external-worker mode; do not launch a duplicate worker as a routine backend startup step.

Shared participant rankers are trained explicitly with `experiments/participants/train-shared.ps1`; see `experiments/participants/SHARED_MODELS.md`. Training uses a read-only database snapshot in memory, never `history.json`. Three `shared_*.joblib` artifacts serve both suggestion modes across accounts and contain no account history. Runtime `ParticipantHistory` builds creator-owned, account-scoped features for the trusted Python worker; do not accept that history from browser input, share it between accounts, or give the worker database credentials. Legacy per-account models remain a fallback. Never train automatically at backend startup.

New shared training writes feature schema 3: additive exponentially decayed interaction counts (default half-life 365 days, `-HalfLifeDays`), place counts and weekday/place-weekday counts, with the query's local start date. Same-day/future history is excluded. Existing schema 2 artifacts keep legacy features until explicitly retrained; never silently reinterpret their weights. See SHARED_MODELS.md. No automatic training or database access for diagnostics.

Read the relevant source and documentation before editing. Keep changes focused on the requested outcome and match existing conventions. Preserve local data and unrelated edits. Update this file or the focused backend documents when commands, architecture, or behavior materially change. Do not record transient process IDs, running-session handles, or current passwords as project setup instructions.

## Quote author selection update
New quotes require authorId. Authenticated GET /api/quotes/authors searches all Humans regardless of owner, including the linked self and imported records; my_exes remains excluded. This exception applies only to quote author selection and creation. Existing authorless quotes and visibility rules remain supported. Other person pickers retain ownership checks.

## Interaction editing

Manual wedding import: root migrate_weddings.sql reads real_meeter.weddings/wedding_guest and explicitly writes only vibecoded_meeter, the destination confirmed by the user (MariaDB 12.3.3), through a schema-qualified conventional stored procedure. See backend/WEDDING_IMPORT.md. Keep this outside Flyway; never run automatically. Preserve original was_i_invited and poprawiny. Self is added only for original was_i_invited=1 (explicit source guests/spouses remain participants). The user confirmed info_after_hover is the wedding name: use it as the title, falling back to Wesele #ID only for NULL/blank. Preserve the original text and partner_id in the ledger, without assuming partner participation. fix_wedding_names.sql corrects only exact generated names on existing ledger-linked weddings, using the preserved snapshot. Retain the ledger for repeat safety and preserve existing IDs/metadata and later application edits. The source schema was provided by the user; do not connect to original/application databases without authorization.

Person profile interaction cards now reuse the calendar participant picker to add participants to creator-owned meetings, visits, events and trips. The profile interaction DTO preserves creator-only canAddParticipants; admin/profile read visibility does not grant write permission. Profile cards do not expose participant removal. Additions use the existing CSRF-protected endpoint and immediately update the displayed participants. No schema migration.

Self profile: GET/PUT /api/people/self and GET/POST /api/people/self/photo use only the authenticated linked Human ID, including imported or foreign-owned records. Self may edit basic personal fields and portrait without changing ownership, home or clique. Existing owner/admin photo writes remain available. All portrait replacements archive the old file under app.photos.former-directory (default backend/data/photos/former-people) with PhotoType.FORMER_HUMAN_PHOTO; preserve old metadata identity. V6 extends photos.photo_type. Copy before commit, remove active old file after commit, and remove new files on rollback. Back up database and both photo folders before adoption; never auto-run maintenance.

Interaction read visibility update: GET /api/interactions (including calendar filters, search, counts and pagination) and GET /api/interactions/{id} allow the creator OR an actual participant identified by the authenticated account's linked Human ID. No admin bypass. This supersedes older owner-only read statements. Removing self-participation revokes participant access; creators retain access. Editing and adding participants remain creator-only; participant removal rules remain unchanged. Event photo reads/reuse remain owner-only and foreign interaction DTOs omit photoUrl. No schema/data migration or backfill is required.

Calendar day details offer an Edit action for the interaction creator. Typed PUT endpoints use the creation contracts with empty details.participantIds; participant edits stay separate. Preserve interaction identity/metadata, retained participant and trip-place rows, and explicit participant removals. New references require ownership; unchanged imported references may remain. Event editing defaults to retaining its photo via photoEventId=self; no source detaches it without deleting shared files. Read backend/INTERACTIONS.md and backend/RELATIONSHIPS.md for details.

## Participant directory selection

Person autocomplete is context-ranked before LIMIT: `/api/people/participants?type=MEETING|VISIT|EVENT|TRIP|FLATMATE|WEDDING` counts distinct interactions of that type containing the current account-linked Human, regardless of creator and including for admins. `/api/people?type=...` uses the same ordering while retaining owned-only candidates (spouses/flatmate). Missing type retains the previous default. Quote authors are ordered by the number of quotes visible under the existing public/author/admin predicate. Ties use surname, first name, ID. Counts are not returned. `PersonSuggestions` owns these queries; do not reuse internal Social Score for autocomplete. Forms and calendar participant editing pass their interaction type.

Authenticated GET `/api/people/participants` searches all directory Humans, including imported and foreign-owned records, including normalized `my_exes` for historical records. It returns only ID, full name and participant portrait URL (/api/people/participants/{id}/photo), including ex portraits. Interaction creation and the dedicated add-participants endpoint accept those people; the linked self is still added automatically at creation. Links record the acting account, without changing person ownership. Interaction reads/writes and removal permissions stay unchanged. Other person assignments (spouses, flatmate, ordinary pickers) retain ownership requirements. Quote-author and participant pickers show circular portraits using authenticated directory/participant photo URLs, with initials for missing images.

## Clique ordering
Owned clique directory order uses the mean of squared PeopleRanking member points, descending, before pagination. Include zero-score members in the denominator, exclude the account-linked Human from numerator and denominator, and rank empty cliques at zero. Ties use clique ID ascending. Member order remains the people-directory order. Never expose the aggregate or formula in DTOs, logs or UI. See backend/CLIQUES.md.

## Explicit self-participation backfill
Admin GET/POST /api/admin/participation-backfill is the user-authorized exception for adding only the acting account-linked Human across all creators' meetings, visits, events, trips and weddings with was_i_invited=true. No flatmates. GET previews counts; POST requires CSRF, locks parents and preserves existing association metadata. New links record the actor and current timestamp. The entire write is atomic and repeatable; it may restore deliberately removed self-links only when explicitly invoked. Never run this tool automatically or infer invitation flags. Social ordering and visit counts now require an actual linked-self participant row rather than creator ownership. Existing ordinary interaction permissions remain unchanged. See backend/PARTICIPATION_BACKFILL.md.

## Quote game visit selection
User-authorized exception: authenticated GET /api/quote-games/visits lists visits created by the account or containing its linked Human as an actual participant. The game creation endpoint uses the same predicate, without an admin bypass. Imported and foreign-owned attended visits are eligible; ordinary interaction APIs stay owner-only. The selector loads a paginated list automatically, ordered by start date and ID descending, with an optional name filter.

## KTO ETA SKAZAŁ
User-requested separate game, alongside the original visit/clique game. POST /api/quote-games/single accepts {secondsPerQuestion}, draws one uniformly random author with public quotes and one random public quote by that author, and persists exactly one round. All Human IDs are answer candidates (including imported/foreign-owned people); only IDs and names are exposed. No author identity is revealed before answering. Existing timer, public-retraction, ownership, CSRF, self-quote and guess rules apply. scopeType=SINGLE uses existing columns; no schema migration. GET /api/quote-games/single/history and /api/quote-games/single/leaderboard serve only SINGLE games. The original history and leaderboard exclude SINGLE games. The single=true query parameter remains supported for API compatibility. The UI offers separate menu entries and resets local state when switching games.


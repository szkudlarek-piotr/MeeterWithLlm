# Meeter news

UUID update: the browser now calls `/api/v2/news`, where `id` is the persistent news UUID. V8 adds and backfills the unique, non-null `app_news.uuid` column; the legacy numeric `/api/news` contract remains available. Both endpoints require authentication.

The release note requested by the user for 8 November 2026 is provided as the separate manual file `../import_news_2026_11_08.sql`, which targets the confirmed `vibecoded_meeter` database. Apply V8 and make a backup first, then import the whole file. It inserts a UUID-backed entry with a stable slug and keeps an existing entry on a re-run. It is not a Flyway migration and does not run automatically. Its INSERT and repeat behaviour are tested against isolated H2 fixtures (only the test destination schema is substituted); it has not been run against application data.

The News view ("News") shows product changes loaded from the database, not a history of private interactions. `src/NewsPanel.jsx` uses `src/api.js` and `GET /api/news?page=0&size=10`. It handles loading, errors with retry, an empty list and pages. Interactions remain available in the calendar and in the existing API.

`app_news` contains `id`, a unique `slug` (a fixed key for the entry, not visible in the UI), `title` (160), `content` (10240) and a local `published_at`. Entries are shared by all signed-in accounts. Reading returns the news to every signed-in user. It returns the DTO `{content: [{id, title, content, publishedAt}], page, size, totalElements, hasNext}`. Parameters: page 0–1000000, size 1–100. Sorting: publication date and ID, descending. Future publications are skipped, according to the Europe/Warsaw time. The content is plain text rendered by React, with line breaks preserved.

The Flyway migration `V3__product_news.sql` creates the table and adds the first entry only when its slug does not exist yet. It uses the currently connected database, without a hard-coded schema name. To move this news item, it is enough to copy the code and run the migrations as described in `MIGRATIONS.md`; you do not need to copy the data of the whole database. Further entries can be published from the form in Administration. The application does not generate messages at every start.

An administrator publishes immediately through `POST /api/v2/news` with a title and content (fields `title` and `content`). Saving requires CSRF and returns HTTP 204.

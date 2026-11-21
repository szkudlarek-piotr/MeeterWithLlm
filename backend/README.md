# Current configuration

This copy uses MariaDB and Flyway; the H2 instructions below are historical. Schema changes and product news are delivered as versioned migrations; see [MIGRATIONS.md](MIGRATIONS.md) and [NEWS.md](NEWS.md). News shows feature changes, and interactions are in the calendar. The standalone Quotes collection has been removed; the form, the people cards and the game remain.

# Meeter backend

## Database in this copy and test accounts

This copy uses the MariaDB/MySQL database given by `spring.datasource.*` in `application.properties`, with the MariaDB driver in `pom.xml`. The older H2 descriptions below refer to the previous configuration; tests should still use a separate in-memory database. Do not write database access details into the documentation.

The automatic creation of sample people, places, cliques, interactions and test accounts has been removed. Records saved earlier remain in the database; a code change does not delete them.

Quotes, their API and the form: [QUOTES.md](QUOTES.md).

The quote author game, the timer and the ranking: [QUOTE_GAME.md](QUOTE_GAME.md).

Relationship tables with their own ID, date and author, and the migration of old data: [RELATIONSHIPS.md](RELATIONSHIPS.md).

The models and API of the six interaction types, and the reasons for the changes: [INTERACTIONS.md](INTERACTIONS.md).

Importing profile pictures from a link and a description of the sample data: [PHOTOS.md](PHOTOS.md).

Spring Boot 4.1.1, Java 21, Spring Data JPA and a separate H2 database. The project contains no connection to the original Meeter database and none of its data.

## Running

In the `backend` folder:

```powershell
.\mvnw.cmd spring-boot:run
```

The Maven Wrapper downloads Maven and the dependencies on the first run. If Java does not trust the certificate of the local network, use the Windows trusted certificates (without disabling TLS verification):

```powershell
$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
.\mvnw.cmd spring-boot:run
```

The API runs at `http://127.0.0.1:8091`. The Vite frontend forwards `/api` to that address; run `npm run dev` in the project root.

## Data

The development database is created automatically in `backend/data/meeter-dev.mv.db`, provided the application is started from the `backend` folder. Data survives a restart. The folder is ignored by Git. You do not need to install a database server or enter a password for the real Meeter. The local initialiser only adds the start-up account and the person described below.

The tests use only an in-memory H2 database. A production MariaDB/PostgreSQL target and schema migrations can be added later. `ddl-auto=update` is a development convenience, not a production migration system.

## API

### Browsing people

The **People** view (Ludzie) skips the person linked to the signed-in account (matched by Human ID, also for administrators) before searching, counting and paginating. Other people with the same first and last name remain visible. It shows the other people from the `people` table, including people without an owner and people assigned to other accounts, with their portraits, the number of visits and one random public quote. It sorts by points in descending order, then alphabetically by surname and first name, then by ID. People without interactions (0 points) are included. The visit counter covers visits with an own participant link, including planned ones, regardless of their length. Points come from interactions in which the person linked to the account takes part, regardless of the creator; point dates in the future do not yet award points.

The scoring in `PeopleRanking` does not need SQL functions from the old database: for each interaction it computes `ROUND(POWER(0.5, number_of_days / half_life), 3) * weight`, then sums them. "Today" is the local date in Europe/Warsaw. Visit: 6 points / 720 days, start date; meeting: 3 / 360, start date; event: 1 / 180, personal departure or end of the event; trip: 3 / 3650, end date. Wedding: only the couple, and only when `was_i_invited=true`; 3 points for a person in a clique named WAGS (ignoring letter case and surrounding spaces), 15 points for everyone else, with a half-life of 1825 days in both cases (5 × 365). A person without a clique counts as "everyone else". Ordinary wedding guests and flatmate periods do not earn points from this. The ranking is computed before pagination. The scoring metadata is not stored in the database and is recalculated on each read.

`GET /api/people/browse?q=&page=0&size=20` returns `{content: [{person, visitCount, publicQuote}], page, size, totalElements, hasNext}`. `person` has the shape of the existing `HumanView`. The name search treats `%` and `_` literally, `page` is non-negative, and `size` is 1–100. Sorting covers the whole set before pagination. The list and `GET /api/people/browse/{id}/photo` require authentication, but not ownership of the person. The existing `GET /api/people?q=&limit=50` is unchanged for person pickers; writing and changing a photo still require ownership.

| Path | GET | POST |
| --- | --- | --- |
| `/api/places` | List / search places | `name`, `latitude`, `longitude`, `category` |
| `/api/cliques` | List / search cliques | `cliqueName`, `cliqueCapitalId` |
| `/api/people` | List / search people | `name`, `surname`, `birthdayDate`, `fbLink`, `gender`, `livesIn`, `homeId`, `cliqueId` |

GET accepts `q` (part of a name, case-insensitive) and `limit` (1–100, default 50). POST returns 201 and the saved object. Invalid data returns 400, and a non-existent or foreign reference returns 404. Leave optional references out or pass `null`. Dates use the format `YYYY-MM-DD`. All data is filtered by the signed-in owner.

Example of a clique after creating a place with ID 1:

```json
{ "cliqueName": "Friends", "cliqueCapitalId": 1 }
```

IDs and UUIDs are generated by the backend. Entities are not serialised directly: the DTOs contain no cyclic collections. Clique membership is represented by `Human.clique`; a second copy of the member list is not stored. Fields for entities not yet implemented (photos, songs, interactions, superpowers) will be added together with their models. The scoring of interactions should eventually belong to a service, not to the person entity.

## Sign-in

On the first start of the local H2 database, the person **Piotr Szkudlarek, ID 77** and the linked account **pszkudlarek** are created. Further person IDs start above the existing maximum. The initialiser does not overwrite another person with ID 77 and does not change the password of an existing account. Existing records without an owner in this new development database are assigned to that account once.

The random start-up password exists only in the local `backend/data/initial-login.txt` (ignored by Git). After signing in, change the password in the interface and delete the file. No real data or passwords from the old Meeter are imported. The initialisation can be disabled by setting `app.dev.bootstrap=false`.

| Endpoint | Action |
| --- | --- |
| `GET /api/auth/csrf` | Gets the CSRF token and the header name; public |
| `POST /api/auth/login` | JSON `username`, `password`; public, requires CSRF |
| `GET /api/auth/me` | The current account and the linked person |
| `POST /api/auth/logout` | Invalidates the current token and removes the cookie |
| `POST /api/auth/password` | `currentPassword`, `newPassword`; invalidates all sessions of the account |

The authentication token is 32 random bytes, sent in an `HttpOnly`, `SameSite=Lax` cookie with path `/api`, valid for 1 day (24 hours from sign-in); the cookie and the token in the database have the same expiry. The new expiry applies to tokens issued after deployment; existing tokens keep their stored expiry date. The database stores only the SHA-256 hash of the token. Passwords are stored as BCrypt with cost 12. A new password must be at least 12 characters and at most 72 bytes of UTF-8. The identity of the data owner comes from authentication, never from the client's JSON.

All operations that change data require a CSRF header matching the cookie. The frontend fetches it automatically. The authentication token never reaches localStorage or a JSON response. Five failed sign-in attempts block that login for 15 minutes; in addition, there is a limit of 100 attempts from one address within that window. The limiter lives in memory of a single process, so it resets on restart; with several instances it must be moved to shared storage.

The backend is bound to loopback. For an HTTPS deployment, set `app.auth.secure-cookie=true`, disable the local bootstrap, and plan schema migrations, account management and password recovery. There is currently no public registration and no API for granting roles.

## Tests

```powershell
.\mvnw.cmd test
```

The HTTP tests check saving linked entities, searching, validating coordinates, names, dates and addresses, invalid IDs, result limits, sign-in, CSRF, owner isolation, expired tokens, sign-out, password change and attempt limiting.

The People tab uses `directory=true`, which limits the search results, the counts and the pages to people with a Social Score above 2. The score is used internally for filtering and sorting. The API and the UI omit the point values. A public quote has the form `{id, quote}` or `null`, and is drawn from the current author's public quotes.

People from the `my_exes` clique (name matched regardless of letter case and surrounding spaces) are skipped before counting, searching and pagination, including for administrators. The directory portrait endpoint returns 404 for them. Other interfaces keep their earlier rules. With equal scores, the order is set by surname, first name and ID.

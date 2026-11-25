# Quotes

The `org.example.meeter.quote` module contains `Quote`, `QuoteRepository`, `QuoteService`, `QuoteController` and the `QuoteModels` contracts. The author of a new quote is a required `Human` person; the owner is the signed-in `User` account, assigned by the server. Quotes are not interactions and do not appear in the calendar.

## API

Suggestions from `GET /api/quotes/authors` are sorted in descending order by the number of quotes visible to the current account (public quotes, private quotes of the author matched by Human ID, and all quotes for an administrator). Private quotes by others do not affect the order for an ordinary account; quotes without an author do not raise any result. Ties are broken by surname, first name and ID. Searching, excluding `my_exes` and sorting all happen before LIMIT. People without quotes can still be selected. Counts are not returned in the suggestions.

- `POST /api/quotes`: creates a quote; response 201.
- `PATCH /api/quotes/{id}`: changes the quote text (`{quote}`, 4–10240 characters after trimming). Only the quote's author (the account-linked person), the account that created the quote, or an administrator may do it; anyone else gets 404. Requires CSRF. Author and visibility are unchanged (visibility has its own endpoint). Author-page items carry `canEdit`, which the person dialog uses to show "Edit quote".
- `GET /api/quotes/{id}`: an available quote, or 404 for a non-existent or inaccessible ID.

The separate collection endpoint `GET /api/quotes` has been removed. Quotes are presented on people's cards and in the game.

Example of creation:

```json
{
  "quote": "Words worth remembering.",
  "authorId": 77,
  "isPublic": false
}
```

After trimming the outer whitespace, the content must be 4–10240 characters long. The minimum of 4 from the annotation on the entity is kept, and a contradictory message was corrected. `authorId` is required (missing or null: 400). The author can come from any account or import; a non-existent person or `my_exes` returns 404. `GET /api/quotes/authors?q=...&limit=100` searches authors regardless of owner, including the person linked to the account. It returns only the ID, the name and an empty photo URL. `isPublic` can be omitted and defaults to false.

The quote response contains `id`, `uuid`, `quote`, an optional `author` summary (`id`, `fullName`, `profilePhotoUrl`), `isPublic` and the server-side `addTime`. Account data, passwords and tokens are never serialised.

All endpoints require a session. POST also requires CSRF. A public quote (`isPublic=true`) is visible to every signed-in user. A private quote is visible to the account linked to the author's person and to `ROLE_ADMIN`. An administrator can read any quote through the single-record endpoint. A private quote without an author is visible to the saving account and to the administrator. Saving someone else's private quote does not by itself give the saver the right to read it later. Author data is limited to a summary; the profile picture URL is omitted for a person who does not belong to the viewing account.

The roles `ROLE_USER` and `ROLE_ADMIN` are stored in `user_roles`. Roles are included in the sign-in DTO and in `/api/auth/me`, and the token authentication filter uses them as authorities. `RoleBootstrap` fills in `ROLE_USER` for existing accounts; with local `app.dev.bootstrap=true` it also gives the `pszkudlarek` account `ROLE_ADMIN`. Outside development, the local bootstrap must be disabled. There is no public endpoint for granting roles.

`PUT /api/quotes/{id}/vote` accepts `{vote: PLUS | MINUS}`, requires a session and CSRF, and accepts votes only for quotes visible to that account. An account can have one active vote. Repeating the same rating returns 204, and trying to cast the opposite vote before withdrawing returns 409. `DELETE /api/quotes/{id}/vote` withdraws your own vote and returns 204, including when repeated. The same session, CSRF and visibility rules apply. Migration V5 creates the vote table with a unique quote–account pair.

## Interface and data

The "Only public quotes" switch next to the theme switch in the top bar saves the preference in the `meeter-public-quotes` cookie. When it is on, the backend filters the author's list before pagination, and returns 404 when reading, rating or withdrawing a rating for a private quote, including for the administrator and the author. Turning it off restores the visibility given by the account's permissions.

The person dialog has a Quotes tab: `GET /api/people/browse/{id}/quotes?page=0&size=20` (size 1–100). It returns the standard page fields and the author's quotes, sorted before pagination in descending order by score (plus votes minus minus votes), then by `addTime` and ID. Each entry contains `score` and `myVote` (1, -1 or null). It applies the same visibility filter as the single quote: signed-in users see public quotes, the account linked to the author sees their private quotes, and the administrator also sees private quotes. The count and pagination cover only visible quotes. The global `/api/quotes` collection endpoint is not restored. The renderer stays `QuoteContent`.

`src/QuoteContent.jsx` is the shared quote renderer for the people list and the game (the question and the answer). Quotes are centred. The HTML is parsed in an inert template element and rebuilt as React elements from a restricted list of formatting tags: bold, italic, underline, strikethrough, paragraphs, line breaks, lists, subscript, superscript and code. All attributes are rejected, including styles and event handlers; scripts, iframes, SVG and similar active content are skipped. Images and links are not embedded; the link text stays. Never insert raw HTML with `dangerouslySetInnerHTML`. Plain text and line breaks are supported. The source quote data in the database is not changed.

The People tab attaches at most one random **public** quote of each person as `publicQuote: {id, quote}` (or null) in `GET /api/people/browse`. The draw happens on every read of the page. The query limits the pool to the authors on the current page with `is_public=true` before drawing; there is no exception for administrators or for one's own private quotes. Quotes without an author are skipped. A quote does not affect the ranking, the counts or the pagination of people. The view sends no separate request for each person. Long quotes have a scrollable field that preserves line breaks.

The menu keeps the "Add quote" action, but there is no separate collection view. The form contains the text, a character counter, a search for the required author, the "Public quote" option, and the saving and error states. After saving, it opens the people list and refreshes its public quotes. Quote data stays in the database and is used by the people cards and the game.

The `quotes` table is created by the current development Hibernate configuration. The text column has a length of 10240, not the default 255. The author and the owner are not deleted in cascade together with the quote. When removal of people or accounts is added, take the foreign keys from quotes into account.

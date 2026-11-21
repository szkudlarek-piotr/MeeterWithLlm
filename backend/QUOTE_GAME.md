# "Guess the author" game

Each signed-in user starts their own game. They choose one visit created by them, or one in which they take part as the person linked to their account, or a set of their own cliques (up to 10). A visit gives the pool of authors from all its participants, including people added automatically; cliques give the union of their members. Cliques remain a private way of organising contacts. Their model and access rules have not changed.

## Drawing

For each person in the chosen scope, `QuoteGamePool` takes **public quotes only** that have an author. It draws up to two different quotes per person, then shuffles the combined list. An author with one quote contributes one, and an author with no public quotes contributes zero. There is no extra limit on the total number of questions. The administrator role does not let private quotes into the game. Quotes without an author are not drawn.

The result of the draw, the order, the participants, and the copies of the text and authors are stored in the database. Reading, refreshing the page and resuming never draw again. An empty pool returns 400 with a message that public quotes must be added first. If a quote stops being public, reading the game and further answers are blocked, so that its stored text is not revealed.

## Time and answers

The limit is 30 seconds per question by default, configurable from 10 to 120 seconds. The first quote starts when the game is created. The next question is started by a separate `next` POST, after the result of the previous answer has been read. The server saves `started_at` and `deadline_at`; repeating `next` for an active question does not reset the timer. The DTO includes the server clock so that the countdown can be synchronised.

Exceeding the deadline gives an incorrect answer, even if the client sends the correct author after the time has run out. An answer missing after the deadline is saved as a timeout with an empty type. Resuming an expired question saves a timeout; it does not grant new time. Leaving the screen of an active question does not stop the clock.

`quote_guesses` stores the ID, the round, the guessed person (optional for a timeout), correctness, the timeout flag, whether the quote is the player's own, the answer time, the save date and the player's account. Each round has at most one answer. The server checks the current round, that the chosen type is among the candidates, and blocks overwriting an answer. A pessimistic lock on the game and the uniqueness of the round in the table protect against concurrent writes.

The question does not contain the quote ID or the author. The correct author is revealed only in the result of the saved answer. The general quote API remains available under its own rules; the game is a social pastime, not an exam system that is safe against viewing the whole public collection.

## Ranking

The score is `100 × correctly guessed other people's quotes / all other people's quotes in the game`. "Other people's" here means **authored by a person linked to a player's account**, not an account that entered the quote. One's own quotes do not increase the numerator or the denominator; timeouts and wrong types for other people's quotes lower the score.

The table shows the best completed game of each account, for up to 100 players. A higher percentage wins; on a tie, the lower total answer time for other people's quotes wins. Further ties are settled by the completion date and the game ID. Games that contain only one's own quotes have a null percentage and are not ranked. Games in progress do not appear in the ranking.

Signed-in users see the player's name, the percentage, the number of hits, the number of ranked quotes and the time. The ranking does not reveal quote text, private visit or clique names, or other players' guesses. History and game details are visible only to their owner; the administrator gets no automatic access to other people's answers.

## API

| Method and path | Action |
| --- | --- |
| `POST /api/quote-games` | `{visitId, cliqueIds, secondsPerQuestion}`; exactly one kind of scope; response 201 |
| `GET /api/quote-games?page=0` | Own history, 20 games per page |
| `GET /api/quote-games/{id}` | Resume with the current time and own results; saves a timeout if the deadline has passed |
| `POST /api/quote-games/{id}/next` | Starts the next question; body `{}` |
| `POST /api/quote-games/{id}/guesses` | `{roundId, authorId}`; `authorId=null` only after the deadline has passed |
| `GET /api/quote-games/leaderboard` | Ranking of completed games |

All endpoints require authentication; POST operations also require CSRF. Creating a game from someone else's visit or clique, and reading someone else's game, return 404. Saving an answer for a round that is not the current one, or for a game that has already ended, returns 409. Identity, correctness, time, scoring and order all come from the server.

## Data model and tests

Module: `org.example.meeter.quote.game`. Entities: `QuoteGame`, `GameRound`, `QuoteGuess`, `GameCandidate`, `GameClique`. The scope and candidate links have their own IDs, `created_at` and `created_by_id` through the existing `RelationshipRecord`. Deleting a game removes its rounds, candidates and links, but not people, cliques or quotes. Tests must delete games before the quotes and their authors.

Interface: `src/QuoteGamePanel.jsx`, the "Guess the author" menu entry, scope selection, the countdown, answering, the result after an answer, resuming from history, and the ranking.

When developing with many accounts, do not match people by surname alone. Today's `Human` is a record in the owner's own address book; separate entries describing the same real person need an explicit shared-identity model. The game compares the author entity IDs with the person linked to the account; it does not automatically merge address books or cliques.

## Visit selection

`GET /api/quote-games/visits?q=&page=0&size=10` returns a ready-made list of visits (id, name, startDate, participantCount), from the latest dates, with descending IDs for ties. The name filter is optional. The list and game creation use the same rule: the creator or an actual participant identified by the linked Human ID. The administrator does not bypass this rule. The ordinary interactions API stays limited to the owner. Creating a game from someone else's visit in which the player does not take part returns 404.

## Single-quote game ("Who said it")

A separate game requested by the user, alongside the original visit and clique game. `POST /api/quote-games/single` accepts `{secondsPerQuestion}`, draws one author uniformly at random among those with public quotes, then one random public quote by that author, and saves exactly one round. All Human IDs are answer candidates, including imported and foreign-owned people; only IDs and names are exposed. No author identity is revealed before the answer. The existing timer, public-retraction, ownership, CSRF, self-quote and guess rules apply. `scopeType=SINGLE` uses the existing columns, so no schema migration is needed. `GET /api/quote-games/single/history` and `/api/quote-games/single/leaderboard` return only SINGLE games. The original history and leaderboard exclude SINGLE games. The `single=true` query parameter remains supported for API compatibility. The interface shows separate menu entries and resets the local state when switching between games.

# Family tree

Family relations live in the SQL table `family_links` (Flyway V21), not in Neo4j: a family is a small graph that a parent-to-child table and in-memory traversal handle well, and genealogy programs store trees in SQL for the same reason. Neo4j stays an optional read-only export (`GraphSync`).

## Model

Each row has a `link_type`, two people and the creating account.

- `PARENT`: `person_a_id` is the parent of `person_b_id`.
- `SPOUSE`: an unordered pair, stored with the smaller ID in `person_a_id`.

Siblings, grandparents, cousins and so on are derived from parent links, never stored. Links are private to the account that recorded them: every read and delete is scoped to `created_by_id`, and another account's link ID returns 404. Both people must be visible through the people browse API (so the `my_exes` clique stays hidden). Ownership of the people is not required.

## API (session and CSRF required)

- `GET /api/people/browse/{id}/family`: `{focusId, people: [{person, generation}], links: [{id, type, personAId, personBId}]}`. `generation` is relative to the person: negative for ancestors (up to 4), positive for descendants (up to 4), 0 for the person, siblings and spouses. Spouses of every included person are added at their generation.
- `POST /api/family/links` with `{type, personAId, personBId}`: 201; 400 for a self link, a cycle, or a third parent (at most two parents per child); 409 for a duplicate.
- `DELETE /api/family/links/{id}`: 204, or 404 for a link the account did not create.

## UI

The **Family** tab of the person dialog shows the tree by generation and the list of relations with a remove button, and a form to add a parent, a child or a spouse of that person.

Back up the database and stop the backend before applying V21.

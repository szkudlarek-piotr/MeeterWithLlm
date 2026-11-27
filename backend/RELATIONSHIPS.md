# Audited relationship tables

Explicit maintenance exception: [PARTICIPATION_BACKFILL.md](PARTICIPATION_BACKFILL.md) documents the administrator tool that adds only the acting account's linked Human to all meetings, visits, events, trips and invited weddings, including imported and foreign records. Ordinary participant mutation permissions remain unchanged. Ranking now uses participation rather than the interaction's creator.

The conceptual many-to-many relationships use explicit JPA association entities:

| Table | Entity | Related records |
| --- | --- | --- |
| `interaction_human` | `InteractionParticipant` | `interaction_id`, `human_id` |
| `trip_place` | `TripPlace` | `trip_id`, `place_id` |

Both extend `RelationshipRecord`, with these additional columns:

- `id`: an auto-incrementing `BIGINT` identity primary key, independent for each table.
- `created_at`: a non-null `Instant`, recorded by the server on creation and immutable through JPA updates.
- `created_by_id`: a non-null foreign key to `app_users.id`, immutable through JPA updates. The account links to the person via `app_users.human_id`.

Each pair of endpoints remains unique. Parent collections use `OneToMany` with cascading persistence and orphan removal; association endpoints use `ManyToOne` without cascading deletion. Deleting an interaction deletes its association rows, not the related people, places or accounts.

The creation APIs take the owner from the authenticated account. Assigning the new interaction's owner records that account as the creator of its new association rows, including automatic participants and seeded records. Clients cannot supply relationship IDs, timestamps or creators. Participant edits record the account performing the edit as the creator of each new row, rather than copying the identity of the original interaction owner. Do not regenerate existing rows during unrelated edits or reads, because that would replace their IDs and creation metadata.

## Participant editing

`POST /api/interactions/{id}/participants` accepts `{ "participantIds": [123, 124] }` (1–200 distinct positive IDs per request). Only the interaction's creator may add people, and every selected person must belong to that account. Existing participants are skipped without changing their relationship IDs, timestamps or creators. New links record the authenticated editor. Invalid or foreign people fail the entire transaction.

`DELETE /api/interactions/{id}/participants/{humanId}` returns 204. A participant may remove their own link using the exact Human ID linked to their account; the account recorded in that link's `created_by_id` may also remove it. Owning the interaction or holding ROLE_ADMIN alone does not authorise removing a link created by somebody else. Missing and unauthorised links return the same 404. Removal deletes only the association, never the person, account or interaction. Automatic participation applies at creation; an explicitly removed participant is not recreated on reads or on later additions. Wedding spouse references and flatmate references keep their separate domain meaning.

Both operations require authentication and CSRF, and take a write lock on the parent interaction, which serialises participant edits. The DTOs expose `canAddParticipants` and `removableParticipantIds` for the current account without revealing creator accounts. The existing owner-only interaction read and list APIs and the person and photo APIs remain unchanged: permission to delete a link does not grant access to a foreign interaction's details.

`getParticipants()` and `getPlaces()` expose read-only endpoint sets for the existing DTO code. The association collections keep the full metadata internally. Participant summaries keep their existing shape; the interaction DTO adds the permission fields described above. JPA entities and account details are not serialised.

## Editing interaction details

The typed `PUT /api/{meetings,visits,events,trips,flatmates,weddings}/{id}` endpoints lock the interaction and require its creator (there is no administrator bypass). They require an empty `details.participantIds`; participant changes keep their dedicated authorisation rules above. Edits preserve the identity and audit metadata of existing participant links and do not restore explicitly removed automatic participants. Newly assigned wedding spouses or a flatmate are added with the editor as creator; former domain references are not automatically removed from the participants. Trip-place updates keep the retained association rows and their metadata, remove only the omitted links, and create new links with server-managed editor metadata. Unchanged imported person and place references can stay; newly selected references require ownership.

## Compatibility with the existing H2 database

`RelationshipSchemaMigration` runs before `entityManagerFactory` starts. Fresh databases are created directly from the entity mappings. For existing tables it adds identity IDs and metadata, replaces the old composite primary key with the ID primary key, and keeps the unique endpoint pairs and the creator foreign keys. The upgrade can be run again without changing existing IDs or populated metadata. It does not delete relationship rows.

Old join tables did not record authors or creation times. Their metadata is **inferred** from `interactions.created_at` and `interactions.created_by_id`, including, for a trip, the parent interaction. These values are a compatibility backfill, not an exact historical audit trail. If a legacy row has no parent, timestamp or owner, startup fails with an explanation rather than inventing an account or dropping the row.

Before upgrading a file-backed development database, stop the backend and save a copy of `data/meeter-dev.mv.db`. H2 schema changes commit independently, so the whole upgrade is not one atomic transaction; the individual steps are repeatable. Test against a copy when changing the migration behaviour. This migration supports H2 only; a future database engine requires an explicit migration strategy. `ddl-auto=update` remains a development convenience rather than a general production migration system.

Verification covers preservation of old rows, repeatability, generated IDs for later inserts, pair uniqueness, creator foreign keys, metadata created through the API, and deletion of association rows while keeping the endpoint records.

## Selecting participants from the directory

At the user's request, the participant selector uses `GET /api/people/participants?q=...&limit=100`. The results include imported people, people without an owner and people belonging to other accounts; `my_exes` is also available. The DTO contains only `id`, `fullName` and `profilePhotoUrl` pointing to `/api/people/participants/{id}/photo`. Creating an interaction and the POST that adds participants accept these people, keeping the link's creator and the person's ownership unchanged. The rules for reading interactions, removing links, and choosing a couple or a flatmate do not change. The selectors show round photos in the results and next to the selected people; a missing photo shows initials.

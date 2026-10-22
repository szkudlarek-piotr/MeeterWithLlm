# Audited relationship tables

The conceptual many-to-many relationships use explicit JPA association entities:

| Table | Entity | Related records |
| --- | --- | --- |
| `interaction_human` | `InteractionParticipant` | `interaction_id`, `human_id` |
| `trip_place` | `TripPlace` | `trip_id`, `place_id` |

Both extend `RelationshipRecord`, with these additional columns:

- `id`: auto-incrementing `BIGINT` identity primary key, independent for each table.
- `created_at`: non-null `Instant`, recorded on creation by the server and immutable through JPA updates.
- `created_by_id`: non-null foreign key to `app_users.id`, immutable through JPA updates. The account links to the person via `app_users.human_id`.

Each endpoint pair remains unique. Parent collections use `OneToMany` with cascading persistence and orphan removal; association endpoints use `ManyToOne` without cascading deletion. Deleting an interaction deletes its association rows, not the related people, places, or accounts.

The creation APIs obtain the owner from the authenticated account. Assigning the new interaction's owner records that account as creator on its new association rows, including automatic participants and seeded records. Clients cannot supply relationship IDs, timestamps, or creators. Participant edits record the account performing the edit as creator of each new row, rather than copying the original interaction owner's identity. Do not regenerate existing rows during unrelated edits or reads, because that would replace their IDs and creation metadata.

## Participant editing

`POST /api/interactions/{id}/participants` accepts `{ "participantIds": [123, 124] }` (1–200 distinct positive IDs per request). Only the interaction creator may add people, and every selected person must belong to that account. Existing participants are skipped without changing their relationship IDs, timestamps or creators. New links record the authenticated editor. Invalid or foreign people fail the entire transaction.

`DELETE /api/interactions/{id}/participants/{humanId}` returns 204. A participant may remove their own link using the exact account-linked Human ID; the account recorded in that link's `created_by_id` may also remove it. Owning the interaction or holding ROLE_ADMIN alone does not authorize removal of a link created by somebody else. Missing and unauthorized links return the same 404. Removal deletes only the association, never the person, account or interaction. Automatic participation applies at creation; an explicitly removed participant is not recreated on reads or subsequent additions. Wedding spouse references and flatmate references retain their separate domain meaning.

Both operations require authentication and CSRF and acquire a write lock on the parent interaction, serializing participant edits. DTOs expose `canAddParticipants` and `removableParticipantIds` for the current account without revealing creator accounts. The existing owner-only interaction read/list and person/photo APIs remain unchanged: permission to delete a link does not grant access to a foreign interaction's details.

`getParticipants()` and `getPlaces()` expose read-only endpoint sets for existing DTO code. The association collections retain the full metadata internally. Participant summaries retain their existing shape; the interaction DTO adds permission fields described above. JPA entities and account details are not serialized.

## Existing H2 databases

`RelationshipSchemaMigration` runs before `entityManagerFactory` starts. Fresh databases are created directly from the entity mappings. For existing tables it adds identity IDs and metadata, replaces the old composite primary key with the ID primary key, and retains unique endpoint pairs and creator foreign keys. The upgrade can be run again without changing existing IDs or populated metadata. It does not delete relationship rows.

Old join tables did not record authors or creation times. Their metadata is **inferred** from `interactions.created_at` and `interactions.created_by_id`, including the parent interaction of a trip. These values are a compatibility backfill, not an exact historical audit trail. If a legacy row has no parent, timestamp, or owner, startup fails with an explanation rather than inventing an account or dropping the row.

Before upgrading a file-backed development database, stop the backend and save a copy of `data/meeter-dev.mv.db`. H2 schema changes commit independently, so the entire upgrade is not one atomic transaction; the individual steps are repeatable. Test against a copy when changing migration behavior. This migration supports H2 only; a future database engine requires an explicit migration strategy. `ddl-auto=update` remains a development convenience rather than a general production migration system.

Verification covers old-row preservation, repeatability, generated IDs for subsequent inserts, pair uniqueness, creator foreign keys, API-created metadata, and deletion of association rows while keeping endpoint records.

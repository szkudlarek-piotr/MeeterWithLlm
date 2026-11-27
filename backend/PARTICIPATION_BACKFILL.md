# Participation in interactions and filling in missing links

## Ranking by participation

The people directory, the member order of cliques and the clique ranking use interactions in which the person linked to the current account has a record in `interaction_human`. The interaction's creator can be anyone or empty. Authorship alone without participation is not enough; removing your own link removes that interaction's contribution from the ranking. Identity is checked by Human ID, never by the person's name. An `EXISTS` check prevents contributions from being multiplied. The shared-visit counter and the contextual person suggestions use the same participation scope.

The weights, time decay, rounding and skipping of future dates are unchanged. Weddings still award points only to the couple when `was_i_invited=true`, and now also require the viewer's own participant link. Guests receive no wedding points. For an event created by the viewer, the point date is its `me_leaving_date` or the event end. For someone else's event the event end is used, because the personal arrival and departure fields belong to the creator and have no equivalent for each participant. Flatmate periods still do not affect the Social Score.

The read and write scope of the ordinary APIs is not widened. In particular, participation does not grant ordinary GET or PUT access to interactions or event photos; the existing exceptions of the person profile work as before. Ranking values stay internal and are not logged or returned by the API.

## Explicit administrative tool

In **Administration → Complete my participation in interactions**, the administrator first selects "Check missing links". The authenticated `GET /api/admin/participation-backfill` shows only the counts of missing entries for meetings, visits, events and trips, and for weddings with the invitation flag. This read writes nothing.

The "Add me to … interactions" button calls `POST /api/admin/participation-backfill` with a session and CSRF. It accepts no Human ID or creator from the client. Only the person linked to the account performing the operation is added, including to imported interactions, interactions without an owner and interactions saved by other accounts. This is a narrow, explicitly requested administrative exception; the ordinary add-participants endpoint keeps its earlier permissions.

The scope covers all dates of meetings, visits, events and trips in which the own link is missing. At the user's explicit request, weddings are selected by `was_i_invited=true`; this is the rule of this tool, not a general equation of invitation with presence. It does not change the invitation flags or any other wedding data. Flatmate periods are skipped.

The operation can restore your own participation that was explicitly removed earlier; the UI informs you of this before saving. It is not run by application startup, the ranking read, Flyway or Neo4j synchronisation. It was not run automatically on the user's data during implementation. Before bulk data maintenance, make a database backup as described in AGENTS.md. There is no schema change.

Parents are locked in ID order; the own link is checked again after the lock. A new record receives a generated ID, the current time and the `created_by_id` of the acting account. Existing records keep their ID, creator and time, and the people and owners of interactions do not change. The whole write is one transaction: an error rolls back all new links. A repeat skips those that already exist. The result contains `added` by type and `skipped` for records changed or deleted in parallel. Access requires ROLE_ADMIN on the backend too.

After the data is filled in, the existing Neo4j synchronisation will carry the new links of meetings, visits, events and trips during its next run. Weddings remain outside the current graph synchronisation. The tool itself does not write to Neo4j.

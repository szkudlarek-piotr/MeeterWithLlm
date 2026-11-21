# Meeter interactions

Source of the models: [Meeter_hibernate/interactions](https://github.com/szkudlarek-piotr/Meeter_hibernate/tree/master/src/main/java/org/example/meeter/interactions), read on 18 October 2026.

`Event` means an external event, for example a concert, a festival or a dance class. The people saved on it are people met at that event, not a complete list of its attendees. A visit, a meeting, a trip, a flatmate period and a wedding are **not subtypes of Event**.

All six models inherit from the common class `Interaction`, which stores the ID, UUID, creation time, name, description, date range, participants, optional place and owner. JPA uses `JOINED` inheritance: a shared table `interactions` plus a separate table for each of the six types. Each type keeps its own fields.

## Differences from the source

- A UUID is always generated; in the original some classes left it empty.
- All participants are stored in an initialised set with a unique interaction–person pair. The signed-in person is added automatically, so the form does not need to ask for them.
- The name is 2–128 characters after trimming whitespace. The description is optional, up to 10240 characters. The arbitrary minimums of 16 or 32 description characters were removed.
- A visit length is an integer from 1 to 3650, validated with `@Min`/`@Max` rather than `@Size`. The end is computed as start plus the number of days minus 1, as in the original semantics.
- The end cannot precede the start. Arrival and departure at an event must fall within its bounds and be in the correct order. Dates accept years 1–9999; future dates are allowed for planning.
- `Flatmate.human` is `ManyToOne`: the same person can have several separate flatmate periods. The end is optional, and a missing end means the period is ongoing.
- The couple and the wedding venues are `ManyToOne`, instead of restrictive `OneToOne`. The same hall can host many weddings, and a person can appear in many such records. A couple must consist of two different people. Spouses are added to participants automatically.
- Every interaction, including weddings and flatmate periods, has an owner. Foreign people and places cannot be assigned; attempts return 404, the same as for records that do not exist.
- Entities are not serialised directly. DTOs have no cycles and no user account data.
- The feed is paginated and sorted by creation time. The calendar loads a full month, including overlapping ranges and all result pages.
- Clicking a day in the calendar opens a dialog with all interactions on that day, their participants, descriptions and photos. The dialog handles empty days, loading and errors; it closes with ×, Escape or a click on the background. Adding a meeting from the dialog keeps the selected date.
- The calendar leaves `FLATMATE` out of its tiles, the dialog, the counter and the legend. Flatmate periods remain in the database, the API and the news.
- In the dialog, the creator of an interaction can add their own people. A participant can be removed by the person themselves (matched by the Human ID linked to the account) or by the account that created their link to the interaction. The buttons follow the permissions returned in the DTO; the backend checks them independently. The contracts and metadata are described in [RELATIONSHIPS.md](RELATIONSHIPS.md).

No new rules for awarding points were added. The point-date method for the four original types keeps their semantics, with a safe event end if no own end is given. An event supports an optional photo from a file, a public link or an earlier own event; details are in [PHOTOS.md](PHOTOS.md).

## Creation API

### Order of person suggestions

Forms and the addition of participants in the calendar pass `type` to `GET /api/people/participants` (MEETING, VISIT, EVENT, TRIP, FLATMATE, WEDDING). Results are sorted by the number of distinct interactions of that type in which both the person linked to the signed-in account and the suggested person have retained participant links. This applies to the administrator too: other creators' interactions affect suggestions only through one's own participation. The count covers saved interactions only, without time decay or the people directory weights. Ties are broken by surname, first name and ID; people without history remain available. Filtering and sorting happen before LIMIT; the counts are not returned by the API.

The choice of spouses and flatmates uses `GET /api/people?type=...` with the same ordering, but keeps the previous scope of one's own people. Without `type`, both endpoints keep their default order. The scope of participants, portraits and write permissions does not change. No schema migration is needed.

### Wedding invitation and people ranking

`WeddingInput` also accepts `wasIInvited` (boolean, default false), returned as `details.wasIInvited`. The wedding form has a field "I was invited to the wedding", initially checked. `weddings.was_i_invited` is independent of `had_after` (the after-party). Historical records without this column get false; the invitation is not inferred from the record's presence or from the participant list. During migration, move the original `was_i_invited` value into this column. Before a schema update, make a database backup as described in AGENTS.md.

The point ranking in the People tab follows the rules described in README.md. For invited weddings, the couple is scored through the groom and bride relations, even if they are not on the participant links; ordinary guests are not scored. WAGS is recognised by the clique name, not by the old ID 5.

Each request contains a shared object:

```json
{
  "details": {
    "name": "Coffee after work",
    "longDesc": "Good conversation",
    "participantIds": [78, 79]
  }
}
```

`participantIds` is required but may be an empty list. Repeated IDs are removed. All person and place IDs must belong to the signed-in owner. The other fields depend on the endpoint:

| Endpoint | Fields outside `details` |
| --- | --- |
| `POST /api/meetings` | required `date`; optional `placeId`, `placeString` |
| `POST /api/visits` | required `visitDate`, `duration`; optional `placeId` |
| `POST /api/events` | required `startDate`, `stopDate`; optional `meComingDate`, `meLeavingDate`, `placeId`, `placeString`, `photoName`, and one photo source: `photoUrl`, `photoData` or `photoEventId` |
| `POST /api/trips` | required `startDate`, `endDate`, `placeIds` (may be empty) |
| `POST /api/flatmates` | required `humanId`, `startDate`; optional `stopDate`, `placeId` |
| `POST /api/weddings` | required `dateTime`, `groomId`, `brideId`; optional `ceremonyPlaceId`, `partyPlaceId`, `hotelId`, `hadAfter` |

Meeting and flatmate dates use `YYYY-MM-DD`. Other dates are local `YYYY-MM-DDTHH:mm:ss` values, with no automatic shift to UTC (as with the original `LocalDateTime`).

Example of a complete meeting:

```json
{
  "details": { "name": "Coffee after work", "longDesc": null, "participantIds": [] },
  "date": "2026-10-18",
  "placeId": null,
  "placeString": "Table by the window"
}
```

## Editing from the calendar dialog

The Edit button in the interaction details opens a form with the current data fetched from the API. Saving uses `PUT /api/meetings/{id}`, `/visits/{id}`, `/events/{id}`, `/trips/{id}`, `/flatmates/{id}` or `/weddings/{id}`, with the contract matching the creation of that type. The response is 200 and the current DTO. The interaction type cannot be changed; foreign IDs, missing IDs or a different type return 404. Only the creator of an interaction may edit it, with no administrator exception; a session and CSRF are required. Saving locks the parent record, as participant operations do.

In an edit, `details.participantIds` must be an empty set; participants are changed separately through the existing endpoints. Saving the name, description, dates or places does not recreate removed participants, and it keeps the IDs, time and creator of existing links. Changing the flatmate or the couple adds the newly chosen people to the participants, but does not remove the previous ones; existing references can be kept, including for imported records. Newly assigned people and places still require ownership. A trip keeps the metadata of the places that remain, removes only the links that were detached, and gives the new server-side metadata of the editing account to the new ones. The interaction identity, UUID, creator and creation date stay unchanged. No schema migration is needed.

The event form keeps the current photo by default through `photoEventId` pointing at the edited record. "No photo" detaches the photo, and a file, a link or another event replaces it with the existing normalisation and download protection. Old files stay on disk because they may be shared. New files are cleaned up on rollback. After saving, the calendar reloads the month, including when the interaction was moved to another date. Cancelling does not save changes.

## Reading interactions

The current rule replacing the earlier owner-only descriptions: the list and a single record are available to the creator or to the account whose linked Human has a retained participant entry. This applies to all types and to the calendar. The filter is applied before counting and pagination, with no administrator exception and no matching by surname. Removing one's own participation removes access for that participant, but not for the creator. Editing and adding participants remain limited to the creator; the rules for removing participants do not change. Event photos still require ownership, and their URL is omitted in foreign interactions. No migration or backfill is needed.

`GET /api/interactions` accepts `page` (from 0), `size` (1–100), `q`, optional `from` and `to`, and `type`: `MEETING`, `VISIT`, `EVENT`, `TRIP`, `FLATMATE`, `WEDDING`. It returns `content`, `page`, `size`, `totalElements` and `hasNext`.

The date filter returns ranges that **overlap** the given interval, not only entries that start within it. For periods that have no end, it applies no upper bound.

`GET /api/interactions/{id}` returns the record if it is your own, otherwise 404. The response contains the shared fields, the participant summaries, the place and a `details` object with the fields of that specific type. All creation operations require a session and CSRF.

## Person profile

The Profile section shows `lastSeen` (`daysAgo`, `interactionName`) from the single profile endpoint, or null when there is no shared history. The last interaction requires retained participant links for both the viewed person and the Human linked to the signed-in account, also for the administrator and regardless of the creator. It covers all types, skips future starts, sorts by end date (for ongoing periods, by the current time), then by start date and ID, descending. The number of days is the difference between local dates; an interaction in progress means zero days. No schema migration is needed.

The single-profile DTO (`GET /api/people/browse/{id}`), including the home and the clique's capital, is built inside a MeeterService transaction. Do not map the entity returned by `browseHuman` in the controller: with open-in-view disabled, lazy relations are not available then, and the profile can fail depending on the relations assigned.

The person dialog in the people directory contains the profile, an interaction map, paginated visits, meetings, events, trips and quotes. `GET /api/people/browse/{id}/interactions` accepts `type`, `page`, `size` (1–100), sorts by interaction date descending, and returns the standard PageView. It covers only interactions with that person on the participant list. Access is granted to the creator of the record, to a participant recognised by the account's Human ID, or to an administrator. The visibility filter is applied before counting and pagination. The profile is read-only; it does not extend the ordinary `/api/interactions` or any modification rights. Photos of other people's events are not attached, because their endpoint stays owner-only.

`GET /api/people/browse/{id}/map` applies the same filter to all results, regardless of the list page. It returns the points of the saved places of visits, meetings, events and all places of trips, with the place name and category, but without interaction names or dates. Each place appears only once (deduplicated by Place ID), even when it occurs in several interactions. Descriptive text is not geocoded. Leaflet shows the available points and a list; each trip also has its own map. `FLATMATE` and `WEDDING` stay outside this interaction profile. The directory exclusion of `my_exes` also applies to the profile endpoints.

## Trip map

For trips, the day dialog in the calendar shows an interactive Leaflet map with the points of the saved places and a list of their names. The view automatically covers all valid coordinates; clicking a point shows its name. The map uses OpenStreetMap tiles and needs an internet connection. If loading fails, the dialog shows a message and the list of places stays available. An empty set of places has its own message. The control colours and borders match both themes. The points do not show visiting order or the route of travel; the model stores a set of places.

Update on participant selection: the owner exception described above now also covers `details.participantIds` and the POST that adds participants. These accept people from the whole directory (except `my_exes`), as described in [RELATIONSHIPS.md](RELATIONSHIPS.md). Other references still require ownership.

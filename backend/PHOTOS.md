# Photos and profile pictures

## Own profile and archive

"My data" in the header opens editing of first name, last name, birthday, gender, city and Facebook link, and changing the portrait from a file or a public URL. `GET`/`PUT /api/people/self` and `GET`/`POST /api/people/self/photo` identify the person only by the Human ID linked to the session. They also work for imported records and people owned by another account, without changing the owner, home, clique or identity metadata. Writes require CSRF; the existing limits and download protections apply. Reading `/self/photo` does not depend on the directory exclusions.

Every replacement of a profile picture (including by the record's owner or an administrator) keeps the previous content in a separate folder `app.photos.former-directory` (default `backend/data/photos/former-people/`). The previous Photo record keeps its ID, UUID and date, receives a new random archive file name and the type `FORMER_HUMAN_PHOTO`. For a legacy `profile_photo_file` without metadata, an archive record is created without inventing a historical date. The archive has no public endpoint. The current portrait still points to the new `PROFILE_PICTURE` record. Migration V6 extends the enum of the `photos` table; earlier migrations stay unchanged.

The file is copied to the archive before the transaction is committed, and removed from the active folder only after the commit. A rollback deletes the new archive copy and the new portrait, and keeps the previous file and metadata. An error during cleanup after the commit leaves an extra copy rather than losing the photo. A replacement is blocked when there is no previous file, so that an archiving success is never pretended. This is the current rule, replacing the earlier description of deleting previous profile pictures. The backup must cover both folders and the database.

Clique photos and the photo import from the administrator's folder panel are described in [CLIQUES.md](CLIQUES.md). The `CLIQUE` type and the `cliques.photo_id` assignment are added by migration V4; the import never starts automatically.

## Place category icons

The PNG icons of the categories are in `backend/data/photos/placeCategories/` (configuration `app.photos.place-categories-directory`). Signed-in users read them through `GET /api/places/category-icons/{filename}`; the endpoint accepts only simple PNG names and files inside this folder. It does not expose other photo folders. When moving the application, keep this folder next to the other runtime files.

`src/PlacesCategoriesDict.js` assigns a category to a file. The shared TripMap uses it on the person's map and for trips (including in the calendar). The matching ignores letter case and extra spaces. The icons have light backgrounds so they remain readable in both themes. An unknown or empty category, or a missing file, keeps the plain point; place names remain in the suggestions. The points of `/api/people/browse/{id}/map` contain the category of the source place.

## Model compatible with the migration

`org.example.meeter.multimedia.photo.Photo` maps the `photos` table: `id`, `photo_name`, `latitude`, `longitude`, `photo_type`, `generation_time`, `uuid`. The type is the text name of the enum: `PROFILE_PICTURE`, `EVENT`, `TRIP`, `SUPERPOWER`. The model deliberately does not require an extra owner or a new creation time for imported metadata. There is no public endpoint that reads the whole photo table.

`Human.profilePhoto` is a one-to-one relation through `people.profile_photo_id`, as in the original. During migration, keep the photo IDs and the person references; after the import, set the ID counter above the maximum imported ID according to the database in use. Copy the files named by `photos.photo_name` into the `app.photos.directory` folder (default `backend/data/photos/people/`). The name denotes a single file, without a path or URL. PNG, JPEG and GIF are served with a type derived from their content. A missing file, a wrong photo type, an unsafe name or a link pointing outside the folder returns 404. Do not put files in public static resources.

The DTO still exposes `profilePhotoUrl` as `/api/people/{id}/photo?v={photo UUID}`. The existing frontend uses this field for participants and avatars too. Reading requires ownership of the person; quotes still omit the portraits of other people's authors. When the relation is empty, the legacy `profile_photo_file` is still supported. New profile-picture imports also save a `Photo` record of type `PROFILE_PICTURE`. Deleting a person or changing the profile picture does not cascade-delete the photo metadata.

All four types can be stored in the `photos` table. The type itself does not add a gallery or links to trips and superpowers; the original `Event` and `Citybreak` had no relation to this table. The event image mechanism described below stays independent. Migrating photo records and copying files do not run automatically at application start.

## Event photos

`POST /api/events` saves the event, its participants and an optional photo in one transaction. `placeString` lets you add a place description independently of the selected `placeId`. `photoName` is an optional label of up to 255 characters, not a file name on disk. At most one photo source is accepted:

- `photoUrl`: a direct public link, downloaded by the same protected HTTP client as profile pictures.
- `photoData`: the file contents encoded in Base64, without the data URL prefix; the original file is up to 4 MB.
- `photoEventId`: the ID of an earlier own event that has a photo. The local copy of the file is shared; it is not downloaded again.

Without a source, the event is saved without a photo. Images are decoded (up to 16 megapixels), stripped of metadata and saved as JPEG with the aspect ratio preserved and a longest side of up to 1600 px. The folder is `backend/data/photos/events/`, configured by `app.photos.events-directory`. File names are random UUIDs; the user's label does not affect the path. A rollback of the transaction deletes the newly saved file.

The event DTO contains `details.photoUrl` (the local address or null), `details.photoName` and `details.placeString`. `GET /api/events/{id}/photo` requires sign-in and ownership of the event. The form lets you search earlier events by name and browse the pages of their photos. None of these copies is placed in `resources/static`.

## Editing event photos

Editing with `PUT /api/events/{id}` applies the same photo sources and protections as creation. Keeping a photo requires `photoEventId` set to the ID of the edited event (the form sets it by default); having no source detaches the photo. Detaching or replacing does not delete the previous file, because other events may use it. A failed save keeps the previous state of the record and removes the new files through the existing transaction synchronisation.

## Person photos — API

The profile dialog lets the owner of a person, or an administrator, add or change the photo from a file or a public link, including for a person imported without an owner or belonging to another account. `POST /api/people/{id}/photo` accepts exactly one non-empty field: `url` or `photoData` (Base64 without the data URL prefix). The file limits and normalisation are shared: 4 MB, 16 megapixels, JPEG 512×512. `GET /api/people/browse/{id}` returns `canEditPhoto`, computed from the person's ownership or the administrator role. The administrator exception applies only to writing a photo; it does not change the person's owner or the rules for ordinary photo reads. For someone else's person, the write response points to the directory portrait address. A new photo also updates the card's avatar without reloading the page.

The person form accepts an optional `profilePhotoUrl`: a direct public HTTP/HTTPS link to an image. The backend downloads the file (up to 4 MB, up to 20 seconds), checks its contents and dimensions with the decoder (up to 16 megapixels), and then saves a cropped JPEG of 512×512 without metadata.

Files go to `backend/data/photos/people/`. The name has the form `<person UUID>-<photo UUID>.jpg`, so Polish letters, duplicate surnames and remote file names cannot collide. The original link is not used to display the photo and is not stored with the person.

- `POST /api/people` — optional `profilePhotoUrl` alongside the existing fields.
- `POST /api/people/{id}/photo` — changes the profile picture, JSON `{ "url": "https://example.org/photo.jpg" }`.
- `GET /api/people/{id}/photo` — the person's own local JPEG copy; requires sign-in and access to the person.
- Person and interaction participant DTOs contain a `profilePhotoUrl` that points to the local endpoint, not to a remote website.

A photo change deletes the previous file only after the transaction is committed. A failed import keeps the previous profile picture; a rolled-back save deletes the new file. If the import fails while a person is being added, the person is not saved partially.

Downloading does not send cookies or account data to remote servers. The standard ports 80 and 443 are allowed. Local, private, multicast, link-local, metadata-service and disallowed IPv6 addresses are blocked, including after a redirect. DNS is checked in the resolver used directly by the HTTP client. A download cannot go from HTTPS to HTTP. SVG and other files the image decoder does not support are not saved.

On Windows, run `./start.ps1` in the backend folder. The script uses the trusted system certificate store and does not disable TLS verification.

## Historical sample data

The sample-data generator and the download of demonstration portraits have been removed. People created earlier may still have the flag `demo=true` and local photos. Removing the generator does not delete existing records or files.

## Profile pictures in the shared people list

The participant DTO uses the normal portrait address for the person's own records. For people imported without an owner or belonging to another account, it points to the existing directory endpoint `/api/people/browse/{id}/photo`, keeping the photo version parameter. People excluded from the directory by `my_exes`, whose account is not held, do not receive a portrait address. This lets the calendar show the available portraits of imported participants without changing the permissions of the photo endpoints.

At the user's explicit request, the shared people directory shows all Human records after sign-in. Its DTO points to `GET /api/people/browse/{id}/photo`, which serves profile pictures also for people without an owner and for people assigned to other accounts. It keeps the validation of photo type, file and path. The existing `GET /api/people/{id}/photo`, the person pickers and the visibility rules for portraits in quotes stay unchanged. The photo write has the administrator exception described above.

The author and participant search results return portraits through the existing authenticated `/api/people/browse/{id}/photo`; this does not extend access to the ordinary `/api/people/{id}/photo` or to photo writes. The other quote DTOs keep their earlier rules for omitting the portraits of other authors.

Exception for history: participant selection also includes `my_exes`. Participant portraits are available through the authenticated `GET /api/people/participants/{id}/photo`, also for ex-partners; they use the same file protections as the other portraits. The ordinary directory and its photos still exclude `my_exes`; the quote-author selection keeps its existing filtering.

# Clique view and photo import

The **Cliques** tab uses the authenticated `GET /api/cliques/browse?page=0&size=12` (at most 100 cliques per page). It shows the cliques owned by the account, sorted in descending order by the mean of the squared internal points of their members. The result uses `PeopleRanking.SCORES`, counts members without history as zero, and skips the signed-in person both in the sum and in the member count. Empty cliques get zero. Ties are broken by ascending ID. The ranking is computed globally before pagination and is not returned by the API or shown in the UI. It does not change clique privacy or the existing `/api/cliques` selector. `my_exes` is skipped. Members of the current page are loaded in bulk, without a 100-person limit: their order comes from the same `PeopleRanking.SCORES` as the people directory (points descending, surname, first name, ID). The signed-in person and `my_exes` are skipped in the same way as in the directory. The result does not reveal points. Member ownership does not limit the shared people directory; clique ownership still limits reading the clique.

On a wide screen there are three cards in a row (two below 900 px, one below 700 px). A card shows a square clique photo of 96×96 px with rounded corners, without cropping (`object-fit: contain`), a centred name below it, and small member thumbnails of 28×28 px without captions. A member's first and last name appears in a tooltip when the pointer is over their avatar. A missing file or portrait shows a fallback state. The view handles loading, errors, retry and pagination.

Member portraits highlight on hover and keyboard focus. Clicking or pressing Enter or Space opens the existing person profile dialog with the interaction, map and quote tabs. The dialog closes with the button, Escape or a click outside it. Changing a photo in the profile also refreshes that person's thumbnails in the clique view.

## Clique sorting formula

For a clique $C$, let $M_C$ be its set of members, excluding the person linked to the signed-in account. The clique score is the **mean of the squared Social Scores of its members**:

$$
R(C) =
\begin{cases}
\displaystyle \frac{1}{n_C}\sum_{h \in M_C} \bigl(SS(h)\bigr)^2, & n_C > 0, \\
0, & n_C = 0.
\end{cases}
$$

Where:

- $M_C$ — members of the clique, excluding the person linked to the current account;
- $n_C = |M_C|$ — the number of those members, including people without shared interaction history;
- $SS(h)$ — the current Social Score of person $h$, computed by `PeopleRanking.SCORES` from the interactions in which the person linked to the viewer's account takes part. The weights, time decay and wedding rules are described in [README.md](README.md), and the participation scope in [PARTICIPATION_BACKFILL.md](PARTICIPATION_BACKFILL.md).

A person with no scored interactions has $SS(h)=0$. Such a person stays in the denominator. The person's own record is excluded from both the sum and the member count. A clique that is empty, or contains only the viewer's own record, scores zero.

The square is computed separately for each person, then the squares are summed and divided by the number of members. This is not the square of the mean, and the result is not divided again by the clique size. Worked example with made-up values: for member scores $10$ and $0$ the clique score is $(10^2+0^2)/2=50$, and for $5$ and $5$ it is $(5^2+5^2)/2=25$. The rule rewards single close relationships more strongly, while still counting every member of the group.

Cliques are sorted in descending order by $R(C)$, and ties are ordered by ascending ID. The ranking covers the whole available set before pagination. The order of people inside a clique still follows their individual Social Score, with ties broken by surname, first name and ID.

The score and formula are a technical description: they are not returned by the API, logged as user data or shown in the application UI.

## Schema preparation

`V4__clique_photos.sql` extends the photo type with `CLIQUE` and adds `cliques.photo_id` pointing to `photos` metadata. It does not import personal data or files. Before starting the backend with the new code, stop the previous backend and make a MariaDB backup as described in [MIGRATIONS.md](MIGRATIONS.md). Do not edit applied migrations. Tests use H2 and do not migrate the user's database.

## Administrator tool

In **Administration → Clique photo import**, paste the path of a folder that is accessible on the backend server. The tool accepts files named `ID.jpg`, `ID.jpeg`, `ID.png` or `ID.gif`, regardless of the letter case of the extension. It does not scan subfolders or follow links. The limits are 5000 entries in the folder, 4 MB and 16 megapixels per photo.

`POST /api/admin/cliques/photos/import` accepts `{ "folder": "C:\\Photos\\Cliques" }`; it requires an administrator session and CSRF. An administrator may assign photos to all existing cliques, including imported and foreign-owned ones, without changing their owner. It does not accept account data or a role from the request.

Each ID must have exactly one file. Unknown IDs, names that do not match and cliques that already have a photo are skipped. Several files for one ID are reported as an error, without choosing a random file. The result contains the counters `imported`, `skipped` and `failed`, and a list `items` with the file name, ID, status and message. It does not return source paths or exception details.

The originals stay unchanged. The backend decodes each photo, strips its metadata and saves it as a JPEG under a UUID name, keeping the aspect ratio and a maximum side of 1600 px. The target folder is `backend/data/photos/cliques/`, configured by `app.photos.cliques-directory`. A `Photo` of type `CLIQUE` is created in the database and linked through `cliques.photo_id`. Each file has its own transaction: a bad file does not roll back good ones, and an uncommitted write removes its new file. A clique lock serialises concurrent imports. Retrying does not create duplicates for cliques that already have a photo. The import never replaces existing photos.

Photos are served only through the authenticated `GET /api/cliques/{id}/photo`, with the existing clique ownership and safe path checks. They are not public static resources. Reading another person's clique still returns 404, even for an administrator; the import permission does not widen clique reads.

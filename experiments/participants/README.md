# Experiment: suggesting participants

The current version shared by all accounts: [SHARED_MODELS.md](SHARED_MODELS.md).
Run `.\experiments\participants\train-shared.ps1` from the project directory.
The description below of the export and the six separate files belongs to the earlier
per-account variant, which remains available for comparison. New training does not need the export file.

A standalone offline prototype. It does not change the backend, the schema or the data. The export
only runs SELECT statements in a consistent read-only snapshot, for the `created_by_id`
of the given account. `--account-id` is a tool for the local operator, not HTTP
authorisation. Do not expose this script as an endpoint. It does not fetch passwords or tokens.

## Models to compare

| Model | Why it is worth trying | Limitation |
| --- | --- | --- |
| Logistic regression | A regularised baseline; sensible with a small history | Mostly linear relationships between features |
| Extra Trees | Non-linear combinations of place, text and co-participation | May overfit rare participants |
| Histogram Gradient Boosting | Another non-linear model, regularised with small trees | Needs comparison on a longer history |
| Two-tower network + Deep Sets (future experiment) | Separate context and person vectors; pooling over the guest set independent of order | More data, tuning and work; not implemented |
| Heterogeneous graph Human–Interaction–Place (future experiment) | Learns shared circles and place relations | Complex graph training with a time cut-off; not implemented |

The first three models are implemented. Each learns a binary ranking of
interaction–candidate pairs, in two modes:

1. `context`: only the query name and place, plus earlier history.
2. `guests`: the same name and place plus the IDs of guests already selected. Guest
   features include the mean and maximum of historical co-participation frequency.
   Guest order does not matter. An empty list is allowed.

Names and the place text go through a tokeniser and a frozen encoder
[`PKOBP/polish-roberta-8k`](https://huggingface.co/PKOBP/polish-roberta-8k).
We use mean pooling with a mask and vector normalisation, not the raw token
numbering. This is a base encoder, not a ready-made sentence-similarity model: the
quality of this pooling must be checked empirically. A limit of 128 tokens is enough as a
start for short names; long place lists will be truncated. The RoBERTa weights are not trained.
We record the SHA of the revision used in training and use it again at prediction time.

## Entity mapping

- `Interaction.name`, `place_id → Place.name`.
- `Meeting.placeString` and `Event.placeString` as an independent place text.
- `Citybreak → trip_place → Place`: the whole set of places, without assuming an order.
- `Wedding`: ceremony, reception and hotel; we do not add people based on the
  spouses or the invitation flag. Labels come from `interaction_human`.
- `InteractionParticipant.human_id`: the participants actually saved. The Human
  linked to the account is skipped, because the application adds it automatically.
- Features: the text vector, its product with the candidate's historical vector, similarity,
  participation frequency, the share of interactions at a given place, the number of guests
  and co-participation. The place ID works alongside its name.

We do not use the Social Score, private quotes, photos, surnames, genders, birthdays or
passwords. The interaction type and event date are exported for future analyses,
but the current models do not use them as features: the first mode stays based
on name and place. Later ablation tests may examine type, day of week, seasonality and
geographic distance. We do not use current clicks in the historical backtest, because we do not
know their composition at the earlier time.

## Running (PowerShell, project directory)

You need your own Python 3.11/3.12. The first model download requires internet access
and space for the large weights. A GPU is optional; on a CPU encoding can be slow.
Texts are processed locally and are not sent to any inference API.

```powershell
py -3.12 -m venv experiments/participants/.venv
$pythonExe = Join-Path $PWD 'experiments/participants/.venv/Scripts/python.exe'
& $pythonExe -m pip install -r experiments/participants/requirements.txt

# After checking the contents of the local configuration script:
. .\set_agent_sandbo_env.ps1
# The same DB_URL (jdbc:mariadb://... or jdbc:mysql://...), DB_USER, DB_PASSWORD.
# Replace 123 with the real ID of your account; not the Human ID.
& $pythonExe experiments/participants/recommend.py export --account-id 123 --output experiments/participants/history.json
& $pythonExe experiments/participants/recommend.py train --data experiments/participants/history.json --output experiments/participants/artifacts
```

The export file is a single JSON document. By default the export does not widen visibility to
other people's interactions and does not require a connection to Neo4j. A DB_URL with unsupported
JDBC options is rejected, rather than silently changing, for example, the TLS settings.
For a remote database, required TLS must be configured explicitly in the PyMySQL client.

A prediction query, e.g. `experiments/participants/artifacts/query.json`:

```json
{
  "name": "Board games",
  "place_text": "my place",
  "places": [{"id": 42, "name": "Home"}],
  "guest_ids": [101, 102]
}
```

```powershell
& $pythonExe experiments/participants/recommend.py predict --model experiments/participants/artifacts/guests_logistic.joblib --query experiments/participants/artifacts/query.json --k 10
```

The result contains only an ordered list of Human IDs. Guests already selected do not return
in the suggestions. For the first mode choose `context_logistic.joblib` and remove
`guest_ids`. Models of the other variants: `*_extra_trees.joblib`, `*_boosting.joblib`.
This is a ranking, not a calibrated probability that someone will appear at a meeting.
Do not load someone else's joblib files: deserialisation can execute code.

## Evaluation and limitations

A 60/20/20 split by unique `created_at`: training, validation and test. Identical
timestamps never fall into different parts. At least 3 distinct creation times are required; a small number
of groups (e.g. after a bulk import) means unequal part sizes and a less reliable
evaluation. In training, candidate features use only earlier interactions, never the current labels.
Validation and test use the frozen training history. We do not update it with the real test answers.
The artefacts also contain only the training history.

The guests mode draws a non-empty proper subset of participants; the rest is the target.
For a single participant the input list is empty. Known guests are excluded from the
pair examples and the result; they are not used as negative labels. Both modes measure
Recall@K and NDCG@K. `candidate_coverage` shows the share of expected people known
from training; unknown people stay in the Recall denominator, so the result does not hide them.
The report also includes query counts. Candidates are people from the account's earlier history,
not the whole catalogue: this is a deliberate cold-start limitation. There is no my_exes filtering;
this is a local experiment on historical participants, not a catalogue endpoint.

Choose the model and K on validation; the test set is for the final evaluation. Do not tune
models based on repeated reads of the test set. It is also worth comparing simple historical popularity and
the list of frequent people for a place before judging the model's gain.
Full deployment will need retraining on all permitted history.

Absence from the list is treated as a weak negative: we sample up to 5 negatives per positive
(minimum 20). This does not prove that someone does not fit. Small or incomplete
sets can give misleading results. A few records are enough to fit trees, but not to choose a
model reliably. With a small history, start with regression.

The snapshot holds today's versions of names, places and participants. The lack of an edit
history means the `created_at` cut-off does not perfectly reproduce the earlier state;
a bulk import can further reduce the usefulness of this split. A manually reviewed
set or a prospective test of suggestions is then needed. Splitting by `start_date`
does not solve this on its own, because events can be entered retrospectively.

Exports and artefacts contain personal data, stay local and are ignored
by Git in the given locations. Do not log records. A production integration
must take the account from the session, separate per-account artefacts, validate guests
and filter candidates again according to the application's rules. The local integration of the
form and the API is described below.

Dependencies have version ranges, not a lockfile. For a reproducible series of experiments,
save `pip freeze` locally next to the report and give `--revision` as the model's SHA.

Synthetic tests (no database, no model download):

```powershell
& $pythonExe -m unittest discover -s experiments/participants -p 'test_*.py'
```

## Comparing models in the Meeter form

After training, run from the project root in PowerShell:

```powershell
. .\set_agent_sandbo_env.ps1
Set-Location backend
.\mvnw.cmd spring-boot:run
```

If the backend is already running, stop it and start it again. Before starting, check the
local database configuration script. Do not run a second backend on the same port. In a second
terminal start the frontend with the usual `npm.cmd run dev`.

In developer mode (`app.dev.bootstrap=true`) the backend starts a worker automatically if it finds `.venv`, `server.py` and at least
one trained `.joblib` file. It does not install dependencies and does not train models.
The worker runs in the background; it needs neither the database nor `history.json`. It uses the ready
artefacts from `artifacts/`. The connection token is generated in memory,
passed to the worker through the environment, never written to a file or printed.
The default port is 8092, optionally set with `MEETER_RECOMMENDER_PORT`. The worker's HTTP is
reachable only on 127.0.0.1 and requires that token. The frontend never receives it.
Process logs are in `artifacts/worker.out.log` and `worker.err.log`.
`worker.err.log` records the stages `warmup`, `models`, `suggest`, the execution time
and the exception type with stack-frame locations, but not exception contents, code lines,
requests, person IDs or secrets. The backend logs the operation, the exception type and its
causes, and the HTTP status (0 means no HTTP response).
`suggest-started` without `suggest-completed` means a calculation is running or was interrupted;
`HttpTimeoutException` in the backend terminal indicates the wait limit was exceeded.
The backend stops its own worker on shutdown; the worker also monitors the closing of the
parent process, so it exits if the backend exits abruptly.

The backend must run in the `backend` directory, as before. Optional
settings: `MEETER_RECOMMENDER_AUTOSTART=false` disables automatic start;
`MEETER_RECOMMENDER_DIRECTORY` points to the directory containing `server.py`, `.venv`
and `artifacts`; `MEETER_RECOMMENDER_PYTHON` overrides the interpreter path.
The default directory is `../experiments/participants`. Outside local developer mode
autostart is disabled by default. Tests with `app.dev.bootstrap=false` do not start
Python. If files are missing the backend starts normally and skips the models;
if the models fail to start, the endpoint returns 503, and the manual selectors still work.

The advanced external mode is still supported through `start-worker.ps1`: dot-source it
before the backend in the same session. A set `MEETER_RECOMMENDER_TOKEN`
with `MEETER_RECOMMENDER_AUTOSTART=false` means an external worker, which the backend neither
starts nor stops. With autostart, the token alone does not disable starting.
The backend checks an authenticated `/health` and waits for Python to become ready (up to 90 seconds).
The worker loads the shared models and the encoder before it exposes `/health`.
Do not mix the two modes; a worker with a different token occupying the port causes an error.

In the form for creating a meeting, visit, event, trip or wedding, choose "Suggestion model",
type a name and choose places, then click "Suggest participants". Clicking a person adds them to the
regular selector. Repeat the suggestions after adding a guest to check the `guests` mode. Switch the model
without changing the data to compare the ordering. The `context` mode ignores guests as
features but still removes them from the suggestions. Changing the data or the model clears the old
results; after 700 ms without changes the form fetches new suggestions automatically.
A button lets you repeat the query. Nothing is saved automatically.
Editing existing interactions and choosing a flatmate keep the regular selectors.

The selection shows only artefacts linked to the signed-in account's ID and Human ID.
If the list is empty, check the account, the artefact directory and the token configuration.
If there are no files for autostart and no configured worker,
the backend returns an empty list; when a configured worker is stopped,
the UI shows an error and a retry button. Manual selection still works.
Models and the encoder are cached in memory; after retraining, restart the worker.
The first inference may load RoBERTa and take longer (API limit 120 seconds).

Backend–worker communication sends JSON as a UTF-8 byte buffer with an explicit
`Content-Type: application/json` and a `Content-Length` counted in bytes.
The worker requires a non-empty body up to 16 MB; it does not support chunked streaming.

Meeter endpoints: an authenticated GET `/api/participant-models` and a POST
`/api/participant-models/suggest` with CSRF. The backend derives the account from the session,
checks ownership of places and the existence of guests, takes place names from the database, and
returns only the ID, fullName and participant photo. The candidate scope matches the
participant selector, including the historical `my_exes`. Other permissions
and endpoints are unchanged. This is a local experiment, not a public model service.

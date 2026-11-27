# Shared participant models

From the project directory, run in PowerShell:

```powershell
.\experiments\participants\train-shared.ps1
```

The script is self-contained: it creates `.venv` if it is missing, checks the dependencies,
loads `set_agent_sandbo_env.ps1`, reads a consistent MariaDB/MySQL snapshot
in a read-only transaction and trains three models. It does not read `history.json`, does not
export records to a file and does not change the database. Python must be installed
(the `py.exe` or `python.exe` command); the first download of RoBERTa requires internet access.
Check the local database configuration script before running it. Password values
and records are not printed by training. The dependency list contains only
package names and versions, without installation source addresses.

By default it uses two CPU threads. Optionally:

```powershell
.\experiments\participants\train-shared.ps1 -Threads 4
# If the dependencies are already installed:
.\experiments\participants\train-shared.ps1 -SkipDependencies
```

Results in `experiments/participants/artifacts/`:

- `shared_logistic.joblib` — shared logistic regression;
- `shared_extra_trees.joblib` — shared Extra Trees;
- `shared_boosting.joblib` — shared boosting;
- `shared_metrics.json` — validation/test metrics and the choice of the recommended algorithm;
- `shared-training-*.log` — the combined training run;
- `shared-requirements-lock.txt` — dependency versions to reproduce the experiment.

Each of the three models supports name/place and name/place/guests. Training
combines examples without guests with two random guest maskings; a candidate who is
a known guest is neither its own target nor a negative. Each pair's features come from
earlier days, separately for each account. Accounts have no separate weights; they are
not model features either. The model receives relationship statistics and text vectors.
New artefacts contain no history, no account or person IDs and no raw texts.

## Split and interpretation of results

The default `-SplitKey start_date` divides about 60/20/20 by the number of records,
keeping whole local days together. It solves the problem of imports with almost
identical `created_at` dates. This is an approximate backtest by event dates:
it does not reproduce the time the data became available, earlier edits or who the guests were in the past.
The `-SplitKey created_at` option assesses availability by creation days, if
the database has at least five different days; an import can make that split impossible.

Validation and test use only the history of the training part. Examples from one
day do not affect each other's features. `recall@k`, `ndcg@k` and
`candidate_coverage` are computed separately for `context` and `guests`. New people
stay in the Recall denominator, and an account without history receives an empty list.
The recommended model is chosen by the mean NDCG of both modes on validation.
The test result is not used to select or tune the model.

After evaluation the same settings are retrained on the whole snapshot, and only
those models are saved for use in the application. The report describes the earlier backtest,
not the result of the final model on its own training data. The shared format also works
when the current database has data for only one account; the benefit of learning
across accounts requires the history of several accounts. Records without a linked creator account
are not assigned to an invented user.

## Use in the application

### Time and day of the week (model format 3)

New training writes `schema_version=3` and `half_life_days` (default 365).
A meeting `d` local days old carries the weight `2 ** (-d / half_life_days)`.
Earlier proportional popularity and the share of a place in a person's history
are replaced by sums of weighted meetings: overall, and at any of the selected places.
Additional features are such sums for the day of the week of the planned interaction,
also combined with the place, and seven day-of-week indicators (Monday–Sunday).
Meetings at another place do not reduce the sum for the selected place.
One interaction contributes at most one weight per person, even with several places.
The remaining text and co-participation features remain available to the model.

The reference date is the local start date from the form or from the calendar interaction.
An older client without a date uses the date of the existing interaction, and when creating, today's date.
Only history from days earlier than the reference date is used, also for the
text and co-participation features. The same day is excluded, because the model
does not decide the order of hours. For a multi-day interaction the start date counts.
Changing the form's date refreshes the suggestions, without conversion to UTC.

The half-life can be set during manual training:

```powershell
.\experiments\participants\train-shared.ps1 -HalfLifeDays 365
```

The change requires an explicit retraining and a backend restart. Code changes alone
do not change existing weights: format 2 models and older per-account models still
use the earlier features. Do not train automatically on start. The
value 365 days is a starting setting, not the result of tuning on the user's data.

After training finishes, restart the backend (there is no need to start Python separately).
The backend waits for the process to be ready and for the weights to be preloaded. The recommended model
from the report is selected by default in the form. Suggestions appear automatically
after typing a name and refresh after a change of place, guests or model (700 ms pause).
They are also available when adding participants in the calendar day details.
Clicking a suggestion adds the person to the selection; saving remains a separate action.
In the form click "Check again" and choose the algorithm and the mode.
The six items in the selector are the three shared models used in two modes,
with no additional files for users. Shared models take precedence
over old per-account files; the old files are kept.

The backend builds the input history from interactions created by the signed-in account,
excludes its linked person and delivers it to the worker over a local, authenticated
connection. The frontend does not pass the account or the history. The worker
does not receive database credentials. When the data changes, the backend delivers the
current history. Text vectors and histories are cached in memory only,
with limits of 4096 texts per encoder and 32 history snapshots. Candidates
come only from the current account's history; person IDs are not merged by name.

The first suggestions require encoding the history with RoBERTa; on a CPU this
can take a while and exceed the 120-second request limit. Once that computation finishes,
a retry uses the buffer. Large histories can exceed the internal request
limit of 16 MB. These are limitations of the current local prototype.

New interactions refresh the features but do not change the model weights. Run
the same script again when you want to retrain the shared parameters. After retraining,
restart the backend to clear the buffer of the previous models. Do not load someone else's joblib files.

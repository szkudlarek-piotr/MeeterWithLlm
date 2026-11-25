# Online neural participant ranker (default)

`neural.py` is the default participant suggestion model (`context_neural`, `guests_neural`). It replaces the
earlier hand-weighted affinity ranker. The trained shared models (`shared_*.joblib`) stay available in the picker,
unchanged and no longer marked as recommended.

## Model

For a candidate person, every earlier interaction they took part in becomes one evidence vector of raw signals:
age in days (log and linear), character-trigram similarity of name, place text and saved place names to the new
interaction, a shared saved place, same weekday, interaction type of both, share of the already selected people
who were there, and group size. A small network encodes each evidence vector, pools them (sum and max), adds the
evidence count, and outputs a score. There is no hand-set decay or weighting: how fast acquaintances fade and
which signals matter is learned. Up to 64 most recent pieces of evidence per person are used.

Training is listwise: for each past interaction, its real participants should win a softmax against sampled
non-participants. Each interaction is learned twice, without guests (context mode) and with a random subset of
its participants given as guests (guest mode). Features of a training row use only strictly earlier days.

## Online learning

Each suggestion request first trains on interactions that are new or whose participants changed since the last
update, in date order, with a 15-second budget, then ranks; anything left continues on the next request. Only
interactions dated before today are learned, so participants added later in the day are included. Each step also
replays a few earlier examples so the network does not forget. On the first request for an account, the network
starts from scratch and works through the account's history within that budget; until its first step it orders
people by how often they appeared.

Weights are separate per account: `artifacts/online/account-{account}-human-{human}.pt` (Git-ignored, loaded with
`torch.load(weights_only=True)`). They are derived from personal data: back them up with the database and never
commit or share them. Deleting a file resets that account's network; it retrains from history on the next request.
The backend now sends the interaction `id` and `type` in the history and the form's `type` in the query.


### Hourly catch-up

While the backend runs, `ParticipantTraining` (`@Scheduled`, first run 5 minutes after start, then one hour after each
run finishes) sends every account's history to the worker's `/train` endpoint, which learns new or changed interactions
exactly like a suggestion request does, but without ranking. It runs up to four 15-second rounds per account and does
nothing when the worker is not available. This also picks up interactions from yesterday and earlier that nobody asked
suggestions for. Tune with `app.recommendations.training-interval` and `app.recommendations.training-initial-delay`
(ISO-8601 durations, e.g. `PT30M`).

## Comparing with the shared models (run by the project owner)

```powershell
. .\set_agent_sandbo_env.ps1   # or your own DB_URL / DB_USER / DB_PASSWORD
cd experiments\participants
.\.venv\Scripts\python.exe compare_neural.py
```

It uses the same temporal split, masked-guest protocol and metrics as `shared.measure`, trains each account's
network in memory on the training partition only, and prints aggregate `recall@k`, `ndcg@k` and
`candidate_coverage`. It writes no file. Agents do not run it or read its output.

## UI

The picker chooses an algorithm; the mode follows the form. With nobody added it uses context mode (name, place,
date, type); from the first person added it switches to guest mode, and back when the last one is removed.

Tests: `python -m unittest test_neural test_server` (synthetic data only, needs torch).

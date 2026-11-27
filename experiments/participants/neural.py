"""Per-account neural participant ranker, trained online from the account's own history.

No hand-set weights or decay: every earlier interaction of a candidate becomes one evidence vector of raw
signals (age in days, name/place similarity, shared places, weekday, interaction type, overlap with the
people already selected, group size). A small network encodes each piece of evidence, pools them, and scores
the candidate, so how fast acquaintances "fade" and what matters is learned from data (DeepSets-style).

Training is online and resumable: each request first trains on interactions that are new (or whose
participants changed) since the last update, in date order, within a time budget, then ranks. Features for a
training row use only interactions from strictly earlier days. Weights live in one file per account and are
never shared between accounts.
"""
from collections import defaultdict
from datetime import date
import math
import os
from pathlib import Path
import random
import re
import tempfile
import time
import unicodedata

SCHEMA_VERSION = 1
TYPES = ("MEETING", "VISIT", "EVENT", "TRIP", "WEDDING", "FLATMATE")
EVIDENCE_LIMIT = 64          # Most recent earlier interactions per candidate kept as evidence.
REPLAY_SIZE = 512
REPLAY_PER_STEP = 4
NEGATIVES_MIN, NEGATIVES_PER_POSITIVE = 20, 5
TRAIN_BUDGET_SECONDS = 15.0
_POLISH = str.maketrans({"ł": "l", "Ł": "l"})


def words(text):
    text = unicodedata.normalize("NFKD", str(text or "").translate(_POLISH).lower())
    return re.findall(r"[a-z0-9]+", "".join(c for c in text if not unicodedata.combining(c)))


def grams(text):
    """Character trigrams per word, tolerant to Polish inflection and typos."""
    result = set()
    for word in words(text):
        padded = f"  {word} "
        result.update(padded[i:i + 3] for i in range(len(padded) - 2))
    return frozenset(result)


def dice(a, b):
    return 2 * len(a & b) / (len(a) + len(b)) if a and b else 0.0


def row_text(row):
    return " ".join([str(row.get("name") or ""), str(row.get("place_text") or "")]
                    + [str(p.get("name") or "") for p in row.get("places", [])])


def day_of(row):
    value = row.get("start_date")
    return date.fromisoformat(str(value)[:10]) if value else date.today()


def type_vector(value):
    return [1.0 if value == name else 0.0 for name in TYPES]


EVENT_FEATURES = 9 + 2 * len(TYPES)
GLOBAL_FEATURES = 2


class Index:
    """Who took part in what, from the history rows the backend sends (self already removed)."""
    def __init__(self, rows):
        self.rows = {}
        self.events = defaultdict(list)
        for row in rows:
            people = frozenset(row.get("participants", []))
            record = (day_of(row), grams(row_text(row)), frozenset(p["id"] for p in row.get("places", [])),
                      people, row.get("type"))
            if row.get("id") is not None:
                self.rows[row["id"]] = row
            for person in people:
                self.events[person].append(record)
        for records in self.events.values():
            records.sort(key=lambda record: record[0])

    def candidates(self, day):
        return sorted(person for person, records in self.events.items() if records and records[0][0] < day)

    def evidence(self, person, query, guests):
        day, query_grams = day_of(query), grams(row_text(query))
        query_places = frozenset(p["id"] for p in query.get("places", []))
        query_type = query.get("type")
        earlier = [record for record in self.events.get(person, []) if record[0] < day]
        vectors = []
        for event_day, event_grams, places, people, event_type in earlier[-EVIDENCE_LIMIT:]:
            age = (day - event_day).days
            overlap = len(guests & people) / len(guests) if guests else 0.0
            vectors.append([math.log1p(age), min(age / 365.0, 20.0), dice(query_grams, event_grams),
                            1.0 if query_places & places else 0.0,
                            1.0 if event_day.weekday() == day.weekday() else 0.0,
                            overlap, 1.0 if guests else 0.0, math.log2(1 + len(people)),
                            1.0 if query_type and query_type == event_type else 0.0]
                           + type_vector(event_type) + type_vector(query_type))
        return vectors, [math.log1p(len(earlier)), 1.0 if guests else 0.0]


def network():
    import torch
    from torch import nn

    class Ranker(nn.Module):
        def __init__(self):
            super().__init__()
            self.event = nn.Sequential(nn.Linear(EVENT_FEATURES, 32), nn.ReLU(), nn.Linear(32, 32), nn.ReLU())
            self.score = nn.Sequential(nn.Linear(64 + GLOBAL_FEATURES, 32), nn.ReLU(), nn.Linear(32, 1))

        def forward(self, events, mask, global_features):
            encoded = self.event(events) * mask.unsqueeze(-1)
            pooled_max = encoded.masked_fill(~mask.unsqueeze(-1).bool(), 0).max(dim=1).values
            return self.score(torch.cat([encoded.sum(dim=1), pooled_max, global_features], dim=1)).squeeze(-1)

    return Ranker()


def batch(index, query, guests, people):
    import torch
    evidence = [index.evidence(person, query, guests) for person in people]
    width = max([len(vectors) for vectors, _ in evidence] + [1])
    events = torch.zeros(len(people), width, EVENT_FEATURES)
    mask = torch.zeros(len(people), width)
    for i, (vectors, _) in enumerate(evidence):
        if vectors:
            events[i, :len(vectors)] = torch.tensor(vectors)
            mask[i, :len(vectors)] = 1
    return events, mask, torch.tensor([extra for _, extra in evidence], dtype=torch.float32)


class OnlineRanker:
    def __init__(self, path=None, seed=0):
        import torch
        torch.manual_seed(seed)
        self.path = Path(path) if path else None
        self.model = network()
        self.optimizer = torch.optim.Adam(self.model.parameters(), lr=1e-3, weight_decay=1e-4)
        self.trained = {}   # interaction id -> participants tuple it was trained with
        self.replay = []    # [interaction id, guests] pairs
        self.steps = 0
        self.random = random.Random(seed)
        if self.path and self.path.is_file():
            state = torch.load(self.path, weights_only=True)
            if state.get("schema_version") == SCHEMA_VERSION:
                self.model.load_state_dict(state["model"])
                self.optimizer.load_state_dict(state["optimizer"])
                self.trained = {int(key): tuple(value) for key, value in state["trained"]}
                self.replay = [(int(key), tuple(value)) for key, value in state["replay"]]
                self.steps = int(state["steps"])

    def save(self):
        import torch
        if not self.path:
            return
        self.path.parent.mkdir(parents=True, exist_ok=True)
        state = {"schema_version": SCHEMA_VERSION, "model": self.model.state_dict(),
                 "optimizer": self.optimizer.state_dict(), "steps": self.steps,
                 "trained": [[key, list(value)] for key, value in self.trained.items()],
                 "replay": [[key, list(value)] for key, value in self.replay]}
        handle, temporary = tempfile.mkstemp(dir=self.path.parent, suffix=".tmp")
        os.close(handle)
        try:
            torch.save(state, temporary)
            os.replace(temporary, self.path)
        finally:
            if os.path.exists(temporary):
                os.remove(temporary)

    def _loss(self, index, row, guests):
        import torch
        truth = frozenset(row.get("participants", [])) - guests
        people = [person for person in index.candidates(day_of(row)) if person not in guests]
        positives = [person for person in people if person in truth]
        if not positives:
            return None
        negatives = [person for person in people if person not in truth]
        limit = max(NEGATIVES_MIN, NEGATIVES_PER_POSITIVE * len(positives))
        if len(negatives) > limit:
            negatives = self.random.sample(negatives, limit)
        chosen = positives + negatives
        logits = self.model(*batch(index, row, guests, chosen))
        # Listwise: every real participant should win the softmax over this candidate list.
        return -torch.log_softmax(logits, dim=0)[:len(positives)].mean()

    def _guest_masks(self, row):
        people = sorted(row.get("participants", []))
        masks = [frozenset()]
        if len(people) >= 2:
            masks.append(frozenset(self.random.sample(people, self.random.randint(1, len(people) - 1))))
        return masks

    def update(self, rows, today=None, budget_seconds=TRAIN_BUDGET_SECONDS):
        """Train on interactions dated before today that are new or changed. Returns rows trained."""
        today = today or date.today()
        index = Index(rows)
        pending = sorted((row for row in index.rows.values()
                          if row.get("participants") and day_of(row) < today
                          and self.trained.get(row["id"]) != tuple(sorted(row["participants"]))),
                         key=lambda row: (day_of(row), row["id"]))
        started, done = time.monotonic(), 0
        self.model.train()
        for row in pending:
            if time.monotonic() - started > budget_seconds:
                break
            losses = []
            for guests in self._guest_masks(row):
                loss = self._loss(index, row, guests)
                if loss is not None:
                    losses.append(loss)
                    self.replay.append((row["id"], tuple(sorted(guests))))
            for key, guests in self.random.sample(self.replay, min(REPLAY_PER_STEP, len(self.replay))):
                if key in index.rows and key != row["id"]:
                    loss = self._loss(index, index.rows[key], frozenset(guests))
                    if loss is not None:
                        losses.append(loss)
            if losses:
                self.optimizer.zero_grad()
                (sum(losses) / len(losses)).backward()
                self.optimizer.step()
                self.steps += 1
            self.trained[row["id"]] = tuple(sorted(row["participants"]))
            del self.replay[:-REPLAY_SIZE]
            done += 1
        if done:
            self.save()
        return done

    def rank(self, rows, query, guests=(), k=10, exclude=()):
        import torch
        index = rows if isinstance(rows, Index) else Index(rows)
        guests = frozenset(guests)
        people = [person for person in index.candidates(day_of(query)) if person not in guests and person not in set(exclude)]
        if not people:
            return []
        if self.steps == 0:
            # Untrained network: order by how often the person appeared before, until the first update.
            counts = {person: len([r for r in index.events[person] if r[0] < day_of(query)]) for person in people}
            return sorted(people, key=lambda person: (-counts[person], person))[:k]
        self.model.eval()
        with torch.inference_mode():
            scores = self.model(*batch(index, query, guests, people)).tolist()
        return [person for _, person in sorted(zip((-s for s in scores), people))][:k]


def backtest(rows, split_key="start_date", k=10, budget_seconds=float("inf")):
    """Owner-run comparison with the shared models: same temporal split, masked guests (rng 81) and
    metrics as shared.measure. Each account's network trains online over the training partition only.
    Returns aggregates only."""
    import numpy as np
    from recommend import known_guests
    from shared import temporal_split
    parts = temporal_split(rows, split_key)
    by_account = defaultdict(list)
    for i in parts[0]:
        row = rows[i]
        by_account[row["account_id"]].append({**row, "participants": sorted(set(row["participants"]) - {row["self_id"]})})
    rankers, indexes = {}, {}
    for account, history in by_account.items():
        rankers[account] = OnlineRanker(seed=account)
        rankers[account].update(history, today=date.max, budget_seconds=budget_seconds)
        indexes[account] = Index(history)
    report = {"k": k, "split_sizes": list(map(len, parts))}
    for name, indices in zip(("validation", "test"), parts[1:]):
        report[name] = {}
        for mode in ("context", "guests"):
            rng = np.random.default_rng(81)
            values = []
            for i in indices:
                row = rows[i]
                truth = set(row["participants"]) - {row["self_id"]}
                guests = known_guests(truth, rng) if mode == "guests" else []
                truth -= set(guests)
                if not truth:
                    continue
                ranker = rankers.get(row["account_id"]) or OnlineRanker(seed=0)
                index = indexes.get(row["account_id"]) or Index([])
                predicted = ranker.rank(index, row, guests, k, exclude={row["self_id"]})
                known = set(index.candidates(day_of(row)))
                dcg = sum(1 / np.log2(j + 2) for j, p in enumerate(predicted) if p in truth)
                ideal = sum(1 / np.log2(j + 2) for j in range(min(k, len(truth))))
                values.append((len(truth & set(predicted)) / len(truth), dcg / ideal, len(truth & known) / len(truth)))
            report[name][mode] = {"queries": len(values), **{
                metric: float(np.mean([v[j] for v in values])) if values else None
                for j, metric in enumerate(("recall@k", "ndcg@k", "candidate_coverage"))}}
    return report

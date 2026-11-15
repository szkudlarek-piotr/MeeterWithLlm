"""Train shared participant rankers from a read-only database snapshot."""
import argparse
from collections import defaultdict
import json
from pathlib import Path
import tempfile

import numpy as np
from recommend import MODEL, Encoder, TemporalHistory, known_guests, rank


def stamp(row, split_key):
    return str(row[split_key])[:10]  # Same local day never crosses a boundary.


def temporal_split(rows, split_key):
    groups = defaultdict(list)
    for i, row in enumerate(rows):
        groups[stamp(row, split_key)].append(i)
    dates = sorted(groups)
    if len(dates) < 5:
        raise ValueError("Za mało różnych dni do podziału trening/walidacja/test")
    # Choose boundaries by record count, retaining entire day groups.
    sizes = np.cumsum([len(groups[day]) for day in dates])
    a = min(max(int(np.searchsorted(sizes, len(rows) * .6)) + 1, 1), len(dates) - 2)
    b = min(max(int(np.searchsorted(sizes, len(rows) * .8)) + 1, a + 1), len(dates) - 1)
    return tuple([i for day in days for i in groups[day]] for days in (dates[:a], dates[a:b], dates[b:]))


def training_samples(rows, vectors, indices, split_key, half_life_days=365):
    histories = defaultdict(lambda: TemporalHistory(half_life_days))
    rng = np.random.default_rng(42)
    x, y = [], []
    groups = defaultdict(list)
    for i in indices:
        groups[stamp(rows[i], split_key)].append(i)
    for day in sorted(groups):
        for i in groups[day]:
            row = rows[i]
            history = histories[row["account_id"]]
            truth = set(row["participants"]) - {row["self_id"]}
            # One shared estimator learns BOTH tasks, with two masked draws for guest mode.
            guest_sets = [[], known_guests(truth, rng), known_guests(truth, rng)]
            seen_masks = set()
            for guests in guest_sets:
                mask = tuple(guests)
                if mask in seen_masks:
                    continue
                seen_masks.add(mask)
                eligible = set(history.past(history.date(row)).count) - set(guests)
                negative = sorted(eligible - truth)
                limit = max(20, 5 * len(truth))
                if len(negative) > limit:
                    negative = rng.choice(negative, limit, replace=False).tolist()
                for person in sorted((eligible & truth) | set(negative)):
                    x.append(history.features(row, vectors[i], person, guests))
                    y.append(int(person in truth))
        # No current-day participants enter the features of another current-day record.
        for i in groups[day]:
            row = {**rows[i], "participants": sorted(set(rows[i]["participants"]) - {rows[i]["self_id"]})}
            histories[row["account_id"]].add(row, vectors[i])
    if len(set(y)) < 2:
        raise ValueError("Za mało przykładów pozytywnych i negatywnych")
    return np.asarray(x, dtype=np.float32), np.asarray(y), dict(histories)


def measure(model, histories, rows, vectors, indices, mode, k):
    rng = np.random.default_rng(81)
    values = []
    for i in indices:
        row = rows[i]
        truth = set(row["participants"]) - {row["self_id"]}
        guests = known_guests(truth, rng) if mode == "guests" else []
        truth -= set(guests)
        if not truth:
            continue
        history = histories.get(row["account_id"], TemporalHistory())
        eligible = history.past(history.date(row)).count
        predicted = rank(model, history, row, vectors[i], guests, k, exclude_ids={row["self_id"]})
        dcg = sum(1 / np.log2(j + 2) for j, p in enumerate(predicted) if p in truth)
        ideal = sum(1 / np.log2(j + 2) for j in range(min(k, len(truth))))
        values.append((len(truth & set(predicted)) / len(truth), dcg / ideal,
                       len(truth & set(eligible)) / len(truth)))
    return {"queries": len(values), **{name: float(np.mean([v[j] for v in values])) if values else None
            for j, name in enumerate(("recall@k", "ndcg@k", "candidate_coverage"))}}


def estimators():
    from sklearn.ensemble import ExtraTreesClassifier, HistGradientBoostingClassifier
    from sklearn.linear_model import LogisticRegression
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler
    return {
        "logistic": make_pipeline(StandardScaler(), LogisticRegression(C=.1, max_iter=1500, random_state=42)),
        "extra_trees": ExtraTreesClassifier(n_estimators=200, max_depth=15, min_samples_leaf=5, random_state=42, n_jobs=2),
        "boosting": HistGradientBoostingClassifier(max_iter=150, max_leaf_nodes=15, l2_regularization=1, random_state=42),
    }


def atomic_dump(bundle, path):
    import joblib
    with tempfile.NamedTemporaryFile(dir=path.parent, suffix=".tmp", delete=False) as file:
        temporary = Path(file.name)
    try:
        joblib.dump(bundle, temporary)
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def train(args):
    import torch
    from database import load_dataset
    torch.set_num_threads(args.threads)
    print("Odczyt spójnego snapshotu bazy (read-only)...", flush=True)
    rows = load_dataset()
    if not rows:
        raise ValueError("Brak interakcji powiązanych z kontami")
    parts = temporal_split(rows, args.split_key)
    print(json.dumps({"interactions": len(rows), "accounts": len({r['account_id'] for r in rows}),
                      "split_sizes": list(map(len, parts)), "split_key": args.split_key}), flush=True)
    encoder = Encoder(args.encoder, args.revision)
    print("Tworzenie wektorów tekstu RoBERTa...", flush=True)
    vectors = encoder.encode(rows, progress=lambda done, total: print(f"Teksty: {done}/{total}", flush=True))
    print("Przygotowanie przykładów par z maskowaniem gości...", flush=True)
    x, y, history = training_samples(rows, vectors, parts[0], args.split_key, args.half_life_days)
    report = {"schema_version": 3, "half_life_days": args.half_life_days, "scope": "shared", "encoder": args.encoder, "revision": encoder.revision,
              "accounts": len({r['account_id'] for r in rows}), "interactions": len(rows),
              "split_key": args.split_key, "split_sizes": list(map(len, parts)), "k": args.k,
              "training_pairs": len(y), "models": {},
              "limitation": "Snapshot of current records; event-date backtest does not reconstruct past edits/import availability."}
    models = estimators()
    for name, model in models.items():
        print("Ocena modelu: " + name, flush=True)
        model.fit(x, y)
        report["models"][name] = {partition: {mode: measure(model, history, rows, vectors, indices, mode, args.k)
            for mode in ("context", "guests")} for partition, indices in zip(("validation", "test"), parts[1:])}
    # Validation selects a default. Test results never affect selection or hyperparameters.
    report["recommended_model"] = max(models, key=lambda name: np.mean([
        report["models"][name]["validation"][mode]["ndcg@k"] or 0 for mode in ("context", "guests")]))
    print("Końcowy trening na całej dopuszczonej historii...", flush=True)
    x, y, _ = training_samples(rows, vectors, range(len(rows)), args.split_key, args.half_life_days)
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    for name, model in models.items():
        print("Zapis wspólnego modelu: " + name, flush=True)
        model.fit(x, y)
        # No account IDs, Human IDs, participant lists, text, or history in the artifact.
        atomic_dump({"schema_version": 3, "half_life_days": args.half_life_days, "scope": "shared", "algorithm": name,
                     "encoder": args.encoder, "revision": encoder.revision, "model": model}, output / ("shared_" + name + ".joblib"))
    (output / "shared_metrics.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default=str(Path(__file__).parent / "artifacts"))
    parser.add_argument("--encoder", default=MODEL)
    parser.add_argument("--revision", default="main")
    parser.add_argument("--split-key", choices=("start_date", "created_at"), default="start_date")
    parser.add_argument("--threads", type=int, default=2)
    parser.add_argument("--k", type=int, default=10)
    parser.add_argument("--half-life-days", type=float, default=365)
    arguments = parser.parse_args()
    import math
    if arguments.threads < 1 or arguments.k < 1 or not math.isfinite(arguments.half_life_days) or arguments.half_life_days <= 0:
        parser.error("threads, k i half-life-days muszą być dodatnie i skończone")
    train(arguments)

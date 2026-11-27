"""Offline, account-scoped participant ranking. No application/database writes."""
import argparse
from collections import Counter, defaultdict, OrderedDict
import json
import os
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

MODEL = "PKOBP/polish-roberta-8k"


def export_data(account_id, output):
    import pymysql
    url = urlsplit(os.environ["DB_URL"].removeprefix("jdbc:"))
    if url.scheme not in {"mysql", "mariadb"} or not url.hostname or not url.path.strip("/"):
        raise ValueError("DB_URL musi być JDBC mysql/mariadb z nazwą bazy")
    if url.username or url.password:
        raise ValueError("Użyj DB_USER i DB_PASSWORD zamiast danych logowania w DB_URL")
    options = parse_qs(url.query)
    unsupported = set(options) - {"useUnicode", "characterEncoding", "serverTimezone"}
    if unsupported:
        raise ValueError("Nieobsługiwane opcje JDBC; skonfiguruj świadomie połączenie Pythona")
    connection = pymysql.connect(
        host=url.hostname, port=url.port or 3306, database=url.path.lstrip("/"),
        user=os.environ["DB_USER"], password=os.environ["DB_PASSWORD"],
        charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor,
        connect_timeout=10, autocommit=False,
    )
    try:
        with connection.cursor() as cur:
            cur.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            cur.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY")
            cur.execute("SELECT human_id FROM app_users WHERE id=%s", (account_id,))
            account = cur.fetchone()
            if not account:
                raise ValueError("Nie znaleziono konta")
            cur.execute("""
                SELECT i.id, i.name, i.created_at, i.start_date,
                       COALESCE(m.place_string, e.place_string, '') AS place_text,
                       CASE WHEN m.id IS NOT NULL THEN 'MEETING'
                            WHEN v.id IS NOT NULL THEN 'VISIT'
                            WHEN e.id IS NOT NULL THEN 'EVENT'
                            WHEN t.id IS NOT NULL THEN 'TRIP'
                            WHEN w.id IS NOT NULL THEN 'WEDDING'
                            ELSE 'FLATMATE' END AS type
                FROM interactions i
                LEFT JOIN meetings m ON m.id=i.id LEFT JOIN visits v ON v.id=i.id
                LEFT JOIN events e ON e.id=i.id LEFT JOIN trips t ON t.id=i.id
                LEFT JOIN weddings w ON w.id=i.id
                WHERE i.created_by_id=%s ORDER BY i.created_at, i.id
            """, (account_id,))
            rows = {r["id"]: {**r, "participants": [], "places": []} for r in cur.fetchall()}
            cur.execute("""
                SELECT ih.interaction_id, ih.human_id FROM interaction_human ih
                JOIN interactions i ON i.id=ih.interaction_id WHERE i.created_by_id=%s
            """, (account_id,))
            for link in cur.fetchall():
                if link["human_id"] != account["human_id"]:
                    rows[link["interaction_id"]]["participants"].append(link["human_id"])
            cur.execute("""
                SELECT DISTINCT x.interaction_id, p.id, p.name FROM (
                    SELECT id AS interaction_id, place_id FROM interactions
                    UNION SELECT trip_id, place_id FROM trip_place
                    UNION SELECT id, ceremony_place_id FROM weddings
                    UNION SELECT id, party_place_id FROM weddings
                    UNION SELECT id, hotel_id FROM weddings
                ) x JOIN places p ON p.id=x.place_id
                JOIN interactions i ON i.id=x.interaction_id WHERE i.created_by_id=%s
                ORDER BY x.interaction_id, p.id
            """, (account_id,))
            for place in cur.fetchall():
                rows[place["interaction_id"]]["places"].append({"id": place["id"], "name": place["name"]})
        payload = {"account_id": account_id, "self_id": account["human_id"], "rows": list(rows.values())}
        Path(output).write_text(json.dumps(payload, default=str, ensure_ascii=False), encoding="utf-8")
    finally:
        connection.rollback()
        connection.close()


class Encoder:
    def __init__(self, model=MODEL, revision="main"):
        import torch
        from transformers import AutoModel, AutoTokenizer
        self.torch = torch
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.tokenizer = AutoTokenizer.from_pretrained(model, revision=revision, trust_remote_code=False)
        self.model = AutoModel.from_pretrained(model, revision=revision, trust_remote_code=False).to(self.device).eval()
        self.revision = self.model.config._commit_hash or revision
        self.text_cache = OrderedDict()

    def encode(self, rows, progress=None):
        import numpy as np
        texts = ["Nazwa: " + r.get("name", "") + ". Miejsce: " + r.get("place_text", "")
                 + "; " + "; ".join(p["name"] for p in r.get("places", [])) for r in rows]
        values = {text: self.text_cache[text] for text in set(texts) if text in self.text_cache}
        missing = list(dict.fromkeys(text for text in texts if text not in values))
        for offset in range(0, len(missing), 8):
            batch = missing[offset:offset + 8]
            tokens = self.tokenizer(batch, padding=True, truncation=True,
                                    max_length=128, return_tensors="pt").to(self.device)
            with self.torch.inference_mode():
                hidden = self.model(**tokens).last_hidden_state
                mask = tokens.attention_mask.unsqueeze(-1)
                pooled = (hidden * mask).sum(1) / mask.sum(1).clamp(min=1)
                vectors = self.torch.nn.functional.normalize(pooled, dim=1).cpu().numpy()
                values.update(zip(batch, vectors))
            if progress is not None:
                progress(min(offset + len(batch), len(missing)), len(missing))
        for text in texts:
            self.text_cache[text] = values[text]
            self.text_cache.move_to_end(text)
        while len(self.text_cache) > 4096:
            self.text_cache.popitem(last=False)
        return np.asarray([values[text] for text in texts])


class History:
    def __init__(self):
        self.count = Counter()
        self.place = Counter()
        self.pair = Counter()
        self.vectors = {}
        self.n = 0

    def add(self, row, vector):
        people = set(row["participants"])
        self.n += 1
        for person in people:
            self.count[person] += 1
            self.vectors[person] = self.vectors.get(person, 0) + vector
            for place in row.get("places", []):
                self.place[person, place["id"]] += 1
            for guest in people - {person}:
                self.pair[person, guest] += 1

    def features(self, row, vector, person, guests):
        import numpy as np
        count = self.count[person]
        centroid = self.vectors.get(person, np.zeros_like(vector)) / max(count, 1)
        affinity = [self.pair[person, g] / max(self.count[g], 1) for g in guests]
        places = [self.place[person, p["id"]] / max(count, 1) for p in row.get("places", [])]
        # Context vector remains present in BOTH modes. No target guest in its own features.
        return np.r_[vector, vector * centroid,
                     np.dot(vector, centroid) / max(np.linalg.norm(centroid), 1e-8),
                     count / max(self.n, 1), max(places, default=0),
                     len(guests), np.mean(affinity) if affinity else 0, max(affinity, default=0)]


class TemporalHistory(History):
    """Schema 3: additive, time-decayed evidence; no place/person denominator."""
    def __init__(self, half_life_days=365):
        super().__init__()
        import math
        if not math.isfinite(half_life_days) or half_life_days <= 0:
            raise ValueError("half_life_days must be positive and finite")
        self.half_life_days = half_life_days
        self.events = {}
        self.records = []
        self.past_cache = {}

    @staticmethod
    def date(row):
        from datetime import date
        return date.fromisoformat(str(row["start_date"])[:10])

    def add(self, row, vector):
        day = self.date(row)
        super().add(row, vector)
        self.records.append((day, row, vector))
        self.past_cache.clear()
        places = frozenset(p["id"] for p in row.get("places", []))
        for person in set(row["participants"]):
            self.events.setdefault(person, []).append((day, places))

    def features(self, row, vector, person, guests):
        import numpy as np
        day = self.date(row)
        places = {p["id"] for p in row.get("places", [])}
        total = at_place = weekday = place_weekday = 0.0
        for previous, previous_places in self.events.get(person, []):
            age = (day - previous).days
            # A local-day query cannot know intra-day order. Exclude same/future days.
            if age <= 0:
                continue
            weight = 2.0 ** (-age / self.half_life_days)
            total += weight
            same_place = bool(places & previous_places)
            same_weekday = previous.weekday() == day.weekday()
            if same_place:
                at_place += weight
            if same_weekday:
                weekday += weight
            if same_place and same_weekday:
                place_weekday += weight
        base = self.past(day).features(row, vector, person, guests)
        base[-5] = total
        base[-4] = at_place
        return np.r_[base, weekday, place_weekday, np.eye(7)[day.weekday()]]

    def past(self, day):
        if day not in self.past_cache:
            history = History()
            for previous, row, vector in self.records:
                if previous < day:
                    history.add(row, vector)
            # Only the current query day is needed; avoid retaining many snapshots.
            self.past_cache = {day: history}
        return self.past_cache[day]


def split_rows(rows):
    # Keep equal creation timestamps together; IDs only break ties within a partition.
    stamps = sorted({r["created_at"] for r in rows})
    if len(stamps) < 3:
        raise ValueError("Potrzeba co najmniej 3 różnych czasów utworzenia interakcji")
    a, b = stamps[int(len(stamps) * .6)], stamps[int(len(stamps) * .8)]
    return ([i for i, r in enumerate(rows) if r["created_at"] < a],
            [i for i, r in enumerate(rows) if a <= r["created_at"] < b],
            [i for i, r in enumerate(rows) if r["created_at"] >= b])


def known_guests(people, rng):
    people = sorted(people)
    if len(people) < 2:
        return []
    n = int(rng.integers(1, len(people)))
    return sorted(rng.choice(people, n, replace=False).tolist())


def rank(model, history, row, vector, guests, k, exclude_ids=()):
    import numpy as np
    eligible = history.past(history.date(row)).count if isinstance(history, TemporalHistory) else history.count
    candidates = sorted(set(eligible) - set(guests) - set(exclude_ids))
    if not candidates:
        return []
    x = np.asarray([history.features(row, vector, p, guests) for p in candidates])
    scores = model.predict_proba(x)[:, 1]
    order = sorted(range(len(candidates)), key=lambda i: (-scores[i], candidates[i]))[:k]
    return [candidates[i] for i in order]


def evaluate(model, history, rows, vectors, indices, mode, k):
    import numpy as np
    rng = np.random.default_rng(81)
    recall, ndcg, coverage = [], [], []
    for i in indices:
        truth = set(rows[i]["participants"])
        guests = known_guests(truth, rng) if mode == "guests" else []
        truth -= set(guests)
        if not truth:
            continue
        predicted = rank(model, history, rows[i], vectors[i], guests, k)
        recall.append(len(set(predicted) & truth) / len(truth))
        dcg = sum(1 / np.log2(j + 2) for j, p in enumerate(predicted) if p in truth)
        ideal = sum(1 / np.log2(j + 2) for j in range(min(k, len(truth))))
        ndcg.append(dcg / ideal)
        coverage.append(len(truth & set(history.count)) / len(truth))
    return {"queries": len(recall), "recall@k": float(np.mean(recall)) if recall else None,
            "ndcg@k": float(np.mean(ndcg)) if ndcg else None,
            "candidate_coverage": float(np.mean(coverage)) if coverage else None}


def train(args):
    import joblib
    import numpy as np
    from sklearn.linear_model import LogisticRegression
    from sklearn.ensemble import ExtraTreesClassifier, HistGradientBoostingClassifier
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler
    data = json.loads(Path(args.data).read_text(encoding="utf-8"))
    rows = sorted(data["rows"], key=lambda r: (r["created_at"], r["id"]))
    train_ids, val_ids, test_ids = split_rows(rows)
    encoder = Encoder(args.encoder, args.revision)
    vectors = encoder.encode(rows)
    history = History()
    rng = np.random.default_rng(42)
    samples = {mode: ([], []) for mode in ("context", "guests")}
    # Features only use strictly earlier creation timestamps, including in training.
    for stamp in sorted({rows[i]["created_at"] for i in train_ids}):
        group = [i for i in train_ids if rows[i]["created_at"] == stamp]
        for i in group:
            positives = set(rows[i]["participants"])
            for mode, (x, y) in samples.items():
                guests = known_guests(positives, rng) if mode == "guests" else []
                eligible = set(history.count) - set(guests)
                negative = sorted(eligible - positives)
                limit = max(20, 5 * len(positives))
                if len(negative) > limit:
                    negative = rng.choice(negative, limit, replace=False).tolist()
                for person in sorted((eligible & positives) | set(negative)):
                    x.append(history.features(rows[i], vectors[i], person, guests))
                    y.append(int(person in positives))
        for i in group:
            history.add(rows[i], vectors[i])
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    report = {"k": args.k, "encoder": args.encoder, "revision": encoder.revision,
              "split_sizes": list(map(len, (train_ids, val_ids, test_ids))), "models": {}}
    for mode, (x, y) in samples.items():
        if len(set(y)) < 2:
            raise ValueError("Za mało przykładów pozytywnych/negatywnych do treningu")
        models = {
            "logistic": make_pipeline(StandardScaler(), LogisticRegression(max_iter=1000, random_state=42)),
            "extra_trees": ExtraTreesClassifier(n_estimators=200, min_samples_leaf=3, random_state=42, n_jobs=-1),
            "boosting": HistGradientBoostingClassifier(max_iter=150, max_leaf_nodes=15, l2_regularization=1, random_state=42),
        }
        for name, model in models.items():
            model.fit(np.asarray(x), np.asarray(y))
            key = mode + "_" + name
            report["models"][key] = {
                "validation": evaluate(model, history, rows, vectors, val_ids, mode, args.k),
                "test": evaluate(model, history, rows, vectors, test_ids, mode, args.k),
            }
            joblib.dump({"model": model, "history": history, "mode": mode,
                         "encoder": args.encoder, "revision": encoder.revision,
                         "account_id": data["account_id"], "self_id": data["self_id"]}, output / (key + ".joblib"))
    (output / "metrics.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


def predict(args):
    import joblib
    bundle = joblib.load(args.model)  # Only trusted local artifacts.
    row = json.loads(Path(args.query).read_text(encoding="utf-8"))
    guests = sorted(set(row.get("guest_ids", [])) - {bundle["self_id"]})
    if bundle["mode"] == "context" and guests:
        raise ValueError("Model context nie przyjmuje gości; wybierz model guests")
    vector = Encoder(bundle["encoder"], bundle["revision"]).encode([row])[0]
    people = rank(bundle["model"], bundle["history"], row, vector, guests, args.k)
    print(json.dumps({"human_ids": people}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    export = commands.add_parser("export")
    export.add_argument("--account-id", type=int, required=True)
    export.add_argument("--output", required=True)
    training = commands.add_parser("train")
    training.add_argument("--data", required=True)
    training.add_argument("--output", default="artifacts")
    training.add_argument("--encoder", default=MODEL)
    training.add_argument("--revision", default="main")
    training.add_argument("--k", type=int, default=10)
    prediction = commands.add_parser("predict")
    prediction.add_argument("--model", required=True)
    prediction.add_argument("--query", required=True)
    prediction.add_argument("--k", type=int, default=10)
    args = parser.parse_args()
    if hasattr(args, "k") and args.k < 1:
        parser.error("k musi być dodatnie")
    if args.command == "export":
        try:
            export_data(args.account_id, args.output)
        except Exception as error:
            # Only database operations need connection-error redaction.
            raise SystemExit(
                f"Eksport nie powiódł się ({type(error).__name__}). "
                "Sprawdź DB_URL, DB_USER, DB_PASSWORD i plik wyjściowy; "
                "szczegóły połączenia ukryto."
            ) from None
    elif args.command == "train":
        train(args)
    else:
        predict(args)


if __name__ == "__main__":
    main()

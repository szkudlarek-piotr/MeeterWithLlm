"""Loopback-only model worker. The authenticated Meeter backend is its only client."""
import argparse
import hmac
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import sys
import threading
from collections import OrderedDict
import hashlib
import time


def diagnostic(stage, error=None, started=None):
    """Technical metadata only: never exception messages, source lines or request data."""
    message = "participant-worker stage=" + stage
    if started is not None:
        message += f" elapsed_seconds={time.monotonic() - started:.2f}"
    if error is not None:
        message += " error_type=" + type(error).__name__
        trace = error.__traceback__
        frames = []
        while trace is not None:
            code = trace.tb_frame.f_code
            frames.append(f"{Path(code.co_filename).name}:{trace.tb_lineno}:{code.co_name}")
            trace = trace.tb_next
        message += " frames=" + " > ".join(frames)
    print(message, file=sys.stderr, flush=True)

import neural
import recommend

# CLI-trained joblib files refer to __main__.History, not recommend.History.
sys.modules["__main__"].History = recommend.History

MODEL_IDS = tuple(f"{mode}_{model}" for mode in ("context", "guests")
                  for model in ("logistic", "extra_trees", "boosting"))
# Per-account network trained online from the request history: needs no artifact, always offered, default.
NEURAL_IDS = ("context_neural", "guests_neural")


class Worker:
    def warmup(self):
        """Load shared weights and their encoders before reporting readiness."""
        for model_id in MODEL_IDS:
            if (self.directory / ("shared_" + model_id.split('_', 1)[1] + ".joblib")).is_file():
                bundle = self.bundle(model_id)
                key = (bundle["encoder"], bundle["revision"])
                if key not in self.encoders:
                    self.encoders[key] = self.encoder_factory(*key)
                    self.encoders[key].encode([{"name": "", "places": []}])

    def __init__(self, directory, loader=None, encoder_factory=None, train_budget=neural.TRAIN_BUDGET_SECONDS):
        self.train_budget = train_budget
        self.directory = Path(directory)
        self.bundles = {}
        self.encoders = {}
        self.history_cache = OrderedDict()
        self.neural = OrderedDict()
        if loader is None:
            import joblib
            loader = joblib.load
        self.loader = loader
        self.encoder_factory = encoder_factory or recommend.Encoder

    def bundle(self, model_id):
        if model_id not in MODEL_IDS:
            raise ValueError("Unknown model")
        if model_id not in self.bundles:
            # Fixed allowlist; no client-provided paths. Only trusted local joblib files.
            algorithm = model_id.split('_', 1)[1]
            shared_path = self.directory / ("shared_" + algorithm + ".joblib")
            cache_id = "shared_" + algorithm if shared_path.is_file() else model_id
            if cache_id not in self.bundles:
                self.bundles[cache_id] = self.loader(shared_path if shared_path.is_file() else self.directory / (model_id + ".joblib"))
            self.bundles[model_id] = self.bundles[cache_id]
        return self.bundles[model_id]

    def models(self, account_id, self_id):
        # The online neural model is the default; trained shared models stay selectable, not recommended.
        result = [{"id": model_id, "mode": model_id.split('_', 1)[0], "recommended": True} for model_id in NEURAL_IDS]
        for model_id in MODEL_IDS:
            if not ((self.directory / (model_id + ".joblib")).is_file()
                    or (self.directory / ("shared_" + model_id.split('_', 1)[1] + ".joblib")).is_file()):
                continue
            bundle = self.bundle(model_id)
            if bundle.get("scope") == "shared" or (bundle["account_id"] == account_id and bundle["self_id"] == self_id):
                result.append({"id": model_id, "mode": model_id.split('_', 1)[0],
                               "recommended": False})
        return {"models": result}

    def online_ranker(self, account_id, self_id):
        key = (int(account_id), int(self_id))
        if key not in self.neural:
            path = self.directory / "online" / f"account-{key[0]}-human-{key[1]}.pt"
            self.neural[key] = neural.OnlineRanker(path, seed=key[0])
        self.neural.move_to_end(key)
        while len(self.neural) > 8:
            self.neural.popitem(last=False)
        return self.neural[key]

    def train_neural(self, request):
        """Learns new or changed interactions without ranking. Used by the backend's scheduled catch-up."""
        self_id = request["self_id"]
        rows = request["history"]  # Only the authenticated backend constructs this field.
        if not isinstance(rows, list):
            raise ValueError()
        rows = [{**row, "participants": sorted(set(row["participants"]) - {self_id})} for row in rows]
        ranker = self.online_ranker(request["account_id"], self_id)
        started = time.monotonic()
        trained = ranker.update(rows, budget_seconds=self.train_budget)
        if trained:
            diagnostic(f"neural-update-completed rows={trained}", started=started)
        return rows, ranker, trained

    def suggest_neural(self, request):
        self_id = request["self_id"]
        guests = set(request["guest_ids"]) - {self_id}
        k = request.get("k", 10)
        if not isinstance(k, int) or not 1 <= k <= 20 or len(guests) > 200 or not isinstance(request["history"], list):
            raise ValueError()
        rows, ranker, _ = self.train_neural(request)
        # Context mode ignores guests as evidence, but still excludes the people already selected.
        evidence = guests if request["model"].startswith("guests_") else set()
        return {"human_ids": ranker.rank(rows, request, evidence, k, exclude=guests | {self_id})}

    def suggest(self, request):
        if request["model"] in NEURAL_IDS:
            return self.suggest_neural(request)
        bundle = self.bundle(request["model"])
        shared = bundle.get("scope") == "shared"
        if not shared and (bundle["account_id"] != request["account_id"] or bundle["self_id"] != request["self_id"]):
            raise PermissionError()
        self_id = request["self_id"]
        guests = set(request["guest_ids"]) - {self_id}
        k = request.get("k", 10)
        if not isinstance(k, int) or not 1 <= k <= 20 or len(guests) > 200:
            raise ValueError()
        key = (bundle["encoder"], bundle["revision"])
        if key not in self.encoders:
            self.encoders[key] = self.encoder_factory(*key)
        diagnostic("encode-query-started")
        vector = self.encoders[key].encode([request])[0]
        diagnostic("encode-query-completed")
        # Context mode ignores guests as features, but still excludes selected people.
        feature_guests = sorted(guests) if request["model"].startswith("guests_") else []
        history = self.history(request, key, bundle) if shared else bundle["history"]
        result = recommend.rank(bundle["model"], history, request, vector,
                                feature_guests, k, exclude_ids=guests | {self_id})
        return {"human_ids": result}

    def history(self, request, encoder_key, bundle):
        rows = request["history"]  # Only the authenticated backend constructs this field.
        if not isinstance(rows, list):
            raise ValueError()
        digest = hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest()
        version = bundle.get("schema_version", 2)
        half_life = bundle.get("half_life_days", 365)
        if version not in (2, 3):
            raise ValueError("Unsupported feature schema")
        cache_key = (request["account_id"], request["self_id"], encoder_key, version, half_life, digest)
        if cache_key not in self.history_cache:
            history = recommend.TemporalHistory(half_life) if version == 3 else recommend.History()
            if rows:
                started = time.monotonic()
                diagnostic("encode-history-started")
                vectors = self.encoders[encoder_key].encode(rows)
                diagnostic("encode-history-completed", started=started)
                for row, vector in zip(rows, vectors):
                    history.add({**row, "participants": sorted(set(row["participants"]) - {request["self_id"]})}, vector)
            self.history_cache[cache_key] = history
        self.history_cache.move_to_end(cache_key)
        while len(self.history_cache) > 32:
            self.history_cache.popitem(last=False)
        return self.history_cache[cache_key]


def handler(worker, token):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path != "/health" or not hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + token):
                self.send_error(404)
                return
            body = b'{"ready":true}'
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *_args):
            pass  # No request text, guest IDs, or credentials in logs.

        def do_POST(self):
            if not hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + token):
                self.send_error(403)
                return
            stage = "read-request"
            started = time.monotonic()
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 16 * 1024 * 1024:
                    raise ValueError()
                request = json.loads(self.rfile.read(length))
                if self.path == "/models":
                    stage = "models"
                    diagnostic(stage + "-started")
                    result = worker.models(request["account_id"], request["self_id"])
                elif self.path == "/train":
                    stage = "train"
                    diagnostic(stage + "-started")
                    result = {"trained": worker.train_neural(request)[2]}
                elif self.path == "/suggest":
                    stage = "suggest"
                    diagnostic(stage + "-started")
                    result = worker.suggest(request)
                else:
                    self.send_error(404)
                    return
                body = json.dumps(result).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Cache-Control", "no-store")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                diagnostic(stage + "-completed", started=started)
            except PermissionError as error:
                diagnostic(stage, error, started)
                self.send_error(404)
            except (ValueError, KeyError, TypeError) as error:
                diagnostic(stage, error, started)
                self.send_error(400)
            except Exception as error:
                diagnostic(stage, error, started)
                self.send_error(503)  # Never send artifact contents or exception details.

    return Handler


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifacts", default=str(Path(__file__).parent / "artifacts"))
    parser.add_argument("--port", type=int, default=8092)
    parser.add_argument("--managed", action="store_true", help="Stop when parent stdin closes")
    args = parser.parse_args()
    token = os.environ.get("MEETER_RECOMMENDER_TOKEN", "")
    if len(token) < 32 or not token.isascii():
        raise SystemExit("Ustaw MEETER_RECOMMENDER_TOKEN: minimum 32 znaki ASCII.")
    # Serial inference protects encoder state and bounds simultaneous memory use.
    worker = Worker(args.artifacts)
    diagnostic("warmup-started")
    worker.warmup()
    diagnostic("warmup-completed")
    service = HTTPServer(("127.0.0.1", args.port), handler(worker, token))
    service.timeout = 120
    if args.managed:
        def watch_parent():
            # Backend keeps its stdin pipe open. OS closes it even after an abrupt parent exit.
            sys.stdin.buffer.read()
            service.shutdown()
        threading.Thread(target=watch_parent, daemon=True).start()
    print(f"Modele uczestników: 127.0.0.1:{args.port}", flush=True)
    service.serve_forever()

"""Exercise trained shared models with synthetic input; no database or export access."""
import json
import os
from pathlib import Path

os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
import torch
from server import Worker

torch.set_num_threads(2)
worker = Worker(Path(__file__).parent / "artifacts")
models = worker.models(90001, 90002)["models"]
assert len(models) == 6, "Oczekiwano sześciu wariantów wspólnych modeli"
for item in models:
    assert worker.bundle(item["id"]).get("scope") == "shared"
    request = {"account_id": 90001, "self_id": 90002, "model": item["id"],
               "name": "Planszówki", "start_date": "2026-01-19", "place_text": "Dom", "places": [],
               "guest_ids": [90003] if item["mode"] == "guests" else [],
               "history": [{"name": "Planszówki", "start_date": "2026-01-12", "place_text": "Dom", "places": [],
                            "participants": [90002, 90003, 90004]}]}
    ids = worker.suggest(request)["human_ids"]
    assert set(ids) == ({90004} if item["mode"] == "guests" else {90003, 90004})
print(json.dumps({"shared_artifacts": 3, "variants_verified": len(models), "synthetic_predictions": "OK"}))

# Verify the actual managed HTTP process, readiness and exit when parent pipe closes.
import gc
import secrets
import socket
import subprocess
import sys
import time
from urllib.request import Request, urlopen
del worker
gc.collect()
with socket.socket() as listener:
    listener.bind(("127.0.0.1", 0))
    port = listener.getsockname()[1]
token = secrets.token_urlsafe(32)
environment = {key: value for key, value in os.environ.items() if not key.startswith(("DB_", "NEO4J_"))}
environment["MEETER_RECOMMENDER_TOKEN"] = token
environment["OMP_NUM_THREADS"] = "2"
environment["MKL_NUM_THREADS"] = "2"
child = subprocess.Popen([sys.executable, str(Path(__file__).parent / "server.py"), "--managed", "--port", str(port)],
    stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=environment)
try:
    deadline = time.monotonic() + 90
    while True:
        try:
            with urlopen(Request(f"http://127.0.0.1:{port}/health", headers={"Authorization": "Bearer " + token}), timeout=2) as response:
                assert json.load(response)["ready"]
            break
        except OSError:
            if child.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError("Model service did not become ready") from None
            time.sleep(.5)
    with urlopen(Request(f"http://127.0.0.1:{port}/suggest", data=json.dumps(request).encode(),
            headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"}), timeout=30) as response:
        assert json.load(response)["human_ids"] == [90004]
    child.stdin.close()
    child.wait(timeout=10)
    assert child.returncode == 0
    print('{"managed_http_service":"OK","readiness":"OK","parent_shutdown":"OK"}')
finally:
    if child.poll() is None:
        child.kill()
        child.wait()

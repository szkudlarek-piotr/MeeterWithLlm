import unittest
from unittest.mock import patch
import numpy as np
from recommend import History
from server import Worker, diagnostic
import io
from contextlib import redirect_stderr


class Model:
    def predict_proba(self, x):
        return np.tile([.5, .5], (len(x), 1))


class Encoder:
    def __init__(self, *_args):
        pass

    def encode(self, _rows):
        return np.tile([1., 0.], (len(_rows), 1))


class WorkerTests(unittest.TestCase):
    def test_diagnostic_omits_exception_message_and_local_values(self):
        output = io.StringIO()
        secret = 'synthetic-private-request'
        with redirect_stderr(output):
            try:
                raise ValueError(secret)
            except ValueError as error:
                diagnostic('suggest', error)
        self.assertIn('error_type=ValueError', output.getvalue())
        self.assertIn('test_server.py:', output.getvalue())
        self.assertNotIn(secret, output.getvalue())
        self.assertNotIn('raise ValueError', output.getvalue())

    def setUp(self):
        history = History()
        history.add({"participants": [1, 2, 3]}, np.array([1., 0.]))
        self.bundle = {"account_id": 10, "self_id": 1, "mode": "context", "encoder": "fake",
                       "revision": "fake", "model": Model(), "history": history}
        self.worker = Worker(".", loader=lambda _: self.bundle, encoder_factory=Encoder)
        self.request = {"model": "context_logistic", "account_id": 10, "self_id": 1,
                        "name": "Example", "guest_ids": [2], "places": []}

    def test_context_excludes_guests_and_self(self):
        self.assertEqual(self.worker.suggest(self.request), {"human_ids": [3]})

    def test_schema_three_uses_temporal_history_and_query_date(self):
        from recommend import TemporalHistory
        shared = {"scope": "shared", "schema_version": 3, "half_life_days": 7,
                  "model": Model(), "encoder": "fake", "revision": "fake"}
        worker = Worker(".", loader=lambda _: shared, encoder_factory=Encoder)
        request = {**self.request, "start_date": "2026-01-19",
                   "history": [{"start_date": "2026-01-12", "participants": [1, 3]},
                               {"start_date": "2026-01-20", "participants": [99]}]}
        with patch("pathlib.Path.is_file", lambda path: path.name == "shared_logistic.joblib"):
            self.assertEqual(worker.suggest(request), {"human_ids": [3]})
        self.assertIsInstance(next(iter(worker.history_cache.values())), TemporalHistory)

    def test_foreign_account_cannot_use_artifact(self):
        with self.assertRaises(PermissionError):
            self.worker.suggest({**self.request, "account_id": 11})
        with self.assertRaises(PermissionError):
            self.worker.suggest({**self.request, "self_id": 99})

    def test_no_path_injection(self):
        with self.assertRaises(ValueError):
            self.worker.bundle("../../other")

    def test_models_are_scoped_to_account_and_encoder_is_cached(self):
        with patch("pathlib.Path.is_file", lambda path: path.name == "context_logistic.joblib"):
            self.assertEqual(len(self.worker.models(10, 1)["models"]), 1)
            self.assertEqual(self.worker.models(11, 1), {"models": []})
        self.worker.suggest(self.request)
        first = self.worker.encoders.copy()
        self.worker.suggest(self.request)
        self.assertEqual(self.worker.encoders, first)

    def test_shared_weights_use_account_scoped_runtime_history(self):
        shared = {"scope": "shared", "schema_version": 2, "model": Model(), "encoder": "fake", "revision": "fake"}
        self.worker = Worker(".", loader=lambda _: shared, encoder_factory=Encoder)
        with patch("pathlib.Path.is_file", lambda path: path.name == "shared_logistic.joblib"):
            self.assertEqual(len(self.worker.models(10, 1)["models"]), 2)
            self.assertEqual(len(self.worker.models(11, 2)["models"]), 2)
            first = {**self.request, "history": [{"participants": [1, 2, 3], "places": []}]}
            second = {**first, "account_id": 11, "self_id": 2,
                      "history": [{"participants": [2, 99], "places": []}]}
            self.assertEqual(self.worker.suggest(first), {"human_ids": [3]})
            self.assertEqual(self.worker.suggest(second), {"human_ids": [99]})
            self.assertEqual(self.worker.suggest({**first, "history": []}), {"human_ids": []})
            # Both modes reference the same estimator, not a duplicate artifact.
            self.assertIs(self.worker.bundle("context_logistic"), self.worker.bundle("guests_logistic"))

    def test_recommended_algorithm_is_read_without_loading_training_data(self):
        shared = {"scope": "shared"}
        worker = Worker(".", loader=lambda _: shared, encoder_factory=Encoder)
        with patch("pathlib.Path.is_file", lambda path: path.name in {"shared_metrics.json", "shared_logistic.joblib"}), \
                patch("pathlib.Path.read_text", return_value='{"recommended_model":"logistic"}'):
            self.assertTrue(all(model["recommended"] for model in worker.models(10, 1)["models"]))


if __name__ == "__main__":
    unittest.main()

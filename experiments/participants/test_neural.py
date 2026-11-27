from datetime import date
import tempfile
import unittest
from pathlib import Path

from neural import Index, OnlineRanker, dice, grams
from server import Worker


def row(id, day, name, people, places=(), type="MEETING"):
    return {"id": id, "start_date": day, "name": name, "place_text": "", "type": type,
            "participants": list(people), "places": [{"id": i, "name": f"p{i}"} for i in places]}


def history():
    """Synthetic: 2 and 3 always come to 'Grill'; 4 and 6 always, 5 every other time, to 'Kino'."""
    rows = []
    for n in range(40):
        day = f"2026-{1 + n // 28:02d}-{1 + n % 28:02d}"
        rows.append(row(2 * n, day, "Grill u Kasi", [2, 3], places=[7]))
        rows.append(row(2 * n + 1, day, "Kino wieczorem", [4, 5, 6] if n % 2 else [4, 6], places=[8]))
    return rows


class NeuralTests(unittest.TestCase):
    def test_text_similarity_handles_polish_inflection(self):
        self.assertGreater(dice(grams("Spotkanie u Kasi"), grams("Spotkanie u Kasia")), .7)
        self.assertEqual(grams("Łódź"), grams("lodz"))

    def test_features_use_only_earlier_days(self):
        index = Index([row(1, "2026-02-01", "A", [2]), row(2, "2026-03-01", "A", [3])])
        self.assertEqual(index.candidates(date(2026, 2, 1)), [])
        self.assertEqual(index.candidates(date(2026, 2, 2)), [2])

    def test_learns_context_from_online_updates(self):
        with tempfile.TemporaryDirectory() as folder:
            ranker = OnlineRanker(Path(folder) / "a.pt", seed=1)
            for _ in range(3):
                ranker.update(history(), budget_seconds=60)
                ranker.trained.clear()
            query = row(None, "2026-03-20", "Grill u Kasi", [], places=[7])
            self.assertEqual(set(ranker.rank(history(), query, k=2)), {2, 3})
            query = row(None, "2026-03-20", "Kino wieczorem", [], places=[8])
            self.assertEqual(set(ranker.rank(history(), query, k=3)), {4, 5, 6})

    def test_update_is_incremental_and_persists(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "a.pt"
            ranker = OnlineRanker(path, seed=1)
            self.assertEqual(ranker.update(history()), 80)
            self.assertEqual(ranker.update(history()), 0)
            more = history() + [row(999, "2026-03-15", "Grill u Kasi", [2, 3])]
            self.assertEqual(ranker.update(more), 1)
            changed = [{**r, "participants": [2, 3, 5]} if r["id"] == 999 else r for r in more]
            self.assertEqual(ranker.update(changed), 1)
            reloaded = OnlineRanker(path, seed=1)
            self.assertEqual(reloaded.steps, ranker.steps)
            self.assertEqual(reloaded.update(changed), 0)

    def test_future_rows_wait_until_their_day(self):
        ranker = OnlineRanker(seed=1)
        self.assertEqual(ranker.update([row(1, "2999-01-01", "A", [2])]), 0)

    def test_selected_people_are_excluded(self):
        ranker = OnlineRanker(seed=1)
        ranker.update(history(), budget_seconds=60)
        query = row(None, "2026-03-20", "Grill u Kasi", [])
        self.assertNotIn(2, ranker.rank(history(), query, guests=[2], k=10, exclude=[2]))

    def test_worker_trains_per_account_and_switches_mode(self):
        with tempfile.TemporaryDirectory() as folder:
            worker = Worker(folder, loader=lambda _: {}, encoder_factory=lambda *_: None)
            request = {"model": "guests_neural", "account_id": 10, "self_id": 1, "name": "Kino wieczorem",
                       "guest_ids": [4], "places": [{"id": 8, "name": "p8"}], "start_date": "2026-03-20",
                       "history": [{**r, "participants": r["participants"] + [1]} for r in history()], "k": 2}
            result = worker.suggest(request)["human_ids"]
            self.assertNotIn(4, result)
            self.assertNotIn(1, result)
            self.assertTrue((Path(folder) / "online" / "account-10-human-1.pt").is_file())
            self.assertFalse((Path(folder) / "online" / "account-11-human-1.pt").exists())
            self.assertNotIn(4, worker.suggest({**request, "model": "context_neural"})["human_ids"])
            with self.assertRaises(ValueError):
                worker.suggest({**request, "k": 99})


if __name__ == "__main__":
    unittest.main()

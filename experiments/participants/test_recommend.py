import unittest
import numpy as np
from recommend import History, TemporalHistory, evaluate, known_guests, rank, split_rows


class FakeModel:
    def predict_proba(self, x):
        # Constant scores exercise deterministic tie breaking and exclusions.
        return np.tile([.5, .5], (len(x), 1))


class RecommendationTests(unittest.TestCase):
    def test_temporal_counts_decay_without_penalizing_other_places(self):
        history = TemporalHistory(half_life_days=7)
        vector = np.array([1., 0.])
        # Both Mondays, one and two half-lives before the requested Monday.
        history.add({"start_date": "2026-01-05", "participants": [2], "places": [{"id": 7}]}, vector)
        history.add({"start_date": "2026-01-12", "participants": [2], "places": [{"id": 7}]}, vector)
        query = {"start_date": "2026-01-19", "places": [{"id": 7}]}
        before = history.features(query, vector, 2, [])
        self.assertAlmostEqual(before[5], .75)
        self.assertAlmostEqual(before[6], .75)
        self.assertAlmostEqual(before[10], .75)
        self.assertAlmostEqual(before[11], .75)
        np.testing.assert_array_equal(before[12:], [1, 0, 0, 0, 0, 0, 0])
        history.add({"start_date": "2026-01-13", "participants": [2], "places": [{"id": 8}]}, vector)
        after = history.features(query, vector, 2, [])
        self.assertGreater(after[5], before[5])
        self.assertEqual(after[6], before[6])
        self.assertEqual(after[10], before[10])
        self.assertEqual(after[11], before[11])

    def test_temporal_history_excludes_same_day_and_future_people(self):
        history = TemporalHistory()
        vector = np.array([1., 0.])
        history.add({"start_date": "2026-01-19", "participants": [2]}, vector)
        history.add({"start_date": "2026-01-20", "participants": [3]}, vector)
        query = {"start_date": "2026-01-19"}
        self.assertEqual(rank(FakeModel(), history, query, vector, [], 10), [])
        self.assertEqual(history.features(query, vector, 2, [3])[5], 0)

    def test_temporal_counts_deduplicate_people_and_multiplace_events(self):
        history = TemporalHistory(7)
        vector = np.array([1., 0.])
        history.add({"start_date": "2026-01-12", "participants": [2, 2], "places": [{"id": 7}, {"id": 8}]}, vector)
        features = history.features({"start_date": "2026-01-19", "places": [{"id": 7}, {"id": 8}]}, vector, 2, [])
        self.assertEqual(features[5], .5)
        self.assertEqual(features[6], .5)

    def test_small_import_history_can_be_split(self):
        rows = [{"created_at": str(i), "id": i} for i in range(7)]
        parts = split_rows(rows)
        self.assertEqual(list(map(len, parts)), [4, 1, 2])
        self.assertTrue(all(split_rows(rows[:3])))
        with self.assertRaises(ValueError):
            split_rows(rows[:2])

    def test_timestamp_groups_do_not_cross_partitions(self):
        rows = [{"created_at": f"2026-01-{i:02}", "id": j}
                for i in range(1, 21) for j in range(2)]
        partitions = split_rows(rows)
        stamps = [{rows[i]["created_at"] for i in ids} for ids in partitions]
        self.assertFalse(stamps[0] & stamps[1] or stamps[1] & stamps[2] or stamps[0] & stamps[2])
        self.assertEqual(sum(map(len, partitions)), len(rows))

    def test_mask_leaves_a_target_and_is_order_invariant(self):
        a = known_guests({5, 2, 9}, np.random.default_rng(42))
        b = known_guests([9, 5, 2], np.random.default_rng(42))
        self.assertEqual(a, b)
        self.assertTrue(0 < len(a) < 3)
        self.assertEqual(known_guests({5}, np.random.default_rng(42)), [])

    def test_features_use_prior_history_and_guest_affinity(self):
        history = History()
        vector = np.array([1., 0.])
        row = {"participants": [2, 3], "places": [{"id": 7}]}
        before = history.features(row, vector, 2, [3])
        self.assertEqual(before[-1], 0)
        self.assertEqual(history.n, 0)
        history.add(row, vector)
        after = history.features(row, vector, 2, [3])
        self.assertEqual(after[-1], 1)
        self.assertEqual(after[-5], 1)  # historical popularity
        np.testing.assert_array_equal(after[:2], vector)
        np.testing.assert_array_equal(history.features(row, vector, 2, [] )[:2], vector)

    def test_ranking_excludes_known_and_never_sees_new_people(self):
        history = History()
        vector = np.array([1., 0.])
        history.add({"participants": [3, 2, 4]}, vector)
        self.assertEqual(rank(FakeModel(), history, {}, vector, [2], 10), [3, 4])
        self.assertEqual(rank(FakeModel(), history, {}, vector, [2, 3, 4], 10), [])

    def test_unseen_targets_stay_in_recall_denominator(self):
        history = History()
        vector = np.array([1., 0.])
        history.add({"participants": [2]}, vector)
        metrics = evaluate(FakeModel(), history, [{"participants": [2, 99]}],
                           np.array([vector]), [0], "context", 10)
        self.assertEqual(metrics["recall@k"], .5)
        self.assertEqual(metrics["candidate_coverage"], .5)


if __name__ == "__main__":
    unittest.main()

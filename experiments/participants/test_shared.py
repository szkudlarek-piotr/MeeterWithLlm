import unittest
import numpy as np
from shared import temporal_split, training_samples, measure


class ConstantModel:
    def predict_proba(self, x):
        return np.tile([.5, .5], (len(x), 1))


def row(i, account, people):
    return {"id": i, "account_id": account, "self_id": account,
            "start_date": f"2020-01-{i:02d}", "participants": people, "places": []}


class SharedTrainingTests(unittest.TestCase):
    def test_splits_by_record_count_without_splitting_days(self):
        rows = [row(i, 1, []) for i in range(1, 21) for _ in range(2)]
        parts = temporal_split(rows, "start_date")
        self.assertEqual(list(map(len, parts)), [24, 8, 8])
        days = [{rows[i]["start_date"] for i in part} for part in parts]
        self.assertFalse(days[0] & days[1] or days[1] & days[2])

    def test_candidate_features_do_not_cross_accounts_or_include_self(self):
        rows = [row(1, 1, [1, 10, 11]), row(2, 2, [2, 99]), row(3, 1, [1, 10])]
        vectors = np.array([[1., 0.]] * 3)
        x, y, history = training_samples(rows, vectors, range(3), "start_date")
        self.assertEqual(set(history[1].count), {10, 11})
        self.assertEqual(set(history[2].count), {99})
        self.assertEqual(set(y), {0, 1})
        self.assertEqual(len(x), 2)  # No pairs for first-time account 2, despite account 1's history.

    def test_same_day_labels_do_not_enter_each_others_features(self):
        rows = [row(1, 1, [10, 11]), row(2, 1, [10]), row(2, 1, [11])]
        vectors = np.array([[1., 0.]] * 3)
        x, _, _ = training_samples(rows, vectors, range(3), "start_date")
        np.testing.assert_allclose(x[:, 5], [2 ** (-1 / 365)] * 4)

    def test_unknown_account_is_counted_as_miss(self):
        rows = [row(1, 999, [10])]
        result = measure(ConstantModel(), {}, rows, np.array([[1., 0.]]), [0], "context", 10)
        self.assertEqual(result["recall@k"], 0)
        self.assertEqual(result["candidate_coverage"], 0)


if __name__ == "__main__":
    unittest.main()

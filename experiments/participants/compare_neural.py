"""Run by the project owner: scores the per-account online neural ranker on the same temporal split,
masked-guest protocol and metrics as shared training, and prints AGGREGATE numbers only (no rows, names
or IDs). Reads the database through a read-only in-memory snapshot, like train-shared.ps1, and writes no
file: the networks trained here live only in memory. Compare its recall@k / ndcg@k with the
validation/test numbers of the trained shared models yourself."""
import argparse
import json

from database import load_dataset
from neural import backtest

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--split-key", choices=("start_date", "created_at"), default="start_date")
parser.add_argument("--k", type=int, default=10)
arguments = parser.parse_args()
if arguments.k < 1:
    parser.error("k musi być dodatnie")
print(json.dumps(backtest(load_dataset(), arguments.split_key, arguments.k), indent=2))

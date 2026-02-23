#!/usr/bin/env python3

import argparse
import json
import math
import sys
from pathlib import Path

STRATEGIES = [
    {"key": "optimistic", "label": "Optimistic", "color": "#4C78A8"},
    {"key": "pessimistic", "label": "Pessimistic", "color": "#F58518"},
    {"key": "separated", "label": "Separated", "color": "#54A24B"},
    {"key": "atomic", "label": "Atomic", "color": "#E45756"},
]

PROFILES = [
    {"name": "read-committed", "prefix": "rc", "title": "READ COMMITTED", "output": "single-rc-4strategy.png"},
    {"name": "repeatable-read", "prefix": "rr", "title": "REPEATABLE READ", "output": "single-rr-4strategy.png"},
]


def fatal(message: str) -> None:
    print(f"[FATAL] {message}", file=sys.stderr)
    raise SystemExit(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Plot single scenario 4-strategy comparison (optimistic/pessimistic/separated/atomic)."
    )
    parser.add_argument(
        "--root",
        default="performance/k6/results/single",
        help="Root directory that contains single summary JSON files.",
    )
    parser.add_argument(
        "--output-dir",
        default="performance/k6/results/single/plots",
        help="Output directory for PNG files.",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=160,
        help="Output image DPI (default: 160).",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict:
    if not path.is_file():
        fatal(f"Missing summary file: {path}")
    try:
        with path.open("r", encoding="utf-8") as handle:
            return json.load(handle)
    except json.JSONDecodeError as exc:
        fatal(f"Invalid JSON at {path}: {exc}")


def get_required(data: dict, path: list[str], file_path: Path):
    cursor = data
    visited = []
    for key in path:
        visited.append(key)
        if not isinstance(cursor, dict) or key not in cursor:
            dotted = ".".join(visited)
            fatal(f"Missing required field `{dotted}` in {file_path}")
        cursor = cursor[key]
    return cursor


def get_required_number(data: dict, path: list[str], file_path: Path) -> float:
    value = get_required(data, path, file_path)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        dotted = ".".join(path)
        fatal(f"Field `{dotted}` must be numeric in {file_path}")
    number = float(value)
    if math.isnan(number) or math.isinf(number):
        dotted = ".".join(path)
        fatal(f"Field `{dotted}` has invalid numeric value in {file_path}")
    return number


def get_required_bool(data: dict, path: list[str], file_path: Path) -> bool:
    value = get_required(data, path, file_path)
    if not isinstance(value, bool):
        dotted = ".".join(path)
        fatal(f"Field `{dotted}` must be boolean in {file_path}")
    return value


def load_profile_rows(root: Path, prefix: str) -> list[dict]:
    rows = []
    for strategy in STRATEGIES:
        file_path = root / f"{prefix}-{strategy['key']}.summary.json"
        summary = load_json(file_path)

        rows.append(
            {
                "strategy": strategy["key"],
                "label": strategy["label"],
                "color": strategy["color"],
                "scenario": get_required(summary, ["scenario"], file_path),
                "p95": get_required_number(summary, ["latencyMs", "p95"], file_path),
                "avg": get_required_number(summary, ["latencyMs", "avg"], file_path),
                "throughput": get_required_number(summary, ["throughputRps"], file_path),
                "success_rate": get_required_number(summary, ["ratios", "successRate"], file_path),
                "pass": get_required_bool(summary, ["domainAssertions", "pass"], file_path),
            }
        )
    return rows


def add_bar_labels(ax, bars, values: list[float], format_pattern: str) -> None:
    max_value = max(values) if values else 0.0
    y_offset = max_value * 0.02 if max_value > 0 else 0.05
    for bar, value in zip(bars, values):
        ax.text(
            bar.get_x() + (bar.get_width() / 2),
            bar.get_height() + y_offset,
            format_pattern.format(value),
            ha="center",
            va="bottom",
            fontsize=9,
            color="#222222",
        )


def mark_failed_bars(bars, rows: list[dict]) -> bool:
    has_failure = False
    for bar, row in zip(bars, rows):
        if not row["pass"]:
            has_failure = True
            bar.set_edgecolor("#D62728")
            bar.set_linewidth(2.6)
            bar.set_hatch("//")
    return has_failure


def make_plot(rows: list[dict], profile_title: str, output_path: Path, dpi: int) -> None:
    try:
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ModuleNotFoundError:
        fatal("matplotlib is required. Install it with `python3 -m pip install matplotlib`.")

    labels = [row["label"] for row in rows]
    colors = [row["color"] for row in rows]
    p95_values = [row["p95"] for row in rows]
    throughput_values = [row["throughput"] for row in rows]

    fig, axes = plt.subplots(1, 2, figsize=(13, 5.4), constrained_layout=True)
    p95_ax, throughput_ax = axes

    p95_bars = p95_ax.bar(labels, p95_values, color=colors, edgecolor="#333333", linewidth=1.0)
    throughput_bars = throughput_ax.bar(labels, throughput_values, color=colors, edgecolor="#333333", linewidth=1.0)

    failed_p95 = mark_failed_bars(p95_bars, rows)
    failed_throughput = mark_failed_bars(throughput_bars, rows)

    p95_ax.set_title("Latency p95 (ms)")
    p95_ax.set_ylabel("ms")
    p95_ax.grid(axis="y", linestyle="--", alpha=0.35)

    throughput_ax.set_title("Throughput (req/s)")
    throughput_ax.set_ylabel("req/s")
    throughput_ax.grid(axis="y", linestyle="--", alpha=0.35)

    for axis in (p95_ax, throughput_ax):
        axis.tick_params(axis="x", rotation=12)

    add_bar_labels(p95_ax, p95_bars, p95_values, "{:.2f}")
    add_bar_labels(throughput_ax, throughput_bars, throughput_values, "{:.2f}")

    all_pass = all(row["pass"] for row in rows)
    fig.suptitle(
        f"Single 4-Strategy Comparison - {profile_title} (allPass={all_pass})",
        fontsize=12,
    )

    if failed_p95 or failed_throughput:
        fig.text(
            0.01,
            0.01,
            "red border + hatch = domainAssertions.pass=false",
            fontsize=9,
            color="#D62728",
        )

    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=dpi)
    plt.close(fig)


def main() -> int:
    args = parse_args()
    if args.dpi <= 0:
        fatal("--dpi must be a positive integer")

    root = Path(args.root)
    output_dir = Path(args.output_dir)
    if not root.is_dir():
        fatal(f"Root directory not found: {root}")

    for profile in PROFILES:
        rows = load_profile_rows(root, profile["prefix"])
        output_path = output_dir / profile["output"]
        make_plot(rows, profile["title"], output_path, args.dpi)
        print(f"[INFO] Plot saved: {output_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

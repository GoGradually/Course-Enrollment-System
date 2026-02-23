#!/usr/bin/env python3

import argparse
import json
import math
import statistics
import sys
from pathlib import Path

SCENARIOS = [
    {
        "label": "RC Atomic",
        "profile": "read-committed",
        "scenario": "rc-atomic-multi",
    },
    {
        "label": "RC Separated",
        "profile": "read-committed",
        "scenario": "rc-separated-multi",
    },
    {
        "label": "RR Atomic",
        "profile": "repeatable-read",
        "scenario": "rr-atomic-multi",
    },
    {
        "label": "RR Separated",
        "profile": "repeatable-read",
        "scenario": "rr-separated-multi",
    },
]


def fatal(message: str) -> None:
    print(f"[FATAL] {message}", file=sys.stderr)
    raise SystemExit(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Plot repeatable k6 comparison (RC/RR atomic vs separated)."
    )
    parser.add_argument(
        "--root",
        default="performance/k6/results/repeatable/aggregated",
        help="Root directory that contains aggregated profile folders.",
    )
    parser.add_argument(
        "--output",
        default="performance/k6/results/repeatable/plots/repeatable-rc-rr-atomic-vs-separated.png",
        help="Output PNG file path.",
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
        fatal(f"Missing aggregate file: {path}")
    try:
        with path.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
    except json.JSONDecodeError as exc:
        fatal(f"Invalid JSON at {path}: {exc}")
    return data


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


def ensure_number_list(value, field: str, file_path: Path) -> list[float]:
    if not isinstance(value, list):
        fatal(f"Field `{field}` must be a list in {file_path}")
    if len(value) == 0:
        fatal(f"Field `{field}` must not be empty in {file_path}")

    normalized = []
    for idx, item in enumerate(value, start=1):
        if isinstance(item, bool) or not isinstance(item, (int, float)):
            fatal(f"Field `{field}` has non-numeric value at index {idx} in {file_path}")
        number = float(item)
        if math.isnan(number) or math.isinf(number):
            fatal(f"Field `{field}` has invalid numeric value at index {idx} in {file_path}")
        normalized.append(number)
    return normalized


def load_scenario_series(root: Path) -> list[dict]:
    rows = []
    for scenario in SCENARIOS:
        file_path = root / scenario["profile"] / f"{scenario['scenario']}.aggregate.json"
        aggregate = load_json(file_path)

        latency_values = ensure_number_list(
            get_required(aggregate, ["metrics", "latencyMsP95", "values"], file_path),
            "metrics.latencyMsP95.values",
            file_path,
        )
        throughput_values = ensure_number_list(
            get_required(aggregate, ["metrics", "throughputRps", "values"], file_path),
            "metrics.throughputRps.values",
            file_path,
        )
        run_count = get_required(aggregate, ["input", "runCount"], file_path)
        stable = get_required(aggregate, ["stability", "pass"], file_path)

        if isinstance(run_count, bool) or not isinstance(run_count, int):
            fatal(f"Field `input.runCount` must be an integer in {file_path}")
        if not isinstance(stable, bool):
            fatal(f"Field `stability.pass` must be a boolean in {file_path}")

        rows.append(
            {
                "label": scenario["label"],
                "profile": scenario["profile"],
                "scenario": scenario["scenario"],
                "run_count": run_count,
                "stable": stable,
                "latency": latency_values,
                "throughput": throughput_values,
            }
        )
    return rows


def build_profile_summary(rows: list[dict], profile: str) -> str:
    profile_rows = [row for row in rows if row["profile"] == profile]
    if len(profile_rows) != 2:
        fatal(f"Expected 2 scenarios for profile {profile}, found {len(profile_rows)}")

    run_counts = sorted({row["run_count"] for row in profile_rows})
    run_text = str(run_counts[0]) if len(run_counts) == 1 else "mixed"

    atomic_row = next(row for row in profile_rows if "Atomic" in row["label"])
    separated_row = next(row for row in profile_rows if "Separated" in row["label"])
    atomic_state = "PASS" if atomic_row["stable"] else "FAIL"
    separated_state = "PASS" if separated_row["stable"] else "FAIL"
    short_name = "RC" if profile == "read-committed" else "RR"
    return f"{short_name}(runs={run_text}, atomic={atomic_state}, separated={separated_state})"


def annotate_medians(ax, values: list[list[float]]) -> None:
    y_min, y_max = ax.get_ylim()
    y_offset = (y_max - y_min) * 0.03 if y_max > y_min else 0.1

    for index, series in enumerate(values, start=1):
        median = statistics.median(series)
        ax.text(
            index,
            median + y_offset,
            f"{median:.1f}",
            ha="center",
            va="bottom",
            fontsize=9,
            color="#222222",
        )


def make_plot(rows: list[dict], output_path: Path, dpi: int) -> None:
    try:
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ModuleNotFoundError:
        fatal("matplotlib is required. Install it with `python3 -m pip install matplotlib`.")

    labels = [row["label"] for row in rows]
    latency_values = [row["latency"] for row in rows]
    throughput_values = [row["throughput"] for row in rows]

    atomic_color = "#4C78A8"
    separated_color = "#F58518"
    colors = [atomic_color if "Atomic" in label else separated_color for label in labels]

    fig, axes = plt.subplots(1, 2, figsize=(14, 6), constrained_layout=True)
    latency_ax, throughput_ax = axes

    for ax, series, title, ylabel in [
        (latency_ax, latency_values, "Latency Comparison (p95)", "Latency (ms)"),
        (
                throughput_ax,
                throughput_values,
                "Throughput Comparison",
                "Throughput (req/s)",
        ),
    ]:
        box = ax.boxplot(series, patch_artist=True)
        for patch, color in zip(box["boxes"], colors):
            patch.set_facecolor(color)
            patch.set_alpha(0.55)
            patch.set_edgecolor("#333333")
        for median in box["medians"]:
            median.set_color("#333333")
            median.set_linewidth(2.0)

        ax.set_title(title)
        ax.set_ylabel(ylabel)
        ax.set_xticks(range(1, len(labels) + 1))
        ax.set_xticklabels(labels, rotation=18, ha="right")
        ax.grid(axis="y", linestyle="--", alpha=0.35)
        annotate_medians(ax, series)

    rc_summary = build_profile_summary(rows, "read-committed")
    rr_summary = build_profile_summary(rows, "repeatable-read")
    fig.suptitle(
        "Repeatable Atomic vs Separated (RC + RR)\n"
        f"{rc_summary} | {rr_summary}",
        fontsize=12,
    )

    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=dpi)
    plt.close(fig)


def main() -> int:
    args = parse_args()
    if args.dpi <= 0:
        fatal("--dpi must be a positive integer")

    root = Path(args.root)
    output = Path(args.output)

    rows = load_scenario_series(root)
    make_plot(rows, output, args.dpi)
    print(f"[INFO] Plot saved: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

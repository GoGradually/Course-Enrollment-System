#!/usr/bin/env python3

import argparse
import json
import math
import sys
from pathlib import Path

SETS = {
    "spread": {
        "title": "RC Single Student Multi-Course (Spread)",
        "output": "single-student-rc-spread-atomic-vs-separated.png",
        "files": {
            "atomic": "rc-atomic-single-student-multi.summary.json",
            "separated": "rc-separated-single-student-multi.summary.json",
        },
    },
    "conflict": {
        "title": "RC Single Student Multi-Course (Conflict)",
        "output": "single-student-rc-conflict-atomic-vs-separated.png",
        "files": {
            "atomic": "rc-atomic-single-student-conflict.summary.json",
            "separated": "rc-separated-single-student-conflict.summary.json",
        },
    },
}

STRATEGIES = [
    {"key": "atomic", "label": "Atomic", "color": "#4C78A8"},
    {"key": "separated", "label": "Separated", "color": "#F58518"},
]

STATUS_STACK = [
    {"key": "status201", "label": "201", "color": "#54A24B"},
    {"key": "status409", "label": "409", "color": "#E45756"},
    {"key": "status422", "label": "422", "color": "#B279A2"},
]

RULE_VIOLATION_STACK = [
    {"key": "scheduleConflict422", "label": "Schedule", "color": "#EECA3B"},
    {"key": "creditLimit422", "label": "CreditLimit", "color": "#72B7B2"},
    {"key": "capacity422", "label": "Capacity", "color": "#FF9DA6"},
    {"key": "other422", "label": "Other", "color": "#9D755D"},
]


def fatal(message: str) -> None:
    print(f"[FATAL] {message}", file=sys.stderr)
    raise SystemExit(1)


def warn(message: str) -> None:
    print(f"[WARN] {message}", file=sys.stderr)


def parse_bool(raw: str, field_name: str) -> bool:
    normalized = raw.strip().lower()
    if normalized in {"1", "true", "yes", "y", "on"}:
        return True
    if normalized in {"0", "false", "no", "n", "off"}:
        return False
    fatal(f"{field_name} must be true/false (or 1/0), got: {raw}")
    return False


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Plot single-student k6 comparison (atomic vs separated)."
    )
    parser.add_argument(
        "--root",
        default="performance/k6/results/single-student",
        help="Root directory that contains single-student summary JSON files.",
    )
    parser.add_argument(
        "--set",
        dest="target_set",
        choices=["spread", "conflict", "all"],
        default="all",
        help="Comparison set to plot (default: all).",
    )
    parser.add_argument(
        "--output-dir",
        default="performance/k6/results/single-student/plots",
        help="Output directory for PNG files.",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=160,
        help="Output image DPI (default: 160).",
    )
    parser.add_argument(
        "--strict-missing",
        default="false",
        help="Fail immediately on missing files (true/false, default: false).",
    )
    args = parser.parse_args()
    args.strict_missing = parse_bool(args.strict_missing, "--strict-missing")
    return args


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
    visited: list[str] = []
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


def get_set_file_paths(root: Path, set_name: str) -> dict[str, Path]:
    set_def = SETS[set_name]
    return {key: root / filename for key, filename in set_def["files"].items()}


def find_missing_paths(file_paths: dict[str, Path]) -> list[Path]:
    return [path for path in file_paths.values() if not path.is_file()]


def load_rows(file_paths: dict[str, Path]) -> list[dict]:
    rows = []
    for strategy in STRATEGIES:
        key = strategy["key"]
        path = file_paths[key]
        summary = load_json(path)

        rows.append(
            {
                "key": key,
                "label": strategy["label"],
                "color": strategy["color"],
                "scenario": get_required(summary, ["scenario"], path),
                "latency_avg": get_required_number(summary, ["latencyMs", "avg"], path),
                "latency_p95": get_required_number(summary, ["latencyMs", "p95"], path),
                "throughput": get_required_number(summary, ["throughputRps"], path),
                "success_rate": get_required_number(summary, ["ratios", "successRate"], path),
                "status201": get_required_number(summary, ["totals", "status201"], path),
                "status409": get_required_number(summary, ["totals", "status409"], path),
                "status422": get_required_number(summary, ["totals", "status422"], path),
                "unexpected": get_required_number(summary, ["totals", "unexpected"], path),
                "scheduleConflict422": get_required_number(summary, ["errorCodes", "scheduleConflict422"], path),
                "creditLimit422": get_required_number(summary, ["errorCodes", "creditLimit422"], path),
                "capacity422": get_required_number(summary, ["errorCodes", "capacity422"], path),
                "other422": get_required_number(summary, ["errorCodes", "other422"], path),
                "pass": get_required_bool(summary, ["domainAssertions", "pass"], path),
            }
        )
    return rows


def add_bar_labels(ax, bars, values: list[float], fmt: str) -> None:
    max_value = max(values) if values else 0.0
    y_offset = max_value * 0.025 if max_value > 0 else 0.05
    for bar, value in zip(bars, values):
        ax.text(
            bar.get_x() + (bar.get_width() / 2),
            bar.get_height() + y_offset,
            fmt.format(value),
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
            bar.set_linewidth(2.5)
            bar.set_hatch("//")
    return has_failure


def mark_failed_bar_groups(*bar_groups, rows: list[dict]) -> bool:
    has_failure = False
    for bars in bar_groups:
        if mark_failed_bars(bars, rows):
            has_failure = True
    return has_failure


def make_plot(rows: list[dict], set_name: str, output_path: Path, dpi: int) -> None:
    try:
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ModuleNotFoundError:
        fatal("matplotlib is required. Install it with `python3 -m pip install matplotlib`.")

    labels = [row["label"] for row in rows]
    x_positions = list(range(len(rows)))
    colors = [row["color"] for row in rows]

    fig, axes = plt.subplots(2, 2, figsize=(14, 10), constrained_layout=True)
    latency_ax = axes[0][0]
    throughput_ax = axes[0][1]
    status_ax = axes[1][0]
    rule_ax = axes[1][1]

    # 1) Latency grouped bars (avg, p95)
    width = 0.35
    avg_values = [row["latency_avg"] for row in rows]
    p95_values = [row["latency_p95"] for row in rows]
    avg_bars = latency_ax.bar(
        [x - (width / 2) for x in x_positions], avg_values, width=width, color=colors, alpha=0.75, label="avg",
    )
    p95_bars = latency_ax.bar(
        [x + (width / 2) for x in x_positions], p95_values, width=width, color=colors, alpha=1.0, label="p95",
    )
    mark_failed_bar_groups(avg_bars, p95_bars, rows=rows)
    latency_ax.set_title("Latency Comparison")
    latency_ax.set_ylabel("ms")
    latency_ax.set_xticks(x_positions)
    latency_ax.set_xticklabels(labels)
    latency_ax.grid(axis="y", linestyle="--", alpha=0.35)
    latency_ax.legend(loc="upper left")
    add_bar_labels(latency_ax, p95_bars, p95_values, "{:.2f}")

    # 2) Throughput bars + success-rate label
    throughput_values = [row["throughput"] for row in rows]
    throughput_bars = throughput_ax.bar(x_positions, throughput_values, color=colors, edgecolor="#333333",
                                        linewidth=1.0)
    mark_failed_bars(throughput_bars, rows)
    throughput_ax.set_title("Throughput + SuccessRate")
    throughput_ax.set_ylabel("req/s")
    throughput_ax.set_xticks(x_positions)
    throughput_ax.set_xticklabels(labels)
    throughput_ax.grid(axis="y", linestyle="--", alpha=0.35)
    add_bar_labels(throughput_ax, throughput_bars, throughput_values, "{:.2f}")
    ymax = max(throughput_values) if throughput_values else 0.0
    y_offset = ymax * 0.08 if ymax > 0 else 0.1
    for bar, row in zip(throughput_bars, rows):
        throughput_ax.text(
            bar.get_x() + (bar.get_width() / 2),
            bar.get_height() + y_offset,
            f"SR={row['success_rate'] * 100:.1f}%",
            ha="center",
            va="bottom",
            fontsize=9,
            color="#222222",
        )

    # 3) HTTP status stacked bars (201/409/422)
    status_bottom = [0.0] * len(rows)
    status_bar_groups = []
    for item in STATUS_STACK:
        values = [row[item["key"]] for row in rows]
        bars = status_ax.bar(
            x_positions, values, bottom=status_bottom, label=item["label"], color=item["color"], edgecolor="#333333",
        )
        status_bar_groups.append(bars)
        status_bottom = [bottom + value for bottom, value in zip(status_bottom, values)]
    mark_failed_bar_groups(*status_bar_groups, rows=rows)
    status_ax.set_title("Status Distribution")
    status_ax.set_ylabel("count")
    status_ax.set_xticks(x_positions)
    status_ax.set_xticklabels(labels)
    status_ax.grid(axis="y", linestyle="--", alpha=0.35)
    status_ax.legend(loc="upper right")

    # 4) 422 breakdown stacked bars
    rule_bottom = [0.0] * len(rows)
    rule_bar_groups = []
    for item in RULE_VIOLATION_STACK:
        values = [row[item["key"]] for row in rows]
        bars = rule_ax.bar(
            x_positions, values, bottom=rule_bottom, label=item["label"], color=item["color"], edgecolor="#333333",
        )
        rule_bar_groups.append(bars)
        rule_bottom = [bottom + value for bottom, value in zip(rule_bottom, values)]
    mark_failed_bar_groups(*rule_bar_groups, rows=rows)
    rule_ax.set_title("422 Breakdown")
    rule_ax.set_ylabel("count")
    rule_ax.set_xticks(x_positions)
    rule_ax.set_xticklabels(labels)
    rule_ax.grid(axis="y", linestyle="--", alpha=0.35)
    rule_ax.legend(loc="upper right")

    all_pass = all(row["pass"] for row in rows)
    scenario_info = " | ".join([f"{row['label']}={row['scenario']}" for row in rows])
    fig.suptitle(
        f"{SETS[set_name]['title']} (allPass={all_pass})\n{scenario_info}",
        fontsize=12,
    )

    if not all_pass:
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


def resolve_target_sets(target_set: str) -> list[str]:
    if target_set == "all":
        return ["spread", "conflict"]
    return [target_set]


def main() -> int:
    args = parse_args()
    if args.dpi <= 0:
        fatal("--dpi must be a positive integer")

    root = Path(args.root)
    output_dir = Path(args.output_dir)
    if not root.is_dir():
        fatal(f"Root directory not found: {root}")

    generated = 0
    for set_name in resolve_target_sets(args.target_set):
        file_paths = get_set_file_paths(root, set_name)
        missing_paths = find_missing_paths(file_paths)
        if missing_paths:
            missing_lines = ", ".join(str(path) for path in missing_paths)
            if args.strict_missing or args.target_set != "all":
                fatal(f"Missing files for set `{set_name}`: {missing_lines}")
            warn(f"Skipping set `{set_name}` due to missing files: {missing_lines}")
            continue

        rows = load_rows(file_paths)
        output_path = output_dir / SETS[set_name]["output"]
        make_plot(rows, set_name, output_path, args.dpi)
        print(f"[INFO] Plot saved: {output_path}")
        generated += 1

    if generated == 0:
        fatal("No plots were generated. Provide at least one complete set of summary files.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

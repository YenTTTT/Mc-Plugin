"""Plot the Java model's exported CSV; no balance formulas are duplicated here."""
import csv
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


def main():
    data = Path(__file__).resolve().parents[2] / "docs" / "data"
    with (data / "BALANCE_SCENARIOS.csv").open(encoding="utf-8-sig", newline="") as file:
        scenarios = list(csv.DictReader(file))
    with (data / "BALANCE_LEVELS_1_60.csv").open(encoding="utf-8-sig", newline="") as file:
        levels = {row["level"]: row for row in csv.DictReader(file)}
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.6), layout="constrained")
    choices = [("BALANCED", "#0072B2", "Balanced", "-"),
               ("GLASS", "#D55E00", "All offense", "--"),
               ("TANK", "#009E73", "Tank", "-.")]
    for build, color, label, style in choices:
        rows = [row for row in scenarios if row["build"] == build and row["weapon"] == "SWORD"
                and row["accuracy"] == "0.80" and row["player_level"] == row["mob_level"] == row["gear_level"]]
        for axis, key in zip(axes, ("continuous_ttk_s", "hit_equivalents_to_die")):
            axis.plot([int(row["player_level"]) for row in rows], [float(row[key]) for row in rows],
                      color=color, label=label, linestyle=style, linewidth=2)
    crafting = [row for row in scenarios if row["build"] == "BALANCED" and row["weapon"] == "SWORD"
                and row["accuracy"] == "0.80" and row["player_level"] == row["mob_level"]
                and row["gear_level"] == levels[row["player_level"]]["available_crafting_level"]]
    axes[0].plot([int(row["player_level"]) for row in crafting],
                 [float(row["continuous_ttk_s"]) for row in crafting], color="#CC79A7",
                 label="Balanced, available crafting tier", linestyle=":", linewidth=2)
    axes[0].axhspan(4, 6, color="#0072B2", alpha=.06, label="Standard target: 4-6 s")
    axes[0].set(title="Same-level ordinary mob", ylabel="Continuous TTK (seconds)", ylim=(3.5, 7))
    axes[1].set(title="No healing, no dodging", ylabel="HP / received hit (ceil = lethal hit)", ylim=(3.8, 8.1))
    for axis in axes:
        axis.set(xlabel="Player and monster level", xlim=(1, 60))
        axis.grid(alpha=.2)
        axis.legend(fontsize=8, loc="best")
    fig.suptitle("Initial PvE model: sword, 80% effective accuracy - offline estimates")
    fig.savefig(data / "BALANCE_CURVES.svg", metadata={"Date": None})
    plt.close(fig)


if __name__ == "__main__":
    main()

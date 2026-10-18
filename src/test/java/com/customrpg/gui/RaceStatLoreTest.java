package com.customrpg.gui;

import com.customrpg.races.RaceData;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaceStatLoreTest {
    private final RaceData demon = new RaceData.Builder("demon").baseStrength(8).baseMagic(7).baseAgility(6)
            .baseVitality(5).baseDefense(3).baseSpirit(3).growthStrength(1.3).growthMagic(1.2)
            .growthAgility(1.1).growthVitality(.9).growthDefense(.7).growthSpirit(.8).build();

    @Test void displayedBonusesUseTheViewedPlayersLevelAndMatchCombatValues() {
        var lines = RaceGUI.attributeLore(demon, 60).stream().map(ChatColor::stripColor).toList();
        assertTrue(lines.get(0).contains("Lv.60"));
        assertTrue(lines.get(1).contains("種族額外加成"));
        String[] labels = {"力量", "魔法", "敏捷", "體力", "防禦", "精神"};
        int[] bonuses = demon.effectiveStats(60);
        for (int i = 0; i < 6; i++) {
            String expected = labels[i] + ": +" + bonuses[i];
            assertTrue(lines.stream().anyMatch(line -> line.contains(expected)), expected);
        }
        assertTrue(lines.contains("六屬性合計: +36"));
        assertNotEquals(RaceGUI.attributeLore(demon, 1), RaceGUI.attributeLore(demon, 60));
    }
    @Test void growthPreviewShowsActualPointsInsteadOfMisleadingGrowthMultipliers() {
        var lines = RaceGUI.growthLore(demon).stream().map(ChatColor::stripColor).toList();
        assertTrue(lines.contains("Lv.1 種族額外加成（合計 +18）"));
        assertTrue(lines.contains("Lv.30 種族額外加成（合計 +26）"));
        assertTrue(lines.contains("Lv.60 種族額外加成（合計 +36）"));
        assertTrue(lines.stream().noneMatch(line -> line.contains("x")));
    }
}

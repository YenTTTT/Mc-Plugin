package com.customrpg.balance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.customrpg.balance.InitialBalanceModel.*;
import static org.junit.jupiter.api.Assertions.*;

class InitialBalanceModelTest {
    @Test void levelSixtyHasAConservedPointBudgetAndConcreteReferenceStats() {
        assertEquals(0, earnedPoints(1));
        assertEquals(177, earnedPoints(60));
        assertEquals(new Allocation(106, 44, 27), allocation(60, Build.BALANCED));
        assertEquals(737, referenceHealth(60));
        assertEquals(34.55, weaponAttack(60, Weapon.SWORD), 1e-9);
        assertEquals(438.208, mobHealth(60, Role.NORMAL, 1), 1e-6);
        for (int l = 1; l <= 60; l++) for (Build b : Build.values()) {
            var a = allocation(l, b);
            assertEquals(earnedPoints(l), a.primary() + a.vitality() + a.defense());
        }
    }

    @Test void extremeBuildsTradeDamageForSurvivalAtEveryLevel() {
        for (int l = 2; l <= 60; l++) {
            double[] dps = new double[3], hits = new double[3];
            for (Build b : Build.values()) {
                var a = allocation(l, b); var g = referenceGear(l);
                dps[b.ordinal()] = expectedDps(l, Weapon.SWORD, a.primary() + g.primary(), 0, ACCURACY);
                double hp = playerHealth(l, a.vitality() + g.vitality(), 0);
                double dr = reduction(l, a.defense() + g.defense());
                hits[b.ordinal()] = hp / (mobRawDamage(l, Role.NORMAL) * (1 - dr));
            }
            assertTrue(dps[Build.GLASS.ordinal()] >= dps[Build.BALANCED.ordinal()]);
            assertTrue(dps[Build.BALANCED.ordinal()] >= dps[Build.TANK.ordinal()]);
            assertTrue(hits[Build.TANK.ordinal()] >= hits[Build.BALANCED.ordinal()]);
            assertTrue(hits[Build.BALANCED.ordinal()] >= hits[Build.GLASS.ordinal()]);
            assertTrue(hits[Build.GLASS.ordinal()] >= 4.3, "Glass must not be a routine one-shot build");
            assertTrue(mobHealth(l, Role.NORMAL, 1) / dps[Build.TANK.ordinal()] < 6.6);
        }
    }

    @Test void allEightWeaponsStayWithinTheirIntendedTimeBandIncludingSlowWeapons() {
        for (int l = 1; l <= 60; l++) for (Weapon w : Weapon.values()) {
            var a = allocation(l, Build.BALANCED); var g = referenceGear(l);
            double primary = a.primary() + g.primary();
            double dps = expectedDps(l, w, primary, w.primary == Primary.AGILITY ? primary : 0, ACCURACY);
            double ttk = mobHealth(l, Role.NORMAL, 1) / dps;
            assertTrue(ttk >= 4.8 && ttk <= 6, w + " continuous TTK " + ttk);
            double discrete = discreteKillSeconds(mobHealth(l, Role.NORMAL, 1), fullHit(l, w, primary),
                    1 / (w.attacksPerSecond * ACCURACY));
            assertTrue(discrete >= 4 && discrete <= 5.5, w + " no-crit discrete TTK " + discrete);
        }
    }

    @Test void actualCraftingTierGapsDoNotCreateAnEightSecondOrdinaryMobWall() {
        for (int l = 1; l <= 60; l++) {
            int itemLevel = availableCraftingLevel(l);
            var a = allocation(l, Build.BALANCED); var g = referenceGear(itemLevel);
            double dps = expectedDps(itemLevel, Weapon.SWORD, a.primary() + g.primary(), 0, ACCURACY);
            assertTrue(mobHealth(l, Role.NORMAL, 1) / dps < 6.6, "Crafting gap at level " + l);
        }
        assertEquals(1, availableCraftingLevel(7));
        assertEquals(8, availableCraftingLevel(8));
        assertEquals(58, availableCraftingLevel(60));
    }

    @Test void armorHasDiminishingReturnsAndNeverProducesImmunity() {
        for (int l = 1; l <= 60; l++) {
            assertTrue(reduction(l, 20) - reduction(l, 10) > reduction(l, 40) - reduction(l, 30));
            assertEquals(.60, reduction(l, 100_000));
            double nakedHp = playerHealth(l, 0, 0);
            assertTrue(nakedHp / mobRawDamage(l, Role.NORMAL) >= 3.4);
        }
        assertEquals(.30, critChance(10000, 1));
        assertEquals(1.75, critMultiplier(100));
    }

    @Test void groupScalingIsFixedFromParticipantsAndDoesNotMultiplyDamage() {
        for (int l = 1; l <= 60; l++) {
            double solo = mobHealth(l, Role.GROUP_BOSS, 1);
            assertEquals(solo * 3.25, mobHealth(l, Role.GROUP_BOSS, 4), 1e-8);
            assertEquals(97.5, mobHealth(l, Role.GROUP_BOSS, 4) / (.8 * 4 * referenceDps(l)), 1e-8);
            assertEquals(78, mobHealth(l, Role.GROUP_BOSS, 4) / (.8 * 4 * referenceDps(l) * 1.25), 1e-8);
            assertEquals(mobHealth(l, Role.NORMAL, 1), mobHealth(l, Role.NORMAL, 8));
        }
    }

    @Test void nakedAndUndergearedCharactersRetainAProgressionFloorButCannotIgnoreGear() {
        assertEquals(572, playerHealth(60, 0, 0));
        var a = allocation(60, Build.BALANCED);
        double currentDps = referenceDps(60);
        double oldDps = expectedDps(1, Weapon.SWORD, a.primary(), 0, ACCURACY);
        assertTrue(currentDps > oldDps * 1.8);
        assertTrue(mobRawDamage(60, Role.NORMAL) > playerHealth(1, 0, 0));
    }

    @Test void exportsEveryLevelAndAllSensitivityCasesWithoutRequiringPaper(@TempDir Path directory) throws Exception {
        export(directory);
        var levels = Files.readAllLines(directory.resolve("BALANCE_LEVELS_1_60.csv"), StandardCharsets.UTF_8);
        var scenarios = Files.readAllLines(directory.resolve("BALANCE_SCENARIOS.csv"), StandardCharsets.UTF_8);
        assertEquals(61, levels.size());
        assertTrue(scenarios.size() > 10000);
        assertEquals(scenarios.size(), scenarios.stream().distinct().count());
        assertEquals(481, Files.readAllLines(directory.resolve("BALANCE_WEAPONS.csv"), StandardCharsets.UTF_8).size());
        assertTrue(levels.getFirst().startsWith("\ufefflevel,"));
        assertTrue(levels.getLast().startsWith("60,3,177,"));
        assertTrue(scenarios.stream().anyMatch(row -> row.startsWith("60,50,BALANCED,SWORD")));
        assertTrue(scenarios.stream().anyMatch(row -> row.startsWith("1,11,BALANCED,SWORD")));
    }

    @Test void invalidLevelsAndNonFiniteInputsCannotBecomeSilentBalanceValues() {
        assertThrows(IllegalArgumentException.class, () -> earnedPoints(0));
        assertThrows(IllegalArgumentException.class, () -> earnedPoints(61));
        assertThrows(IllegalArgumentException.class, () -> playerHealth(1, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> reduction(1, -1));
        assertThrows(IllegalArgumentException.class, () -> fullHit(1, Weapon.SWORD, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> expectedDps(1, Weapon.SWORD, 0, 0, 1.1));
        assertThrows(IllegalArgumentException.class, () -> mobHealth(1, Role.GROUP_BOSS, 9));
        assertThrows(IllegalArgumentException.class, () -> discreteKillSeconds(1, 0, 1));
    }
}

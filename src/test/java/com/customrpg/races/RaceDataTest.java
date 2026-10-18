package com.customrpg.races;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RaceDataTest {
    @Test void firstLevelUsesBaseBudgetAndLaterLevelsAddGrowthWithoutRemovingStats() {
        var race=new RaceData.Builder("elf").baseAgility(8).growthAgility(1.3).baseMana(20).growthMana(3).build();
        assertEquals(4,race.calculateAgilityBonus(1)); assertEquals(4,race.calculateAgilityBonus(2)); assertEquals(7,race.calculateAgilityBonus(60));
        assertEquals(1,race.calculateManaBonus(1)); assertEquals(9.85,race.calculateManaBonus(60),1e-9);
        assertEquals(4,race.calculateAgilityBonus(Integer.MIN_VALUE));
    }
    @Test void builderCopiesCollectionsAndNormalizesIdsIndependentOfLocale() {
        var skills=new ArrayList<>(List.of("focus")); var weapons=new LinkedHashMap<>(Map.of("sword",1.2));
        var race=new RaceData.Builder("HUMAN").skills(skills).weaponBonuses(weapons).build(); skills.clear(); weapons.clear();
        assertEquals("human",race.getId()); assertEquals(List.of("focus"),race.getSkills()); assertEquals(1.02,race.getWeaponBonus("SWORD"));
        assertThrows(UnsupportedOperationException.class,()->race.getWeaponBonuses().clear());
    }
    @Test void invalidRatesAndWeaponCategoriesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new RaceData.Builder("bad").growthMana(Double.NaN).build());
        assertThrows(IllegalArgumentException.class,()->new RaceData.Builder("bad").bonusMoveSpeed(4).build());
        assertThrows(IllegalArgumentException.class,()->new RaceData.Builder("bad").weaponBonuses(Map.of("typo",1.2)).build());
        assertThrows(IllegalArgumentException.class,()->new RaceData.Builder("bad").defaultWeaponBonus(-1).build());
    }
    @Test void everyLevelStaysWithinBudgetAndNeverLosesAnAttribute() {
        var race = new RaceData.Builder("demon").baseStrength(8).baseMagic(7).baseAgility(6)
                .baseVitality(5).baseDefense(3).baseSpirit(3).growthStrength(1.3).growthMagic(1.2)
                .growthAgility(1.1).growthVitality(.9).growthDefense(.7).growthSpirit(.8).build();
        int[] previous = new int[6];
        for (int level = 1; level <= 60; level++) {
            int[] current = race.effectiveStats(level);
            assertEquals(com.customrpg.balance.BalancePlayerRules.raceBudget(level), Arrays.stream(current).sum());
            for (int i = 0; i < 6; i++) { assertTrue(current[i] >= 1); assertTrue(current[i] >= previous[i]); }
            previous = current;
        }
        assertArrayEquals(race.effectiveStats(1), race.effectiveStats(Integer.MIN_VALUE));
        assertArrayEquals(race.effectiveStats(60), race.effectiveStats(Integer.MAX_VALUE));
    }
    @Test void explicitZeroCustomProfileDoesNotGainInventedAttributes() {
        var race = new RaceData.Builder("zero").baseStrength(0).baseMagic(0).baseAgility(0).baseVitality(0)
                .baseDefense(0).baseSpirit(0).growthStrength(0).growthMagic(0).growthAgility(0)
                .growthVitality(0).growthDefense(0).growthSpirit(0).build();
        assertArrayEquals(new int[6], race.effectiveStats(1));
        assertArrayEquals(new int[6], race.effectiveStats(60));
    }
}

package com.customrpg.races;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RaceDataTest {
    @Test void firstLevelUsesBaseAndLaterLevelsAddGrowthOnce() {
        var race=new RaceData.Builder("elf").baseAgility(8).growthAgility(1.3).baseMana(20).growthMana(3).build();
        assertEquals(8,race.calculateAgilityBonus(1)); assertEquals(9,race.calculateAgilityBonus(2)); assertEquals(84,race.calculateAgilityBonus(60));
        assertEquals(20,race.calculateManaBonus(1)); assertEquals(197,race.calculateManaBonus(60));
        assertEquals(8,race.calculateAgilityBonus(Integer.MIN_VALUE));
    }
    @Test void builderCopiesCollectionsAndNormalizesIdsIndependentOfLocale() {
        var skills=new ArrayList<>(List.of("focus")); var weapons=new LinkedHashMap<>(Map.of("sword",1.2));
        var race=new RaceData.Builder("HUMAN").skills(skills).weaponBonuses(weapons).build(); skills.clear(); weapons.clear();
        assertEquals("human",race.getId()); assertEquals(List.of("focus"),race.getSkills()); assertEquals(1.2,race.getWeaponBonus("SWORD"));
        assertThrows(UnsupportedOperationException.class,()->race.getWeaponBonuses().clear());
    }
    @Test void invalidRatesAndWeaponCategoriesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new RaceData.Builder("bad").growthMana(Double.NaN).build());
        assertThrows(IllegalArgumentException.class,()->new RaceData.Builder("bad").bonusMoveSpeed(4).build());
        assertThrows(IllegalArgumentException.class,()->new RaceData.Builder("bad").weaponBonuses(Map.of("typo",1.2)).build());
        assertThrows(IllegalArgumentException.class,()->new RaceData.Builder("bad").defaultWeaponBonus(-1).build());
    }
}

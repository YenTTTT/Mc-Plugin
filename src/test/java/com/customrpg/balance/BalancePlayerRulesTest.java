package com.customrpg.balance;

import com.customrpg.players.PlayerStats;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BalancePlayerRulesTest {
    @Test void temporaryFormsCannotRestoreAStaleMaximumAfterLevelOrGearChanges() {
        var stats=new PlayerStats(); stats.setLevel(60); stats.setVitality(44); stats.setEquipmentVitality(11);
        assertEquals(737,BalancePlayerRules.maxHealth(stats));
        stats.setFormMaxHealth(100); assertEquals(837,BalancePlayerRules.maxHealth(stats));
        stats.setEquipmentVitality(12); stats.setFormMaxHealth(0); assertEquals(740,BalancePlayerRules.maxHealth(stats));
        stats.setVitality(177); assertEquals(1024,BalancePlayerRules.maxHealth(stats));
    }
    @Test void sixtyLevelsGiveExactly177PointsAnd59TalentsWithoutAutoAllocations() {
        var stats=new PlayerStats(); stats.setExp(1_000_000);
        int gains=0; while(BalancePlayerRules.advanceLevel(stats)) gains++;
        assertEquals(59,gains); assertEquals(60,stats.getLevel()); assertEquals(177,stats.getStatPoints()); assertEquals(59,stats.getTalentPoints());
        assertEquals(0,stats.getStrength()+stats.getMagic()+stats.getAgility()+stats.getVitality()+stats.getDefense()+stats.getSpirit());
        assertFalse(BalancePlayerRules.advanceLevel(stats));
    }
    @Test void InsufficientExperienceDoesNotGrantAFreeLevel() {
        var stats=new PlayerStats(); stats.setExp(99); assertFalse(BalancePlayerRules.advanceLevel(stats));
        assertEquals(1,stats.getLevel()); assertEquals(0,stats.getStatPoints());
        stats.setExp(100); assertTrue(BalancePlayerRules.advanceLevel(stats)); assertEquals(0,stats.getExp()); assertEquals(3,stats.getStatPoints());
    }
    @Test void transientTalentAndRaceStatsAreNotSerializedBackIntoAllocatedPoints() {
        var stats=new PlayerStats(); stats.setStrength(10); stats.setTalentStrength(6); stats.setRaceStrength(4);
        assertEquals(20,stats.getTotalStrength());
        var restored=PlayerStats.deserialize(stats.serialize()); assertEquals(10,restored.getTotalStrength()); assertEquals(10,restored.getStrength());
    }
    @Test void tooManyAccessoriesRunesAndOldSetBonusesCannotExceedTheAggregateBudget() {
        var stats=new PlayerStats(); stats.setLevel(60); stats.setEquipmentStrength(10000); stats.setEquipmentVitality(10000); stats.setEquipmentDefense(10000);
        stats.setBonusMaxHealth(10000); stats.setBonusCritChance(10000); BalancePlayerRules.limitEquipment(stats);
        assertEquals(81,stats.getEquipmentStrength()+stats.getEquipmentVitality()+stats.getEquipmentDefense());
        assertEquals(28.6,stats.getBonusMaxHealth(),1e-9); assertEquals(5,stats.getBonusCritChance());
    }
    @Test void talentManaCostUsesTheConfiguredFixedValue() {
        var talent=new com.customrpg.talents.Talent("test","test","",com.customrpg.talents.TalentType.ACTIVE,
                com.customrpg.talents.TalentBranch.FROST,3,1,java.util.List.of(),null,0,"PAPER","none",8,50,0,"hotkey");
        assertEquals(50,talent.getManaCost(),1e-9);
        assertEquals(18,BalancePlayerRules.raceBudget(1)); assertEquals(36,BalancePlayerRules.raceBudget(60));
        assertTrue(BalancePlayerRules.talentBudget(60)<=6);
    }
}

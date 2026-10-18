package com.customrpg.balance;

import com.customrpg.players.PlayerStats;
import java.util.Arrays;

/** Source budgets shared by login, equipment, talent and race recalculation. */
public final class BalancePlayerRules {
    private BalancePlayerRules() {}

    public static int[] bounded(long[] values, int budget) {
        long sum = Arrays.stream(values).sum();
        return BalanceMigration.redistribute(values, (int) Math.min(Math.max(0, sum), budget));
    }

    public static int raceBudget(int level) { return 18 + 18 * (BalanceMigration.level(level) - 1) / 59; }
    public static int talentBudget(int level) { return 1 + (int) Math.floor(.10 * (BalanceMigration.level(level) - 1)); }
    public static double maxHealth(PlayerStats stats) {
        return Math.min(1024,InitialBalanceModel.playerHealth(stats.getLevel(),stats.getTotalVitality(),
                stats.getBonusMaxHealth()+stats.getTalentMaxHealth()+stats.getFormMaxHealth()));
    }
    public static boolean advanceLevel(PlayerStats stats) {
        if (stats.getLevel()>=60 || stats.getExp()<stats.getLevel()*100L) return false;
        stats.setExp(stats.getExp()-stats.getLevel()*100L);
        stats.setLevel(stats.getLevel()+1);
        stats.setStatPoints(stats.getStatPoints()+InitialBalanceModel.POINTS_PER_LEVEL);
        stats.addTalentPoints(1);
        return true;
    }

    public static void limitEquipment(PlayerStats stats) {
        var reference = InitialBalanceModel.referenceGear(stats.getLevel());
        int cap = (int) Math.ceil((reference.primary() + reference.vitality() + reference.defense()) * 1.10);
        int[] adjusted = bounded(new long[]{stats.getEquipmentStrength(), stats.getEquipmentMagic(), stats.getEquipmentAgility(),
                stats.getEquipmentVitality(), stats.getEquipmentDefense(), stats.getEquipmentSpirit()}, cap);
        stats.setEquipmentStrength(adjusted[0]); stats.setEquipmentMagic(adjusted[1]); stats.setEquipmentAgility(adjusted[2]);
        stats.setEquipmentVitality(adjusted[3]); stats.setEquipmentDefense(adjusted[4]); stats.setEquipmentSpirit(adjusted[5]);
        stats.setBonusMaxHealth(Math.clamp(stats.getBonusMaxHealth(), 0, (100 + 8 * (stats.getLevel() - 1)) * .05));
        stats.setBonusMaxMana(Math.clamp(stats.getBonusMaxMana(), 0, 10));
        stats.setBonusManaRegen(Math.clamp(stats.getBonusManaRegen(), 0, .5));
        stats.setBonusCritChance(Math.clamp(stats.getBonusCritChance(), 0, 5));
        stats.setBonusCritDamage(Math.clamp(stats.getBonusCritDamage(), 0, 10));
        stats.setBonusDefenseValue(Math.clamp(stats.getBonusDefenseValue(), 0, 5));
        stats.setBonusAttackDamage(Math.clamp(stats.getBonusAttackDamage(), 0, 3));
    }
}

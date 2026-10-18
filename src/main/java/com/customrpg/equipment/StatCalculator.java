package com.customrpg.equipment;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 屬性計算器
 *
 * 負責計算玩家最終屬性，包含：
 * - 基礎屬性
 * - 裝備加成
 * - 職業加成
 * - 天賦加成
 * - BUFF加成
 *
 * 提供戰鬥相關的傷害計算公式
 */
public class StatCalculator {

    private final CustomRPG plugin;
    private final EquipmentManager equipmentManager;

    public StatCalculator(CustomRPG plugin, EquipmentManager equipmentManager) {
        this.plugin = plugin;
        this.equipmentManager = equipmentManager;
    }

    /**
     * 計算玩家最終屬性
     */
    public FinalStats calculateFinalStats(Player player) {
        FinalStats finalStats = new FinalStats();

        PlayerStats baseStats=plugin.getPlayerStatsManager().getStats(player);
        finalStats.setBaseStrength(baseStats.getTotalStrength());
        finalStats.setBaseMagic(baseStats.getTotalMagic());
        finalStats.setBaseAgility(baseStats.getTotalAgility());
        finalStats.setBaseVitality(baseStats.getTotalVitality());
        finalStats.setBaseDefense(baseStats.getTotalDefense());
        return finalStats;
    }

    /**
     * 添加裝備屬性加成
     */
    private void addEquipmentBonus(FinalStats finalStats, EquipmentData equipment) {
        Map<EquipmentAttribute, Double> attributes = equipment.getAllAttributes();

        for (Map.Entry<EquipmentAttribute, Double> entry : attributes.entrySet()) {
            EquipmentAttribute attr = entry.getKey();
            double value = entry.getValue();

            switch (attr) {
                case STRENGTH:
                    finalStats.addStrength((int) value);
                    break;
                case INTELLIGENCE:
                    finalStats.addMagic((int) value);
                    break;
                case AGILITY:
                    finalStats.addAgility((int) value);
                    break;
                case VITALITY:
                    finalStats.addVitality((int) value);
                    break;
                case DEFENSE:
                    finalStats.addDefense((int) value);
                    break;
                case ATTACK_DAMAGE:
                    finalStats.addBonusAttackDamage(value);
                    break;
                case MAGIC_DAMAGE:
                    finalStats.addBonusMagicDamage(value);
                    break;
                case CRITICAL_CHANCE:
                    finalStats.addBonusCritChance(value);
                    break;
                case CRITICAL_DAMAGE:
                    finalStats.addBonusCritDamage(value);
                    break;
                case MAX_HEALTH:
                    finalStats.addBonusMaxHealth(value);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 計算物理傷害
     * 使用 v2 武器快照的單擊期望值
     */
    public double calculatePhysicalDamage(Player player) {
        return plugin.getCombatBalance().calculate(player, player.getInventory().getItemInMainHand(),1,1).expectedHit();
    }

    /**
     * 計算魔法傷害
     * 法杖亦使用 v2 武器攻擊模型
     */
    public double calculateMagicDamage(Player player) {
        return plugin.getCombatBalance().calculate(player, player.getInventory().getItemInMainHand(),1,1).expectedHit();
    }

    /**
     * 計算技能傷害
     * 公式：技能傷害 = 技能基礎值 + (智慧 × 技能係數)
     */
    public double calculateSkillDamage(Player player, double skillBase, double skillCoefficient) {
        FinalStats stats = calculateFinalStats(player);
        return skillBase + (stats.getTotalMagic() * skillCoefficient);
    }

    /**
     * 計算暴擊率
     * 讀取實際戰鬥快照，單位為百分比
     */
    public double calculateCritChance(Player player) {
        return plugin.getCombatBalance().calculate(player, player.getInventory().getItemInMainHand(),1,1).critChance()*100;
    }

    /**
     * 計算暴擊傷害
     * 讀取實際戰鬥快照，單位為百分比
     */
    public double calculateCritDamage(Player player) {
        return plugin.getCombatBalance().calculate(player, player.getInventory().getItemInMainHand(),1,1).critMultiplier()*100;
    }

    /**
     * 計算物理防禦
     */
    public double calculatePhysicalDefense(Player player) {
        FinalStats stats = calculateFinalStats(player);
        return stats.getTotalDefense();
    }

    /**
     * 計算魔法防禦
     * RPG 傷害共用防禦評分
     */
    public double calculateMagicDefense(Player player) {
        return plugin.getPlayerStatsManager().getStats(player).getTotalDefense();
    }

    /**
     * 計算最大血量
     */
    public double calculateMaxHealth(Player player) {
        return player.getMaxHealth();
    }

    /**
     * 最終屬性數據類
     */
    public static class FinalStats {
        // 基礎屬性
        private int baseStrength, baseMagic, baseAgility, baseVitality, baseDefense;

        // 裝備加成
        private int equipStrength, equipMagic, equipAgility, equipVitality, equipDefense;

        // 額外加成
        private double bonusAttackDamage;
        private double bonusMagicDamage;
        private double bonusCritChance;
        private double bonusCritDamage;
        private double bonusMaxHealth;

        // Setters for base stats
        public void setBaseStrength(int value) { this.baseStrength = value; }
        public void setBaseMagic(int value) { this.baseMagic = value; }
        public void setBaseAgility(int value) { this.baseAgility = value; }
        public void setBaseVitality(int value) { this.baseVitality = value; }
        public void setBaseDefense(int value) { this.baseDefense = value; }

        // Adders for equipment stats
        public void addStrength(int value) { this.equipStrength += value; }
        public void addMagic(int value) { this.equipMagic += value; }
        public void addAgility(int value) { this.equipAgility += value; }
        public void addVitality(int value) { this.equipVitality += value; }
        public void addDefense(int value) { this.equipDefense += value; }

        // Adders for bonus stats
        public void addBonusAttackDamage(double value) { this.bonusAttackDamage += value; }
        public void addBonusMagicDamage(double value) { this.bonusMagicDamage += value; }
        public void addBonusCritChance(double value) { this.bonusCritChance += value; }
        public void addBonusCritDamage(double value) { this.bonusCritDamage += value; }
        public void addBonusMaxHealth(double value) { this.bonusMaxHealth += value; }

        // Total stats getters
        public int getTotalStrength() { return baseStrength + equipStrength; }
        public int getTotalMagic() { return baseMagic + equipMagic; }
        public int getTotalAgility() { return baseAgility + equipAgility; }
        public int getTotalVitality() { return baseVitality + equipVitality; }
        public int getTotalDefense() { return baseDefense + equipDefense; }

        // Bonus stats getters
        public double getBonusAttackDamage() { return bonusAttackDamage; }
        public double getBonusMagicDamage() { return bonusMagicDamage; }
        public double getBonusCritChance() { return bonusCritChance; }
        public double getBonusCritDamage() { return bonusCritDamage; }
        public double getBonusMaxHealth() { return bonusMaxHealth; }
    }
}


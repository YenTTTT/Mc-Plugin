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

        // 1. 獲取基礎屬性
        PlayerStats baseStats = plugin.getPlayerStatsManager().getStats(player);
        finalStats.setBaseStrength(baseStats.getStrength());
        finalStats.setBaseMagic(baseStats.getMagic());
        finalStats.setBaseAgility(baseStats.getAgility());
        finalStats.setBaseVitality(baseStats.getVitality());
        finalStats.setBaseDefense(baseStats.getDefense());

        // 2. 添加裝備加成
        Map<EquipmentSlot, EquipmentData> equipment = equipmentManager.getPlayerEquipment(player.getUniqueId());
        for (EquipmentData equip : equipment.values()) {
            if (equip != null) {
                addEquipmentBonus(finalStats, equip);
            }
        }

        // 3. 添加職業加成（預留）
        // PlayerClass playerClass = getPlayerClass(player);
        // addClassBonus(finalStats, playerClass);

        // 4. 添加天賦加成（預留）
        // addTalentBonus(finalStats, player);

        // 5. 添加BUFF加成（預留）
        // addBuffBonus(finalStats, player);

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
     * 公式：物理傷害 = (力量 × 0.1) + 武器基礎值
     */
    public double calculatePhysicalDamage(Player player) {
        FinalStats stats = calculateFinalStats(player);

        // 基礎物理傷害
        double damage = stats.getTotalStrength() * 0.1;

        // 添加武器基礎值
        EquipmentData mainHand = equipmentManager.getPlayerEquipment(player.getUniqueId()).get(EquipmentSlot.MAIN_HAND);
        if (mainHand != null) {
            damage += mainHand.getAttribute(EquipmentAttribute.ATTACK_DAMAGE);
        }

        // 添加額外攻擊傷害加成
        damage += stats.getBonusAttackDamage();

        // 檢查雙手武器加成 (+20%)
        // 預留：需要判斷武器是否為雙手武器

        return damage;
    }

    /**
     * 計算魔法傷害
     * 公式：魔法傷害 = 60 + (智慧 × 0.1) + 武器魔法加成
     */
    public double calculateMagicDamage(Player player) {
        FinalStats stats = calculateFinalStats(player);

        // 基礎魔法傷害
        double damage = 60 + (stats.getTotalMagic() * 0.1);

        // 添加武器魔法傷害
        EquipmentData mainHand = equipmentManager.getPlayerEquipment(player.getUniqueId()).get(EquipmentSlot.MAIN_HAND);
        if (mainHand != null) {
            damage += mainHand.getAttribute(EquipmentAttribute.MAGIC_DAMAGE);
        }

        // 添加額外魔法傷害加成
        damage += stats.getBonusMagicDamage();

        return damage;
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
     * 公式：暴擊率 = 5% (基礎) + (總敏捷 × 0.5%) + 裝備暴擊率加成
     */
    public double calculateCritChance(Player player) {
        FinalStats stats = calculateFinalStats(player);
        double critChance = 5.0; // 基礎 5%
        critChance += stats.getTotalAgility() * 0.5; // 每點敏捷 +0.5%
        critChance += stats.getBonusCritChance(); // 裝備加成
        return Math.min(critChance, 100.0); // 上限 100%
    }

    /**
     * 計算暴擊傷害
     * 公式：暴擊傷害 = 150% (基礎) + (總力量 × 1%) + 裝備暴擊傷害加成
     */
    public double calculateCritDamage(Player player) {
        FinalStats stats = calculateFinalStats(player);
        double critDamage = 150.0; // 基礎 150%
        critDamage += stats.getTotalStrength() * 1.0; // 每點力量 +1%
        critDamage += stats.getBonusCritDamage(); // 裝備加成
        return critDamage;
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
     * 公式：魔法防禦 = (總智慧 / 2) + 裝備防禦加成
     */
    public double calculateMagicDefense(Player player) {
        FinalStats stats = calculateFinalStats(player);
        return (stats.getTotalMagic() / 2.0);
    }

    /**
     * 計算最大血量
     */
    public double calculateMaxHealth(Player player) {
        FinalStats stats = calculateFinalStats(player);
        return 20.0 + (stats.getTotalVitality() * 2.0) + stats.getBonusMaxHealth();
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


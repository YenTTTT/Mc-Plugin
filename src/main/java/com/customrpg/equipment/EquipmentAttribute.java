package com.customrpg.equipment;

/**
 * 裝備屬性類型枚舉
 * 定義裝備可提供的所有屬性類型
 */
public enum EquipmentAttribute {
    // 基礎屬性
    STRENGTH("力量", "STR", "§c", "增加物理攻擊力和負重"),
    AGILITY("敏捷", "AGI", "§a", "增加攻擊速度、暴擊率和迴避率"),
    INTELLIGENCE("智力", "INT", "§9", "增加魔法攻擊力和魔力值"),
    VITALITY("體力", "VIT", "§6", "增加生命值和生命回復"),
    DEFENSE("防禦", "DEF", "§7", "減少受到的物理傷害"),

    // 戰鬥屬性
    ATTACK_DAMAGE("攻擊力", "ATK", "§c", "增加造成的傷害"),
    MAGIC_DAMAGE("魔法攻擊", "MATK", "§5", "增加魔法傷害"),
    ATTACK_SPEED("攻擊速度", "ASPD", "§e", "增加攻擊頻率"),
    CRITICAL_CHANCE("暴擊率", "CRIT", "§6", "增加暴擊機率"),
    CRITICAL_DAMAGE("暴擊傷害", "CRITDMG", "§6", "增加暴擊傷害倍率"),
    ACCURACY("命中率", "ACC", "§a", "增加攻擊命中率"),
    EVASION("迴避率", "EVA", "§b", "增加迴避攻擊的機率"),

    // 生存屬性
    MAX_HEALTH("最大生命值", "HP", "§c", "增加生命值上限"),
    MAX_MANA("最大魔力值", "MP", "§9", "增加魔力值上限"),
    HEALTH_REGEN("生命回復", "HREGEN", "§d", "增加生命值回復速度"),
    MANA_REGEN("魔力回復", "MREGEN", "§b", "增加魔力值回復速度"),

    // 抗性屬性
    PHYSICAL_RESISTANCE("物理抗性", "PRES", "§7", "減少物理傷害"),
    MAGIC_RESISTANCE("魔法抗性", "MRES", "§5", "減少魔法傷害"),
    FIRE_RESISTANCE("火焰抗性", "FIRE_RES", "§c", "減少火焰傷害"),
    ICE_RESISTANCE("冰霜抗性", "ICE_RES", "§b", "減少冰霜傷害"),
    LIGHTNING_RESISTANCE("雷電抗性", "LIGHT_RES", "§e", "減少雷電傷害"),

    // 特殊屬性
    MOVEMENT_SPEED("移動速度", "SPEED", "§f", "增加移動速度"),
    LUCK("幸運", "LUCK", "§2", "增加掉落率和暴擊率"),
    EXPERIENCE_GAIN("經驗獲得", "EXP", "§a", "增加經驗值獲得"),
    SKILL_DAMAGE("技能傷害", "SKILL_DMG", "§d", "增加技能傷害"),
    COOLDOWN_REDUCTION("冷卻縮減", "CDR", "§3", "減少技能冷卻時間");

    private final String displayName;
    private final String shortName;
    private final String color;
    private final String description;

    EquipmentAttribute(String displayName, String shortName, String color, String description) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.color = color;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColoredDisplayName() {
        return color + displayName + "§r";
    }

    public String getShortName() {
        return shortName;
    }

    public String getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 檢查是否為基礎屬性
     */
    public boolean isBasicAttribute() {
        return this == STRENGTH || this == AGILITY || this == INTELLIGENCE ||
               this == VITALITY || this == DEFENSE;
    }

    /**
     * 檢查是否為戰鬥屬性
     */
    public boolean isCombatAttribute() {
        return this == ATTACK_DAMAGE || this == MAGIC_DAMAGE || this == ATTACK_SPEED ||
               this == CRITICAL_CHANCE || this == CRITICAL_DAMAGE || this == ACCURACY || this == EVASION;
    }

    /**
     * 檢查是否為抗性屬性
     */
    public boolean isResistanceAttribute() {
        return this == PHYSICAL_RESISTANCE || this == MAGIC_RESISTANCE ||
               this == FIRE_RESISTANCE || this == ICE_RESISTANCE || this == LIGHTNING_RESISTANCE;
    }

    /**
     * 獲取屬性值格式化字符串
     */
    public String formatValue(double value) {
        if (this == CRITICAL_CHANCE || this == EVASION || this == ACCURACY ||
            this == EXPERIENCE_GAIN || this == COOLDOWN_REDUCTION) {
            return String.format("%.1f%%", value);
        } else if (this == MOVEMENT_SPEED) {
            return String.format("+%.2f", value);
        } else if (value >= 1000) {
            return String.format("%.1fk", value / 1000);
        } else if (value == (int) value) {
            return String.valueOf((int) value);
        } else {
            return String.format("%.1f", value);
        }
    }

    /**
     * 根據短名稱或全名獲取屬性
     */
    public static EquipmentAttribute fromName(String name) {
        for (EquipmentAttribute attr : values()) {
            if (attr.shortName.equalsIgnoreCase(name) || attr.name().equalsIgnoreCase(name)) {
                return attr;
            }
        }
        return null;
    }

    /**
     * 根據短名稱獲取屬性
     * @deprecated 使用 fromName(String) 代替
     */
    @Deprecated
    public static EquipmentAttribute fromShortName(String shortName) {
        return fromName(shortName);
    }
}

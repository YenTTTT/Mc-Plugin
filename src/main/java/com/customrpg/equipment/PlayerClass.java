package com.customrpg.equipment;

/**
 * 職業枚舉
 *
 * 三大職業：
 * - 刺客（ASSASSIN）：使用輕甲，偏向敏捷和暴擊
 * - 法師（MAGE）：使用布甲，偏向智慧和魔力
 * - 鬥士（WARRIOR）：使用重甲，偏向體力和防禦
 */
public enum PlayerClass {
    ASSASSIN("刺客", "§a", ArmorType.LIGHT),
    MAGE("法師", "§b", ArmorType.CLOTH),
    WARRIOR("鬥士", "§c", ArmorType.HEAVY),
    NONE("無職業", "§7", null);

    private final String displayName;
    private final String colorCode;
    private final ArmorType preferredArmor;

    PlayerClass(String displayName, String colorCode, ArmorType preferredArmor) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.preferredArmor = preferredArmor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public ArmorType getPreferredArmor() {
        return preferredArmor;
    }

    public String getColoredName() {
        return colorCode + displayName;
    }

    /**
     * 檢查是否可以穿戴指定類型的裝甲
     */
    public boolean canWear(ArmorType armorType) {
        if (this == NONE) return true; // 無職業可以穿任何裝甲
        return this.preferredArmor == armorType;
    }
}


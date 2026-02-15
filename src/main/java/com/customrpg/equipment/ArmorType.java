package com.customrpg.equipment;

/**
 * 裝甲類型枚舉
 *
 * 三種裝甲類型：
 * - 輕甲（LIGHT）：適合刺客，偏向敏捷/暴擊
 * - 布甲（CLOTH）：適合法師，偏向智慧/mana
 * - 重甲（HEAVY）：適合鬥士，偏向體力/防禦
 */
public enum ArmorType {
    LIGHT("輕甲", "§a", "刺客"),
    CLOTH("布甲", "§b", "法師"),
    HEAVY("重甲", "§7", "鬥士");

    private final String displayName;
    private final String colorCode;
    private final String suitableClass;

    ArmorType(String displayName, String colorCode, String suitableClass) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.suitableClass = suitableClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getSuitableClass() {
        return suitableClass;
    }

    public String getColoredName() {
        return colorCode + displayName;
    }
}


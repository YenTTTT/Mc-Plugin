package com.customrpg.equipment;

/**
 * 裝備稀有度枚舉
 * 定義裝備的品質等級
 */
public enum EquipmentRarity {
    COMMON("普通", "§f", 1, 0.0),
    UNCOMMON("精良", "§a", 2, 0.15),
    RARE("稀有", "§9", 3, 0.30),
    EPIC("史詩", "§5", 4, 0.50),
    LEGENDARY("傳說", "§6", 5, 0.75),
    MYTHIC("神話", "§c", 6, 1.0),
    MASTERWORK("頂級手作", "§d", 7, 1.25);

    private final String displayName;
    private final String color;
    private final int level;
    private final double statMultiplier; // 屬性倍率加成

    EquipmentRarity(String displayName, String color, int level, double statMultiplier) {
        this.displayName = displayName;
        this.color = color;
        this.level = level;
        this.statMultiplier = statMultiplier;
    }

    public String getDisplayName() {
        return color + displayName;
    }

    public String getColorCode() {
        return color;
    }

    public int getLevel() {
        return level;
    }

    public double getStatMultiplier() {
        return statMultiplier;
    }

    /**
     * 獲取隨機詞條數量
     */
    public int getRandomAffixCount() {
        switch (this) {
            case COMMON: return 0;
            case UNCOMMON: return 1;
            case RARE: return 2;
            case EPIC: return 3;
            case LEGENDARY: return 4;
            case MYTHIC: return 5;
            case MASTERWORK: return 6;
            default: return 0;
        }
    }

    /**
     * 獲取符文槽位數量
     */
    public int getRuneSlots() {
        switch (this) {
            case COMMON:
            case UNCOMMON: return 1;
            case RARE: return 2;
            case EPIC:
            case LEGENDARY: return 3;
            case MYTHIC:
            case MASTERWORK: return 4;
            default: return 1;
        }
    }

    /**
     * 獲取最大強化等級
     */
    public int getMaxEnhanceLevel() {
        switch (this) {
            case COMMON: return 5;
            case UNCOMMON: return 8;
            case RARE: return 12;
            case EPIC: return 15;
            case LEGENDARY: return 18;
            case MYTHIC: return 20;
            case MASTERWORK: return 25;
            default: return 5;
        }
    }

    /**
     * 根據名稱獲取稀有度
     */
    public static EquipmentRarity fromName(String name) {
        for (EquipmentRarity rarity : values()) {
            if (rarity.name().equalsIgnoreCase(name)) {
                return rarity;
            }
        }
        return COMMON;
    }
}

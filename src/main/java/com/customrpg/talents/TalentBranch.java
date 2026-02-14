package com.customrpg.talents;

/**
 * TalentBranch - 天賦分支枚舉
 *
 * 三大分支對應不同的戰鬥風格：
 * - WARRIOR: 戰士 - 近戰、坦克、物理傷害
 * - MAGE: 法師 - 遠程、魔法、範圍傷害
 * - ASSASSIN: 刺客 - 敏捷、暴擊、機動性
 */
public enum TalentBranch {
    WARRIOR("戰士", "近戰物理專精，高生存力與穩定輸出"),
    MAGE("法師", "遠程魔法專精，強大範圍傷害與控制"),
    ASSASSIN("刺客", "敏捷暴擊專精，高機動性與爆發傷害");

    private final String displayName;
    private final String description;

    TalentBranch(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}

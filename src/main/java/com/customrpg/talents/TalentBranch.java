package com.customrpg.talents;

/**
 * TalentBranch - 天賦分支枚舉
 *
 * 流派分支對應不同的戰鬥風格：
 * - FIRE: 烈焰系 - 火焰 / 岩漿 / 燃燒技能
 * - DARK: 暗黑系 - 施放技能會消耗自身血量的高風險技能
 * - WEAPON: 武器系 - 強化武器攻擊與戰鬥技巧
 * - TECH: 科技系 - 機械 / 裝置 / 特殊戰術技能
 * - NATURE: 自然系 - 治療 / 控制 / 自然元素技能
 */
public enum TalentBranch {
    FIRE("烈焰系", "火焰 / 岩漿 / 燃燒技能"),
    DARK("暗黑系", "施放技能會消耗自身血量的高風險技能"),
    WEAPON("武器系", "強化武器攻擊與戰鬥技巧"),
    TECH("科技系", "機械 / 裝置 / 特殊戰術技能"),
    NATURE("自然系", "治療 / 控制 / 自然元素技能");

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

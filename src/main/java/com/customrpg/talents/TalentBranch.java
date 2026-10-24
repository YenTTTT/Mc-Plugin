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
    DARK("暗黑系", "永夜禁術 / 詛咒侵蝕 / 暗影召喚"),
    WEAPON("武器系", "強化武器攻擊與戰鬥技巧"),
    TECH("科技系", "機械 / 裝置 / 特殊戰術技能"),
    NATURE("自然系", "治療 / 控制 / 自然元素技能"),
    SUKUNA("宿儺系", "咒術王的斬擊與火焰術式"),
    BLOOD("赤血操術系", "操控血液的禁忌術式"),
    ASSASSIN("刺客系", "影襲 / 連擊 / 收割的暗殺技巧"),
    BEAST("野獸系", "馴服野獸召喚同伴 / 化身野獸變身戰鬥"),
    BOW("風獵者之道", "距離狙擊 / 箭雨連射 / 陷阱控場的弓箭流派"),
    FROST("冰霜系", "冰霜射線 / 緩速控場 / 暴風雪領域"),
    STORM("雷霆系", "連鎖雷電 / 疾風位移 / 近身風暴"),
    EARTH("大地守衛系", "山岳誓約 / 正面岩盾 / 團隊承傷"),
    LIGHT("聖光系", "治療 / 淨化 / 聖光穿刺 / 同隊支援"),
    HUNTER("獵人・念能力", "纏練凝 / 猜猜拳 / 神速 / 伸縮自在的愛 / 百式觀音"),
    NECROMANCER("死靈法師系", "亡靈召喚 / 骸骨魔法 / 獻祭融合 / 亡者君臨"),
    CHICKEN("養雞大師", "雞蛋投擲 / 小雞孵化 / 雞群衝鋒 / 咕咕霸權");

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

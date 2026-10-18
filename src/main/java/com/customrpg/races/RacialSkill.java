package com.customrpg.races;

import java.util.Arrays;
import java.util.Locale;

/** One distinct active ability for each built-in race; durations and cooldowns are seconds. */
public enum RacialSkill {
    HUMAN("human", "不屈戰意", "10 秒內普攻傷害 +20%，立即回復 15 魔力", 45, 0, 10),
    ELF("elf", "疾風祝福", "10 秒內移速 +30%，遠程普攻傷害 +25%", 40, 20, 10),
    ORC("orc", "狂戰怒吼", "8 秒內近戰普攻傷害 +35%，受到傷害 +15%", 45, 20, 8),
    DWARF("dwarf", "山脈壁壘", "10 秒內減傷 35%，移速 -15%", 50, 20, 10),
    UNDEAD("undead", "靈魂汲取", "汲取視線內 12 格敵人生命，造成 12 傷害並按實際傷害治療", 30, 20, 0),
    DEMON("demon", "煉獄爆發", "對 5 格內可見敵人造成 10 傷害並點燃 4 秒", 40, 30, 0),
    ANGEL("angel", "聖光庇佑", "治療自己及 6 格內可見同隊玩家 12 生命，自己獲得 6 秒減傷 20%", 45, 30, 6),
    DRAGONKIN("dragonkin", "龍翼覺醒", "展開火焰粒子龍翼並飛行 12 秒，普攻追加 4 傷害並點燃 3 秒", 60, 35, 12),
    BEASTKIN("beastkin", "獵獸突襲", "向前撲躍，6 秒內首次近戰普攻傷害 +60%", 30, 15, 6),
    DARK_ELF("dark_elf", "暗影帷幕", "8 秒內首發近戰普攻傷害 +50%，持續減傷 20%", 40, 20, 8),
    HALFLING("halfling", "幸運閃避", "8 秒內閃避下一次直接攻擊，移速 +25%", 35, 15, 8),
    GOLEM("golem", "大地震盪", "對 4 格內可見敵人造成 8 傷害並擊退，自己獲得 8 秒減傷 25%", 45, 25, 8);

    private final String raceId, displayName, description;
    private final int cooldown, manaCost, duration;

    RacialSkill(String raceId, String displayName, String description, int cooldown, int manaCost, int duration) {
        this.raceId = raceId; this.displayName = displayName; this.description = description;
        this.cooldown = cooldown; this.manaCost = manaCost; this.duration = duration;
    }

    public String raceId() { return raceId; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public int cooldown() { return cooldown; }
    public int manaCost() { return manaCost; }
    public int duration() { return duration; }
    public static RacialSkill forRace(String id) {
        if (id == null) return null;
        String normalized = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(skill -> skill.raceId.equals(normalized)).findFirst().orElse(null);
    }
}

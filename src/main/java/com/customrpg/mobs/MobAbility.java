package com.customrpg.mobs;

/** Shared, telegraphed moves; each template supplies its own sequence, scale, range and timing. */
public enum MobAbility {
    CLEAVE("扇形揮擊"), CHARGE("直線衝鋒"), STOMP("震地重擊"),
    POISON_CLOUD("毒霧爆發"), FROST_BOLT("冰霜射線"), FIRE_BREATH("火焰吐息"),
    ARCANE_BURST("奧術落點"), SHADOW_LANCE("暗影穿刺"), THUNDER_STRIKE("雷擊落點"),
    HOWL("族群戰吼"), FORTIFY("防禦姿態"), WIND_GUST("疾風推擊"),
    LEAP("飛撲"), VOLLEY("三連箭"), SOUL_DRAIN("生命汲取"),
    EXPLOSIVE_PULSE("蓄力爆破"), FLEE("逃離威脅");

    private final String displayName;
    MobAbility(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}

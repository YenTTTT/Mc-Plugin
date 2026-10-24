package com.customrpg.talents;

import org.bukkit.Material;

/** Stable ownership of talent IDs; rearranging a role never changes learned skills. */
public enum HunterPath {
    GON("小杰・強化與猜猜拳", Material.BAMBOO, 10, 0xFF9A35),
    KILLUA("奇犽・雷電與暗殺", Material.LIGHTNING_ROD, 12, 0x8CEBFF),
    KURAPIKA("酷拉皮卡・鎖鏈與誓約", Material.IRON_CHAIN, 14, 0xF4C65D),
    HISOKA("西索・魔術與伸縮念線", Material.PAPER, 16, 0xFF88C2),
    NETERO("尼特羅・心源流與觀音", Material.GOLD_INGOT, 30, 0xFFE29A),
    COMMON("共通・念的修行", Material.AMETHYST_SHARD, 32, 0xC4A4FF);
    public final String title;
    public final Material icon;
    public final int menuSlot, color;
    HunterPath(String title, Material icon, int menuSlot, int color) {
        this.title = title; this.icon = icon; this.menuSlot = menuSlot; this.color = color;
    }
    public static HunterPath of(String id) {
        if (id.startsWith("nen_gon_") || id.equals("nen_rock") || id.equals("nen_scissors") || id.equals("nen_paper")) return GON;
        if (id.startsWith("nen_killua_") || id.equals("nen_lightning") || id.equals("nen_godspeed")) return KILLUA;
        if (id.startsWith("nen_kurapika_") || id.equals("nen_chain")) return KURAPIKA;
        if (id.startsWith("nen_hisoka_") || id.equals("nen_bungee")) return HISOKA;
        if (id.startsWith("nen_netero_") || id.equals("nen_bodhisattva")) return NETERO;
        return COMMON;
    }
}

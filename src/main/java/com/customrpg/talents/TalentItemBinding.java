package com.customrpg.talents;

import org.bukkit.Material;

/** Material aliases used by both existing deployed talent descriptions and bundled defaults. */
public final class TalentItemBinding {
    private TalentItemBinding() { }

    public static boolean matches(String mechanism, Material material) {
        if (mechanism == null || material == null || material == Material.AIR) return false;
        String name = material.name();
        if (mechanism.equalsIgnoreCase(name)) return true;
        boolean melee = name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_HOE");
        boolean staff = material == Material.STICK || material == Material.BLAZE_ROD
                || material == Material.BREEZE_ROD || material == Material.ENCHANTED_BOOK;
        boolean gun = material == Material.IRON_HORSE_ARMOR || material == Material.GOLDEN_HORSE_ARMOR
                || material == Material.DIAMOND_HORSE_ARMOR;
        if (mechanism.contains("武器系終極")) return melee || staff || gun;
        if (mechanism.contains("持武器")) return melee || staff;
        if (mechanism.contains("近戰武器")) return melee;
        if (mechanism.contains("槍械武器")) return gun;
        if (mechanism.contains("法杖")) return staff;
        if (mechanism.contains("鐮刀")) return name.endsWith("_HOE");
        if (mechanism.contains("匕首")) return name.endsWith("_SWORD");
        return switch (material) {
            case EGG -> mechanism.contains("雞蛋");
            case SNOWBALL -> mechanism.contains("雪球");
            case GOLD_NUGGET -> mechanism.contains("金粒");
            case BONE -> mechanism.contains("骨頭");
            case STICK -> mechanism.contains("木棒");
            case PRISMARINE_CRYSTALS -> mechanism.contains("海靈晶體");
            case PRISMARINE_SHARD -> mechanism.contains("海磷碎片");
            case NETHER_STAR -> mechanism.contains("地獄之星");
            case DIAMOND_SWORD -> mechanism.contains("鑽石劍");
            case REDSTONE -> mechanism.contains("紅石粉");
            case SPIDER_EYE -> mechanism.contains("蜘蛛眼");
            case WITHER_ROSE -> mechanism.contains("凋零玫瑰");
            case BLAZE_POWDER -> mechanism.contains("烈焰粉");
            case FLINT -> mechanism.contains("燧石");
            case GLOWSTONE_DUST -> mechanism.contains("螢石粉");
            case AMETHYST_SHARD -> mechanism.contains("紫水晶碎片");
            case BOW -> mechanism.contains("弓");
            default -> false;
        };
    }

    public static String triggerName(String trigger) {
        if (trigger == null) return "未設定";
        return switch (trigger) {
            case "LEFT_CLICK" -> "左鍵";
            case "RIGHT_CLICK" -> "右鍵";
            case "LEFT_CLICK_SNEAK" -> "蹲下左鍵";
            case "RIGHT_CLICK_SNEAK" -> "蹲下右鍵";
            default -> trigger;
        };
    }
}

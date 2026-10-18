package com.customrpg.worldevent.location;

import org.bukkit.Material;
import java.util.*;

/** Deliberate whitelist: no inventories, tile states, fluids, waterlogging, gravity, logs or multi-block plants. */
public final class TerrainValidator {
    private static final Set<Material> GROUND=EnumSet.of(Material.GRASS_BLOCK,Material.DIRT,Material.COARSE_DIRT,
            Material.ROOTED_DIRT,Material.PODZOL,Material.MYCELIUM,Material.STONE,Material.ANDESITE,Material.DIORITE,
            Material.GRANITE,Material.TUFF,Material.DEEPSLATE);
    private static final Set<Material> COVER=EnumSet.of(Material.AIR,Material.CAVE_AIR,Material.SHORT_GRASS,
            Material.FERN,Material.DANDELION,Material.POPPY);
    private static final Set<Material> TEST_GROUND=EnumSet.of(Material.COBBLESTONE,Material.MOSSY_COBBLESTONE,
            Material.STONE_BRICKS,Material.MOSSY_STONE_BRICKS,Material.CRACKED_STONE_BRICKS,Material.SMOOTH_STONE,
            Material.BRICKS,Material.SANDSTONE,Material.RED_SANDSTONE,Material.CALCITE,Material.DRIPSTONE_BLOCK,
            Material.MOSS_BLOCK,Material.CLAY,Material.SNOW_BLOCK,Material.OBSIDIAN,Material.CRYING_OBSIDIAN,
            Material.BLACKSTONE,Material.BASALT,Material.END_STONE,Material.NETHERRACK,
            Material.COAL_ORE,Material.IRON_ORE,Material.COPPER_ORE,Material.GOLD_ORE,Material.REDSTONE_ORE,
            Material.LAPIS_ORE,Material.EMERALD_ORE,Material.DIAMOND_ORE);
    private static final Set<Material> TEST_COVER=EnumSet.of(Material.TALL_GRASS,Material.LARGE_FERN,
            Material.SUNFLOWER,Material.LILAC,Material.ROSE_BUSH,Material.PEONY,Material.SNOW,
            Material.BLUE_ORCHID,Material.ALLIUM,Material.AZURE_BLUET,Material.RED_TULIP,Material.ORANGE_TULIP,
            Material.WHITE_TULIP,Material.PINK_TULIP,Material.OXEYE_DAISY,Material.CORNFLOWER,Material.LILY_OF_THE_VALLEY);
    public static boolean accepts(Material material) { return GROUND.contains(material)||COVER.contains(material); }
    public static boolean stableGround(Material material) { return GROUND.contains(material); }
    /** Explicit admin test opt-in; still no fluids, gravity, inventories, doors, beds or machinery. */
    public static boolean acceptsForTesting(Material material) {
        return accepts(material)||stableGroundForTesting(material)||TEST_COVER.contains(material);
    }
    public static boolean stableGroundForTesting(Material material) {
        if(stableGround(material)||TEST_GROUND.contains(material)) return true;
        String name=material.name();
        return name.endsWith("_LOG")||name.endsWith("_WOOD")||name.endsWith("_PLANKS")
                ||name.endsWith("_STEM")||name.endsWith("_HYPHAE")||name.endsWith("_LEAVES");
    }
    private TerrainValidator() {}
}

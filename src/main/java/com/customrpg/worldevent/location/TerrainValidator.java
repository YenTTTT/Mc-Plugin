package com.customrpg.worldevent.location;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import java.util.EnumSet;
import java.util.Set;

/** A site can host an event regardless of block type; only fully restorable blocks enter its crater snapshot. */
public final class TerrainValidator {
    private static final Set<Material> RESTORABLE=EnumSet.of(Material.AIR,Material.CAVE_AIR,Material.VOID_AIR,
            Material.GRASS_BLOCK,Material.DIRT,Material.COARSE_DIRT,Material.ROOTED_DIRT,Material.PODZOL,
            Material.MYCELIUM,Material.STONE,Material.COBBLESTONE,Material.MOSSY_COBBLESTONE,
            Material.ANDESITE,Material.DIORITE,Material.GRANITE,Material.TUFF,Material.DEEPSLATE,
            Material.COBBLED_DEEPSLATE,Material.SAND,Material.RED_SAND,Material.GRAVEL,
            Material.SANDSTONE,Material.RED_SANDSTONE,Material.CLAY,Material.MUD,Material.MOSS_BLOCK,
            Material.SNOW_BLOCK,Material.ICE,Material.PACKED_ICE,Material.BLUE_ICE,Material.CALCITE,
            Material.DRIPSTONE_BLOCK,Material.BASALT,Material.BLACKSTONE,Material.SMOOTH_STONE,
            Material.STONE_BRICKS,Material.MOSSY_STONE_BRICKS,Material.CRACKED_STONE_BRICKS,
            Material.BRICKS,Material.OBSIDIAN,Material.CRYING_OBSIDIAN,Material.END_STONE,
            Material.NETHERRACK,Material.MAGMA_BLOCK,Material.ANCIENT_DEBRIS);
    public static boolean canTemporarilyReplace(Block block) {
        Material material=block.getType();
        // BlockData alone cannot restore inventories, block entities, fluid state, or multipart mechanisms.
        String name=material.name();
        if(!RESTORABLE.contains(material)&&!name.endsWith("_ORE")&&!name.endsWith("_LOG")
                &&!name.endsWith("_WOOD")&&!name.endsWith("_PLANKS")
                &&!name.endsWith("_STEM")&&!name.endsWith("_HYPHAE")) return false;
        BlockData data=block.getBlockData();
        if(data instanceof Waterlogged waterlogged&&waterlogged.isWaterlogged()) return false;
        return !(block.getState() instanceof TileState);
    }
    private TerrainValidator() {}
}

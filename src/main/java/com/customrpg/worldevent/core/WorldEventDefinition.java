package com.customrpg.worldevent.core;

import com.customrpg.worldevent.terrain.TerrainSnapshot.Position;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.Consumer;

/** New event types supply content; scheduling, safety, snapshots, combat and rewards stay shared. */
public interface WorldEventDefinition {
    record Requirements(int radius, int depth, int headroom, int maxSlope) {
        public Requirements {
            if(radius<1||radius>24||depth<1||depth>8||headroom<1||headroom>32||maxSlope<0||maxSlope>8) throw new IllegalArgumentException("Unsafe event terrain bounds");
        }
    }
    record Column(int x, int z) {}
    record MobSpec(String template, int count, String rank) {}
    interface Animation extends AutoCloseable {
        boolean tick();
        @Override void close();
    }
    String type();
    Requirements requirements();
    long durationMillis();
    long cleanupDelayMillis();
    Map<Position,String> generate(Map<Column,Integer> heights, int x, int y, int z, long seed);
    List<MobSpec> mobs();
    Map<String,Long> rewards();
    double minimumDamage();
    double minimumShare();
    Animation animation(JavaPlugin plugin, WorldEventInstance instance, World world, Consumer<Entity> tag);
}

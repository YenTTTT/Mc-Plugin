package com.customrpg.worldevent.core;

import java.util.*;

/** Persistent domain data. Bukkit objects and scheduler handles never enter the repository. */
public final class WorldEventInstance {
    public enum MobState { PLANNED, SPAWNING, ALIVE, DEAD, UNLOADED, REMOVED_BY_CLEANUP }

    public static final class EventMob {
        public final UUID id;
        public final String template, rank;
        public final int level;
        public UUID entity;
        public int chunkX, chunkZ;
        public MobState state = MobState.PLANNED;
        public final Map<UUID, Double> damage = new LinkedHashMap<>();

        public EventMob(UUID id, String template, String rank, int level) {
            this.id = Objects.requireNonNull(id);
            this.template = Objects.requireNonNull(template);
            this.rank = Objects.requireNonNull(rank);
            this.level = level;
        }

        public boolean beginSpawn() {
            if (state != MobState.PLANNED) return false;
            state = MobState.SPAWNING;
            return true;
        }

        public boolean die() {
            if (state != MobState.ALIVE && state != MobState.UNLOADED) return false;
            state = MobState.DEAD;
            return true;
        }
    }

    public final UUID id, world;
    public final String type, worldName;
    public final int x, y, z, radius, minLevel, maxLevel, level;
    public final long seed, startedAt, expiresAt;
    public WorldEventState state = WorldEventState.SCHEDULED;
    public long cleanupAt;
    public boolean successful;
    public boolean testMode;
    public String diagnostic = "";
    public final Map<UUID, EventMob> mobs = new LinkedHashMap<>();
    public final Map<UUID, Double> contribution = new LinkedHashMap<>();
    public final Map<UUID, Integer> killParticipation = new LinkedHashMap<>();
    public final Set<UUID> claimed = new LinkedHashSet<>();
    public final Map<String, Long> rewards = new LinkedHashMap<>();
    public double minimumDamage = 1, minimumShare = 0.01;

    public WorldEventInstance(UUID id, String type, UUID world, String worldName, int x, int y, int z,
                              int radius, int minLevel, int maxLevel, int level, long seed,
                              long startedAt, long expiresAt) {
        this.id = Objects.requireNonNull(id); this.type = Objects.requireNonNull(type);
        this.world = Objects.requireNonNull(world); this.worldName = Objects.requireNonNull(worldName);
        this.x = x; this.y = y; this.z = z; this.radius = radius;
        this.minLevel = minLevel; this.maxLevel = maxLevel; this.level = level;
        this.seed = seed; this.startedAt = startedAt; this.expiresAt = expiresAt;
        if (radius < 1 || radius > 48 || minLevel < 1 || maxLevel < minLevel || level < 1 || expiresAt <= startedAt) {
            throw new IllegalArgumentException("Invalid world event bounds");
        }
    }

    public void transition(WorldEventState next) {
        if (!state.canTransitionTo(next)) throw new IllegalStateException(state + " -> " + next);
        state = next;
        if (next == WorldEventState.COMPLETED) successful = true;
    }

    public boolean allDefeated() {
        return !mobs.isEmpty() && mobs.values().stream().allMatch(m -> m.state == MobState.DEAD);
    }

    public boolean reserved() { return state != WorldEventState.CLOSED; }

    public boolean contains(UUID world, int x, int z, int padding) {
        return this.world.equals(world) && Math.abs((long) x - this.x) <= radius + padding
                && Math.abs((long) z - this.z) <= radius + padding;
    }
}

package com.customrpg.worldevent.terrain;

import java.util.*;

public record TerrainSnapshot(UUID eventId, UUID world, int centerX, int centerY, int centerZ,
                              int radius, List<BlockChange> blocks) {
    public record Position(int x, int y, int z) {
        public String key() { return x + "," + y + "," + z; }
        public static Position parse(String text) {
            String[] parts = text.split(",", -1);
            if (parts.length != 3) throw new IllegalArgumentException("Invalid block position");
            return new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
    }
    public record BlockChange(Position position, String before, String after) {
        public BlockChange { Objects.requireNonNull(position); Objects.requireNonNull(before); Objects.requireNonNull(after); }
    }

    public TerrainSnapshot {
        Objects.requireNonNull(eventId); Objects.requireNonNull(world);
        blocks = List.copyOf(blocks);
        if (radius < 1 || radius > 48 || blocks.isEmpty() || blocks.size() > 30_000) {
            throw new IllegalArgumentException("Invalid snapshot size");
        }
        Set<Position> seen = new HashSet<>();
        for (BlockChange block : blocks) {
            if (Math.abs((long) block.position.x - centerX) > radius
                    || Math.abs((long) block.position.z - centerZ) > radius || !seen.add(block.position)) {
                throw new IllegalArgumentException("Snapshot outside bounds or duplicate position");
            }
        }
    }
}

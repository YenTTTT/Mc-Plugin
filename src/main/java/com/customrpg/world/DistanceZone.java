package com.customrpg.world;

public record DistanceZone(String id, String name, double minDistance, double maxDistance,
                           int minLevel, int maxLevel) {
    public DistanceZone {
        if (!Double.isFinite(minDistance) || !Double.isFinite(maxDistance)
                || minDistance < 0 || maxDistance <= minDistance || minLevel < 1 || maxLevel < minLevel) {
            throw new IllegalArgumentException("Invalid distance zone: " + id);
        }
    }

    public int baseLevel(double distance) {
        double progress = Math.clamp((distance - minDistance) / (maxDistance - minDistance), 0.0, 1.0);
        return (int) Math.round(minLevel + progress * (maxLevel - minLevel));
    }

    public int clampLevel(int level) {
        return Math.clamp(level, minLevel, maxLevel);
    }
}

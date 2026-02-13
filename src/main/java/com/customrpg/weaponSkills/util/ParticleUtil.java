package com.customrpg.weaponSkills.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ParticleUtil
 *
 * Reusable particle helper.
 */
public class ParticleUtil {

    public enum AnimationStyle {
        NONE,
        FALLING,
        SPIRAL,
        CONE,
        LINE_STEPS
    }

    public void burst(Location location, String particleName, int count, double offX, double offY, double offZ, double extra) {
        if (location == null) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        Particle particle = Particle.CRIT;
        try {
            particle = Particle.valueOf(String.valueOf(particleName).trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
        }

        int safeCount = Math.max(1, count);

        // Paper 1.21+ is stricter about particle data. For particles that require data, we must supply it.
        // For particles that DON'T require data, prefer the no-data overload so 'extra' won't be
        // interpreted as a missing data payload.
        try {
            switch (particle) {
                case DUST -> world.spawnParticle(particle, location, safeCount, offX, offY, offZ, extra,
                        new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.0f));

                case DUST_COLOR_TRANSITION -> world.spawnParticle(particle, location, safeCount, offX, offY, offZ, extra,
                        new Particle.DustTransition(Color.fromRGB(255, 255, 255), Color.fromRGB(255, 80, 80), 1.0f));

                // SCULK_CHARGE, DRAGON_BREATH, SHRIEK 等需要 Float/Integer 數據
                case SCULK_CHARGE -> world.spawnParticle(particle, location, safeCount, offX, offY, offZ, extra, 0.0f);

                case DRAGON_BREATH -> world.spawnParticle(particle, location, safeCount, offX, offY, offZ, extra, 1.0f);

                case SHRIEK -> world.spawnParticle(particle, location, safeCount, offX, offY, offZ, extra, 0);

                // These require item/block data; current config format doesn't provide it, so fallback.
                case ITEM, BLOCK, BLOCK_MARKER, FALLING_DUST, DUST_PILLAR, BLOCK_CRUMBLE ->
                        world.spawnParticle(Particle.CRIT, location, safeCount, offX, offY, offZ, extra);

                default -> {
                    // No-data overload: count + offsets only. (No 'extra' here.)
                    world.spawnParticle(particle, location, safeCount, offX, offY, offZ);
                }
            }
        } catch (IllegalArgumentException ex) {
            // Any mismatch between particle and expected data -> fallback.
            world.spawnParticle(Particle.CRIT, location, safeCount, offX, offY, offZ, extra);
        }
    }

    public void trail(Player player, String particleName, int steps, double stepSize) {
        if (player == null) {
            return;
        }
        Location origin = player.getLocation().clone();
        Vector dir = origin.getDirection().normalize();
        for (int i = 1; i <= steps; i++) {
            Location p = origin.clone().add(dir.clone().multiply(i * stepSize));
            burst(p, particleName, 3, 0.1, 0.1, 0.1, 0.0);
        }
    }

    public void playParticles(Location origin, Vector direction, Location center, List<Map<String, Object>> particles) {
        if (particles == null || particles.isEmpty()) {
            return;
        }
        for (Map<String, Object> p : particles) {
            if (p == null) {
                continue;
            }

            String id = String.valueOf(p.getOrDefault("id", "")).trim();
            if (id.isBlank()) {
                continue;
            }

            int count = toInt(p.getOrDefault("count", 10), 10);
            double offX = toDouble(p.getOrDefault("offset-x", 0.2), 0.2);
            double offY = toDouble(p.getOrDefault("offset-y", 0.2), 0.2);
            double offZ = toDouble(p.getOrDefault("offset-z", 0.2), 0.2);
            double extra = toDouble(p.getOrDefault("extra", 0.0), 0.0);

            AnimationStyle style = AnimationStyle.NONE;
            Object animObj = p.get("animation");
            if (animObj instanceof Map<?, ?> animMap) {
                Object styleObj = animMap.get("style");
                if (styleObj != null) {
                    style = parseStyle(String.valueOf(styleObj));
                }
            }

            // LINE_STEPS: if center exists, compute step size to land exactly on target.
            if (style == AnimationStyle.LINE_STEPS && center != null) {
                int steps = Math.max(1, toInt(p.getOrDefault("steps", 8), 8));
                double dist = origin.distance(center);
                if (dist > 0.0001) {
                    p.put("computed-step-size", dist / steps);
                }
            }

            switch (style) {
                case FALLING -> falling(origin, id, count, offX, offY, offZ, extra, p);
                case SPIRAL -> spiral(center != null ? center : origin, direction, id, count, extra, p);
                case CONE -> cone(origin, direction, id, count, extra, p);
                case LINE_STEPS -> lineSteps(origin, direction, id, count, offX, offY, offZ, extra, p);
                case NONE -> burst(center != null ? center : origin, id, count, offX, offY, offZ, extra);
            }
        }
    }

    private void falling(Location origin, String particle, int count, double offX, double offY, double offZ, double extra, Map<String, Object> cfg) {
        Location base = origin.clone();
        double height = toDouble(cfg.getOrDefault("height", 2.5), 2.5);
        int steps = Math.max(1, toInt(cfg.getOrDefault("steps", 6), 6));
        for (int i = 0; i < steps; i++) {
            double y = height * (1.0 - (i / (double) steps));
            burst(base.clone().add(0, y, 0), particle, count, offX, offY, offZ, extra);
        }
    }

    private void spiral(Location center, Vector direction, String particle, int count, double extra, Map<String, Object> cfg) {
        Vector dir = direction == null ? new Vector(1, 0, 0) : direction.clone().normalize();
        double radius = toDouble(cfg.getOrDefault("radius", 1.2), 1.2);
        int points = Math.max(6, toInt(cfg.getOrDefault("points", 24), 24));
        double height = toDouble(cfg.getOrDefault("height", 1.5), 1.5);

        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-6) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        Vector up = right.clone().crossProduct(dir).normalize();

        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            double angle = t * Math.PI * 2.0 * toDouble(cfg.getOrDefault("turns", 2.0), 2.0);
            double x = Math.cos(angle) * radius;
            double y = t * height;
            double z = Math.sin(angle) * radius;

            Location loc = center.clone().add(right.clone().multiply(x)).add(up.clone().multiply(y)).add(dir.clone().multiply(z));
            burst(loc, particle, Math.max(1, count / 4), 0.05, 0.05, 0.05, extra);
        }
    }

    private void cone(Location origin, Vector direction, String particle, int count, double extra, Map<String, Object> cfg) {
        Vector dir = direction == null ? new Vector(1, 0, 0) : direction.clone().normalize();
        double range = toDouble(cfg.getOrDefault("range", 3.0), 3.0);
        double angleDeg = toDouble(cfg.getOrDefault("angle", 35.0), 35.0);
        int rays = Math.max(4, toInt(cfg.getOrDefault("rays", 8), 8));
        int steps = Math.max(3, toInt(cfg.getOrDefault("steps", 10), 10));

        for (int r = 0; r < rays; r++) {
            Vector ray = randomConeVector(dir, angleDeg);
            for (int i = 1; i <= steps; i++) {
                double dist = range * (i / (double) steps);
                Location loc = origin.clone().add(ray.clone().multiply(dist));
                burst(loc, particle, Math.max(1, count / (rays * 2)), 0.05, 0.05, 0.05, extra);
            }
        }
    }

    private void lineSteps(Location origin, Vector direction, String particle, int count, double offX, double offY, double offZ, double extra, Map<String, Object> cfg) {
        Vector dir = direction == null ? origin.getDirection() : direction.clone();
        if (dir.lengthSquared() < 1e-6) {
            dir = new Vector(1, 0, 0);
        }
        dir.normalize();

        int steps = Math.max(1, toInt(cfg.getOrDefault("steps", 8), 8));
        double stepSize;

        // step-size supports:
        // - number (blocks)
        // - "auto" (computed in playParticles when center is provided)
        Object rawStepSize = cfg.getOrDefault("step-size", "auto");
        if (rawStepSize == null || String.valueOf(rawStepSize).trim().equalsIgnoreCase("auto")) {
            // fallback: a reasonable default; playParticles may override by injecting computed-step-size
            stepSize = 0.35;
        } else {
            stepSize = toDouble(rawStepSize, 0.35);
        }

        // If playParticles precomputed a step size, prefer it.
        if (cfg.containsKey("computed-step-size")) {
            stepSize = toDouble(cfg.get("computed-step-size"), stepSize);
        }

        for (int i = 1; i <= steps; i++) {
            Location p = origin.clone().add(dir.clone().multiply(i * stepSize));
            burst(p, particle, Math.max(1, count / steps), offX, offY, offZ, extra);
        }
    }

    private Vector randomConeVector(Vector dir, double angleDeg) {
        // simple random spread around direction
        double angleRad = Math.toRadians(angleDeg);
        double u = Math.random();
        double v = Math.random();
        double theta = 2.0 * Math.PI * u;
        double phi = Math.acos(1.0 - v * (1.0 - Math.cos(angleRad)));

        double x = Math.sin(phi) * Math.cos(theta);
        double y = Math.sin(phi) * Math.sin(theta);
        double z = Math.cos(phi);

        Vector w = dir.clone().normalize();
        Vector a = Math.abs(w.getY()) < 0.99 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector uVec = a.clone().crossProduct(w).normalize();
        Vector vVec = w.clone().crossProduct(uVec).normalize();

        return uVec.multiply(x).add(vVec.multiply(y)).add(w.multiply(z)).normalize();
    }

    private AnimationStyle parseStyle(String s) {
        if (s == null) {
            return AnimationStyle.NONE;
        }
        String v = s.trim().toUpperCase(Locale.ROOT);
        try {
            return AnimationStyle.valueOf(v);
        } catch (Exception ignored) {
            return AnimationStyle.NONE;
        }
    }

    private int toInt(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ignored) {
            return def;
        }
    }

    private double toDouble(Object v, double def) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception ignored) {
            return def;
        }
    }
}

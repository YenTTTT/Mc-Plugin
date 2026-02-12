package com.customrpg.weaponSkills.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * ParticleUtil
 *
 * Reusable particle helper.
 */
public class ParticleUtil {

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
}

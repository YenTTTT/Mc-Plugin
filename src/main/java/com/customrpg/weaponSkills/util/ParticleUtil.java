package com.customrpg.weaponSkills.util;

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

        world.spawnParticle(particle, location, Math.max(1, count), offX, offY, offZ, extra);
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


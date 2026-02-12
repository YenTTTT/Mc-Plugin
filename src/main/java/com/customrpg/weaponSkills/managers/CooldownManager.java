package com.customrpg.weaponSkills.managers;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CooldownManager
 *
 * Per-player cooldown tracking.
 * Millisecond-based.
 */
public class CooldownManager {

    private final Map<UUID, Map<String, Long>> cooldownUntil = new ConcurrentHashMap<>();

    private static String norm(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    public boolean canCast(Player player, String key) {
        if (player == null) {
            return false;
        }
        String k = norm(key);
        if (k.isEmpty()) {
            return false;
        }

        Map<String, Long> perPlayer = cooldownUntil.get(player.getUniqueId());
        if (perPlayer == null) {
            return true;
        }

        Long until = perPlayer.get(k);
        if (until == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (until <= now) {
            perPlayer.remove(k);
            return true;
        }

        return false;
    }

    public void applyCooldown(Player player, String key, long cooldownMillis) {
        if (player == null) {
            return;
        }
        String k = norm(key);
        if (k.isEmpty()) {
            return;
        }
        if (cooldownMillis <= 0) {
            return;
        }

        long until = System.currentTimeMillis() + cooldownMillis;
        cooldownUntil.compute(player.getUniqueId(), (uuid, perPlayer) -> {
            if (perPlayer == null) {
                perPlayer = new ConcurrentHashMap<>();
            }
            perPlayer.put(k, until);
            return perPlayer;
        });
    }

    public long getRemainingCooldown(Player player, String key) {
        if (player == null) {
            return 0L;
        }
        String k = norm(key);
        if (k.isEmpty()) {
            return 0L;
        }

        Map<String, Long> perPlayer = cooldownUntil.get(player.getUniqueId());
        if (perPlayer == null) {
            return 0L;
        }

        Long until = perPlayer.get(k);
        if (until == null) {
            return 0L;
        }

        long now = System.currentTimeMillis();
        if (until <= now) {
            perPlayer.remove(k);
            return 0L;
        }

        return until - now;
    }
}


package com.customrpg.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CooldownUtil - Utility class for managing skill cooldowns
 *
 * This class provides a thread-safe cooldown management system for skills.
 * It tracks when skills were last used by players and calculates remaining
 * cooldown times.
 *
 * Features:
 * - Per-player, per-skill cooldown tracking
 * - Thread-safe operations
 * - Automatic cooldown expiration checking
 * - Bulk cooldown clearing
 */
public class CooldownUtil {

    private final Map<UUID, Map<String, Long>> cooldowns;

    /**
     * Constructor for CooldownUtil
     * Initializes the cooldown storage with thread-safe maps
     */
    public CooldownUtil() {
        this.cooldowns = new ConcurrentHashMap<>();
    }

    /**
     * Set a cooldown for a player's skill
     * @param playerUUID The player's UUID
     * @param skillKey The skill identifier
     * @param cooldownSeconds The cooldown duration in seconds
     */
    public void setCooldown(UUID playerUUID, String skillKey, int cooldownSeconds) {
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>());

        long expirationTime = System.currentTimeMillis() + (cooldownSeconds * 1000L);
        playerCooldowns.put(skillKey, expirationTime);
    }

    /**
     * Check if a player can use a skill (not on cooldown)
     * @param playerUUID The player's UUID
     * @param skillKey The skill identifier
     * @return true if the skill can be used, false if on cooldown
     */
    public boolean canUse(UUID playerUUID, String skillKey) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerUUID);
        if (playerCooldowns == null) {
            return true;
        }

        Long expirationTime = playerCooldowns.get(skillKey);
        if (expirationTime == null) {
            return true;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime >= expirationTime) {
            playerCooldowns.remove(skillKey);
            return true;
        }

        return false;
    }

    /**
     * Get the remaining cooldown time for a skill
     * @param playerUUID The player's UUID
     * @param skillKey The skill identifier
     * @return Remaining cooldown in seconds, 0 if not on cooldown
     */
    public long getRemainingCooldown(UUID playerUUID, String skillKey) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerUUID);
        if (playerCooldowns == null) {
            return 0;
        }

        Long expirationTime = playerCooldowns.get(skillKey);
        if (expirationTime == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long remainingMillis = expirationTime - currentTime;

        if (remainingMillis <= 0) {
            playerCooldowns.remove(skillKey);
            return 0;
        }

        return (remainingMillis + 999) / 1000;
    }

    /**
     * Clear all cooldowns for a specific player
     * @param playerUUID The player's UUID
     */
    public void clearPlayer(UUID playerUUID) {
        cooldowns.remove(playerUUID);
    }

    /**
     * Clear a specific skill cooldown for a player
     * @param playerUUID The player's UUID
     * @param skillKey The skill identifier
     */
    public void clearSkill(UUID playerUUID, String skillKey) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerUUID);
        if (playerCooldowns != null) {
            playerCooldowns.remove(skillKey);
        }
    }

    /**
     * Clear all cooldowns for all players
     * Useful for plugin shutdown or reload
     */
    public void clearAll() {
        cooldowns.clear();
    }

    /**
     * Get the number of players with active cooldowns
     * @return Number of players
     */
    public int getActivePlayerCount() {
        return cooldowns.size();
    }

    /**
     * Clean up expired cooldowns to free memory
     * This should be called periodically if desired
     */
    public void cleanupExpired() {
        long currentTime = System.currentTimeMillis();

        cooldowns.forEach((playerUUID, playerCooldowns) -> {
            playerCooldowns.entrySet().removeIf(entry -> entry.getValue() <= currentTime);
        });

        cooldowns.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}

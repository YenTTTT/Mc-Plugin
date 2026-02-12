package com.customrpg.weaponSkills.managers;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BuffManager (minimal skeleton)
 *
 * Tracks temporary buffs/debuffs with duration.
 * We keep the API simple now; extend with tick updates when needed.
 */
public class BuffManager {

    public enum BuffType {
        BURN
    }

    private static class BuffState {
        private final BuffType type;
        private final long expireAtMillis;

        private BuffState(BuffType type, long expireAtMillis) {
            this.type = type;
            this.expireAtMillis = expireAtMillis;
        }
    }

    private final Map<UUID, Map<BuffType, BuffState>> buffs = new ConcurrentHashMap<>();

    public void apply(Player player, BuffType type, long durationMillis) {
        if (player == null || type == null || durationMillis <= 0) {
            return;
        }
        long expireAt = System.currentTimeMillis() + durationMillis;
        buffs.compute(player.getUniqueId(), (uuid, map) -> {
            if (map == null) {
                map = new ConcurrentHashMap<>();
            }
            map.put(type, new BuffState(type, expireAt));
            return map;
        });
    }

    public boolean has(Player player, BuffType type) {
        if (player == null || type == null) {
            return false;
        }
        Map<BuffType, BuffState> map = buffs.get(player.getUniqueId());
        if (map == null) {
            return false;
        }
        BuffState state = map.get(type);
        if (state == null) {
            return false;
        }
        if (state.expireAtMillis <= System.currentTimeMillis()) {
            map.remove(type);
            return false;
        }
        return true;
    }
}


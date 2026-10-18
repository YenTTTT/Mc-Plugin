package com.customrpg.worldevent.core;

import org.bukkit.configuration.ConfigurationSection;

public record WorldEventSettings(boolean enabled, int minMinutes, int maxMinutes, int retrySeconds,
                                 int maxActive, int minimumPlayers, int attempts, int minDistance,
                                 int maxDistance, int padding, int blocksPerTick) {
    public static WorldEventSettings load(ConfigurationSection config) {
        if (config == null) throw new IllegalArgumentException("Missing world-events section");
        WorldEventSettings s = new WorldEventSettings(config.getBoolean("enabled", true), config.getInt("scheduler.min-interval-minutes",45),
                config.getInt("scheduler.max-interval-minutes",120), config.getInt("scheduler.retry-seconds",300),
                config.getInt("max-active-events",1), config.getInt("minimum-online-players",1), config.getInt("location.attempts-per-search",32),
                config.getInt("location.min-player-distance",64), config.getInt("location.max-player-distance",256),
                config.getInt("location.protection-padding",4), config.getInt("terrain.blocks-per-tick",128));
        if(s.minMinutes<1||s.maxMinutes<s.minMinutes||s.maxMinutes>10_080||s.retrySeconds<10||s.retrySeconds>86_400
                ||s.maxActive<1||s.maxActive>4||s.minimumPlayers<1||s.attempts<1||s.attempts>128
                ||s.minDistance<32||s.maxDistance<s.minDistance||s.maxDistance>1024||s.padding<2||s.padding>16
                ||s.blocksPerTick<1||s.blocksPerTick>512) throw new IllegalArgumentException("Invalid world event limits");
        return s;
    }
}

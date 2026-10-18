package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.world.DistanceZone;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class WorldZoneManager {
    private final CustomRPG plugin;
    private final List<DistanceZone> zones = new ArrayList<>();
    private FileConfiguration config;

    public WorldZoneManager(CustomRPG plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "config/world_zones.yml");
        if (!file.exists()) {
            plugin.saveResource("config/world_zones.yml", false);
        }
        FileConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        List<DistanceZone> loadedZones = new ArrayList<>();
        ConfigurationSection section = loaded.getConfigurationSection("zones");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection zone = section.getConfigurationSection(id);
                if (zone != null) {
                    loadedZones.add(new DistanceZone(id, zone.getString("name", id),
                            zone.getDouble("min-distance"), zone.getDouble("max-distance"),
                            zone.getInt("level-min"), zone.getInt("level-max")));
                }
            }
        }
        loadedZones.sort(Comparator.comparingDouble(DistanceZone::minDistance));
        if (loadedZones.isEmpty()) {
            throw new IllegalArgumentException("config/world_zones.yml must define distance zones");
        }
        for (int index = 1; index < loadedZones.size(); index++) {
            if (loadedZones.get(index).minDistance() != loadedZones.get(index - 1).maxDistance()) {
                throw new IllegalArgumentException("Distance zones must be contiguous and non-overlapping");
            }
        }
        config = loaded;
        zones.clear();
        zones.addAll(loadedZones);
    }

    public Location getOrigin(World world) {
        Location spawn = world.getSpawnLocation();
        String path = "worlds." + world.getName() + ".origin";
        if (config.isConfigurationSection(path)) {
            return new Location(world, config.getDouble(path + ".x", spawn.getX()), spawn.getY(),
                    config.getDouble(path + ".z", spawn.getZ()));
        }
        return spawn;
    }

    public double getDistance(Location location) {
        Location origin = getOrigin(location.getWorld());
        return Math.hypot(location.getX() - origin.getX(), location.getZ() - origin.getZ());
    }

    public DistanceZone getZone(Location location) {
        double distance = getDistance(location);
        for (DistanceZone zone : zones) {
            if (distance < zone.maxDistance()) {
                return zone;
            }
        }
        return zones.getLast();
    }

    public int getBaseLevel(Location location) {
        return Math.max(getDimensionMinLevel(location.getWorld()),
                getZone(location).baseLevel(getDistance(location)));
    }

    public int rollLevel(Location location) {
        DistanceZone zone = getZone(location);
        int variation = Math.clamp(config.getInt("level-variation", 2), 0, 100);
        int level = zone.clampLevel(zone.baseLevel(getDistance(location))
                + ThreadLocalRandom.current().nextInt(-variation, variation + 1));
        return Math.max(getDimensionMinLevel(location.getWorld()), level);
    }

    public int clampLevel(Location location, int level) {
        return Math.max(getDimensionMinLevel(location.getWorld()), getZone(location).clampLevel(level));
    }

    public int getDimensionMinLevel(World world) {
        return Math.max(1, config.getInt("dimensions." + world.getEnvironment().name() + ".min-level", 1));
    }

    public boolean isSafeZone(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        double radius = config.getDouble("dimensions." + location.getWorld().getEnvironment().name()
                + ".safe-radius", 0);
        return radius > 0 && getDistance(location) < radius;
    }

    public boolean isLegacyRegionsEnabled() {
        return config.getBoolean("legacy-regions-enabled", false);
    }

    public boolean isNotificationEnabled() {
        return config.getBoolean("zone-notifications", true);
    }

    public String getZoneKey(Location location) {
        return location.getWorld().getUID() + ":" + (plugin.getSafeZoneManager().isInSafeZone(location)
                ? "safe" : getZone(location).id());
    }

    public String getDescription(Location location) {
        if (plugin.getSafeZoneManager().isInSafeZone(location)) {
            return "§a安全區域";
        }
        DistanceZone zone = getZone(location);
        int minimum = Math.max(zone.minLevel(), getDimensionMinLevel(location.getWorld()));
        int maximum = Math.max(zone.maxLevel(), minimum);
        return "§e" + zone.name() + " §7｜ 建議等級 §fLv." + minimum + " ~ " + maximum;
    }
}

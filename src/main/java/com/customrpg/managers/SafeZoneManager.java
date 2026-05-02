package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SafeZoneManager - 管理圓形安全區域
 */
public class SafeZoneManager {

    private final CustomRPG plugin;
    private final File configFile;
    private final Map<String, SafeZone> safeZones = new LinkedHashMap<>();

    public SafeZoneManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config/safe_zones.yml");
        load();
    }

    public void reload() {
        load();
    }

    private void load() {
        safeZones.clear();

        if (!configFile.exists()) {
            plugin.saveResource("config/safe_zones.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection zonesSection = config.getConfigurationSection("zones");
        if (zonesSection == null) {
            plugin.getLogger().warning("[SafeZoneManager] No zones section found in config/safe_zones.yml");
            return;
        }

        for (String id : zonesSection.getKeys(false)) {
            ConfigurationSection section = zonesSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            String world = section.getString("world", "world");
            String name = section.getString("name", id);
            String type = section.getString("type", "SAFE_ZONE");
            double x = section.getDouble("center.x");
            double y = section.getDouble("center.y");
            double z = section.getDouble("center.z");
            double radius = Math.max(1.0, section.getDouble("radius", 200.0));

            safeZones.put(id.toLowerCase(), new SafeZone(id, name, type, world, x, y, z, radius));
        }

        plugin.getLogger().info("[SafeZoneManager] Loaded " + safeZones.size() + " safe zones");
    }

    public boolean isInSafeZone(Location location) {
        return getZoneAt(location) != null;
    }

    public SafeZone getZoneAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        String worldName = location.getWorld().getName();
        for (SafeZone zone : safeZones.values()) {
            if (zone.contains(worldName, location.getX(), location.getY(), location.getZ())) {
                return zone;
            }
        }
        return null;
    }

    public int getZoneCount() {
        return safeZones.size();
    }

    public Collection<SafeZone> getZones() {
        return new ArrayList<>(safeZones.values());
    }

    public static class SafeZone {
        private final String id;
        private final String name;
        private final String type;
        private final String world;
        private final double x;
        private final double y;
        private final double z;
        private final double radius;
        private final double radiusSquared;

        public SafeZone(String id, String name, String type, String world,
                        double x, double y, double z, double radius) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.radiusSquared = radius * radius;
        }

        public boolean contains(String worldName, double targetX, double targetY, double targetZ) {
            if (!world.equalsIgnoreCase(worldName)) {
                return false;
            }
            double dx = targetX - x;
            double dy = targetY - y;
            double dz = targetZ - z;
            return (dx * dx) + (dy * dy) + (dz * dz) <= radiusSquared;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getWorld() {
            return world;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public double getRadius() {
            return radius;
        }
    }
}


package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * ConfigManager - Manages multiple configuration files
 *
 * This class handles loading and accessing configuration files from the configs/ directory.
 * It supports nested folder structures like configs/weapons/swords.yml
 */
public class ConfigManager {

    private final CustomRPG plugin;
    private final Map<String, FileConfiguration> configs;

    public ConfigManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.configs = new HashMap<>();
        loadAllConfigs();
    }

    /**
     * Load all configuration files from configs/ directory
     */
    private void loadAllConfigs() {
        // Create configs directory structure
        File configsDir = new File(plugin.getDataFolder(), "configs");
        File weaponsDir = new File(configsDir, "weapons");

        // Ensure directories exist
        if (!weaponsDir.exists()) {
            weaponsDir.mkdirs();
        }

        // Load weapon configs
        loadConfig("configs/weapons/scythes.yml");
        loadConfig("configs/weapons/swords.yml");
        loadConfig("configs/weapons/axes.yml");

        // Load skill config
        loadConfig("configs/skills.yml");

        // Load mob config
        loadConfig("configs/mobs.yml");

        plugin.getLogger().info("Loaded " + configs.size() + " configuration file(s)");
    }

    /**
     * Load a specific configuration file
     * @param relativePath Path relative to plugin data folder (e.g., "configs/weapons/swords.yml")
     */
    private void loadConfig(String relativePath) {
        File configFile = new File(plugin.getDataFolder(), relativePath);

        // Save default file from resources if it doesn't exist
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                InputStream inputStream = plugin.getResource(relativePath);
                if (inputStream != null) {
                    Files.copy(inputStream, configFile.toPath());
                    plugin.getLogger().info("Created default config: " + relativePath);
                } else {
                    plugin.getLogger().warning("Could not find default config in resources: " + relativePath);
                    return;
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create config file: " + relativePath);
                e.printStackTrace();
                return;
            }
        }

        // Load the configuration
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        configs.put(relativePath, config);
        plugin.getLogger().info("Loaded config: " + relativePath);
    }

    /**
     * Get a configuration file
     * @param relativePath Path relative to plugin data folder
     * @return FileConfiguration, or null if not found
     */
    public FileConfiguration getConfig(String relativePath) {
        return configs.get(relativePath);
    }

    /**
     * Get all weapon configurations merged into one map
     * @return Map of weapon key to configuration section data
     */
    public Map<String, Map<String, Object>> getAllWeapons() {
        Map<String, Map<String, Object>> allWeapons = new HashMap<>();

        // Load from all weapon config files
        for (String configPath : configs.keySet()) {
            if (configPath.startsWith("configs/weapons/")) {
                FileConfiguration config = configs.get(configPath);
                for (String key : config.getKeys(false)) {
                    Map<String, Object> weaponData = new HashMap<>();
                    weaponData.put("name", config.getString(key + ".name"));
                    weaponData.put("material", config.getString(key + ".material"));
                    weaponData.put("damage-multiplier", config.getDouble(key + ".damage-multiplier"));
                    weaponData.put("special-effect", config.getString(key + ".special-effect"));
                    weaponData.put("lore", config.getStringList(key + ".lore"));
                    allWeapons.put(key, weaponData);
                }
            }
        }

        return allWeapons;
    }

    /**
     * Get all skill configurations
     * @return Map of skill key to configuration section data
     */
    public Map<String, Map<String, Object>> getAllSkills() {
        Map<String, Map<String, Object>> allSkills = new HashMap<>();
        FileConfiguration config = configs.get("configs/skills.yml");

        if (config != null) {
            for (String key : config.getKeys(false)) {
                Map<String, Object> skillData = new HashMap<>();
                skillData.put("name", config.getString(key + ".name"));
                skillData.put("item", config.getString(key + ".item"));
                skillData.put("activation", config.getString(key + ".activation"));
                skillData.put("cooldown", config.getInt(key + ".cooldown"));
                skillData.put("effect", config.getString(key + ".effect"));
                allSkills.put(key, skillData);
            }
        }

        return allSkills;
    }

    /**
     * Get all mob configurations
     * @return Map of mob key to configuration section data
     */
    public Map<String, Map<String, Object>> getAllMobs() {
        Map<String, Map<String, Object>> allMobs = new HashMap<>();
        FileConfiguration config = configs.get("configs/mobs.yml");

        if (config != null) {
            for (String key : config.getKeys(false)) {
                Map<String, Object> mobData = new HashMap<>();
                mobData.put("name", config.getString(key + ".name"));
                mobData.put("type", config.getString(key + ".type"));
                mobData.put("health", config.getDouble(key + ".health"));
                mobData.put("damage", config.getDouble(key + ".damage"));
                mobData.put("special-behavior", config.getString(key + ".special-behavior"));
                allMobs.put(key, mobData);
            }
        }

        return allMobs;
    }

    /**
     * Reload all configuration files
     */
    public void reloadAllConfigs() {
        configs.clear();
        loadAllConfigs();
        plugin.getLogger().info("Reloaded all configuration files");
    }
}


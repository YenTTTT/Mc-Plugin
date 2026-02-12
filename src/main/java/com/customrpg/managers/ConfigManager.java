package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
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
     * Load all configuration files from config/ directory
     */
    private void loadAllConfigs() {
        // Create config directory structure
        File configDir = new File(plugin.getDataFolder(), "config");
        File weaponsDir = new File(configDir, "weapons");
        File weaponTypesDir = new File(weaponsDir, "types");
        File weaponSkillsDir = new File(weaponsDir, "skills");
        File mobsDir = new File(configDir, "mobs");
        File mobTypesDir = new File(mobsDir, "types");
        File mobSkillsDir = new File(mobsDir, "skills");
        File skillsDir = new File(configDir, "skills");

        // Ensure directories exist
        weaponTypesDir.mkdirs();
        weaponSkillsDir.mkdirs();
        mobTypesDir.mkdirs();
        mobSkillsDir.mkdirs();
        skillsDir.mkdirs();

        // Ensure default example configs exist on disk (so directory scans can find them)
        ensureDefaultConfigExists("config/config.yml");
        ensureDefaultConfigExists("config/weapons/types/example.yml");
        ensureDefaultConfigExists("config/weapons/skills/skill1.yml");
        ensureDefaultConfigExists("config/mobs/types/example.yml");
        ensureDefaultConfigExists("config/mobs/skills/skill1.yml");
        ensureDefaultConfigExists("config/skills/example.yml");

        // Load main config
        loadConfig("config/config.yml");

        // Load weapon type configs from config/weapons/types/
        loadConfigsFromDirectory("config/weapons/types");

        // Load weapon skill configs from config/weapons/skills/
        loadConfigsFromDirectory("config/weapons/skills");

        // Load mob type configs from config/mobs/types/
        loadConfigsFromDirectory("config/mobs/types");

        // Load mob skill configs from config/mobs/skills/
        loadConfigsFromDirectory("config/mobs/skills");

        // Load skill configs from config/skills/
        loadConfigsFromDirectory("config/skills");

        plugin.getLogger().info("Loaded " + configs.size() + " configuration file(s)");
    }

    private void ensureDefaultConfigExists(String relativePath) {
        File configFile = new File(plugin.getDataFolder(), relativePath);
        if (configFile.exists()) {
            return;
        }

        configFile.getParentFile().mkdirs();

        try {
            plugin.saveResource(relativePath, false);
            plugin.getLogger().info("Created default config: " + relativePath);
        } catch (IllegalArgumentException ex) {
            // Not bundled in jar; ignore
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create default config: " + relativePath + " - " + ex.getMessage());
        }
    }

    /**
     * Load all yml files from a directory
     * @param relativePath Path relative to plugin data folder
     */
    private void loadConfigsFromDirectory(String relativePath) {
        File dir = new File(plugin.getDataFolder(), relativePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                loadConfig(relativePath + "/" + file.getName());
            }
        }
    }

    /**
     * Load a specific configuration file
     * @param relativePath Path relative to plugin data folder (e.g., "config/weapons/types/example.yml")
     */
    private void loadConfig(String relativePath) {
        File configFile = new File(plugin.getDataFolder(), relativePath);

        // Save default file from resources if it doesn't exist
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();

            try {
                // JavaPlugin#saveResource 支援帶子資料夾的路徑（例如 config/weapons/types/example.yml）
                plugin.saveResource(relativePath, false);
                plugin.getLogger().info("Created default config: " + relativePath);
            } catch (IllegalArgumentException ex) {
                // resource 不存在於 jar 內
                plugin.getLogger().warning("Could not find default config in resources: " + relativePath);
                return;
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to create config file: " + relativePath);
                plugin.getLogger().severe(ex.getMessage());
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

        for (String configPath : configs.keySet()) {
            if (!configPath.startsWith("config/weapons/types/")) {
                continue;
            }

            FileConfiguration config = configs.get(configPath);
            for (String key : config.getKeys(false)) {
                // 支援兩種格式：
                // 1) 舊格式：name/material/lore/damage-multiplier/special-effect
                // 2) 新格式：basic/appearance/stats/special/...
                boolean isNewFormat = config.isConfigurationSection(key + ".basic")
                        || config.isConfigurationSection(key + ".appearance")
                        || config.isConfigurationSection(key + ".stats")
                        || config.isConfigurationSection(key + ".special");

                Map<String, Object> weaponData = new HashMap<>();

                if (isNewFormat) {
                    // 【武器基本資料】
                    weaponData.put("name", config.getString(key + ".basic.name"));
                    weaponData.put("material", config.getString(key + ".basic.material"));

                    // 【外觀設定】
                    weaponData.put("display-name", config.getString(key + ".appearance.display-name"));
                    weaponData.put("lore", config.getStringList(key + ".appearance.lore"));
                    weaponData.put("custom-model-data", config.getInt(key + ".appearance.custom-model-data", 0));
                    weaponData.put("enchanted-glow", config.getBoolean(key + ".appearance.enchanted-glow", false));

                    // 【基礎戰鬥屬性】
                    // 若沒填 damage-multiplier，就用 (base-damage) 去推一個倍率；沒辦法推就回到 1.0
                    double damageMultiplier = config.getDouble(key + ".stats.damage-multiplier", 0.0);
                    if (damageMultiplier <= 0) {
                        damageMultiplier = 1.0;
                    }
                    weaponData.put("damage-multiplier", damageMultiplier);

                    // 其餘 stats 欄位（先提供給戰鬥計算使用）
                    weaponData.put("base-damage", config.getDouble(key + ".stats.base-damage", 0.0));
                    weaponData.put("attack-speed", config.getDouble(key + ".stats.attack-speed", 0.0));
                    weaponData.put("crit-chance", config.getDouble(key + ".stats.crit-chance", 0.0));
                    weaponData.put("crit-damage-multiplier", config.getDouble(key + ".stats.crit-damage-multiplier", 0.0));
                    weaponData.put("knockback", config.getDouble(key + ".stats.knockback", 0.0));
                    weaponData.put("durability-cost-multiplier", config.getDouble(key + ".stats.durability-cost-multiplier", 1.0));

                    // 【特殊機制】(先以舊的 special-effect string 相容 WeaponListener)
                    // 處理元素屬性（火/冰/雷/毒/無）→ 對應成 burn/lightning/...（目前 listener 只支援這三種）
                    String element = config.getString(key + ".element.element", "");
                    String specialEffect = config.getString(key + ".special.effect", "");

                    String resolvedEffect = resolveWeaponEffect(element, specialEffect);
                    weaponData.put("special-effect", resolvedEffect);

                    // 額外資料（之後 listener 參數化會用到）
                    Map<String, Object> extra = new HashMap<>();
                    extra.put("backstab-enabled", config.getBoolean(key + ".special.backstab-enabled", false));
                    extra.put("backstab-multiplier", config.getDouble(key + ".special.backstab-multiplier", 1.0));

                    // 視覺/音效（支援新位置：special.effects.*，並向下相容舊的 effects.*）
                    extra.put("backstab-sound", config.getString(key + ".special.effects.backstab-sound",
                            config.getString(key + ".effects.backstab-sound", "")));
                    extra.put("backstab-particle", config.getString(key + ".special.effects.backstab-particle",
                            config.getString(key + ".effects.backstab-particle", "")));

                    extra.put("burn-duration-ticks", config.getInt(key + ".element.duration-ticks", 100));
                    extra.put("lightning-chance", config.getDouble(key + ".element.lightning-chance", 0.3));

                    // 【被動效果】（測試版）
                    // passive.effect: 例如 "kill_crit_boost"
                    // passive.value: 例如 50.0（代表 +50% crit chance）
                    // passive.duration-ticks: 持續時間（ticks），0 代表只對下一擊/一次生效（由 listener 決定）
                    // passive.chance: 觸發機率（0~1 或 0~100 都支援）
                    extra.put("passive-effect", config.getString(key + ".passive.effect", ""));
                    extra.put("passive-name", config.getString(key + ".passive.name", ""));
                    extra.put("passive-value", config.getDouble(key + ".passive.value", 0.0));
                    extra.put("passive-duration-ticks", config.getInt(key + ".passive.duration-ticks", 200));
                    extra.put("passive-chance", config.getDouble(key + ".passive.chance", 1.0));
                    extra.put("passive-cooldown-ticks", config.getInt(key + ".passive.cooldown-ticks", 0));

                    // 【主動技能】（武器內建 active-skill）
                    extra.put("active-skill-name", config.getString(key + ".active-skill.name", ""));
                    extra.put("active-skill-trigger", config.getString(key + ".active-skill.trigger", ""));
                    extra.put("active-skill-cooldown", config.getInt(key + ".active-skill.cooldown", 0));
                    extra.put("active-skill-description", config.getString(key + ".active-skill.description", ""));
                    extra.put("active-skill-damage", config.getDouble(key + ".active-skill.damage", 0.0));
                    extra.put("active-skill-range", config.getDouble(key + ".active-skill.range", 0.0));
                    extra.put("active-skill-aoe-width", config.getDouble(key + ".active-skill.aoe-width", 0.0));
                    extra.put("active-skill-particle", config.getString(key + ".active-skill.particle", ""));
                    extra.put("active-skill-sound", config.getString(key + ".active-skill.sound", ""));

                    weaponData.put("extra", extra);
                } else {
                    // 舊格式讀取（向下相容）
                    weaponData.put("name", config.getString(key + ".name"));
                    weaponData.put("material", config.getString(key + ".material"));
                    weaponData.put("damage-multiplier", config.getDouble(key + ".damage-multiplier"));
                    weaponData.put("special-effect", config.getString(key + ".special-effect"));
                    weaponData.put("lore", config.getStringList(key + ".lore"));
                }

                allWeapons.put(key, weaponData);
            }
        }

        return allWeapons;
    }

    private String resolveWeaponEffect(String element, String specialEffect) {
        String se = specialEffect == null ? "" : specialEffect.trim().toLowerCase();
        String elRaw = element == null ? "" : element.trim();
        String el = elRaw.toLowerCase();

        // 先以 special.effect 優先
        if (!se.isEmpty() && !se.equals("none")) {
            if (se.equals("fire")) {
                return "burn";
            }
            if (se.equals("thunder")) {
                return "lightning";
            }
            return se;
        }

        // 再用元素屬性推導（支援中英）
        if (el.equals("fire") || elRaw.equals("火")) {
            return "burn";
        }
        if (el.equals("ice") || elRaw.equals("冰")) {
            return "none";
        }
        if (el.equals("lightning") || el.equals("thunder") || elRaw.equals("雷")) {
            return "lightning";
        }
        if (el.equals("poison") || elRaw.equals("毒")) {
            return "none";
        }
        if (el.equals("none") || elRaw.equals("無")) {
            return "none";
        }

        return "none";
    }

    /**
     * Get all skill configurations
     * @return Map of skill key to configuration section data
     */
    public Map<String, Map<String, Object>> getAllSkills() {
        Map<String, Map<String, Object>> allSkills = new HashMap<>();

        // Load from all skill config files in config/skills/
        for (String configPath : configs.keySet()) {
            if (configPath.startsWith("config/skills/")) {
                FileConfiguration config = configs.get(configPath);
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
        }

        return allSkills;
    }

    /**
     * Get all mob configurations
     * @return Map of mob key to configuration section data
     */
    public Map<String, Map<String, Object>> getAllMobs() {
        Map<String, Map<String, Object>> allMobs = new HashMap<>();

        // Load from all mob type config files in config/mobs/types/
        for (String configPath : configs.keySet()) {
            if (configPath.startsWith("config/mobs/types/")) {
                FileConfiguration config = configs.get(configPath);
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
        }

        return allMobs;
    }

    /**
     * Get all weapon skill configurations
     * @return Map of weapon skill key to configuration section data
     */
    public Map<String, Map<String, Object>> getAllWeaponSkills() {
        Map<String, Map<String, Object>> allWeaponSkills = new HashMap<>();

        // Load from all weapon skill config files in config/weapons/skills/
        for (String configPath : configs.keySet()) {
            if (configPath.startsWith("config/weapons/skills/")) {
                FileConfiguration config = configs.get(configPath);
                for (String key : config.getKeys(false)) {
                    Map<String, Object> skillData = new HashMap<>();
                    skillData.put("name", config.getString(key + ".name"));
                    skillData.put("effect", config.getString(key + ".effect"));
                    skillData.put("cooldown", config.getInt(key + ".cooldown"));
                    skillData.put("damage", config.getDouble(key + ".damage"));
                    skillData.put("range", config.getDouble(key + ".range"));
                    allWeaponSkills.put(key, skillData);
                }
            }
        }

        return allWeaponSkills;
    }

    /**
     * Get all mob skill configurations
     * @return Map of mob skill key to configuration section data
     */
    public Map<String, Map<String, Object>> getAllMobSkills() {
        Map<String, Map<String, Object>> allMobSkills = new HashMap<>();

        // Load from all mob skill config files in config/mobs/skills/
        for (String configPath : configs.keySet()) {
            if (configPath.startsWith("config/mobs/skills/")) {
                FileConfiguration config = configs.get(configPath);
                for (String key : config.getKeys(false)) {
                    Map<String, Object> skillData = new HashMap<>();
                    skillData.put("name", config.getString(key + ".name"));
                    skillData.put("effect", config.getString(key + ".effect"));
                    skillData.put("cooldown", config.getInt(key + ".cooldown"));
                    skillData.put("trigger", config.getString(key + ".trigger"));
                    allMobSkills.put(key, skillData);
                }
            }
        }

        return allMobSkills;
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

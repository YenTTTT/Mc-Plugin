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
        if (!weaponTypesDir.exists() && !weaponTypesDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + weaponTypesDir.getPath());
        }
        if (!weaponSkillsDir.exists() && !weaponSkillsDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + weaponSkillsDir.getPath());
        }
        if (!mobTypesDir.exists() && !mobTypesDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + mobTypesDir.getPath());
        }
        if (!mobSkillsDir.exists() && !mobSkillsDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + mobSkillsDir.getPath());
        }
        if (!skillsDir.exists() && !skillsDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create directory: " + skillsDir.getPath());
        }

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

        if (!configFile.getParentFile().exists() && !configFile.getParentFile().mkdirs()) {
            plugin.getLogger().warning("Failed to create parent directory for: " + relativePath);
            return;
        }

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
            if (!configFile.getParentFile().exists() && !configFile.getParentFile().mkdirs()) {
                plugin.getLogger().warning("Failed to create parent directory for config: " + relativePath);
            }

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

                    // 將解析後的元素類型放入 extra，供 WeaponListener 使用
                    extra.put("element-type", resolvedEffect.toUpperCase());

                    extra.put("backstab-enabled", config.getBoolean(key + ".special.backstab-enabled", false));
                    extra.put("backstab-multiplier", config.getDouble(key + ".special.backstab-multiplier", 1.0));

                    // 視覺/音效（支援新位置：special.effects.*，並向下相容舊的 effects.*）
                    extra.put("backstab-sound", config.getString(key + ".special.effects.backstab-sound",
                            config.getString(key + ".effects.backstab-sound", "")));
                    extra.put("backstab-particle", config.getString(key + ".special.effects.backstab-particle",
                            config.getString(key + ".effects.backstab-particle", "")));

                    // 元素效果參數
                    extra.put("burn-duration-ticks", config.getInt(key + ".element.duration-ticks", 100));
                    extra.put("lightning-chance", config.getDouble(key + ".element.lightning-chance", 0.3));

                    // 冰霜元素參數
                    extra.put("ice-chance", config.getDouble(key + ".element.ice-chance", 0.3));
                    extra.put("ice-duration-ticks", config.getInt(key + ".element.ice-duration-ticks", 40));

                    // 水流元素參數
                    extra.put("water-duration-ticks", config.getInt(key + ".element.water-duration-ticks", 60));
                    extra.put("water-slowness-level", config.getInt(key + ".element.water-slowness-level", 1));

                    // 毒素元素參數
                    extra.put("poison-duration-ticks", config.getInt(key + ".element.poison-duration-ticks", 100));
                    extra.put("poison-level", config.getInt(key + ".element.poison-level", 1));
                    extra.put("poison-armor-reduction-level", config.getInt(key + ".element.poison-armor-reduction-level", 1));

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

                    // 【主動技能】- 支援引用 skills 配置
                    String skillName = config.getString(key + ".active-skill.name", "");
                    extra.put("active-skill-name", skillName);

                    // helper: 判斷武器是否有覆寫某個 active-skill.*（字串用非空白、數字用 isSet）
                    java.util.function.Function<String, String> getNonBlank = (path) -> {
                        String v = config.getString(path, "");
                        return v.trim();
                    };

                    if (!skillName.isEmpty()) {
                        Map<String, Map<String, Object>> weaponSkills = getAllWeaponSkills();
                        if (weaponSkills.containsKey(skillName)) {
                            Map<String, Object> skillTemplate = weaponSkills.get(skillName);

                            // 1) 先放模板預設值
                            String templateTrigger = String.valueOf(skillTemplate.getOrDefault("trigger", "RIGHT_CLICK"));
                            int templateCooldown = ((Number) skillTemplate.getOrDefault("cooldown", 0)).intValue();
                            String templateDesc = String.valueOf(skillTemplate.getOrDefault("description", ""));
                            double templateDamage = ((Number) skillTemplate.getOrDefault("damage", 0.0)).doubleValue();
                            double templateRange = ((Number) skillTemplate.getOrDefault("range", 0.0)).doubleValue();
                            double templateWidth = ((Number) skillTemplate.getOrDefault("aoe-width", 0.0)).doubleValue();
                            double templateHealPlayer = ((Number) skillTemplate.getOrDefault("heal-player", 0.0)).doubleValue();
                            String templateParticle = String.valueOf(skillTemplate.getOrDefault("particle", ""));
                            String templateSound = String.valueOf(skillTemplate.getOrDefault("sound", ""));
                            String templateType = String.valueOf(skillTemplate.getOrDefault("type", ""));

                            extra.put("active-skill-trigger", templateTrigger);
                            extra.put("active-skill-cooldown", templateCooldown);
                            extra.put("active-skill-description", templateDesc);
                            extra.put("active-skill-damage", templateDamage);
                            extra.put("active-skill-range", templateRange);
                            extra.put("active-skill-aoe-width", templateWidth);
                            extra.put("active-skill-heal-player", templateHealPlayer);
                            extra.put("active-skill-particle", templateParticle);
                            extra.put("active-skill-sound", templateSound);
                            extra.put("active-skill-type", templateType);

                            // complex nodes from template (target/visuals) are stored as maps
                            Object templateTargetObj = skillTemplate.get("target");
                            Object templateVisualsObj = skillTemplate.get("visuals");
                            if (templateTargetObj instanceof Map) {
                                extra.put("active-skill-target", templateTargetObj);
                            }
                            if (templateVisualsObj instanceof Map) {
                                extra.put("active-skill-visuals", templateVisualsObj);
                            }

                            // 2) 再用武器 active-skill.* 覆寫（指定技能）
                            String triggerOverride = getNonBlank.apply(key + ".active-skill.trigger");
                            if (!triggerOverride.isEmpty()) {
                                extra.put("active-skill-trigger", triggerOverride);
                            }

                            if (config.isSet(key + ".active-skill.cooldown")) {
                                extra.put("active-skill-cooldown", config.getInt(key + ".active-skill.cooldown", templateCooldown));
                            }

                            String descOverride = getNonBlank.apply(key + ".active-skill.description");
                            if (!descOverride.isEmpty()) {
                                extra.put("active-skill-description", descOverride);
                            }

                            if (config.isSet(key + ".active-skill.damage")) {
                                extra.put("active-skill-damage", config.getDouble(key + ".active-skill.damage", templateDamage));
                            }
                            if (config.isSet(key + ".active-skill.range")) {
                                extra.put("active-skill-range", config.getDouble(key + ".active-skill.range", templateRange));
                            }
                            if (config.isSet(key + ".active-skill.aoe-width")) {
                                extra.put("active-skill-aoe-width", config.getDouble(key + ".active-skill.aoe-width", templateWidth));
                            }
                            if (config.isSet(key + ".active-skill.heal-player")) {
                                extra.put("active-skill-heal-player", config.getDouble(key + ".active-skill.heal-player", templateHealPlayer));
                            }

                            String particleOverride = getNonBlank.apply(key + ".active-skill.particle");
                            if (!particleOverride.isEmpty()) {
                                extra.put("active-skill-particle", particleOverride);
                            }
                            String soundOverride = getNonBlank.apply(key + ".active-skill.sound");
                            if (!soundOverride.isEmpty()) {
                                extra.put("active-skill-sound", soundOverride);
                            }

                            // deep merge target/visuals overrides (if present in weapon yml)
                            org.bukkit.configuration.ConfigurationSection targetSection = config.getConfigurationSection(key + ".active-skill.target");
                            org.bukkit.configuration.ConfigurationSection visualsSection = config.getConfigurationSection(key + ".active-skill.visuals");

                            if (targetSection != null) {
                                Map<String, Object> overrideTarget = safeSectionToMap(targetSection);
                                Map<String, Object> baseTarget = null;
                                Object bt = extra.get("active-skill-target");
                                if (bt instanceof Map) {
                                    //noinspection unchecked
                                    baseTarget = (Map<String, Object>) bt;
                                }
                                extra.put("active-skill-target", deepMerge(baseTarget, overrideTarget));
                            }

                            if (visualsSection != null) {
                                Map<String, Object> overrideVisuals = safeSectionToMap(visualsSection);
                                Map<String, Object> baseVisuals = null;
                                Object bv = extra.get("active-skill-visuals");
                                if (bv instanceof Map) {
                                    //noinspection unchecked
                                    baseVisuals = (Map<String, Object>) bv;
                                }
                                extra.put("active-skill-visuals", deepMerge(baseVisuals, overrideVisuals));
                            }
                        } else {
                            // 技能不存在：只能使用武器自己的配置
                            plugin.getLogger().warning("Weapon '" + key + "' references skill '" + skillName + "' which does not exist in config/weapons/skills/");
                            plugin.getLogger().warning("  Using weapon's own active-skill configuration instead.");

                            extra.put("active-skill-trigger", config.getString(key + ".active-skill.trigger", ""));
                            extra.put("active-skill-cooldown", config.getInt(key + ".active-skill.cooldown", 0));
                            extra.put("active-skill-description", config.getString(key + ".active-skill.description", ""));
                            extra.put("active-skill-damage", config.getDouble(key + ".active-skill.damage", 0.0));
                            extra.put("active-skill-range", config.getDouble(key + ".active-skill.range", 0.0));
                            extra.put("active-skill-aoe-width", config.getDouble(key + ".active-skill.aoe-width", 0.0));
                            extra.put("active-skill-heal-player", config.getDouble(key + ".active-skill.heal-player", 0.0));
                            extra.put("active-skill-particle", config.getString(key + ".active-skill.particle", ""));
                            extra.put("active-skill-sound", config.getString(key + ".active-skill.sound", ""));
                            extra.put("active-skill-type", config.getString(key + ".active-skill.type", ""));

                            org.bukkit.configuration.ConfigurationSection targetSection = config.getConfigurationSection(key + ".active-skill.target");
                            if (targetSection != null) {
                                extra.put("active-skill-target", safeSectionToMap(targetSection));
                            }
                            org.bukkit.configuration.ConfigurationSection visualsSection = config.getConfigurationSection(key + ".active-skill.visuals");
                            if (visualsSection != null) {
                                extra.put("active-skill-visuals", safeSectionToMap(visualsSection));
                            }
                        }
                    } else {
                        // 沒有指定技能名稱：使用武器配置
                        extra.put("active-skill-trigger", config.getString(key + ".active-skill.trigger", ""));
                        extra.put("active-skill-cooldown", config.getInt(key + ".active-skill.cooldown", 0));
                        extra.put("active-skill-description", config.getString(key + ".active-skill.description", ""));
                        extra.put("active-skill-damage", config.getDouble(key + ".active-skill.damage", 0.0));
                        extra.put("active-skill-range", config.getDouble(key + ".active-skill.range", 0.0));
                        extra.put("active-skill-aoe-width", config.getDouble(key + ".active-skill.aoe-width", 0.0));
                        extra.put("active-skill-heal-player", config.getDouble(key + ".active-skill.heal-player", 0.0));
                        extra.put("active-skill-particle", config.getString(key + ".active-skill.particle", ""));
                        extra.put("active-skill-sound", config.getString(key + ".active-skill.sound", ""));
                        extra.put("active-skill-type", config.getString(key + ".active-skill.type", ""));

                        org.bukkit.configuration.ConfigurationSection targetSection = config.getConfigurationSection(key + ".active-skill.target");
                        if (targetSection != null) {
                            extra.put("active-skill-target", safeSectionToMap(targetSection));
                        }
                        org.bukkit.configuration.ConfigurationSection visualsSection = config.getConfigurationSection(key + ".active-skill.visuals");
                        if (visualsSection != null) {
                            extra.put("active-skill-visuals", safeSectionToMap(visualsSection));
                        }
                    }

                    weaponData.put("extra", extra);
                } else {
                    // 舊格式讀取（向下相容）
                    weaponData.put("name", config.getString(key + ".name"));
                    weaponData.put("material", config.getString(key + ".material"));
                    weaponData.put("damage-multiplier", config.getDouble(key + ".damage-multiplier"));

                    String oldSpecialEffect = config.getString(key + ".special-effect", "none");
                    weaponData.put("special-effect", oldSpecialEffect);

                    // 為舊格式也添加 element-type 到 extra
                    Map<String, Object> extra = new HashMap<>();
                    extra.put("element-type", oldSpecialEffect.toUpperCase());
                    weaponData.put("extra", extra);

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
            if (se.equals("ice") || se.equals("freeze")) {
                return "ice";
            }
            if (se.equals("water")) {
                return "water";
            }
            if (se.equals("poison")) {
                return "poison";
            }
            return se;
        }

        // 再用元素屬性推導（支援中英）
        if (el.equals("fire") || elRaw.equals("火")) {
            return "burn";
        }
        if (el.equals("ice") || el.equals("freeze") || elRaw.equals("冰")) {
            return "ice";
        }
        if (el.equals("water") || elRaw.equals("水")) {
            return "water";
        }
        if (el.equals("lightning") || el.equals("thunder") || elRaw.equals("雷")) {
            return "lightning";
        }
        if (el.equals("poison") || elRaw.equals("毒")) {
            return "poison";
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

                    // 新版格式（支援所有屬性）
                    skillData.put("display-name", config.getString(key + ".display-name", config.getString(key + ".name", key)));
                    skillData.put("description", config.getString(key + ".description", ""));
                    skillData.put("type", config.getString(key + ".type", ""));
                    skillData.put("trigger", config.getString(key + ".trigger", "RIGHT_CLICK"));
                    skillData.put("cooldown", config.getInt(key + ".cooldown", 0));
                    skillData.put("damage", config.getDouble(key + ".damage", 0.0));
                    skillData.put("range", config.getDouble(key + ".range", 0.0));
                    skillData.put("aoe-width", config.getDouble(key + ".aoe-width", 0.0));
                    skillData.put("particle", config.getString(key + ".particle", ""));
                    skillData.put("sound", config.getString(key + ".sound", ""));
                    skillData.put("heal-player", config.getDouble(key + ".heal-player", 0.0));

                    // complex config nodes
                    org.bukkit.configuration.ConfigurationSection targetSection = config.getConfigurationSection(key + ".target");
                    if (targetSection != null) {
                        skillData.put("target", safeSectionToMap(targetSection));
                    }
                    org.bukkit.configuration.ConfigurationSection visualsSection = config.getConfigurationSection(key + ".visuals");
                    if (visualsSection != null) {
                        skillData.put("visuals", safeSectionToMap(visualsSection));
                    }

                    // 舊版格式相容
                    skillData.put("name", config.getString(key + ".name", key));
                    skillData.put("effect", config.getString(key + ".effect", ""));

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

    private static Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> override) {
        if (base == null) {
            base = new HashMap<>();
        }
        Map<String, Object> out = new HashMap<>(base);
        if (override == null) {
            return out;
        }

        for (Map.Entry<String, Object> e : override.entrySet()) {
            String k = e.getKey();
            Object overrideVal = e.getValue();
            Object baseVal = out.get(k);

            if (baseVal instanceof Map && overrideVal instanceof Map) {
                //noinspection unchecked
                out.put(k, deepMerge((Map<String, Object>) baseVal, (Map<String, Object>) overrideVal));
            } else {
                out.put(k, overrideVal);
            }
        }
        return out;
    }

    private static Map<String, Object> safeSectionToMap(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        for (String k : section.getKeys(false)) {
            Object v = section.get(k);
            map.put(k, v);
        }
        return map;
    }
}

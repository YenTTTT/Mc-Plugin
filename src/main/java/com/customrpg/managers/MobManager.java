package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.*;

/**
 * MobManager - Manages custom mob types and spawning
 *
 * This class handles the registration and spawning of custom mobs with unique
 * properties and behaviors. Custom mobs are marked with persistent data to
 * identify them and trigger special mechanics.
 *
 * Example custom mobs:
 * - Snow Zombie: Throws snowballs
 * - Fire Skeleton: Shoots fire arrows
 * - Giant Slime: Extra large, splits on death
 */
public class MobManager {

    private final CustomRPG plugin;
    private final Map<String, MobData> mobTypes;
    private final NamespacedKey customMobKey;
    private final NamespacedKey mobLevelKey;
    private final ConfigManager configManager;
    private final Random random;

    /**
     * Constructor for MobManager
     * @param plugin Main plugin instance
     * @param configManager Config manager for loading mob configs
     */
    public MobManager(CustomRPG plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobTypes = new HashMap<>();
        this.customMobKey = new NamespacedKey(plugin, "custom_mob_type");
        this.mobLevelKey = new NamespacedKey(plugin, "mob_level");
        this.random = new Random();
        loadMobTypes();
    }

    /**
     * Load all custom mob types from config/mobs/types/ folder
     */
    private void loadMobTypes() {
        Map<String, Map<String, Object>> allMobs = configManager.getAllMobs();

        if (allMobs.isEmpty()) {
            plugin.getLogger().warning("No mobs found in config/mobs/types/ folder");
            return;
        }

        for (Map.Entry<String, Map<String, Object>> entry : allMobs.entrySet()) {
            String mobKey = entry.getKey();
            Map<String, Object> mobConfig = entry.getValue();

            try {
                MobData mobData = parseMobData(mobKey, mobConfig);
                mobTypes.put(mobKey, mobData);
                plugin.getLogger().info("Loaded custom mob: " + mobData.getName() +
                    (mobData.hasLevelSystem() ? " (Lv." + mobData.getMinLevel() + "-" + mobData.getMaxLevel() + ")" : ""));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load mob '" + mobKey + "': " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Parse mob data from config (supports both old and new formats)
     */
    private MobData parseMobData(String key, Map<String, Object> config) {
        String name = (String) config.getOrDefault("name", key);
        EntityType entityType = EntityType.valueOf((String) config.get("type"));
        String specialBehavior = (String) config.getOrDefault("special-behavior", "none");

        // 檢查是否為新格式（包含 level-range 或 base-stats）
        boolean isNewFormat = config.containsKey("level-range") || config.containsKey("base-stats");

        if (isNewFormat) {
            return parseEnhancedMobData(key, name, entityType, specialBehavior, config);
        } else {
            // 舊格式（向後兼容）
            double health = getDouble(config, "health", 20.0);
            double damage = getDouble(config, "damage", 1.0);
            int exp = getInt(config, "exp", 0);
            return new MobData(key, name, entityType, health, damage, specialBehavior, exp);
        }
    }

    /**
     * Parse enhanced mob data (new format)
     */
    @SuppressWarnings("unchecked")
    private MobData parseEnhancedMobData(String key, String name, EntityType entityType,
                                          String specialBehavior, Map<String, Object> config) {
        // 等級範圍
        int minLevel = 1;
        int maxLevel = 1;
        if (config.containsKey("level-range")) {
            Map<String, Object> levelRange = (Map<String, Object>) config.get("level-range");
            minLevel = getInt(levelRange, "min", 1);
            maxLevel = getInt(levelRange, "max", 1);
        }

        // 基礎屬性
        double baseHealth = 20.0;
        double baseDamage = 1.0;
        if (config.containsKey("base-stats")) {
            Map<String, Object> baseStats = (Map<String, Object>) config.get("base-stats");
            baseHealth = getDouble(baseStats, "health", 20.0);
            baseDamage = getDouble(baseStats, "damage", 1.0);
        }

        // 屬性成長
        double healthPerLevel = 0;
        double damagePerLevel = 0;
        if (config.containsKey("stat-scaling")) {
            Map<String, Object> scaling = (Map<String, Object>) config.get("stat-scaling");
            healthPerLevel = getDouble(scaling, "health-per-level", 0);
            damagePerLevel = getDouble(scaling, "damage-per-level", 0);
        }

        // 經驗值
        int baseExp = 0;
        int expPerLevel = 0;
        if (config.containsKey("exp")) {
            Object expObj = config.get("exp");
            if (expObj instanceof Map) {
                // 新格式：exp 是一個 Map
                Map<String, Object> expConfig = (Map<String, Object>) expObj;
                baseExp = getInt(expConfig, "base", 0);
                expPerLevel = getInt(expConfig, "per-level", 0);
            } else {
                // 舊格式或簡化格式：exp 是一個整數
                baseExp = getInt(config, "exp", 0);
                expPerLevel = 0;
            }
        }

        // 裝備
        Map<String, ItemStack> equipment = parseEquipment(config);

        // 掉落物
        List<DropItem> vanillaDrops = new ArrayList<>();
        List<WeaponDrop> weaponDrops = new ArrayList<>();
        if (config.containsKey("drops")) {
            Map<String, Object> drops = (Map<String, Object>) config.get("drops");
            vanillaDrops = parseVanillaDrops(drops);
            weaponDrops = parseWeaponDrops(drops);
        }

        // 偽裝
        DisguiseConfig disguise = parseDisguise(config);

        // 其他設定
        boolean showLevelInName = (boolean) config.getOrDefault("show-level-in-name", false);

        return new MobData(key, name, entityType, minLevel, maxLevel,
                          baseHealth, baseDamage, healthPerLevel, damagePerLevel,
                          baseExp, expPerLevel, equipment, vanillaDrops, weaponDrops,
                          disguise, specialBehavior, showLevelInName);
    }

    /**
     * Parse equipment from config
     */
    @SuppressWarnings("unchecked")
    private Map<String, ItemStack> parseEquipment(Map<String, Object> config) {
        Map<String, ItemStack> equipment = new HashMap<>();

        if (!config.containsKey("equipment")) {
            return equipment;
        }

        Map<String, Object> equipConfig = (Map<String, Object>) config.get("equipment");
        String[] slots = {"helmet", "chestplate", "leggings", "boots", "main-hand", "off-hand"};

        for (String slot : slots) {
            if (equipConfig.containsKey(slot)) {
                Map<String, Object> itemConfig = (Map<String, Object>) equipConfig.get(slot);
                ItemStack item = parseItemStack(itemConfig);
                if (item != null) {
                    equipment.put(slot, item);
                }
            }
        }

        return equipment;
    }

    /**
     * Parse ItemStack from config
     */
    @SuppressWarnings("unchecked")
    private ItemStack parseItemStack(Map<String, Object> config) {
        try {
            Material material = Material.valueOf((String) config.get("material"));
            ItemStack item = new ItemStack(material);

            // 附魔
            if (config.containsKey("enchantments")) {
                List<Map<String, Object>> enchants = (List<Map<String, Object>>) config.get("enchantments");
                ItemMeta meta = item.getItemMeta();
                for (Map<String, Object> enchant : enchants) {
                    String enchantName = (String) enchant.get("type");
                    int level = getInt(enchant, "level", 1);
                    Enchantment enchantment = Enchantment.getByName(enchantName);
                    if (enchantment != null && meta != null) {
                        meta.addEnchant(enchantment, level, true);
                    }
                }
                item.setItemMeta(meta);
            }

            return item;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse item: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse vanilla drops from config
     */
    @SuppressWarnings("unchecked")
    private List<DropItem> parseVanillaDrops(Map<String, Object> drops) {
        List<DropItem> vanillaDrops = new ArrayList<>();

        if (!drops.containsKey("vanilla-items")) {
            return vanillaDrops;
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) drops.get("vanilla-items");
        for (Map<String, Object> itemConfig : items) {
            try {
                Material material = Material.valueOf((String) itemConfig.get("material"));
                String amountStr = (String) itemConfig.getOrDefault("amount", "1");
                double chance = getDouble(itemConfig, "chance", 1.0);

                int minAmount = 1;
                int maxAmount = 1;
                if (amountStr.contains("-")) {
                    String[] parts = amountStr.split("-");
                    minAmount = Integer.parseInt(parts[0]);
                    maxAmount = Integer.parseInt(parts[1]);
                } else {
                    minAmount = maxAmount = Integer.parseInt(amountStr);
                }

                vanillaDrops.add(new DropItem(material, minAmount, maxAmount, chance));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse drop item: " + e.getMessage());
            }
        }

        return vanillaDrops;
    }

    /**
     * Parse weapon drops from config
     */
    @SuppressWarnings("unchecked")
    private List<WeaponDrop> parseWeaponDrops(Map<String, Object> drops) {
        List<WeaponDrop> weaponDrops = new ArrayList<>();

        if (!drops.containsKey("custom-weapons")) {
            return weaponDrops;
        }

        List<Map<String, Object>> weapons = (List<Map<String, Object>>) drops.get("custom-weapons");
        for (Map<String, Object> weaponConfig : weapons) {
            String weaponKey = (String) weaponConfig.get("weapon-key");
            double chance = getDouble(weaponConfig, "chance", 0.01);
            weaponDrops.add(new WeaponDrop(weaponKey, chance));
        }

        return weaponDrops;
    }

    /**
     * Parse disguise config
     */
    @SuppressWarnings("unchecked")
    private DisguiseConfig parseDisguise(Map<String, Object> config) {
        if (!config.containsKey("disguise")) {
            return null;
        }

        Map<String, Object> disguiseConfig = (Map<String, Object>) config.get("disguise");
        boolean enabled = (boolean) disguiseConfig.getOrDefault("enabled", false);

        if (!enabled) {
            return null;
        }

        EntityType disguiseType = EntityType.valueOf((String) disguiseConfig.getOrDefault("type", "PIG"));
        double babyChance = getDouble(disguiseConfig, "baby-chance", 0);
        boolean riderInvisible = (boolean) disguiseConfig.getOrDefault("rider-invisible", true);
        boolean riderSilent = (boolean) disguiseConfig.getOrDefault("rider-silent", true);
        boolean riderInvulnerable = (boolean) disguiseConfig.getOrDefault("rider-invulnerable", false);

        return new DisguiseConfig(enabled, disguiseType, babyChance, riderInvisible, riderSilent, riderInvulnerable);
    }

    // 輔助方法
    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Spawn a custom mob at the specified location
     * @param mobKey The mob type identifier
     * @param location The spawn location
     * @return The spawned entity, or null if mob type not found
     */
    public LivingEntity spawnCustomMob(String mobKey, Location location) {
        MobData mobData = mobTypes.get(mobKey);
        if (mobData == null) {
            return null;
        }

        // 生成隨機等級
        int level = mobData.getMinLevel();
        if (mobData.getMaxLevel() > mobData.getMinLevel()) {
            level = random.nextInt(mobData.getMaxLevel() - mobData.getMinLevel() + 1) + mobData.getMinLevel();
        }

        // 檢查是否需要偽裝
        LivingEntity mob;
        if (mobData.getDisguise() != null && mobData.getDisguise().isEnabled()) {
            mob = spawnDisguisedMob(mobData, location, level);
        } else {
            mob = spawnNormalMob(mobData, location, level);
        }

        if (mob == null) {
            return null;
        }

        // 設置持久化數據
        mob.getPersistentDataContainer().set(customMobKey, PersistentDataType.STRING, mobKey);
        mob.getPersistentDataContainer().set(mobLevelKey, PersistentDataType.INTEGER, level);

        plugin.getLogger().info("Spawned custom mob: " + mobData.getName() + " (Lv." + level + ") at " + location);
        return mob;
    }

    /**
     * Spawn a normal (non-disguised) mob
     */
    private LivingEntity spawnNormalMob(MobData mobData, Location location, int level) {
        Entity entity = location.getWorld().spawnEntity(location, mobData.getEntityType());
        if (!(entity instanceof LivingEntity)) {
            entity.remove();
            return null;
        }

        LivingEntity mob = (LivingEntity) entity;

        // 設置名稱
        String displayName = mobData.getName();
        if (mobData.shouldShowLevelInName() && mobData.hasLevelSystem()) {
            displayName = "&8[&eLv." + level + "&8] " + mobData.getName();
        }
        mob.setCustomName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName));
        mob.setCustomNameVisible(true);

        // 設置等級化屬性
        double health = mobData.calculateHealth(level);
        mob.setMaxHealth(health);
        mob.setHealth(health);

        // 裝備物品
        applyEquipment(mob, mobData);

        return mob;
    }

    /**
     * Spawn a disguised mob (invisible host + visible passenger)
     */
    private LivingEntity spawnDisguisedMob(MobData mobData, Location location, int level) {
        DisguiseConfig disguise = mobData.getDisguise();

        // 生成宿主（實際戰鬥實體）
        Entity hostEntity = location.getWorld().spawnEntity(location, mobData.getEntityType());
        if (!(hostEntity instanceof LivingEntity)) {
            hostEntity.remove();
            return null;
        }

        LivingEntity host = (LivingEntity) hostEntity;

        // 設置宿主屬性
        if (disguise.isRiderInvisible()) {
            host.setInvisible(true);
        }
        if (disguise.isRiderSilent()) {
            host.setSilent(true);
        }
        if (disguise.isRiderInvulnerable()) {
            host.setInvulnerable(true);
        }

        // 設置等級化屬性
        double health = mobData.calculateHealth(level);
        host.setMaxHealth(health);
        host.setHealth(health);

        // 裝備物品（給宿主）
        applyEquipment(host, mobData);

        // 生成偽裝實體（視覺顯示）
        Entity disguiseEntity = location.getWorld().spawnEntity(location, disguise.getDisguiseType());
        if (!(disguiseEntity instanceof LivingEntity)) {
            host.remove();
            disguiseEntity.remove();
            return null;
        }

        LivingEntity disguiseMob = (LivingEntity) disguiseEntity;

        // 設置偽裝實體屬性
        disguiseMob.setSilent(true);
        disguiseMob.setInvulnerable(true);
        disguiseMob.setAI(false);

        // 隨機幼年
        if (disguise.getBabyChance() > 0 && random.nextDouble() < disguise.getBabyChance()) {
            if (disguiseMob instanceof org.bukkit.entity.Ageable) {
                ((org.bukkit.entity.Ageable) disguiseMob).setBaby();
            }
        }

        // 設置名稱（顯示在偽裝實體上）
        String displayName = mobData.getName();
        if (mobData.shouldShowLevelInName() && mobData.hasLevelSystem()) {
            displayName = "&8[&eLv." + level + "&8] " + mobData.getName();
        }
        disguiseMob.setCustomName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName));
        disguiseMob.setCustomNameVisible(true);

        // 讓偽裝實體騎在宿主上
        host.addPassenger(disguiseMob);

        // 標記偽裝實體（用於識別和清理）
        disguiseMob.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "disguise_passenger"),
            PersistentDataType.STRING,
            "true"
        );

        return host;
    }

    /**
     * Apply equipment to a mob
     */
    private void applyEquipment(LivingEntity mob, MobData mobData) {
        EntityEquipment equipment = mob.getEquipment();
        if (equipment == null) {
            return;
        }

        Map<String, ItemStack> equipmentMap = mobData.getEquipment();

        if (equipmentMap.containsKey("helmet")) {
            equipment.setHelmet(equipmentMap.get("helmet"));
            equipment.setHelmetDropChance(0.0f);
        }
        if (equipmentMap.containsKey("chestplate")) {
            equipment.setChestplate(equipmentMap.get("chestplate"));
            equipment.setChestplateDropChance(0.0f);
        }
        if (equipmentMap.containsKey("leggings")) {
            equipment.setLeggings(equipmentMap.get("leggings"));
            equipment.setLeggingsDropChance(0.0f);
        }
        if (equipmentMap.containsKey("boots")) {
            equipment.setBoots(equipmentMap.get("boots"));
            equipment.setBootsDropChance(0.0f);
        }
        if (equipmentMap.containsKey("main-hand")) {
            equipment.setItemInMainHand(equipmentMap.get("main-hand"));
            equipment.setItemInMainHandDropChance(0.0f);
        }
        if (equipmentMap.containsKey("off-hand")) {
            equipment.setItemInOffHand(equipmentMap.get("off-hand"));
            equipment.setItemInOffHandDropChance(0.0f);
        }
    }

    /**
     * Check if an entity is a custom mob
     * @param entity The entity to check
     * @return Mob key if it's a custom mob, null otherwise
     */
    public String getCustomMobKey(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return null;
        }

        LivingEntity mob = (LivingEntity) entity;
        return mob.getPersistentDataContainer().get(customMobKey, PersistentDataType.STRING);
    }

    /**
     * Get the level of a custom mob
     * @param entity The entity to check
     * @return Mob level, or 1 if not found
     */
    public int getMobLevel(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return 1;
        }

        LivingEntity mob = (LivingEntity) entity;
        Integer level = mob.getPersistentDataContainer().get(mobLevelKey, PersistentDataType.INTEGER);
        return level != null ? level : 1;
    }

    /**
     * Get mob data by key
     * @param mobKey The mob identifier
     * @return MobData or null if not found
     */
    public MobData getMobData(String mobKey) {
        return mobTypes.get(mobKey);
    }

    /**
     * Get the number of registered custom mob types
     * @return Number of custom mob types
     */
    public int getMobTypeCount() {
        return mobTypes.size();
    }

    /**
     * Get all mob keys
     * @return List of mob keys
     */
    public List<String> getMobKeys() {
        return new ArrayList<>(mobTypes.keySet());
    }

    /**
     * Inner class to store custom mob data
     */
    public static class MobData {
        private final String key;
        private final String name;
        private final EntityType entityType;

        // 基礎屬性 (向後兼容)
        private final double health;
        private final double damage;

        // 等級系統
        private final int minLevel;
        private final int maxLevel;
        private final double baseHealth;
        private final double baseDamage;
        private final double healthPerLevel;
        private final double damagePerLevel;

        // 經驗值
        private final int exp;
        private final int baseExp;
        private final int expPerLevel;

        // 裝備
        private final Map<String, ItemStack> equipment;

        // 掉落物
        private final List<DropItem> vanillaDrops;
        private final List<WeaponDrop> weaponDrops;

        // 偽裝
        private final DisguiseConfig disguise;

        // 其他
        private final String specialBehavior;
        private final boolean showLevelInName;

        // 向後兼容的構造函數 (舊格式)
        public MobData(String key, String name, EntityType entityType, double health, double damage, String specialBehavior) {
            this(key, name, entityType, health, damage, specialBehavior, 0);
        }

        public MobData(String key, String name, EntityType entityType, double health, double damage, String specialBehavior, int exp) {
            this.key = key;
            this.name = name;
            this.entityType = entityType;
            this.health = health;
            this.damage = damage;
            this.baseHealth = health;
            this.baseDamage = damage;
            this.specialBehavior = specialBehavior;
            this.exp = exp;

            // 預設值（無等級系統）
            this.minLevel = 1;
            this.maxLevel = 1;
            this.healthPerLevel = 0;
            this.damagePerLevel = 0;
            this.baseExp = exp;
            this.expPerLevel = 0;
            this.equipment = new HashMap<>();
            this.vanillaDrops = new ArrayList<>();
            this.weaponDrops = new ArrayList<>();
            this.disguise = null;
            this.showLevelInName = false;
        }

        // 新格式的完整構造函數
        public MobData(String key, String name, EntityType entityType,
                       int minLevel, int maxLevel,
                       double baseHealth, double baseDamage,
                       double healthPerLevel, double damagePerLevel,
                       int baseExp, int expPerLevel,
                       Map<String, ItemStack> equipment,
                       List<DropItem> vanillaDrops, List<WeaponDrop> weaponDrops,
                       DisguiseConfig disguise,
                       String specialBehavior, boolean showLevelInName) {
            this.key = key;
            this.name = name;
            this.entityType = entityType;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.baseHealth = baseHealth;
            this.baseDamage = baseDamage;
            this.healthPerLevel = healthPerLevel;
            this.damagePerLevel = damagePerLevel;
            this.baseExp = baseExp;
            this.expPerLevel = expPerLevel;
            this.equipment = equipment;
            this.vanillaDrops = vanillaDrops;
            this.weaponDrops = weaponDrops;
            this.disguise = disguise;
            this.specialBehavior = specialBehavior;
            this.showLevelInName = showLevelInName;

            // 計算舊格式的值（向後兼容）
            this.health = baseHealth;
            this.damage = baseDamage;
            this.exp = baseExp;
        }

        // Getters
        public String getKey() { return key; }
        public String getName() { return name; }
        public EntityType getEntityType() { return entityType; }
        public double getHealth() { return health; }
        public double getDamage() { return damage; }
        public int getExp() { return exp; }
        public String getSpecialBehavior() { return specialBehavior; }

        // 新 Getters
        public int getMinLevel() { return minLevel; }
        public int getMaxLevel() { return maxLevel; }
        public double getBaseHealth() { return baseHealth; }
        public double getBaseDamage() { return baseDamage; }
        public double getHealthPerLevel() { return healthPerLevel; }
        public double getDamagePerLevel() { return damagePerLevel; }
        public int getBaseExp() { return baseExp; }
        public int getExpPerLevel() { return expPerLevel; }
        public Map<String, ItemStack> getEquipment() { return equipment; }
        public List<DropItem> getVanillaDrops() { return vanillaDrops; }
        public List<WeaponDrop> getWeaponDrops() { return weaponDrops; }
        public DisguiseConfig getDisguise() { return disguise; }
        public boolean shouldShowLevelInName() { return showLevelInName; }

        // 計算等級化屬性
        public double calculateHealth(int level) {
            return baseHealth + (level - 1) * healthPerLevel;
        }

        public double calculateDamage(int level) {
            return baseDamage + (level - 1) * damagePerLevel;
        }

        public int calculateExp(int level) {
            return baseExp + level * expPerLevel;
        }

        public boolean hasLevelSystem() {
            return maxLevel > 1 || healthPerLevel > 0 || damagePerLevel > 0;
        }
    }

    /**
     * 掉落物品配置
     */
    public static class DropItem {
        private final Material material;
        private final int minAmount;
        private final int maxAmount;
        private final double chance;

        public DropItem(Material material, int minAmount, int maxAmount, double chance) {
            this.material = material;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.chance = chance;
        }

        public Material getMaterial() { return material; }
        public int getMinAmount() { return minAmount; }
        public int getMaxAmount() { return maxAmount; }
        public double getChance() { return chance; }
    }

    /**
     * 自定義武器掉落配置
     */
    public static class WeaponDrop {
        private final String weaponKey;
        private final double chance;

        public WeaponDrop(String weaponKey, double chance) {
            this.weaponKey = weaponKey;
            this.chance = chance;
        }

        public String getWeaponKey() { return weaponKey; }
        public double getChance() { return chance; }
    }

    /**
     * 偽裝配置
     */
    public static class DisguiseConfig {
        private final boolean enabled;
        private final EntityType disguiseType;
        private final double babyChance;
        private final boolean riderInvisible;
        private final boolean riderSilent;
        private final boolean riderInvulnerable;

        public DisguiseConfig(boolean enabled, EntityType disguiseType, double babyChance,
                             boolean riderInvisible, boolean riderSilent, boolean riderInvulnerable) {
            this.enabled = enabled;
            this.disguiseType = disguiseType;
            this.babyChance = babyChance;
            this.riderInvisible = riderInvisible;
            this.riderSilent = riderSilent;
            this.riderInvulnerable = riderInvulnerable;
        }

        public boolean isEnabled() { return enabled; }
        public EntityType getDisguiseType() { return disguiseType; }
        public double getBabyChance() { return babyChance; }
        public boolean isRiderInvisible() { return riderInvisible; }
        public boolean isRiderSilent() { return riderSilent; }
        public boolean isRiderInvulnerable() { return riderInvulnerable; }
    }
}

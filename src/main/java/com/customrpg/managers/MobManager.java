package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

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

        return new DisguiseConfig(enabled, disguiseType, babyChance);
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
     * Spawn a disguised mob (directly spawn as disguise type with custom behavior)
     * 直接生成偽裝類型的生物，根據類型決定實現方式
     */
    private LivingEntity spawnDisguisedMob(MobData mobData, Location location, int level) {
        DisguiseConfig disguise = mobData.getDisguise();

        // 特殊處理：盔甲架偽裝（用於創建純視覺效果，如行走的仙人掌方塊）
        if (disguise.getDisguiseType() == EntityType.ARMOR_STAND) {
            return spawnArmorStandDisguise(mobData, location, level);
        }

        // 特殊處理：檢查是否配置了方塊材質的 helmet（用於創建行走的方塊）
        // 當裝備中的 helmet 是方塊類型時，使用 BlockDisplay 方式顯示
        if (mobData.getEquipment().containsKey("helmet")) {
            ItemStack helmetItem = mobData.getEquipment().get("helmet");
            Material helmetMaterial = helmetItem.getType();

            // 檢查是否為方塊材料（不是物品）
            if (helmetMaterial.isBlock() && !helmetMaterial.isItem()) {
                plugin.getLogger().info("檢測到方塊材質頭盔: " + helmetMaterial.name() + "，使用 BlockDisplay 模式");
                return spawnArmorStandDisguise(mobData, location, level);
            }
        }

        // 一般偽裝：直接生成偽裝實體（例如：豬）
        Entity disguiseEntity = location.getWorld().spawnEntity(location, disguise.getDisguiseType());
        if (!(disguiseEntity instanceof LivingEntity)) {
            disguiseEntity.remove();
            return null;
        }

        LivingEntity mob = (LivingEntity) disguiseEntity;

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

        // 隨機幼年
        if (disguise.getBabyChance() > 0 && random.nextDouble() < disguise.getBabyChance()) {
            if (mob instanceof org.bukkit.entity.Ageable) {
                ((org.bukkit.entity.Ageable) mob).setBaby();
            }
        }

        // 裝備物品
        applyEquipment(mob, mobData);

        // 只有當生物是被動類型且設置了傷害值時，才賦予攻擊行為
        if (isPassiveMob(disguise.getDisguiseType()) && mobData.getBaseDamage() > 0) {
            applyAggressiveBehavior(mob);
        }

        return mob;
    }

    /**
     * Spawn BlockDisplay disguise - creates a walking block (like cactus)
     * 使用 BlockDisplay 生成會行走的方塊偽裝（如仙人掌）
     *
     * 架構說明：
     * 1. 隱形殭屍作為移動核心（提供 AI 和碰撞）
     * 2. BlockDisplay 作為視覺顯示（精準位置控制）
     * 3. ArmorStand 作為名稱標籤（顯示名稱和等級）
     *
     * BlockDisplay 的優勢：
     * - 不受乘客系統限制，可以精準設置位置
     * - 支援 Transformation 調整偏移、旋轉、縮放
     * - 平滑插值移動（setTeleportDuration）
     * - 可以顯示任何方塊類型
     */
    private LivingEntity spawnArmorStandDisguise(MobData mobData, Location location, int level) {
        plugin.getLogger().info("=== 開始生成 BlockDisplay 行走方塊 ===");

        // ===== 第一步：生成隱形殭屍核心 =====
        Entity coreEntity = location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
        if (!(coreEntity instanceof Zombie core)) {
            coreEntity.remove();
            plugin.getLogger().severe("✗ 無法生成殭屍核心");
            return null;
        }

        // 設置殭屍屬性（隱形、靜音、保持 AI）
        core.setAdult(); // 使用新 API 設置成年
        core.setInvisible(true);
        core.setSilent(true);
        core.setAI(true);
        core.setRemoveWhenFarAway(false); // 防止自動清除

        // 設置生命值和傷害（等級化）
        double health = mobData.calculateHealth(level);
        core.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(health);
        core.setHealth(health);

        // 設置攻擊力（等級化）
        double damage = mobData.calculateDamage(level);
        core.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(damage);

        plugin.getLogger().info("✓ 殭屍核心已生成 (隱形, Lv." + level + ", HP:" + health + ", ATK:" + damage + ")");

        // **重要：標記殭屍核心為自定義怪物**
        // 這樣 MobListener 才能識別並正確處理傷害、掉落物、經驗值
        core.getPersistentDataContainer().set(customMobKey, PersistentDataType.STRING, mobData.getKey());
        core.getPersistentDataContainer().set(mobLevelKey, PersistentDataType.INTEGER, level);
        plugin.getLogger().info("✓ 殭屍核心已標記為自定義怪物: " + mobData.getKey() + " (Lv." + level + ")");

        // ===== 第二步：獲取方塊類型 =====
        Material blockMaterial = Material.CACTUS;
        if (mobData.getEquipment().containsKey("helmet")) {
            ItemStack helmetItem = mobData.getEquipment().get("helmet");
            blockMaterial = helmetItem.getType();
        }
        plugin.getLogger().info("✓ 方塊材質: " + blockMaterial.name());

        // ===== 第三步：生成 BlockDisplay =====
        Location displayLocation = location.clone();
        BlockDisplay blockDisplay = (BlockDisplay) location.getWorld().spawnEntity(
            displayLocation,
            EntityType.BLOCK_DISPLAY
        );

        // 設置方塊數據
        blockDisplay.setBlock(blockMaterial.createBlockData());

        // 設置 Transformation（精準位置控制的關鍵）
        // translation: 相對於當前位置的偏移量 (x, y, z)
        //   - Y 軸偏移讓方塊浮空或貼地
        //   - X/Z 軸可以微調水平位置
        // scale: 方塊大小縮放 (x, y, z)
        //   - 1.0 = 原始大小
        //   - 可以做出巨大或迷你方塊效果
        // rotation: 旋轉角度（使用四元數表示）
        Vector3f translation = new Vector3f(0f, 0.5f, 0f); // Y=0.5 讓方塊在地面上方（重要！）
        AxisAngle4f leftRotation = new AxisAngle4f(0, 0, 1, 0); // 無旋轉
        Vector3f scale = new Vector3f(1.0f, 1.0f, 1.0f); // 原始大小
        AxisAngle4f rightRotation = new AxisAngle4f(0, 0, 1, 0); // 無旋轉

        Transformation transformation = new Transformation(translation, leftRotation, scale, rightRotation);
        blockDisplay.setTransformation(transformation);

        // 設置顯示屬性
        blockDisplay.setTeleportDuration(2); // 平滑傳送（2 ticks = 0.1秒）
        blockDisplay.setInterpolationDuration(2); // 插值時間
        blockDisplay.setInterpolationDelay(0); // 立即開始插值
        blockDisplay.setViewRange(128.0f); // 可視範圍 128 格
        blockDisplay.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15)); // 最大亮度（不受環境影響）
        blockDisplay.setShadowRadius(0.5f); // 陰影半徑
        blockDisplay.setShadowStrength(1.0f); // 陰影強度

        // 標記為偽裝實體（用於清理和識別）
        blockDisplay.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "disguise_blockdisplay"),
            PersistentDataType.STRING,
            core.getUniqueId().toString()
        );

        plugin.getLogger().info("✓ BlockDisplay 已生成（平滑移動: 2 ticks）");

        // ===== 第四步：生成名稱標籤 ArmorStand =====
        Location nameLocation = location.clone().add(0, 2.0, 0); // 在方块上方 2 格
        ArmorStand nameTag = (ArmorStand) location.getWorld().spawnEntity(
            nameLocation,
            EntityType.ARMOR_STAND
        );

        // 設置為隱形 marker（不占空間、不碰撞、不受重力）
        nameTag.setVisible(false);
        nameTag.setGravity(false);
        nameTag.setInvulnerable(true);
        nameTag.setMarker(true);
        nameTag.setSmall(true);
        nameTag.setBasePlate(false);
        nameTag.setArms(false);

        // 設置名稱
        String displayName = mobData.getName();
        if (mobData.shouldShowLevelInName() && mobData.hasLevelSystem()) {
            displayName = "&8[&eLv." + level + "&8] " + mobData.getName();
        }
        nameTag.customName(net.kyori.adventure.text.Component.text(
            org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName)
        ));
        nameTag.setCustomNameVisible(true);

        // 標記為偽裝名稱標籤
        nameTag.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "disguise_nametag"),
            PersistentDataType.STRING,
            core.getUniqueId().toString()
        );

        plugin.getLogger().info("✓ 名稱標籤已生成: " + displayName);

        // ===== 第五步：建立位置同步系統 =====
        // 使用高頻率更新（每 tick）確保 BlockDisplay 跟隨殭屍移動
        // 這是精準控制位置的關鍵
        new BukkitRunnable() {
            private Location lastCoreLoc = null;
            private int tickCounter = 0;

            @Override
            public void run() {
                // 檢查核心是否還存在
                if (core.isDead() || !core.isValid()) {
                    // 清理所有關聯實體
                    if (blockDisplay.isValid()) blockDisplay.remove();
                    if (nameTag.isValid()) nameTag.remove();
                    this.cancel();
                    plugin.getLogger().info("✗ BlockDisplay 已清理（核心死亡）");
                    return;
                }

                // 獲取殭屍當前位置
                Location coreLoc = core.getLocation();
                tickCounter++;

                // 同步 BlockDisplay 位置
                if (blockDisplay.isValid()) {
                    // 檢查位置是否改變（優化性能）
                    boolean moved = lastCoreLoc == null ||
                                  lastCoreLoc.getX() != coreLoc.getX() ||
                                  lastCoreLoc.getY() != coreLoc.getY() ||
                                  lastCoreLoc.getZ() != coreLoc.getZ();

                    if (moved) {
                        // 精準設置 BlockDisplay 位置
                        // BlockDisplay 的 transformation 已經處理了 Y 軸偏移
                        Location blockLoc = coreLoc.clone();
                        blockLoc.setYaw(0); // BlockDisplay 不需要旋轉（方塊是立方體）
                        blockLoc.setPitch(0);
                        blockDisplay.teleport(blockLoc);

                        lastCoreLoc = coreLoc.clone();
                    }
                } else {
                    // BlockDisplay 消失，清理所有
                    if (nameTag.isValid()) nameTag.remove();
                    core.remove();
                    this.cancel();
                    return;
                }

                // 同步名稱標籤位置（在方塊上方）
                if (nameTag.isValid()) {
                    Location nameLoc = coreLoc.clone().add(0, 2.0, 0); // 在方块上方 2 格
                    nameLoc.setYaw(0);
                    nameLoc.setPitch(0);
                    nameTag.teleport(nameLoc);
                } else {
                    // 名稱標籤消失，清理所有
                    if (blockDisplay.isValid()) blockDisplay.remove();
                    core.remove();
                    this.cancel();
                    return;
                }

                // 每 100 ticks（5秒）輸出一次調試信息
                if (tickCounter % 100 == 0) {
                    plugin.getLogger().fine("BlockDisplay 同步中 (tick " + tickCounter + ")");
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // 每 tick 更新（20 次/秒）

        plugin.getLogger().info("=== BlockDisplay 行走方塊生成完成 ===");
        plugin.getLogger().info("  實體 UUID: " + core.getUniqueId());
        plugin.getLogger().info("  方塊類型: " + blockMaterial.name());
        plugin.getLogger().info("  顯示名稱: " + displayName);
        plugin.getLogger().info("  等級: " + level + " | HP: " + health + " | ATK: " + damage);

        return core;
    }

    /**
     * Check if an entity type is a passive mob
     * 檢查生物類型是否為被動生物
     */
    private boolean isPassiveMob(EntityType type) {
        switch (type) {
            // 被動生物
            case PIG:
            case COW:
            case SHEEP:
            case CHICKEN:
            case RABBIT:
            case HORSE:
            case DONKEY:
            case MULE:
            case LLAMA:
            case PARROT:
            case BAT:
            case OCELOT:
            case CAT:
            case PANDA:
            case FOX:
            case VILLAGER:
            case WANDERING_TRADER:
            case IRON_GOLEM:
            case SNOW_GOLEM:
            case TURTLE:
            case MOOSHROOM:
            case SQUID:
            case GLOW_SQUID:
            case COD:
            case SALMON:
            case TROPICAL_FISH:
            case PUFFERFISH:
            case DOLPHIN:
            case AXOLOTL:
            case STRIDER:
                return true;
            default:
                return false;
        }
    }

    /**
     * Apply aggressive behavior to passive mobs
     * 讓被動生物（如豬、雞等）能夠攻擊玩家
     */
    private void applyAggressiveBehavior(LivingEntity mob) {
        try {
            // 使用 Bukkit API 讓生物主動尋找附近的玩家並攻擊
            // 透過定期任務模擬攻擊行為
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (mob.isDead() || !mob.isValid()) {
                        this.cancel();
                        return;
                    }

                    // 檢查附近是否有玩家
                    Player target = findNearestPlayer(mob, 16.0);
                    if (target != null && target.getGameMode() != org.bukkit.GameMode.CREATIVE &&
                        target.getGameMode() != org.bukkit.GameMode.SPECTATOR) {

                        // 設置生物的目標為玩家
                        if (mob instanceof Mob) {
                            ((Mob) mob).setTarget(target);
                        }

                        // 讓生物朝玩家移動
                        Location targetLoc = target.getLocation();
                        Location mobLoc = mob.getLocation();

                        double distance = mobLoc.distance(targetLoc);

                        // 計算朝向玩家的角度
                        Vector direction = targetLoc.toVector().subtract(mobLoc.toVector());
                        double yaw = Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
                        double pitch = Math.toDegrees(Math.atan2(-direction.getY(),
                            Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ())));

                        // 設置生物朝向玩家
                        Location newLoc = mobLoc.clone();
                        newLoc.setYaw((float) yaw);
                        newLoc.setPitch((float) pitch);
                        mob.teleport(newLoc);

                        // 如果在攻擊範圍內（2格內），則進行攻擊
                        if (distance <= 2.0) {
                            // 透過 EntityDamageByEntityEvent 處理傷害
                            // 這裡只需要標記，實際傷害由事件處理
                            target.damage(0.1, mob); // 極小傷害，實際傷害在事件中設置
                        } else if (distance <= 16.0) {
                            // 計算移動方向（水平）
                            direction.normalize();
                            direction.setY(0); // 保持在水平面移動

                            // 設置速度（追逐玩家）
                            Vector velocity = direction.multiply(0.3);
                            velocity.setY(mob.getVelocity().getY()); // 保持原有的Y軸速度（重力）
                            mob.setVelocity(velocity);
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 10L); // 每 0.5 秒執行一次

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply aggressive behavior: " + e.getMessage());
        }
    }

    /**
     * Find the nearest player to a mob within range
     */
    private Player findNearestPlayer(LivingEntity mob, double range) {
        Player nearest = null;
        double nearestDistance = range * range; // 使用平方距離避免開方運算

        for (Player player : mob.getWorld().getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
                player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }

            double distanceSquared = player.getLocation().distanceSquared(mob.getLocation());
            if (distanceSquared < nearestDistance) {
                nearest = player;
                nearestDistance = distanceSquared;
            }
        }

        return nearest;
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

        public DisguiseConfig(boolean enabled, EntityType disguiseType, double babyChance) {
            this.enabled = enabled;
            this.disguiseType = disguiseType;
            this.babyChance = babyChance;
        }

        public boolean isEnabled() { return enabled; }
        public EntityType getDisguiseType() { return disguiseType; }
        public double getBabyChance() { return babyChance; }
    }
}

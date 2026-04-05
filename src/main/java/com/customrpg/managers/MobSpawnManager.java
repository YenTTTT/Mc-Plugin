package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * MobSpawnManager - 區域式動態怪物生成系統
 *
 * Zone-Based 生成：
 * - 玩家在 zone 內 → 不生成怪物
 * - 玩家在 zone 外 → 依距離計算怪物等級 (tier 系統)
 * - 保留 BOSS / ELITE / NORMAL 階級系統
 * - 保留累積機率 (pity) 系統
 */
public class MobSpawnManager {

    public enum MobTier {
        NORMAL("普通", ChatColor.WHITE),
        ELITE("精英", ChatColor.GOLD),
        BOSS("BOSS", ChatColor.RED);

        private final String displayName;
        private final ChatColor color;

        MobTier(String displayName, ChatColor color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public ChatColor getColor() { return color; }
    }

    private final CustomRPG plugin;
    private final MobManager mobManager;
    private final PlayerStatsManager statsManager;
    private final Logger log;
    private BukkitTask spawnTask;

    // Debug mode
    private boolean debug = false;

    // ===== Configuration =====
    private boolean enabled;
    private int intervalTicks;
    private int spawnsPerCycle;
    private int spawnRadiusMin;
    private int spawnRadiusMax;
    private int maxMobsPerPlayer;

    // Tier settings
    private int normalLevelOffsetMin, normalLevelOffsetMax;
    private String normalNamePrefix;

    private double eliteChance;
    private int eliteLevelOffsetMin, eliteLevelOffsetMax;
    private double eliteStatMultiplier;
    private String eliteNamePrefix;

    // Boss — 累積機率系統 (pity system)
    private double bossBaseChance;
    private double bossPityIncrement;
    private int bossCooldownSeconds;
    private int bossSpawnRadiusMin;
    private int bossSpawnRadiusMax;
    private int bossLevelOffsetMin, bossLevelOffsetMax;
    private double bossStatMultiplier;
    private String bossNamePrefix;
    private String bossSpawnParticle;
    private int bossParticleCount;
    private String bossSpawnSound;
    private float bossSoundVolume, bossSoundPitch;

    // Boss pity 運行時狀態 (per-player)
    private final Map<UUID, Double> playerPityChance = new HashMap<>();
    private final Map<UUID, Long> playerBossCooldown = new HashMap<>();

    // Conditions
    private boolean nightOnly;
    private int maxLightLevel;
    private boolean requireSolidGround;
    private boolean requireAirSpace;

    // Disabled worlds
    private Set<String> disabledWorlds;

    // Track spawned mobs
    private final Set<UUID> spawnedMobs = new HashSet<>();
    private final Random random = new Random();

    // 統計用
    private int totalSpawnAttempts = 0;
    private int totalSpawnSuccess = 0;
    private int totalLocationFails = 0;
    private String lastSkipReason = "無";

    public MobSpawnManager(CustomRPG plugin, MobManager mobManager, PlayerStatsManager statsManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        this.statsManager = statsManager;
        this.log = plugin.getLogger();

        loadConfig();

        if (enabled) {
            startSpawnTask();
        } else {
            log.info("[MobSpawnManager] ✗ 動態怪物生成系統已停用 (config: enabled=false)");
        }
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "config/mob_spawner.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config/mob_spawner.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        enabled = config.getBoolean("enabled", true);
        debug = config.getBoolean("debug", false);
        intervalTicks = config.getInt("interval-ticks", 60);
        spawnsPerCycle = config.getInt("spawns-per-cycle", 3);

        spawnRadiusMin = config.getInt("spawn-radius.min", 10);
        spawnRadiusMax = config.getInt("spawn-radius.max", 35);
        maxMobsPerPlayer = config.getInt("limits.max-mobs-per-player", 10);

        // Normal tier
        normalLevelOffsetMin = config.getInt("tiers.normal.level-offset-min", -3);
        normalLevelOffsetMax = config.getInt("tiers.normal.level-offset-max", 3);
        normalNamePrefix = ChatColor.translateAlternateColorCodes('&',
                config.getString("tiers.normal.name-prefix", ""));

        // Elite tier
        eliteChance = config.getDouble("tiers.elite.chance", 0.20);
        eliteLevelOffsetMin = config.getInt("tiers.elite.level-offset-min", 1);
        eliteLevelOffsetMax = config.getInt("tiers.elite.level-offset-max", 5);
        eliteStatMultiplier = config.getDouble("tiers.elite.stat-multiplier", 1.5);
        eliteNamePrefix = ChatColor.translateAlternateColorCodes('&',
                config.getString("tiers.elite.name-prefix", "&6⚔ "));

        // Boss tier — pity system
        bossBaseChance = config.getDouble("tiers.boss.base-chance", 0.002);
        bossPityIncrement = config.getDouble("tiers.boss.pity-increment", 0.003);
        bossCooldownSeconds = config.getInt("tiers.boss.cooldown-seconds", 1800);
        bossSpawnRadiusMin = config.getInt("tiers.boss.spawn-radius-min", 40);
        bossSpawnRadiusMax = config.getInt("tiers.boss.spawn-radius-max", 80);
        bossLevelOffsetMin = config.getInt("tiers.boss.level-offset-min", 3);
        bossLevelOffsetMax = config.getInt("tiers.boss.level-offset-max", 8);
        bossStatMultiplier = config.getDouble("tiers.boss.stat-multiplier", 3.0);
        bossNamePrefix = ChatColor.translateAlternateColorCodes('&',
                config.getString("tiers.boss.name-prefix", "&c✦ BOSS ✦ "));
        bossSpawnParticle = config.getString("tiers.boss.spawn-particle", "FLAME");
        bossParticleCount = config.getInt("tiers.boss.spawn-particle-count", 100);
        bossSpawnSound = config.getString("tiers.boss.spawn-sound", "ENTITY_ENDER_DRAGON_GROWL");
        bossSoundVolume = (float) config.getDouble("tiers.boss.spawn-sound-volume", 2.0);
        bossSoundPitch = (float) config.getDouble("tiers.boss.spawn-sound-pitch", 0.5);

        // Conditions
        nightOnly = config.getBoolean("conditions.night-only", false);
        maxLightLevel = config.getInt("conditions.max-light-level", 15);
        requireSolidGround = config.getBoolean("conditions.require-solid-ground", true);
        requireAirSpace = config.getBoolean("conditions.require-air-space", true);

        disabledWorlds = new HashSet<>(config.getStringList("disabled-worlds"));
    }

    private void startSpawnTask() {
        spawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                int playerCount = plugin.getServer().getOnlinePlayers().size();
                if (playerCount == 0) return;

                if (debug) {
                    log.info("[MobSpawn] === 開始生成周期 === 在線玩家: " + playerCount);
                }

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    processPlayer(player);
                }
            }
        }.runTaskTimer(plugin, 40, intervalTicks);
    }

    private void processPlayer(Player player) {
        String pName = player.getName();

        // 跳過旁觀者
        if (player.getGameMode() == GameMode.SPECTATOR) {
            if (debug) log.info("[MobSpawn] 跳過 " + pName + ": 旁觀者模式");
            lastSkipReason = "旁觀者模式";
            return;
        }

        // 跳過禁用世界
        String worldName = player.getWorld().getName();
        if (disabledWorlds.contains(worldName)) {
            if (debug) log.info("[MobSpawn] 跳過 " + pName + ": 世界 " + worldName + " 已禁用");
            lastSkipReason = "世界禁用: " + worldName;
            return;
        }

        // 夜晚限制
        if (nightOnly) {
            long time = player.getWorld().getTime();
            if (time < 13000 || time > 23000) {
                if (debug) log.info("[MobSpawn] 跳過 " + pName + ": 白天 (time=" + time + ")");
                lastSkipReason = "白天";
                return;
            }
        }

        // ===== Zone-Based 檢查 =====
        ZoneManager zoneManager = plugin.getZoneManager();
        if (zoneManager == null || zoneManager.getZoneCount() == 0) {
            // 尚未設置任何 zone → 完全不生成自訂怪物
            if (debug) log.info("[MobSpawn] 跳過 " + pName + ": 尚未設定任何怪物區域 (/setmob)");
            lastSkipReason = "尚未設定怪物區域";
            return;
        }

        // 玩家在安全區域內 → 檢查是否為純安全區域（無生態域標籤）
        // 有生態域標籤的區域不阻止生成（怪物會生成在區域外）
        if (zoneManager.isInsideAnyZone(player.getLocation())) {
            // 檢查玩家所在的 zone 是否有 biome tag
            ZoneManager.MobZone playerZone = zoneManager.getZoneAt(player.getLocation());
            if (playerZone == null || playerZone.biomeTag == null) {
                // 純安全區域（無生態域標籤）→ 不生成
                if (debug) log.info("[MobSpawn] 跳過 " + pName + ": 在怪物安全區域內");
                lastSkipReason = "怪物安全區域";
                return;
            }
            // 有生態域標籤 → 允許生成（怪物生成在區域外）
            if (debug) log.info("[MobSpawn] " + pName + " 在生態域區域 '" + playerZone.name + "' [" + playerZone.biomeTag + "] 內，允許在區域外生成怪物");
        }

        // 保護區域 — 原有的 ProtectionArea 系統也繼續生效
        ProtectionAreaManager pam = plugin.getProtectionAreaManager();
        if (pam != null && pam.isInProtectedArea(player.getLocation())) {
            if (debug) log.info("[MobSpawn] 跳過 " + pName + ": 在保護區域內");
            lastSkipReason = "保護區域";
            return;
        }

        // 怪物數量上限
        int nearbyMobCount = countNearbyCustomMobs(player);
        if (nearbyMobCount >= maxMobsPerPlayer) {
            if (debug) log.info("[MobSpawn] 跳過 " + pName + ": 附近已有 " + nearbyMobCount + "/" + maxMobsPerPlayer + " 隻");
            lastSkipReason = "已達上限: " + nearbyMobCount;
            return;
        }

        // 檢查可用怪物 (普通怪物池，不包含 Boss)
        List<String> mobKeys = mobManager.getNormalMobKeys();
        if (mobKeys.isEmpty()) {
            if (debug) log.info("[MobSpawn] 跳過 " + pName + ": 沒有已註冊的普通怪物類型！");
            lastSkipReason = "無可用怪物類型";
            return;
        }

        // 嘗試生成
        int toSpawn = Math.min(spawnsPerCycle, maxMobsPerPlayer - nearbyMobCount);
        if (debug) log.info("[MobSpawn] 為 " + pName + " 嘗試生成 " + toSpawn + " 隻 (附近: " + nearbyMobCount + ", 可用類型: " + mobKeys.size() + ")");

        int spawned = 0;
        for (int i = 0; i < toSpawn; i++) {
            if (attemptSpawn(player, mobKeys)) {
                spawned++;
            }
        }

        if (debug) log.info("[MobSpawn] " + pName + " 本周期成功生成: " + spawned + "/" + toSpawn);
    }

    private int countNearbyCustomMobs(Player player) {
        int count = 0;
        int searchRadius = Math.max(spawnRadiusMax, bossSpawnRadiusMax);
        NamespacedKey key = mobManager.getCustomMobNamespacedKey();
        for (Entity entity : player.getNearbyEntities(searchRadius, searchRadius, searchRadius)) {
            if (entity instanceof LivingEntity mob) {
                if (mob.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 嘗試生成一隻怪物
     */
    private boolean attemptSpawn(Player player, List<String> mobKeys) {
        totalSpawnAttempts++;

        // 先決定階級 (含 pity system)
        MobTier tier = determineTier(player);

        // 根據階級選擇不同的生成位置
        Location spawnLoc;
        if (tier == MobTier.BOSS) {
            spawnLoc = findBossSpawnLocation(player);
        } else {
            spawnLoc = findSpawnLocation(player);
        }

        if (spawnLoc == null) {
            totalLocationFails++;
            if (debug) log.info("[MobSpawn]   找不到有效生成位置 (嘗試5次失敗)");
            return false;
        }

        // ===== Zone-Based 等級計算 =====
        ZoneManager zoneManager = plugin.getZoneManager();
        int mobLevel;
        // Zone 一定存在 (processPlayer 已檢查過)
        int[] levelRange = zoneManager.calculateMobLevelRange(spawnLoc);
        int baseLevel = levelRange[0] + random.nextInt(Math.max(1, levelRange[1] - levelRange[0] + 1));

        // 再疊加階級偏移
        int tierOffset = calculateTierLevelOffset(tier);
        mobLevel = Math.max(1, baseLevel + tierOffset);

        if (debug) {
            int zoneTier = zoneManager.calculateTier(spawnLoc);
            log.info("[MobSpawn]   Zone等級: " + levelRange[0] + "~" + levelRange[1]
                    + " (Tier " + zoneTier + "), 基礎=" + baseLevel + " + 階級偏移=" + tierOffset
                    + " → 最終等級=" + mobLevel);
        }

        // ===== 根據等級篩選合適的怪物 =====
        // Boss 階級使用獨立的 Boss 怪物池
        List<String> candidateMobKeys;
        if (tier == MobTier.BOSS) {
            candidateMobKeys = mobManager.getBossMobKeys();
            if (candidateMobKeys.isEmpty()) {
                if (debug) log.info("[MobSpawn]   Boss 怪物池為空，跳過 Boss 生成");
                return false;
            }
        } else {
            candidateMobKeys = mobKeys;
        }

        // 檢查最近的zone是否有生態域標籤，如果有則過濾怪物
        List<String> filteredMobKeys = candidateMobKeys;
        ZoneManager.MobZone nearestZone = zoneManager.getNearestZone(spawnLoc);
        if (nearestZone != null && nearestZone.biomeTag != null) {
            String biomeTag = nearestZone.biomeTag.toLowerCase();
            filteredMobKeys = new ArrayList<>();
            for (String key : candidateMobKeys) {
                MobManager.MobData data = mobManager.getMobData(key);
                if (data == null) continue;
                List<String> mobTags = data.getTags();
                // 包含該生態域標籤的怪物，或沒有設定任何標籤的怪物（通用怪物）
                if (mobTags.isEmpty() || mobTags.contains(biomeTag)) {
                    filteredMobKeys.add(key);
                }
            }
            if (debug) {
                log.info("[MobSpawn]   生態域標籤: " + biomeTag + " → 過濾後可用怪物: " + filteredMobKeys.size() + "/" + candidateMobKeys.size());
            }
            // 如果過濾後沒有任何怪物，回退到候選池
            if (filteredMobKeys.isEmpty()) {
                if (debug) log.info("[MobSpawn]   生態域過濾後無怪物可用，回退到候選池");
                filteredMobKeys = candidateMobKeys;
            }
        }

        String mobKey = selectMobTypeForLevel(filteredMobKeys, mobLevel);

        if (mobKey == null) {
            if (debug) log.info("[MobSpawn]   找不到符合等級 " + mobLevel + " 的怪物類型");
            return false;
        }

        if (debug) {
            log.info("[MobSpawn]   生成: " + mobKey + " [" + tier.getDisplayName() + "] Lv." + mobLevel
                    + " 在 (" + spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ() + ")");
        }

        boolean success = spawnTieredMob(mobKey, spawnLoc, mobLevel, tier);
        if (success) {
            totalSpawnSuccess++;
            // BOSS 成功生成 → 重置 pity、設定冷卻
            if (tier == MobTier.BOSS) {
                UUID pid = player.getUniqueId();
                playerPityChance.put(pid, bossBaseChance);
                playerBossCooldown.put(pid, System.currentTimeMillis() + (bossCooldownSeconds * 1000L));
                if (debug) log.info("[MobSpawn]   ★ BOSS 已生成！pity 重置為 " + String.format("%.2f%%", bossBaseChance * 100)
                        + "，冷卻 " + bossCooldownSeconds + " 秒");
            }
        }
        return success;
    }

    /**
     * 計算階級等級偏移
     */
    private int calculateTierLevelOffset(MobTier tier) {
        return switch (tier) {
            case ELITE -> ThreadLocalRandom.current().nextInt(eliteLevelOffsetMin, eliteLevelOffsetMax + 1);
            case BOSS -> ThreadLocalRandom.current().nextInt(bossLevelOffsetMin, bossLevelOffsetMax + 1);
            default -> ThreadLocalRandom.current().nextInt(normalLevelOffsetMin, normalLevelOffsetMax + 1);
        };
    }

    /**
     * 決定怪物階級 (含累積機率系統)
     */
    private MobTier determineTier(Player player) {
        UUID pid = player.getUniqueId();

        // === BOSS 擲骰 (pity system) ===
        long now = System.currentTimeMillis();
        Long cooldownEnd = playerBossCooldown.get(pid);
        boolean bossOnCooldown = (cooldownEnd != null && now < cooldownEnd);

        if (!bossOnCooldown) {
            double currentPity = playerPityChance.getOrDefault(pid, bossBaseChance);
            double roll = random.nextDouble();

            if (debug) {
                log.info("[MobSpawn]   BOSS擲骰: roll=" + String.format("%.4f", roll)
                        + " 需要 < " + String.format("%.4f", currentPity)
                        + " (" + String.format("%.2f%%", currentPity * 100) + ")");
            }

            if (roll < currentPity) {
                return MobTier.BOSS;
            } else {
                double newPity = currentPity + bossPityIncrement;
                playerPityChance.put(pid, newPity);
                if (debug) {
                    log.info("[MobSpawn]   BOSS未命中，pity 累積: "
                            + String.format("%.2f%%", currentPity * 100) + " → "
                            + String.format("%.2f%%", newPity * 100));
                }
            }
        } else if (debug) {
            long remainSec = (cooldownEnd - now) / 1000;
            log.info("[MobSpawn]   BOSS 冷卻中 (剩餘 " + remainSec + " 秒)");
        }

        // === 精英 / 普通 擲骰 ===
        // 高 tier 區域提升精英機率
        double adjustedEliteChance = eliteChance;
        ZoneManager zoneManager = plugin.getZoneManager();
        if (zoneManager != null && zoneManager.getZoneCount() > 0) {
            int zoneTier = zoneManager.calculateTier(player.getLocation());
            // 每個 tier 增加 5% 精英機率
            adjustedEliteChance = Math.min(0.80, eliteChance + (zoneTier - 1) * 0.05);
        }

        double roll = random.nextDouble();
        if (roll < adjustedEliteChance) {
            return MobTier.ELITE;
        }
        return MobTier.NORMAL;
    }

    /**
     * 尋找普通怪 / 精英怪的生成位置
     */
    private Location findSpawnLocation(Player player) {
        return findSpawnLocationInRadius(player, spawnRadiusMin, spawnRadiusMax);
    }

    /**
     * 尋找 BOSS 的生成位置 (更遠)
     */
    private Location findBossSpawnLocation(Player player) {
        return findSpawnLocationInRadius(player, bossSpawnRadiusMin, bossSpawnRadiusMax);
    }

    /**
     * 在指定半徑範圍內尋找有效生成位置
     */
    private Location findSpawnLocationInRadius(Player player, int radiusMin, int radiusMax) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();

        for (int attempt = 0; attempt < 5; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = radiusMin + random.nextDouble() * (radiusMax - radiusMin);

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;

            // 確保 chunk 已載入
            int chunkX = (int) x >> 4;
            int chunkZ = (int) z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            int highestY = world.getHighestBlockYAt((int) x, (int) z);
            if (highestY < world.getMinHeight() + 1) {
                continue;
            }

            Location candidate = new Location(world, x + 0.5, highestY + 1, z + 0.5);

            if (!isValidSpawnLocation(candidate)) {
                continue;
            }

            // 檢查是否在 Zone 安全區內
            ZoneManager zoneManager = plugin.getZoneManager();
            if (zoneManager != null && zoneManager.isInsideAnyZone(candidate)) {
                if (debug) log.info("[MobSpawn]   候選位置在怪物安全區域內，跳過");
                continue;
            }

            // 檢查是否在保護區域內
            ProtectionAreaManager pam = plugin.getProtectionAreaManager();
            if (pam != null && pam.isInProtectedArea2D(candidate)) {
                if (debug) log.info("[MobSpawn]   候選位置在保護區域內，跳過");
                continue;
            }

            if (maxLightLevel < 15) {
                Block block = candidate.getBlock();
                if (block.getLightLevel() > maxLightLevel) {
                    continue;
                }
            }

            return candidate;
        }

        return null;
    }

    private boolean isValidSpawnLocation(Location location) {
        Block feetBlock = location.getBlock();
        Block groundBlock = feetBlock.getRelative(BlockFace.DOWN);
        Block headBlock = feetBlock.getRelative(BlockFace.UP);

        if (requireSolidGround && !groundBlock.getType().isSolid()) {
            return false;
        }

        if (requireAirSpace) {
            if (!feetBlock.getType().isAir() || !headBlock.getType().isAir()) {
                return false;
            }
        }

        if (groundBlock.isLiquid() || feetBlock.isLiquid()) {
            return false;
        }

        return true;
    }

    /**
     * 舊版等級計算 (當沒有 zone 時使用)
     */
    private int calculateLevelLegacy(int playerLevel, MobTier tier, Location playerLocation) {
        int levelOffset = calculateTierLevelOffset(tier);
        int level = playerLevel + levelOffset;
        return Math.max(1, level);
    }

    private String selectMobType(List<String> mobKeys) {
        return mobKeys.get(random.nextInt(mobKeys.size()));
    }

    /**
     * 根據等級篩選合適的怪物類型
     * 只選擇其 level-range 包含（或接近）指定等級的怪物
     *
     * @param mobKeys 所有可用的怪物 key
     * @param level   目標等級
     * @return 選中的 mob key，或 null（沒有符合的）
     */
    private String selectMobTypeForLevel(List<String> mobKeys, int level) {
        // 第一輪：嚴格匹配 — 怪物的 level-range 包含目標等級
        List<String> exactMatches = new ArrayList<>();
        for (String key : mobKeys) {
            MobManager.MobData data = mobManager.getMobData(key);
            if (data == null) continue;
            if (level >= data.getMinLevel() && level <= data.getMaxLevel()) {
                exactMatches.add(key);
            }
        }
        if (!exactMatches.isEmpty()) {
            return exactMatches.get(random.nextInt(exactMatches.size()));
        }

        // 第二輪：寬鬆匹配 — 允許 ±5 等級的容差
        List<String> nearMatches = new ArrayList<>();
        for (String key : mobKeys) {
            MobManager.MobData data = mobManager.getMobData(key);
            if (data == null) continue;
            if (level >= data.getMinLevel() - 5 && level <= data.getMaxLevel() + 5) {
                nearMatches.add(key);
            }
        }
        if (!nearMatches.isEmpty()) {
            return nearMatches.get(random.nextInt(nearMatches.size()));
        }

        // 第三輪：找最接近的怪物
        String closest = null;
        int closestDist = Integer.MAX_VALUE;
        for (String key : mobKeys) {
            MobManager.MobData data = mobManager.getMobData(key);
            if (data == null) continue;
            int dist = Math.min(Math.abs(level - data.getMinLevel()), Math.abs(level - data.getMaxLevel()));
            if (dist < closestDist) {
                closestDist = dist;
                closest = key;
            }
        }
        return closest;
    }

    private boolean spawnTieredMob(String mobKey, Location location, int level, MobTier tier) {
        LivingEntity mob = mobManager.spawnCustomMobWithLevel(mobKey, location, level);
        if (mob == null) {
            if (debug) log.warning("[MobSpawn]   生成失敗: mobManager 回傳 null (key=" + mobKey + ")");
            return false;
        }

        mobManager.setMobTier(mob, tier.name());
        spawnedMobs.add(mob.getUniqueId());

        MobManager.MobData mobData = mobManager.getMobData(mobKey);

        // 群體生成（如草原狼 pack_passive）
        if (mobData != null && "pack_passive".equalsIgnoreCase(mobData.getSpecialBehavior())) {
            int packSize = 2 + random.nextInt(4); // 額外 2~5 隻（加上原本的 1 隻 = 3~6 隻）
            for (int i = 0; i < packSize; i++) {
                double ox = (random.nextDouble() - 0.5) * 4;
                double oz = (random.nextDouble() - 0.5) * 4;
                Location packLoc = location.clone().add(ox, 0, oz);
                packLoc.setY(packLoc.getWorld().getHighestBlockYAt(packLoc) + 1);
                LivingEntity packMob = mobManager.spawnCustomMobWithLevel(mobKey, packLoc, level);
                if (packMob != null) {
                    mobManager.setMobTier(packMob, tier.name());
                    spawnedMobs.add(packMob.getUniqueId());
                }
            }
        }

        switch (tier) {
            case ELITE:
                applyEliteModifiers(mob);
                break;
            case BOSS:
                applyBossModifiers(mob, location, level);
                break;
            default:
                if (!normalNamePrefix.isEmpty()) {
                    String currentName = mob.getCustomName();
                    if (currentName != null) {
                        mob.setCustomName(normalNamePrefix + currentName);
                    }
                }
                break;
        }
        return true;
    }

    private void applyEliteModifiers(LivingEntity mob) {
        if (eliteStatMultiplier != 1.0) {
            double newMaxHealth = mob.getMaxHealth() * eliteStatMultiplier;
            mob.setMaxHealth(newMaxHealth);
            mob.setHealth(newMaxHealth);
        }

        String currentName = mob.getCustomName();
        if (currentName != null) {
            mob.setCustomName(eliteNamePrefix + ChatColor.translateAlternateColorCodes('&', "&e[精英] ") + currentName);
        }

        mob.setGlowing(true);
    }

    private void applyBossModifiers(LivingEntity mob, Location location, int level) {
        if (bossStatMultiplier != 1.0) {
            double newMaxHealth = mob.getMaxHealth() * bossStatMultiplier;
            mob.setMaxHealth(newMaxHealth);
            mob.setHealth(newMaxHealth);
        }

        String currentName = mob.getCustomName();
        if (currentName != null) {
            mob.setCustomName(bossNamePrefix + currentName);
        }

        mob.setGlowing(true);

        try {
            Particle particle = Particle.valueOf(bossSpawnParticle);
            location.getWorld().spawnParticle(particle, location.clone().add(0, 1, 0),
                    bossParticleCount, 1.5, 1.5, 1.5, 0.1);
        } catch (IllegalArgumentException e) {
            location.getWorld().spawnParticle(Particle.FLAME, location.clone().add(0, 1, 0),
                    bossParticleCount, 1.5, 1.5, 1.5, 0.1);
        }

        location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location.clone().add(0, 1, 0),
                50, 2.0, 2.0, 2.0, 0.05);

        try {
            Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(bossSpawnSound.toLowerCase()));
            if (sound != null) {
                location.getWorld().playSound(location, sound, bossSoundVolume, bossSoundPitch);
            } else {
                location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
            }
        } catch (Exception e) {
            location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        }

        announceBossSpawn(mob, location, level);
    }

    private void announceBossSpawn(LivingEntity mob, Location location, int level) {
        String mobName = mob.getCustomName() != null ? mob.getCustomName() : "未知BOSS";
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();

        String coordStr = ChatColor.AQUA + "(" + bx + ", " + by + ", " + bz + ")";

        // 顯示 Zone Tier 資訊
        String tierInfo = "";
        ZoneManager zoneManager = plugin.getZoneManager();
        if (zoneManager != null && zoneManager.getZoneCount() > 0) {
            int zoneTier = zoneManager.calculateTier(location);
            tierInfo = ChatColor.GRAY + " [" + zoneManager.getTierDisplayName(zoneTier) + ChatColor.GRAY + "]";
        }

        String message = ChatColor.RED + "" + ChatColor.BOLD + "⚠ "
                + ChatColor.DARK_RED + "" + ChatColor.BOLD + "【BOSS出現】\n"
                + ChatColor.RED + mobName + ChatColor.GRAY + " (Lv." + level + ")" + tierInfo + "\n"
                + ChatColor.YELLOW + "座標: " + coordStr + "\n"
                + ChatColor.GOLD + "快去討伐吧！";

        double notifyRadius = bossSpawnRadiusMax * 3.0;

        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distance(location) <= notifyRadius) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "════════════════════════════════");
                player.sendMessage(message);
                player.sendMessage(ChatColor.GOLD + "════════════════════════════════");
                player.sendMessage("");
                player.sendTitle(
                        ChatColor.RED + "⚠ BOSS 出現 ⚠",
                        ChatColor.GOLD + mobName + ChatColor.GRAY + " Lv." + level + "  " + coordStr,
                        10, 80, 20
                );
            }
        }
    }

    // ==========================================
    // ===== 測試 / 指令用公開方法 =====
    // ==========================================

    /**
     * 強制在玩家附近生成一隻指定類型的怪物 (用於測試)
     */
    public String forceSpawn(Player player, String mobKey, MobTier tier) {
        List<String> mobKeys = mobManager.getMobKeys();
        if (mobKeys.isEmpty()) {
            return ChatColor.RED + "沒有已註冊的怪物類型！";
        }

        if (mobKey == null || mobKey.isEmpty()) {
            mobKey = selectMobType(mobKeys);
        } else if (mobManager.getMobData(mobKey) == null) {
            return ChatColor.RED + "未知的怪物類型: " + mobKey + "\n"
                    + ChatColor.YELLOW + "可用類型: " + String.join(", ", mobKeys);
        }

        Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().multiply(8));
        spawnLoc.setY(player.getWorld().getHighestBlockYAt(spawnLoc.getBlockX(), spawnLoc.getBlockZ()) + 1);

        // Zone-based level
        int mobLevel;
        ZoneManager zoneManager = plugin.getZoneManager();
        if (zoneManager != null && zoneManager.getZoneCount() > 0) {
            int[] levelRange = zoneManager.calculateMobLevelRange(spawnLoc);
            int baseLevel = levelRange[0] + random.nextInt(Math.max(1, levelRange[1] - levelRange[0] + 1));
            int tierOffset = calculateTierLevelOffset(tier);
            mobLevel = Math.max(1, baseLevel + tierOffset);
        } else {
            PlayerStats stats = statsManager.getStats(player);
            int playerLevel = stats.getLevel();
            mobLevel = calculateLevelLegacy(playerLevel, tier, player.getLocation());
        }

        boolean success = spawnTieredMob(mobKey, spawnLoc, mobLevel, tier);
        if (success) {
            return ChatColor.GREEN + "✓ 成功生成 " + tier.getColor() + "[" + tier.getDisplayName() + "] "
                    + ChatColor.WHITE + mobKey + " Lv." + mobLevel
                    + ChatColor.GRAY + " 在 (" + spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ() + ")";
        } else {
            return ChatColor.RED + "✗ 生成失敗: " + mobKey;
        }
    }

    /**
     * 取得診斷資訊 (用於 /mobspawn status)
     */
    public List<String> getDebugStatus(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "========= MobSpawnManager 診斷 =========");
        lines.add(ChatColor.YELLOW + "系統狀態: " + (enabled ? ChatColor.GREEN + "啟用" : ChatColor.RED + "停用"));
        lines.add(ChatColor.YELLOW + "生成任務: " + (spawnTask != null && !spawnTask.isCancelled() ? ChatColor.GREEN + "運行中" : ChatColor.RED + "未運行"));
        lines.add(ChatColor.YELLOW + "間隔: " + ChatColor.WHITE + intervalTicks + " ticks (" + (intervalTicks / 20.0) + "秒)");
        lines.add(ChatColor.YELLOW + "每次生成: " + ChatColor.WHITE + spawnsPerCycle + " 隻");
        lines.add("");

        // 怪物類型
        List<String> mobKeys = mobManager.getMobKeys();
        List<String> bossMobKeys = mobManager.getBossMobKeys();
        List<String> normalMobKeys = mobManager.getNormalMobKeys();
        lines.add(ChatColor.YELLOW + "已註冊怪物類型: " + ChatColor.WHITE + mobKeys.size()
                + ChatColor.GRAY + " (普通: " + normalMobKeys.size() + ", Boss: " + bossMobKeys.size() + ")");
        if (mobKeys.size() <= 20) {
            lines.add(ChatColor.GRAY + "  " + String.join(", ", mobKeys));
        }
        if (!bossMobKeys.isEmpty()) {
            lines.add(ChatColor.RED + "  Boss: " + String.join(", ", bossMobKeys));
        }
        lines.add("");

        // Zone 資訊
        ZoneManager zoneManager = plugin.getZoneManager();
        if (zoneManager != null) {
            lines.add(ChatColor.AQUA + "--- Zone-Based 系統 ---");
            lines.add(ChatColor.YELLOW + "怪物區域數量: " + ChatColor.WHITE + zoneManager.getZoneCount());
            for (var zone : zoneManager.getAllZones()) {
                lines.add(ChatColor.GRAY + "  " + zone.name + ": Lv." + zone.minLevel + "~" + zone.maxLevel
                        + " (步進: " + zone.radiusStep + "格)");
            }
            lines.add("");
        }

        // 玩家資訊
        if (player != null) {
            lines.add(ChatColor.AQUA + "--- 你的狀態 ---");
            lines.add(ChatColor.YELLOW + "遊戲模式: " + ChatColor.WHITE + player.getGameMode().name());
            lines.add(ChatColor.YELLOW + "世界: " + ChatColor.WHITE + player.getWorld().getName()
                    + (disabledWorlds.contains(player.getWorld().getName()) ? ChatColor.RED + " (已禁用)" : ChatColor.GREEN + " (允許)"));

            // Zone-based 狀態
            if (zoneManager != null && zoneManager.getZoneCount() > 0) {
                boolean inZone = zoneManager.isInsideAnyZone(player.getLocation());
                lines.add(ChatColor.YELLOW + "安全區域: " + (inZone ? ChatColor.GREEN + "✓ 在安全區內" : ChatColor.RED + "✗ 在野外"));

                int zoneTier = zoneManager.calculateTier(player.getLocation());
                lines.add(ChatColor.YELLOW + "Zone Tier: " + zoneManager.getTierDisplayName(zoneTier));

                int[] levelRange = zoneManager.calculateMobLevelRange(player.getLocation());
                lines.add(ChatColor.YELLOW + "怪物等級: " + ChatColor.WHITE + "Lv." + levelRange[0] + " ~ Lv." + levelRange[1]);

                double dist = zoneManager.getDistanceToNearestZoneBorder(player.getLocation());
                if (dist >= 0) {
                    lines.add(ChatColor.YELLOW + "距最近安全區: " + ChatColor.WHITE + String.format("%.1f", dist) + " 格");
                }
            }

            int nearbyCount = countNearbyCustomMobs(player);
            lines.add(ChatColor.YELLOW + "附近自訂怪物: " + ChatColor.WHITE + nearbyCount + "/" + maxMobsPerPlayer
                    + (nearbyCount >= maxMobsPerPlayer ? ChatColor.RED + " (已達上限!)" : ChatColor.GREEN + " (未滿 ✓)"));

            PlayerStats stats = statsManager.getStats(player);
            lines.add(ChatColor.YELLOW + "玩家等級: " + ChatColor.WHITE + stats.getLevel());

            if (nightOnly) {
                long time = player.getWorld().getTime();
                lines.add(ChatColor.YELLOW + "時間: " + ChatColor.WHITE + time
                        + ((time < 13000 || time > 23000) ? ChatColor.RED + " (白天，不生成)" : ChatColor.GREEN + " (夜晚 ✓)"));
            }

            // BOSS pity 系統資訊
            lines.add("");
            lines.add(ChatColor.LIGHT_PURPLE + "--- BOSS 累積機率系統 ---");
            UUID pid = player.getUniqueId();
            double currentPity = playerPityChance.getOrDefault(pid, bossBaseChance);
            lines.add(ChatColor.YELLOW + "基礎機率: " + ChatColor.WHITE + String.format("%.2f%%", bossBaseChance * 100));
            lines.add(ChatColor.YELLOW + "每次累積: " + ChatColor.WHITE + "+" + String.format("%.2f%%", bossPityIncrement * 100));
            lines.add(ChatColor.YELLOW + "當前機率: " + ChatColor.GREEN + String.format("%.2f%%", currentPity * 100));

            Long cooldownEnd = playerBossCooldown.get(pid);
            if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
                long remainSec = (cooldownEnd - System.currentTimeMillis()) / 1000;
                long min = remainSec / 60;
                long sec = remainSec % 60;
                lines.add(ChatColor.YELLOW + "BOSS冷卻: " + ChatColor.RED + min + "分" + sec + "秒");
            } else {
                lines.add(ChatColor.YELLOW + "BOSS冷卻: " + ChatColor.GREEN + "就緒 ✓");
            }
            lines.add(ChatColor.YELLOW + "BOSS生成範圍: " + ChatColor.WHITE + bossSpawnRadiusMin + "~" + bossSpawnRadiusMax + " 格");
        }
        lines.add("");

        // 統計
        lines.add(ChatColor.AQUA + "--- 生成統計 ---");
        lines.add(ChatColor.YELLOW + "總嘗試: " + ChatColor.WHITE + totalSpawnAttempts);
        lines.add(ChatColor.YELLOW + "總成功: " + ChatColor.GREEN + totalSpawnSuccess);
        lines.add(ChatColor.YELLOW + "位置失敗: " + ChatColor.RED + totalLocationFails);
        lines.add(ChatColor.YELLOW + "追蹤中怪物: " + ChatColor.WHITE + getSpawnedMobCount());
        lines.add(ChatColor.YELLOW + "最後跳過原因: " + ChatColor.WHITE + lastSkipReason);
        lines.add(ChatColor.GOLD + "==========================================");

        return lines;
    }

    /**
     * 清除所有已追蹤的怪物
     */
    public int clearAllSpawnedMobs() {
        int removed = 0;
        for (UUID uuid : spawnedMobs) {
            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
                removed++;
            }
        }
        spawnedMobs.clear();
        return removed;
    }

    /**
     * 切換 debug 模式
     */
    public boolean toggleDebug() {
        debug = !debug;
        return debug;
    }

    public void reload() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        loadConfig();
        if (enabled) {
            startSpawnTask();
        } else {
            log.info("[MobSpawnManager] 配置已重新載入，生成系統已停用");
        }
    }

    public void shutdown() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        int removed = clearAllSpawnedMobs();
    }

    public int getSpawnedMobCount() {
        spawnedMobs.removeIf(uuid -> {
            Entity entity = plugin.getServer().getEntity(uuid);
            return entity == null || entity.isDead();
        });
        return spawnedMobs.size();
    }

    public boolean isEnabled() {
        return enabled;
    }
}



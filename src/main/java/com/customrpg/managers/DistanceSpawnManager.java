package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class DistanceSpawnManager {

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

        public String getDisplayName() {
            return displayName;
        }

        public ChatColor getColor() {
            return color;
        }
    }

    private final CustomRPG plugin;
    private final MobManager mobManager;
    private final SafeZoneManager safeZoneManager;
    private final Logger log;
    private final Random random = new Random();
    private final Set<UUID> spawnedMobs = new HashSet<>();

    private BossZoneManager bossZoneManager;
    private BukkitTask spawnTask;

    private boolean enabled;
    private boolean active;
    private boolean debug;
    private int intervalTicks;
    private int spawnsPerCycle;
    private int spawnRadiusMin;
    private int spawnRadiusMax;
    private int maxMobsPerPlayer;
    private Set<String> disabledWorlds = new HashSet<>();

    private String originWorld;
    private double originX;
    private double originY;
    private double originZ;
    private int baseLevel;
    private double blocksPerLevel;

    private int normalLevelOffsetMin;
    private int normalLevelOffsetMax;
    private String normalNamePrefix;

    private double eliteChance;
    private int eliteLevelOffsetMin;
    private int eliteLevelOffsetMax;
    private double eliteStatMultiplier;
    private String eliteNamePrefix;

    private int bossLevelOffsetMin;
    private int bossLevelOffsetMax;
    private double bossStatMultiplier;
    private String bossNamePrefix;
    private String bossSpawnParticle;
    private int bossParticleCount;
    private String bossSpawnSound;
    private float bossSoundVolume;
    private float bossSoundPitch;

    private boolean nightOnly;
    private int maxLightLevel;
    private boolean requireSolidGround;
    private boolean requireAirSpace;

    private int totalSpawnAttempts;
    private int totalSpawnSuccess;
    private int totalLocationFails;
    private String lastSkipReason = "尚未執行 /finishrpg";

    public DistanceSpawnManager(CustomRPG plugin, MobManager mobManager, SafeZoneManager safeZoneManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        this.safeZoneManager = safeZoneManager;
        this.log = plugin.getLogger();
        loadConfig();
        startSpawnTask();
    }

    public void setBossZoneManager(BossZoneManager bossZoneManager) {
        this.bossZoneManager = bossZoneManager;
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "config/mob_spawner.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config/mob_spawner.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        enabled = config.getBoolean("enabled", true);
        active = false;
        debug = config.getBoolean("debug", false);
        intervalTicks = config.getInt("interval-ticks", 60);
        spawnsPerCycle = config.getInt("spawns-per-cycle", 3);
        spawnRadiusMin = config.getInt("spawn-radius.min", 10);
        spawnRadiusMax = config.getInt("spawn-radius.max", 35);
        maxMobsPerPlayer = config.getInt("limits.max-mobs-per-player", 10);
        disabledWorlds = new HashSet<>(config.getStringList("disabled-worlds"));

        originWorld = config.getString("difficulty-scaling.origin.world", "world");
        originX = config.getDouble("difficulty-scaling.origin.x", 192.0);
        originY = config.getDouble("difficulty-scaling.origin.y", 73.0);
        originZ = config.getDouble("difficulty-scaling.origin.z", -70.0);
        baseLevel = Math.max(1, config.getInt("difficulty-scaling.base-level", 1));
        blocksPerLevel = Math.max(1.0, config.getDouble("difficulty-scaling.blocks-per-level", 80.0));

        normalLevelOffsetMin = config.getInt("tiers.normal.level-offset-min", 0);
        normalLevelOffsetMax = config.getInt("tiers.normal.level-offset-max", 1);
        normalNamePrefix = ChatColor.translateAlternateColorCodes('&', config.getString("tiers.normal.name-prefix", ""));

        eliteChance = config.getDouble("tiers.elite.chance", 0.20);
        eliteLevelOffsetMin = config.getInt("tiers.elite.level-offset-min", 1);
        eliteLevelOffsetMax = config.getInt("tiers.elite.level-offset-max", 4);
        eliteStatMultiplier = config.getDouble("tiers.elite.stat-multiplier", 1.5);
        eliteNamePrefix = ChatColor.translateAlternateColorCodes('&', config.getString("tiers.elite.name-prefix", "&6⚔ "));

        bossLevelOffsetMin = config.getInt("tiers.boss.level-offset-min", 3);
        bossLevelOffsetMax = config.getInt("tiers.boss.level-offset-max", 8);
        bossStatMultiplier = config.getDouble("tiers.boss.stat-multiplier", 3.0);
        bossNamePrefix = ChatColor.translateAlternateColorCodes('&', config.getString("tiers.boss.name-prefix", "&c✦ BOSS ✦ "));
        bossSpawnParticle = config.getString("tiers.boss.spawn-particle", "FLAME");
        bossParticleCount = config.getInt("tiers.boss.spawn-particle-count", 100);
        bossSpawnSound = config.getString("tiers.boss.spawn-sound", "ENTITY_ENDER_DRAGON_GROWL");
        bossSoundVolume = (float) config.getDouble("tiers.boss.spawn-sound-volume", 2.0);
        bossSoundPitch = (float) config.getDouble("tiers.boss.spawn-sound-pitch", 0.5);

        nightOnly = config.getBoolean("conditions.night-only", false);
        maxLightLevel = config.getInt("conditions.max-light-level", 15);
        requireSolidGround = config.getBoolean("conditions.require-solid-ground", true);
        requireAirSpace = config.getBoolean("conditions.require-air-space", true);
    }

    private void startSpawnTask() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }

        spawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled || !active) {
                    return;
                }

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    processPlayer(player);
                }
            }
        }.runTaskTimer(plugin, 40L, intervalTicks);
    }

    public boolean activateSystem() {
        if (!enabled) {
            return false;
        }

        active = true;
        lastSkipReason = "系統已啟動";
        if (bossZoneManager != null) {
            bossZoneManager.setActive(true);
        }
        return true;
    }

    public void deactivateSystem() {
        active = false;
        if (bossZoneManager != null) {
            bossZoneManager.setActive(false);
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void processPlayer(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            lastSkipReason = "玩家模式不生成";
            return;
        }

        if (disabledWorlds.contains(player.getWorld().getName())) {
            lastSkipReason = "世界禁用: " + player.getWorld().getName();
            return;
        }

        if (safeZoneManager.isInSafeZone(player.getLocation())) {
            lastSkipReason = "玩家位於安全區";
            return;
        }

        if (nightOnly) {
            long time = player.getWorld().getTime();
            if (time < 13000 || time > 23000) {
                lastSkipReason = "白天不生成";
                return;
            }
        }

        ProtectionAreaManager protectionAreaManager = plugin.getProtectionAreaManager();
        if (protectionAreaManager != null && protectionAreaManager.isInProtectedArea(player.getLocation())) {
            lastSkipReason = "玩家位於保護區域";
            return;
        }

        if (bossZoneManager != null) {
            bossZoneManager.trySpawnBossForPlayer(player);
        }

        int nearbyMobCount = countNearbyCustomMobs(player);
        if (nearbyMobCount >= maxMobsPerPlayer) {
            lastSkipReason = "附近自訂怪已達上限";
            return;
        }

        List<String> availableMobKeys = new ArrayList<>(mobManager.getNormalMobKeys());
        if (player.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            availableMobKeys.removeIf(mobManager::willBeRemovedInPeaceful);
        }

        if (availableMobKeys.isEmpty()) {
            lastSkipReason = player.getWorld().getDifficulty() == Difficulty.PEACEFUL
                    ? "和平模式下沒有可生成的敵對自訂怪"
                    : "沒有可用的普通自訂怪";
            return;
        }

        int toSpawn = Math.min(spawnsPerCycle, maxMobsPerPlayer - nearbyMobCount);
        for (int i = 0; i < toSpawn; i++) {
            attemptSpawn(player, availableMobKeys);
        }
    }

    private boolean attemptSpawn(Player player, List<String> availableMobKeys) {
        totalSpawnAttempts++;

        Location spawnLoc = findSpawnLocation(player);
        if (spawnLoc == null) {
            totalLocationFails++;
            lastSkipReason = "找不到有效生成位置";
            return false;
        }

        if (bossZoneManager != null && bossZoneManager.isInsideBossZone(spawnLoc)) {
            lastSkipReason = "Boss 區域排他範圍內";
            return false;
        }

        MobTier tier = determineNaturalTier();
        int level = calculateLevelForLocation(spawnLoc, tier);
        String mobKey = selectMobTypeForLevel(filterBySpawnTime(availableMobKeys, spawnLoc.getWorld()), level);

        if (mobKey == null) {
            lastSkipReason = "沒有符合當前等級的怪物";
            return false;
        }

        boolean success = spawnTieredMob(mobKey, spawnLoc, level, tier);
        if (success) {
            totalSpawnSuccess++;
            lastSkipReason = "成功";
        }
        return success;
    }

    private List<String> filterBySpawnTime(List<String> mobKeys, World world) {
        long worldTime = world.getTime();
        boolean isNight = worldTime >= 13000 && worldTime < 23000;
        List<String> filtered = new ArrayList<>();
        for (String key : mobKeys) {
            MobManager.MobData data = mobManager.getMobData(key);
            if (data == null) {
                continue;
            }
            String spawnTime = data.getSpawnTime();
            if ("all".equalsIgnoreCase(spawnTime)
                    || ("night".equalsIgnoreCase(spawnTime) && isNight)
                    || ("day".equalsIgnoreCase(spawnTime) && !isNight)) {
                filtered.add(key);
            }
        }
        return filtered.isEmpty() ? mobKeys : filtered;
    }

    private MobTier determineNaturalTier() {
        return random.nextDouble() < eliteChance ? MobTier.ELITE : MobTier.NORMAL;
    }

    public int calculateBaseLevel(Location location) {
        double distance = calculateDistanceFromOrigin(location);
        return Math.max(1, baseLevel + (int) Math.floor(distance / blocksPerLevel));
    }

    public int calculateLevelForLocation(Location location, MobTier tier) {
        return Math.max(1, calculateBaseLevel(location) + calculateTierLevelOffset(tier));
    }

    public double calculateDistanceFromOrigin(Location location) {
        if (location == null || location.getWorld() == null) {
            return 0.0;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(originWorld)) {
            return 0.0;
        }
        double dx = location.getX() - originX;
        double dy = location.getY() - originY;
        double dz = location.getZ() - originZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private int calculateTierLevelOffset(MobTier tier) {
        return switch (tier) {
            case ELITE -> ThreadLocalRandom.current().nextInt(eliteLevelOffsetMin, eliteLevelOffsetMax + 1);
            case BOSS -> ThreadLocalRandom.current().nextInt(bossLevelOffsetMin, bossLevelOffsetMax + 1);
            default -> ThreadLocalRandom.current().nextInt(normalLevelOffsetMin, normalLevelOffsetMax + 1);
        };
    }

    private Location findSpawnLocation(Player player) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();

        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = spawnRadiusMin + random.nextDouble() * Math.max(1.0, spawnRadiusMax - spawnRadiusMin);
            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;

            int chunkX = ((int) Math.floor(x)) >> 4;
            int chunkZ = ((int) Math.floor(z)) >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            int highestY = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));
            Location desired = new Location(world, Math.floor(x) + 0.5, highestY + 1, Math.floor(z) + 0.5);
            Location candidate = mobManager.findSafeSpawnLocation(desired);
            if (candidate == null || !isValidSpawnLocation(candidate)) {
                continue;
            }

            if (safeZoneManager.isInSafeZone(candidate)) {
                continue;
            }

            ProtectionAreaManager protectionAreaManager = plugin.getProtectionAreaManager();
            if (protectionAreaManager != null && protectionAreaManager.isInProtectedArea2D(candidate)) {
                continue;
            }

            if (bossZoneManager != null && bossZoneManager.isInsideBossZone(candidate)) {
                continue;
            }

            if (maxLightLevel < 15 && candidate.getBlock().getLightLevel() > maxLightLevel) {
                continue;
            }

            return candidate;
        }

        return null;
    }

    public Location findSpawnNear(Location center, int horizontalRadius) {
        if (center == null || center.getWorld() == null) {
            return null;
        }

        World world = center.getWorld();
        LinkedHashSet<Location> candidates = new LinkedHashSet<>();
        Location safeCenter = mobManager.findSafeSpawnLocation(center.clone());
        if (safeCenter != null) {
            candidates.add(safeCenter);
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * horizontalRadius;
            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;
            int highestY = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));
            Location test = new Location(world, Math.floor(x) + 0.5, highestY + 1, Math.floor(z) + 0.5);
            Location safe = mobManager.findSafeSpawnLocation(test);
            if (safe != null) {
                candidates.add(safe);
            }
        }

        for (Location candidate : candidates) {
            if (!isValidSpawnLocation(candidate)) {
                continue;
            }
            if (safeZoneManager.isInSafeZone(candidate)) {
                continue;
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
        if (requireAirSpace && (!feetBlock.getType().isAir() || !headBlock.getType().isAir())) {
            return false;
        }
        if (groundBlock.isLiquid() || feetBlock.isLiquid()) {
            return false;
        }
        return true;
    }

    private String selectMobTypeForLevel(List<String> mobKeys, int level) {
        if (mobKeys == null || mobKeys.isEmpty()) {
            return null;
        }

        List<String> exactMatches = new ArrayList<>();
        for (String key : mobKeys) {
            MobManager.MobData data = mobManager.getMobData(key);
            if (data != null && level >= data.getMinLevel() && level <= data.getMaxLevel()) {
                exactMatches.add(key);
            }
        }
        if (!exactMatches.isEmpty()) {
            return exactMatches.get(random.nextInt(exactMatches.size()));
        }

        List<String> nearMatches = new ArrayList<>();
        for (String key : mobKeys) {
            MobManager.MobData data = mobManager.getMobData(key);
            if (data != null && level >= data.getMinLevel() - 5 && level <= data.getMaxLevel() + 5) {
                nearMatches.add(key);
            }
        }
        if (!nearMatches.isEmpty()) {
            return nearMatches.get(random.nextInt(nearMatches.size()));
        }

        String closest = null;
        int closestDistance = Integer.MAX_VALUE;
        for (String key : mobKeys) {
            MobManager.MobData data = mobManager.getMobData(key);
            if (data == null) {
                continue;
            }
            int distance = Math.min(Math.abs(level - data.getMinLevel()), Math.abs(level - data.getMaxLevel()));
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = key;
            }
        }
        return closest;
    }

    public boolean spawnTieredMob(String mobKey, Location location, int level, MobTier tier) {
        LivingEntity mob = mobManager.spawnCustomMobWithLevel(mobKey, location, level);
        if (mob == null) {
            return false;
        }

        mobManager.setMobTier(mob, tier.name());
        spawnedMobs.add(mob.getUniqueId());

        switch (tier) {
            case ELITE -> applyEliteModifiers(mob);
            case BOSS -> applyBossModifiers(mob, location, level);
            default -> {
                if (!normalNamePrefix.isEmpty() && mob.getCustomName() != null) {
                    mob.setCustomName(normalNamePrefix + mob.getCustomName());
                }
            }
        }
        return true;
    }

    private void applyEliteModifiers(LivingEntity mob) {
        double newMaxHealth = mob.getMaxHealth() * eliteStatMultiplier;
        mob.setMaxHealth(newMaxHealth);
        mob.setHealth(newMaxHealth);
        if (mob.getCustomName() != null) {
            mob.setCustomName(eliteNamePrefix + ChatColor.translateAlternateColorCodes('&', "&e[精英] ") + mob.getCustomName());
        }
        mob.setGlowing(true);
    }

    public void applyBossModifiers(LivingEntity mob, Location location, int level) {
        double newMaxHealth = mob.getMaxHealth() * bossStatMultiplier;
        mob.setMaxHealth(newMaxHealth);
        mob.setHealth(newMaxHealth);
        if (mob.getCustomName() != null) {
            mob.setCustomName(bossNamePrefix + mob.getCustomName());
        }
        mob.setGlowing(true);

        try {
            Particle particle = Particle.valueOf(bossSpawnParticle);
            location.getWorld().spawnParticle(particle, location.clone().add(0, 1, 0), bossParticleCount, 1.5, 1.5, 1.5, 0.1);
        } catch (Exception ignored) {
            location.getWorld().spawnParticle(Particle.FLAME, location.clone().add(0, 1, 0), bossParticleCount, 1.5, 1.5, 1.5, 0.1);
        }

        Sound sound = resolveConfiguredSound(bossSpawnSound);
        if (sound != null) {
            location.getWorld().playSound(location, sound, bossSoundVolume, bossSoundPitch);
        } else {
            location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        }

        BossBarManager bossBarManager = plugin.getBossBarManager();
        if (bossBarManager != null) {
            bossBarManager.createBossBossBar(mob, level);
        }
    }

    private Sound resolveConfiguredSound(String configuredValue) {
        if (configuredValue == null || configuredValue.isBlank()) {
            return null;
        }

        String trimmed = configuredValue.trim();
        for (Sound sound : Sound.values()) {
            if (sound.name().equalsIgnoreCase(trimmed)) {
                return sound;
            }
        }

        String normalized = trimmed.toLowerCase();
        NamespacedKey key = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);

        return key != null ? Registry.SOUNDS.get(key) : null;
    }

    public String forceSpawn(Player player, String mobKey, MobTier tier) {
        List<String> mobKeys = new ArrayList<>(mobManager.getMobKeys());
        if (mobKeys.isEmpty()) {
            return ChatColor.RED + "沒有已註冊的怪物類型！";
        }

        if (mobKey == null || mobKey.isBlank()) {
            mobKey = mobKeys.get(random.nextInt(mobKeys.size()));
        } else if (mobManager.getMobData(mobKey) == null) {
            return ChatColor.RED + "未知的怪物類型: " + mobKey;
        }

        if (player.getWorld().getDifficulty() == Difficulty.PEACEFUL && mobManager.willBeRemovedInPeaceful(mobKey)) {
            EntityType actualType = mobManager.getActualSpawnEntityType(mobKey);
            return ChatColor.RED + "此怪物在 Peaceful 世界中會被 Minecraft 立刻移除：" + mobKey
                    + ChatColor.GRAY + " (" + (actualType != null ? actualType.name() : "unknown") + ")";
        }

        Location desired = player.getLocation().clone().add(player.getLocation().getDirection().multiply(8));
        Location spawnLocation = mobManager.findSafeSpawnLocation(desired);
        if (spawnLocation == null) {
            return ChatColor.RED + "找不到安全生成位置，請前往空曠地區再試一次。";
        }

        int level = calculateLevelForLocation(spawnLocation, tier);
        boolean success = spawnTieredMob(mobKey, spawnLocation, level, tier);
        if (!success) {
            return ChatColor.RED + "生成失敗: " + mobKey;
        }

        return ChatColor.GREEN + "✓ 成功生成 " + tier.getColor() + "[" + tier.getDisplayName() + "] "
                + ChatColor.WHITE + mobKey + " Lv." + level
                + ChatColor.GRAY + " 距離中心 " + String.format("%.1f", calculateDistanceFromOrigin(spawnLocation));
    }

    private int countNearbyCustomMobs(Player player) {
        int count = 0;
        int searchRadius = Math.max(spawnRadiusMax, 64);
        NamespacedKey key = mobManager.getCustomMobNamespacedKey();
        for (Entity entity : player.getNearbyEntities(searchRadius, searchRadius, searchRadius)) {
            if (entity instanceof LivingEntity mob && mob.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                count++;
            }
        }
        return count;
    }

    public List<String> getDebugStatus(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "========= DistanceSpawnManager 診斷 =========");
        lines.add(ChatColor.YELLOW + "配置啟用: " + (enabled ? ChatColor.GREEN + "是" : ChatColor.RED + "否"));
        lines.add(ChatColor.YELLOW + "RPG 已完成: " + (active ? ChatColor.GREEN + "是 (/finishrpg 已執行)" : ChatColor.RED + "否"));
        lines.add(ChatColor.YELLOW + "重生點中心: " + ChatColor.WHITE + originWorld + " (" + originX + ", " + originY + ", " + originZ + ")");
        lines.add(ChatColor.YELLOW + "等級公式: " + ChatColor.WHITE + "Lv = " + baseLevel + " + distance / " + String.format("%.1f", blocksPerLevel));
        lines.add(ChatColor.YELLOW + "安全區數量: " + ChatColor.WHITE + safeZoneManager.getZoneCount());
        lines.add(ChatColor.YELLOW + "Boss 區域數量: " + ChatColor.WHITE + (bossZoneManager != null ? bossZoneManager.getZoneCount() : 0));
        lines.add(ChatColor.YELLOW + "已註冊怪物: " + ChatColor.WHITE + mobManager.getMobKeys().size());
        lines.add(ChatColor.YELLOW + "最後狀態: " + ChatColor.WHITE + lastSkipReason);

        if (player != null) {
            lines.add("");
            lines.add(ChatColor.AQUA + "--- 你的位置 ---");
            lines.add(ChatColor.YELLOW + "世界: " + ChatColor.WHITE + player.getWorld().getName());
            lines.add(ChatColor.YELLOW + "難度: " + ChatColor.WHITE + player.getWorld().getDifficulty().name());
            lines.add(ChatColor.YELLOW + "距離中心: " + ChatColor.WHITE + String.format("%.2f", calculateDistanceFromOrigin(player.getLocation())));
            lines.add(ChatColor.YELLOW + "當前基礎怪物等級: " + ChatColor.WHITE + "Lv." + calculateLevelForLocation(player.getLocation(), MobTier.NORMAL));

            SafeZoneManager.SafeZone safeZone = safeZoneManager.getZoneAt(player.getLocation());
            if (safeZone != null) {
                lines.add(ChatColor.YELLOW + "安全區: " + ChatColor.GREEN + safeZone.getName() + ChatColor.GRAY + " (" + safeZone.getType() + ")");
            } else {
                lines.add(ChatColor.YELLOW + "安全區: " + ChatColor.RED + "不在安全區內");
            }

            if (bossZoneManager != null) {
                BossZoneManager.BossZone bossZone = bossZoneManager.getZoneAt(player.getLocation());
                if (bossZone != null) {
                    lines.add(ChatColor.YELLOW + "Boss 區域: " + ChatColor.LIGHT_PURPLE + bossZone.getName());
                    lines.add(ChatColor.YELLOW + "Boss: " + ChatColor.WHITE + bossZone.getBossMobKey());
                    lines.add(ChatColor.YELLOW + "綁定小怪: " + ChatColor.WHITE + String.join(", ", bossZone.getLinkedMinions()));
                }
            }
        }

        lines.add("");
        lines.add(ChatColor.AQUA + "--- 統計 ---");
        lines.add(ChatColor.YELLOW + "總嘗試: " + ChatColor.WHITE + totalSpawnAttempts);
        lines.add(ChatColor.YELLOW + "總成功: " + ChatColor.GREEN + totalSpawnSuccess);
        lines.add(ChatColor.YELLOW + "位置失敗: " + ChatColor.RED + totalLocationFails);
        lines.add(ChatColor.YELLOW + "追蹤中怪物: " + ChatColor.WHITE + getSpawnedMobCount());
        lines.add(ChatColor.GOLD + "===========================================");
        return lines;
    }

    public int clearAllSpawnedMobs() {
        int removed = 0;
        for (UUID uuid : new HashSet<>(spawnedMobs)) {
            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
                removed++;
            }
        }
        spawnedMobs.clear();
        return removed;
    }

    public boolean toggleDebug() {
        debug = !debug;
        return debug;
    }

    public void reload() {
        boolean wasActive = active;
        loadConfig();
        active = wasActive && enabled;
        safeZoneManager.reload();
        if (bossZoneManager != null) {
            bossZoneManager.reload();
            bossZoneManager.setActive(active);
        }
        startSpawnTask();
    }

    public void shutdown() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        clearAllSpawnedMobs();
    }

    public int getSpawnedMobCount() {
        spawnedMobs.removeIf(uuid -> {
            Entity entity = plugin.getServer().getEntity(uuid);
            return entity == null || entity.isDead();
        });
        return spawnedMobs.size();
    }
}





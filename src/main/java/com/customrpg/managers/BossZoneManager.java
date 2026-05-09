package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * BossZoneManager - 管理 Boss 區域、綁定小怪、排他生成與 Boss 邊界限制
 */
public class BossZoneManager {

    private final CustomRPG plugin;
    private final MobManager mobManager;
    private final DistanceSpawnManager distanceSpawnManager;
    private final File configFile;
    private final NamespacedKey zoneIdKey;
    private final NamespacedKey zoneRoleKey;
    private final Random random = new Random();

    private final Map<String, BossZone> zones = new LinkedHashMap<>();
    private final Map<String, UUID> activeBosses = new HashMap<>();
    private final Map<String, Set<UUID>> activeMinions = new HashMap<>();
    private final Map<String, Long> bossCooldownUntil = new HashMap<>();
    private final Map<String, Long> lastMinionSpawn = new HashMap<>();

    private BukkitTask bossTask;
    private boolean active;

    public BossZoneManager(CustomRPG plugin, MobManager mobManager, DistanceSpawnManager distanceSpawnManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        this.distanceSpawnManager = distanceSpawnManager;
        this.configFile = new File(plugin.getDataFolder(), "config/boss_zones.yml");
        this.zoneIdKey = new NamespacedKey(plugin, "boss_zone_id");
        this.zoneRoleKey = new NamespacedKey(plugin, "boss_zone_role");
        load();
        startTask();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public void reload() {
        load();
    }

    public void shutdown() {
        if (bossTask != null) {
            bossTask.cancel();
            bossTask = null;
        }
        despawnAllTracked();
    }

    private void load() {
        zones.clear();
        if (!configFile.exists()) {
            plugin.saveResource("config/boss_zones.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = config.getConfigurationSection("boss-zones");
        if (section == null) {
            plugin.getLogger().warning("[BossZoneManager] No boss-zones section found in config/boss_zones.yml");
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection zoneSection = section.getConfigurationSection(id);
            if (zoneSection == null) {
                continue;
            }

            BossZone zone = new BossZone(
                    id,
                    zoneSection.getString("name", id),
                    zoneSection.getString("world", "world"),
                    zoneSection.getDouble("center.x"),
                    zoneSection.getDouble("center.y"),
                    zoneSection.getDouble("center.z"),
                    Math.max(1.0, zoneSection.getDouble("radius", 200.0)),
                    zoneSection.getString("boss-mob-key", ""),
                    zoneSection.getStringList("linked-minions"),
                    Math.max(0.0, zoneSection.getDouble("boss-spawn-chance-per-cycle", 0.35)),
                    Math.max(0, zoneSection.getInt("respawn-cooldown-seconds", 900)),
                    Math.max(0, zoneSection.getInt("max-minions", 4)),
                    Math.max(20L, zoneSection.getLong("minion-spawn-interval-ticks", 200L))
            );
            zones.put(id.toLowerCase(), zone);
        }

        plugin.getLogger().info("[BossZoneManager] Loaded " + zones.size() + " boss zones");
    }

    private void startTask() {
        if (bossTask != null) {
            bossTask.cancel();
        }

        bossTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    return;
                }

                for (BossZone zone : zones.values()) {
                    maintainZone(zone);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    public void trySpawnBossForPlayer(Player player) {
        if (!active || player == null || player.getWorld() == null) {
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }

        BossZone zone = getZoneAt(player.getLocation());
        if (zone == null || zone.getBossMobKey().isBlank()) {
            return;
        }

        if (getBossEntity(zone) != null) {
            return;
        }

        long cooldownUntil = bossCooldownUntil.getOrDefault(zone.getId(), 0L);
        if (System.currentTimeMillis() < cooldownUntil) {
            return;
        }

        if (random.nextDouble() > zone.getBossSpawnChancePerCycle()) {
            return;
        }

        Location zoneCenter = zone.getCenter(player.getWorld());
        Location spawnLocation = mobManager.findSafeSpawnLocationExact(zoneCenter);
        if (spawnLocation == null) {
            spawnLocation = distanceSpawnManager.findSpawnNear(zoneCenter, 8);
        }
        if (spawnLocation == null) {
            spawnLocation = mobManager.findSafeSpawnLocation(zoneCenter);
        }
        if (spawnLocation == null) {
            return;
        }

        int level = distanceSpawnManager.calculateLevelForLocation(spawnLocation, DistanceSpawnManager.MobTier.BOSS);
        LivingEntity boss = mobManager.spawnCustomMobWithLevel(zone.getBossMobKey(), spawnLocation, level);
        if (boss == null) {
            return;
        }

        mobManager.setMobTier(boss, DistanceSpawnManager.MobTier.BOSS.name());
        distanceSpawnManager.applyBossModifiers(boss, spawnLocation, level);
        tagEntity(boss, zone.getId(), "BOSS");
        activeBosses.put(zone.getId(), boss.getUniqueId());
        clearForbiddenCustomMobs(zone);
        announceBoss(zone, boss, level);
    }

    private void maintainZone(BossZone zone) {
        // ── 強制「每個 Zone 最多 1 隻 Boss」限制 ──────────────────────────
        List<LivingEntity> allBosses = scanAllBossEntitiesInZone(zone);
        if (allBosses.size() > 1) {
            // 保留追蹤中的那隻（若存在），其餘全部移除
            UUID trackedUUID = activeBosses.get(zone.getId());
            for (LivingEntity b : allBosses) {
                if (!b.getUniqueId().equals(trackedUUID)) {
                    b.remove();
                    plugin.getLogger().warning("[BossZoneManager] 移除 Zone [" + zone.getId() + "] 多餘的 Boss：" + b.getUniqueId());
                }
            }
        }

        LivingEntity boss = getBossEntity(zone);
        if (boss == null) {
            // 若追蹤 UUID 對應的實體不在了，但世界仍有掃描到的 Boss，接手追蹤第一個
            if (!allBosses.isEmpty()) {
                LivingEntity scanned = allBosses.get(0);
                activeBosses.put(zone.getId(), scanned.getUniqueId());
                boss = scanned;
            } else {
                activeBosses.remove(zone.getId());
                cleanupMinions(zone, false);
                return;
            }
        }

        keepBossInsideZone(zone, boss);
        clearForbiddenCustomMobs(zone);
        maintainMinions(zone, boss);
    }

    private void keepBossInsideZone(BossZone zone, LivingEntity boss) {
        Location center = zone.getCenter(boss.getWorld());
        if (boss.getLocation().distance(center) <= zone.getRadius()) {
            return;
        }

        Vector pull = center.toVector().subtract(boss.getLocation().toVector()).normalize().multiply(Math.max(1.0, zone.getRadius() - 2.0));
        Location target = center.clone().subtract(pull);
        Location safeTarget = distanceSpawnManager.findSpawnNear(target, 6);
        if (safeTarget == null) {
            safeTarget = mobManager.findSafeSpawnLocation(center);
        }
        if (safeTarget != null) {
            boss.teleport(safeTarget);
        }
    }

    private void maintainMinions(BossZone zone, LivingEntity boss) {
        cleanupInvalidMinions(zone);
        if (zone.getLinkedMinions().isEmpty() || zone.getMaxMinions() <= 0) {
            return;
        }

        Set<UUID> tracked = activeMinions.computeIfAbsent(zone.getId(), ignored -> new HashSet<>());
        if (tracked.size() >= zone.getMaxMinions()) {
            return;
        }

        long now = System.currentTimeMillis();
        long intervalMillis = Math.max(1000L, zone.getMinionSpawnIntervalTicks() * 50L);
        long lastSpawn = lastMinionSpawn.getOrDefault(zone.getId(), 0L);
        if (now - lastSpawn < intervalMillis) {
            return;
        }

        String minionKey = zone.getLinkedMinions().get(random.nextInt(zone.getLinkedMinions().size()));
        Location spawnLocation = distanceSpawnManager.findSpawnNear(boss.getLocation(), 8);
        if (spawnLocation == null) {
            return;
        }

        int level = distanceSpawnManager.calculateLevelForLocation(spawnLocation, DistanceSpawnManager.MobTier.NORMAL);
        LivingEntity minion = mobManager.spawnCustomMobWithLevel(minionKey, spawnLocation, level);
        if (minion == null) {
            return;
        }

        tagEntity(minion, zone.getId(), "MINION");
        mobManager.setMobTier(minion, DistanceSpawnManager.MobTier.NORMAL.name());
        tracked.add(minion.getUniqueId());
        lastMinionSpawn.put(zone.getId(), now);
    }

    private void clearForbiddenCustomMobs(BossZone zone) {
        World world = plugin.getServer().getWorld(zone.getWorld());
        if (world == null) {
            return;
        }

        Location center = zone.getCenter(world);
        for (Entity entity : world.getNearbyEntities(center, zone.getRadius(), zone.getRadius(), zone.getRadius())) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            String customMobKey = mobManager.getCustomMobKey(living);
            if (customMobKey == null) {
                continue;
            }
            if (!zone.isAllowedMob(customMobKey)) {
                living.remove();
            }
        }
    }

    public boolean isInsideBossZone(Location location) {
        return getZoneAt(location) != null;
    }

    public BossZone getZoneAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String worldName = location.getWorld().getName();
        for (BossZone zone : zones.values()) {
            if (zone.contains(worldName, location.getX(), location.getY(), location.getZ())) {
                return zone;
            }
        }
        return null;
    }

    public boolean canSpawnCustomMob(Location location, String mobKey) {
        BossZone zone = getZoneAt(location);
        return zone == null || zone.isAllowedMob(mobKey);
    }

    private void tagEntity(LivingEntity entity, String zoneId, String role) {
        entity.getPersistentDataContainer().set(zoneIdKey, PersistentDataType.STRING, zoneId);
        entity.getPersistentDataContainer().set(zoneRoleKey, PersistentDataType.STRING, role);
    }

    public void handleEntityDeath(LivingEntity entity) {
        if (entity == null) {
            return;
        }

        String zoneId = entity.getPersistentDataContainer().get(zoneIdKey, PersistentDataType.STRING);
        String role = entity.getPersistentDataContainer().get(zoneRoleKey, PersistentDataType.STRING);
        if (zoneId == null || role == null) {
            return;
        }

        if ("BOSS".equalsIgnoreCase(role)) {
            activeBosses.remove(zoneId);
            BossZone zone = zones.get(zoneId.toLowerCase());
            if (zone != null) {
                bossCooldownUntil.put(zoneId, System.currentTimeMillis() + (zone.getRespawnCooldownSeconds() * 1000L));
                cleanupMinions(zone, true);
            }
        } else if ("MINION".equalsIgnoreCase(role)) {
            Set<UUID> tracked = activeMinions.get(zoneId);
            if (tracked != null) {
                tracked.remove(entity.getUniqueId());
            }
        }
    }

    private void cleanupMinions(BossZone zone, boolean removeEntities) {
        Set<UUID> tracked = activeMinions.remove(zone.getId());
        if (tracked == null) {
            return;
        }

        if (!removeEntities) {
            return;
        }

        for (UUID uuid : tracked) {
            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
    }

    private void cleanupInvalidMinions(BossZone zone) {
        Set<UUID> tracked = activeMinions.computeIfAbsent(zone.getId(), ignored -> new HashSet<>());
        tracked.removeIf(uuid -> {
            Entity entity = plugin.getServer().getEntity(uuid);
            return entity == null || entity.isDead();
        });
    }

    private LivingEntity getBossEntity(BossZone zone) {
        UUID uuid = activeBosses.get(zone.getId());
        if (uuid == null) {
            return null;
        }
        Entity entity = plugin.getServer().getEntity(uuid);
        if (entity instanceof LivingEntity living && !living.isDead()) {
            return living;
        }
        return null;
    }

    private void announceBoss(BossZone zone, LivingEntity boss, int level) {
        String name = boss.getCustomName() != null ? boss.getCustomName() : zone.getBossMobKey();
        for (Player player : boss.getWorld().getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distance(zone.getCenter(player.getWorld())) <= zone.getRadius() * 2.0) {
                player.sendMessage(ChatColor.DARK_RED + "[Boss Zone] " + ChatColor.RED + name + ChatColor.GRAY + " Lv." + level + ChatColor.YELLOW + " 已出現在 " + zone.getName());
            }
        }
    }

    private void despawnAllTracked() {
        for (UUID uuid : activeBosses.values()) {
            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        for (Set<UUID> uuids : activeMinions.values()) {
            for (UUID uuid : uuids) {
                Entity entity = plugin.getServer().getEntity(uuid);
                if (entity != null && !entity.isDead()) {
                    entity.remove();
                }
            }
        }
        activeBosses.clear();
        activeMinions.clear();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 新增：Boss 上限控制 & 強制生成 API
    // ──────────────────────────────────────────────────────────────────────────

    /** 掃描世界中所有在指定 Zone 內、被標記為 BOSS 的實體（含未追蹤的殘留）*/
    private List<LivingEntity> scanAllBossEntitiesInZone(BossZone zone) {
        World world = plugin.getServer().getWorld(zone.getWorld());
        if (world == null) return new ArrayList<>();
        Location center = zone.getCenter(world);
        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, zone.getRadius(), zone.getRadius(), zone.getRadius())) {
            if (!(entity instanceof LivingEntity living) || living.isDead()) continue;
            String role = living.getPersistentDataContainer().get(zoneRoleKey, PersistentDataType.STRING);
            String zid  = living.getPersistentDataContainer().get(zoneIdKey,  PersistentDataType.STRING);
            if ("BOSS".equalsIgnoreCase(role) && zone.getId().equalsIgnoreCase(zid)) {
                found.add(living);
            }
        }
        return found;
    }

    /** 移除指定 Zone 內所有 Boss（追蹤中 + 掃描到的殘留都清除）*/
    public void removeAllBossesInZone(BossZone zone) {
        UUID trackedUUID = activeBosses.remove(zone.getId());
        if (trackedUUID != null) {
            Entity tracked = plugin.getServer().getEntity(trackedUUID);
            if (tracked != null && !tracked.isDead()) tracked.remove();
        }
        for (LivingEntity boss : scanAllBossEntitiesInZone(zone)) {
            boss.remove();
        }
    }

    /**
     * 強制在指定 Zone 生成 Boss（先移除所有現有 Boss，再生成新的，並跳過冷卻）
     * @return 成功生成的 Boss 實體，若失敗則為 null
     */
    public LivingEntity forceSpawnBoss(String zoneId) {
        BossZone zone = zones.get(zoneId.toLowerCase());
        if (zone == null || zone.getBossMobKey().isBlank()) return null;

        removeAllBossesInZone(zone);
        bossCooldownUntil.remove(zone.getId());

        World world = plugin.getServer().getWorld(zone.getWorld());
        if (world == null) return null;
        Location center = zone.getCenter(world);
        Location spawnLoc = mobManager.findSafeSpawnLocationExact(center);
        if (spawnLoc == null) {
            plugin.getLogger().warning("[BossZoneManager] Force spawn failed for zone '" + zone.getId()
                    + "' because configured center " + center + " is not a safe spawn location.");
            return null;
        }

        int level = distanceSpawnManager.calculateLevelForLocation(spawnLoc, DistanceSpawnManager.MobTier.BOSS);
        LivingEntity boss = mobManager.spawnCustomMobWithLevelExact(zone.getBossMobKey(), spawnLoc, level);
        if (boss == null) return null;

        mobManager.setMobTier(boss, DistanceSpawnManager.MobTier.BOSS.name());
        distanceSpawnManager.applyBossModifiers(boss, spawnLoc, level);
        tagEntity(boss, zone.getId(), "BOSS");
        activeBosses.put(zone.getId(), boss.getUniqueId());
        clearForbiddenCustomMobs(zone);
        announceBoss(zone, boss, level);
        return boss;
    }

    /** 取得指定 Zone 目前存活的 Boss（含世界掃描驗證）*/
    public LivingEntity getActiveBoss(String zoneId) {
        BossZone zone = zones.get(zoneId.toLowerCase());
        if (zone == null) return null;
        // 優先返回追蹤中的
        LivingEntity tracked = getBossEntity(zone);
        if (tracked != null) return tracked;
        // Fallback：掃描世界
        List<LivingEntity> scanned = scanAllBossEntitiesInZone(zone);
        if (!scanned.isEmpty()) {
            activeBosses.put(zone.getId(), scanned.get(0).getUniqueId());
            return scanned.get(0);
        }
        return null;
    }

    /** 回傳所有已知 Zone 的 ID 列表（供 Tab 補全使用）*/
    public List<String> getZoneIds() {
        return new ArrayList<>(zones.keySet());
    }

    // ──────────────────────────────────────────────────────────────────────────

    public int getZoneCount() {
        return zones.size();
    }

    public Collection<BossZone> getZones() {
        return new ArrayList<>(zones.values());
    }

    public static class BossZone {
        private final String id;
        private final String name;
        private final String world;
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final double radius;
        private final double radiusSquared;
        private final String bossMobKey;
        private final List<String> linkedMinions;
        private final double bossSpawnChancePerCycle;
        private final int respawnCooldownSeconds;
        private final int maxMinions;
        private final long minionSpawnIntervalTicks;

        public BossZone(String id, String name, String world,
                        double centerX, double centerY, double centerZ,
                        double radius, String bossMobKey, List<String> linkedMinions,
                        double bossSpawnChancePerCycle, int respawnCooldownSeconds,
                        int maxMinions, long minionSpawnIntervalTicks) {
            this.id = id;
            this.name = name;
            this.world = world;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.radius = radius;
            this.radiusSquared = radius * radius;
            this.bossMobKey = bossMobKey;
            this.linkedMinions = linkedMinions != null ? linkedMinions : new ArrayList<>();
            this.bossSpawnChancePerCycle = bossSpawnChancePerCycle;
            this.respawnCooldownSeconds = respawnCooldownSeconds;
            this.maxMinions = maxMinions;
            this.minionSpawnIntervalTicks = minionSpawnIntervalTicks;
        }

        public boolean contains(String worldName, double x, double y, double z) {
            if (!world.equalsIgnoreCase(worldName)) {
                return false;
            }
            double dx = x - centerX;
            double dy = y - centerY;
            double dz = z - centerZ;
            return (dx * dx) + (dy * dy) + (dz * dz) <= radiusSquared;
        }

        public boolean isAllowedMob(String mobKey) {
            return bossMobKey.equalsIgnoreCase(mobKey) || linkedMinions.stream().anyMatch(key -> key.equalsIgnoreCase(mobKey));
        }

        public Location getCenter(World world) {
            return new Location(world, centerX, centerY, centerZ);
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getWorld() {
            return world;
        }

        public double getRadius() {
            return radius;
        }

        public String getBossMobKey() {
            return bossMobKey;
        }

        public List<String> getLinkedMinions() {
            return linkedMinions;
        }

        public double getBossSpawnChancePerCycle() {
            return bossSpawnChancePerCycle;
        }

        public int getRespawnCooldownSeconds() {
            return respawnCooldownSeconds;
        }

        public int getMaxMinions() {
            return maxMinions;
        }

        public long getMinionSpawnIntervalTicks() {
            return minionSpawnIntervalTicks;
        }
    }
}


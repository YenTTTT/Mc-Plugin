package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * ZoneManager — 區域式怪物生成管理器
 *
 * 管理「安全區域 (MobZone)」：
 * - 區域內不生成怪物
 * - 離開區域後，依據距離計算怪物等級
 * - 每 radiusStep 格距離提升一個 tier
 */
public class ZoneManager {

    private final CustomRPG plugin;
    private final File dataFile;
    private final Logger log;

    // 所有 mob zones (key = zoneId lowercase)
    private final Map<String, MobZone> zones = new LinkedHashMap<>();

    // 玩家選區狀態 (playerUUID -> selection)
    private final Map<UUID, Selection> selections = new HashMap<>();

    // 玩家上次所在 tier (用來偵測 tier 切換)
    private final Map<UUID, Integer> playerLastTier = new HashMap<>();

    public ZoneManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.dataFile = new File(plugin.getDataFolder(), "data/mob_zones.yml");
        load();
    }

    // ==========================================
    // ===== 選區操作 =====
    // ==========================================

    public void setPos1(UUID playerUUID, Location loc) {
        selections.computeIfAbsent(playerUUID, k -> new Selection()).pos1 = loc;
    }

    public void setPos2(UUID playerUUID, Location loc) {
        selections.computeIfAbsent(playerUUID, k -> new Selection()).pos2 = loc;
    }

    public Selection getSelection(UUID playerUUID) {
        return selections.get(playerUUID);
    }

    public void clearSelection(UUID playerUUID) {
        selections.remove(playerUUID);
    }

    // ==========================================
    // ===== Zone CRUD =====
    // ==========================================

    /**
     * 從玩家的選區建立怪物區域
     *
     * @return true 如果成功
     */
    public boolean createZone(UUID playerUUID, String zoneId, int minLevel, int maxLevel) {
        Selection sel = selections.get(playerUUID);
        if (sel == null || sel.pos1 == null || sel.pos2 == null) {
            return false;
        }
        if (!sel.pos1.getWorld().equals(sel.pos2.getWorld())) {
            return false;
        }

        String worldName = sel.pos1.getWorld().getName();
        int minX = Math.min(sel.pos1.getBlockX(), sel.pos2.getBlockX());
        int minY = Math.min(sel.pos1.getBlockY(), sel.pos2.getBlockY());
        int minZ = Math.min(sel.pos1.getBlockZ(), sel.pos2.getBlockZ());
        int maxX = Math.max(sel.pos1.getBlockX(), sel.pos2.getBlockX());
        int maxY = Math.max(sel.pos1.getBlockY(), sel.pos2.getBlockY());
        int maxZ = Math.max(sel.pos1.getBlockZ(), sel.pos2.getBlockZ());

        MobZone zone = new MobZone(zoneId, worldName, minX, minY, minZ, maxX, maxY, maxZ, minLevel, maxLevel, 150);
        zones.put(zoneId.toLowerCase(), zone);
        save();
        clearSelection(playerUUID);
        return true;
    }

    public boolean removeZone(String zoneId) {
        if (zones.remove(zoneId.toLowerCase()) != null) {
            save();
            return true;
        }
        return false;
    }

    public Collection<MobZone> getAllZones() {
        return zones.values();
    }

    public MobZone getZone(String zoneId) {
        return zones.get(zoneId.toLowerCase());
    }

    public int getZoneCount() {
        return zones.size();
    }

    // ==========================================
    // ===== 核心：位置判定與等級計算 =====
    // ==========================================

    /**
     * 檢查某個位置是否在任何 mob zone 內 (2D，忽略 Y 軸)
     */
    public boolean isInsideAnyZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();

        for (MobZone zone : zones.values()) {
            if (zone.contains2D(worldName, x, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 檢查某個位置是否在任何 mob zone 內 (3D，含 Y 軸容差)
     */
    public boolean isInsideAnyZone3D(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        for (MobZone zone : zones.values()) {
            if (zone.worldName.equals(worldName)
                    && x >= zone.minX && x <= zone.maxX
                    && z >= zone.minZ && z <= zone.maxZ
                    && y >= (zone.minY - 10) && y <= (zone.maxY + 50)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取得距離最近的 zone (2D 邊界距離)
     *
     * @return nearest zone, 或 null (如果沒有任何 zone 在同一世界)
     */
    public MobZone getNearestZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        String worldName = loc.getWorld().getName();
        double px = loc.getX();
        double pz = loc.getZ();

        MobZone nearest = null;
        double minDist = Double.MAX_VALUE;

        for (MobZone zone : zones.values()) {
            if (!zone.worldName.equals(worldName)) continue;
            double dist = zone.getDistance2D(px, pz);
            if (dist < minDist) {
                minDist = dist;
                nearest = zone;
            }
        }
        return nearest;
    }

    /**
     * 計算某位置到最近 zone 邊界的 2D 距離
     *
     * @return 距離 (如果在 zone 內則回傳 0；沒有 zone 則回傳 -1)
     */
    public double getDistanceToNearestZoneBorder(Location loc) {
        MobZone nearest = getNearestZone(loc);
        if (nearest == null) return -1;
        return nearest.getDistance2D(loc.getX(), loc.getZ());
    }

    /**
     * 依據距離計算當前 tier (每 radiusStep 格升一級)
     *
     * @return tier 等級 (0 = zone 內; 1 = 第一圈; 2 = 第二圈 ...)
     */
    public int calculateTier(Location loc) {
        MobZone nearest = getNearestZone(loc);
        if (nearest == null) return 1; // 沒有zone 預設 tier 1
        double distance = nearest.getDistance2D(loc.getX(), loc.getZ());
        if (distance <= 0) return 0; // 在 zone 內
        return Math.max(1, (int) Math.ceil(distance / nearest.radiusStep));
    }

    /**
     * 根據玩家位置計算怪物等級範圍
     *
     * @return int[2] = {levelMin, levelMax}
     */
    public int[] calculateMobLevelRange(Location loc) {
        MobZone nearest = getNearestZone(loc);
        if (nearest == null) {
            // 沒有設定任何 zone，給一個預設等級
            return new int[]{1, 5};
        }

        double distance = nearest.getDistance2D(loc.getX(), loc.getZ());
        if (distance <= 0) {
            // 在 zone 內，不該生成，但以防萬一
            return new int[]{nearest.minLevel, nearest.maxLevel};
        }

        int tier = Math.max(1, (int) Math.ceil(distance / nearest.radiusStep));
        int levelRange = nearest.maxLevel - nearest.minLevel;

        // 每升一個 tier，等級範圍往上疊加
        int levelMin = nearest.minLevel + ((tier - 1) * levelRange);
        int levelMax = nearest.maxLevel + ((tier - 1) * levelRange);

        return new int[]{Math.max(1, levelMin), Math.max(1, levelMax)};
    }

    /**
     * 從等級範圍中隨機取一個等級
     */
    public int calculateMobLevel(Location loc) {
        int[] range = calculateMobLevelRange(loc);
        if (range[0] == range[1]) return range[0];
        return range[0] + new Random().nextInt(range[1] - range[0] + 1);
    }

    /**
     * 檢查玩家是否進入了新的 tier (用於顯示提示)
     *
     * @return 新 tier 值，若無變化則回傳 -1
     */
    public int checkTierChange(Player player) {
        int currentTier = calculateTier(player.getLocation());
        Integer lastTier = playerLastTier.get(player.getUniqueId());

        if (lastTier == null || lastTier != currentTier) {
            playerLastTier.put(player.getUniqueId(), currentTier);
            return currentTier;
        }
        return -1; // 無變化
    }

    /**
     * 取得 tier 的顯示名稱和顏色
     */
    public String getTierDisplayName(int tier) {
        if (tier <= 0) return ChatColor.GREEN + "安全區域";
        if (tier == 1) return ChatColor.WHITE + "低危區域 (Tier 1)";
        if (tier == 2) return ChatColor.YELLOW + "中危區域 (Tier 2)";
        if (tier == 3) return ChatColor.GOLD + "高危區域 (Tier 3)";
        if (tier == 4) return ChatColor.RED + "極危區域 (Tier 4)";
        return ChatColor.DARK_RED + "煉獄區域 (Tier " + tier + ")";
    }

    /**
     * 取得 tier 的顏色
     */
    public ChatColor getTierColor(int tier) {
        if (tier <= 0) return ChatColor.GREEN;
        if (tier == 1) return ChatColor.WHITE;
        if (tier == 2) return ChatColor.YELLOW;
        if (tier == 3) return ChatColor.GOLD;
        if (tier == 4) return ChatColor.RED;
        return ChatColor.DARK_RED;
    }

    // ==========================================
    // ===== 持久化 =====
    // ==========================================

    private void load() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            log.info("[ZoneManager] 資料檔不存在，將建立新檔案");
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = config.getConfigurationSection("zones");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            ConfigurationSection zoneSec = sec.getConfigurationSection(key);
            if (zoneSec == null) continue;
            String name = zoneSec.getString("name", key);
            String world = zoneSec.getString("world", "world");
            int minX = zoneSec.getInt("minX");
            int minY = zoneSec.getInt("minY");
            int minZ = zoneSec.getInt("minZ");
            int maxX = zoneSec.getInt("maxX");
            int maxY = zoneSec.getInt("maxY");
            int maxZ = zoneSec.getInt("maxZ");
            int minLevel = zoneSec.getInt("min-level", 1);
            int maxLevel = zoneSec.getInt("max-level", 5);
            int radiusStep = zoneSec.getInt("radius-step", 150);

            zones.put(key, new MobZone(name, world, minX, minY, minZ, maxX, maxY, maxZ, minLevel, maxLevel, radiusStep));
        }
        log.info("[ZoneManager] 已載入 " + zones.size() + " 個怪物區域");
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, MobZone> entry : zones.entrySet()) {
            String key = entry.getKey();
            MobZone z = entry.getValue();
            String path = "zones." + key;
            config.set(path + ".name", z.name);
            config.set(path + ".world", z.worldName);
            config.set(path + ".minX", z.minX);
            config.set(path + ".minY", z.minY);
            config.set(path + ".minZ", z.minZ);
            config.set(path + ".maxX", z.maxX);
            config.set(path + ".maxY", z.maxY);
            config.set(path + ".maxZ", z.maxZ);
            config.set(path + ".min-level", z.minLevel);
            config.set(path + ".max-level", z.maxLevel);
            config.set(path + ".radius-step", z.radiusStep);
        }
        try {
            dataFile.getParentFile().mkdirs();
            config.save(dataFile);
        } catch (IOException e) {
            log.severe("[ZoneManager] 無法儲存怪物區域: " + e.getMessage());
        }
    }

    public void reload() {
        zones.clear();
        playerLastTier.clear();
        load();
    }

    // ==========================================
    // ===== 內部類別 =====
    // ==========================================

    /**
     * 選區狀態
     */
    public static class Selection {
        public Location pos1;
        public Location pos2;

        public boolean isComplete() {
            return pos1 != null && pos2 != null;
        }
    }

    /**
     * 怪物區域 — 安全區域 + 等級計算基準
     */
    public static class MobZone {
        public final String name;
        public final String worldName;
        public final int minX, minY, minZ;
        public final int maxX, maxY, maxZ;
        public final int minLevel;    // 基礎最小等級
        public final int maxLevel;    // 基礎最大等級
        public final int radiusStep;  // 每幾格升一個 tier

        public MobZone(String name, String worldName,
                       int minX, int minY, int minZ,
                       int maxX, int maxY, int maxZ,
                       int minLevel, int maxLevel, int radiusStep) {
            this.name = name;
            this.worldName = worldName;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.radiusStep = radiusStep;
        }

        /**
         * 2D 檢查 (忽略 Y 軸)
         */
        public boolean contains2D(String world, int x, int z) {
            return worldName.equals(world)
                    && x >= minX && x <= maxX
                    && z >= minZ && z <= maxZ;
        }

        /**
         * 3D 檢查
         */
        public boolean contains(String world, int x, int y, int z) {
            return worldName.equals(world)
                    && x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        /**
         * 取得 zone 的 2D 中心點
         */
        public double getCenterX() {
            return (minX + maxX) / 2.0;
        }

        public double getCenterZ() {
            return (minZ + maxZ) / 2.0;
        }

        /**
         * 計算某個 2D 點到 zone 邊界的最短距離
         * 如果在 zone 內，回傳 0 (或負數)
         */
        public double getDistance2D(double px, double pz) {
            // 計算到矩形邊界的最短距離
            // 如果在矩形內部，距離 <= 0
            double dx = Math.max(minX - px, Math.max(0, px - maxX));
            double dz = Math.max(minZ - pz, Math.max(0, pz - maxZ));

            if (dx == 0 && dz == 0) {
                // 在矩形內部
                return 0;
            }

            return Math.sqrt(dx * dx + dz * dz);
        }

        /**
         * 計算 zone 的面積 (方塊數)
         */
        public int getArea() {
            return (maxX - minX + 1) * (maxZ - minZ + 1);
        }
    }
}



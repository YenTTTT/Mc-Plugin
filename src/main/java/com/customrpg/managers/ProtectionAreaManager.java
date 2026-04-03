package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 保護區域管理器 — 管理禁止怪物生成的區域
 */
public class ProtectionAreaManager {

    private final CustomRPG plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    // 所有保護區域
    private final Map<String, ProtectedArea> areas = new LinkedHashMap<>();

    // 玩家選區狀態 (playerUUID -> selection)
    private final Map<UUID, Selection> selections = new HashMap<>();

    public ProtectionAreaManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/protected_areas.yml");
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
    // ===== 區域 CRUD =====
    // ==========================================

    /**
     * 從玩家的選區建立保護區域
     * @return true 如果成功
     */
    public boolean createArea(UUID playerUUID, String name) {
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

        ProtectedArea area = new ProtectedArea(name, worldName, minX, minY, minZ, maxX, maxY, maxZ);
        areas.put(name.toLowerCase(), area);
        save();
        clearSelection(playerUUID);
        return true;
    }

    public boolean removeArea(String name) {
        if (areas.remove(name.toLowerCase()) != null) {
            save();
            return true;
        }
        return false;
    }

    public Collection<ProtectedArea> getAllAreas() {
        return areas.values();
    }

    public ProtectedArea getArea(String name) {
        return areas.get(name.toLowerCase());
    }

    // ==========================================
    // ===== 核心：位置是否在保護區內 =====
    // ==========================================

    /**
     * 檢查某個位置是否在任何保護區域內 (3D 含 Y 軸，但 Y 軸擴大 10 格容差)
     */
    public boolean isInProtectedArea(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        for (ProtectedArea area : areas.values()) {
            // Y 軸擴大容差：選區地板往下10格、往上50格都算在保護區內
            if (area.worldName.equals(worldName)
                    && x >= area.minX && x <= area.maxX
                    && z >= area.minZ && z <= area.maxZ
                    && y >= (area.minY - 10) && y <= (area.maxY + 50)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 2D 檢查（完全忽略 Y 軸）— 用於自訂怪物生成判定
     */
    public boolean isInProtectedArea2D(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();

        for (ProtectedArea area : areas.values()) {
            if (area.contains2D(worldName, x, z)) {
                return true;
            }
        }
        return false;
    }

    // ==========================================
    // ===== 持久化 =====
    // ==========================================

    private void load() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = dataConfig.getConfigurationSection("areas");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            ConfigurationSection areaSec = sec.getConfigurationSection(key);
            if (areaSec == null) continue;
            String name = areaSec.getString("name", key);
            String world = areaSec.getString("world", "world");
            int minX = areaSec.getInt("minX");
            int minY = areaSec.getInt("minY");
            int minZ = areaSec.getInt("minZ");
            int maxX = areaSec.getInt("maxX");
            int maxY = areaSec.getInt("maxY");
            int maxZ = areaSec.getInt("maxZ");
            areas.put(key, new ProtectedArea(name, world, minX, minY, minZ, maxX, maxY, maxZ));
        }
        plugin.getLogger().info("[ProtectionArea] 已載入 " + areas.size() + " 個保護區域");
    }

    private void save() {
        dataConfig = new YamlConfiguration();
        for (Map.Entry<String, ProtectedArea> entry : areas.entrySet()) {
            String key = entry.getKey();
            ProtectedArea a = entry.getValue();
            dataConfig.set("areas." + key + ".name", a.name);
            dataConfig.set("areas." + key + ".world", a.worldName);
            dataConfig.set("areas." + key + ".minX", a.minX);
            dataConfig.set("areas." + key + ".minY", a.minY);
            dataConfig.set("areas." + key + ".minZ", a.minZ);
            dataConfig.set("areas." + key + ".maxX", a.maxX);
            dataConfig.set("areas." + key + ".maxY", a.maxY);
            dataConfig.set("areas." + key + ".maxZ", a.maxZ);
        }
        try {
            dataFile.getParentFile().mkdirs();
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[ProtectionArea] 無法儲存保護區域: " + e.getMessage());
        }
    }

    public void reload() {
        areas.clear();
        load();
    }

    // ==========================================
    // ===== 內部類別 =====
    // ==========================================

    public static class Selection {
        public Location pos1;
        public Location pos2;

        public boolean isComplete() {
            return pos1 != null && pos2 != null;
        }
    }

    public static class ProtectedArea {
        public final String name;
        public final String worldName;
        public final int minX, minY, minZ;
        public final int maxX, maxY, maxZ;

        public ProtectedArea(String name, String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.name = name;
            this.worldName = worldName;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public boolean contains(String world, int x, int y, int z) {
            return worldName.equals(world)
                    && x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        /**
         * 2D 檢查 (忽略 Y 軸) — 用於怪物生成判定
         */
        public boolean contains2D(String world, int x, int z) {
            return worldName.equals(world)
                    && x >= minX && x <= maxX
                    && z >= minZ && z <= maxZ;
        }
    }
}



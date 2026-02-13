package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerStatsManager - 管理玩家數據的讀取、儲存與應用
 *
 * 功能：
 * - 從 YAML 載入玩家數據
 * - 儲存玩家數據至 YAML
 * - 應用 Vitality 到玩家最大血量
 * - 快取玩家數據於記憶體
 */
public class PlayerStatsManager {

    private final CustomRPG plugin;
    private final File playerDataFolder;
    private final Map<UUID, PlayerStats> statsCache;

    // 配置：每點 Vitality 增加多少血量 (預設 2.0)
    private static final double HP_PER_VITALITY = 2.0;

    // 基礎最大血量 (預設 20.0)
    private static final double BASE_MAX_HEALTH = 20.0;

    public PlayerStatsManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "data/players");
        this.statsCache = new ConcurrentHashMap<>();

        // 確保資料夾存在
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
    }

    /**
     * 取得玩家數據 (從快取或載入)
     */
    public PlayerStats getStats(Player player) {
        return getStats(player.getUniqueId());
    }

    /**
     * 取得玩家數據 (透過 UUID)
     */
    public PlayerStats getStats(UUID uuid) {
        return statsCache.computeIfAbsent(uuid, this::loadStats);
    }

    /**
     * 從檔案載入玩家數據
     */
    private PlayerStats loadStats(UUID uuid) {
        File file = new File(playerDataFolder, uuid.toString() + ".yml");

        if (!file.exists()) {
            // 如果檔案不存在，回傳預設數據
            return new PlayerStats();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // 讀取數據
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("strength", config.getInt("stats.strength", 0));
        data.put("magic", config.getInt("stats.magic", 0));
        data.put("agility", config.getInt("stats.agility", 0));
        data.put("vitality", config.getInt("stats.vitality", 0));
        data.put("defense", config.getInt("stats.defense", 0));

        return PlayerStats.deserialize(data);
    }

    /**
     * 儲存玩家數據到檔案
     */
    public void saveStats(Player player) {
        saveStats(player.getUniqueId());
    }

    /**
     * 儲存玩家數據到檔案 (透過 UUID)
     */
    public void saveStats(UUID uuid) {
        PlayerStats stats = statsCache.get(uuid);
        if (stats == null) {
            return;
        }

        File file = new File(playerDataFolder, uuid.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        Map<String, Object> data = stats.serialize();
        config.set("stats.strength", data.get("strength"));
        config.set("stats.magic", data.get("magic"));
        config.set("stats.agility", data.get("agility"));
        config.set("stats.vitality", data.get("vitality"));
        config.set("stats.defense", data.get("defense"));

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("無法儲存玩家數據: " + uuid);
            e.printStackTrace();
        }
    }

    /**
     * 更新玩家的最大血量 (根據 Vitality)
     */
    public void updateMaxHealth(Player player) {
        PlayerStats stats = getStats(player);
        double newMaxHealth = BASE_MAX_HEALTH + (stats.getVitality() * HP_PER_VITALITY);

        // 使用 Attribute API 設定最大血量
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(Math.max(1.0, newMaxHealth));

            // 治療玩家至滿血 (可選)
            player.setHealth(maxHealthAttr.getValue());
        }
    }

    /**
     * 設定玩家的某項數據
     */
    public void setStat(UUID uuid, String statName, int value) {
        PlayerStats stats = getStats(uuid);

        switch (statName.toLowerCase()) {
            case "strength", "str" -> stats.setStrength(value);
            case "magic", "mag" -> stats.setMagic(value);
            case "agility", "agi" -> stats.setAgility(value);
            case "vitality", "vit" -> stats.setVitality(value);
            case "defense", "def" -> stats.setDefense(value);
            default -> {
                plugin.getLogger().warning("未知的數據名稱: " + statName);
                return;
            }
        }

        // 立即儲存
        saveStats(uuid);
    }

    /**
     * 清除玩家快取 (玩家登出時)
     */
    public void unloadStats(UUID uuid) {
        statsCache.remove(uuid);
    }

    /**
     * 儲存所有玩家數據
     */
    public void saveAllStats() {
        for (UUID uuid : statsCache.keySet()) {
            saveStats(uuid);
        }
    }
}


package com.customrpg.races;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RaceManager - 種族管理系統
 *
 * 功能：
 * - 從 YAML 載入所有種族定義
 * - 管理玩家的種族選擇
 * - 計算並套用種族屬性加成
 * - 提供武器限制查詢
 * - 玩家數據持久化
 */
public class RaceManager {

    private final CustomRPG plugin;
    private final Map<String, RaceData> raceRegistry = new LinkedHashMap<>();
    private final Map<UUID, String> playerRaces = new ConcurrentHashMap<>();
    private final File raceDataFolder;

    public RaceManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.raceDataFolder = new File(plugin.getDataFolder(), "data/races");
        if (!raceDataFolder.exists()) {
            raceDataFolder.mkdirs();
        }

        loadRaceConfig();
        plugin.getLogger().info("[RaceManager] 已載入 " + raceRegistry.size() + " 個種族");
    }

    // ===== 載入種族配置 =====

    private void loadRaceConfig() {
        // 確保默認配置檔案存在
        File configFile = new File(plugin.getDataFolder(), "config/races.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                plugin.saveResource("config/races.yml", false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[RaceManager] 未找到內建 races.yml，將建立空配置");
                try {
                    configFile.createNewFile();
                } catch (IOException ex) {
                    plugin.getLogger().severe("[RaceManager] 無法建立 races.yml: " + ex.getMessage());
                }
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection racesSection = config.getConfigurationSection("races");
        if (racesSection == null) {
            plugin.getLogger().warning("[RaceManager] races.yml 中沒有 'races' 段落");
            return;
        }

        for (String raceId : racesSection.getKeys(false)) {
            ConfigurationSection section = racesSection.getConfigurationSection(raceId);
            if (section == null) continue;

            try {
                RaceData data = parseRaceData(raceId, section);
                raceRegistry.put(raceId.toLowerCase(), data);
                plugin.getLogger().info("[RaceManager] 載入種族: " + raceId + " (" + data.getDisplayName() + ")");
            } catch (Exception e) {
                plugin.getLogger().warning("[RaceManager] 載入種族 '" + raceId + "' 失敗: " + e.getMessage());
            }
        }
    }

    private RaceData parseRaceData(String id, ConfigurationSection section) {
        RaceData.Builder builder = new RaceData.Builder(id);

        // 名稱與描述
        builder.displayName(section.getString("name", "§f" + id));
        builder.description(section.getString("description", ""));

        // 基礎屬性
        ConfigurationSection baseStats = section.getConfigurationSection("base-stats");
        if (baseStats != null) {
            builder.baseStrength(baseStats.getInt("strength", 5));
            builder.baseMagic(baseStats.getInt("magic", 5));
            builder.baseAgility(baseStats.getInt("agility", 5));
            builder.baseVitality(baseStats.getInt("vitality", 5));
            builder.baseDefense(baseStats.getInt("defense", 5));
            builder.baseSpirit(baseStats.getInt("spirit", 5));
        }

        // 成長倍率
        ConfigurationSection growth = section.getConfigurationSection("growth");
        if (growth != null) {
            builder.growthStrength(growth.getDouble("strength", 1.0));
            builder.growthMagic(growth.getDouble("magic", 1.0));
            builder.growthAgility(growth.getDouble("agility", 1.0));
            builder.growthVitality(growth.getDouble("vitality", 1.0));
            builder.growthDefense(growth.getDouble("defense", 1.0));
            builder.growthSpirit(growth.getDouble("spirit", 1.0));
        }

        // 技能
        List<String> skills = section.getStringList("skills");
        if (skills.size() > 2) {
            skills = skills.subList(0, 2); // 最多2個技能
        }
        builder.skills(skills);

        // 武器類型加成
        ConfigurationSection weaponSection = section.getConfigurationSection("weapon");
        if (weaponSection != null) {
            builder.defaultWeaponBonus(weaponSection.getDouble("default", 1.0));

            ConfigurationSection bonuses = weaponSection.getConfigurationSection("bonuses");
            if (bonuses != null) {
                java.util.Map<String, Double> weaponBonuses = new java.util.LinkedHashMap<>();
                for (String weaponType : bonuses.getKeys(false)) {
                    weaponBonuses.put(weaponType.toUpperCase(), bonuses.getDouble(weaponType, 1.0));
                }
                builder.weaponBonuses(weaponBonuses);
            }
        }

        // 被動效果
        ConfigurationSection passives = section.getConfigurationSection("passives");
        if (passives != null) {
            builder.bonusCritChance(passives.getDouble("crit-chance", 0.0));
            builder.bonusCritDamage(passives.getDouble("crit-damage", 0.0));
            builder.bonusMoveSpeed(passives.getDouble("move-speed", 0.0));
            builder.bonusHealthRegen(passives.getDouble("health-regen", 0.0));
        }

        return builder.build();
    }

    // ===== 種族查詢 =====

    /**
     * 取得所有已註冊的種族
     */
    public Collection<RaceData> getAllRaces() {
        return raceRegistry.values();
    }

    /**
     * 取得所有種族 ID
     */
    public Set<String> getAllRaceIds() {
        return raceRegistry.keySet();
    }

    /**
     * 取得指定種族資料
     */
    public RaceData getRaceData(String raceId) {
        if (raceId == null) return null;
        return raceRegistry.get(raceId.toLowerCase());
    }

    /**
     * 取得種族數量
     */
    public int getRaceCount() {
        return raceRegistry.size();
    }

    // ===== 玩家種族管理 =====

    /**
     * 取得玩家的種族 ID
     */
    public String getPlayerRaceId(Player player) {
        return getPlayerRaceId(player.getUniqueId());
    }

    public String getPlayerRaceId(UUID uuid) {
        return playerRaces.get(uuid);
    }

    /**
     * 取得玩家的種族資料
     */
    public RaceData getPlayerRaceData(Player player) {
        String raceId = getPlayerRaceId(player);
        if (raceId == null) return null;
        return getRaceData(raceId);
    }

    public RaceData getPlayerRaceData(UUID uuid) {
        String raceId = getPlayerRaceId(uuid);
        if (raceId == null) return null;
        return getRaceData(raceId);
    }

    /**
     * 設定玩家的種族
     * @return true 如果設定成功
     */
    public boolean setPlayerRace(Player player, String raceId) {
        if (raceId == null || !raceRegistry.containsKey(raceId.toLowerCase())) {
            return false;
        }

        playerRaces.put(player.getUniqueId(), raceId.toLowerCase());
        applyRaceStats(player);
        savePlayerRace(player.getUniqueId());
        return true;
    }

    /**
     * 檢查玩家是否已選擇種族
     */
    public boolean hasRace(Player player) {
        return playerRaces.containsKey(player.getUniqueId());
    }

    /**
     * 重置玩家的種族 (管理員功能)
     */
    public void resetPlayerRace(Player player) {
        playerRaces.remove(player.getUniqueId());

        // 清除種族屬性加成
        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);
        stats.setRaceStrength(0);
        stats.setRaceMagic(0);
        stats.setRaceAgility(0);
        stats.setRaceVitality(0);
        stats.setRaceDefense(0);
        stats.setRaceSpirit(0);

        // 更新血量
        plugin.getPlayerStatsManager().updateMaxHealth(player);

        // 刪除檔案
        File file = new File(raceDataFolder, player.getUniqueId().toString() + ".yml");
        if (file.exists()) {
            file.delete();
        }
    }

    // ===== 套用種族效果 =====

    /**
     * 套用種族屬性加成到玩家
     */
    public void applyRaceStats(Player player) {
        RaceData raceData = getPlayerRaceData(player);
        if (raceData == null) return;

        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);
        int level = stats.getLevel();

        // 設定種族屬性加成 (基礎 + 等級成長)
        stats.setRaceStrength(raceData.calculateStrengthBonus(level));
        stats.setRaceMagic(raceData.calculateMagicBonus(level));
        stats.setRaceAgility(raceData.calculateAgilityBonus(level));
        stats.setRaceVitality(raceData.calculateVitalityBonus(level));
        stats.setRaceDefense(raceData.calculateDefenseBonus(level));
        stats.setRaceSpirit(raceData.calculateSpiritBonus(level));

        // 更新血量
        plugin.getPlayerStatsManager().updateMaxHealth(player);
    }

    /**
     * 取得玩家對指定武器類型的傷害加成倍率
     * @param player 玩家
     * @param weaponType 武器類型名稱 (如 "SWORD", "BOW", "AXE" 等)
     * @return 傷害倍率 (例: 1.3 = 130% 傷害)
     */
    public double getWeaponDamageBonus(Player player, String weaponType) {
        RaceData raceData = getPlayerRaceData(player);
        if (raceData == null) return 1.0;
        return raceData.getWeaponBonus(weaponType);
    }

    // ===== 資料持久化 =====

    /**
     * 載入玩家的種族資料 (登入時呼叫)
     */
    public void loadPlayerRace(UUID uuid) {
        File file = new File(raceDataFolder, uuid.toString() + ".yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String raceId = config.getString("race");
        if (raceId != null && raceRegistry.containsKey(raceId.toLowerCase())) {
            playerRaces.put(uuid, raceId.toLowerCase());
        }
    }

    /**
     * 儲存玩家的種族資料
     */
    public void savePlayerRace(UUID uuid) {
        String raceId = playerRaces.get(uuid);
        if (raceId == null) return;

        File file = new File(raceDataFolder, uuid.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("race", raceId);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[RaceManager] 無法儲存玩家種族資料: " + uuid);
            e.printStackTrace();
        }
    }

    /**
     * 卸載玩家資料 (登出時呼叫)
     */
    public void unloadPlayer(UUID uuid) {
        savePlayerRace(uuid);
        playerRaces.remove(uuid);
    }

    /**
     * 儲存所有玩家的種族資料
     */
    public void saveAll() {
        for (UUID uuid : playerRaces.keySet()) {
            savePlayerRace(uuid);
        }
    }

    /**
     * 重新載入種族配置
     */
    public void reload() {
        raceRegistry.clear();
        loadRaceConfig();
        plugin.getLogger().info("[RaceManager] 種族配置已重新載入，共 " + raceRegistry.size() + " 個種族");
    }
}


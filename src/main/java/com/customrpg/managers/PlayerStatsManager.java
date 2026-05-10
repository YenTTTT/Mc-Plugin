package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.Sound;

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
        data.put("spirit", config.getInt("stats.spirit", 0));

        data.put("level", config.getInt("stats.level", 1));
        data.put("exp", config.getInt("stats.exp", 0));
        data.put("statPoints", config.getInt("stats.statPoints", 0));
        data.put("talentPoints", config.getInt("stats.talentPoints", 0)); // 載入天賦點數

        data.put("maxMana", config.getDouble("stats.maxMana", 100.0));
        data.put("currentMana", config.getDouble("stats.currentMana", 100.0));
        data.put("manaRegen", config.getDouble("stats.manaRegen", 1.0));

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
        config.set("stats.spirit", data.get("spirit"));

        config.set("stats.level", data.get("level"));
        config.set("stats.exp", data.get("exp"));
        config.set("stats.statPoints", data.get("statPoints"));
        config.set("stats.talentPoints", data.get("talentPoints")); // 儲存天賦點數

        config.set("stats.maxMana", data.get("maxMana"));
        config.set("stats.currentMana", data.get("currentMana"));
        config.set("stats.manaRegen", data.get("manaRegen"));

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("無法儲存玩家數據: " + uuid);
            e.printStackTrace();
        }
    }

    /**
     * 更新玩家的最大血量 (根據 Vitality)
     * 使用 Health Scale 讓血條固定顯示為 10 顆愛心
     */
    public void updateMaxHealth(Player player) {
        PlayerStats stats = getStats(player);

        // 計算最大生命值：基礎血量 + (總 Vitality × 每點血量)
        // 總 Vitality 包含基礎體力 + 裝備體力（包含從 MAX_HEALTH 轉換的體力）
        double vitalityBonus = stats.getTotalVitality() * HP_PER_VITALITY;
        double newMaxHealth = BASE_MAX_HEALTH + vitalityBonus;

        // 使用 Attribute API 設定最大血量
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(Math.max(1.0, newMaxHealth));

            // 使用 Health Scale 固定血條顯示為 10 顆愛心 (20.0 血量)
            player.setHealthScaled(true);
            player.setHealthScale(20.0);

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
            case "spirit", "spi" -> stats.setSpirit(value);
            case "level", "lvl" -> stats.setLevel(value);
            case "exp" -> stats.setExp(value);
            case "points", "pts" -> stats.setStatPoints(value);
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

    /**
     * 給予玩家經驗值
     */
    public void addExp(Player player, long amount) {
        PlayerStats stats = getStats(player);
        long currentExp = stats.getExp();
        long newExp = currentExp + amount;

        stats.setExp(newExp);

        checkLevelUp(player);
        saveStats(player);
    }

    /**
     * 檢查是否升級
     */
    private void checkLevelUp(Player player) {
        PlayerStats stats = getStats(player);
        int level = stats.getLevel();
        long exp = stats.getExp();
        long requiredExp = getRequiredExp(level);

        if (exp >= requiredExp) {
            levelUp(player);
        }
    }

    /**
     * 玩家升級邏輯
     */
    public void levelUp(Player player) {
        PlayerStats stats = getStats(player);
        int currentLevel = stats.getLevel();
        long currentExp = stats.getExp();
        long requiredExp = getRequiredExp(currentLevel);

        // 扣除經驗並升級
        stats.setExp(currentExp - requiredExp);
        stats.setLevel(currentLevel + 1);

        // 增加屬性點數 (10 點)
        stats.setStatPoints(stats.getStatPoints() + 10);

        // 增加天賦點數 (每級1點)
        stats.addTalentPoints(1);

        // 自動提升整體屬性 (每升一等全屬性 +1)
        stats.setStrength(stats.getStrength() + 1);
        stats.setMagic(stats.getMagic() + 1);
        stats.setAgility(stats.getAgility() + 1);
        stats.setVitality(stats.getVitality() + 1);
        stats.setDefense(stats.getDefense() + 1);
        stats.setSpirit(stats.getSpirit() + 1);

        // 每次升級增加基礎魔力 +5 和魔力回復 +0.1
        double baseManaGrowth = 5.0;
        double baseManaRegenGrowth = 0.1;

        // 如果有種族，種族的成長倍率也會影響
        com.customrpg.races.RaceManager raceManager = plugin.getRaceManager();
        com.customrpg.races.RaceData raceData = null;
        if (raceManager != null && raceManager.hasRace(player)) {
            raceData = raceManager.getPlayerRaceData(player);
        }

        // 計算種族魔力成長加成
        double raceManaBonus = 0;
        double raceManaRegenBonus = 0;
        if (raceData != null) {
            raceManaBonus = raceData.calculateManaBonus(stats.getLevel());
            raceManaRegenBonus = raceData.calculateManaRegenBonus(stats.getLevel());
        }

        // 最終最大魔力 = 基礎100 + (等級 * 每級基礎成長) + 種族加成 + 裝備加成
        double newMaxMana = 100.0 + (stats.getLevel() * baseManaGrowth) + raceManaBonus + stats.getBonusMaxMana();
        stats.setMaxMana(newMaxMana);

        // 最終魔力回復 = 基礎1.0 + (等級 * 每級基礎成長) + 種族加成 + 裝備加成
        double newManaRegen = 1.0 + (stats.getLevel() * baseManaRegenGrowth) + raceManaRegenBonus + stats.getBonusManaRegen();
        stats.setManaRegen(newManaRegen);

        // 更新最大血量
        updateMaxHealth(player);

        // 重新計算種族屬性加成 (因為成長倍率與等級相關)
        if (raceManager != null && raceManager.hasRace(player)) {
            raceManager.applyRaceStats(player);
        }

        // 特效與訊息
        player.sendMessage(ChatColor.GOLD + "========================================");
        player.sendMessage(ChatColor.YELLOW + "  🎉 恭喜升級！你現在是等級 " + ChatColor.AQUA + (currentLevel + 1));
        player.sendMessage(ChatColor.GREEN + "  獲得 10 點屬性點數！");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "  獲得 1 點天賦點數！");
        player.sendMessage(ChatColor.GREEN + "  全屬性自動 +1！");
        player.sendMessage(ChatColor.AQUA + "  最大魔力: " + String.format("%.0f", stats.getMaxMana()) +
                " §7| §b魔力回復: " + String.format("%.1f", stats.getManaRegen()) + "/秒");
        player.sendMessage(ChatColor.GOLD + "========================================");

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);

        // 如果還有剩餘經驗值大於下一級需求，繼續升級
        checkLevelUp(player);

        // 刷新背包中裝備物品的「需求等級」lore
        com.customrpg.equipment.EquipmentManager equipmentManager = plugin.getEquipmentManager();
        if (equipmentManager != null) {
            equipmentManager.refreshRequiredLevelLore(player, stats.getLevel());
        }

        saveStats(player);
    }

    /**
     * 取得升級所需經驗值 (等級 * 100)
     */
    public long getRequiredExp(int level) {
        return level * 100L;
    }
}

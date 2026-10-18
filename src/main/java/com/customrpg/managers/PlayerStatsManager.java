package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import com.customrpg.balance.InitialBalanceModel;
import com.customrpg.balance.BalanceMigration;
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
    private final java.util.Set<UUID> loadFailed = ConcurrentHashMap.newKeySet();


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
    public boolean isDataAvailable(Player player) { getStats(player); return !loadFailed.contains(player.getUniqueId()); }

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
            loadFailed.remove(uuid);
            return new PlayerStats();
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
            if (BalanceMigration.migratePlayer(config, file.toPath(),
                    plugin.getDataFolder().toPath().resolve("data/balance-backups/v2/players"))) {
                plugin.getLogger().info("[Balance] Backed up and migrated player " + uuid);
            }
            loadFailed.remove(uuid);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            // 讀取失敗時不可回傳預設值後再覆寫原檔：標記為載入失敗並停止寫入，保留原檔供管理員修復
            loadFailed.add(uuid);
            plugin.getLogger().severe("無法讀取玩家數據 " + file.getName()
                    + "，本次不會覆寫該檔案，請手動檢查: " + e.getMessage());
            return new PlayerStats();
        }

        // 讀取數據
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("strength", config.getInt("stats.strength", 0));
        data.put("magic", config.getInt("stats.magic", 0));
        data.put("agility", config.getInt("stats.agility", 0));
        data.put("vitality", config.getInt("stats.vitality", 0));
        data.put("defense", config.getInt("stats.defense", 0));
        data.put("spirit", config.getInt("stats.spirit", 0));

        data.put("balance-version", config.getInt("stats.balance-version", BalanceMigration.VERSION));
        data.put("level", config.getInt("stats.level", 1));
        data.put("exp", config.getLong("stats.exp", 0L));
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
        if (loadFailed.contains(uuid)) {
            plugin.getLogger().warning("略過儲存玩家數據 " + uuid + "：原始檔案讀取失敗，避免以預設值覆寫");
            return;
        }

        File file = new File(playerDataFolder, uuid.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        Map<String, Object> data = stats.serialize();
        if (file.exists()) {
            try { config.load(file); } catch (Exception error) {
                plugin.getLogger().severe("Refusing to replace unreadable player file " + uuid); return;
            }
        }
        config.set("stats.balance-version", stats.getBalanceVersion());
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
            saveAtomically(config, file);
        } catch (IOException e) {
            plugin.getLogger().warning("無法儲存玩家數據: " + uuid);
            e.printStackTrace();
        }
    }

    /**
     * 先寫入暫存檔再取代原檔，避免寫到一半當機時留下截斷的存檔。
     */
    public static void saveAtomically(YamlConfiguration config, File file) throws IOException {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        config.save(temp);
        try {
            java.nio.file.Files.move(temp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            java.nio.file.Files.move(temp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 更新玩家的最大血量 (根據 Vitality)
     * 使用 Health Scale 讓血條固定顯示為 10 顆愛心
     */
    public void updateMaxHealth(Player player) {
        updateMaxHealth(player, false);
    }

    /** One formula shared by race changes, equipment, talents, login and level gains. */
    public void recalculateMana(Player player) {
        PlayerStats stats=getStats(player);
        var races=plugin.getRaceManager();
        var race=races==null?null:races.getPlayerRaceData(player);
        var talents=plugin.getTalentPassiveEffectManager();
        double talentMana=talents==null?0:talents.getPlayerBonus(player,"manaBonus");
        double talentRegen=talents==null?0:talents.getPlayerBonus(player,"mana-regen");
        int level=BalanceMigration.level(stats.getLevel());
        double raceMana=race==null?0:race.calculateManaBonus(level);
        double raceRegen=race==null?0:race.calculateManaRegenBonus(level);
        stats.setMaxMana(InitialBalanceModel.maxMana(level, stats.getTotalSpirit(), Math.clamp(stats.getBonusMaxMana(),0,10))
                +raceMana+Math.min(10,Math.max(0,talentMana)));
        stats.setManaRegen(InitialBalanceModel.manaRegen(level, stats.getTotalSpirit())
                +raceRegen+Math.min(.5,Math.max(0,talentRegen))+Math.clamp(stats.getBonusManaRegen(),0,.5));

    }

    /**
     * @param healToFull true 時補滿血量（升級獎勵）；false 時依比例保留目前血量，
     *                   避免換裝／天賦／種族重新計算時被當作免費補血。
     */
    public void updateMaxHealth(Player player, boolean healToFull) {
        PlayerStats stats = getStats(player);

        // 計算最大生命值：基礎血量 + (總 Vitality × 每點血量)
        // 總 Vitality 包含基礎體力 + 裝備體力（包含從 MAX_HEALTH 轉換的體力）
        double newMaxHealth = com.customrpg.balance.BalancePlayerRules.maxHealth(stats);

        // 使用 Attribute API 設定最大血量
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double oldMax = maxHealthAttr.getValue();
            double oldHealth = player.getHealth();

            maxHealthAttr.setBaseValue(Math.max(1.0, newMaxHealth));

            // 使用 Health Scale 固定血條顯示為 10 顆愛心 (20.0 血量)
            player.setHealthScaled(true);
            player.setHealthScale(20.0);

            if (player.isDead() || oldHealth <= 0) {
                return;
            }
            double max = maxHealthAttr.getValue();
            double health = healToFull || oldMax <= 0 ? max : oldHealth / oldMax * max;
            player.setHealth(Math.min(max, Math.max(0.1, health)));
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
        loadFailed.remove(uuid);
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
        if (amount <= 0) {
            return;
        }
        PlayerStats stats = getStats(player);
        long currentExp = stats.getExp();
        long newExp = currentExp > Long.MAX_VALUE - amount ? Long.MAX_VALUE : currentExp + amount;

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

        if (level < InitialBalanceModel.MAX_LEVEL && exp >= requiredExp) {
            levelUp(player);
        }
    }

    /**
     * 玩家升級邏輯
     */
    public void levelUp(Player player) {
        PlayerStats stats = getStats(player);
        int currentLevel = stats.getLevel();
        if (currentLevel >= InitialBalanceModel.MAX_LEVEL) return;
        if (!com.customrpg.balance.BalancePlayerRules.advanceLevel(stats)) return;

        com.customrpg.races.RaceManager raceManager = plugin.getRaceManager();
        if (raceManager != null) {
            raceManager.applyRaceStats(player);
        } else recalculateMana(player);
        if (plugin.getTalentManager()!=null) plugin.getTalentManager().applyTalentEffects(player);
        if (plugin.getEquipmentManager()!=null) plugin.getEquipmentManager().updatePlayerAttributes(player);
        // Apply new race vitality before awarding the level-up heal.
        updateMaxHealth(player, true);

        // 特效與訊息
        player.sendMessage(ChatColor.GOLD + "========================================");
        player.sendMessage(ChatColor.YELLOW + "  🎉 恭喜升級！你現在是等級 " + ChatColor.AQUA + (currentLevel + 1));
        player.sendMessage(ChatColor.GREEN + "  獲得 3 點屬性點數！");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "  獲得 1 點天賦點數！");
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

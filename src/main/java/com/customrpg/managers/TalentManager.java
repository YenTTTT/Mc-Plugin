package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.*;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Sound;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TalentManager - 天賦系統管理器
 *
 * 功能：
 * - 載入天賦配置與樹狀結構
 * - 管理玩家天賦數據
 * - 處理天賦升級與重置
 * - 計算天賦效果加成
 */
public class TalentManager {

    private final CustomRPG plugin;
    private final Map<TalentBranch, TalentTree> talentTrees;
    private final Map<UUID, PlayerTalents> playerTalentsCache;
    private final File playerTalentFolder;
    private final File talentConfigFile;

    public TalentManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.talentTrees = new HashMap<>();
        this.playerTalentsCache = new ConcurrentHashMap<>();

        // 設置資料夾
        this.playerTalentFolder = new File(plugin.getDataFolder(), "data/talents");
        this.talentConfigFile = new File(plugin.getDataFolder(), "config/talents.yml");

        // 確保資料夾存在
        if (!playerTalentFolder.exists()) {
            playerTalentFolder.mkdirs();
        }

        try {
            // 初始化天賦樹
            initializeTalentTrees();

            // 載入天賦配置
            loadTalentConfigurations();

            plugin.getLogger().info("TalentManager 初始化完成，載入了 " + getTotalTalentCount() + " 個天賦");
        } catch (Exception e) {
            plugin.getLogger().severe("TalentManager 初始化失敗: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 初始化三個天賦樹
     */
    private void initializeTalentTrees() {
        for (TalentBranch branch : TalentBranch.values()) {
            TalentTree tree = new TalentTree(branch, branch.getDisplayName(), branch.getDescription());
            talentTrees.put(branch, tree);
        }
    }

    /**
     * 從配置檔案載入天賦
     */
    private void loadTalentConfigurations() {
        plugin.getLogger().info("開始載入天賦配置從: " + talentConfigFile.getAbsolutePath());

        if (!talentConfigFile.exists()) {
            plugin.getLogger().info("配置檔案不存在，正在創建預設配置...");
            createDefaultTalentConfig();
        }

        if (!talentConfigFile.exists()) {
            plugin.getLogger().severe("無法創建配置檔案！");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(talentConfigFile);
        plugin.getLogger().info("配置檔案載入完成，根節點數量: " + config.getKeys(false).size());

        // 如果配置檔案是空的，先跳過載入天賦
        if (config.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("天賦配置檔案為空，請手動配置 talents.yml");
            plugin.getLogger().warning("配置檔案路徑: " + talentConfigFile.getAbsolutePath());
            return;
        }

        plugin.getLogger().info("找到配置分支: " + config.getKeys(false));

        for (TalentBranch branch : TalentBranch.values()) {
            loadBranchTalents(config, branch);
        }
    }

    /**
     * 載入指定分支的天賦
     */
    private void loadBranchTalents(YamlConfiguration config, TalentBranch branch) {
        String branchKey = branch.name().toLowerCase();
        plugin.getLogger().info("正在載入分支: " + branchKey);

        ConfigurationSection branchSection = config.getConfigurationSection(branchKey);

        if (branchSection == null) {
            plugin.getLogger().warning("未找到分支配置: " + branchKey);
            plugin.getLogger().warning("可用的配置節點: " + config.getKeys(false));
            return;
        }

        TalentTree tree = talentTrees.get(branch);
        if (tree == null) {
            plugin.getLogger().severe("天賦樹 " + branch + " 未初始化！");
            return;
        }

        Set<String> talentKeys = branchSection.getKeys(false);
        plugin.getLogger().info("分支 " + branchKey + " 中找到 " + talentKeys.size() + " 個天賦: " + talentKeys);

        for (String talentId : talentKeys) {
            ConfigurationSection talentSection = branchSection.getConfigurationSection(talentId);
            if (talentSection == null) {
                plugin.getLogger().warning("天賦 " + talentId + " 配置為空");
                continue;
            }

            try {
                Talent talent = loadTalentFromConfig(talentId, talentSection, branch);
                tree.registerTalent(talent);
                plugin.getLogger().info("成功載入天賦: " + talentId);
            } catch (Exception e) {
                plugin.getLogger().warning("載入天賦失敗 " + talentId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("載入 " + branch.getDisplayName() + " 分支完成: " + tree.getTalentCount() + " 個天賦");
    }

    /**
     * 從配置段載入單個天賦
     */
    private Talent loadTalentFromConfig(String talentId, ConfigurationSection section, TalentBranch branch) {
        String name = section.getString("name", talentId);
        String description = section.getString("description", "");
        TalentType type = TalentType.valueOf(section.getString("type", "PASSIVE").toUpperCase());
        int maxLevel = section.getInt("max-level", 5);
        int pointsPerLevel = section.getInt("points-per-level", 1);
        int tier = section.getInt("tier", 1);
        String icon = section.getString("icon", "BOOK");

        List<String> prerequisites = section.getStringList("prerequisites");

        // 載入基礎效果
        Map<String, Double> baseEffects = new HashMap<>();
        ConfigurationSection effectsSection = section.getConfigurationSection("base-effects");
        if (effectsSection != null) {
            for (String key : effectsSection.getKeys(false)) {
                baseEffects.put(key, effectsSection.getDouble(key));
            }
        }

        // 載入等級成長係數
        Map<String, Double> levelScaling = new HashMap<>();
        ConfigurationSection scalingSection = section.getConfigurationSection("level-scaling");
        if (scalingSection != null) {
            for (String key : scalingSection.getKeys(false)) {
                levelScaling.put(key, scalingSection.getDouble(key));
            }
        }

        return new Talent(talentId, name, description, type, branch,
                         maxLevel, pointsPerLevel, prerequisites,
                         baseEffects, levelScaling, tier, icon);
    }

    /**
     * 創建預設天賦配置檔案
     */
    private void createDefaultTalentConfig() {
        try {
            if (!talentConfigFile.getParentFile().exists()) {
                talentConfigFile.getParentFile().mkdirs();
            }

            if (!talentConfigFile.exists()) {
                // 嘗試從resources載入預設配置
                try {
                    plugin.saveResource("config/talents.yml", false);
                    plugin.getLogger().info("已從resources載入預設天賦配置檔案");
                } catch (Exception e) {
                    // 如果無法從resources載入，創建空檔案
                    talentConfigFile.createNewFile();
                    plugin.getLogger().warning("無法從resources載入配置，已創建空的天賦配置檔案: " + talentConfigFile.getPath());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("無法創建預設天賦配置: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== 玩家天賦數據管理 =====

    /**
     * 獲取玩家天賦數據
     * @param player 玩家
     * @return 天賦數據
     */
    public PlayerTalents getPlayerTalents(Player player) {
        return getPlayerTalents(player.getUniqueId());
    }

    /**
     * 獲取玩家天賦數據
     * @param uuid 玩家UUID
     * @return 天賦數據
     */
    public PlayerTalents getPlayerTalents(UUID uuid) {
        PlayerTalents cached = playerTalentsCache.get(uuid);
        if (cached != null) {
            // 同步天賦點數從 PlayerStats
            try {
                PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
                if (statsManager != null) {
                    com.customrpg.players.PlayerStats playerStats = statsManager.getStats(uuid);
                    if (playerStats != null) {
                        cached.setAvailablePoints(playerStats.getTalentPoints());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("同步天賦點數時發生錯誤: " + e.getMessage());
            }
            return cached;
        }
        return playerTalentsCache.computeIfAbsent(uuid, this::loadPlayerTalents);
    }

    /**
     * 從檔案載入玩家天賦數據
     */
    private PlayerTalents loadPlayerTalents(UUID uuid) {
        File playerFile = new File(playerTalentFolder, uuid + ".yml");

        PlayerTalents talents = new PlayerTalents();

        if (!playerFile.exists()) {
            // 新玩家，同步PlayerStats中的天賦點數
            try {
                PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
                if (statsManager != null) {
                    com.customrpg.players.PlayerStats playerStats = statsManager.getStats(uuid);
                    if (playerStats != null) {
                        talents.setAvailablePoints(playerStats.getTalentPoints());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("初始化玩家天賦數據時發生錯誤: " + e.getMessage());
            }
            return talents; // 返回預設數據
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

            // 從PlayerStats同步最新的天賦點數，而不是從檔案讀取
            try {
                PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
                if (statsManager != null) {
                    com.customrpg.players.PlayerStats playerStats = statsManager.getStats(uuid);
                    if (playerStats != null) {
                        talents.setAvailablePoints(playerStats.getTalentPoints());
                    }
                }
            } catch (Exception e) {
                // 如果無法同步，則從檔案讀取
                talents.setAvailablePoints(config.getInt("available-points", 0));
            }

            talents.setTotalPointsSpent(config.getInt("total-points-spent", 0));

            // 載入天賦等級
            ConfigurationSection talentsSection = config.getConfigurationSection("talents");
            if (talentsSection != null) {
                for (String talentId : talentsSection.getKeys(false)) {
                    int level = talentsSection.getInt(talentId, 0);
                    talents.setTalentLevel(talentId, level);
                }
            }

            // 載入分支點數
            ConfigurationSection branchSection = config.getConfigurationSection("branch-points");
            if (branchSection != null) {
                for (String branchName : branchSection.getKeys(false)) {
                    try {
                        TalentBranch branch = TalentBranch.valueOf(branchName.toUpperCase());
                        int points = branchSection.getInt(branchName, 0);
                        talents.getBranchPoints().put(branch, points);
                    } catch (IllegalArgumentException e) {
                        // 忽略無效的分支名稱
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("載入玩家天賦數據失敗 " + uuid + ": " + e.getMessage());
        }

        return talents;
    }

    /**
     * 儲存玩家天賦數據
     * @param uuid 玩家UUID
     * @param talents 天賦數據
     */
    public void savePlayerTalents(UUID uuid, PlayerTalents talents) {
        File playerFile = new File(playerTalentFolder, uuid + ".yml");

        try {
            YamlConfiguration config = new YamlConfiguration();

            config.set("available-points", talents.getAvailablePoints());
            config.set("total-points-spent", talents.getTotalPointsSpent());

            // 儲存天賦等級
            for (Map.Entry<String, Integer> entry : talents.getTalentLevels().entrySet()) {
                config.set("talents." + entry.getKey(), entry.getValue());
            }

            // 儲存分支點數
            for (Map.Entry<TalentBranch, Integer> entry : talents.getBranchPoints().entrySet()) {
                config.set("branch-points." + entry.getKey().name().toLowerCase(), entry.getValue());
            }

            config.save(playerFile);

        } catch (IOException e) {
            plugin.getLogger().warning("儲存玩家天賦數據失敗 " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * 儲存所有快取的玩家數據
     */
    public void saveAllPlayerTalents() {
        for (Map.Entry<UUID, PlayerTalents> entry : playerTalentsCache.entrySet()) {
            savePlayerTalents(entry.getKey(), entry.getValue());
        }
    }

    // ===== 天賦操作 =====

    /**
     * 升級天賦
     * @param player 玩家
     * @param talentId 天賦ID
     * @return 升級是否成功
     */
    public boolean upgradeTalent(Player player, String talentId) {
        PlayerTalents playerTalents = getPlayerTalents(player);
        Talent talent = findTalent(talentId);

        if (talent == null) {
            player.sendMessage(ChatColor.RED + "天賦不存在: " + talentId);
            return false;
        }

        int currentLevel = playerTalents.getTalentLevel(talentId);
        int targetLevel = currentLevel + 1;

        // 檢查等級上限
        if (targetLevel > talent.getMaxLevel()) {
            player.sendMessage(ChatColor.RED + "天賦已達最大等級！");
            return false;
        }

        // 檢查前置條件
        if (!talent.canLearn(playerTalents.getTalentLevels(), targetLevel)) {
            TalentTree tree = talentTrees.get(talent.getBranch());
            String status = tree.getPrerequisiteStatus(talentId, playerTalents.getTalentLevels());
            player.sendMessage(ChatColor.RED + "無法升級天賦：" + status);
            return false;
        }

        // 檢查天賦點數 - 從 PlayerStats 同步檢查
        PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
        com.customrpg.players.PlayerStats playerStats = statsManager.getStats(player);
        int pointsNeeded = talent.getPointsPerLevel();

        if (playerStats.getTalentPoints() < pointsNeeded) {
            player.sendMessage(ChatColor.RED + "天賦點數不足！需要 " + pointsNeeded + " 點，擁有 " + playerStats.getTalentPoints() + " 點");
            return false;
        }

        // 執行升級
        int newLevel = playerTalents.upgradeTalent(talentId, talent.getBranch(), pointsNeeded);

        // 同步扣除 PlayerStats 中的天賦點數
        if (playerStats.spendTalentPoints(pointsNeeded)) {
            statsManager.saveStats(player);
        }

        // 發送成功訊息
        player.sendMessage(ChatColor.GREEN + "成功升級 " + ChatColor.YELLOW + talent.getName() +
                          ChatColor.GREEN + " 到等級 " + ChatColor.GOLD + newLevel + ChatColor.GREEN + "！");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        // 應用天賦效果（如果需要即時應用）
        applyTalentEffects(player);

        return true;
    }

    /**
     * 重置所有天賦
     * @param player 玩家
     * @return 重置是否成功
     */
    public boolean resetAllTalents(Player player) {
        PlayerTalents playerTalents = getPlayerTalents(player);

        if (playerTalents.getTotalPointsSpent() == 0) {
            player.sendMessage(ChatColor.YELLOW + "您沒有已學習的天賦！");
            return false;
        }

        // 計算要返還的點數
        int refundedPoints = playerTalents.getTotalPointsSpent();

        // 執行重置
        playerTalents.resetAllTalents();

        // 同步返還 PlayerStats 中的天賦點數
        PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
        com.customrpg.players.PlayerStats playerStats = statsManager.getStats(player);
        playerStats.addTalentPoints(refundedPoints);
        statsManager.saveStats(player);

        // 重新應用效果
        applyTalentEffects(player);

        player.sendMessage(ChatColor.GREEN + "成功重置所有天賦！返還 " + ChatColor.GOLD + refundedPoints + ChatColor.GREEN + " 點天賦點數");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);

        return true;
    }

    /**
     * 給予玩家天賦點數
     * @param player 玩家
     * @param points 點數
     */
    public void givePoints(Player player, int points) {
        // 直接修改 PlayerStats 中的天賦點數
        PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
        com.customrpg.players.PlayerStats playerStats = statsManager.getStats(player);
        playerStats.addTalentPoints(points);
        statsManager.saveStats(player);

        player.sendMessage(ChatColor.GREEN + "獲得 " + ChatColor.GOLD + points + ChatColor.GREEN + " 點天賦點數！");
        player.sendMessage(ChatColor.YELLOW + "可用天賦點數: " + playerStats.getTalentPoints());
    }

    // ===== 工具方法 =====

    /**
     * 尋找天賦
     * @param talentId 天賦ID
     * @return 天賦物件
     */
    public Talent findTalent(String talentId) {
        for (TalentTree tree : talentTrees.values()) {
            Talent talent = tree.getTalent(talentId);
            if (talent != null) return talent;
        }
        return null;
    }

    /**
     * 獲取天賦樹
     * @param branch 分支
     * @return 天賦樹
     */
    public TalentTree getTalentTree(TalentBranch branch) {
        TalentTree tree = talentTrees.get(branch);
        if (tree == null) {
            plugin.getLogger().warning("天賦樹 " + branch + " 為 null! 可用的天賦樹: " + talentTrees.keySet());
        } else {
            plugin.getLogger().info("獲取天賦樹 " + branch + ", 包含 " + tree.getTalentCount() + " 個天賦");
        }
        return tree;
    }

    /**
     * 獲取所有天賦樹
     * @return 天賦樹映射表
     */
    public Map<TalentBranch, TalentTree> getAllTalentTrees() {
        return talentTrees;
    }

    /**
     * 獲取總天賦數量
     * @return 天賦總數
     */
    public int getTotalTalentCount() {
        return talentTrees.values().stream().mapToInt(TalentTree::getTalentCount).sum();
    }

    /**
     * 獲取插件實例
     */
    public CustomRPG getPlugin() {
        return plugin;
    }

    /**
     * 應用玩家的天賦效果（預留給PassiveEffectManager處理）
     * @param player 玩家
     */
    public void applyTalentEffects(Player player) {
        try {
            PlayerTalents playerTalents = getPlayerTalents(player);
            PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
            com.customrpg.players.PlayerStats playerStats = statsManager.getStats(player);

            // 計算所有天賦提供的屬性加成
            int totalStrengthBonus = 0;
            int totalMagicBonus = 0;
            int totalAgilityBonus = 0;
            int totalVitalityBonus = 0;
            int totalDefenseBonus = 0;

            // 遍歷所有學習過的天賦
            for (Map.Entry<String, Integer> entry : playerTalents.getTalentLevels().entrySet()) {
                String talentId = entry.getKey();
                int level = entry.getValue();

                if (level > 0) {
                    Talent talent = findTalent(talentId);
                    if (talent != null && talent.getType() == TalentType.ATTRIBUTE) {
                        // 計算屬性加成
                        totalStrengthBonus += (int) talent.getEffectValue("strength", level);
                        totalMagicBonus += (int) talent.getEffectValue("magic", level);
                        totalAgilityBonus += (int) talent.getEffectValue("agility", level);
                        totalVitalityBonus += (int) talent.getEffectValue("vitality", level);
                        totalDefenseBonus += (int) talent.getEffectValue("defense", level);
                    }
                }
            }

            // 暫時獲取基礎屬性（沒有天賦加成的）
            int baseStrength = playerStats.getStrength() - getTalentAttributeBonus(player, "strength");
            int baseMagic = playerStats.getMagic() - getTalentAttributeBonus(player, "magic");
            int baseAgility = playerStats.getAgility() - getTalentAttributeBonus(player, "agility");
            int baseVitality = playerStats.getVitality() - getTalentAttributeBonus(player, "vitality");
            int baseDefense = playerStats.getDefense() - getTalentAttributeBonus(player, "defense");

            // 確保基礎值不會小於0
            baseStrength = Math.max(0, baseStrength);
            baseMagic = Math.max(0, baseMagic);
            baseAgility = Math.max(0, baseAgility);
            baseVitality = Math.max(0, baseVitality);
            baseDefense = Math.max(0, baseDefense);

            // 應用新的總屬性值（基礎 + 天賦加成）
            playerStats.setStrength(baseStrength + totalStrengthBonus);
            playerStats.setMagic(baseMagic + totalMagicBonus);
            playerStats.setAgility(baseAgility + totalAgilityBonus);
            playerStats.setVitality(baseVitality + totalVitalityBonus);
            playerStats.setDefense(baseDefense + totalDefenseBonus);

            // 更新最大血量（基於新的vitality值）
            statsManager.updateMaxHealth(player);

            // 儲存更新後的屬性
            statsManager.saveStats(player);

            // 快取天賦加成值以便下次使用
            cacheTalentBonuses(player, totalStrengthBonus, totalMagicBonus, totalAgilityBonus, totalVitalityBonus, totalDefenseBonus);

            player.sendMessage("§a天賦效果已應用！");
            player.sendMessage("§7力量: +" + totalStrengthBonus + ", 魔法: +" + totalMagicBonus +
                              ", 敏捷: +" + totalAgilityBonus + ", 體力: +" + totalVitalityBonus + ", 防禦: +" + totalDefenseBonus);

        } catch (Exception e) {
            plugin.getLogger().warning("應用天賦效果時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 快取玩家的天賦屬性加成
    private final Map<UUID, Map<String, Integer>> talentBonusCache = new ConcurrentHashMap<>();

    /**
     * 快取天賦屬性加成
     */
    private void cacheTalentBonuses(Player player, int str, int mag, int agi, int vit, int def) {
        Map<String, Integer> bonuses = new HashMap<>();
        bonuses.put("strength", str);
        bonuses.put("magic", mag);
        bonuses.put("agility", agi);
        bonuses.put("vitality", vit);
        bonuses.put("defense", def);
        talentBonusCache.put(player.getUniqueId(), bonuses);
    }

    /**
     * 獲取玩家的天賦屬性加成
     */
    private int getTalentAttributeBonus(Player player, String attribute) {
        Map<String, Integer> bonuses = talentBonusCache.get(player.getUniqueId());
        return bonuses != null ? bonuses.getOrDefault(attribute, 0) : 0;
    }

    /**
     * 計算玩家天賦加成
     * @param player 玩家
     * @param effectName 效果名稱
     * @return 加成值
     */
    public double calculateTalentBonus(Player player, String effectName) {
        PlayerTalents playerTalents = getPlayerTalents(player);
        double totalBonus = 0.0;

        for (Map.Entry<String, Integer> entry : playerTalents.getTalentLevels().entrySet()) {
            String talentId = entry.getKey();
            int level = entry.getValue();

            if (level > 0) {
                Talent talent = findTalent(talentId);
                if (talent != null && talent.getBaseEffects().containsKey(effectName)) {
                    totalBonus += talent.getEffectValue(effectName, level);
                }
            }
        }

        return totalBonus;
    }

    /**
     * 關閉管理器，儲存所有數據
     */
    public void shutdown() {
        saveAllPlayerTalents();
        playerTalentsCache.clear();
    }
}


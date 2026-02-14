package com.customrpg.gui;

import com.customrpg.CustomRPG;
import com.customrpg.managers.TalentManager;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * TalentGUI - 天賦系統圖形介面
 *
 * 功能：
 * - 顯示三分支天賦樹
 * - 支援天賦升級與預覽
 * - 顯示前置條件與效果
 * - 天賦重置功能
 */
public class TalentGUI implements Listener {

    private final CustomRPG plugin;
    private final TalentManager talentManager;
    private final Map<UUID, TalentBranch> currentBranch;
    private final Map<UUID, Inventory> openInventories;

    // GUI 配置
    private static final int GUI_SIZE = 54; // 6行9列
    private static final String GUI_TITLE_PREFIX = "§6§l天賦樹 - ";

    // 特殊按鈕位置
    private static final int WARRIOR_BUTTON = 45;
    private static final int MAGE_BUTTON = 46;
    private static final int ASSASSIN_BUTTON = 47;
    private static final int STATS_BUTTON = 49;
    private static final int RESET_BUTTON = 53;
    private static final int CLOSE_BUTTON = 8;

    public TalentGUI(CustomRPG plugin, TalentManager talentManager) {
        this.plugin = plugin;
        this.talentManager = talentManager;
        this.currentBranch = new HashMap<>();
        this.openInventories = new HashMap<>();
    }

    /**
     * 為玩家開啟天賦GUI
     * @param player 玩家
     * @param branch 要顯示的分支（null為預設戰士）
     */
    public void openTalentGUI(Player player, TalentBranch branch) {
        if (player == null) {
            return;
        }

        if (branch == null) {
            branch = TalentBranch.WARRIOR;
        }

        try {
            currentBranch.put(player.getUniqueId(), branch);
            Inventory gui = createTalentGUI(player, branch);
            openInventories.put(player.getUniqueId(), gui);

            player.openInventory(gui);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        } catch (Exception e) {
            player.sendMessage("§c開啟天賦界面時發生錯誤，請聯繫管理員");
            e.printStackTrace();
        }
    }

    /**
     * 創建天賦GUI介面
     */
    private Inventory createTalentGUI(Player player, TalentBranch branch) {
        String title = GUI_TITLE_PREFIX + branch.getDisplayName();
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        // 清空界面
        fillEmptySlots(gui);

        // 添加分支切換按鈕
        addBranchButtons(gui, branch);

        // 添加功能按鈕
        addFunctionButtons(gui, player);

        // 添加天賦圖標
        addTalentIcons(gui, player, branch);

        return gui;
    }

    /**
     * 填充空白位置
     */
    private void fillEmptySlots(Inventory gui) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
        }

        // 填充邊框和空白位置
        int[] fillerSlots = {0, 1, 2, 3, 4, 5, 6, 7, 9, 17, 18, 26, 27, 35, 36, 44, 48, 50, 51, 52};
        for (int slot : fillerSlots) {
            gui.setItem(slot, filler);
        }
    }

    /**
     * 添加分支切換按鈕
     */
    private void addBranchButtons(Inventory gui, TalentBranch currentBranch) {
        // 戰士分支按鈕
        ItemStack warriorButton = createBranchButton(
            TalentBranch.WARRIOR,
            Material.IRON_SWORD,
            currentBranch == TalentBranch.WARRIOR
        );
        gui.setItem(WARRIOR_BUTTON, warriorButton);

        // 法師分支按鈕
        ItemStack mageButton = createBranchButton(
            TalentBranch.MAGE,
            Material.ENCHANTED_BOOK,
            currentBranch == TalentBranch.MAGE
        );
        gui.setItem(MAGE_BUTTON, mageButton);

        // 刺客分支按鈕
        ItemStack assassinButton = createBranchButton(
            TalentBranch.ASSASSIN,
            Material.DIAMOND_SWORD,
            currentBranch == TalentBranch.ASSASSIN
        );
        gui.setItem(ASSASSIN_BUTTON, assassinButton);
    }

    /**
     * 創建分支按鈕
     */
    private ItemStack createBranchButton(TalentBranch branch, Material material, boolean selected) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            String prefix = selected ? "§a§l» " : "§7";
            meta.setDisplayName(prefix + branch.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add("§8" + branch.getDescription());
            lore.add("");
            if (selected) {
                lore.add("§a§l當前選中");
            } else {
                lore.add("§e點擊切換到此分支");
            }
            meta.setLore(lore);

            button.setItemMeta(meta);
        }
        return button;
    }

    /**
     * 添加功能按鈕
     */
    private void addFunctionButtons(Inventory gui, Player player) {
        PlayerTalents playerTalents = talentManager.getPlayerTalents(player);

        // 從PlayerStats獲取實際的天賦點數
        com.customrpg.managers.PlayerStatsManager statsManager = ((com.customrpg.CustomRPG) org.bukkit.Bukkit.getPluginManager().getPlugin("CustomRPG")).getPlayerStatsManager();
        com.customrpg.players.PlayerStats playerStats = statsManager.getStats(player);
        int actualTalentPoints = playerStats.getTalentPoints();

        // 統計資訊按鈕
        ItemStack statsButton = new ItemStack(Material.PAPER);
        ItemMeta statsMeta = statsButton.getItemMeta();
        if (statsMeta != null) {
            statsMeta.setDisplayName("§6§l角色統計");

            List<String> statsLore = new ArrayList<>();
            statsLore.add("§8查看詳細的天賦統計資訊");
            statsLore.add("");
            statsLore.add("§e可用天賦點數: §a" + actualTalentPoints);
            statsLore.add("§e已消耗點數: §c" + playerTalents.getTotalPointsSpent());
            statsLore.add("");

            // 分支統計
            for (TalentBranch branch : TalentBranch.values()) {
                int branchPoints = playerTalents.getBranchPoints(branch);
                TalentTree tree = talentManager.getTalentTree(branch);
                String branchStats = tree.getBranchStats(playerTalents.getTalentLevels());
                statsLore.add("§6" + branch.getDisplayName() + " §7(" + branchPoints + " 點)");
            }

            TalentBranch mainSpec = playerTalents.getMainSpecialization();
            if (mainSpec != null) {
                statsLore.add("");
                statsLore.add("§b主要專精: §a" + mainSpec.getDisplayName());
            }

            statsMeta.setLore(statsLore);
            statsButton.setItemMeta(statsMeta);
        }
        gui.setItem(STATS_BUTTON, statsButton);

        // 重置按鈕
        ItemStack resetButton = new ItemStack(Material.TNT);
        ItemMeta resetMeta = resetButton.getItemMeta();
        if (resetMeta != null) {
            resetMeta.setDisplayName("§c§l重置天賦");

            List<String> resetLore = new ArrayList<>();
            resetLore.add("§8重置所有已學習的天賦");
            resetLore.add("§8並返還所有天賦點數");
            resetLore.add("");
            if (playerTalents.getTotalPointsSpent() > 0) {
                resetLore.add("§e將返還 §a" + playerTalents.getTotalPointsSpent() + " §e點天賦點數");
                resetLore.add("§c警告: 此操作不可逆！");
                resetLore.add("§a左鍵點擊確認重置");
            } else {
                resetLore.add("§7沒有已學習的天賦");
            }

            resetMeta.setLore(resetLore);
            resetButton.setItemMeta(resetMeta);
        }
        gui.setItem(RESET_BUTTON, resetButton);

        // 關閉按鈕
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName("§c關閉");
            closeButton.setItemMeta(closeMeta);
        }
        gui.setItem(CLOSE_BUTTON, closeButton);
    }

    /**
     * 添加天賦圖標
     */
    private void addTalentIcons(Inventory gui, Player player, TalentBranch branch) {
        TalentTree tree = talentManager.getTalentTree(branch);
        PlayerTalents playerTalents = talentManager.getPlayerTalents(player);

        for (Talent talent : tree.getAllTalents().values()) {
            int slot = tree.calculateGUISlot(talent.getId());
            if (slot >= 0 && slot < GUI_SIZE) {
                ItemStack icon = createTalentIcon(talent, playerTalents, player);
                gui.setItem(slot, icon);
            }
        }
    }

    /**
     * 創建天賦圖標
     */
    private ItemStack createTalentIcon(Talent talent, PlayerTalents playerTalents, Player player) {
        Material iconMaterial;
        try {
            iconMaterial = Material.valueOf(talent.getIcon());
        } catch (IllegalArgumentException e) {
            iconMaterial = Material.BOOK;
        }

        ItemStack icon = new ItemStack(iconMaterial);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            int currentLevel = playerTalents.getTalentLevel(talent.getId());
            boolean canLearn = talentManager.getTalentTree(talent.getBranch())
                .canLearnTalent(talent.getId(), playerTalents.getTalentLevels());

            // 從PlayerStats檢查實際可用的天賦點數
            com.customrpg.managers.PlayerStatsManager statsManager = ((com.customrpg.CustomRPG) org.bukkit.Bukkit.getPluginManager().getPlugin("CustomRPG")).getPlayerStatsManager();
            com.customrpg.players.PlayerStats playerStats = statsManager.getStats(player);
            boolean canAfford = playerStats.getTalentPoints() >= talent.getPointsPerLevel();

            // 設置顯示名稱
            String displayName = formatTalentDisplayName(talent, currentLevel, canLearn, canAfford);
            meta.setDisplayName(displayName);

            // 設置描述
            List<String> lore = createTalentLore(talent, currentLevel, playerTalents, canLearn, canAfford);
            meta.setLore(lore);

            icon.setItemMeta(meta);
        }

        return icon;
    }

    /**
     * 格式化天賦顯示名稱
     */
    private String formatTalentDisplayName(Talent talent, int currentLevel, boolean canLearn, boolean canAfford) {
        String baseName = talent.getName();
        String levelText = "";
        String statusColor;

        if (currentLevel > 0) {
            statusColor = "§a"; // 已學習 - 綠色
            levelText = " §7[§b" + currentLevel + "§7/§b" + talent.getMaxLevel() + "§7]";
        } else if (canLearn && canAfford) {
            statusColor = "§e"; // 可學習 - 黃色
        } else if (canLearn) {
            statusColor = "§c"; // 點數不足 - 紅色
        } else {
            statusColor = "§8"; // 無法學習 - 灰色
        }

        return statusColor + baseName + levelText;
    }

    /**
     * 創建天賦描述
     */
    private List<String> createTalentLore(Talent talent, int currentLevel, PlayerTalents playerTalents, boolean canLearn, boolean canAfford) {
        List<String> lore = new ArrayList<>();

        // 天賦類型與層級
        lore.add("§8類型: §7" + talent.getType().name() + " §8| §7第" + talent.getTier() + "層");
        lore.add("");

        // 天賦描述
        String[] descLines = talent.getFormattedDescription(currentLevel).split("\n");
        for (String line : descLines) {
            lore.add(line);
        }

        lore.add("");

        // 前置條件檢查
        if (!talent.getPrerequisites().isEmpty()) {
            lore.add("§6前置條件:");
            for (String prerequisiteId : talent.getPrerequisites()) {
                boolean hasPrerequsite = playerTalents.hasTalent(prerequisiteId);
                String status = hasPrerequsite ? "§a✓" : "§c✗";
                Talent prereqTalent = talentManager.findTalent(prerequisiteId);
                String prereqName = prereqTalent != null ? prereqTalent.getName() : prerequisiteId;
                lore.add("  " + status + " §7" + prereqName);
            }
            lore.add("");
        }

        // 消耗與操作提示
        if (currentLevel < talent.getMaxLevel()) {
            lore.add("§6升級消耗: §e" + talent.getPointsPerLevel() + " 天賦點數");

            if (canLearn && canAfford) {
                lore.add("§a左鍵點擊升級");
            } else if (!canLearn) {
                lore.add("§c需要滿足前置條件");
            } else {
                lore.add("§c天賦點數不足");
            }
        } else {
            lore.add("§b已達最大等級");
        }

        if (currentLevel > 0) {
            lore.add("§7右鍵查看詳細效果");
        }

        return lore;
    }

    /**
     * 處理GUI點擊事件
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // 檢查是否是天賦GUI - 使用多重檢查
        String invTitle = event.getView().getTitle();
        boolean isTalentGUI = openInventories.containsKey(player.getUniqueId()) ||
                             (invTitle != null && invTitle.startsWith(GUI_TITLE_PREFIX));

        if (!isTalentGUI) return;

        // 阻止所有物品拿取行為
        event.setCancelled(true);

        int slot = event.getRawSlot();
        ClickType clickType = event.getClick();

        // 添加調試信息
        player.sendMessage("§8[調試] 點擊了slot: " + slot + ", 按鈕位置: 戰士=" + WARRIOR_BUTTON + " 法師=" + MAGE_BUTTON + " 刺客=" + ASSASSIN_BUTTON);

        // 分支切換按鈕
        if (slot == WARRIOR_BUTTON) {
            player.sendMessage("§c點擊戰士分支");
            switchBranch(player, TalentBranch.WARRIOR);
        } else if (slot == MAGE_BUTTON) {
            player.sendMessage("§b點擊法師分支");
            switchBranch(player, TalentBranch.MAGE);
        } else if (slot == ASSASSIN_BUTTON) {
            player.sendMessage("§6點擊刺客分支");
            switchBranch(player, TalentBranch.ASSASSIN);
        }
        // 功能按鈕
        else if (slot == RESET_BUTTON && clickType == ClickType.LEFT) {
            handleTalentReset(player);
        } else if (slot == CLOSE_BUTTON) {
            player.closeInventory();
        }
        // 天賦圖標點擊
        else {
            handleTalentClick(player, slot, clickType);
        }
    }

    /**
     * 處理分支切換
     */
    private void switchBranch(Player player, TalentBranch newBranch) {
        TalentBranch currentBranch = this.currentBranch.get(player.getUniqueId());
        if (currentBranch != newBranch) {
            try {
                // 預先設置新分支
                this.currentBranch.put(player.getUniqueId(), newBranch);

                // 直接更新當前GUI內容，而不關閉
                Inventory currentGui = openInventories.get(player.getUniqueId());
                if (currentGui != null) {
                    // 清空並重新填充GUI
                    currentGui.clear();
                    fillEmptySlots(currentGui);
                    addBranchButtons(currentGui, newBranch);
                    addFunctionButtons(currentGui, player);
                    addTalentIcons(currentGui, player, newBranch);

                    // 更新GUI標題（創建新的GUI）
                    String newTitle = GUI_TITLE_PREFIX + newBranch.getDisplayName();
                    Inventory newGui = Bukkit.createInventory(null, GUI_SIZE, newTitle);

                    // 複製更新後的內容
                    for (int i = 0; i < currentGui.getSize(); i++) {
                        newGui.setItem(i, currentGui.getItem(i));
                    }

                    // 更新引用並打開新GUI
                    openInventories.put(player.getUniqueId(), newGui);
                    player.openInventory(newGui);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                } else {
                    // 如果沒有當前GUI，創建新的
                    Inventory newGui = createTalentGUI(player, newBranch);
                    openInventories.put(player.getUniqueId(), newGui);
                    player.openInventory(newGui);
                }

            } catch (Exception e) {
                player.sendMessage("§c切換分支時發生錯誤，請重新開啟GUI");
                e.printStackTrace();

                // 錯誤恢復
                try {
                    Inventory newGui = createTalentGUI(player, newBranch);
                    openInventories.put(player.getUniqueId(), newGui);
                    player.openInventory(newGui);
                } catch (Exception e2) {
                    player.sendMessage("§c無法恢復GUI，請重新使用 /talent 指令");
                }
            }
        }
    }

    /**
     * 處理天賦重置
     */
    private void handleTalentReset(Player player) {
        if (talentManager.resetAllTalents(player)) {
            // 獲取當前分支，如果為空則設為戰士
            TalentBranch branch = currentBranch.getOrDefault(player.getUniqueId(), TalentBranch.WARRIOR);
            currentBranch.put(player.getUniqueId(), branch);

            // 刷新GUI
            try {
                Inventory newGui = createTalentGUI(player, branch);
                openInventories.put(player.getUniqueId(), newGui);
                player.openInventory(newGui);
            } catch (Exception e) {
                player.sendMessage("§c重置後刷新GUI時發生錯誤: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 處理天賦點擊
     */
    private void handleTalentClick(Player player, int slot, ClickType clickType) {
        // 添加詳細的調試信息
        player.sendMessage("§8[調試] 處理天賦點擊 - slot: " + slot + ", 點擊類型: " + clickType);

        TalentBranch branch = currentBranch.get(player.getUniqueId());
        player.sendMessage("§8[調試] 當前分支快取: " + branch);
        player.sendMessage("§8[調試] GUI快取存在: " + openInventories.containsKey(player.getUniqueId()));

        if (branch == null) {
            // 嘗試從GUI標題推斷分支
            Inventory currentGui = openInventories.get(player.getUniqueId());
            if (currentGui != null) {
                String title = player.getOpenInventory().getTitle();
                player.sendMessage("§8[調試] 從GUI標題推斷分支: " + title);

                if (title.contains("戰士")) branch = TalentBranch.WARRIOR;
                else if (title.contains("法師")) branch = TalentBranch.MAGE;
                else if (title.contains("刺客")) branch = TalentBranch.ASSASSIN;
                else branch = TalentBranch.WARRIOR; // 預設

                // 重新設置分支
                currentBranch.put(player.getUniqueId(), branch);
                player.sendMessage("§8[調試] 重新設置分支為: " + branch);
            } else {
                player.sendMessage("§c錯誤：無法確定當前分支");
                player.sendMessage("§8[調試] 無法從GUI標題推斷分支，currentGui為null");
                return;
            }
        }

        TalentTree tree = talentManager.getTalentTree(branch);
        if (tree == null) {
            player.sendMessage("§c錯誤：天賦樹未載入，請檢查配置檔案");
            player.sendMessage("§7Debug: 分支 " + branch + " 的天賦樹為 null");
            return;
        }

        // 查找對應的天賦
        Talent clickedTalent = null;
        try {
            for (Talent talent : tree.getAllTalents().values()) {
                if (tree.calculateGUISlot(talent.getId()) == slot) {
                    clickedTalent = talent;
                    break;
                }
            }
        } catch (Exception e) {
            player.sendMessage("§c查找天賦時發生錯誤: " + e.getMessage());
            return;
        }

        if (clickedTalent == null) return;

        if (clickType == ClickType.LEFT) {
            // 左鍵升級天賦
            if (talentManager.upgradeTalent(player, clickedTalent.getId())) {
                // 確保分支狀態正確保存
                currentBranch.put(player.getUniqueId(), branch);

                // 立即更新GUI內容，而不是重新打開
                try {
                    Inventory currentGui = openInventories.get(player.getUniqueId());
                    if (currentGui != null) {
                        // 清空當前GUI
                        currentGui.clear();

                        // 重新填充GUI內容
                        fillEmptySlots(currentGui);
                        addBranchButtons(currentGui, branch);
                        addFunctionButtons(currentGui, player);
                        addTalentIcons(currentGui, player, branch);

                        // 更新玩家視圖
                        player.updateInventory();

                        player.sendMessage("§a天賦升級成功，GUI已更新！");
                    } else {
                        // 如果當前GUI為空，創建新的
                        Inventory newGui = createTalentGUI(player, branch);
                        openInventories.put(player.getUniqueId(), newGui);
                        player.openInventory(newGui);
                    }
                } catch (Exception e) {
                    player.sendMessage("§c刷新GUI時發生錯誤: " + e.getMessage());
                    e.printStackTrace();

                    // 錯誤恢復：重新打開GUI
                    try {
                        Inventory newGui = createTalentGUI(player, branch);
                        openInventories.put(player.getUniqueId(), newGui);
                        player.openInventory(newGui);
                    } catch (Exception e2) {
                        player.sendMessage("§c無法恢復GUI，請重新使用 /talent 指令");
                    }
                }
            }
        } else if (clickType == ClickType.RIGHT) {
            // 右鍵查看詳細資訊
            showTalentDetails(player, clickedTalent);
        }
    }

    /**
     * 顯示天賦詳細資訊
     */
    private void showTalentDetails(Player player, Talent talent) {
        PlayerTalents playerTalents = talentManager.getPlayerTalents(player);
        int currentLevel = playerTalents.getTalentLevel(talent.getId());

        player.sendMessage("§8§m" + "=".repeat(50));
        player.sendMessage("§6§l天賦詳情: " + talent.getName());
        player.sendMessage("");
        player.sendMessage("§7" + talent.getDescription());
        player.sendMessage("");
        player.sendMessage("§e類型: §f" + talent.getType().name());
        player.sendMessage("§e分支: §f" + talent.getBranch().getDisplayName());
        player.sendMessage("§e層級: §f" + talent.getTier());
        player.sendMessage("§e當前等級: §f" + currentLevel + "/" + talent.getMaxLevel());

        if (currentLevel > 0) {
            player.sendMessage("");
            player.sendMessage("§a當前效果:");
            for (Map.Entry<String, Double> entry : talent.getBaseEffects().entrySet()) {
                String effectName = entry.getKey();
                double value = talent.getEffectValue(effectName, currentLevel);
                player.sendMessage("§7- " + effectName + ": §a" + String.format("%.1f", value));
            }
        }

        player.sendMessage("§8§m" + "=".repeat(50));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
    }

    /**
     * 處理GUI關閉事件
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            UUID uuid = player.getUniqueId();

            if (openInventories.containsKey(uuid)) {
                player.sendMessage("§8[調試] GUI關閉事件觸發");

                // 檢查是否是天賦GUI的關閉事件
                String title = event.getView().getTitle();
                boolean isTalentGUI = title.startsWith(GUI_TITLE_PREFIX);

                if (isTalentGUI) {
                    player.sendMessage("§8[調試] 確認是天賦GUI關閉");

                    // 延遲檢查是否即將重新打開GUI
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        // 如果3tick後玩家沒有重新打開天賦GUI，才清理數據
                        if (!openInventories.containsKey(uuid)) {
                            currentBranch.remove(uuid);
                            player.sendMessage("§8[調試] 清理分支快取");

                            // 儲存玩家天賦數據
                            PlayerTalents talents = talentManager.getPlayerTalents(player);
                            talentManager.savePlayerTalents(uuid, talents);
                        } else {
                            player.sendMessage("§8[調試] GUI重新打開，保留分支快取");
                        }
                    }, 3L);
                }

                // 先移除當前GUI引用，但保留分支信息一小段時間
                openInventories.remove(uuid);
            }
        }
    }

    /**
     * 清理資源
     */
    public void cleanup() {
        openInventories.clear();
        currentBranch.clear();
    }
}



package com.customrpg.gui;

import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.players.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * StatsGUI - 屬性介面系統
 *
 * 5x5 箱子介面：
 * Row 1: 屬性顯示（鐵劍、附魔之瓶、弓、花、鑽石胸甲）
 * Row 2: 灰色玻璃板
 * Row 3: 增加 1 點按鈕（鑽石）
 * Row 4: 灰色玻璃板
 * Row 5: 增加 5 點按鈕（鑽石）
 */
public class StatsGUI implements Listener {

    private final PlayerStatsManager statsManager;
    private static final String GUI_TITLE = ChatColor.DARK_PURPLE + "屬性介面";

    // 槽位定義
    private static final int[] STAT_DISPLAY_SLOTS = {0, 1, 2, 3, 4, 5}; // Row 1
    private static final int[] GLASS_ROW_2 = {9, 10, 11, 12, 13, 14}; // Row 2
    private static final int[] ADD_1_SLOTS = {18, 19, 20, 21, 22, 23}; // Row 3
    private static final int[] GLASS_ROW_4 = {27, 28, 29, 30, 31, 32}; // Row 4
    private static final int[] ADD_5_SLOTS = {36, 37, 38, 39, 40, 41}; // Row 5

    public StatsGUI(PlayerStatsManager statsManager) {
        this.statsManager = statsManager;
    }

    /**
     * 開啟屬性 GUI
     */
    public void openStatsGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, GUI_TITLE); // 5 rows * 9 slots = 45

        PlayerStats stats = statsManager.getStats(player);

        // Row 1: 屬性顯示
        gui.setItem(0, createStatDisplay(Material.IRON_SWORD, "物理攻擊 (Strength)",
                stats.getStrength(), stats.getEquipmentStrength(),
                "每點增加 0.5 近戰傷害"));
        gui.setItem(1, createStatDisplay(Material.EXPERIENCE_BOTTLE, "魔法攻擊 (Magic)",
                stats.getMagic(), stats.getEquipmentMagic(),
                "每點增加 0.3 技能傷害"));
        gui.setItem(2, createStatDisplay(Material.BOW, "敏捷 (Agility)",
                stats.getAgility(), stats.getEquipmentAgility(),
                "每點增加 0.2% 暴擊率", "每點增加 0.1 弓箭傷害"));
        gui.setItem(3, createStatDisplay(Material.POPPY, "生命力 (Vitality)",
                stats.getVitality(), stats.getEquipmentVitality(),
                "每點增加 2.0 最大血量"));
        gui.setItem(4, createStatDisplay(Material.DIAMOND_CHESTPLATE, "防禦 (Defense)",
                stats.getDefense(), stats.getEquipmentDefense(),
                "每點減免 0.5% 傷害"));
        gui.setItem(5, createStatDisplay(Material.GHAST_TEAR, "精神 (Spirit)",
                stats.getSpirit(), stats.getEquipmentSpirit(),
                "目前無特殊效果"));

        // Center slot: Stat Points Display
        gui.setItem(8, createStatPointsDisplay(stats.getStatPoints(), stats.getLevel(), stats.getExp(), statsManager.getRequiredExp(stats.getLevel())));

        // Row 2: 灰色玻璃板
        ItemStack glassPane = createGlassPane();
        for (int slot : GLASS_ROW_2) {
            gui.setItem(slot, glassPane);
        }

        // Row 3: 增加 1 點按鈕
        gui.setItem(18, createAddButton(Material.DIAMOND, "增加 1 點 Strength", 1));
        gui.setItem(19, createAddButton(Material.DIAMOND, "增加 1 點 Magic", 1));
        gui.setItem(20, createAddButton(Material.DIAMOND, "增加 1 點 Agility", 1));
        gui.setItem(21, createAddButton(Material.DIAMOND, "增加 1 點 Vitality", 1));
        gui.setItem(22, createAddButton(Material.DIAMOND, "增加 1 點 Defense", 1));
        gui.setItem(23, createAddButton(Material.DIAMOND, "增加 1 點 Spirit", 1));

        // Row 4: 灰色玻璃板
        for (int slot : GLASS_ROW_4) {
            gui.setItem(slot, glassPane);
        }

        // Row 5: 增加 5 點按鈕
        gui.setItem(36, createAddButton(Material.DIAMOND, "增加 5 點 Strength", 5));
        gui.setItem(37, createAddButton(Material.DIAMOND, "增加 5 點 Magic", 5));
        gui.setItem(38, createAddButton(Material.DIAMOND, "增加 5 點 Agility", 5));
        gui.setItem(39, createAddButton(Material.DIAMOND, "增加 5 點 Vitality", 5));
        gui.setItem(40, createAddButton(Material.DIAMOND, "增加 5 點 Defense", 5));
        gui.setItem(41, createAddButton(Material.DIAMOND, "增加 5 點 Spirit", 5));

        player.openInventory(gui);
    }

    /**
     * 創建屬性顯示物品
     */
    private ItemStack createStatDisplay(Material material, String name, int baseValue, int equipmentBonus, String... descriptions) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);

            List<String> lore = new ArrayList<>();

            // 顯示基礎值
            lore.add(ChatColor.GRAY + "基礎: " + ChatColor.WHITE + baseValue);

            // 顯示裝備加成
            if (equipmentBonus > 0) {
                lore.add(ChatColor.GRAY + "裝備: " + ChatColor.GREEN + "+" + equipmentBonus);
            } else {
                lore.add(ChatColor.GRAY + "裝備: " + ChatColor.DARK_GRAY + "+0");
            }

            // 顯示總計
            int total = baseValue + equipmentBonus;
            lore.add(ChatColor.YELLOW + "總計: " + ChatColor.AQUA + "" + ChatColor.BOLD + total);

            lore.add("");
            for (String desc : descriptions) {
                lore.add(ChatColor.GRAY + desc);
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 創建屬性點數顯示物品
     */
    private ItemStack createStatPointsDisplay(int statPoints, int level, long exp, long requiredExp) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "▣ 玩家資訊 ▣");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GOLD + "等級: " + ChatColor.WHITE + level);
            lore.add(ChatColor.AQUA + "經驗: " + ChatColor.WHITE + exp + " / " + requiredExp);
            lore.add("");
            lore.add(ChatColor.GREEN + "可用屬性點數: " + ChatColor.YELLOW + statPoints);
            lore.add("");
            lore.add(ChatColor.GRAY + "點擊下方按鈕來分配屬性點數");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 創建灰色玻璃板
     */
    private ItemStack createGlassPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 創建增加按鈕
     */
    private ItemStack createAddButton(Material material, String name, int amount) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + name);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "點擊增加 " + amount + " 點");
            lore.add(ChatColor.RED + "消耗: " + amount + " 屬性點數");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 處理 GUI 點擊事件
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 檢查是否為屬性 GUI
        if (!event.getView().getTitle().equals(GUI_TITLE)) {
            return;
        }

        event.setCancelled(true); // 防止拿取物品

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 45) {
            return;
        }

        // 處理增加 1 點按鈕
        if (isInArray(slot, ADD_1_SLOTS)) {
            handleStatIncrease(player, slot, 1, ADD_1_SLOTS);
        }
        // 處理增加 5 點按鈕
        else if (isInArray(slot, ADD_5_SLOTS)) {
            handleStatIncrease(player, slot, 5, ADD_5_SLOTS);
        }
    }

    /**
     * 處理屬性增加
     */
    private void handleStatIncrease(Player player, int slot, int amount, int[] slotArray) {
        // 找出是哪個屬性（0=STR, 1=MAG, 2=AGI, 3=VIT, 4=DEF）
        int statIndex = -1;
        for (int i = 0; i < slotArray.length; i++) {
            if (slotArray[i] == slot) {
                statIndex = i;
                break;
            }
        }

        if (statIndex == -1) {
            return;
        }

        // 檢查玩家是否有足夠的屬性點數
        PlayerStats stats = statsManager.getStats(player);

        if (stats.getStatPoints() < amount) {
            player.sendMessage(ChatColor.RED + "❌ 屬性點數不足！需要 " + amount + " 點，你只有 " + stats.getStatPoints() + " 點");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String statName = "";

        switch (statIndex) {
            case 0 -> {
                stats.setStrength(stats.getStrength() + amount);
                statName = "Strength";
            }
            case 1 -> {
                stats.setMagic(stats.getMagic() + amount);
                statName = "Magic";
            }
            case 2 -> {
                stats.setAgility(stats.getAgility() + amount);
                statName = "Agility";
            }
            case 3 -> {
                stats.setVitality(stats.getVitality() + amount);
                statName = "Vitality";
                // 更新最大血量
                statsManager.updateMaxHealth(player);
            }
            case 4 -> {
                stats.setDefense(stats.getDefense() + amount);
                statName = "Defense";
            }
            case 5 -> {
                stats.setSpirit(stats.getSpirit() + amount);
                statName = "Spirit";
            }
        }

        // 扣除屬性點數
        stats.setStatPoints(stats.getStatPoints() - amount);

        // 儲存數據
        statsManager.saveStats(player);

        // 發送訊息
        player.sendMessage(ChatColor.GREEN + "✓ " + statName + " +" + amount + "！ (剩餘點數: " + stats.getStatPoints() + ")");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        // 刷新 GUI
        openStatsGUI(player);
    }

    /**
     * 檢查值是否在陣列中
     */
    private boolean isInArray(int value, int[] array) {
        for (int i : array) {
            if (i == value) {
                return true;
            }
        }
        return false;
    }
}


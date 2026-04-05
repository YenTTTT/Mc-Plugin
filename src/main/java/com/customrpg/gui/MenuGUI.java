package com.customrpg.gui;

import com.customrpg.CustomRPG;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * MenuGUI — 主選單 GUI
 *
 * 3 排 (27 格) 介面，排列 7 個功能按鈕：
 *
 *  Slot 10 → 角色屬性      (/rpg gui)
 *  Slot 11 → 角色資訊      (/stat)
 *  Slot 12 → 天賦系統      (/talent)
 *  Slot 13 → 種族系統      (/race)
 *  Slot 14 → 裝備系統      (/equipment)
 *  Slot 15 → 保護區域 (OP) (/protectarea)
 *  Slot 16 → 怪物區域 (OP) (/setmob)
 *
 *  外圍用灰色玻璃板填滿
 */
public class MenuGUI implements Listener {

    private final CustomRPG plugin;
    public static final String GUI_TITLE = ChatColor.DARK_PURPLE + "✦ " + ChatColor.BOLD + "主選單" + ChatColor.DARK_PURPLE + " ✦";

    public MenuGUI(CustomRPG plugin) {
        this.plugin = plugin;
    }

    // ===== 開啟選單 =====

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

        // 填滿灰色玻璃板
        ItemStack filler = createFiller();
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        // ---- 功能按鈕 ----

        // Slot 10 — 角色屬性 (/rpg gui)
        gui.setItem(10, createButton(
                Material.DIAMOND_SWORD,
                ChatColor.GREEN + "" + ChatColor.BOLD + "⚔ 角色屬性",
                List.of(
                        "",
                        ChatColor.GRAY + "查看並分配你的角色屬性點",
                        ChatColor.GRAY + "包含力量、魔法、敏捷等",
                        "",
                        ChatColor.YELLOW + "▶ 點擊開啟"
                )
        ));

        // Slot 11 — 角色資訊 (/stat)
        gui.setItem(11, createButton(
                Material.BOOK,
                ChatColor.AQUA + "" + ChatColor.BOLD + "📋 角色資訊",
                List.of(
                        "",
                        ChatColor.GRAY + "在聊天欄顯示完整的角色資訊",
                        ChatColor.GRAY + "包含所有屬性、副屬性等",
                        "",
                        ChatColor.YELLOW + "▶ 點擊查看"
                )
        ));

        // Slot 12 — 天賦系統 (/talent)
        gui.setItem(12, createButton(
                Material.NETHER_STAR,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ 天賦系統",
                List.of(
                        "",
                        ChatColor.GRAY + "選擇天賦流派、分配天賦點",
                        ChatColor.GRAY + "解鎖強力技能與被動能力",
                        "",
                        ChatColor.YELLOW + "▶ 點擊開啟"
                )
        ));

        // Slot 13 — 種族系統 (/race)
        gui.setItem(13, createButton(
                Material.DRAGON_HEAD,
                ChatColor.GOLD + "" + ChatColor.BOLD + "🛡 種族系統",
                List.of(
                        "",
                        ChatColor.GRAY + "選擇你的種族",
                        ChatColor.GRAY + "不同種族有不同的屬性加成",
                        "",
                        ChatColor.YELLOW + "▶ 點擊開啟"
                )
        ));

        // Slot 14 — 裝備系統 (/equipment)
        gui.setItem(14, createButton(
                Material.DIAMOND_CHESTPLATE,
                ChatColor.BLUE + "" + ChatColor.BOLD + "🔷 裝備系統",
                List.of(
                        "",
                        ChatColor.GRAY + "管理你的裝備、符文與套裝",
                        ChatColor.GRAY + "強化裝備獲取更強屬性",
                        "",
                        ChatColor.YELLOW + "▶ 點擊開啟"
                )
        ));

        // Slot 15 — 保護區域 (/protectarea) (OP)
        if (player.hasPermission("customrpg.admin")) {
            gui.setItem(15, createButton(
                    Material.SHIELD,
                    ChatColor.RED + "" + ChatColor.BOLD + "🛑 保護區域",
                    List.of(
                            "",
                            ChatColor.GRAY + "取得保護區域選取工具",
                            ChatColor.GRAY + "設定區域後可禁止怪物生成",
                            "",
                            ChatColor.DARK_RED + "⚠ 管理員專用",
                            "",
                            ChatColor.YELLOW + "▶ 點擊取得工具"
                    )
            ));
        }

        // Slot 16 — 怪物區域 (/setmob) (OP)
        if (player.hasPermission("customrpg.admin")) {
            gui.setItem(16, createButton(
                    Material.SPAWNER,
                    ChatColor.RED + "" + ChatColor.BOLD + "👾 怪物區域",
                    List.of(
                            "",
                            ChatColor.GRAY + "取得怪物區域選取工具",
                            ChatColor.GRAY + "設定安全區域與怪物等級縮放",
                            "",
                            ChatColor.DARK_RED + "⚠ 管理員專用",
                            "",
                            ChatColor.YELLOW + "▶ 點擊取得工具"
                    )
            ));
        }

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    // ===== 處理 GUI 點擊 =====

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        player.closeInventory();

        switch (slot) {
            case 10 -> {
                // 角色屬性 — 直接開啟 StatsGUI
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    com.customrpg.gui.StatsGUI statsGUI = getStatsGUI();
                    if (statsGUI != null) {
                        statsGUI.openStatsGUI(player);
                    } else {
                        player.performCommand("rpg gui");
                    }
                }, 1L);
            }
            case 11 -> {
                // 角色資訊 — 執行 /stat
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                player.performCommand("stat");
            }
            case 12 -> {
                // 天賦系統 — 直接開啟 TalentMainMenuGUI
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    com.customrpg.gui.TalentMainMenuGUI talentGUI = plugin.getTalentMainMenuGUI();
                    if (talentGUI != null) {
                        talentGUI.open(player);
                    } else {
                        player.performCommand("talent");
                    }
                }, 1L);
            }
            case 13 -> {
                // 種族系統 — 直接開啟 RaceGUI
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    com.customrpg.gui.RaceGUI raceGUI = plugin.getRaceGUI();
                    if (raceGUI != null) {
                        raceGUI.open(player);
                    } else {
                        player.performCommand("race");
                    }
                }, 1L);
            }
            case 14 -> {
                // 裝備系統 — 直接開啟 EquipmentGUI
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    com.customrpg.gui.EquipmentGUI eqGUI = plugin.getEquipmentGUI();
                    if (eqGUI != null) {
                        eqGUI.openEquipmentGUI(player);
                    } else {
                        player.performCommand("equipment open");
                    }
                }, 1L);
            }
            case 15 -> {
                // 保護區域 — 執行 /protectarea (給工具)
                if (player.hasPermission("customrpg.admin")) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                    player.performCommand("protectarea");
                } else {
                    player.sendMessage(ChatColor.RED + "你沒有權限使用此功能！");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                }
            }
            case 16 -> {
                // 怪物區域 — 執行 /setmob (給工具)
                if (player.hasPermission("customrpg.admin")) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                    player.performCommand("setmob");
                } else {
                    player.sendMessage(ChatColor.RED + "你沒有權限使用此功能！");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                }
            }
        }
    }

    // ===== Helper =====

    private StatsGUI getStatsGUI() {
        return plugin.getStatsGUI();
    }

    private ItemStack createButton(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        return filler;
    }
}



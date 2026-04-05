package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.gui.MenuGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * /menu 指令
 *
 * /menu          → 開啟主選單 GUI
 * /menu getitem  → 給予玩家主選單指南針
 */
public class MenuCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;
    private final MenuGUI menuGUI;

    /**
     * PDC key，用來標記主選單指南針
     */
    public static final String COMPASS_KEY = "menu_compass";

    public MenuCommand(CustomRPG plugin, MenuGUI menuGUI) {
        this.plugin = plugin;
        this.menuGUI = menuGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此指令只能由玩家使用！");
            return true;
        }

        if (args.length == 0) {
            // /menu → 開啟 GUI
            menuGUI.open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "getitem" -> giveCompass(player);
            default -> {
                player.sendMessage(ChatColor.GOLD + "════════ 主選單指令 ════════");
                player.sendMessage(ChatColor.YELLOW + "/menu" + ChatColor.GRAY + " — 開啟主選單");
                player.sendMessage(ChatColor.YELLOW + "/menu getitem" + ChatColor.GRAY + " — 取得選單指南針");
                player.sendMessage(ChatColor.GOLD + "═══════════════════════════");
            }
        }
        return true;
    }

    /**
     * 給玩家一把帶 PDC 標記的指南針
     */
    private void giveCompass(Player player) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "✦ 主選單 ✦");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "右鍵點擊開啟" + ChatColor.YELLOW + "主選單");
            lore.add(ChatColor.GRAY + "快速存取所有 RPG 功能");
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "CustomRPG");
            meta.setLore(lore);

            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            // 寫入 PDC 標記
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, COMPASS_KEY),
                    PersistentDataType.BOOLEAN, true
            );
            compass.setItemMeta(meta);
        }

        player.getInventory().addItem(compass);
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  ✦ 已取得 " + ChatColor.GOLD + "主選單指南針");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "  手持指南針按 " + ChatColor.GREEN + "右鍵" + ChatColor.WHITE + " 即可開啟主選單");
        player.sendMessage(ChatColor.WHITE + "  或輸入 " + ChatColor.GREEN + "/menu" + ChatColor.WHITE + " 開啟");
        player.sendMessage(ChatColor.GOLD + "════════════════════════════════");
        player.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("getitem");
            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.startsWith(input));
        }
        return completions;
    }
}


package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.managers.ProtectionAreaManager;
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
 * /protectarea 指令
 * - /protectarea           → 取得選區工具（特殊木鋤）
 * - /protectarea <name>    → 用選取的兩點建立保護區域
 * - /protectarea list      → 列出所有保護區域
 * - /protectarea remove <name> → 移除保護區域
 * - /protectarea reload    → 重新載入
 */
public class ProtectAreaCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;
    private final ProtectionAreaManager areaManager;
    public static final String WAND_KEY = "protection_area_wand";

    public ProtectAreaCommand(CustomRPG plugin, ProtectionAreaManager areaManager) {
        this.plugin = plugin;
        this.areaManager = areaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此指令只能由玩家使用！");
            return true;
        }

        // /protectarea → 給予選區工具
        if (args.length == 0) {
            giveWand(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list" -> listAreas(player);
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "用法: /protectarea remove <名稱>");
                    return true;
                }
                removeArea(player, args[1]);
            }
            case "reload" -> {
                areaManager.reload();
                player.sendMessage(ChatColor.GREEN + "✓ 保護區域已重新載入！");
            }
            case "help" -> showHelp(player);
            default -> {
                // /protectarea <name> → 建立保護區域
                createArea(player, args[0]);
            }
        }
        return true;
    }

    private void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.WOODEN_HOE);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "✦ 區域選取工具 ✦");
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.YELLOW + "左鍵點擊地板" + ChatColor.GRAY + " → 設定座標 1");
            lore.add(ChatColor.YELLOW + "右鍵點擊地板" + ChatColor.GRAY + " → 設定座標 2");
            lore.add("");
            lore.add(ChatColor.GREEN + "選好後輸入 /protectarea <名稱>");
            lore.add(ChatColor.GREEN + "即可建立保護區域");
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "保護區域內不會生成怪物");
            meta.setLore(lore);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, WAND_KEY),
                    PersistentDataType.BOOLEAN, true
            );
            wand.setItemMeta(meta);
        }
        player.getInventory().addItem(wand);
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  ✦ 已取得" + ChatColor.GOLD + "區域選取工具");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "  左鍵點擊 → 設定座標 1");
        player.sendMessage(ChatColor.WHITE + "  右鍵點擊 → 設定座標 2");
        player.sendMessage(ChatColor.WHITE + "  然後輸入 " + ChatColor.GREEN + "/protectarea <名稱>");
        player.sendMessage(ChatColor.GOLD + "════════════════════════════════");
        player.sendMessage("");
    }

    private void createArea(Player player, String name) {
        ProtectionAreaManager.Selection sel = areaManager.getSelection(player.getUniqueId());
        if (sel == null || !sel.isComplete()) {
            player.sendMessage(ChatColor.RED + "✗ 請先用選區工具設定兩個座標！");
            player.sendMessage(ChatColor.GRAY + "  左鍵點擊 = 座標1，右鍵點擊 = 座標2");
            return;
        }

        // 檢查名稱是否已存在
        if (areaManager.getArea(name) != null) {
            player.sendMessage(ChatColor.RED + "✗ 名稱 '" + name + "' 已存在！請換一個名稱。");
            return;
        }

        boolean success = areaManager.createArea(player.getUniqueId(), name);
        if (success) {
            ProtectionAreaManager.ProtectedArea area = areaManager.getArea(name);
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✓ 保護區域 '" + ChatColor.GOLD + name + ChatColor.GREEN + "' 已建立！");
            if (area != null) {
                player.sendMessage(ChatColor.GRAY + "  世界: " + ChatColor.WHITE + area.worldName);
                player.sendMessage(ChatColor.GRAY + "  範圍: " + ChatColor.AQUA
                        + "(" + area.minX + ", " + area.minY + ", " + area.minZ + ")"
                        + ChatColor.GRAY + " → " + ChatColor.AQUA
                        + "(" + area.maxX + ", " + area.maxY + ", " + area.maxZ + ")");
            }
            player.sendMessage(ChatColor.YELLOW + "  此區域內將不會生成任何怪物。");
            player.sendMessage("");
        } else {
            player.sendMessage(ChatColor.RED + "✗ 建立失敗！請確認兩個座標在同一個世界。");
        }
    }

    private void removeArea(Player player, String name) {
        if (areaManager.removeArea(name)) {
            player.sendMessage(ChatColor.GREEN + "✓ 已移除保護區域: " + ChatColor.GOLD + name);
        } else {
            player.sendMessage(ChatColor.RED + "✗ 找不到保護區域: " + name);
        }
    }

    private void listAreas(Player player) {
        var areas = areaManager.getAllAreas();
        if (areas.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "目前沒有任何保護區域。");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "═══════ 保護區域列表 ═══════");
        for (var area : areas) {
            player.sendMessage(ChatColor.YELLOW + "▸ " + ChatColor.WHITE + area.name
                    + ChatColor.GRAY + " [" + area.worldName + "] "
                    + ChatColor.AQUA + "(" + area.minX + "," + area.minY + "," + area.minZ + ")"
                    + ChatColor.GRAY + " → "
                    + ChatColor.AQUA + "(" + area.maxX + "," + area.maxY + "," + area.maxZ + ")");
        }
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════");
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "═══════ 保護區域指令 ═══════");
        player.sendMessage(ChatColor.YELLOW + "/protectarea" + ChatColor.GRAY + " — 取得選區工具");
        player.sendMessage(ChatColor.YELLOW + "/protectarea <名稱>" + ChatColor.GRAY + " — 建立保護區域");
        player.sendMessage(ChatColor.YELLOW + "/protectarea list" + ChatColor.GRAY + " — 列出所有區域");
        player.sendMessage(ChatColor.YELLOW + "/protectarea remove <名稱>" + ChatColor.GRAY + " — 移除區域");
        player.sendMessage(ChatColor.YELLOW + "/protectarea reload" + ChatColor.GRAY + " — 重新載入");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("list");
            completions.add("remove");
            completions.add("reload");
            completions.add("help");
            // 也列出現有區域名稱方便直接輸入
            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            for (var area : areaManager.getAllAreas()) {
                completions.add(area.name);
            }
            String input = args[1].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        }
        return completions;
    }
}


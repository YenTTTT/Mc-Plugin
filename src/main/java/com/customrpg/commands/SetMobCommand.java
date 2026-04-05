package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.managers.ZoneManager;
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
 * /setmob 指令 — 怪物區域管理
 *
 * /setmob                          → 取得選區工具 (烈風棒 BREEZE_ROD)
 * /setmob <minLevel> <maxLevel> <zoneId>  → 用選取的兩點建立怪物區域
 * /setmob list                     → 列出所有區域
 * /setmob remove <zoneId>          → 移除區域
 * /setmob info <zoneId>            → 查看區域資訊
 * /setmob reload                   → 重新載入
 * /setmob help                     → 說明
 */
public class SetMobCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;
    private final ZoneManager zoneManager;
    public static final String WAND_KEY = "mob_zone_wand";

    public SetMobCommand(CustomRPG plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此指令只能由玩家使用！");
            return true;
        }

        // /setmob → 給予選區工具
        if (args.length == 0) {
            giveWand(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list" -> listZones(player);
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "用法: /setmob remove <zoneId>");
                    return true;
                }
                removeZone(player, args[1]);
            }
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "用法: /setmob info <zoneId>");
                    return true;
                }
                showZoneInfo(player, args[1]);
            }
            case "reload" -> {
                zoneManager.reload();
                player.sendMessage(ChatColor.GREEN + "✓ 怪物區域已重新載入！共 " + zoneManager.getZoneCount() + " 個區域");
            }
            case "help" -> showHelp(player);
            case "tier" -> showCurrentTier(player);
            default -> {
                // /setmob <minLevel> <maxLevel> <zoneId>
                if (args.length >= 3) {
                    try {
                        int minLevel = Integer.parseInt(args[0]);
                        int maxLevel = Integer.parseInt(args[1]);
                        String zoneId = args[2];
                        createZone(player, zoneId, minLevel, maxLevel);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "用法: /setmob <最小等級> <最大等級> <區域ID>");
                        player.sendMessage(ChatColor.GRAY + "範例: /setmob 1 5 area1");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "未知指令！輸入 /setmob help 查看說明。");
                }
            }
        }
        return true;
    }

    /**
     * 給予選區工具 (烈風棒 BREEZE_ROD)
     */
    private void giveWand(Player player) {
        // 嘗試使用 BREEZE_ROD，若不存在則使用 BLAZE_ROD
        Material wandMaterial;
        try {
            wandMaterial = Material.valueOf("BREEZE_ROD");
        } catch (IllegalArgumentException e) {
            wandMaterial = Material.BLAZE_ROD;
        }

        ItemStack wand = new ItemStack(wandMaterial);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "✦ 怪物區域選取工具 ✦");
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.YELLOW + "左鍵點擊地板" + ChatColor.GRAY + " → 設定座標 1");
            lore.add(ChatColor.YELLOW + "右鍵點擊地板" + ChatColor.GRAY + " → 設定座標 2");
            lore.add("");
            lore.add(ChatColor.GREEN + "選好後輸入:");
            lore.add(ChatColor.WHITE + "  /setmob <最小等級> <最大等級> <區域ID>");
            lore.add(ChatColor.GRAY + "  範例: /setmob 1 5 area1");
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "區域內不會生成怪物");
            lore.add(ChatColor.DARK_GRAY + "區域外距離越遠怪物越強");
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
        player.sendMessage(ChatColor.AQUA + "════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "  ✦ 已取得" + ChatColor.AQUA + "怪物區域選取工具");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "  左鍵點擊 → 設定座標 1");
        player.sendMessage(ChatColor.WHITE + "  右鍵點擊 → 設定座標 2");
        player.sendMessage(ChatColor.WHITE + "  然後輸入 " + ChatColor.GREEN + "/setmob <最小等級> <最大等級> <區域ID>");
        player.sendMessage(ChatColor.GRAY + "  範例: /setmob 1 5 area1");
        player.sendMessage(ChatColor.AQUA + "════════════════════════════════");
        player.sendMessage("");
    }

    /**
     * 建立怪物區域
     */
    private void createZone(Player player, String zoneId, int minLevel, int maxLevel) {
        if (minLevel < 1) {
            player.sendMessage(ChatColor.RED + "✗ 最小等級不能小於 1！");
            return;
        }
        if (maxLevel < minLevel) {
            player.sendMessage(ChatColor.RED + "✗ 最大等級不能小於最小等級！");
            return;
        }

        ZoneManager.Selection sel = zoneManager.getSelection(player.getUniqueId());
        if (sel == null || !sel.isComplete()) {
            player.sendMessage(ChatColor.RED + "✗ 請先用選區工具設定兩個座標！");
            player.sendMessage(ChatColor.GRAY + "  左鍵點擊 = 座標1，右鍵點擊 = 座標2");
            return;
        }

        // 檢查名稱是否已存在
        if (zoneManager.getZone(zoneId) != null) {
            player.sendMessage(ChatColor.RED + "✗ 區域 ID '" + zoneId + "' 已存在！請換一個名稱。");
            return;
        }

        boolean success = zoneManager.createZone(player.getUniqueId(), zoneId, minLevel, maxLevel);
        if (success) {
            ZoneManager.MobZone zone = zoneManager.getZone(zoneId);
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✓ 怪物區域 '" + ChatColor.AQUA + zoneId + ChatColor.GREEN + "' 已建立！");
            if (zone != null) {
                player.sendMessage(ChatColor.GRAY + "  世界: " + ChatColor.WHITE + zone.worldName);
                player.sendMessage(ChatColor.GRAY + "  範圍: " + ChatColor.AQUA
                        + "(" + zone.minX + ", " + zone.minY + ", " + zone.minZ + ")"
                        + ChatColor.GRAY + " → " + ChatColor.AQUA
                        + "(" + zone.maxX + ", " + zone.maxY + ", " + zone.maxZ + ")");
                player.sendMessage(ChatColor.GRAY + "  等級範圍: " + ChatColor.WHITE + "Lv." + zone.minLevel + " ~ Lv." + zone.maxLevel);
                player.sendMessage(ChatColor.GRAY + "  距離步進: " + ChatColor.WHITE + zone.radiusStep + " 格/tier");
            }
            player.sendMessage(ChatColor.YELLOW + "  區域內不會生成怪物。");
            player.sendMessage(ChatColor.YELLOW + "  離開區域後，每 " + 150 + " 格怪物等級提升一個等級。");
            player.sendMessage("");
        } else {
            player.sendMessage(ChatColor.RED + "✗ 建立失敗！請確認兩個座標在同一個世界。");
        }
    }

    /**
     * 移除區域
     */
    private void removeZone(Player player, String zoneId) {
        if (zoneManager.removeZone(zoneId)) {
            player.sendMessage(ChatColor.GREEN + "✓ 已移除怪物區域: " + ChatColor.AQUA + zoneId);
        } else {
            player.sendMessage(ChatColor.RED + "✗ 找不到怪物區域: " + zoneId);
        }
    }

    /**
     * 查看區域資訊
     */
    private void showZoneInfo(Player player, String zoneId) {
        ZoneManager.MobZone zone = zoneManager.getZone(zoneId);
        if (zone == null) {
            player.sendMessage(ChatColor.RED + "✗ 找不到怪物區域: " + zoneId);
            return;
        }

        player.sendMessage(ChatColor.AQUA + "═══════ 怪物區域資訊 ═══════");
        player.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + zone.name);
        player.sendMessage(ChatColor.YELLOW + "世界: " + ChatColor.WHITE + zone.worldName);
        player.sendMessage(ChatColor.YELLOW + "範圍: " + ChatColor.AQUA
                + "(" + zone.minX + ", " + zone.minY + ", " + zone.minZ + ")"
                + ChatColor.GRAY + " → " + ChatColor.AQUA
                + "(" + zone.maxX + ", " + zone.maxY + ", " + zone.maxZ + ")");
        player.sendMessage(ChatColor.YELLOW + "面積: " + ChatColor.WHITE + zone.getArea() + " 方塊");
        player.sendMessage(ChatColor.YELLOW + "等級範圍: " + ChatColor.WHITE + "Lv." + zone.minLevel + " ~ Lv." + zone.maxLevel);
        player.sendMessage(ChatColor.YELLOW + "距離步進: " + ChatColor.WHITE + zone.radiusStep + " 格/tier");
        player.sendMessage("");

        // 顯示各 tier 等級
        player.sendMessage(ChatColor.YELLOW + "等級縮放:");
        for (int tier = 1; tier <= 5; tier++) {
            int levelRange = zone.maxLevel - zone.minLevel;
            int levelMin = zone.minLevel + ((tier - 1) * levelRange);
            int levelMax = zone.maxLevel + ((tier - 1) * levelRange);
            String tierName = zoneManager.getTierDisplayName(tier);
            player.sendMessage(ChatColor.GRAY + "  " + tierName
                    + ChatColor.GRAY + " → Lv." + ChatColor.WHITE + levelMin + "~" + levelMax
                    + ChatColor.DARK_GRAY + " (距離 " + ((tier - 1) * zone.radiusStep) + "~" + (tier * zone.radiusStep) + "格)");
        }
        player.sendMessage(ChatColor.AQUA + "═══════════════════════════");
    }

    /**
     * 列出所有區域
     */
    private void listZones(Player player) {
        var zones = zoneManager.getAllZones();
        if (zones.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "目前沒有任何怪物區域。");
            player.sendMessage(ChatColor.GRAY + "使用 /setmob 取得選區工具來建立區域。");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "═══════ 怪物區域列表 ═══════");
        for (var zone : zones) {
            player.sendMessage(ChatColor.YELLOW + "▸ " + ChatColor.WHITE + zone.name
                    + ChatColor.GRAY + " [" + zone.worldName + "] "
                    + ChatColor.AQUA + "(" + zone.minX + "," + zone.minZ + ")"
                    + ChatColor.GRAY + " → "
                    + ChatColor.AQUA + "(" + zone.maxX + "," + zone.maxZ + ")"
                    + ChatColor.GRAY + " Lv." + zone.minLevel + "~" + zone.maxLevel);
        }
        player.sendMessage(ChatColor.AQUA + "═══════════════════════════");
    }

    /**
     * 顯示玩家當前所在 tier
     */
    private void showCurrentTier(Player player) {
        int tier = zoneManager.calculateTier(player.getLocation());
        String tierName = zoneManager.getTierDisplayName(tier);
        int[] levelRange = zoneManager.calculateMobLevelRange(player.getLocation());
        double dist = zoneManager.getDistanceToNearestZoneBorder(player.getLocation());

        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "═══════ 目前位置資訊 ═══════");
        player.sendMessage(ChatColor.YELLOW + "區域等級: " + tierName);
        player.sendMessage(ChatColor.YELLOW + "怪物等級: " + ChatColor.WHITE + "Lv." + levelRange[0] + " ~ Lv." + levelRange[1]);
        if (dist >= 0) {
            player.sendMessage(ChatColor.YELLOW + "距最近安全區: " + ChatColor.WHITE + String.format("%.1f", dist) + " 格");
        } else {
            player.sendMessage(ChatColor.YELLOW + "距最近安全區: " + ChatColor.GRAY + "無安全區域");
        }
        player.sendMessage(ChatColor.AQUA + "═══════════════════════════");
        player.sendMessage("");
    }

    /**
     * 顯示說明
     */
    private void showHelp(Player player) {
        player.sendMessage(ChatColor.AQUA + "═══════ 怪物區域指令 ═══════");
        player.sendMessage(ChatColor.YELLOW + "/setmob" + ChatColor.GRAY + " — 取得選區工具");
        player.sendMessage(ChatColor.YELLOW + "/setmob <最小等級> <最大等級> <區域ID>" + ChatColor.GRAY + " — 建立區域");
        player.sendMessage(ChatColor.YELLOW + "/setmob list" + ChatColor.GRAY + " — 列出所有區域");
        player.sendMessage(ChatColor.YELLOW + "/setmob info <區域ID>" + ChatColor.GRAY + " — 查看區域資訊");
        player.sendMessage(ChatColor.YELLOW + "/setmob remove <區域ID>" + ChatColor.GRAY + " — 移除區域");
        player.sendMessage(ChatColor.YELLOW + "/setmob tier" + ChatColor.GRAY + " — 查看目前位置 tier");
        player.sendMessage(ChatColor.YELLOW + "/setmob reload" + ChatColor.GRAY + " — 重新載入");
        player.sendMessage(ChatColor.AQUA + "═══════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("list");
            completions.add("remove");
            completions.add("info");
            completions.add("tier");
            completions.add("reload");
            completions.add("help");
            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("info"))) {
            for (var zone : zoneManager.getAllZones()) {
                completions.add(zone.name);
            }
            String input = args[1].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        }
        return completions;
    }
}



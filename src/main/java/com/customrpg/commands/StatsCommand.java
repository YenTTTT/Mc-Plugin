package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.gui.StatsGUI;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.players.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * StatsCommand - 管理玩家數據的指令
 *
 * 用法：
 * - /rpg stats [玩家] - 查看玩家數據
 * - /rpg setstat <玩家> <屬性> <數值> - 設定玩家數據
 * - /rpg reload - 重新載入玩家數據
 *
 * 屬性：strength, magic, agility, vitality, defense
 */
public class StatsCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;
    private final PlayerStatsManager statsManager;
    private final StatsGUI statsGUI;

    public StatsCommand(CustomRPG plugin, PlayerStatsManager statsManager, StatsGUI statsGUI) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.statsGUI = statsGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "gui" -> {
                return handleGUI(sender);
            }
            case "stats" -> {
                return handleStats(sender, args);
            }
            case "setstat" -> {
                return handleSetStat(sender, args);
            }
            case "reload" -> {
                return handleReload(sender, args);
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    /**
     * 開啟屬性 GUI
     * 用法: /rpg gui
     */
    private boolean handleGUI(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用 GUI！");
            return true;
        }

        statsGUI.openStatsGUI(player);
        return true;
    }

    /**
     * 查看玩家數據
     * 用法: /rpg stats [玩家]
     */
    private boolean handleStats(CommandSender sender, String[] args) {
        Player target;

        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "找不到玩家: " + args[1]);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "控制台必須指定玩家名稱！");
                return true;
            }
            target = (Player) sender;
        }

        PlayerStats stats = statsManager.getStats(target);

        sender.sendMessage(ChatColor.GOLD + "========== " + target.getName() + " 的數據 ==========");
        sender.sendMessage(ChatColor.YELLOW + "物理攻擊 (Strength): " + ChatColor.WHITE + stats.getStrength());
        sender.sendMessage(ChatColor.AQUA + "魔法攻擊 (Magic): " + ChatColor.WHITE + stats.getMagic());
        sender.sendMessage(ChatColor.GREEN + "敏捷 (Agility): " + ChatColor.WHITE + stats.getAgility());
        sender.sendMessage(ChatColor.RED + "生命力 (Vitality): " + ChatColor.WHITE + stats.getVitality());
        sender.sendMessage(ChatColor.GRAY + "防禦 (Defense): " + ChatColor.WHITE + stats.getDefense());
        sender.sendMessage(ChatColor.GOLD + "=====================================");

        return true;
    }

    /**
     * 設定玩家數據
     * 用法: /rpg setstat <玩家> <屬性> <數值>
     */
    private boolean handleSetStat(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customrpg.admin")) {
            sender.sendMessage(ChatColor.RED + "你沒有權限使用此指令！");
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "用法: /rpg setstat <玩家> <屬性> <數值>");
            sender.sendMessage(ChatColor.GRAY + "屬性: strength, magic, agility, vitality, defense");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "找不到玩家: " + args[1]);
            return true;
        }

        String statName = args[2].toLowerCase();
        int value;

        try {
            value = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "無效的數值: " + args[3]);
            return true;
        }

        if (value < 0) {
            sender.sendMessage(ChatColor.RED + "數值不能為負數！");
            return true;
        }

        // 設定屬性
        statsManager.setStat(target.getUniqueId(), statName, value);

        // 如果是 Vitality，更新最大血量
        if (statName.equals("vitality") || statName.equals("vit")) {
            statsManager.updateMaxHealth(target);
        }

        sender.sendMessage(ChatColor.GREEN + "✓ 已設定 " + target.getName() + " 的 " + statName + " 為 " + value);
        target.sendMessage(ChatColor.GREEN + "你的 " + statName + " 已被設定為 " + value);

        return true;
    }

    /**
     * 重新載入玩家數據
     * 用法: /rpg reload [玩家]
     */
    private boolean handleReload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customrpg.admin")) {
            sender.sendMessage(ChatColor.RED + "你沒有權限使用此指令！");
            return true;
        }

        if (args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "找不到玩家: " + args[1]);
                return true;
            }

            // 儲存並清除快取
            statsManager.saveStats(target);
            statsManager.unloadStats(target.getUniqueId());

            // 重新載入
            statsManager.getStats(target);
            statsManager.updateMaxHealth(target);

            sender.sendMessage(ChatColor.GREEN + "✓ 已重新載入 " + target.getName() + " 的數據");
        } else {
            // 重新載入所有線上玩家
            for (Player player : Bukkit.getOnlinePlayers()) {
                statsManager.saveStats(player);
                statsManager.unloadStats(player.getUniqueId());
                statsManager.getStats(player);
                statsManager.updateMaxHealth(player);
            }

            sender.sendMessage(ChatColor.GREEN + "✓ 已重新載入所有玩家的數據");
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== CustomRPG 指令 ==========");
        sender.sendMessage(ChatColor.YELLOW + "/rpg gui" + ChatColor.GRAY + " - 開啟屬性介面");
        sender.sendMessage(ChatColor.YELLOW + "/rpg stats [玩家]" + ChatColor.GRAY + " - 查看玩家數據");
        sender.sendMessage(ChatColor.YELLOW + "/rpg setstat <玩家> <屬性> <數值>" + ChatColor.GRAY + " - 設定玩家數據");
        sender.sendMessage(ChatColor.YELLOW + "/rpg reload [玩家]" + ChatColor.GRAY + " - 重新載入玩家數據");
        sender.sendMessage(ChatColor.GRAY + "屬性: strength, magic, agility, vitality, defense");
        sender.sendMessage(ChatColor.GOLD + "====================================");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("gui", "stats", "setstat", "reload"));
        } else if (args.length == 2) {
            // 玩家名稱補全
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setstat")) {
            // 屬性補全
            completions.addAll(Arrays.asList("strength", "magic", "agility", "vitality", "defense"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("setstat")) {
            // 數值範例
            completions.addAll(Arrays.asList("0", "10", "20", "50", "100"));
        }

        return completions;
    }
}


package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.managers.MobSpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MobSpawnCommand - 動態怪物生成系統的測試與管理指令
 *
 * 用法:
 *   /mobspawn status           - 顯示系統診斷資訊
 *   /mobspawn spawn [類型] [階級] - 強制在你前方生成一隻怪物
 *   /mobspawn clear            - 清除所有自動生成的怪物
 *   /mobspawn debug            - 切換 debug 日誌
 *   /mobspawn reload           - 重新載入配置
 *   /mobspawn help             - 顯示幫助
 */
public class MobSpawnCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;

    public MobSpawnCommand(CustomRPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MobSpawnManager manager = plugin.getMobSpawnManager();
        if (manager == null) {
            sender.sendMessage(ChatColor.RED + "MobSpawnManager 未初始化！");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
            case "info":
            case "s":
                return handleStatus(sender, manager);

            case "spawn":
            case "test":
                return handleSpawn(sender, args, manager);

            case "clear":
            case "clean":
                return handleClear(sender, manager);

            case "debug":
                return handleDebug(sender, manager);

            case "reload":
                return handleReload(sender, manager);

            case "help":
            default:
                showHelp(sender);
                return true;
        }
    }

    private boolean handleStatus(CommandSender sender, MobSpawnManager manager) {
        Player player = (sender instanceof Player) ? (Player) sender : null;
        List<String> lines = manager.getDebugStatus(player);
        for (String line : lines) {
            sender.sendMessage(line);
        }
        return true;
    }

    private boolean handleSpawn(CommandSender sender, String[] args, MobSpawnManager manager) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此指令只能由玩家執行！");
            return true;
        }

        String mobKey = args.length >= 2 ? args[1] : null;

        MobSpawnManager.MobTier tier = MobSpawnManager.MobTier.NORMAL;
        if (args.length >= 3) {
            try {
                tier = MobSpawnManager.MobTier.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "未知階級: " + args[2]);
                sender.sendMessage(ChatColor.YELLOW + "可用階級: NORMAL, ELITE, BOSS");
                return true;
            }
        }

        String result = manager.forceSpawn(player, mobKey, tier);
        sender.sendMessage(result);
        return true;
    }

    private boolean handleClear(CommandSender sender, MobSpawnManager manager) {
        int removed = manager.clearAllSpawnedMobs();
        sender.sendMessage(ChatColor.GREEN + "✓ 已清除 " + removed + " 隻自動生成的怪物");
        return true;
    }

    private boolean handleDebug(CommandSender sender, MobSpawnManager manager) {
        boolean newState = manager.toggleDebug();
        sender.sendMessage(ChatColor.YELLOW + "Debug 模式: " + (newState ? ChatColor.GREEN + "開啟" : ChatColor.RED + "關閉"));
        sender.sendMessage(ChatColor.GRAY + "開啟後會在伺服器 console 輸出詳細的生成日誌");
        return true;
    }

    private boolean handleReload(CommandSender sender, MobSpawnManager manager) {
        manager.reload();
        sender.sendMessage(ChatColor.GREEN + "✓ MobSpawnManager 配置已重新載入");
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========= 動態怪物生成系統 =========");
        sender.sendMessage(ChatColor.YELLOW + "/mobspawn status" + ChatColor.GRAY + " - 顯示系統診斷資訊");
        sender.sendMessage(ChatColor.YELLOW + "/mobspawn spawn [類型] [階級]" + ChatColor.GRAY + " - 強制生成怪物");
        sender.sendMessage(ChatColor.GRAY + "  例: /mobspawn spawn rotten_zombie ELITE");
        sender.sendMessage(ChatColor.GRAY + "  例: /mobspawn spawn (隨機類型)");
        sender.sendMessage(ChatColor.YELLOW + "/mobspawn clear" + ChatColor.GRAY + " - 清除所有自動生成的怪物");
        sender.sendMessage(ChatColor.YELLOW + "/mobspawn debug" + ChatColor.GRAY + " - 切換 debug 日誌");
        sender.sendMessage(ChatColor.YELLOW + "/mobspawn reload" + ChatColor.GRAY + " - 重新載入配置");
        sender.sendMessage(ChatColor.GOLD + "====================================");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("status", "spawn", "clear", "debug", "reload", "help"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            // Tab complete mob keys
            if (plugin.getMobManager() != null) {
                completions.addAll(plugin.getMobManager().getMobKeys());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
            // Tab complete tiers
            completions.addAll(Arrays.asList("NORMAL", "ELITE", "BOSS"));
        }

        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
    }
}


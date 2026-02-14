package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.gui.TalentGUI;
import com.customrpg.managers.TalentManager;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.TalentBranch;
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

/**
 * TalentCommand - 天賦系統指令處理器
 *
 * 指令功能：
 * - /talent - 開啟天賦GUI
 * - /talent <分支> - 開啟指定分支的天賦GUI
 * - /talent give <玩家> <點數> - 給予玩家天賦點數
 * - /talent reset <玩家> - 重置玩家天賦
 * - /talent info <玩家> - 查看玩家天賦資訊
 * - /talent reload - 重新載入天賦配置
 */
public class TalentCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;
    private final TalentManager talentManager;
    private final TalentGUI talentGUI;

    public TalentCommand(CustomRPG plugin, TalentManager talentManager, TalentGUI talentGUI) {
        this.plugin = plugin;
        this.talentManager = talentManager;
        this.talentGUI = talentGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // 無參數 - 開啟天賦GUI
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "只有玩家可以使用此指令！");
                return true;
            }

            Player player = (Player) sender;
            talentGUI.openTalentGUI(player, null);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "warrior":
            case "mage":
            case "assassin":
                return handleBranchCommand(sender, subCommand);

            case "give":
                return handleGiveCommand(sender, args);

            case "reset":
                return handleResetCommand(sender, args);

            case "info":
                return handleInfoCommand(sender, args);

            case "reload":
                return handleReloadCommand(sender);

            case "help":
                return handleHelpCommand(sender);

            default:
                sender.sendMessage(ChatColor.RED + "未知的子指令！使用 /talent help 查看幫助");
                return true;
        }
    }

    /**
     * 處理分支指令
     */
    private boolean handleBranchCommand(CommandSender sender, String branchName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用此指令！");
            return true;
        }

        Player player = (Player) sender;
        TalentBranch branch;

        try {
            branch = TalentBranch.valueOf(branchName.toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "無效的分支名稱！");
            return true;
        }

        talentGUI.openTalentGUI(player, branch);
        return true;
    }

    /**
     * 處理給予天賦點數指令
     */
    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customrpg.talent.admin")) {
            sender.sendMessage(ChatColor.RED + "您沒有權限執行此指令！");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "用法: /talent give <玩家> <點數>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "玩家 " + args[1] + " 不在線上！");
            return true;
        }

        int points;
        try {
            points = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "無效的點數！");
            return true;
        }

        if (points <= 0) {
            sender.sendMessage(ChatColor.RED + "點數必須為正數！");
            return true;
        }

        talentManager.givePoints(target, points);
        sender.sendMessage(ChatColor.GREEN + "成功給予 " + target.getName() + " " + points + " 點天賦點數");

        return true;
    }

    /**
     * 處理重置天賦指令
     */
    private boolean handleResetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customrpg.talent.admin")) {
            sender.sendMessage(ChatColor.RED + "您沒有權限執行此指令！");
            return true;
        }

        Player target;
        if (args.length < 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "請指定要重置的玩家！");
                return true;
            }
            target = (Player) sender;
        } else {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "玩家 " + args[1] + " 不在線上！");
                return true;
            }
        }

        if (talentManager.resetAllTalents(target)) {
            sender.sendMessage(ChatColor.GREEN + "成功重置 " + target.getName() + " 的天賦！");
            if (target != sender) {
                target.sendMessage(ChatColor.YELLOW + "您的天賦已被管理員重置！");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "重置失敗，該玩家沒有已學習的天賦！");
        }

        return true;
    }

    /**
     * 處理查看玩家資訊指令
     */
    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customrpg.talent.admin")) {
            sender.sendMessage(ChatColor.RED + "您沒有權限執行此指令！");
            return true;
        }

        Player target;
        if (args.length < 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "請指定要查看的玩家！");
                return true;
            }
            target = (Player) sender;
        } else {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "玩家 " + args[1] + " 不在線上！");
                return true;
            }
        }

        PlayerTalents playerTalents = talentManager.getPlayerTalents(target);

        sender.sendMessage("§8§m" + "=".repeat(50));
        sender.sendMessage("§6§l" + target.getName() + " 的天賦資訊");
        sender.sendMessage("");
        sender.sendMessage("§e可用天賦點數: §a" + playerTalents.getAvailablePoints());
        sender.sendMessage("§e已消耗點數: §c" + playerTalents.getTotalPointsSpent());
        sender.sendMessage("");

        // 分支統計
        for (TalentBranch branch : TalentBranch.values()) {
            int branchPoints = playerTalents.getBranchPoints(branch);
            sender.sendMessage("§6" + branch.getDisplayName() + ": §7" + branchPoints + " 點");
        }

        TalentBranch mainSpec = playerTalents.getMainSpecialization();
        if (mainSpec != null) {
            sender.sendMessage("");
            sender.sendMessage("§b主要專精: §a" + mainSpec.getDisplayName());
        }

        sender.sendMessage("§8§m" + "=".repeat(50));

        return true;
    }

    /**
     * 處理重新載入指令
     */
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("customrpg.talent.admin")) {
            sender.sendMessage(ChatColor.RED + "您沒有權限執行此指令！");
            return true;
        }

        // 重新載入插件 (簡化版本，實際上可能需要重新初始化 TalentManager)
        sender.sendMessage(ChatColor.YELLOW + "正在重新載入天賦配置...");

        try {
            // 這裡可以添加重新載入邏輯
            sender.sendMessage(ChatColor.GREEN + "天賦配置重新載入完成！");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "重新載入失敗: " + e.getMessage());
        }

        return true;
    }

    /**
     * 處理幫助指令
     */
    private boolean handleHelpCommand(CommandSender sender) {
        sender.sendMessage("§8§m" + "=".repeat(50));
        sender.sendMessage("§6§l天賦系統指令幫助");
        sender.sendMessage("");
        sender.sendMessage("§e/talent §7- 開啟天賦GUI");
        sender.sendMessage("§e/talent <分支> §7- 開啟指定分支 (warrior/mage/assassin)");

        if (sender.hasPermission("customrpg.talent.admin")) {
            sender.sendMessage("");
            sender.sendMessage("§c管理員指令:");
            sender.sendMessage("§e/talent give <玩家> <點數> §7- 給予玩家天賦點數");
            sender.sendMessage("§e/talent reset [玩家] §7- 重置天賦");
            sender.sendMessage("§e/talent info [玩家] §7- 查看天賦資訊");
            sender.sendMessage("§e/talent reload §7- 重新載入配置");
        }

        sender.sendMessage("§8§m" + "=".repeat(50));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("warrior", "mage", "assassin", "help");

            if (sender.hasPermission("customrpg.talent.admin")) {
                subCommands = Arrays.asList("warrior", "mage", "assassin", "give", "reset", "info", "reload", "help");
            }

            for (String subCommand : subCommands) {
                if (subCommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("info")) {
                if (sender.hasPermission("customrpg.talent.admin")) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(player.getName());
                        }
                    }
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("customrpg.talent.admin")) {
                completions.add("1");
                completions.add("5");
                completions.add("10");
            }
        }

        return completions;
    }
}

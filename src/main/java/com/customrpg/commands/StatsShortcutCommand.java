package com.customrpg.commands;

import com.customrpg.gui.StatsGUI;
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
 * StatsShortcutCommand - /stats 指令的快捷方式
 *
 * /stats = /rpg stats (查看自己的數據)
 * /stats gui = /rpg gui (開啟 GUI)
 * /stats [玩家] = /rpg stats [玩家] (查看其他玩家數據)
 */
public class StatsShortcutCommand implements CommandExecutor, TabCompleter {

    private final StatsCommand mainCommand;
    private final StatsGUI statsGUI;

    public StatsShortcutCommand(StatsCommand mainCommand, StatsGUI statsGUI) {
        this.mainCommand = mainCommand;
        this.statsGUI = statsGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /stats gui
        if (args.length >= 1 && args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "只有玩家可以使用 GUI！");
                return true;
            }
            statsGUI.openStatsGUI(player);
            return true;
        }

        // /stats [玩家] 或 /stats -> 轉發給主指令
        String[] newArgs = new String[args.length + 1];
        newArgs[0] = "stats";
        System.arraycopy(args, 0, newArgs, 1, args.length);

        return mainCommand.onCommand(sender, command, label, newArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("gui");
            // 加入線上玩家名稱
            org.bukkit.Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }

        return completions;
    }
}


package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.managers.ManaManager;
import com.customrpg.players.PlayerStats;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * ManaCommand - /mana unlimit | limit
 * unlimit: 管理員模式，無限魔力
 * limit: 恢復正常魔力模式
 */
public class ManaCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;
    private final ManaManager manaManager;
    private final Set<UUID> unlimitedPlayers = new HashSet<>();

    public ManaCommand(CustomRPG plugin, ManaManager manaManager) {
        this.plugin = plugin;
        this.manaManager = manaManager;
    }

    public boolean isUnlimited(UUID uuid) {
        return unlimitedPlayers.contains(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此指令只能由玩家執行！");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.YELLOW + "用法: /mana <unlimit|limit>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "unlimit":
                if (!player.hasPermission("customrpg.admin")) {
                    player.sendMessage(ChatColor.RED + "你沒有權限使用此指令！");
                    return true;
                }
                unlimitedPlayers.add(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "已啟用無限魔力模式！");
                return true;

            case "limit":
                if (!player.hasPermission("customrpg.admin")) {
                    player.sendMessage(ChatColor.RED + "你沒有權限使用此指令！");
                    return true;
                }
                unlimitedPlayers.remove(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "已恢復正常魔力模式！");
                return true;

            default:
                player.sendMessage(ChatColor.YELLOW + "用法: /mana <unlimit|limit>");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String sub : Arrays.asList("unlimit", "limit")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}


package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.managers.WeaponManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * WeaponCommand - Command handler for giving custom weapons
 *
 * This class handles the /weapon command which allows players or admins
 * to obtain custom weapons. It supports tab completion and proper error
 * handling.
 *
 * Usage:
 * - /weapon <weapon_name> [player] - Give a weapon to yourself or another player
 * - /weapon list - List all available weapons
 */
public class WeaponCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;
    private final WeaponManager weaponManager;

    /**
     * Constructor for WeaponCommand
     * @param plugin Main plugin instance
     * @param weaponManager WeaponManager instance
     */
    public WeaponCommand(CustomRPG plugin, WeaponManager weaponManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
    }

    /**
     * Execute the /weapon command
     * @param sender Command sender
     * @param command Command
     * @param label Command label
     * @param args Command arguments
     * @return true if command was handled
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /weapon <weapon_name> [player]");
            sender.sendMessage(ChatColor.RED + "Usage: /weapon list");
            sender.sendMessage(ChatColor.RED + "Usage: /weapon reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            listWeapons(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfigs(sender);
            return true;
        }

        String weaponKey = args[0].toLowerCase();
        Player targetPlayer;

        if (args.length >= 2) {
            if (!sender.hasPermission("customrpg.weapon.others")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to give weapons to other players!");
                return true;
            }

            targetPlayer = plugin.getServer().getPlayer(args[1]);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "You must specify a player when using this command from console!");
                return true;
            }
            targetPlayer = (Player) sender;
        }

        ItemStack weapon = weaponManager.createWeapon(weaponKey);
        if (weapon == null) {
            sender.sendMessage(ChatColor.RED + "Unknown weapon: " + weaponKey);
            sender.sendMessage(ChatColor.YELLOW + "Use /weapon list to see all available weapons");
            return true;
        }

        targetPlayer.getInventory().addItem(weapon);
        targetPlayer.sendMessage(ChatColor.GREEN + "You received a custom weapon!");

        if (!sender.equals(targetPlayer)) {
            sender.sendMessage(ChatColor.GREEN + "Gave weapon to " + targetPlayer.getName());
        }

        return true;
    }

    private void reloadConfigs(CommandSender sender) {
        if (!sender.hasPermission("customrpg.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to reload configs!");
            return;
        }

        plugin.getConfigManager().reloadAllConfigs();
        plugin.getWeaponManager().reloadWeapons();

        sender.sendMessage(ChatColor.GREEN + "Configs reloaded! Weapons refreshed.");
    }

    /**
     * List all available weapons
     * @param sender Command sender
     */
    private void listWeapons(CommandSender sender) {
        List<String> weaponKeys = weaponManager.getWeaponKeys();

        if (weaponKeys.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No weapons available!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "========== Custom Weapons ==========");
        for (String key : weaponKeys) {
            WeaponManager.WeaponData weaponData = weaponManager.getWeaponData(key);
            if (weaponData != null) {
                sender.sendMessage(ChatColor.YELLOW + "- " + key + ChatColor.WHITE + " (" + weaponData.getDisplayName() + ")");
            }
        }
        sender.sendMessage(ChatColor.GOLD + "===================================");
    }

    /**
     * Provide tab completion for the /weapon command
     * @param sender Command sender
     * @param command Command
     * @param alias Command alias
     * @param args Command arguments
     * @return List of completion options
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("list");
            completions.add("reload");
            completions.addAll(weaponManager.getWeaponKeys());

            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        } else if (args.length == 2) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                completions.add(player.getName());
            }

            String input = args[1].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        }

        return completions;
    }
}

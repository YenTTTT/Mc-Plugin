package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.managers.MobManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * MobCommand - Command handler for spawning custom mobs
 *
 * This class handles the /custommob command which allows admins
 * to spawn custom mobs for testing and gameplay purposes.
 *
 * Usage:
 * - /custommob spawn <mob_key> - Spawn a custom mob at your location
 * - /custommob list - List all available custom mobs
 */
public class MobCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;
    private final MobManager mobManager;

    /**
     * Constructor for MobCommand
     * @param plugin Main plugin instance
     * @param mobManager MobManager instance
     */
    public MobCommand(CustomRPG plugin, MobManager mobManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
    }

    /**
     * Execute the /custommob command
     * @param sender Command sender
     * @param command Command
     * @param label Command label
     * @param args Command arguments
     * @return true if command was handled
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("customrpg.mob")) {
            sender.sendMessage(ChatColor.RED + "你沒有權限使用此指令！");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "用法: /custommob <spawn|list|info> [參數]");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                listMobs(sender);
                return true;

            case "spawn":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /custommob spawn <mob_key>");
                    return true;
                }
                return spawnMob(sender, args[1]);

            case "info":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /custommob info <mob_key>");
                    return true;
                }
                return showMobInfo(sender, args[1]);

            default:
                sender.sendMessage(ChatColor.RED + "未知子指令: " + subCommand);
                sender.sendMessage(ChatColor.YELLOW + "可用指令: list, spawn, info");
                return true;
        }
    }

    /**
     * Spawn a custom mob at the sender's location
     */
    private boolean spawnMob(CommandSender sender, String mobKey) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此指令只能由玩家執行！");
            return true;
        }

        Player player = (Player) sender;
        MobManager.MobData mobData = mobManager.getMobData(mobKey);

        if (mobData == null) {
            sender.sendMessage(ChatColor.RED + "未知的生物類型: " + mobKey);
            sender.sendMessage(ChatColor.YELLOW + "使用 /custommob list 查看所有可用生物");
            return true;
        }

        Location spawnLocation = player.getLocation().add(player.getLocation().getDirection().multiply(3));
        LivingEntity mob = mobManager.spawnCustomMob(mobKey, spawnLocation);

        if (mob == null) {
            sender.sendMessage(ChatColor.RED + "生成生物失敗！");
            return true;
        }

        int level = mobManager.getMobLevel(mob);
        sender.sendMessage(ChatColor.GREEN + "成功生成 " + mobData.getName() + " (Lv." + level + ")");

        return true;
    }

    /**
     * List all available custom mobs
     */
    private void listMobs(CommandSender sender) {
        List<String> mobKeys = mobManager.getMobKeys();

        if (mobKeys.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "沒有可用的自定義生物！");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "========== 自定義生物列表 ==========");
        sender.sendMessage(ChatColor.YELLOW + "使用 /custommob spawn <key> 來生成生物");
        sender.sendMessage(ChatColor.YELLOW + "使用 /custommob info <key> 查看詳細資訊");
        sender.sendMessage(ChatColor.GRAY + "-----------------------------------");

        for (String key : mobKeys) {
            MobManager.MobData mobData = mobManager.getMobData(key);
            if (mobData != null) {
                String levelInfo = "";
                if (mobData.hasLevelSystem()) {
                    levelInfo = ChatColor.GRAY + " (Lv." + mobData.getMinLevel() + "-" + mobData.getMaxLevel() + ")";
                }
                sender.sendMessage(ChatColor.YELLOW + "- " + key + ChatColor.WHITE +
                    " (" + mobData.getName() + ")" + levelInfo);
            }
        }

        sender.sendMessage(ChatColor.GOLD + "===================================");
        sender.sendMessage(ChatColor.GRAY + "總計: " + mobKeys.size() + " 種生物");
    }

    /**
     * Show detailed information about a custom mob
     */
    private boolean showMobInfo(CommandSender sender, String mobKey) {
        MobManager.MobData mobData = mobManager.getMobData(mobKey);

        if (mobData == null) {
            sender.sendMessage(ChatColor.RED + "未知的生物類型: " + mobKey);
            sender.sendMessage(ChatColor.YELLOW + "使用 /custommob list 查看所有可用生物");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "========== 生物資訊 ==========");
        sender.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + mobKey);
        sender.sendMessage(ChatColor.YELLOW + "名稱: " + mobData.getName());
        sender.sendMessage(ChatColor.YELLOW + "類型: " + ChatColor.WHITE + mobData.getEntityType().name());

        if (mobData.hasLevelSystem()) {
            sender.sendMessage(ChatColor.GRAY + "------- 等級系統 -------");
            sender.sendMessage(ChatColor.YELLOW + "等級範圍: " + ChatColor.WHITE +
                mobData.getMinLevel() + " - " + mobData.getMaxLevel());
            sender.sendMessage(ChatColor.YELLOW + "基礎血量: " + ChatColor.WHITE +
                String.format("%.1f", mobData.getBaseHealth()));
            sender.sendMessage(ChatColor.YELLOW + "每級血量: " + ChatColor.WHITE +
                "+" + String.format("%.1f", mobData.getHealthPerLevel()));
            sender.sendMessage(ChatColor.YELLOW + "基礎傷害: " + ChatColor.WHITE +
                String.format("%.1f", mobData.getBaseDamage()));
            sender.sendMessage(ChatColor.YELLOW + "每級傷害: " + ChatColor.WHITE +
                "+" + String.format("%.1f", mobData.getDamagePerLevel()));

            sender.sendMessage(ChatColor.GRAY + "------- 範例屬性 -------");
            int minLv = mobData.getMinLevel();
            int maxLv = mobData.getMaxLevel();
            sender.sendMessage(ChatColor.WHITE + "Lv." + minLv + ": " +
                ChatColor.RED + String.format("%.0f", mobData.calculateHealth(minLv)) + " HP, " +
                String.format("%.0f", mobData.calculateDamage(minLv)) + " 傷害, " +
                ChatColor.GOLD + mobData.calculateExp(minLv) + " 經驗");
            sender.sendMessage(ChatColor.WHITE + "Lv." + maxLv + ": " +
                ChatColor.RED + String.format("%.0f", mobData.calculateHealth(maxLv)) + " HP, " +
                String.format("%.0f", mobData.calculateDamage(maxLv)) + " 傷害, " +
                ChatColor.GOLD + mobData.calculateExp(maxLv) + " 經驗");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "血量: " + ChatColor.WHITE +
                String.format("%.1f", mobData.getHealth()));
            sender.sendMessage(ChatColor.YELLOW + "傷害: " + ChatColor.WHITE +
                String.format("%.1f", mobData.getDamage()));
            sender.sendMessage(ChatColor.YELLOW + "經驗值: " + ChatColor.WHITE + mobData.getExp());
        }

        if (!mobData.getEquipment().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "------- 裝備 -------");
            sender.sendMessage(ChatColor.YELLOW + "裝備數量: " + ChatColor.WHITE + mobData.getEquipment().size() + " 件");
        }

        if (!mobData.getVanillaDrops().isEmpty() || !mobData.getWeaponDrops().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "------- 掉落物 -------");
            sender.sendMessage(ChatColor.YELLOW + "原版物品: " + ChatColor.WHITE + mobData.getVanillaDrops().size());
            sender.sendMessage(ChatColor.YELLOW + "自定義武器: " + ChatColor.WHITE + mobData.getWeaponDrops().size());
        }

        if (mobData.getDisguise() != null) {
            sender.sendMessage(ChatColor.GRAY + "------- 偽裝 -------");
            sender.sendMessage(ChatColor.YELLOW + "偽裝類型: " + ChatColor.WHITE +
                mobData.getDisguise().getDisguiseType().name());
        }

        if (!mobData.getSpecialBehavior().equals("none")) {
            sender.sendMessage(ChatColor.GRAY + "------- 特殊行為 -------");
            sender.sendMessage(ChatColor.YELLOW + "行為: " + ChatColor.WHITE + mobData.getSpecialBehavior());
        }

        sender.sendMessage(ChatColor.GOLD + "==============================");
        return true;
    }

    /**
     * Provide tab completion for the /custommob command
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("spawn");
            completions.add("list");
            completions.add("info");

            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("info"))) {
            // 添加所有可用的生物 key
            completions.addAll(mobManager.getMobKeys());

            String input = args[1].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        }

        return completions;
    }
}




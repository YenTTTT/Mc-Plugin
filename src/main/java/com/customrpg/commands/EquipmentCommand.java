package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.equipment.EquipmentData;
import com.customrpg.equipment.EquipmentManager;
import com.customrpg.gui.EquipmentGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 裝備系統指令處理器
 * 處理 /equipment 相關指令
 */
public class EquipmentCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;
    private final EquipmentManager equipmentManager;
    private final EquipmentGUI equipmentGUI;

    public EquipmentCommand(CustomRPG plugin) {
        this.plugin = plugin;
        this.equipmentManager = plugin.getEquipmentManager();
        this.equipmentGUI = plugin.getEquipmentGUI();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此指令只能由玩家使用！");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // 開啟裝備GUI
            equipmentGUI.openEquipmentGUI(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "open":
            case "gui":
                equipmentGUI.openEquipmentGUI(player);
                break;

            case "give":
                handleGiveCommand(player, args);
                break;

            case "enhance":
                handleEnhanceCommand(player, args);
                break;

            case "info":
                handleInfoCommand(player, args);
                break;

            case "reload":
                handleReloadCommand(player);
                break;

            case "list":
                handleListCommand(player, args);
                break;

            case "help":
            default:
                sendHelpMessage(player);
                break;
        }

        return true;
    }

    /**
     * 處理give指令
     */
    private void handleGiveCommand(Player player, String[] args) {
        if (!player.hasPermission("customrpg.equipment.give")) {
            player.sendMessage("§c你沒有權限使用此指令！");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c用法: /equipment give <裝備ID> [玩家]");
            return;
        }

        String equipmentId = args[1];
        Player target = player;

        if (args.length >= 3) {
            target = plugin.getServer().getPlayer(args[2]);
            if (target == null) {
                player.sendMessage("§c找不到玩家: " + args[2]);
                return;
            }
        }

        EquipmentData equipment = equipmentManager.createEquipment(equipmentId);
        if (equipment == null) {
            player.sendMessage("§c找不到裝備: " + equipmentId);
            return;
        }

        target.getInventory().addItem(equipment.toItemStack());
        player.sendMessage("§a已給予 " + target.getName() + " 裝備: " + equipment.getName());

        if (target != player) {
            target.sendMessage("§a你收到了裝備: " + equipment.getName());
        }
    }

    /**
     * 處理enhance指令
     */
    private void handleEnhanceCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /equipment enhance <槽位>");
            player.sendMessage("§7可用槽位: helmet, chestplate, leggings, boots, main_hand, off_hand");
            return;
        }

        try {
            com.customrpg.equipment.EquipmentSlot slot = com.customrpg.equipment.EquipmentSlot.valueOf(args[1].toUpperCase());
            equipmentGUI.openEnhanceGUI(player, slot);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c無效的槽位: " + args[1]);
        }
    }

    /**
     * 處理info指令
     */
    private void handleInfoCommand(Player player, String[] args) {
        java.util.Map<com.customrpg.equipment.EquipmentSlot, EquipmentData> equipment =
            equipmentManager.getPlayerEquipment(player.getUniqueId());

        player.sendMessage("§6§l=== 裝備信息 ===");

        if (equipment.isEmpty()) {
            player.sendMessage("§7你目前沒有裝備任何物品");
            return;
        }

        for (java.util.Map.Entry<com.customrpg.equipment.EquipmentSlot, EquipmentData> entry : equipment.entrySet()) {
            com.customrpg.equipment.EquipmentSlot slot = entry.getKey();
            EquipmentData equip = entry.getValue();

            if (equip != null) {
                String enhanceText = equip.getEnhanceLevel() > 0 ? " §7[+" + equip.getEnhanceLevel() + "]" : "";
                player.sendMessage("§e" + slot.getDisplayName() + ": " +
                                 equip.getRarity().getColorCode() + equip.getName() + enhanceText);
            }
        }

        player.sendMessage("§7使用 /equipment 開啟詳細界面");
    }

    /**
     * 處理reload指令
     */
    private void handleReloadCommand(Player player) {
        if (!player.hasPermission("customrpg.equipment.reload")) {
            player.sendMessage("§c你沒有權限使用此指令！");
            return;
        }

        equipmentManager.reload();
        player.sendMessage("§a裝備配置已重載！");
    }

    /**
     * 處理list指令
     */
    private void handleListCommand(Player player, String[] args) {
        if (!player.hasPermission("customrpg.equipment.list")) {
            player.sendMessage("§c你沒有權限使用此指令！");
            return;
        }

        java.util.Map<String, EquipmentData> templates = equipmentManager.getEquipmentTemplates();

        if (templates.isEmpty()) {
            player.sendMessage("§c沒有可用的裝備模板！");
            return;
        }

        player.sendMessage("§6§l=== 可用裝備列表 ===");

        String filter = args.length > 1 ? args[1].toLowerCase() : "";
        int count = 0;

        for (java.util.Map.Entry<String, EquipmentData> entry : templates.entrySet()) {
            String id = entry.getKey();
            EquipmentData template = entry.getValue();

            if (!filter.isEmpty() && !id.toLowerCase().contains(filter) &&
                !template.getName().toLowerCase().contains(filter)) {
                continue;
            }

            player.sendMessage("§e" + id + " §7- " + template.getRarity().getColorCode() +
                             template.getName() + " §7(" + template.getSlot().getDisplayName() + ")");
            count++;

            if (count >= 20) { // 限制顯示數量
                player.sendMessage("§7... 還有更多裝備，使用過濾器縮小範圍");
                break;
            }
        }

        if (count == 0) {
            player.sendMessage("§7沒有符合條件的裝備");
        } else {
            player.sendMessage("§7顯示了 " + count + " 個裝備");
        }
    }

    /**
     * 發送幫助信息
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§6§l=== 裝備系統指令 ===");
        player.sendMessage("§e/equipment §7- 開啟裝備界面");
        player.sendMessage("§e/equipment info §7- 查看當前裝備");
        player.sendMessage("§e/equipment enhance <槽位> §7- 開啟強化界面");

        if (player.hasPermission("customrpg.equipment.give")) {
            player.sendMessage("§e/equipment give <裝備ID> [玩家] §7- 給予裝備");
            player.sendMessage("§e/equipment list [過濾器] §7- 查看裝備列表");
        }

        if (player.hasPermission("customrpg.equipment.reload")) {
            player.sendMessage("§e/equipment reload §7- 重載配置");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("open");
            subCommands.add("info");
            subCommands.add("enhance");
            subCommands.add("help");

            if (player.hasPermission("customrpg.equipment.give")) {
                subCommands.add("give");
                subCommands.add("list");
            }

            if (player.hasPermission("customrpg.equipment.reload")) {
                subCommands.add("reload");
            }

            String input = args[0].toLowerCase();
            completions.addAll(subCommands.stream()
                .filter(cmd -> cmd.startsWith(input))
                .collect(Collectors.toList()));

        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if ("give".equals(subCommand)) {
                // 自動完成裝備ID
                String input = args[1].toLowerCase();
                completions.addAll(equipmentManager.getEquipmentTemplates().keySet().stream()
                    .filter(id -> id.toLowerCase().startsWith(input))
                    .collect(Collectors.toList()));

            } else if ("enhance".equals(subCommand)) {
                // 自動完成槽位
                String input = args[1].toLowerCase();
                for (com.customrpg.equipment.EquipmentSlot slot : com.customrpg.equipment.EquipmentSlot.values()) {
                    if (slot.name().toLowerCase().startsWith(input)) {
                        completions.add(slot.name().toLowerCase());
                    }
                }
            }

        } else if (args.length == 3 && "give".equals(args[0].toLowerCase())) {
            // 自動完成玩家名稱
            String input = args[2].toLowerCase();
            completions.addAll(plugin.getServer().getOnlinePlayers().stream()
                .map(org.bukkit.entity.Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .collect(Collectors.toList()));
        }

        return completions;
    }
}

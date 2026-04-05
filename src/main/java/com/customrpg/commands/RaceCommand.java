package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.gui.RaceGUI;
import com.customrpg.players.PlayerStats;
import com.customrpg.races.RaceData;
import com.customrpg.races.RaceManager;
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
import java.util.Map;

/**
 * RaceCommand - 種族系統指令處理器
 *
 * 指令功能：
 * - /race - 開啟種族選擇 GUI
 * - /race info [玩家] - 查看種族資訊
 * - /race set <玩家> <種族> - 管理員設定種族
 * - /race reset <玩家> - 管理員重置種族
 * - /race list - 列出所有種族
 * - /race reload - 重新載入種族配置
 * - /race help - 顯示幫助
 */
public class RaceCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;
    private final RaceManager raceManager;
    private final RaceGUI raceGUI;

    public RaceCommand(CustomRPG plugin, RaceManager raceManager, RaceGUI raceGUI) {
        this.plugin = plugin;
        this.raceManager = raceManager;
        this.raceGUI = raceGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 無參數 → 開啟 GUI
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "只有玩家可以使用此指令！");
                return true;
            }
            raceGUI.open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "info" -> handleInfoCommand(sender, args);
            case "set" -> handleSetCommand(sender, args);
            case "reset" -> handleResetCommand(sender, args);
            case "list" -> handleListCommand(sender);
            case "reload" -> handleReloadCommand(sender);
            case "help" -> handleHelpCommand(sender);
            default -> {
                sender.sendMessage(ChatColor.RED + "未知的子指令！使用 /race help 查看幫助");
                yield true;
            }
        };
    }

    /**
     * /race info [玩家]
     */
    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "找不到玩家: " + args[1]);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "請指定玩家名稱！");
                return true;
            }
            target = (Player) sender;
        }

        String raceId = raceManager.getPlayerRaceId(target);
        if (raceId == null) {
            sender.sendMessage(ChatColor.YELLOW + target.getName() + " §e尚未選擇種族。");
            return true;
        }

        RaceData race = raceManager.getRaceData(raceId);
        if (race == null) {
            sender.sendMessage(ChatColor.RED + "種族資料異常: " + raceId);
            return true;
        }

        PlayerStats stats = plugin.getPlayerStatsManager().getStats(target);

        sender.sendMessage("§6§l════════ 種族資訊 ════════");
        sender.sendMessage("§e玩家: §f" + target.getName());
        sender.sendMessage("§e種族: " + race.getDisplayName());
        sender.sendMessage("§e等級: §f" + stats.getLevel());
        sender.sendMessage("");

        sender.sendMessage("§e§l── 基礎屬性加成 ──");
        sender.sendMessage("§c⚔ 力量: §f+" + race.getBaseStrength() + " §7(成長: " + String.format("%.1fx", race.getGrowthStrength()) + ")");
        sender.sendMessage("§b✦ 魔法: §f+" + race.getBaseMagic() + " §7(成長: " + String.format("%.1fx", race.getGrowthMagic()) + ")");
        sender.sendMessage("§a➤ 敏捷: §f+" + race.getBaseAgility() + " §7(成長: " + String.format("%.1fx", race.getGrowthAgility()) + ")");
        sender.sendMessage("§d♥ 體力: §f+" + race.getBaseVitality() + " §7(成長: " + String.format("%.1fx", race.getGrowthVitality()) + ")");
        sender.sendMessage("§9⛨ 防禦: §f+" + race.getBaseDefense() + " §7(成長: " + String.format("%.1fx", race.getGrowthDefense()) + ")");
        sender.sendMessage("§5✧ 精神: §f+" + race.getBaseSpirit() + " §7(成長: " + String.format("%.1fx", race.getGrowthSpirit()) + ")");

        sender.sendMessage("");
        sender.sendMessage("§e§l── 等級加成 (Lv" + stats.getLevel() + ") ──");
        sender.sendMessage("§c力量: §f+" + race.calculateStrengthBonus(stats.getLevel())
                + " §b魔法: §f+" + race.calculateMagicBonus(stats.getLevel())
                + " §a敏捷: §f+" + race.calculateAgilityBonus(stats.getLevel()));
        sender.sendMessage("§d體力: §f+" + race.calculateVitalityBonus(stats.getLevel())
                + " §9防禦: §f+" + race.calculateDefenseBonus(stats.getLevel())
                + " §5精神: §f+" + race.calculateSpiritBonus(stats.getLevel()));

        if (!race.getSkills().isEmpty()) {
            sender.sendMessage("");
            sender.sendMessage("§d§l── 種族技能 ──");
            for (String skill : race.getSkills()) {
                sender.sendMessage("§b• " + skill);
            }
        }

        sender.sendMessage("");
        sender.sendMessage("§3§l── 武器親和 ──");
        java.util.Map<String, Double> weaponBonuses = race.getWeaponBonuses();
        if (weaponBonuses.isEmpty()) {
            sender.sendMessage("§7所有武器: §f" + String.format("%.0f%%", race.getDefaultWeaponBonus() * 100));
        } else {
            for (java.util.Map.Entry<String, Double> entry : weaponBonuses.entrySet()) {
                double bonus = entry.getValue();
                String color = bonus > 1.0 ? "§a" : (bonus < 1.0 ? "§c" : "§f");
                sender.sendMessage("§e" + entry.getKey() + ": " + color + String.format("%.0f%%", bonus * 100));
            }
            sender.sendMessage("§7其他武器: §f" + String.format("%.0f%%", race.getDefaultWeaponBonus() * 100));
        }

        sender.sendMessage("§6§l════════════════════════════");
        return true;
    }

    /**
     * /race set <玩家> <種族> (管理員)
     */
    private boolean handleSetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customrpg.race.admin")) {
            sender.sendMessage(ChatColor.RED + "你沒有權限使用此指令！");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "用法: /race set <玩家> <種族ID>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "找不到玩家: " + args[1]);
            return true;
        }

        String raceId = args[2].toLowerCase();
        RaceData raceData = raceManager.getRaceData(raceId);
        if (raceData == null) {
            sender.sendMessage(ChatColor.RED + "無效的種族 ID: " + raceId);
            sender.sendMessage(ChatColor.GRAY + "可用種族: " + String.join(", ", raceManager.getAllRaceIds()));
            return true;
        }

        // 先重置再設定（允許管理員覆蓋）
        raceManager.resetPlayerRace(target);
        boolean success = raceManager.setPlayerRace(target, raceId);

        if (success) {
            sender.sendMessage(ChatColor.GREEN + "已將 " + target.getName() + " 的種族設定為: " + raceData.getDisplayName());
            target.sendMessage("§a§l[種族] §e管理員已將你的種族設定為: " + raceData.getDisplayName());
        } else {
            sender.sendMessage(ChatColor.RED + "設定種族失敗！");
        }

        return true;
    }

    /**
     * /race reset <玩家> (管理員)
     */
    private boolean handleResetCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customrpg.race.admin")) {
            sender.sendMessage(ChatColor.RED + "你沒有權限使用此指令！");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /race reset <玩家>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "找不到玩家: " + args[1]);
            return true;
        }

        raceManager.resetPlayerRace(target);
        sender.sendMessage(ChatColor.GREEN + "已重置 " + target.getName() + " 的種族。");
        target.sendMessage("§c§l[種族] §e你的種族已被管理員重置。使用 §a/race §e重新選擇。");
        return true;
    }

    /**
     * /race list - 列出所有種族
     */
    private boolean handleListCommand(CommandSender sender) {
        sender.sendMessage("§6§l════════ 所有種族 ════════");
        for (RaceData race : raceManager.getAllRaces()) {
            sender.sendMessage("§e• " + race.getId() + " §7- " + race.getDisplayName());
        }
        sender.sendMessage("§7共 " + raceManager.getRaceCount() + " 個種族");
        sender.sendMessage("§6§l════════════════════════════");
        return true;
    }

    /**
     * /race reload (管理員)
     */
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("customrpg.race.admin")) {
            sender.sendMessage(ChatColor.RED + "你沒有權限使用此指令！");
            return true;
        }

        raceManager.reload();
        sender.sendMessage(ChatColor.GREEN + "種族配置已重新載入！共 " + raceManager.getRaceCount() + " 個種族。");

        // 重新套用所有在線玩家的種族屬性
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (raceManager.hasRace(player)) {
                raceManager.applyRaceStats(player);
            }
        }
        sender.sendMessage(ChatColor.GREEN + "已重新套用所有在線玩家的種族屬性。");

        return true;
    }

    /**
     * /race help
     */
    private boolean handleHelpCommand(CommandSender sender) {
        sender.sendMessage("§6§l════════ 種族指令幫助 ════════");
        sender.sendMessage("§e/race §7- 開啟種族選擇介面");
        sender.sendMessage("§e/race info §7[玩家] §7- 查看種族資訊");
        sender.sendMessage("§e/race list §7- 列出所有種族");
        if (sender.hasPermission("customrpg.race.admin")) {
            sender.sendMessage("§c/race set §7<玩家> <種族> §7- 設定玩家種族 (管理員)");
            sender.sendMessage("§c/race reset §7<玩家> §7- 重置玩家種族 (管理員)");
            sender.sendMessage("§c/race reload §7- 重新載入種族配置 (管理員)");
        }
        sender.sendMessage("§e/race help §7- 顯示此幫助");
        sender.sendMessage("§6§l════════════════════════════════");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("info", "list", "help"));
            if (sender.hasPermission("customrpg.race.admin")) {
                subs.addAll(Arrays.asList("set", "reset", "reload"));
            }
            String prefix = args[0].toLowerCase();
            for (String s : subs) {
                if (s.startsWith(prefix)) completions.add(s);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("info") || sub.equals("set") || sub.equals("reset")) {
                String prefix = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) completions.add(p.getName());
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set")) {
                String prefix = args[2].toLowerCase();
                for (String id : raceManager.getAllRaceIds()) {
                    if (id.startsWith(prefix)) completions.add(id);
                }
            }
        }

        return completions;
    }
}


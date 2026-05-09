package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.managers.BossZoneManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /bosszone — Boss 區域管理指令
 *
 *   /bosszone list                — 列出所有 Boss 區域與目前狀態
 *   /bosszone spawn <zone_id>     — 強制在指定區域生成 Boss（先清除再生成）
 *   /bosszone remove <zone_id>    — 移除指定區域內所有 Boss
 *   /bosszone status [zone_id]    — 查看指定區域（或所有區域）的詳細狀態
 */
public class BossZoneCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "customrpg.admin";
    private final CustomRPG plugin;

    public BossZoneCommand(CustomRPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(ChatColor.RED + "你沒有權限使用此指令！");
            return true;
        }

        BossZoneManager mgr = plugin.getBossZoneManager();
        if (mgr == null) {
            sender.sendMessage(ChatColor.RED + "BossZoneManager 尚未初始化！");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "list"   -> handleList(sender, mgr);
            case "spawn"  -> handleSpawn(sender, mgr, args);
            case "remove" -> handleRemove(sender, mgr, args);
            case "status" -> handleStatus(sender, mgr, args);
            case "help"   -> { sendHelp(sender, label); yield true; }
            default       -> { sendHelp(sender, label); yield true; }
        };
    }

    // ─── list ────────────────────────────────────────────────────────────────

    private boolean handleList(CommandSender sender, BossZoneManager mgr) {
        Collection<BossZoneManager.BossZone> zones = mgr.getZones();
        if (zones.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "目前沒有設定任何 Boss 區域。");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "══ Boss 區域列表 (" + zones.size() + " 個) ══");
        for (BossZoneManager.BossZone zone : zones) {
            LivingEntity boss = mgr.getActiveBoss(zone.getId());
            String bossStatus = boss != null
                    ? ChatColor.RED + "● 存在 (" + boss.getUniqueId().toString().substring(0, 8) + "...)"
                    : ChatColor.GRAY + "○ 未生成";
            sender.sendMessage(ChatColor.YELLOW + "  " + zone.getId()
                    + ChatColor.WHITE + " [" + zone.getName() + "]"
                    + "  " + bossStatus
                    + ChatColor.GRAY + "  世界:" + zone.getWorld()
                    + "  半徑:" + (int) zone.getRadius()
                    + "  Boss怪:" + zone.getBossMobKey());
        }
        return true;
    }

    // ─── spawn ───────────────────────────────────────────────────────────────

    private boolean handleSpawn(CommandSender sender, BossZoneManager mgr, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法：/bosszone spawn <zone_id>");
            return true;
        }
        String zoneId = args[1].toLowerCase();
        BossZoneManager.BossZone zone = findZone(mgr, zoneId);
        if (zone == null) {
            sender.sendMessage(ChatColor.RED + "找不到 Boss 區域：" + zoneId);
            sender.sendMessage(ChatColor.GRAY + "可用區域：" + String.join(", ", mgr.getZoneIds()));
            return true;
        }

        // 告知現有 Boss 將被移除
        LivingEntity existingBoss = mgr.getActiveBoss(zone.getId());
        if (existingBoss != null) {
            sender.sendMessage(ChatColor.YELLOW + "► 偵測到區域 [" + zone.getName() + "] 已有 Boss，先移除再重新生成...");
        } else {
            sender.sendMessage(ChatColor.AQUA + "► 在區域 [" + zone.getName() + "] 強制生成 Boss...");
        }

        LivingEntity spawned = mgr.forceSpawnBoss(zoneId);
        if (spawned == null) {
            sender.sendMessage(ChatColor.RED + "✗ 生成失敗！");
            sender.sendMessage(ChatColor.GRAY + "  可能原因：世界不存在、boss-mob-key 設定錯誤，或 center 指定的位置本身不是安全生成點。");
            sender.sendMessage(ChatColor.GRAY + "  center 位置需要：腳下為實心方塊、Boss 腳下與頭頂為空氣。否則將不會改到其他位置，而是直接失敗。");
            sender.sendMessage(ChatColor.GRAY + "  請確認 boss_zones.yml 中 [" + zoneId + "] 的配置正確。");
        } else {
            String name = spawned.getCustomName() != null ? spawned.getCustomName() : zone.getBossMobKey();
            sender.sendMessage(ChatColor.GREEN + "✓ 已在 [" + zone.getName() + "] 生成 Boss：" + name);
            sender.sendMessage(ChatColor.GRAY + "  位置: " + formatLoc(spawned));
        }
        return true;
    }

    // ─── remove ──────────────────────────────────────────────────────────────

    private boolean handleRemove(CommandSender sender, BossZoneManager mgr, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法：/bosszone remove <zone_id>");
            return true;
        }
        String zoneId = args[1].toLowerCase();
        BossZoneManager.BossZone zone = findZone(mgr, zoneId);
        if (zone == null) {
            sender.sendMessage(ChatColor.RED + "找不到 Boss 區域：" + zoneId);
            sender.sendMessage(ChatColor.GRAY + "可用區域：" + String.join(", ", mgr.getZoneIds()));
            return true;
        }
        mgr.removeAllBossesInZone(zone);
        sender.sendMessage(ChatColor.GREEN + "✓ 已移除 [" + zone.getName() + "] 內所有 Boss。");
        return true;
    }

    // ─── status ──────────────────────────────────────────────────────────────

    private boolean handleStatus(CommandSender sender, BossZoneManager mgr, String[] args) {
        if (args.length >= 2) {
            String zoneId = args[1].toLowerCase();
            BossZoneManager.BossZone zone = findZone(mgr, zoneId);
            if (zone == null) {
                sender.sendMessage(ChatColor.RED + "找不到 Boss 區域：" + zoneId);
                return true;
            }
            printZoneStatus(sender, mgr, zone);
        } else {
            for (BossZoneManager.BossZone zone : mgr.getZones()) {
                printZoneStatus(sender, mgr, zone);
            }
        }
        return true;
    }

    private void printZoneStatus(CommandSender sender, BossZoneManager mgr, BossZoneManager.BossZone zone) {
        sender.sendMessage(ChatColor.GOLD + "── [" + zone.getId() + "] " + zone.getName() + " ──");
        sender.sendMessage(ChatColor.GRAY + "  世界: " + zone.getWorld() + "  半徑: " + (int) zone.getRadius());
        sender.sendMessage(ChatColor.GRAY + "  Boss怪Key: " + zone.getBossMobKey());
        sender.sendMessage(ChatColor.GRAY + "  小怪: " + zone.getLinkedMinions());

        LivingEntity boss = mgr.getActiveBoss(zone.getId());
        if (boss != null) {
            String name = boss.getCustomName() != null ? boss.getCustomName() : zone.getBossMobKey();
            double maxHp = 0;
            try {
                org.bukkit.attribute.AttributeInstance attr = boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attr != null) maxHp = attr.getValue();
            } catch (Exception ignored) {}
            sender.sendMessage(ChatColor.RED + "  目前 Boss: " + name + " HP:" + (int) boss.getHealth()
                    + "/" + (int) maxHp
                    + " 位置:" + formatLoc(boss));
        } else {
            sender.sendMessage(ChatColor.GRAY + "  目前 Boss: 未生成");
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private BossZoneManager.BossZone findZone(BossZoneManager mgr, String id) {
        for (BossZoneManager.BossZone z : mgr.getZones()) {
            if (z.getId().equalsIgnoreCase(id)) return z;
        }
        return null;
    }

    private String formatLoc(LivingEntity e) {
        return String.format("(%.0f, %.0f, %.0f)", e.getLocation().getX(), e.getLocation().getY(), e.getLocation().getZ());
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "══ /bosszone 指令說明 ══");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " list" + ChatColor.GRAY + " — 列出所有 Boss 區域與狀態");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " spawn <zone_id>" + ChatColor.GRAY + " — 強制生成 Boss（先清除再生成）");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " remove <zone_id>" + ChatColor.GRAY + " — 移除指定區域內所有 Boss");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " status [zone_id]" + ChatColor.GRAY + " — 查看 Boss 區域詳細狀態");
    }

    // ─── Tab Completer ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERM)) return new ArrayList<>();

        if (args.length == 1) {
            return filterPrefix(Arrays.asList("list", "spawn", "remove", "status", "help"), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("spawn") || sub.equals("remove") || sub.equals("status")) {
                BossZoneManager mgr = plugin.getBossZoneManager();
                if (mgr != null) return filterPrefix(mgr.getZoneIds(), args[1]);
            }
        }
        return new ArrayList<>();
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        String low = prefix.toLowerCase();
        return options.stream().filter(s -> s.startsWith(low)).collect(Collectors.toList());
    }
}




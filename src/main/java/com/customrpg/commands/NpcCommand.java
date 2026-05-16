package com.customrpg.commands;
import com.customrpg.integration.npc.NpcManager;
import com.customrpg.integration.npc.QuestBindGUI;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
public class NpcCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS =
            Arrays.asList("create","delete","list","tp","rename","settype","spawn","bind","unbind","setquest","clearquest");
    private final NpcManager npcManager;
    private final QuestBindGUI questBindGUI;
    public NpcCommand(NpcManager npcManager, QuestBindGUI questBindGUI) {
        this.npcManager   = npcManager;
        this.questBindGUI = questBindGUI;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c此指令只能由玩家執行。"); return true; }
        if (!player.hasPermission("customrpg.npc.admin")) { player.sendMessage("§c你沒有權限執行此指令。"); return true; }
        if (args.length == 0) { sendHelp(player); return true; }
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) { player.sendMessage("§c用法：/rpgnpc create <名稱>"); return true; }
                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                NpcManager.NpcEntry npc = npcManager.createNpc(name, player.getLocation(), EntityType.VILLAGER);
                player.sendMessage("§a[已建立 NPC] §f" + name + " §7ID: §e" + npc.getInternalId());
                player.sendMessage("§7→ 使用 §e/rpgnpc bind " + npc.getInternalId() + " §7來綁定任務");
            }
            case "delete" -> {
                if (args.length < 2) { player.sendMessage("§c用法：/rpgnpc delete <ID>"); return true; }
                if (npcManager.deleteNpc(args[1])) player.sendMessage("§a已刪除 NPC: §e" + args[1]);
                else player.sendMessage("§c找不到 NPC: " + args[1]);
            }
            case "list" -> {
                player.sendMessage("§6§l╔══ NPC 列表 ══╗");
                if (npcManager.getAllNpcs().isEmpty()) { player.sendMessage("§7  (目前沒有 NPC)"); }
                else for (NpcManager.NpcEntry n : npcManager.getAllNpcs()) {
                    Location l = n.getLocation();
                    String s = n.isSpawned() ? "§a●" : "§c●";
                    String w = l.getWorld() != null ? l.getWorld().getName() : "?";
                    String qStr = n.getQuestId() >= 0 ? " §d[任務:" + n.getQuestId() + "]" : "";
                    player.sendMessage(s + " §e" + n.getInternalId() + " §f" + n.getName()
                            + " §7[" + n.getEntityType().name() + "] " + w
                            + " (" + (int)l.getX() + "," + (int)l.getY() + "," + (int)l.getZ() + ")" + qStr);
                }
                player.sendMessage("§6§l╚═════════════╝");
            }
            case "tp" -> {
                if (args.length < 2) { player.sendMessage("§c用法：/rpgnpc tp <ID>"); return true; }
                NpcManager.NpcEntry n = npcManager.getNpcByInternalId(args[1]);
                if (n == null) { player.sendMessage("§c找不到 NPC: " + args[1]); return true; }
                player.teleport(n.getLocation());
                player.sendMessage("§a已傳送到 NPC [§f" + n.getName() + "§a]");
            }
            case "rename" -> {
                if (args.length < 3) { player.sendMessage("§c用法：/rpgnpc rename <ID> <新名稱>"); return true; }
                NpcManager.NpcEntry n = npcManager.getNpcByInternalId(args[1]);
                if (n == null) { player.sendMessage("§c找不到 NPC: " + args[1]); return true; }
                n.rename(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                player.sendMessage("§a已重新命名: §e" + args[1]);
            }
            case "settype" -> {
                if (args.length < 3) { player.sendMessage("§c用法：/rpgnpc settype <ID> <類型>"); return true; }
                NpcManager.NpcEntry n = npcManager.getNpcByInternalId(args[1]);
                if (n == null) { player.sendMessage("§c找不到 NPC: " + args[1]); return true; }
                try { EntityType t = EntityType.valueOf(args[2].toUpperCase()); npcManager.changeNpcType(n, t); player.sendMessage("§a已更換類型: " + t.name()); }
                catch (IllegalArgumentException e) { player.sendMessage("§c無效類型: " + args[2]); }
            }
            case "spawn" -> {
                if (args.length < 2) { player.sendMessage("§c用法：/rpgnpc spawn <ID>"); return true; }
                NpcManager.NpcEntry n = npcManager.getNpcByInternalId(args[1]);
                if (n == null) { player.sendMessage("§c找不到 NPC: " + args[1]); return true; }
                npcManager.forceRespawn(n);
                player.sendMessage("§a已重新生成 NPC: §e" + args[1]);
            }
            case "bind" -> {
                if (args.length < 2) { player.sendMessage("§c用法：/rpgnpc bind <ID>"); return true; }
                NpcManager.NpcEntry n = npcManager.getNpcByInternalId(args[1]);
                if (n == null) { player.sendMessage("§c找不到 NPC: " + args[1]); return true; }
                questBindGUI.open(player, n);
            }
            case "unbind" -> {
                if (args.length < 2) { player.sendMessage("§c用法：/rpgnpc unbind <ID>"); return true; }
                NpcManager.NpcEntry n = npcManager.getNpcByInternalId(args[1]);
                if (n == null) { player.sendMessage("§c找不到 NPC: " + args[1]); return true; }
                npcManager.bindQuest(n, -1);
                player.sendMessage("§a已取消 NPC §f" + n.getName() + " §a的任務綁定。");
            }
            case "setquest" -> {
                if (args.length < 3) { player.sendMessage("§c用法：/rpgnpc setquest <ID> <questKey>"); return true; }
                NpcManager.NpcEntry n = npcManager.getNpcByInternalId(args[1]);
                if (n == null) { player.sendMessage("§c找不到 NPC: " + args[1]); return true; }
                n.setQuestKey(args[2]);
                npcManager.saveConfig();
                player.sendMessage("§a已將 NPC §f" + n.getName() + " §a的原生任務設為: §e" + args[2]);
            }
            case "clearquest" -> {
                if (args.length < 2) { player.sendMessage("§c用法：/rpgnpc clearquest <ID>"); return true; }
                NpcManager.NpcEntry n = npcManager.getNpcByInternalId(args[1]);
                if (n == null) { player.sendMessage("§c找不到 NPC: " + args[1]); return true; }
                n.setQuestKey(null);
                npcManager.saveConfig();
                player.sendMessage("§a已清除 NPC §f" + n.getName() + " §a的原生任務綁定。");
            }
            default -> sendHelp(player);
        }
        return true;
    }
    private void sendHelp(Player p) {
        p.sendMessage("§6§l/rpgnpc 指令說明:");
        p.sendMessage("§e/rpgnpc create <名稱>  §7— 建立 NPC");
        p.sendMessage("§e/rpgnpc delete <ID>  §7— 刪除 NPC");
        p.sendMessage("§e/rpgnpc list  §7— 列出所有 NPC（含任務綁定資訊）");
        p.sendMessage("§e/rpgnpc tp <ID>  §7— 傳送到 NPC");
        p.sendMessage("§e/rpgnpc rename <ID> <名>  §7— 重新命名 NPC");
        p.sendMessage("§e/rpgnpc settype <ID> <類型>  §7— 更換實體類型");
        p.sendMessage("§e/rpgnpc spawn <ID>  §7— 強制重新生成");
        p.sendMessage("§e/rpgnpc bind <ID>  §7— 開啟任務綁定選單");
        p.sendMessage("§e/rpgnpc unbind <ID>  §7— 取消任務綁定");
        p.sendMessage("§7也可對 NPC §eShift+右鍵§7（管理員）直接開啟綁定選單");
    }
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] args) {
        if (args.length == 1) return SUBCOMMANDS.stream().filter(x -> x.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        String sub = args[0].toLowerCase();
        if (args.length == 2 && List.of("delete","tp","rename","settype","spawn","bind","unbind").contains(sub))
            return npcManager.getAllNpcs().stream().map(NpcManager.NpcEntry::getId).filter(x -> x.startsWith(args[1])).collect(Collectors.toList());
        if (args.length == 3 && "settype".equals(sub))
            return Arrays.stream(EntityType.values()).map(Enum::name).filter(x -> x.startsWith(args[2].toUpperCase())).limit(20).collect(Collectors.toList());
        return new ArrayList<>();
    }
}

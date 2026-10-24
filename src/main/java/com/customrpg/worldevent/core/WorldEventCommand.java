package com.customrpg.worldevent.core;

import com.customrpg.worldevent.reward.EventRewardService;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class WorldEventCommand implements CommandExecutor,TabCompleter {
    private final WorldEventManager manager;
    public WorldEventCommand(WorldEventManager manager) { this.manager=manager; }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        try {
            if(command.getName().equalsIgnoreCase("mmorpg")) return admin(sender,args);
            String action=args.length==0?"info":args[0].toLowerCase(Locale.ROOT);
            if(action.equals("join")||action.equals("leave")) {
                if(!(sender instanceof Player player)) { sender.sendMessage("Player only"); return true; }
                if(action.equals("join")&&args.length<=2)
                    sender.sendMessage(manager.joinArena(player,args.length==2?UUID.fromString(args[1]):null));
                else if(action.equals("leave")&&args.length==1) sender.sendMessage(manager.leaveArena(player));
                else sender.sendMessage("/events join [eventId] 或 /events leave");
                return true;
            }
            if(action.equals("wallet")||action.equals("rewards")) {
                if(!(sender instanceof Player player)) { sender.sendMessage("Player only"); return true; }
                sender.sendMessage("§6個人事件素材錢包：");
                var balances=manager.wallet(player.getUniqueId());
                if(balances.isEmpty()) sender.sendMessage("§7目前沒有素材。");
                balances.forEach((key,count)->sender.sendMessage("§b"+EventRewardService.MATERIALS.getOrDefault(key,key)+" §fx"+count));
                return true;
            }
            if(action.equals("claim")) {
                if(!(sender instanceof Player player)||args.length!=2) { sender.sendMessage("/events claim <eventId>"); return true; }
                sender.sendMessage(manager.claim(UUID.fromString(args[1]),player.getUniqueId())); return true;
            }
            if(!action.equals("info")&&!action.equals("list")) { sender.sendMessage("/events [info|list|join [eventId]|leave|wallet|claim <eventId>]"); return true; }
            sender.sendMessage("§6世界事件： §7"+manager.diagnostic());
            List<WorldEventInstance> events=new ArrayList<>(manager.events()); Collections.reverse(events);
            events.stream().limit(10).forEach(e->describe(sender,e));
        } catch(IllegalArgumentException e) { sender.sendMessage("§c指令參數或 Event ID 無效。"); }
        catch(RuntimeException e) { sender.sendMessage("§c事件操作失敗；恢復資料保留。請查看伺服器紀錄。"); }
        return true;
    }
    private boolean admin(CommandSender sender,String[] args) {
        if(!sender.hasPermission("customrpg.event.admin")) { sender.sendMessage("§c沒有管理事件的權限。"); return true; }
        if(args.length<2||!args[0].equalsIgnoreCase("event")) { help(sender); return true; }
        String action=args[1].toLowerCase(Locale.ROOT);
        if(action.equals("test")) {
            if(args.length!=4||!args[3].equalsIgnoreCase("here")) { sender.sendMessage("/mmorpg event test meteor here"); return true; }
            if(!(sender instanceof Player player)) { sender.sendMessage("§c測試模式必須由玩家指定目前位置。"); return true; }
            sender.sendMessage(manager.requestTestStart(args[2].toLowerCase(Locale.ROOT),player.getLocation(),sender::sendMessage)); return true;
        }
        if(action.equals("start")) {
            if(args.length<3||args.length>4||args.length==4&&!args[3].equalsIgnoreCase("here")) { help(sender); return true; }
            if(args.length==4&&!(sender instanceof Player)) { sender.sendMessage("here requires a player"); return true; }
            sender.sendMessage(manager.requestStart(args[2].toLowerCase(Locale.ROOT),args.length==4?((Player)sender).getLocation():null,sender::sendMessage)); return true;
        }
        if(action.equals("list")||action.equals("info")) {
            sender.sendMessage(manager.diagnostic()); manager.events().forEach(e->describe(sender,e)); return true;
        }
        if(Set.of("stop","cleanup","debug").contains(action)&&args.length==3) {
            WorldEventInstance event=manager.find(UUID.fromString(args[2]));
            if(event==null) { sender.sendMessage("Unknown event ID"); return true; }
            sender.sendMessage(action.equals("debug")?manager.debug(event):manager.stop(event.id)); return true;
        }
        help(sender); return true;
    }
    private void describe(CommandSender sender,WorldEventInstance e) {
        long remaining=Math.max(0,(e.expiresAt-System.currentTimeMillis())/1000);
        sender.sendMessage("§e"+e.type+(e.testMode?" [管理員測試]":"")+" §7"+e.id+" §f"+e.state+"\n§7"+e.worldName+" X:"+e.x+" Y:"+e.y+" Z:"+e.z
                +" §bLv."+e.minLevel+"～"+e.maxLevel+" §7剩餘 "+remaining+" 秒；討伐 "+e.mobs.values().stream().filter(m->m.state==WorldEventInstance.MobState.DEAD).count()+"/"+e.mobs.size());
    }
    private void help(CommandSender sender) {
        sender.sendMessage("/mmorpg event <list|info|start meteor [here]|test meteor here|stop <id>|cleanup <id>|debug <id>>");
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        if(command.getName().equalsIgnoreCase("mmorpg")) {
            if(!sender.hasPermission("customrpg.event.admin")) return List.of();
            if(args.length==1) return List.of("event");
            if(args.length==2) return List.of("list","info","start","test","stop","cleanup","debug");
            if(args.length==3&&(args[1].equalsIgnoreCase("start")||args[1].equalsIgnoreCase("test"))) return List.of("meteor");
            if(args.length==4&&(args[1].equalsIgnoreCase("start")||args[1].equalsIgnoreCase("test"))) return List.of("here");
        } else if(args.length==1) return List.of("info","list","join","leave","wallet","claim");
        return List.of();
    }
}

package com.customrpg.vfx;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

/** Admin-only previews never call a spell, damage service, mana manager or cooldown manager. */
public final class VfxCommand implements CommandExecutor,TabCompleter {
    private final BukkitVfxService service;
    public VfxCommand(BukkitVfxService service){this.service=service;}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("customrpg.vfx.admin")){sender.sendMessage("§c沒有特效預覽權限。");return true;}
        String action=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
        if(action.equals("stats")){
            var stats=service.runtime().stats();var limits=service.runtime().limits();
            sender.sendMessage("§eVFX：實例 "+stats.active()+"／"+limits.instances()+"；上tick取樣 "+stats.sampled()+"／"+limits.samples()+"，粒子發送 "+stats.sent()+"／"+limits.globalSends());
            sender.sendMessage("§7發送檢查 "+stats.deliveryChecks()+"／"+limits.deliveryChecks()+"；取樣裁切 "+stats.rejectedSamples()+"，發送裁切 "+stats.rejectedSends()+"；聲音 "+stats.soundSends()+"；Runtime CPU "+String.format(Locale.ROOT,"%.3f",stats.frameNanos()/1_000_000.0)+"ms");
            sender.sendMessage("§7臨時尖牙："+service.fangCount()+"／128（僅視覺、無原版傷害）");
            var chickens=service.chickenStats();if(chickens!=null)sender.sendMessage("§7養雞：召喚實體 "+chickens.bodies()+"（幼雞無上限），飛蛋 "+chickens.eggs()+"／128，原生預覽 "+chickens.previews()+"／16；場域 "+chickens.fields()+"，延遲事件 "+chickens.queued()+"／256");
            return true;
        }
        if(!(sender instanceof Player player)){sender.sendMessage("此操作需要玩家在遊戲中執行。");return true;}
        switch(action){
            case "preview"->{
                String id=args.length>1?VfxEffects.resolve(args[1]):null;
                if(id==null){player.sendMessage("§e/rpgvfx preview <skillId>；Tab可查看冰霜、大地、暗黑、死靈、養雞與展示樣本。");return true;}
                if(service.preview(player,id,0))player.sendMessage("§a預覽："+VfxEffects.design(id).name()+"；僅自己可見，不消耗技能資源。");
                else player.sendMessage("§c目前無法啟動預覽，請確認模式、已載入區域或特效名額。");
            }
            case "showcase"->{
                service.stopPreview(player);int started=0;
                if(service.preview(player,VfxEffects.SLASH,0))started++;
                if(service.preview(player,VfxEffects.THUNDER,30))started++;
                if(service.preview(player,VfxEffects.VOID,80))started++;
                player.sendMessage("§a展示已排入 "+started+"／3 段：斬擊、雷霆、虛空。/rpgvfx stop 停止。");
            }
            case "stop"->{service.stopPreview(player);player.sendMessage("§a已停止你的特效預覽。");}
            case "quality"->{
                try{var quality=VfxRuntime.Quality.valueOf(args.length>1?args[1].toUpperCase(Locale.ROOT):"");service.quality(player,quality);player.sendMessage("§a你的特效品質："+quality);}
                catch(IllegalArgumentException error){player.sendMessage("§e/rpgvfx quality <low|medium|high>；目前 "+service.quality(player));}
            }
            default->player.sendMessage("§e/rpgvfx preview <技能ID> | showcase | stop | quality <low|medium|high> | stats");
        }
        return true;
    }
    public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!sender.hasPermission("customrpg.vfx.admin"))return List.of();
        List<String> values=List.of();
        if(args.length==1)values=List.of("preview","showcase","stop","quality","stats");
        else if(args.length==2&&args[0].equalsIgnoreCase("preview")){var ids=new ArrayList<String>();ids.add("w01");for(var dna:VfxEffects.designs())ids.add(dna.id());values=ids;}
        else if(args.length==2&&args[0].equalsIgnoreCase("quality"))values=List.of("low","medium","high");
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(value->value.startsWith(prefix)).toList();
    }
}

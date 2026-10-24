package com.customrpg.talents;

import java.util.*;

/** Eight current abilities; legacy ranks and bindings resolve without rewriting their stored IDs. */
public enum DarkSkill {
    REND("dark_rend","裂影裁決",12,7,3,12,37),
    STITCH("dark_stitch","縫魂咒紋",16,17,2,11,39),
    STEP("dark_step","換影步",17,16,0,6,41),
    CURTAIN("dark_curtain","斷夜帷幕",25,24,4,5,28),
    ECHO("dark_echo","孽影回聲",21,22,0,10,30),
    SERVANT("dark_servant","烏影僕從",27,28,2,6,32),
    FRACTURE("dark_fracture","深淵折盾",20,25,0,8,19),
    ECLIPSE("dark_eclipse","黯日墜界・永夜裁決",70,110,13,12,4);
    public final String id,title;public final int mana,cooldown,range,slot;public final double damage;
    DarkSkill(String id,String title,int mana,int cooldown,double damage,int range,int slot){this.id=id;this.title=title;this.mana=mana;this.cooldown=cooldown;this.damage=damage;this.range=range;this.slot=slot;}
    private static final Map<String,String> LEGACY=Map.ofEntries(
        Map.entry("shadow_bolt",REND.id),Map.entry("soul_drain",REND.id),
        Map.entry("curse_mark",STITCH.id),Map.entry("weakening_curse",STITCH.id),
        Map.entry("blood_sacrifice",STEP.id),Map.entry("dark_flame",CURTAIN.id),
        Map.entry("death_mark",ECHO.id),Map.entry("soul_burn",SERVANT.id),Map.entry("dark_shield",FRACTURE.id),
        Map.entry("void_eruption",ECLIPSE.id),Map.entry("reaper_domain",ECLIPSE.id),Map.entry("dark_apotheosis",ECLIPSE.id));
    public static Map<String,String> legacy(){return LEGACY;}
    public static String canonical(String id){return id==null?null:LEGACY.getOrDefault(id,id);}
    public static DarkSkill of(String id){for(var s:values())if(s.id.equals(id))return s;return null;}
    public static boolean handles(String id){return of(id)!=null;}
    public static boolean ultimate(String id){return ECLIPSE.id.equals(id);}
    public static int rank(Map<String,Integer> levels,String id){String current=canonical(id);int n=levels.getOrDefault(current,0);for(var e:LEGACY.entrySet())if(e.getValue().equals(current))n=Math.max(n,levels.getOrDefault(e.getKey(),0));return Math.clamp(n,0,3);}
    public static String resolve(String id){if(id==null)return null;String canonical=canonical(id.toLowerCase(Locale.ROOT));if(handles(canonical))return canonical;for(var s:values())if(s.name().equalsIgnoreCase(id))return s.id;return null;}
    public static double mana(Talent t,int rank){var s=of(t.getId());return s==STEP||s==ECHO||s==FRACTURE?Math.round(t.getManaCost()*(1-.1*(Math.clamp(rank,1,3)-1))*100)/100.0:t.getManaCost();}
}

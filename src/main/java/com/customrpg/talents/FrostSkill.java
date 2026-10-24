package com.customrpg.talents;

import java.util.*;

/** Stable IDs retain the four original learned ranks; the frost ultimate is independent of retired core trees. */
public enum FrostSkill {
    PIERCER("frost_shard","極冰穿心",10,6,7,14,37),
    FANGS("frost_nova","寒棘追獵",18,12,8,8,39),
    STEP("frost_step","霜影瞬步",16,14,0,5,41),
    MIRROR("frost_mirror","凝晶護鏡",20,24,3,12,28),
    CROSS("frost_lance","冰縛十字",18,18,0,10,30),
    MIST("frost_blizzard","寒潮霧域",26,26,2,12,32),
    PRISM("frost_prism","冰棱折光",22,17,6,12,19),
    CROWN("frost_crown","零度王權",65,100,16,12,4);
    public final String id,title; public final int mana,cooldown,range,slot; public final double damage;
    FrostSkill(String id,String title,int mana,int cooldown,double damage,int range,int slot) {
        this.id=id;this.title=title;this.mana=mana;this.cooldown=cooldown;this.damage=damage;this.range=range;this.slot=slot;
    }
    public static FrostSkill of(String id) { return Arrays.stream(values()).filter(s->s.id.equals(id)).findFirst().orElse(null); }
    public static boolean handles(String id) { return of(id)!=null; }
    public static boolean ultimate(String id) { return CROWN.id.equals(id); }
    public static double mana(Talent talent,int rank) {
        var skill=of(talent.getId());double cost=talent.getManaCost();
        return skill==STEP||skill==CROSS?Math.round(cost*(1-.1*(Math.clamp(rank,1,3)-1))*100)/100.0:cost;
    }
    public static String resolve(String input) {
        if(input==null)return null;
        for(var skill:values())if(skill.id.equalsIgnoreCase(input)||skill.name().equalsIgnoreCase(input))return skill.id;
        return null;
    }
}

package com.customrpg.talents;

import java.util.Locale;

/** Death is commanded through a bounded roster, never through stacks or corpse requirements. */
public enum NecroSkill {
    LANCE("necro_lance","骨脊裂矛",12,7,8,14,37),
    SUMMON("necro_summon","墓土喚骸",26,28,0,12,39),
    COMMAND("necro_command","喪鐘號令",13,13,3,12,41),
    PRISON("necro_prison","骨棺禁錮",20,20,4,10,28),
    LANTERN("necro_lantern","冥燈庇魂",21,24,0,12,30),
    SACRIFICE("necro_sacrifice","亡骸祭爆",18,22,9,10,32),
    COLOSSUS("necro_colossus","骸骨縫合・葬骨巨衛",22,26,0,12,19),
    GATE("necro_gate","冥府開門・百骸行軍",75,120,4,14,4);
    public final String id,title;public final int mana,cooldown,range,slot;public final double damage;
    NecroSkill(String id,String title,int mana,int cooldown,double damage,int range,int slot){this.id=id;this.title=title;this.mana=mana;this.cooldown=cooldown;this.damage=damage;this.range=range;this.slot=slot;}
    public static NecroSkill of(String id){for(var s:values())if(s.id.equals(id))return s;return null;}
    public static boolean handles(String id){return of(id)!=null;}
    public static boolean ultimate(String id){return GATE.id.equals(id);}
    public static String resolve(String id){if(id==null)return null;for(var s:values())if(s.id.equals(id.toLowerCase(Locale.ROOT))||s.name().equalsIgnoreCase(id))return s.id;return null;}
    public static double mana(Talent t,int rank){var s=of(t.getId());return s==SUMMON||s==LANTERN||s==COLOSSUS?Math.round(t.getManaCost()*(1-.1*(Math.clamp(rank,1,3)-1))*100)/100.0:t.getManaCost();}
}

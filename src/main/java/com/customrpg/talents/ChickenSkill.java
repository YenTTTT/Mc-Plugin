package com.customrpg.talents;
import java.util.Locale;

public enum ChickenSkill {
    EGG("chicken_egg","丟雞蛋",3,1.5,2,12,37),
    FEED("chicken_feed","撒米開飯",15,18,0,10,39),
    SUPPLY("chicken_supply","營養雙黃蛋",20,22,0,10,41),
    DAWN("chicken_dawn","破曉雞鳴",22,24,2,6,28),
    NEST("chicken_nest","母雞護巢",24,28,0,8,30),
    ROOSTER("chicken_rooster","鬥雞突襲",18,16,2,10,32),
    STAMPEDE("chicken_stampede","雞群衝鋒",28,25,6,10,19),
    GOD("chicken_god","雞神降世・神雞天墜",75,120,16,12,4);
    public final String id,title;public final int mana,range,slot;public final double cooldown,damage;
    ChickenSkill(String id,String title,int mana,double cooldown,double damage,int range,int slot){this.id=id;this.title=title;this.mana=mana;this.cooldown=cooldown;this.damage=damage;this.range=range;this.slot=slot;}
    public double spiritCoefficient(){return switch(this){case EGG->.12;case DAWN->.15;case ROOSTER->.08;case STAMPEDE->.25;case GOD->.60;default->0;};}
    public static double damage(Talent t,int rank,double spirit,int babies,boolean godPeck){var s=of(t.getId());if(s==null)return 0;double power=Double.isFinite(spirit)?Math.max(0,spirit):0;if(godPeck)return 2+.10*power;double coefficient=t.getEffectDouble(rank,"spiritScaling",s.spiritCoefficient()*(s==GOD?1:1+.1*(Math.clamp(rank,1,3)-1)));return t.getEffectDouble(rank,"baseDamage",s.damage)+coefficient*power+(s==STAMPEDE?Math.max(0,babies):0);}
    public static ChickenSkill of(String id){for(var s:values())if(s.id.equals(id))return s;return null;}
    public static boolean handles(String id){return of(id)!=null;}
    public static boolean ultimate(String id){return GOD.id.equals(id);}
    public static String resolve(String id){if(id==null)return null;for(var s:values())if(s.id.equals(id.toLowerCase(Locale.ROOT))||s.name().equalsIgnoreCase(id))return s.id;return null;}
    public static double mana(Talent t,int rank){var s=of(t.getId());return s==FEED||s==SUPPLY||s==NEST?Math.round(t.getManaCost()*(1-.1*(Math.clamp(rank,1,3)-1))*100)/100.0:t.getManaCost();}
}

package com.customrpg.talents;

import java.util.*;

/** Original four IDs keep their ranks; a single shared ultimate slot serves both redesigned branches. */
public enum EarthSkill {
    STRIKE("earth_shockwave","斷岩重擊",12,9,6,4,37),
    AEGIS("earth_guard","山紋壁盾",22,22,0,0,39),
    GRASP("earth_grasp","地脈牽引",18,17,0,7,41),
    SINK("earth_bastion","沉岩陷域",26,24,3,12,28),
    OATH("earth_oath","守護岩契",24,30,0,6,30),
    RUSH("earth_rush","震步援護",18,20,0,9,32),
    REPRISAL("earth_reprisal","岩心反震",23,26,5,5,19),
    TITAN("earth_titan","泰坦鎮界・萬岳降臨",70,110,10,7,4);
    public final String id,title;public final int mana,cooldown,range,slot;public final double damage;
    EarthSkill(String id,String title,int mana,int cooldown,double damage,int range,int slot){this.id=id;this.title=title;this.mana=mana;this.cooldown=cooldown;this.damage=damage;this.range=range;this.slot=slot;}
    public static EarthSkill of(String id){for(var s:values())if(s.id.equals(id))return s;return null;}
    public static boolean handles(String id){return of(id)!=null;}
    public static boolean ultimate(String id){return TITAN.id.equals(id);}
    public boolean defensive(){return this==AEGIS||this==OATH||this==RUSH||this==REPRISAL||this==TITAN;}
    public static String resolve(String id){if(id!=null)for(var s:values())if(s.id.equalsIgnoreCase(id)||s.name().equalsIgnoreCase(id))return s.id;return null;}
    public static double mana(Talent talent,int rank){var s=of(talent.getId());return s==AEGIS||s==GRASP||s==OATH||s==RUSH?Math.round(talent.getManaCost()*(1-.1*(Math.clamp(rank,1,3)-1))*100)/100.0:talent.getManaCost();}
}

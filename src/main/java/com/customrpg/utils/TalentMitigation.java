package com.customrpg.utils;

import java.util.*;
import org.bukkit.event.entity.EntityDamageEvent;

/** Tracks this plugin's pre-balance reductions so Earth defense can cap their combined result. */
public final class TalentMitigation {
    private static final Map<EntityDamageEvent,Double> FACTORS=new WeakHashMap<>();
    private TalentMitigation(){}
    public static void reduce(EntityDamageEvent event,double reduction){double factor=1-Math.clamp(reduction,0,.95);FACTORS.merge(event,factor,(a,b)->a*b);event.setDamage(event.getDamage()*factor);}
    public static double baseline(EntityDamageEvent event,double raw){return raw/Math.max(.001,FACTORS.getOrDefault(event,1.0));}
    public static void finish(EntityDamageEvent event){FACTORS.remove(event);}
}

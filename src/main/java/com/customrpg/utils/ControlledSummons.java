package com.customrpg.utils;
import org.bukkit.entity.Entity;
/** Shared exclusions for drops, rewards and automatic assists; individual engines own their lifecycles. */
public final class ControlledSummons {
    private ControlledSummons(){}
    public static boolean is(Entity e){return ControlledUndead.is(e)||ControlledChicken.is(e);}
}

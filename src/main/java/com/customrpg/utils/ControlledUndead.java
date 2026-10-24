package com.customrpg.utils;

import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/** A spawn-consumer tag is visible before spawn listeners run. No combat or registry initialization. */
public final class ControlledUndead {
    public static final NamespacedKey OWNER=new NamespacedKey("customrpg","necromancer_owner");
    private ControlledUndead(){}
    public static boolean is(Entity e){return e!=null&&e.getPersistentDataContainer()!=null&&e.getPersistentDataContainer().has(OWNER,PersistentDataType.STRING);}
    public static void mark(Entity e,UUID owner){e.getPersistentDataContainer().set(OWNER,PersistentDataType.STRING,owner.toString());}
}

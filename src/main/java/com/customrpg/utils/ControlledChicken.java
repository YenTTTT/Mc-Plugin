package com.customrpg.utils;

import java.util.UUID;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.persistence.PersistentDataType;

/** Chicken ownership is written before spawn events. Only these tags change chicken behavior. */
public final class ControlledChicken {
    public enum Kind {BABY(4,240),ROOSTER(4,80),HEALER(4,200),GOD(40,630);public final int hp,ticks;Kind(int hp,int ticks){this.hp=hp;this.ticks=ticks;}}
    public static final NamespacedKey OWNER=new NamespacedKey("customrpg","chicken_owner"),PREVIEW=new NamespacedKey("customrpg","chicken_preview"),EGG=new NamespacedKey("customrpg","chicken_skill_egg");
    private ControlledChicken(){}
    public static boolean is(Entity e){return e!=null&&e.getPersistentDataContainer()!=null&&e.getPersistentDataContainer().has(OWNER,PersistentDataType.STRING);}
    public static UUID owner(Entity e){if(!is(e))return null;try{return UUID.fromString(e.getPersistentDataContainer().get(OWNER,PersistentDataType.STRING));}catch(RuntimeException invalid){return null;}}
    public static boolean preview(Entity e){return is(e)&&e.getPersistentDataContainer().has(PREVIEW,PersistentDataType.BYTE);}
    public static boolean egg(Entity e){return e!=null&&e.getPersistentDataContainer()!=null&&e.getPersistentDataContainer().has(EGG,PersistentDataType.BYTE);}
    public static Chicken spawn(Player owner,Location at,Kind kind,boolean preview){
        Chicken e=at.getWorld().spawn(at,Chicken.class,org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM,c->{
            c.getPersistentDataContainer().set(OWNER,PersistentDataType.STRING,owner.getUniqueId().toString());if(preview)c.getPersistentDataContainer().set(PREVIEW,PersistentDataType.BYTE,(byte)1);
            if(kind==Kind.BABY)c.setBaby();else c.setAdult();c.setAgeLock(true);c.setBreed(false);c.setEggLayTime(Integer.MAX_VALUE);
            c.setAI(false);c.setGravity(false);c.setCollidable(false);c.setPersistent(false);c.setRemoveWhenFarAway(false);c.setSilent(true);c.setInvulnerable(preview);
            var hp=c.getAttribute(Attribute.MAX_HEALTH);if(hp==null)throw new IllegalStateException("Chicken missing health");hp.setBaseValue(kind.hp);c.setHealth(kind.hp);
            if(kind==Kind.GOD){var scale=c.getAttribute(Attribute.SCALE);if(scale==null)throw new IllegalStateException("Chicken SCALE unavailable");scale.setBaseValue(10);c.setCustomName("§6§l【神獸】雞神大人");c.setCustomNameVisible(true);}
            else if(kind==Kind.ROOSTER){c.setCustomName("§c鬥雞");c.setCustomNameVisible(true);}
            if(preview)c.setVisibleByDefault(false);
        });
        return e;
    }
}

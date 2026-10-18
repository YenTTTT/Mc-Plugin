package com.customrpg.worldevent.mob;

import com.customrpg.CustomRPG;
import com.customrpg.worldevent.core.*;
import com.customrpg.worldevent.persistence.WorldEventRepository;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import java.io.*;
import java.util.*;

public final class EventMobSpawner {
    public static final NamespacedKey EVENT_ID=new NamespacedKey("customrpg","event_id");
    public static final NamespacedKey MOB_ID=new NamespacedKey("customrpg","event_mob_id");
    public static final NamespacedKey EVENT_TYPE=new NamespacedKey("customrpg","event_type");
    public static final NamespacedKey EVENT_LEVEL=new NamespacedKey("customrpg","event_level");
    public static final NamespacedKey EVENT_RANK=new NamespacedKey("customrpg","event_rank");
    private final CustomRPG plugin;
    private final WorldEventRepository repository;
    public EventMobSpawner(CustomRPG plugin,WorldEventRepository repository) { this.plugin=plugin; this.repository=repository; }

    public void plan(WorldEventInstance event,WorldEventDefinition definition) throws IOException {
        if(!event.mobs.isEmpty()) throw new IllegalStateException("Event mob registry already allocated");
        for(var group:definition.mobs()) {
            if(plugin.getMobManager().getMobData(group.template())==null) throw new IllegalStateException("Missing event mob template: "+group.template());
            for(int i=0;i<group.count();i++) {
                var mob=new WorldEventInstance.EventMob(UUID.randomUUID(),group.template(),group.rank(),event.level);
                event.mobs.put(mob.id,mob);
            }
        }
        repository.save();
    }

    /** PLANNED -> SPAWNING is durable BEFORE spawning. UNLOADED/DEAD/SPAWNING are never retried. */
    public boolean spawnNext(WorldEventInstance event,World world) throws IOException {
        int index=0;
        for(var mob:event.mobs.values()) {
            if(mob.state!=WorldEventInstance.MobState.PLANNED) { index++; continue; }
            Location spawn=findSpawn(event,world,index);
            if(spawn==null) throw new IllegalStateException("No safe event mob spawn position");
            if(!mob.beginSpawn()) return false;
            repository.save();
            java.util.function.Consumer<LivingEntity> beforeSpawn=living -> {
                tag(living,event); living.getPersistentDataContainer().set(MOB_ID,PersistentDataType.STRING,mob.id.toString());
                living.getPersistentDataContainer().set(EVENT_RANK,PersistentDataType.STRING,mob.rank);
                living.setPersistent(true); living.setRemoveWhenFarAway(false);
                if(living instanceof Mob ai) ai.setCanPickupItems(false);
                mob.entity=living.getUniqueId();
                mob.chunkX=spawn.getBlockX()>>4; mob.chunkZ=spawn.getBlockZ()>>4;
                try { repository.save(); } catch(IOException e) { throw new UncheckedIOException(e); }
            };
            LivingEntity entity=event.testMode?plugin.getMobManager().spawnTestEventMob(mob.template,spawn,mob.level,beforeSpawn)
                    :plugin.getMobManager().spawnEventMob(mob.template,spawn,mob.level,beforeSpawn);
            if(entity==null||!entity.isValid()||entity.isDead()) throw new IllegalStateException("Event mob spawn cancelled: "+mob.template);
            plugin.getMobManager().setMobTier(entity,mob.rank.equals("TANK")?"NORMAL":mob.rank);
            if(mob.rank.equals("ELITE")||mob.rank.equals("BOSS")) entity.setGlowing(true);
            mob.state=WorldEventInstance.MobState.ALIVE;
            repository.save();
            return true;
        }
        return false;
    }

    public Location findSpawn(WorldEventInstance event,World world,int index) {
        for(int attempt=0;attempt<24;attempt++) {
            double angle=index*2.3999632297+attempt*0.37;
            double radius=Math.max(4,event.radius*(0.55+(attempt%3)*0.1));
            int x=event.x+(int)Math.round(Math.cos(angle)*radius),z=event.z+(int)Math.round(Math.sin(angle)*radius);
            if(!world.isChunkLoaded(x>>4,z>>4)) continue;
            int y=world.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES)+1;
            Location candidate=new Location(world,x+0.5,y,z+0.5);
            Location safe=event.testMode?plugin.getMobManager().findSafeSpawnLocationExactForTesting(candidate)
                    :plugin.getMobManager().findSafeSpawnLocationExact(candidate);
            if(safe!=null) return safe;
        }
        return null;
    }

    public static void tag(Entity entity,WorldEventInstance event) {
        entity.getPersistentDataContainer().set(EVENT_ID,PersistentDataType.STRING,event.id.toString());
        entity.getPersistentDataContainer().set(EVENT_TYPE,PersistentDataType.STRING,event.type);
        entity.getPersistentDataContainer().set(EVENT_LEVEL,PersistentDataType.INTEGER,event.level);
    }
    public static UUID eventId(Entity entity) { return uuid(entity,EVENT_ID); }
    public static UUID mobId(Entity entity) { return uuid(entity,MOB_ID); }
    private static UUID uuid(Entity entity,NamespacedKey key) {
        String raw=entity.getPersistentDataContainer().get(key,PersistentDataType.STRING);
        try { return raw==null?null:UUID.fromString(raw); } catch(IllegalArgumentException e) { return null; }
    }
}

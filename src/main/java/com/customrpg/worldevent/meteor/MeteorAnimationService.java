package com.customrpg.worldevent.meteor;

import com.customrpg.worldevent.core.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;
import java.util.function.Consumer;

/** Reusable visual-only falling meteor for world events and talent casts. */
public final class MeteorAnimationService implements WorldEventDefinition.Animation {
    private final Location impact;
    private final World world;
    private final int duration, height;
    private final boolean particles,sounds;
    private BlockDisplay display;
    private int age;
    public MeteorAnimationService(JavaPlugin plugin,WorldEventInstance event,World world,int duration,int height,
                                  boolean enabled,boolean particles,boolean sounds,Consumer<Entity> tag) {
        this(new Location(world,event.x+0.5,event.y+1,event.z+0.5),duration,height,enabled,particles,sounds,tag);
    }
    /** Visual-only skill animation; no event identity, terrain edits, mobs, or combat logic. */
    public MeteorAnimationService(Location impact,int duration,int height,boolean particles,boolean sounds) {
        this(impact,duration,height,true,particles,sounds,entity->{});
    }
    private MeteorAnimationService(Location impact,int duration,int height,boolean enabled,boolean particles,
                                   boolean sounds,Consumer<Entity> tag) {
        this.impact=impact.clone(); this.world=Objects.requireNonNull(impact.getWorld(),"meteor world");
        this.duration=enabled?duration:1; this.height=height; this.particles=particles; this.sounds=sounds;
        if(enabled) {
            display=world.spawn(point(0),BlockDisplay.class,entity -> {
                tag.accept(entity); entity.setPersistent(false); entity.setBlock(Material.MAGMA_BLOCK.createBlockData());
                entity.setDisplayWidth(4); entity.setDisplayHeight(4); entity.setTeleportDuration(1);
                entity.setBrightness(new Display.Brightness(15,15));
                entity.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-1.5f,0,-1.5f),
                        new org.joml.Quaternionf(),new org.joml.Vector3f(3),new org.joml.Quaternionf()));
            });
        }
    }
    private Location point(double progress) {
        double start=Math.min(world.getMaxHeight()-3,impact.getY()-1+height);
        return new Location(world,impact.getX(),start*(1-progress)+impact.getY()*progress,impact.getZ());
    }
    @Override public boolean tick() {
        age++;
        double progress=Math.min(1,age/(double)duration);
        Location point=point(progress);
        if(display!=null) {
            if(!display.isValid()||!world.isChunkLoaded(point.getBlockX()>>4,point.getBlockZ()>>4)) throw new IllegalStateException("Meteor display lost/unloaded");
            if(!display.teleport(point)) throw new IllegalStateException("Meteor display movement cancelled");
        }
        if(particles && age%2==0) for(Player player:world.getNearbyPlayers(point,128)) {
            player.spawnParticle(Particle.FLAME,point,8,0.5,0.5,0.5,0.01);
            player.spawnParticle(Particle.SMOKE,point,5,0.7,0.7,0.7,0.02);
        }
        if(sounds && age==Math.max(1,duration-Math.min(20,Math.max(3,duration/5)))) for(Player player:world.getNearbyPlayers(impact,128)) {
            player.playSound(player.getLocation(),Sound.BLOCK_BEACON_DEACTIVATE,0.6f,0.6f);
        }
        if(progress<1) return false;
        close();
        impact(impact,particles,sounds);
        return true;
    }
    public static void impact(World world,WorldEventInstance event,boolean particles,boolean sounds) {
        impact(new Location(world,event.x+0.5,event.y+1,event.z+0.5),particles,sounds);
    }
    public static void impact(Location center,boolean particles,boolean sounds) {
        World world=Objects.requireNonNull(center.getWorld(),"meteor world");
        for(Player player:world.getNearbyPlayers(center,128)) {
            if(particles) {
                player.spawnParticle(Particle.EXPLOSION_EMITTER,center,1);
                player.spawnParticle(Particle.LARGE_SMOKE,center,45,5,2,5,0.03);
            }
            if(sounds) player.playSound(center,Sound.ENTITY_GENERIC_EXPLODE,1.0f,0.7f);
        }
    }
    @Override public void close() { if(display!=null) { display.remove(); display=null; } }
}

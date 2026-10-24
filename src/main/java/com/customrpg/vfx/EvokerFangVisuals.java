package com.customrpg.vfx;

import com.customrpg.CustomRPG;
import com.customrpg.vfx.VfxRuntime.*;
import com.customrpg.vfx.VfxRuntime.Effect;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.function.Predicate;

/** Native jaws are visual only. Tokens share the particle runtime's admission and cancellation. */
final class EvokerFangVisuals {
    static final NamespacedKey VISUAL=new NamespacedKey("customrpg","visual_evoker_fang");
    static final int GLOBAL_LIMIT=128,OWNER_LIMIT=14,LIFETIME=48;
    private record Fang(UUID token,Player owner,World world,Mode mode,EvokerFangs entity,long until){}
    private final CustomRPG plugin;
    private final List<Fang> active=new ArrayList<>();
    private long tick;
    EvokerFangVisuals(CustomRPG plugin){this.plugin=plugin;}
    static Effect lifetime(){return new Effect(){public int duration(){return LIFETIME;}public void frame(int age,Canvas c){if(age==6)c.sound(Layer.OUTLINE,"entity.evoker_fangs.attack",.5f,1);}};}
    static boolean tagged(Entity e){return e!=null&&e.getPersistentDataContainer()!=null&&e.getPersistentDataContainer().has(VISUAL,PersistentDataType.BYTE);}
    static boolean nativeDamage(EntityDamageEvent e){
        if(e instanceof EntityDamageByEntityEvent hit&&tagged(hit.getDamager()))return true;
        var source=e.getDamageSource();return source!=null&&(tagged(source.getDirectEntity())||tagged(source.getCausingEntity()));
    }
    private boolean loaded(Location at){return at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4);}
    private Location floor(Location sample){
        var w=sample.getWorld();if(!loaded(sample)||!w.getWorldBorder().isInside(sample))return null;
        for(int y=sample.getBlockY();y>=sample.getBlockY()-3;y--){
            if(y<w.getMinHeight()||y+1>=w.getMaxHeight())continue;
            var ground=w.getBlockAt(sample.getBlockX(),y,sample.getBlockZ());
            var above=w.getBlockAt(sample.getBlockX(),y+1,sample.getBlockZ());
            if(ground.isPassable()||ground.isLiquid()||!above.isPassable()||above.isLiquid())continue;
            double top=ground.getBoundingBox().getMaxY();
            if(top>sample.getY()+.1||sample.getY()-top>3)continue;
            return new Location(w,sample.getX(),top+.01,sample.getZ(),sample.getYaw(),0);
        }
        return null;
    }
    int play(UUID token,Player owner,Location origin,Vector direction,double range,Mode mode,Quality quality,int delay){
        if(!Double.isFinite(range)||range<=0||range>14||delay<0||delay>200||!loaded(origin))return 0;
        int owned=(int)active.stream().filter(f->f.owner.equals(owner)).count(),spawned=0;
        var dir=direction.clone().normalize();
        for(int segment=0;segment<7;segment++){
            if(quality==Quality.LOW&&segment%2!=0)continue;
            if(owned>=OWNER_LIMIT||active.size()>=GLOBAL_LIMIT)break;
            double distance=segment*2+1;if(distance>range)break;
            var sample=origin.clone().add(dir.clone().multiply(distance));
            boolean pathLoaded=true;for(double d=0;d<=distance;d+=.5)if(!loaded(origin.clone().add(dir.clone().multiply(d)))){pathLoaded=false;break;}
            if(!pathLoaded||!loaded(sample))break;
            // Ground projection is only a visual. Existing eye-level combat ray is untouched.
            var wall=origin.getWorld().rayTraceBlocks(origin,dir,distance,FluidCollisionMode.NEVER,true);
            if(wall!=null)break;
            var at=floor(sample);if(at==null)continue;
            at.setDirection(new Vector(dir.getX(),0,dir.getZ()).lengthSquared()<.001?new Vector(0,0,1):new Vector(dir.getX(),0,dir.getZ()));
            final int warmup=delay+5+segment;
            EvokerFangs fang=null;
            try{
                fang=at.getWorld().spawn(at,EvokerFangs.class,CreatureSpawnEvent.SpawnReason.CUSTOM,e->{
                    e.getPersistentDataContainer().set(VISUAL,PersistentDataType.BYTE,(byte)1);
                    e.setOwner(null);e.setAttackDelay(warmup);e.setPersistent(false);e.setInvulnerable(true);e.setSilent(true);e.setVisibleByDefault(false);
                });
                if(fang==null||!fang.isValid()){if(fang!=null)fang.remove();continue;}
                active.add(new Fang(token,owner,origin.getWorld(),mode,fang,tick+delay+LIFETIME));owned++;spawned++;
                owner.showEntity(plugin,fang);
                if(mode==Mode.COMBAT){int examined=0;for(var viewer:at.getWorld().getNearbyPlayers(at,32)){
                    if(examined++>=64)break;
                    if(!viewer.equals(owner)&&viewer.isOnline()&&viewer.canSee(owner)&&viewer.getWorld().equals(at.getWorld())&&viewer.getLocation().distanceSquared(at)<=1024)viewer.showEntity(plugin,fang);
                }}
            }catch(RuntimeException error){if(fang!=null){EvokerFangs failed=fang;active.removeIf(f->f.entity.equals(failed));fang.remove();}plugin.getLogger().warning("Evoker fang visual skipped: "+error.getClass().getSimpleName());}
        }
        return spawned;
    }
    int count(){return active.size();}
    void tick(Predicate<UUID> running){
        tick++;
        active.removeIf(f->{boolean remove=tick>=f.until||!running.test(f.token)||!f.owner.isOnline()||f.owner.isDead()||!f.owner.getWorld().equals(f.world)||!f.entity.isValid();if(remove)f.entity.remove();return remove;});
    }
    void cancel(UUID owner,Mode mode){active.removeIf(f->{if(f.owner.getUniqueId().equals(owner)&&(mode==null||mode==f.mode)){f.entity.remove();return true;}return false;});}
    void clear(Mode mode){active.removeIf(f->{if(mode==null||f.mode==mode){f.entity.remove();return true;}return false;});}
    void orphan(Entity e){if(tagged(e)&&active.stream().noneMatch(f->f.entity.equals(e)))e.remove();}
}

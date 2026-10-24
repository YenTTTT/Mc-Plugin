package com.customrpg.vfx;

import com.customrpg.CustomRPG;
import com.customrpg.vfx.VfxMath.V;
import com.customrpg.vfx.VfxRuntime.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** The only Bukkit adapter: one task for every effect, exact particle locations and viewer filtering. */
public final class BukkitVfxService implements Listener {
    private final CustomRPG plugin;
    private final VfxRuntime runtime;
    private final EvokerFangVisuals fangs;
    private final Map<UUID,Quality> quality=new HashMap<>();
    private final Map<Paint,Object> particleData=new HashMap<>();
    private BukkitTask task;
    private long lastError=Long.MIN_VALUE;
    public BukkitVfxService(CustomRPG plugin){
        this.plugin=plugin;
        fangs=new EvokerFangVisuals(plugin);
        runtime=new VfxRuntime(new Adapter(),Limits.defaults(),()->System.nanoTime()/1_000_000);
    }
    public VfxRuntime runtime(){return runtime;}
    public void start(){if(task==null){for(var w:plugin.getServer().getWorlds())for(var e:w.getEntities())fangs.orphan(e);task=plugin.getServer().getScheduler().runTaskTimer(plugin,()->{runtime.tick();fangs.tick(runtime::active);},1,1);}}
    public int fangCount(){return fangs.count();}
    public com.customrpg.managers.ChickenTalentSkills.Stats chickenStats(){var skills=plugin.getTalentSkillManager();return skills==null||skills.getChicken()==null?null:skills.getChicken().stats();}
    public Quality quality(Player player){return quality.getOrDefault(player.getUniqueId(),Quality.MEDIUM);}
    public void quality(Player player,Quality selected){quality.put(player.getUniqueId(),selected);}
    private V vector(org.bukkit.util.Vector value){return new V(value.getX(),value.getY(),value.getZ());}
    private Pose pose(Player owner,Location origin,boolean impact){
        return new Pose(owner.getUniqueId(),origin.getWorld().getUID(),new V(origin.getX(),origin.getY()+(impact?0:1.1),origin.getZ()),
                vector(owner.getEyeLocation().getDirection()),Math.toRadians(owner.getLocation().getYaw()),null);
    }
    public boolean preview(Player player,String id,int delay){
        if(com.customrpg.talents.ChickenSkill.handles(id)){
            var s=com.customrpg.talents.ChickenSkill.of(id);var dir=player.getLocation().getDirection().setY(0);if(dir.lengthSquared()<.001)dir=new org.bukkit.util.Vector(0,0,1);else dir.normalize();
            var at=player.getLocation().clone();if(s==com.customrpg.talents.ChickenSkill.GOD||s==com.customrpg.talents.ChickenSkill.FEED||s==com.customrpg.talents.ChickenSkill.NEST)at.add(dir.clone().multiply(5));
            UUID token=chicken(player,id,at,dir,ChickenVfx.preview(id,42),Mode.PREVIEW,null,delay);if(token==null)return false;
            if(s==com.customrpg.talents.ChickenSkill.GOD||s==com.customrpg.talents.ChickenSkill.ROOSTER||s==com.customrpg.talents.ChickenSkill.SUPPLY||s==com.customrpg.talents.ChickenSkill.NEST){var skills=plugin.getTalentSkillManager();if(skills==null||skills.getChicken()==null||delay!=0||!skills.getChicken().preview(player,s,token,at,dir)){runtime.cancelInstance(token);return false;}}
            return true;
        }

        if(com.customrpg.talents.NecroSkill.handles(id)){
            var direction=player.getLocation().getDirection().setY(0);if(direction.lengthSquared()<.001)direction=new org.bukkit.util.Vector(0,0,1);
            var at=player.getLocation().clone();var skill=com.customrpg.talents.NecroSkill.of(id);
            if(skill==com.customrpg.talents.NecroSkill.LANCE){at=player.getEyeLocation();direction=at.getDirection();}
            if(skill==com.customrpg.talents.NecroSkill.PRISON||skill==com.customrpg.talents.NecroSkill.SACRIFICE)at.add(direction.clone().normalize().multiply(5));
            return necro(player,id,at,direction,NecroVfx.preview(id,42),Mode.PREVIEW,null,delay)!=null;
        }
        if(com.customrpg.talents.DarkSkill.handles(id)){
            var direction=player.getLocation().getDirection().setY(0);if(direction.lengthSquared()<.001)direction=new org.bukkit.util.Vector(0,0,1);
            Location at=player.getLocation().clone();var skill=com.customrpg.talents.DarkSkill.of(id);
            if(skill==com.customrpg.talents.DarkSkill.REND||skill==com.customrpg.talents.DarkSkill.STITCH||skill==com.customrpg.talents.DarkSkill.ECHO||skill==com.customrpg.talents.DarkSkill.CURTAIN||skill==com.customrpg.talents.DarkSkill.ECLIPSE)at.add(direction.clone().normalize().multiply(5));
            return dark(player,id,at,direction,DarkVfx.preview(id,42),Mode.PREVIEW,null,delay)!=null;
        }
        if(com.customrpg.talents.EarthSkill.handles(id)) {
            var direction=player.getLocation().getDirection().setY(0);if(direction.lengthSquared()<.001)direction=new org.bukkit.util.Vector(0,0,1);
            var origin=player.getLocation().clone();var s=com.customrpg.talents.EarthSkill.of(id);
            if(s==com.customrpg.talents.EarthSkill.GRASP||s==com.customrpg.talents.EarthSkill.SINK)origin.add(direction.clone().normalize().multiply(5));
            return earth(player,id,origin,direction,EarthVfx.preview(id,42),Mode.PREVIEW,null,delay)!=null;
        }
        if(com.customrpg.talents.FrostSkill.handles(id)) {
            var skill=com.customrpg.talents.FrostSkill.of(id);
            Location origin=player.getLocation().clone();org.bukkit.util.Vector direction=player.getLocation().getDirection().setY(0);
            if(direction.lengthSquared()<.001)direction=new org.bukkit.util.Vector(0,0,1);
            if(skill==com.customrpg.talents.FrostSkill.PIERCER) {origin=player.getEyeLocation();direction=origin.getDirection();}
            if(skill==com.customrpg.talents.FrostSkill.CROSS) origin.add(direction.clone().normalize().multiply(5));
            return frost(player,id,origin,direction,FrostVfx.preview(id,42),Mode.PREVIEW,null,delay)!=null;
        }
        return play(player,id,player.getLocation(),Mode.PREVIEW,false,delay,false);
    }
    public UUID frost(Player owner,String id,Location origin,org.bukkit.util.Vector direction,VfxRuntime.Effect effect,Mode mode,LivingEntity target,int delay) {
        try {
            if(task==null)return null;
            var pose=new Pose(owner.getUniqueId(),origin.getWorld().getUID(),new V(origin.getX(),origin.getY(),origin.getZ()),vector(direction),
                    Math.toRadians(owner.getLocation().getYaw()),target==null?null:target.getUniqueId());
            int priority=mode==Mode.PREVIEW?10:com.customrpg.talents.FrostSkill.ultimate(id)?100:com.customrpg.talents.FrostSkill.MIRROR.id.equals(id)?60:40;
            return runtime.play(id,pose,mode,effect,mode==Mode.PREVIEW?42:System.nanoTime(),false,delay,priority);
        }catch(RuntimeException error){report(id,error);return null;}
    }
    public UUID earth(Player owner,String id,Location origin,org.bukkit.util.Vector direction,VfxRuntime.Effect effect,Mode mode,LivingEntity target,int delay){
        try{if(task==null)return null;var at=new Pose(owner.getUniqueId(),origin.getWorld().getUID(),new V(origin.getX(),origin.getY(),origin.getZ()),vector(direction),Math.toRadians(owner.getLocation().getYaw()),target==null?null:target.getUniqueId());
            int priority=mode==Mode.PREVIEW?10:com.customrpg.talents.EarthSkill.ultimate(id)?100:com.customrpg.talents.EarthSkill.AEGIS.id.equals(id)||com.customrpg.talents.EarthSkill.OATH.id.equals(id)?60:40;
            return runtime.play(id,at,mode,effect,mode==Mode.PREVIEW?42:System.nanoTime(),false,delay,priority);
        }catch(RuntimeException error){report(id,error);return null;}
    }
    public UUID chicken(Player owner,String id,Location origin,org.bukkit.util.Vector direction,VfxRuntime.Effect effect,Mode mode,LivingEntity target,int delay){
        try{if(task==null)return null;var at=new Pose(owner.getUniqueId(),origin.getWorld().getUID(),new V(origin.getX(),origin.getY(),origin.getZ()),vector(direction),Math.toRadians(owner.getLocation().getYaw()),target==null?null:target.getUniqueId());
            int priority=mode==Mode.PREVIEW?10:com.customrpg.talents.ChickenSkill.ultimate(id)?100:com.customrpg.talents.ChickenSkill.NEST.id.equals(id)||com.customrpg.talents.ChickenSkill.SUPPLY.id.equals(id)?60:40;
            return runtime.play(id,at,mode,effect,mode==Mode.PREVIEW?42:System.nanoTime(),false,delay,priority);
        }catch(RuntimeException error){report(id,error);return null;}
    }
    public UUID dark(Player owner,String id,Location origin,org.bukkit.util.Vector direction,VfxRuntime.Effect effect,Mode mode,LivingEntity target,int delay){
        try{if(task==null)return null;var at=new Pose(owner.getUniqueId(),origin.getWorld().getUID(),new V(origin.getX(),origin.getY(),origin.getZ()),vector(direction),Math.toRadians(owner.getLocation().getYaw()),target==null?null:target.getUniqueId());
            int priority=mode==Mode.PREVIEW?10:com.customrpg.talents.DarkSkill.ultimate(id)?100:com.customrpg.talents.DarkSkill.FRACTURE.id.equals(id)||com.customrpg.talents.DarkSkill.CURTAIN.id.equals(id)?60:40;
            return runtime.play(id,at,mode,effect,mode==Mode.PREVIEW?42:System.nanoTime(),false,delay,priority);
        }catch(RuntimeException error){report(id,error);return null;}
    }
    public UUID necro(Player owner,String id,Location origin,org.bukkit.util.Vector direction,VfxRuntime.Effect effect,Mode mode,LivingEntity target,int delay){
        try{if(task==null)return null;var at=new Pose(owner.getUniqueId(),origin.getWorld().getUID(),new V(origin.getX(),origin.getY(),origin.getZ()),vector(direction),Math.toRadians(owner.getLocation().getYaw()),target==null?null:target.getUniqueId());
            int priority=mode==Mode.PREVIEW?10:com.customrpg.talents.NecroSkill.ultimate(id)?100:com.customrpg.talents.NecroSkill.COLOSSUS.id.equals(id)||com.customrpg.talents.NecroSkill.LANTERN.id.equals(id)?60:40;
            if(effect instanceof NecroVfx.Lance lance){
                UUID token=runtime.play(id,at,mode,EvokerFangVisuals.lifetime(),42,false,delay,priority);
                if(token==null)return null;
                if(fangs.play(token,owner,origin,direction,lance.range(),mode,quality(owner),delay)==0){runtime.cancelInstance(token);return null;}
                return token;
            }
            return runtime.play(id,at,mode,effect,mode==Mode.PREVIEW?42:System.nanoTime(),false,delay,priority);
        }catch(RuntimeException error){report(id,error);return null;}
    }
    public void slash(Player player,Location origin,boolean mirrored){play(player,VfxEffects.SLASH,origin,Mode.COMBAT,mirrored,0,false);}
    public void slashImpact(Player player,Location at){play(player,"slash_impact",at,Mode.COMBAT,false,0,true);}
    private boolean play(Player owner,String id,Location origin,Mode mode,boolean mirrored,int delay,boolean impact){
        try{
            if(task==null)return false;
            long seed=mode==Mode.PREVIEW?42:owner.getUniqueId().getLeastSignificantBits()^runtime.stats().totalSent()^System.nanoTime();
            VfxRuntime.Effect effect=impact?VfxEffects.impact():VfxEffects.create(id,seed);
            return runtime.play(id,pose(owner,origin,impact),mode,effect,seed,mirrored,delay,mode==Mode.COMBAT?impact?20:30:10)!=null;
        }catch(RuntimeException error){report(id,error);return false;}
    }
    private void report(String id,RuntimeException error){
        long now=System.nanoTime()/1_000_000;
        if(lastError==Long.MIN_VALUE||now-lastError>10000){lastError=now;plugin.getLogger().warning("VFX stopped for "+id+": "+error);}
    }
    public void cancelCombat(Player player){cancelVisuals(player,Mode.COMBAT);}
    public void clearCombat(){runtime.clear(Mode.COMBAT);fangs.clear(Mode.COMBAT);}
    public void stopPreview(Player player){cancelVisuals(player,Mode.PREVIEW);}
    public void shutdown(){if(task!=null){task.cancel();task=null;}fangs.clear(null);runtime.shutdown();quality.clear();particleData.clear();}
    @EventHandler public void quit(PlayerQuitEvent event){cancelVisuals(event.getPlayer(),null);quality.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent event){cancelVisuals(event.getEntity(),null);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent event){
        if(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getFrost()!=null&&plugin.getTalentSkillManager().getFrost().internalStep(event.getPlayer()))return;
        if(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getEarth()!=null&&plugin.getTalentSkillManager().getEarth().internalRush(event.getPlayer()))return;
        if(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getDark()!=null&&plugin.getTalentSkillManager().getDark().internalStep(event.getPlayer()))return;
        cancelVisuals(event.getPlayer(),null);
    }
    @EventHandler public void world(PlayerChangedWorldEvent event){cancelVisuals(event.getPlayer(),null);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mode(PlayerGameModeChangeEvent event){cancelVisuals(event.getPlayer(),null);}
    private void cancelVisuals(Player player,Mode mode){runtime.cancel(player.getUniqueId(),mode);fangs.cancel(player.getUniqueId(),mode);if(mode==null||mode==Mode.PREVIEW){var skills=plugin.getTalentSkillManager();if(skills!=null&&skills.getChicken()!=null)skills.getChicken().cancelPreviews(player);}}
    @EventHandler(priority=EventPriority.LOWEST)public void preventFangDamage(org.bukkit.event.entity.EntityDamageEvent e){if(EvokerFangVisuals.nativeDamage(e))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)public void enforceFangDamage(org.bukkit.event.entity.EntityDamageEvent e){if(EvokerFangVisuals.nativeDamage(e))e.setCancelled(true);}
    @EventHandler public void loaded(org.bukkit.event.world.EntitiesLoadEvent e){e.getEntities().forEach(fangs::orphan);}
    private final class Adapter implements Transport {
        public boolean valid(Pose pose){
            Player owner=plugin.getServer().getPlayer(pose.owner());World world=plugin.getServer().getWorld(pose.world());
            if(owner==null||!owner.isOnline()||owner.isDead()||owner.getGameMode()==GameMode.SPECTATOR||world==null||!owner.getWorld().getUID().equals(pose.world()))return false;
            if(!world.isChunkLoaded(((int)Math.floor(pose.origin().x()))>>4,((int)Math.floor(pose.origin().z()))>>4))return false;
            if(pose.target()!=null){var target=plugin.getServer().getEntity(pose.target());if(target==null||!target.isValid()||target.isDead()||!target.getWorld().getUID().equals(pose.world()))return false;}
            return true;
        }
        public List<Viewer> viewers(Pose pose,Mode mode,double distance){
            Player owner=plugin.getServer().getPlayer(pose.owner());if(owner==null)return List.of();
            Collection<Player> candidates=mode==Mode.PREVIEW?List.of(owner):owner.getWorld().getNearbyPlayers(new Location(owner.getWorld(),pose.origin().x(),pose.origin().y(),pose.origin().z()),distance);
            List<Viewer> result=new ArrayList<>();
            if(mode==Mode.COMBAT){Location at=owner.getEyeLocation();result.add(new Viewer(owner.getUniqueId(),pose.world(),new V(at.getX(),at.getY(),at.getZ()),quality(owner)));}
            int examined=result.size();
            for(Player player:candidates){
                if(mode==Mode.COMBAT&&player.equals(owner))continue;
                if(examined++>=64)break;
                if(player.isOnline()&&player.canSee(owner)){
                Location at=player.getEyeLocation();result.add(new Viewer(player.getUniqueId(),player.getWorld().getUID(),new V(at.getX(),at.getY(),at.getZ()),quality(player)));
                }
            }
            return result;
        }
        public boolean particle(UUID viewer,UUID worldId,V at,Paint paint){
            Player player=plugin.getServer().getPlayer(viewer);World world=plugin.getServer().getWorld(worldId);
            if(player==null||world==null||!player.getWorld().getUID().equals(worldId)||at.y()<world.getMinHeight()||at.y()>=world.getMaxHeight()
                    ||!world.isChunkLoaded(((int)Math.floor(at.x()))>>4,((int)Math.floor(at.z()))>>4))return false;
            var location=new Location(world,at.x(),at.y(),at.z());
            // Dust data is reused; geometry and locations are never emitted using random offsets.
            if(paint.kind()==ParticleKind.DIRECTIONAL_SPARK){
                player.spawnParticle(Particle.CRIT,location,0,paint.direction().x(),paint.direction().y(),paint.direction().z(),paint.speed());return true;
            }
            if(paint.kind()==ParticleKind.ICE_BLOCK||paint.kind()==ParticleKind.STONE_BLOCK){
                Object data=particleData.computeIfAbsent(paint,key->(key.kind()==ParticleKind.STONE_BLOCK?Material.STONE:Material.PACKED_ICE).createBlockData());
                player.spawnParticle(Particle.BLOCK,location,1,0,0,0,0,data);return true;
            }
            if(paint.kind()==ParticleKind.SNOWFLAKE||paint.kind()==ParticleKind.END_ROD||paint.kind()==ParticleKind.CLOUD||paint.kind()==ParticleKind.GROWTH||paint.kind()==ParticleKind.HEART){
                Particle particle=switch(paint.kind()){case SNOWFLAKE->Particle.SNOWFLAKE;case END_ROD->Particle.END_ROD;case GROWTH->Particle.HAPPY_VILLAGER;case HEART->Particle.HEART;default->Particle.CLOUD;};
                player.spawnParticle(particle,location,1,0,0,0,0);return true;
            }
            if(particleData.size()>256)particleData.clear();
            Object data=particleData.computeIfAbsent(paint,key->key.kind()==ParticleKind.DUST
                    ?new Particle.DustOptions(Color.fromRGB(key.color()),key.size())
                    :new Particle.DustTransition(Color.fromRGB(key.color()),Color.fromRGB(key.endColor()),key.size()));
            player.spawnParticle(paint.kind()==ParticleKind.DUST?Particle.DUST:Particle.DUST_COLOR_TRANSITION,location,1,0,0,0,0,data);
            return true;
        }
        public boolean sound(UUID viewer,UUID world,V at,String sound,float volume,float pitch){
            Player player=plugin.getServer().getPlayer(viewer);
            if(player==null||!player.getWorld().getUID().equals(world))return false;
            player.playSound(new Location(player.getWorld(),at.x(),at.y(),at.z()),sound,volume,pitch);return true;
        }
        public void failed(String id,RuntimeException error){report(id,error);}
    }
}

package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.talents.*;
import com.customrpg.utils.SkillDamageGuard;
import com.customrpg.vfx.*;
import com.customrpg.vfx.VfxMath.V;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;

/** One combat clock, separate from the visual clock. No damage is triggered by a rendered particle. */
public final class FrostTalentSkills implements Listener {
    private static final NamespacedKey SLOW=new NamespacedKey("customrpg","frost_movement");
    private final CustomRPG plugin;private final ExpandedTalentSkills combat;
    interface MotionEffects { void set(LivingEntity target,double percent); void clear(LivingEntity target); }
    private final MotionEffects motion;
    private final FrostMarks marks=new FrostMarks();
    private long tick;private BukkitTask task;
    private record Cast(Player source,Talent talent,int rank,Location origin) {}
    private record Job(Cast cast,long at,Runnable action) {}
    private record Slow(double percent,long until) {}
    private static final class Control {
        final LivingEntity target;final List<Slow> slows=new ArrayList<>();long root;double applied=-1;
        Control(LivingEntity target){this.target=target;}
    }
    private record Trail(Cast cast,Location from,Location to,long until,Set<UUID> seen) {}
    private static final class Mirror {
        final Cast cast;final long until;final FrostVfx.Anchor anchor=new FrostVfx.Anchor();boolean spent;
        Mirror(Cast cast,long until){this.cast=cast;this.until=until;}
    }
    private record Following(Cast cast,LivingEntity target,Location origin,FrostVfx.Anchor anchor,long until) {}
    private record Movement(Vector direction,long at) {}
    private final List<Job> jobs=new ArrayList<>();
    private final List<Trail> trails=new ArrayList<>();
    private final Map<UUID,Mirror> mirrors=new HashMap<>();
    private final Map<UUID,Control> controls=new HashMap<>();
    private final List<Following> following=new ArrayList<>();
    private final Map<UUID,Movement> movement=new HashMap<>();
    private final Map<UUID,Set<UUID>> visuals=new HashMap<>();
    private final Set<UUID> stepping=new HashSet<>();
    private final Set<UUID> mistDamage=new HashSet<>();
    public FrostTalentSkills(CustomRPG plugin,ExpandedTalentSkills combat){this(plugin,combat,null);}
    FrostTalentSkills(CustomRPG plugin,ExpandedTalentSkills combat,MotionEffects motion){
        this.plugin=plugin;this.combat=combat;
        this.motion=motion!=null?motion:new MotionEffects(){
            public void clear(LivingEntity target){var attribute=target.getAttribute(Attribute.MOVEMENT_SPEED);if(attribute!=null)for(var modifier:new ArrayList<>(attribute.getModifiers()))if(modifier.getKey().equals(SLOW))attribute.removeModifier(modifier);}
            public void set(LivingEntity target,double percent){clear(target);var attribute=target.getAttribute(Attribute.MOVEMENT_SPEED);if(attribute!=null&&percent>0)attribute.addTransientModifier(new AttributeModifier(SLOW,-percent,AttributeModifier.Operation.MULTIPLY_SCALAR_1));}
        };
    }
    public void start(){if(task==null)task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,1,1);}
    public boolean internalStep(Player player){return stepping.contains(player.getUniqueId());}
    public static void validate(Talent talent) {
        var skill=FrostSkill.of(talent.getId());if(skill==null||talent.getType()!=TalentType.ACTIVE||talent.getBranch()!=TalentBranch.FROST)throw new IllegalArgumentException("Unknown frost skill");
        if(talent.getMaxLevel()!=(skill==FrostSkill.CROWN?1:3)||talent.getPointsPerLevel()!=1||talent.getGuiSlot()!=skill.slot
                ||talent.getManaCost()!=skill.mana||talent.getCooldown()!=skill.cooldown||talent.getHpCost()!=0)throw new IllegalArgumentException("Invalid frost layout/cost: "+talent.getId());
        for(int rank=1;rank<=talent.getMaxLevel();rank++) {
            var data=talent.getLevelData(rank);if(data==null||!data.scaling.isEmpty()||!data.effects.keySet().equals(Set.of("baseDamage","range")))throw new IllegalArgumentException("Invalid frost effect data");
            double damage=talent.getEffectDouble(rank,"baseDamage",-1),range=talent.getEffectDouble(rank,"range",-1);
            if(!Double.isFinite(damage)||damage<0||damage>32||range!=skill.range)throw new IllegalArgumentException("Unsafe frost value");
        }
    }
    private boolean valid(Cast cast) {
        var p=cast.source;
        if(!p.isOnline()||p.isDead()||p.getGameMode()==GameMode.SPECTATOR||!p.getWorld().equals(cast.origin.getWorld())||combat.protectedAt(p.getLocation()))return false;
        var ranks=plugin.getTalentManager().getPlayerTalents(p);
        return ranks.getTalentLevel(cast.talent.getId())>0&&(FrostSkill.ultimate(cast.talent.getId())?cast.talent.getId().equals(ranks.getUltimateSkill()):Arrays.asList(ranks.getSelectedSkills()).contains(cast.talent.getId()));
    }
    private Vector flat(Player p){var direction=p.getLocation().getDirection().setY(0);return direction.lengthSquared()<.001?new Vector(0,0,1):direction.normalize();}
    private boolean boss(LivingEntity target) {
        if(target instanceof Wither||target instanceof EnderDragon||target instanceof Warden)return true;
        var mobs=plugin.getMobManager();var data=mobs==null?null:mobs.getMobData(mobs.getCustomMobKey(target));return data!=null&&data.isBoss();
    }
    private void later(Cast cast,int delay,Runnable action){jobs.add(new Job(cast,tick+delay,action));}
    private V v(Location location){return new V(location.getX(),location.getY(),location.getZ());}
    private V local(Location origin,Vector direction,Location at) {
        var frame=VfxMath.Frame.facing(v(origin),new V(direction.getX(),direction.getY(),direction.getZ()),Math.toRadians(origin.getYaw()));
        var delta=v(at).sub(v(origin));return new V(delta.dot(frame.right()),delta.dot(frame.up()),delta.dot(frame.forward()));
    }
    private void show(Cast cast,Location origin,Vector direction,VfxRuntime.Effect effect,LivingEntity target) {
        var service=plugin.getVfxService();if(service==null)return;
        UUID id=service.frost(cast.source,cast.talent.getId(),origin,direction,effect,VfxRuntime.Mode.COMBAT,target,0);
        if(id!=null)visuals.computeIfAbsent(cast.source.getUniqueId(),ignored->new HashSet<>()).add(id);
    }
    private boolean control(Cast cast,LivingEntity target,double slow,int duration,boolean root) {
        if(duration<=0||target.isDead()||!target.isValid()||controls.size()>=1024&&!controls.containsKey(target.getUniqueId()))return false;
        var event=new FrostControlEvent(cast.source,target,root&&boss(target)?.3:slow,duration,root&&!boss(target));
        plugin.getServer().getPluginManager().callEvent(event);if(event.isCancelled())return false;
        var state=controls.computeIfAbsent(target.getUniqueId(),ignored->new Control(target));
        if(root&&!boss(target))state.root=Math.max(state.root,tick+duration);
        else {double percent=root?.3:slow;var same=state.slows.stream().filter(s->s.percent==percent).findFirst().orElse(null);state.slows.remove(same);state.slows.add(new Slow(percent,tick+duration));}
        applyControl(state);return true;
    }
    public boolean cleanseOrdinarySlow(LivingEntity target){var state=controls.get(target.getUniqueId());if(state==null)return false;var slow=state.slows.stream().filter(s->s.percent>0&&s.percent<1&&s.until>tick).findFirst().orElse(null);if(slow==null)return false;state.slows.remove(slow);applyControl(state);return true;}
    private void applyControl(Control state) {
        double percent=state.root>tick?1:state.slows.stream().filter(s->s.until>tick).mapToDouble(Slow::percent).max().orElse(0);
        if(percent==state.applied)return;
        motion.set(state.target,percent);
        state.applied=percent;
    }
    private boolean mark(Cast cast,LivingEntity target,boolean allowCondense) {
        var result=marks.hit(cast.source.getUniqueId(),target.getUniqueId(),tick,allowCondense);
        if(result.condensed()) {control(cast,target,.3,16,true);crossVisual(cast,target,true);}
        return result.condensed();
    }
    private boolean hit(Cast cast,LivingEntity target,double amount,boolean mark) {
        if(!valid(cast)||!combat.enemy(cast.source,target))return false;
        double before=MobManager.getVirtualCurrentHP(target)+target.getAbsorptionAmount();
        boolean periodic=cast.talent.getId().equals(FrostSkill.MIST.id)&&mistDamage.add(cast.source.getUniqueId());
        try{SkillDamageGuard.damage(target,amount,cast.source);}finally{if(periodic)mistDamage.remove(cast.source.getUniqueId());}
        boolean accepted=MobManager.getVirtualCurrentHP(target)+target.getAbsorptionAmount()<before;
        if(accepted&&mark&&!target.isDead())mark(cast,target,true);
        return accepted;
    }
    private List<LivingEntity> enemies(Cast cast,Location center,double radius,double height) {
        if(!center.getWorld().isChunkLoaded(center.getBlockX()>>4,center.getBlockZ()>>4))return List.of();
        return center.getWorld().getNearbyEntities(center,radius,height,radius).stream().filter(e->e instanceof LivingEntity)
                .map(e->(LivingEntity)e).filter(e->Math.pow(e.getLocation().getX()-center.getX(),2)+Math.pow(e.getLocation().getZ()-center.getZ(),2)<=radius*radius)
                .filter(e->combat.enemy(cast.source,e)&&clear(center.clone().add(0,.3,0),e.getEyeLocation())).limit(24).toList();
    }
    private boolean clear(Location from,Location to) {
        var delta=to.toVector().subtract(from.toVector());double distance=delta.length();return distance<.1||loaded(from,delta,distance)&&from.getWorld().rayTraceBlocks(from,delta.normalize(),Math.max(0,distance-.1),FluidCollisionMode.NEVER,true)==null;
    }
    private boolean loaded(Location from,Vector direction,double distance){var dir=direction.clone().normalize();for(double d=0;d<=distance+.5;d+=.5){var at=from.clone().add(dir.clone().multiply(Math.min(d,distance)));if(!at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))return false;}return true;}
    private Location ground(Location sample) {
        var world=sample.getWorld();if(!world.isChunkLoaded(sample.getBlockX()>>4,sample.getBlockZ()>>4))return null;
        for(int y=sample.getBlockY()+1;y>=sample.getBlockY()-3;y--) {
            if(y<world.getMinHeight()||y+2>=world.getMaxHeight())continue;
            var block=world.getBlockAt(sample.getBlockX(),y,sample.getBlockZ());
            if(!block.isPassable()&&!block.isLiquid()&&world.getBlockAt(sample.getBlockX(),y+1,sample.getBlockZ()).isPassable())
                return new Location(world,sample.getX(),block.getBoundingBox().getMaxY()+.03,sample.getZ(),sample.getYaw(),0);
        }return null;
    }
    private Location aimGround(Player source,int range) {
        if(!loaded(source.getEyeLocation(),source.getEyeLocation().getDirection(),range))return null;
        var ray=source.getWorld().rayTraceBlocks(source.getEyeLocation(),source.getEyeLocation().getDirection(),range,FluidCollisionMode.NEVER,true);
        Location at=ray==null?source.getLocation().add(flat(source).multiply(range)):ray.getHitPosition().toLocation(source.getWorld());
        var ground=ground(at);return ground!=null&&ground.distanceSquared(source.getEyeLocation())<=(range+.5)*(range+.5)&&clear(source.getEyeLocation(),ground.clone().add(0,.1,0))&&!combat.protectedAt(ground)?ground:null;
    }
    private void crossVisual(Cast cast,LivingEntity target,boolean condensed) {
        var anchor=new FrostVfx.Anchor();var at=target.getLocation().clone();following.add(new Following(cast,target,at,anchor,tick+(condensed?26:17)));
        show(cast,at,flat(cast.source),new FrostVfx.Cross(anchor,condensed),target);
    }
    public boolean cast(Player source,Talent talent,int rank) {
        var skill=FrostSkill.of(talent.getId());if(skill==null)return false;
        var cast=new Cast(source,talent,rank,source.getLocation().clone());
        if(!valid(cast)||jobs.size()>192||mirrors.size()>=64||trails.size()>=64) {source.sendMessage("§c目前無法施放冰霜技能，請確認區域與技能配置。");return false;}
        double damage=talent.getEffectDouble(rank,"baseDamage",skill.damage);Vector direction=flat(source);
        switch(skill) {
            case PIERCER -> {
                Location eye=source.getEyeLocation().clone();Vector aim=eye.getDirection().normalize();
                if(!loaded(eye,aim,14))return noTarget(source);
                var predicted=source.getWorld().rayTrace(eye,aim,14,FluidCollisionMode.NEVER,true,.35,e->e instanceof LivingEntity l&&combat.enemy(source,l));
                double distance=predicted==null?14:predicted.getHitPosition().distance(eye.toVector());
                show(cast,eye,aim,new FrostVfx.Piercer(distance),null);
                Set<UUID> finished=new HashSet<>();
                for(int step=0;step<6;step++){final int part=step;later(cast,5+step,()->{
                    if(!finished.isEmpty())return;Location from=eye.clone().add(aim.clone().multiply(part*14.0/6));
                    if(!loaded(from,aim,14.0/6)){finished.add(source.getUniqueId());return;}
                    var ray=from.getWorld().rayTrace(from,aim,14.0/6,FluidCollisionMode.NEVER,true,.35,e->e instanceof LivingEntity l&&combat.enemy(source,l));
                    if(ray!=null){finished.add(source.getUniqueId());if(ray.getHitEntity() instanceof LivingEntity target&&hit(cast,target,damage,true)&&!target.isDead())control(cast,target,.2,40,false);}
                });}
            }
            case FANGS -> {
                List<Location> ground=new ArrayList<>();Location previous=source.getLocation();
                for(int i=1;i<=8;i++){Location at=ground(source.getLocation().add(direction.clone().multiply(i)));
                    if(at==null||Math.abs(at.getY()-previous.getY())>1.3||!clear(previous.clone().add(0,.35,0),at.clone().add(0,.35,0))||combat.protectedAt(at))break;
                    ground.add(at);previous=at;}
                if(ground.isEmpty())return noTarget(source);
                show(cast,cast.origin,direction,new FrostVfx.Fangs(ground.stream().map(p->local(cast.origin,direction,p)).toList()),null);
                Set<UUID> seen=new HashSet<>();for(int i=0;i<ground.size();i++){Location at=ground.get(i);later(cast,6+i,()->{for(var target:enemies(cast,at,.75,2))if(seen.add(target.getUniqueId()))hit(cast,target,damage,true);});}
            }
            case STEP -> {
                var lastMove=movement.get(source.getUniqueId());Vector dashDirection=lastMove!=null&&tick-lastMove.at<8?lastMove.direction.clone():direction;
                Location from=source.getLocation().clone(),last=null;
                for(double distance=.25;distance<=5;distance+=.25){var at=from.clone().add(dashDirection.clone().multiply(distance));boolean loaded=true;
                    for(int x=(int)Math.floor(at.getX()-.4)>>4;x<=((int)Math.floor(at.getX()+.4)>>4);x++)for(int z=(int)Math.floor(at.getZ()-.4)>>4;z<=((int)Math.floor(at.getZ()+.4)>>4);z++)loaded&=at.getWorld().isChunkLoaded(x,z);
                    if(!loaded||!at.getWorld().getWorldBorder().isInside(at)||combat.protectedAt(at)||source.collidesAt(at)||at.getBlock().isLiquid()||at.clone().add(0,1,0).getBlock().isLiquid())break;last=at;}
                if(last==null||from.distanceSquared(last)<.25)return noTarget(source);
                stepping.add(source.getUniqueId());boolean moved;try{moved=source.teleport(last,PlayerTeleportEvent.TeleportCause.PLUGIN);}finally{stepping.remove(source.getUniqueId());}
                if(!moved)return false;
                Vector facing=flat(source);show(cast,from,facing,new FrostVfx.PhantomStep(local(from,facing,last)),null);
                trails.add(new Trail(cast,from,last,tick+40,new HashSet<>()));
            }
            case MIRROR -> {
                var mirror=new Mirror(cast,tick+100);mirrors.put(source.getUniqueId(),mirror);
                show(cast,cast.origin,direction,new FrostVfx.Mirror(mirror.anchor),null);
            }
            case CROSS -> {
                if(!loaded(source.getEyeLocation(),source.getEyeLocation().getDirection(),10))return noTarget(source);
                var target=combat.aim(source,10);if(target==null)return noTarget(source);
                if(!control(cast,target,.5,40,false))return false;boolean condensed=mark(cast,target,true);if(!condensed)crossVisual(cast,target,false);
            }
            case MIST -> {
                Location center=aimGround(source,12);if(center==null)return noTarget(source);Set<UUID> stamped=new HashSet<>();long until=tick+120;
                show(cast,center,new Vector(0,0,1),new FrostVfx.Mist(3.5),null);
                for(int pulse=1;pulse<=24;pulse++){int at=pulse*5;later(cast,at,()->{
                    for(var target:enemies(cast,center,3.5,3)){control(cast,target,.25,(int)Math.max(0,Math.min(8,until-tick)),false);
                        if(at%40==0&&hit(cast,target,damage,false)&&!target.isDead()&&stamped.add(target.getUniqueId()))mark(cast,target,true);}
                });}
            }
            case PRISM -> {
                if(!loaded(source.getEyeLocation(),source.getEyeLocation().getDirection(),12))return noTarget(source);
                LivingEntity first=combat.aim(source,12);if(first==null)return noTarget(source);
                var nodes=new ArrayList<V>();var origin=cast.origin.clone();var forward=direction.clone();var seen=new HashSet<UUID>();
                show(cast,origin,forward,new FrostVfx.Prism(nodes),null);
                chain(cast,first,null,seen,nodes,origin,forward,damage,0);
            }
            case CROWN -> {
                Location center=aimGround(source,12);if(center==null)return noTarget(source);
                show(cast,center,new Vector(0,0,1),new FrostVfx.Crown(),null);
                long until=tick+110;
                later(cast,30,()->{for(var target:enemies(cast,center,6,5))if(hit(cast,target,damage,false)&&!target.isDead()){
                    mark(cast,target,false);if(boss(target))control(cast,target,.2,40,false);else control(cast,target,0,30,true);}
                    for(int pulse=0;pulse<16;pulse++)later(cast,pulse*5,()->{for(var target:enemies(cast,center,6,3))if(!boss(target))control(cast,target,.3,(int)Math.max(0,Math.min(8,until-tick)),false);});
                });
            }
        }
        return true;
    }
    private void chain(Cast cast,LivingEntity target,Location previous,Set<UUID> seen,List<V> nodes,Location origin,Vector direction,double firstDamage,int hop) {
        later(cast,hop==0?12:6,()->{
            if(target.isDead()||!combat.enemy(cast.source,target)||seen.contains(target.getUniqueId())
                    ||previous!=null&&(!previous.getWorld().equals(target.getWorld())||previous.distanceSquared(target.getLocation())>25||!clear(previous.clone().add(0,.5,0),target.getEyeLocation())))return;
            Location struck=target.getLocation().clone();
            seen.add(target.getUniqueId());if(!hit(cast,target,hop==0?firstDamage:firstDamage*2/3,true))return;
            nodes.add(local(origin,direction,target.getEyeLocation()));if(hop>=2)return;
            var next=enemies(cast,struck,5,5).stream().filter(e->!seen.contains(e.getUniqueId())&&clear(struck.clone().add(0,.5,0),e.getEyeLocation()))
                    .min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(struck))).orElse(null);
            if(next!=null)chain(cast,next,struck,seen,nodes,origin,direction,firstDamage,hop+1);
        });
    }
    private boolean noTarget(Player player){player.sendMessage("§c請瞄準射程內可見的敵人或可站立地面，位移不能穿牆。");return false;}
    void tick() {
        tick++;
        for(var job:new ArrayList<>(jobs)) {if(!valid(job.cast)){jobs.remove(job);continue;}if(job.at<=tick){jobs.remove(job);try{job.action.run();}catch(RuntimeException error){cancel(job.cast.source);plugin.getLogger().warning("Frost cast cancelled: "+job.cast.talent.getId()+": "+error.getMessage());}}}
        for(var trail:new ArrayList<>(trails)){if(trail.until<=tick||!valid(trail.cast)){trails.remove(trail);continue;}
            for(int i=0;i<=10;i++){Location sample=trail.from.clone().add(trail.to.toVector().subtract(trail.from.toVector()).multiply(i/10.0));for(var target:enemies(trail.cast,sample,.65,1.5))if(trail.seen.add(target.getUniqueId()))control(trail.cast,target,.25,30,false);}}
        mirrors.values().removeIf(m->m.until<=tick||!valid(m.cast));for(var mirror:mirrors.values())mirror.anchor.offset=v(mirror.cast.source.getLocation()).sub(v(mirror.cast.origin));
        following.removeIf(f->f.until<=tick||!valid(f.cast)||f.target.isDead()||!f.target.isValid());for(var follow:following)follow.anchor.offset=v(follow.target.getLocation()).sub(v(follow.origin));
        for(var entry:new ArrayList<>(controls.entrySet())){var state=entry.getValue();state.slows.removeIf(s->s.until<=tick);
            if(state.target.isDead()||!state.target.isValid()){removeControl(state);controls.remove(entry.getKey());continue;}
            applyControl(state);if(state.root<=tick&&state.slows.isEmpty())controls.remove(entry.getKey());}
        if(tick%20==0){marks.prune(tick);movement.values().removeIf(m->tick-m.at>20);var fx=plugin.getVfxService();if(fx!=null)visuals.values().forEach(ids->ids.removeIf(id->!fx.runtime().active(id)));visuals.values().removeIf(Set::isEmpty);}
    }
    private void removeControl(Control control){motion.clear(control.target);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)public void defend(EntityDamageEvent event){
        if(SkillDamageGuard.isTransfer(event.getEntity()))return;
        if(!(event.getEntity() instanceof Player player)||event.getCause()==EntityDamageEvent.DamageCause.VOID||event.getCause()==EntityDamageEvent.DamageCause.KILL||event.getCause()==EntityDamageEvent.DamageCause.SUICIDE)return;
        var mirror=mirrors.get(player.getUniqueId());if(mirror!=null&&mirror.until>tick&&valid(mirror.cast))com.customrpg.utils.TalentMitigation.reduce(event,.25);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void counter(EntityDamageByEntityEvent event){
        if(SkillDamageGuard.isTransfer(event.getEntity())||!(event.getEntity() instanceof Player player)||event.getFinalDamage()<=0)return;var mirror=mirrors.get(player.getUniqueId());
        if(mirror==null||mirror.spent||mirror.until<=tick||!valid(mirror.cast))return;
        LivingEntity attacker=event.getDamager() instanceof LivingEntity living?living:event.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof LivingEntity living?living:null;
        if(attacker==null||SkillDamageGuard.isFireTick(attacker)||SkillDamageGuard.isPeriodic(attacker)||mistDamage.contains(attacker.getUniqueId())||!combat.enemy(player,attacker))return;
        mirror.spent=true;mirror.anchor.shattered=true;
        Location eye=player.getEyeLocation();Vector dir=attacker.getEyeLocation().toVector().subtract(eye.toVector());show(mirror.cast,eye,dir,new FrostVfx.MirrorShard(dir.length()),attacker);
        later(mirror.cast,6,()->hit(mirror.cast,attacker,mirror.cast.talent.getEffectDouble(mirror.cast.rank,"baseDamage",3),true));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void move(PlayerMoveEvent event){
        if(event.getTo()==null)return;var state=controls.get(event.getPlayer().getUniqueId());
        if(state!=null&&state.root>tick&&(event.getFrom().getX()!=event.getTo().getX()||event.getFrom().getZ()!=event.getTo().getZ())){
            var stop=event.getFrom().clone();stop.setYaw(event.getTo().getYaw());stop.setPitch(event.getTo().getPitch());event.setTo(stop);return;}
        Vector delta=event.getTo().toVector().subtract(event.getFrom().toVector()).setY(0);if(delta.lengthSquared()>.0001)movement.put(event.getPlayer().getUniqueId(),new Movement(delta.normalize(),tick));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void mobMove(io.papermc.paper.event.entity.EntityMoveEvent event){
        var state=controls.get(event.getEntity().getUniqueId());
        if(state!=null&&state.root>tick){var to=event.getTo().clone();to.setX(event.getFrom().getX());to.setZ(event.getFrom().getZ());event.setTo(to);}
    }
    private void forgetTarget(LivingEntity target){var state=controls.remove(target.getUniqueId());if(state!=null)removeControl(state);marks.target(target.getUniqueId());following.removeIf(f->f.target.equals(target));}
    public void cancel(Player player) {
        UUID id=player.getUniqueId();jobs.removeIf(j->j.cast.source.equals(player));trails.removeIf(t->t.cast.source.equals(player));mirrors.remove(id);following.removeIf(f->f.cast.source.equals(player));movement.remove(id);marks.owner(id);
        var ids=visuals.remove(id);if(ids!=null&&plugin.getVfxService()!=null)ids.forEach(plugin.getVfxService().runtime()::cancelInstance);
    }
    public void clear(){jobs.clear();trails.clear();mirrors.clear();following.clear();movement.clear();controls.values().forEach(this::removeControl);controls.clear();marks.clear();if(plugin.getVfxService()!=null)visuals.values().forEach(ids->ids.forEach(plugin.getVfxService().runtime()::cancelInstance));visuals.clear();}
    public void shutdown(){if(task!=null){task.cancel();task=null;}clear();}
    @EventHandler public void quit(PlayerQuitEvent event){cancel(event.getPlayer());forgetTarget(event.getPlayer());}
    @EventHandler public void death(EntityDeathEvent event){if(event.getEntity() instanceof Player p)cancel(p);marks.target(event.getEntity().getUniqueId());var state=controls.remove(event.getEntity().getUniqueId());if(state!=null)removeControl(state);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void teleport(PlayerTeleportEvent event){if(!(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getDark()!=null&&plugin.getTalentSkillManager().getDark().internalStep(event.getPlayer()))&&!internalStep(event.getPlayer())&&!(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getEarth()!=null&&plugin.getTalentSkillManager().getEarth().internalRush(event.getPlayer()))){cancel(event.getPlayer());forgetTarget(event.getPlayer());}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void entityTeleport(EntityTeleportEvent event){if(event.getEntity() instanceof LivingEntity target)forgetTarget(target);}
    @EventHandler public void world(PlayerChangedWorldEvent event){cancel(event.getPlayer());forgetTarget(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void mode(PlayerGameModeChangeEvent event){cancel(event.getPlayer());}
}

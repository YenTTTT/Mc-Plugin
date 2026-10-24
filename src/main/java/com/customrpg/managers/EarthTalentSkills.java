package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.talents.*;
import com.customrpg.utils.*;
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

/** One combat clock. Geometry and quality never decide damage, protection, movement or resources. */
public final class EarthTalentSkills implements Listener {
    private static final NamespacedKey SLOW=new NamespacedKey("customrpg","earth_movement"),ABSORB=new NamespacedKey("customrpg","earth_absorption");
    interface Motion {void set(LivingEntity e,double slow);void clear(LivingEntity e);}
    interface Transfer {void damage(Player target,double amount,LivingEntity attacker);}
    private final CustomRPG plugin;private final ExpandedTalentSkills combat;private final Motion motion;private final Transfer transfer;private final EarthShields shields;
    private final EarthResilience resilience=new EarthResilience();private long tick;private BukkitTask task;
    private record Cast(Player source,Talent talent,int rank,Location origin,Vector facing){}
    private record Job(Cast cast,long at,Runnable action){}
    private record SlowKey(UUID owner,UUID target,double percent){}
    private record Slow(UUID owner,LivingEntity target,double percent,long until){}
    private static final class Guard {final Cast cast;final long until;final EarthVfx.Anchor anchor=new EarthVfx.Anchor();boolean reserved;Guard(Cast c,long until){cast=c;this.until=until;}}
    private static final class Link {final Cast cast;final Player ally;final long until;final EarthVfx.Anchor anchor=new EarthVfx.Anchor();UUID visual;Link(Cast c,Player ally,long until){cast=c;this.ally=ally;this.until=until;}}
    private record Victim(long next,int hits){}
    private static final class Field {final Cast cast;final Location center;final long starts,until;final EarthVfx.Anchor anchor=new EarthVfx.Anchor();final Map<UUID,Victim> victims=new HashMap<>();Field(Cast c,Location at,long starts,long until){cast=c;center=at;this.starts=starts;this.until=until;}}
    private record Follow(Cast cast,EarthVfx.Anchor anchor,long until,UUID visual){}
    private final List<Job> jobs=new ArrayList<>();private final Map<UUID,Guard> guards=new HashMap<>(),hearts=new HashMap<>();
    private final List<Link> links=new ArrayList<>();private final List<Field> fields=new ArrayList<>();private final Map<SlowKey,Slow> slows=new HashMap<>();
    private final Map<UUID,LivingEntity> slowed=new HashMap<>();private final Map<UUID,Double> applied=new HashMap<>();private final Map<UUID,Set<UUID>> visuals=new HashMap<>();
    private final Map<UUID,Follow> innate=new HashMap<>();private final Map<EntityDamageEvent,Guard> pending=new IdentityHashMap<>();private final Set<UUID> rushing=new HashSet<>();
    public EarthTalentSkills(CustomRPG p,ExpandedTalentSkills c){this(p,c,null,null,null);}
    EarthTalentSkills(CustomRPG p,ExpandedTalentSkills c,Motion movement,EarthShields.Access absorption,Transfer transfer){
        plugin=p;combat=c;this.transfer=transfer==null?SkillDamageGuard::transfer:transfer;
        motion=movement!=null?movement:new Motion(){
            public void clear(LivingEntity e){var a=e.getAttribute(Attribute.MOVEMENT_SPEED);if(a!=null)for(var m:new ArrayList<>(a.getModifiers()))if(m.getKey().equals(SLOW))a.removeModifier(m);}
            public void set(LivingEntity e,double percent){clear(e);var a=e.getAttribute(Attribute.MOVEMENT_SPEED);if(a!=null&&percent>0)a.addTransientModifier(new AttributeModifier(SLOW,-percent,AttributeModifier.Operation.MULTIPLY_SCALAR_1));}
        };
        shields=new EarthShields(absorption!=null?absorption:new EarthShields.Access(){
            public double amount(Player target){return target.getAbsorptionAmount();}
            public void amount(Player target,double value){target.setAbsorptionAmount(value);}
            public void capacity(Player target,double own){var a=target.getAttribute(Attribute.MAX_ABSORPTION);if(a!=null){for(var m:new ArrayList<>(a.getModifiers()))if(m.getKey().equals(ABSORB))a.removeModifier(m);if(own>0)a.addTransientModifier(new AttributeModifier(ABSORB,own,AttributeModifier.Operation.ADD_NUMBER));}}
        });
    }
    public void start(){if(task==null)task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,1,1);}
    public boolean internalRush(Player p){return rushing.contains(p.getUniqueId());}
    public static void validate(Talent t){var s=EarthSkill.of(t.getId());if(s==null||t.getBranch()!=TalentBranch.EARTH||t.getType()!=TalentType.ACTIVE||t.getMaxLevel()!=(s==EarthSkill.TITAN?1:3)||t.getGuiSlot()!=s.slot||t.getPointsPerLevel()!=1||t.getManaCost()!=s.mana||t.getCooldown()!=s.cooldown||t.getHpCost()!=0)throw new IllegalArgumentException("Invalid Earth layout/cost: "+t.getId());
        for(int rank=1;rank<=t.getMaxLevel();rank++){var d=t.getLevelData(rank);double damage=t.getEffectDouble(rank,"baseDamage",-1);if(d==null||!d.scaling.isEmpty()||!d.effects.keySet().equals(Set.of("baseDamage","range"))||!Double.isFinite(damage)||damage<0||damage>32||t.getEffectDouble(rank,"range",-1)!=s.range)throw new IllegalArgumentException("Invalid Earth effects");}}
    private boolean valid(Cast c){var p=c.source;if(!p.isOnline()||p.isDead()||p.getGameMode()==GameMode.SPECTATOR||!p.getWorld().equals(c.origin.getWorld())||combat.protectedAt(p.getLocation()))return false;var pt=plugin.getTalentManager().getPlayerTalents(p);return pt.getTalentLevel(c.talent.getId())>0&&(EarthSkill.ultimate(c.talent.getId())?c.talent.getId().equals(pt.getUltimateSkill()):Arrays.asList(pt.getSelectedSkills()).contains(c.talent.getId()));}
    private boolean active(Player p){if(!p.isOnline()||p.isDead()||p.getGameMode()==GameMode.SPECTATOR||combat.protectedAt(p.getLocation()))return false;var pt=plugin.getTalentManager().getPlayerTalents(p);return Arrays.stream(EarthSkill.values()).anyMatch(s->pt.getTalentLevel(s.id)>0);}
    private Vector flat(Player p){var d=p.getLocation().getDirection().setY(0);return d.lengthSquared()<.001?new Vector(0,0,1):d.normalize();}
    private V v(Location at){return new V(at.getX(),at.getY(),at.getZ());}
    private V local(Location origin,Vector dir,Location at){var f=VfxMath.Frame.facing(v(origin),new V(dir.getX(),dir.getY(),dir.getZ()),Math.toRadians(origin.getYaw()));var delta=v(at).sub(v(origin));return new V(delta.dot(f.right()),delta.dot(f.up()),delta.dot(f.forward()));}
    private UUID show(Cast c,Location at,Vector dir,VfxRuntime.Effect effect,LivingEntity target){var service=plugin.getVfxService();if(service==null)return null;var id=service.earth(c.source,c.talent.getId(),at,dir,effect,VfxRuntime.Mode.COMBAT,target,0);if(id!=null)visuals.computeIfAbsent(c.source.getUniqueId(),ignored->new HashSet<>()).add(id);return id;}
    private void stop(UUID id){if(id!=null&&plugin.getVfxService()!=null)plugin.getVfxService().runtime().cancelInstance(id);}
    private void later(Cast c,int delay,Runnable action){jobs.add(new Job(c,tick+delay,action));}
    private boolean loaded(Location from,Vector direction,double distance){var dir=direction.clone().normalize();for(double d=0;d<=distance+.5;d+=.5){var at=from.clone().add(dir.clone().multiply(Math.min(d,distance)));if(!at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))return false;}return true;}
    private boolean clear(Location from,Location to){if(!from.getWorld().equals(to.getWorld()))return false;var d=to.toVector().subtract(from.toVector());double n=d.length();return n<.1||loaded(from,d,n)&&from.getWorld().rayTraceBlocks(from,d.normalize(),Math.max(0,n-.1),FluidCollisionMode.NEVER,true)==null;}
    private Location ground(Location at){var w=at.getWorld();if(!w.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))return null;for(int y=at.getBlockY()+1;y>=at.getBlockY()-3;y--){if(y<w.getMinHeight()||y+2>=w.getMaxHeight())continue;var b=w.getBlockAt(at.getBlockX(),y,at.getBlockZ());if(!b.isPassable()&&!b.isLiquid()&&w.getBlockAt(at.getBlockX(),y+1,at.getBlockZ()).isPassable())return new Location(w,at.getX(),b.getBoundingBox().getMaxY()+.03,at.getZ(),at.getYaw(),0);}return null;}
    private Location aimGround(Player p,int range){var eye=p.getEyeLocation();if(!loaded(eye,eye.getDirection(),range))return null;var ray=p.getWorld().rayTraceBlocks(eye,eye.getDirection(),range,FluidCollisionMode.NEVER,true);var at=ground(ray==null?p.getLocation().add(flat(p).multiply(range)):ray.getHitPosition().toLocation(p.getWorld()));return at!=null&&at.distanceSquared(eye)<=(range+.5)*(range+.5)&&clear(eye,at.clone().add(0,.1,0))&&!combat.protectedAt(at)?at:null;}
    private List<LivingEntity> enemies(Cast c,Location center,double radius){if(!center.getWorld().isChunkLoaded(center.getBlockX()>>4,center.getBlockZ()>>4))return List.of();return center.getWorld().getNearbyEntities(center,radius,3,radius).stream().filter(e->e instanceof LivingEntity).map(e->(LivingEntity)e).filter(e->e.getLocation().distanceSquared(center)<=radius*radius&&combat.enemy(c.source,e)&&clear(center.clone().add(0,.4,0),e.getEyeLocation())).limit(24).toList();}
    private Player aimAlly(Player p,int range){if(!loaded(p.getEyeLocation(),p.getEyeLocation().getDirection(),range))return null;var ray=p.getWorld().rayTrace(p.getEyeLocation(),p.getEyeLocation().getDirection(),range,FluidCollisionMode.NEVER,true,.4,e->e instanceof Player ally&&!ally.equals(p)&&combat.ally(p,ally)&&!combat.protectedAt(ally.getLocation()));return ray!=null&&ray.getHitEntity() instanceof Player ally?ally:null;}
    private boolean boss(LivingEntity e){if(e instanceof Wither||e instanceof EnderDragon||e instanceof Warden)return true;var m=plugin.getMobManager();var data=m==null?null:m.getMobData(m.getCustomMobKey(e));return data!=null&&data.isBoss();}
    private boolean hit(Cast c,LivingEntity e,double damage,boolean periodic){if(!valid(c)||!combat.enemy(c.source,e))return false;double before=MobManager.getVirtualCurrentHP(e)+e.getAbsorptionAmount();if(periodic)SkillDamageGuard.damagePeriodic(e,damage,c.source);else SkillDamageGuard.damage(e,damage,c.source);return MobManager.getVirtualCurrentHP(e)+e.getAbsorptionAmount()<before;}
    private boolean permit(Cast c,LivingEntity e,EarthControlEvent.Kind kind,double amount,int ticks){var event=new EarthControlEvent(c.source,e,kind,amount,ticks);plugin.getServer().getPluginManager().callEvent(event);return !event.isCancelled();}
    private void slow(Cast c,LivingEntity e,double percent,int ticks){
        if(ticks<=0||e.isDead()||!e.isValid())return;var key=new SlowKey(c.source.getUniqueId(),e.getUniqueId(),percent);
        if(slows.size()>=1024&&!slows.containsKey(key)||!permit(c,e,EarthControlEvent.Kind.SLOW,percent,ticks))return;
        slows.put(key,new Slow(c.source.getUniqueId(),e,percent,tick+ticks));
    }
    public boolean cleanseOrdinarySlow(LivingEntity target){var key=slows.entrySet().stream().filter(e->e.getValue().target.equals(target)&&e.getValue().percent<1&&e.getValue().until>tick).map(Map.Entry::getKey).findFirst().orElse(null);if(key==null)return false;slows.remove(key);applySlows();return true;}
    private void applySlows(){var values=new HashMap<UUID,Double>();for(var s:slows.values()){slowed.put(s.target.getUniqueId(),s.target);values.merge(s.target.getUniqueId(),s.percent,Math::max);}for(var id:new ArrayList<>(slowed.keySet())){var e=slowed.get(id);double n=values.getOrDefault(id,0.0);if(n!=applied.getOrDefault(id,-1.0)){motion.set(e,n);applied.put(id,n);}if(n==0){slowed.remove(id);applied.remove(id);}}}
    private Location safeEnd(LivingEntity e,Vector direction,double max){Location start=e.getLocation(),last=null;var dir=direction.clone().setY(0);if(dir.lengthSquared()<.001)return null;dir.normalize();for(double d=.2;d<=max+.001;d+=.2){var at=start.clone().add(dir.clone().multiply(d));boolean loaded=true;for(int x=(int)Math.floor(at.getX()-.4)>>4;x<=((int)Math.floor(at.getX()+.4)>>4);x++)for(int z=(int)Math.floor(at.getZ()-.4)>>4;z<=((int)Math.floor(at.getZ()+.4)>>4);z++)loaded&=at.getWorld().isChunkLoaded(x,z);if(!loaded||!at.getWorld().getWorldBorder().isInside(at)||combat.protectedAt(at)||e.collidesAt(at)||at.getBlock().isLiquid()||at.clone().add(0,1,0).getBlock().isLiquid())break;var floor=ground(at);if(floor==null||Math.abs(floor.getY()-at.getY())>.55)break;last=at;}return last;}
    private void displace(Cast c,LivingEntity e,Vector dir,double distance,boolean pull){if(boss(e)||!permit(c,e,pull?EarthControlEvent.Kind.PULL:EarthControlEvent.Kind.KNOCKBACK,distance,0))return;var to=safeEnd(e,dir,distance);if(to!=null){rushing.add(e.getUniqueId());try{e.teleport(to,PlayerTeleportEvent.TeleportCause.PLUGIN);}finally{rushing.remove(e.getUniqueId());}}}
    private void grant(Player target,Cast c,String key,double amount,int ticks){shields.grant(target,c.source.getUniqueId(),key,amount,tick+ticks);}
    private void gain(Player p){
        if(!active(p)||!resilience.gain(p.getUniqueId(),tick))return;
        var old=innate.remove(p.getUniqueId());if(old!=null){old.anchor.stacks=0;stop(old.visual);}
        var pt=plugin.getTalentManager().getPlayerTalents(p);var t=Arrays.stream(EarthSkill.values()).map(e->plugin.getTalentManager().findTalent(e.id)).filter(Objects::nonNull).filter(e->pt.getTalentLevel(e.getId())>0).findFirst().orElse(null);
        if(t==null)return;var c=new Cast(p,t,pt.getTalentLevel(t.getId()),p.getLocation().clone(),flat(p));var a=new EarthVfx.Anchor();a.stacks=resilience.stacks(p.getUniqueId(),tick);
        var id=show(c,c.origin,c.facing,new EarthVfx.Resilience(a),null);innate.put(p.getUniqueId(),new Follow(c,a,tick+160,id));
    }
    private void empowered(Cast c,EarthSkill s){if(s.defensive()&&resilience.consume(c.source.getUniqueId(),tick)){var old=innate.remove(c.source.getUniqueId());if(old!=null){old.anchor.stacks=0;stop(old.visual);}grant(c.source,c,"resilience",4,100);show(c,c.source.getLocation(),flat(c.source),new EarthVfx.Fortify(),null);}}
    private boolean noTarget(Player p){p.sendMessage("§c請瞄準射程內的合法地面或可見同隊玩家，移動路徑不可穿牆。");return false;}
    public boolean cast(Player p,Talent t,int rank){var s=EarthSkill.of(t.getId());if(s==null)return false;var c=new Cast(p,t,rank,p.getLocation().clone(),flat(p));if(!valid(c)||jobs.size()>192||fields.size()>=64||links.size()>=64)return false;double damage=t.getEffectDouble(rank,"baseDamage",s.damage);
        switch(s){
            case STRIKE->{show(c,c.origin,c.facing,new EarthVfx.Strike(p.getUniqueId().getLeastSignificantBits()^tick),null);later(c,5,()->{for(var e:enemies(c,c.origin,4)){var delta=e.getLocation().toVector().subtract(c.origin.toVector()).setY(0);double forward=delta.dot(c.facing),side=Math.abs(delta.getX()*c.facing.getZ()-delta.getZ()*c.facing.getX());if(forward>=0&&side<=1.25*(forward/4)+.15&&hit(c,e,damage,false))displace(c,e,delta,.8,false);}});}
            case AEGIS->{var g=new Guard(c,tick+80);guards.put(p.getUniqueId(),g);slow(c,p,.1,80);show(c,c.origin,c.facing,new EarthVfx.Aegis(g.anchor,false),null);}
            case GRASP->{var center=aimGround(p,7);if(center==null)return noTarget(p);var targets=enemies(c,center,2.5);show(c,center,new Vector(0,0,1),new EarthVfx.Grasp(targets.stream().map(e->local(center,new Vector(0,0,1),e.getLocation())).toList()),null);later(c,14,()->{for(var e:enemies(c,center,2.5)){slow(c,e,.2,40);Vector toward=center.toVector().subtract(e.getLocation().toVector()).setY(0);displace(c,e,toward,Math.min(1.5,toward.length()),true);}});}
            case SINK->{var center=aimGround(p,12);if(center==null)return noTarget(p);fields.add(new Field(c,center,tick,tick+100));show(c,center,new Vector(0,0,1),new EarthVfx.Sinking(p.getUniqueId().getLeastSignificantBits()),null);}
            case OATH->{var ally=aimAlly(p,6);if(ally==null)return noTarget(p);links.removeIf(l->{if(l.cast.source.equals(p)){stop(l.visual);return true;}return false;});var l=new Link(c,ally,tick+120);l.anchor.end=local(c.origin,c.facing,ally.getLocation());links.add(l);l.visual=show(c,c.origin,c.facing,new EarthVfx.Oath(l.anchor,false),ally);}
            case RUSH->{var ally=aimAlly(p,9);if(ally==null)return noTarget(p);Vector direction=ally.getLocation().toVector().subtract(c.origin.toVector()).setY(0);var end=safeEnd(p,direction,Math.min(7,Math.max(0,direction.length()-1)));if(end==null)return noTarget(p);rushing.add(p.getUniqueId());boolean moved;try{moved=p.teleport(end,PlayerTeleportEvent.TeleportCause.PLUGIN);}finally{rushing.remove(p.getUniqueId());}if(!moved)return false;boolean arrived=p.getWorld().equals(ally.getWorld())&&end.distanceSquared(ally.getLocation())<=4&&clear(end.clone().add(0,1,0),ally.getEyeLocation());grant(p,c,"rush_self",2,80);if(arrived)grant(ally,c,"rush_ally",4,80);show(c,c.origin,c.facing,new EarthVfx.Rush(local(c.origin,c.facing,end),arrived),null);}
            case REPRISAL->{var heart=new Guard(c,tick+100);hearts.put(p.getUniqueId(),heart);show(c,c.origin,c.facing,new EarthVfx.Reprisal(heart.anchor,false),null);}
            case TITAN->{var field=new Field(c,c.origin.clone(),tick+25,tick+185);fields.add(field);show(c,c.origin,c.facing,new EarthVfx.Titan(field.anchor),null);later(c,25,()->{for(var e:enemies(c,field.center,7))if(hit(c,e,damage,false))displace(c,e,e.getLocation().toVector().subtract(field.center.toVector()),1.5,false);});}
        }
        empowered(c,s);applySlows();return true;
    }
    private boolean linked(Link l){return tick<l.until&&valid(l.cast)&&combat.ally(l.cast.source,l.ally)&&!combat.protectedAt(l.ally.getLocation())&&l.cast.source.getLocation().distanceSquared(l.ally.getLocation())<=81;}
    private LivingEntity attacker(EntityDamageEvent event){if(!(event instanceof EntityDamageByEntityEvent hit))return null;var damager=hit.getDamager();return damager instanceof LivingEntity l?l:damager instanceof Projectile shot&&shot.getShooter() instanceof LivingEntity l?l:null;}
    private boolean direct(Player victim,EntityDamageEvent e){var a=attacker(e);return a!=null&&!SkillDamageGuard.isTransfer(victim)&&!SkillDamageGuard.isFireTick(a)&&!SkillDamageGuard.isPeriodic(a)&&combat.enemy(victim,a);}
    private boolean excluded(EntityDamageEvent e){return e.getCause()==EntityDamageEvent.DamageCause.VOID||e.getCause()==EntityDamageEvent.DamageCause.KILL||e.getCause()==EntityDamageEvent.DamageCause.SUICIDE;}
    private boolean in(Field f,Player p){return f.cast.talent.getId().equals(EarthSkill.TITAN.id)&&tick>=f.starts&&tick<f.until&&valid(f.cast)&&combat.ally(f.cast.source,p)&&!combat.protectedAt(p.getLocation())&&p.getLocation().distanceSquared(f.center)<=49&&clear(f.center.clone().add(0,.5,0),p.getEyeLocation());}
    /** Called after the shared RPG normalization/armor pass; never registered as a second mitigation listener. */
    public void mitigate(EntityDamageEvent event,double baseline){
        if(event.isCancelled()||!(event.getEntity() instanceof Player p)||SkillDamageGuard.isTransfer(p)||excluded(event))return;
        double factor=1;var guard=guards.get(p.getUniqueId());
        if(guard!=null&&tick<guard.until&&valid(guard.cast)){var a=attacker(event);boolean front=false;if(a!=null){var delta=a.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);front=delta.lengthSquared()>.001&&delta.normalize().dot(flat(p))>=.5;}factor*=front?.6:.85;}
        var heart=hearts.get(p.getUniqueId());if(heart!=null&&tick<heart.until&&!heart.reserved&&valid(heart.cast)&&direct(p,event)){factor*=.55;heart.reserved=true;pending.put(event,heart);}
        if(fields.stream().anyMatch(f->in(f,p)))factor*=.75;
        if(factor<1)event.setDamage(Math.max(Math.max(0,baseline)*.25,event.getDamage()*factor));
    }
    /** Run only after lethal-hit cancellation, using final damage already resolved by the shared balance pass. */
    public void share(EntityDamageEvent event){
        if(event.isCancelled()||!(event.getEntity() instanceof Player p)||excluded(event)||!direct(p,event)||event.getFinalDamage()<=0)return;
        var link=links.stream().filter(l->l.ally.equals(p)&&linked(l)&&!l.cast.source.equals(p)&&combat.enemy(l.cast.source,attacker(event))).findFirst().orElse(null);
        if(link==null)return;var owner=link.cast.source;double requested=Math.min(5,event.getFinalDamage()*.25);if(requested<=0||owner.isInvulnerable())return;
        double before=owner.getHealth()+owner.getAbsorptionAmount();transfer.damage(owner,requested,attacker(event));double received=Math.min(requested,Math.max(0,before-owner.getHealth()-owner.getAbsorptionAmount()));
        if(received<=0)return;setFinal(event,Math.max(0,event.getFinalDamage()-received));gain(owner);link.anchor.pulse=10;
    }
    /** Find a raw value for the desired final health loss, retaining native blocking/absorption modifiers. */
    private static void setFinal(EntityDamageEvent e,double desired){double low=0,high=e.getDamage();for(int i=0;i<40;i++){double mid=(low+high)/2;e.setDamage(mid);if(e.getFinalDamage()>desired)high=mid;else low=mid;}e.setDamage(low);}
    @SuppressWarnings("deprecation")
    private boolean landed(EntityDamageEvent event){double absorbed=event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)?Math.max(0,-event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION)):0;return event.getFinalDamage()+absorbed>0;}
    @EventHandler(priority=EventPriority.MONITOR)public void accepted(EntityDamageEvent event){var heart=pending.remove(event);if(heart!=null)heart.reserved=false;
        if(event.isCancelled()||!landed(event)||!(event.getEntity() instanceof Player p)||!direct(p,event))return;gain(p);
        var guard=guards.get(p.getUniqueId());if(guard!=null){guard.anchor.pulse=4;guard.anchor.strength=Math.clamp(event.getFinalDamage()/10,.2,1);}
        for(var f:fields)if(in(f,p)){f.anchor.pulse=4;f.anchor.impact=local(f.center,f.cast.facing,p.getLocation());}
        if(heart!=null&&hearts.get(p.getUniqueId())==heart){hearts.remove(p.getUniqueId());heart.anchor.triggered=true;var a=attacker(event);heart.anchor.end=local(p.getLocation(),heart.cast.facing,a.getEyeLocation());
            if(a.getWorld().equals(p.getWorld())&&a.getLocation().distanceSquared(p.getLocation())<=25)later(heart.cast,1,()->{if(a.getWorld().equals(p.getWorld())&&a.getLocation().distanceSquared(p.getLocation())<=25&&hit(heart.cast,a,heart.cast.talent.getEffectDouble(heart.cast.rank,"baseDamage",5),false))displace(heart.cast,a,a.getLocation().toVector().subtract(p.getLocation().toVector()),.6,false);});}
    }
    private void anchor(Guard g){g.anchor.offset=v(g.cast.source.getLocation()).sub(v(g.cast.origin));V dir=local(g.cast.source.getLocation(),g.cast.facing,g.cast.source.getLocation().add(flat(g.cast.source)));g.anchor.yaw=Math.atan2(dir.x(),dir.z());if(g.anchor.pulse>0)g.anchor.pulse--;}
    void tick(){tick++;
        for(var job:new ArrayList<>(jobs)){if(!valid(job.cast)){jobs.remove(job);continue;}if(job.at<=tick){jobs.remove(job);try{job.action.run();}catch(RuntimeException e){cancel(job.cast.source);plugin.getLogger().warning("Earth cast cancelled: "+job.cast.talent.getId()+": "+e.getMessage());}}}
        guards.values().removeIf(g->g.until<=tick||!valid(g.cast));hearts.values().removeIf(g->g.until<=tick||!valid(g.cast));guards.values().forEach(this::anchor);hearts.values().forEach(this::anchor);
        links.removeIf(l->{if(!linked(l)){stop(l.visual);return true;}return false;});for(var l:links){l.anchor.offset=v(l.cast.source.getLocation()).sub(v(l.cast.origin));l.anchor.end=local(l.cast.source.getLocation(),l.cast.facing,l.ally.getLocation());if(l.anchor.pulse>0)l.anchor.pulse--;}
        fields.removeIf(f->f.until<=tick||!valid(f.cast));for(var f:fields){if(f.anchor.pulse>0)f.anchor.pulse--;if(tick<f.starts||tick%5!=0)continue;boolean titan=f.cast.talent.getId().equals(EarthSkill.TITAN.id);for(var e:enemies(f.cast,f.center,titan?7:3.5)){slow(f.cast,e,titan?.2:.3,(int)Math.min(6,f.until-tick));if(titan)continue;var state=f.victims.get(e.getUniqueId());if(state!=null&&(state.hits>=3||tick<state.next))continue;double damage=f.cast.talent.getEffectDouble(f.cast.rank,"baseDamage",3)*(state==null?1:2.0/3);if(hit(f.cast,e,damage,true))f.victims.put(e.getUniqueId(),new Victim(tick+40,state==null?1:state.hits+1));}}
        slows.values().removeIf(s->s.until<=tick||!s.target.isValid()||s.target.isDead());applySlows();shields.tick(tick);
        innate.values().removeIf(f->{int n=resilience.stacks(f.cast.source.getUniqueId(),tick);f.anchor.stacks=n;f.anchor.offset=v(f.cast.source.getLocation()).sub(v(f.cast.origin));return n==0||!active(f.cast.source)||!f.cast.source.getWorld().equals(f.cast.origin.getWorld());});
        if(tick%20==0){resilience.prune(tick);var fx=plugin.getVfxService();if(fx!=null)visuals.values().forEach(ids->ids.removeIf(id->!fx.runtime().active(id)));visuals.values().removeIf(Set::isEmpty);}
    }
    public void cancel(Player p){UUID id=p.getUniqueId();jobs.removeIf(j->j.cast.source.equals(p));guards.remove(id);hearts.remove(id);links.removeIf(l->{if(l.cast.source.equals(p)||l.ally.equals(p)){stop(l.visual);return true;}return false;});fields.removeIf(f->f.cast.source.equals(p));slows.values().removeIf(s->s.owner.equals(id)||s.target.equals(p));applySlows();shields.owner(id);shields.target(id);resilience.remove(id);var a=innate.remove(id);if(a!=null)a.anchor.stacks=0;var ids=visuals.remove(id);if(ids!=null&&plugin.getVfxService()!=null)ids.forEach(plugin.getVfxService().runtime()::cancelInstance);pending.entrySet().removeIf(e->e.getValue().cast.source.equals(p));}
    public void clear(){jobs.clear();guards.clear();hearts.clear();links.clear();fields.clear();slows.clear();slowed.values().forEach(motion::clear);slowed.clear();applied.clear();shields.clear();resilience.clear();innate.clear();pending.clear();if(plugin.getVfxService()!=null)visuals.values().forEach(ids->ids.forEach(plugin.getVfxService().runtime()::cancelInstance));visuals.clear();}
    public void shutdown(){if(task!=null){task.cancel();task=null;}clear();}
    @EventHandler public void quit(PlayerQuitEvent e){cancel(e.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent e){cancel(e.getEntity());}
    @EventHandler public void world(PlayerChangedWorldEvent e){cancel(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void entityTeleport(EntityTeleportEvent e){
        if(e.getTo()!=null&&!e.getFrom().getWorld().equals(e.getTo().getWorld())&&e.getEntity() instanceof LivingEntity target){slows.values().removeIf(s->s.target.equals(target));applySlows();}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void teleport(PlayerTeleportEvent e){if(!(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getDark()!=null&&plugin.getTalentSkillManager().getDark().internalStep(e.getPlayer()))&&!internalRush(e.getPlayer())&&!(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getFrost()!=null&&plugin.getTalentSkillManager().getFrost().internalStep(e.getPlayer())))cancel(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void mode(PlayerGameModeChangeEvent e){cancel(e.getPlayer());}
}

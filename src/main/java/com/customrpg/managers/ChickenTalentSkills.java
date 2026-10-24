package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.talents.*;
import com.customrpg.utils.*;
import com.customrpg.utils.ControlledChicken.Kind;
import com.customrpg.vfx.*;
import com.customrpg.vfx.VfxMath.V;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.function.DoubleSupplier;

/** One bounded clock owns eggs, chickens, fields and preview bodies. Particle quality never decides combat. */
public final class ChickenTalentSkills implements Listener {
    private static final NamespacedKey SLOW=new NamespacedKey("customrpg","chicken_slow");
    interface Body {Chicken spawn(Player owner,Location at,Kind kind,boolean preview);}
    interface EggFactory {Egg launch(Player owner,Vector velocity);}
    interface Motion {void slow(LivingEntity target,double percent);double resistance(LivingEntity target);default boolean cleansePotion(Player target){return false;}}
    interface Support {boolean needs(Player target);void give(Player target,double hp,double mana);void refund(Player source);}
    interface NestPlacer {TemporaryChickenNests.Lease place(Player player,Location at);}
    private final CustomRPG plugin;private final ExpandedTalentSkills combat;private final Body body;private final EggFactory launcher;private final Motion motion;private final Support support;private final DoubleSupplier random;
    private long tick;private BukkitTask task;private final TemporaryChickenNests nestBlocks;private final NestPlacer nestPlacer;
    private record Cast(Player source,Talent talent,int rank,Location origin,Vector facing){}
    private record Job(Cast cast,long at,Runnable action){}
    private record Slow(Cast cast,LivingEntity target,double percent,long until){}
    private static final class Unit {
        final Cast cast;final Chicken entity;final Kind kind;final long until;final ChickenVfx.Anchor anchor=new ChickenVfx.Anchor();final Location origin;int attacks;int formation;double fallSpeed;long next;LivingEntity target;boolean consumed,charging;long retire;UUID visual;
        Unit(Cast c,Chicken e,Kind k,long now){cast=c;entity=e;kind=k;until=now+k.ticks;next=now+(k==Kind.GOD?40:k==Kind.ROOSTER?12:k==Kind.HEALER?20:0);origin=e.getLocation().clone();}
    }
    private static final class Shot {
        final Cast cast;final Egg egg;final Location origin;final ChickenVfx.Anchor anchor=new ChickenVfx.Anchor();final long until;boolean spent;UUID visual;
        Shot(Cast c,Egg e,long now){cast=c;egg=e;origin=e.getLocation().clone();until=now+40;var velocity=e.getVelocity();if(velocity.lengthSquared()>1e-8)anchor.travel=new V(velocity.getX(),velocity.getY(),velocity.getZ()).unit();}
    }
    private static final class Feed {
        final Cast cast;final Location at;final long until;final Map<UUID,Long> lured=new HashMap<>();final Set<UUID> seen=new HashSet<>();Feed(Cast c,Location at,long now){cast=c;this.at=at;until=now+80;}
    }
    private static final class Nest {
        final Cast cast;final Location at;final long until;final TemporaryChickenNests.Lease block;final ChickenVfx.Anchor anchor=new ChickenVfx.Anchor();boolean spent;Nest(Cast c,TemporaryChickenNests.Lease block,long now){cast=c;this.block=block;at=block.location();until=now+120;}
    }
    private static final class Charge {
        final Cast cast;final long began;final List<Unit> babies;final Set<UUID> hit=new HashSet<>();Charge(Cast c,long now,List<Unit> babies){cast=c;began=now;this.babies=babies;}
    }
    private static final class God {
        final Cast cast;final Location center;final ChickenVfx.Anchor anchor=new ChickenVfx.Anchor();final long began;Unit unit;UUID visual;boolean landed,refunded;God(Cast c,Location center,long now){cast=c;this.center=center;began=now;}
    }
    private static final class Preview {
        final Player owner;final ChickenSkill skill;final UUID token;final Location at;final Vector facing;final long began;Chicken entity,twin;BlockDisplay display;Preview(Player p,ChickenSkill s,UUID token,Location at,Vector dir,long now){owner=p;skill=s;this.token=token;this.at=at;facing=dir;began=now;}
    }
    private final Map<UUID,Unit> units=new LinkedHashMap<>();private final Map<UUID,Shot> shots=new LinkedHashMap<>();private final List<Feed> feeds=new ArrayList<>();private final List<Nest> nests=new ArrayList<>();private final List<Charge> charges=new ArrayList<>();private final Map<UUID,God> gods=new HashMap<>();private final List<Preview> previews=new ArrayList<>();private final List<Job> jobs=new ArrayList<>();
    private final List<Slow> slows=new ArrayList<>();private final Map<UUID,LivingEntity> slowed=new HashMap<>();private final Map<UUID,Double> applied=new HashMap<>();private final Map<UUID,Set<UUID>> visuals=new HashMap<>();
    public ChickenTalentSkills(CustomRPG p,ExpandedTalentSkills c){this(p,c,null,null,null,null,Math::random);}
    ChickenTalentSkills(CustomRPG p,ExpandedTalentSkills c,Body bodies,EggFactory eggs,Motion movement,Support resources,DoubleSupplier random){this(p,c,bodies,eggs,movement,resources,random,null);}
    ChickenTalentSkills(CustomRPG p,ExpandedTalentSkills c,Body bodies,EggFactory eggs,Motion movement,Support resources,DoubleSupplier random,NestPlacer placer){
        plugin=p;combat=c;nestBlocks=placer==null?new TemporaryChickenNests(p,c::protectedAt):null;nestPlacer=placer!=null?placer:nestBlocks::place;body=bodies==null?ControlledChicken::spawn:bodies;this.random=random;
        launcher=eggs!=null?eggs:(owner,velocity)->owner.launchProjectile(Egg.class,velocity,e->e.getPersistentDataContainer().set(ControlledChicken.EGG,PersistentDataType.BYTE,(byte)1));
        motion=movement!=null?movement:new Motion(){public void slow(LivingEntity e,double n){var a=e.getAttribute(Attribute.MOVEMENT_SPEED);if(a!=null){for(var m:new ArrayList<>(a.getModifiers()))if(m.getKey().equals(SLOW))a.removeModifier(m);if(n>0)a.addTransientModifier(new AttributeModifier(SLOW,-n,AttributeModifier.Operation.MULTIPLY_SCALAR_1));}}public double resistance(LivingEntity e){var a=e.getAttribute(Attribute.KNOCKBACK_RESISTANCE);return a==null?0:Math.clamp(a.getValue(),0,1);}public boolean cleansePotion(Player target){var potion=target.getPotionEffect(PotionEffectType.SLOWNESS);if(potion!=null&&potion.getAmplifier()<4){target.removePotionEffect(PotionEffectType.SLOWNESS);return true;}return false;}};
        support=resources!=null?resources:new Support(){
            public boolean needs(Player t){var m=plugin.getPlayerStatsManager();if(m==null||!m.isDataAvailable(t))return false;var s=m.getStats(t);return t.getHealth()<t.getMaxHealth()||s.getCurrentMana()<s.getMaxMana();}
            public void give(Player t,double hp,double mana){var e=new EntityRegainHealthEvent(t,hp,EntityRegainHealthEvent.RegainReason.CUSTOM);plugin.getServer().getPluginManager().callEvent(e);if(!e.isCancelled()&&Double.isFinite(e.getAmount())&&e.getAmount()>0)t.setHealth(Math.min(t.getMaxHealth(),t.getHealth()+e.getAmount()));plugin.getManaManager().restoreMana(t,mana);}
            public void refund(Player source){var skills=plugin.getTalentSkillManager();if(skills!=null)skills.refundFailedChickenGod(source);}
        };
    }
    public static void validate(Talent t){var s=ChickenSkill.of(t.getId());if(s==null||t.getBranch()!=TalentBranch.CHICKEN||t.getType()!=TalentType.ACTIVE||t.getMaxLevel()!=(s==ChickenSkill.GOD?1:3)||t.getGuiSlot()!=s.slot||t.getPointsPerLevel()!=1||t.getManaCost()!=s.mana||t.getCooldown()!=s.cooldown||t.getHpCost()!=0)throw new IllegalArgumentException("Invalid Chicken layout/cost");for(int n=1;n<=t.getMaxLevel();n++){var d=t.getLevelData(n);double damage=t.getEffectDouble(n,"baseDamage",-1);if(d==null||!d.scaling.isEmpty()||!d.effects.keySet().equals(Set.of("baseDamage","range","spiritScaling"))||!Double.isFinite(damage)||damage<0||damage>32||t.getEffectDouble(n,"range",-1)!=s.range||!Double.isFinite(t.getEffectDouble(n,"spiritScaling",-1))||Math.abs(t.getEffectDouble(n,"spiritScaling",-1)-s.spiritCoefficient()*(s==ChickenSkill.GOD?1:1+.1*(n-1)))>1e-8)throw new IllegalArgumentException("Invalid Chicken data");}}
    public void start(){if(task!=null)return;if(nestBlocks!=null)nestBlocks.start();for(var w:plugin.getServer().getWorlds())for(var e:w.getEntities())if(ControlledChicken.is(e)||ControlledChicken.egg(e))e.remove();task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,1,1);}
    private static Vector flat(Player p){var d=p.getLocation().getDirection().setY(0);return d.lengthSquared()<.001?new Vector(0,0,1):d.normalize();}
    private V v(Location at){return new V(at.getX(),at.getY(),at.getZ());}
    private V local(Location origin,Vector dir,Location point){var f=VfxMath.Frame.facing(v(origin),new V(dir.getX(),dir.getY(),dir.getZ()),0);var d=v(point).sub(v(origin));return new V(d.dot(f.right()),d.dot(f.up()),d.dot(f.forward()));}
    private boolean active(Player p,World w){return p.isOnline()&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&p.getWorld().equals(w)&&!combat.protectedAt(p.getLocation());}
    private boolean valid(Cast c){if(!active(c.source,c.origin.getWorld()))return false;var p=plugin.getTalentManager().getPlayerTalents(c.source);return p.getTalentLevel(c.talent.getId())>0&&(ChickenSkill.ultimate(c.talent.getId())?c.talent.getId().equals(p.getUltimateSkill()):Arrays.asList(p.getSelectedSkills()).contains(c.talent.getId()));}
    private boolean loaded(Location p){return p.getWorld().isChunkLoaded(p.getBlockX()>>4,p.getBlockZ()>>4);}
    private boolean clear(Location from,Location to){if(!from.getWorld().equals(to.getWorld()))return false;var d=to.toVector().subtract(from.toVector());double n=d.length();if(n<.1)return true;d.normalize();for(double x=0;x<=n+.4;x+=.4)if(!loaded(from.clone().add(d.clone().multiply(Math.min(x,n)))))return false;return from.getWorld().rayTraceBlocks(from,d,Math.max(0,n-.05),FluidCollisionMode.NEVER,true)==null;}
    private Location ground(Location at){var w=at.getWorld();if(!loaded(at)||!w.getWorldBorder().isInside(at)||combat.protectedAt(at))return null;for(int y=at.getBlockY()+1;y>=at.getBlockY()-3;y--){if(y<w.getMinHeight()||y+2>=w.getMaxHeight())continue;var b=w.getBlockAt(at.getBlockX(),y,at.getBlockZ());if(!b.isPassable()&&!b.isLiquid()&&w.getBlockAt(at.getBlockX(),y+1,at.getBlockZ()).isPassable()&&!w.getBlockAt(at.getBlockX(),y+1,at.getBlockZ()).isLiquid())return new Location(w,at.getX(),b.getBoundingBox().getMaxY()+.03,at.getZ(),at.getYaw(),0);}return null;}
    private Location aimGround(Player p,int range){var eye=p.getEyeLocation();var dir=eye.getDirection();for(double d=0;d<=range;d+=.5)if(!loaded(eye.clone().add(dir.clone().multiply(d))))return null;var ray=p.getWorld().rayTraceBlocks(eye,dir,range,FluidCollisionMode.NEVER,true);var point=ray==null?p.getLocation().add(flat(p).multiply(Math.max(1,range-1))):ray.getHitPosition().toLocation(p.getWorld());var at=ground(point);return at!=null&&at.distanceSquared(eye)<=(range+.2)*(range+.2)&&clear(eye,at.clone().add(0,.15,0))?at:null;}
    private LivingEntity aim(Player p,int range){var eye=p.getEyeLocation();for(double d=0;d<=range;d+=.5)if(!loaded(eye.clone().add(eye.getDirection().multiply(d))))return null;return combat.aim(p,range);}
    private boolean ally(Player p,Player other){return !other.isDead()&&other.isOnline()&&p.getWorld().equals(other.getWorld())&&!combat.protectedAt(other.getLocation())&&(p.equals(other)||combat.ally(p,other));}
    private List<LivingEntity> enemies(Cast c,Location at,double r){return loaded(at)?combat.enemies(c.source,at,r):List.of();}
    private boolean boss(LivingEntity target){if(target instanceof Wither||target instanceof EnderDragon||target instanceof Warden)return true;var m=plugin.getMobManager();var data=m==null?null:m.getMobData(m.getCustomMobKey(target));return data!=null&&data.isBoss();}
    private boolean permit(Cast c,LivingEntity target,ChickenControlEvent.Kind kind,double amount,int ticks){var e=new ChickenControlEvent(c.source,target,kind,amount,ticks);plugin.getServer().getPluginManager().callEvent(e);return !e.isCancelled();}
    private boolean hit(Cast c,LivingEntity t,double amount,boolean follow){if(!valid(c)||!combat.enemy(c.source,t)||amount<=0)return false;double before=MobManager.getVirtualCurrentHP(t)+t.getAbsorptionAmount();SkillDamageGuard.damageSpirit(t,amount,c.source,follow);return MobManager.getVirtualCurrentHP(t)+t.getAbsorptionAmount()<before;}
    private double damage(Cast c,int babies,boolean godPeck){var stats=plugin.getPlayerStatsManager();double spirit=stats==null||stats.getStats(c.source)==null?0:stats.getStats(c.source).getTotalSpirit();return ChickenSkill.damage(c.talent,c.rank,spirit,babies,godPeck);}
    private UUID show(Cast c,Location at,Vector dir,VfxRuntime.Effect effect,LivingEntity target){var fx=plugin.getVfxService();if(fx==null)return null;UUID id=fx.chicken(c.source,c.talent.getId(),at,dir,effect,VfxRuntime.Mode.COMBAT,target,0);if(id!=null)visuals.computeIfAbsent(c.source.getUniqueId(),x->new HashSet<>()).add(id);return id;}
    private void stop(UUID id){if(id!=null&&plugin.getVfxService()!=null)plugin.getVfxService().runtime().cancelInstance(id);}
    private void later(Cast c,int delay,Runnable r){if(jobs.size()<256)jobs.add(new Job(c,tick+delay,r));}
    private boolean fail(Player p,String why){p.sendMessage("§c"+why+"；未消耗魔力與冷卻。");return false;}
    private void slow(Cast c,LivingEntity target,double n,int duration){if(slows.size()>=2048||target.isDead()||!permit(c,target,ChickenControlEvent.Kind.SLOW,n,duration))return;slows.add(new Slow(c,target,n,tick+duration));}
    private void applySlows(){var values=new HashMap<UUID,Double>();for(var s:slows){slowed.put(s.target.getUniqueId(),s.target);values.merge(s.target.getUniqueId(),s.percent,Math::max);}for(var id:new ArrayList<>(slowed.keySet())){double n=values.getOrDefault(id,0.0);if(n!=applied.getOrDefault(id,-1.0)){motion.slow(slowed.get(id),n);applied.put(id,n);}if(n==0){slowed.remove(id);applied.remove(id);}}}
    public boolean cleanseOrdinarySlow(LivingEntity target){var s=slows.stream().filter(x->x.target.equals(target)&&x.percent<1&&x.until>tick).findFirst().orElse(null);if(s==null)return false;slows.remove(s);applySlows();return true;}
    private void cleanse(Cast c,Player target){if(!permit(c,target,ChickenControlEvent.Kind.CLEANSE,1,0))return;if(motion.cleansePotion(target))return;var s=plugin.getTalentSkillManager();if(s!=null){if(s.getFrost()!=null&&s.getFrost().cleanseOrdinarySlow(target))return;if(s.getEarth()!=null&&s.getEarth().cleanseOrdinarySlow(target))return;if(s.getDark()!=null&&s.getDark().cleanseOrdinarySlow(target))return;if(s.getNecro()!=null&&s.getNecro().cleanseOrdinarySlow(target))return;}cleanseOrdinarySlow(target);}
    private void move(LivingEntity e,Location target,double amount,boolean giant){var start=e.getLocation();var d=target.toVector().subtract(start.toVector()).setY(0);double length=d.length();if(length<.15)return;Location at=ground(start.clone().add(d.normalize().multiply(Math.min(amount,length))));if(at==null||Math.abs(at.getY()-start.getY())>.6)return;double width=giant?ChickenSafety.GOD_WIDTH:.4,height=giant?ChickenSafety.GOD_HEIGHT:.8;if(!ChickenSafety.swept(start,at,width,height,combat::protectedAt))return;at.setDirection(target.toVector().subtract(at.toVector()).setY(0));e.setVelocity(new Vector());e.teleport(at);}
    private boolean walker(Unit u){return u.kind==Kind.HEALER||u.kind==Kind.ROOSTER;}
    /** Independent from steering: a chicken over open air must keep falling even when directly above its goal. */
    private void fall(Unit u){
        Location start=u.entity.getLocation();if(!ChickenSafety.clearVolume(start,.4,.8,combat::protectedAt))return;
        if(u.fallSpeed==0&&!ChickenSafety.clearVolume(start.clone().add(0,-.02,0),.4,.8,combat::protectedAt))return;
        double speed=Math.min(.9,u.fallSpeed+.08);Location next=start.clone().add(0,-speed,0);
        if(ChickenSafety.swept(start,next,.4,.8,combat::protectedAt)){if(u.entity.teleport(next)){u.entity.setVelocity(new Vector());u.fallSpeed=speed*.98;}else u.fallSpeed=0;return;}
        double low=0,high=1;for(int i=0;i<8;i++){double q=(low+high)*.5;if(ChickenSafety.swept(start,start.clone().add(0,-speed*q,0),.4,.8,combat::protectedAt))low=q;else high=q;}
        if(Math.abs(speed*low)>.005)u.entity.teleport(start.clone().add(0,-speed*low,0));u.entity.setVelocity(new Vector());u.fallSpeed=0;
    }
    /** Walk off ledges, hop one-block steps, and keep all movement inside swept body collision checks. */
    private void walk(Unit u,Location target,double speed){
        var e=u.entity;var start=e.getLocation();if(!start.getWorld().equals(target.getWorld()))return;var delta=target.toVector().subtract(start.toVector()).setY(0);if(delta.lengthSquared()<.0225)return;
        double length=delta.length();Location next=start.clone().add(delta.normalize().multiply(Math.min(speed,length)));
        if(ChickenSafety.swept(start,next,.4,.8,combat::protectedAt)){next.setDirection(target.toVector().subtract(next.toVector()).setY(0));e.setVelocity(new Vector());e.teleport(next);return;}
        if(u.fallSpeed!=0||ChickenSafety.clearVolume(start.clone().add(0,-.08,0),.4,.8,combat::protectedAt))return;
        var raised=start.clone().add(0,1.05,0);var over=next.clone().add(0,1.05,0);
        if(ChickenSafety.swept(start,raised,.4,.8,combat::protectedAt)&&ChickenSafety.swept(raised,over,.4,.8,combat::protectedAt))u.fallSpeed=-.5;
    }
    private void push(Cast c,LivingEntity e,Vector direction,double distance){if(boss(e)||!permit(c,e,ChickenControlEvent.Kind.KNOCKBACK,distance,0))return;double resist=motion.resistance(e);var d=direction.clone().setY(0);if(d.lengthSquared()<.001||resist>=1)return;d.normalize();Location start=e.getLocation(),last=null;double width=Math.clamp(e.getWidth(),.3,4.5),height=Math.clamp(e.getHeight(),.5,7.5);for(double x=.2;x<=distance*(1-resist)+.001;x+=.2){Location at=ground(start.clone().add(d.clone().multiply(x)));if(at==null||Math.abs(at.getY()-start.getY())>.6||!ChickenSafety.swept(last==null?start:last,at,width,height,combat::protectedAt))break;last=at;}if(last!=null)e.setVelocity(d.clone().multiply(Math.min(.65,.25*distance*(1-resist))).setY(.15*(1-resist)));}
    private boolean live(Unit u){return !u.consumed&&u.entity.isValid()&&!u.entity.isDead()&&tick<=u.until&&valid(u.cast)&&u.entity.getWorld().equals(u.cast.origin.getWorld())&&!combat.protectedAt(u.entity.getLocation())&&(walker(u)?u.entity.getLocation().toVector().subtract(u.cast.source.getLocation().toVector()).setY(0).lengthSquared():u.entity.getLocation().distanceSquared(u.cast.source.getLocation()))<=(u.kind==Kind.ROOSTER?144:576);}
    private List<Unit> babies(Player p){return units.values().stream().filter(u->u.kind==Kind.BABY&&u.cast.source.equals(p)&&!u.consumed&&u.entity.isValid()&&!u.entity.isDead()).toList();}
    private Unit spawn(Cast c,Location at,Kind kind){if(kind!=Kind.BABY&&units.values().stream().filter(u->u.kind!=Kind.BABY).count()>=160||!valid(c))return null;Chicken e=null;try{var position=at.clone();position.setDirection(c.facing);e=body.spawn(c.source,position,kind,false);if(e==null||!e.isValid()||e.isDead()){if(e!=null)e.remove();return null;}var u=new Unit(c,e,kind,tick);units.put(e.getUniqueId(),u);return u;}catch(RuntimeException error){if(e!=null)e.remove();plugin.getLogger().warning("Chicken summon rejected: "+error.getClass().getSimpleName());return null;}}
    private void remove(Unit u){if(u==null||u.consumed)return;u.consumed=true;units.remove(u.entity.getUniqueId());stop(u.visual);u.entity.remove();}
    private boolean hatch(Cast c,Location origin){var at=ground(origin);return at!=null&&ChickenSafety.clearVolume(at,.3,.5,combat::protectedAt)&&spawn(c,at,Kind.BABY)!=null;}
    public boolean cast(Player p,Talent talent,int rank){var s=ChickenSkill.of(talent.getId());if(s==null)return false;var c=new Cast(p,talent,rank,p.getLocation().clone(),flat(p));if(!valid(c))return false;
        switch(s){
            case EGG->{if(shots.size()>=128||shots.values().stream().filter(x->x.cast.source.equals(p)).count()>=2)return fail(p,"正在飛行的技能雞蛋已達上限");Egg e=null;try{var velocity=p.getEyeLocation().getDirection().multiply(1.05).add(new Vector(0,.14,0));e=launcher.launch(p,velocity);if(e==null||!e.isValid()){if(e!=null)e.remove();return fail(p,"雞蛋生成遭拒");}var shot=new Shot(c,e,tick);shots.put(e.getUniqueId(),shot);shot.visual=show(c,shot.origin,c.facing,new ChickenVfx.Trail(shot.anchor),null);}catch(RuntimeException rejected){if(e!=null)e.remove();return fail(p,"目前無法投擲雞蛋");}}
            case FEED->{Location at=aimGround(p,10);if(at==null||feeds.size()>=64)return fail(p,"請選擇10格內安全地面");feeds.removeIf(f->f.cast.source.equals(p));feeds.add(new Feed(c,at,tick));show(c,at,c.facing,new ChickenVfx.Feeding(),null);}
            case SUPPLY->{
                var axis=new Vector(c.facing.getZ(),0,-c.facing.getX());var behind=p.getLocation().add(c.facing.clone().multiply(-1));var left=ground(behind.clone().add(axis.clone().multiply(-.65)));var right=ground(behind.clone().add(axis.clone().multiply(.65)));
                if(left==null||right==null||!ChickenSafety.clearVolume(left,.4,.8,combat::protectedAt)||!ChickenSafety.clearVolume(right,.4,.8,combat::protectedAt))return fail(p,"身後需要兩處安全召喚位置");
                var first=spawn(c,left,Kind.HEALER);if(first==null)return fail(p,"補給雞生成遭拒");var second=spawn(c,right,Kind.HEALER);if(second==null){remove(first);return fail(p,"補給雞生成遭拒");}first.formation=-1;second.formation=1;
                for(var old:new ArrayList<>(units.values()))if(old.kind==Kind.HEALER&&old.cast.source.equals(p)&&old!=first&&old!=second)remove(old);
                first.visual=show(c,left,c.facing,new ChickenVfx.Supply(first.anchor,false),first.entity);second.visual=show(c,right,c.facing,new ChickenVfx.Supply(second.anchor,false),second.entity);
            }
            case DAWN->{show(c,c.origin,c.facing,new ChickenVfx.Dawn(),null);later(c,7,()->{for(var enemy:enemies(c,p.getLocation(),6)){if(boss(enemy))hit(c,enemy,damage(c,0,false),false);else if(permit(c,enemy,ChickenControlEvent.Kind.INTERRUPT,0,0)&&plugin.getMobEncounterManager()!=null)plugin.getMobEncounterManager().interrupt(enemy);}for(var ally:p.getWorld().getNearbyPlayers(p.getLocation(),6))if(ally(p,ally))cleanse(c,ally);});}
            case NEST->{Location at=aimGround(p,8);if(at==null||nests.size()>=64)return fail(p,"請選擇8格內可放置堆肥桶的安全地面");var block=nestPlacer.place(p,at);if(block==null)return fail(p,"堆肥桶位置被佔用、受保護或無法保存還原紀錄");for(var old:new ArrayList<>(nests))if(old.cast.source.equals(p))closeNest(old);var n=new Nest(c,block,tick);nests.add(n);show(c,n.at,c.facing,new ChickenVfx.Nest(n.anchor,false),null);}
            case ROOSTER->{LivingEntity target=aim(p,10);Location at=ground(p.getLocation().add(c.facing.clone().multiply(.7)));if(target==null||at==null||!ChickenSafety.clearVolume(at,.4,.8,combat::protectedAt))return fail(p,"請瞄準10格內可見敵人並保留召喚空間");var u=spawn(c,at,Kind.ROOSTER);if(u==null)return fail(p,"鬥雞生成遭拒或名額不足");for(var old:new ArrayList<>(units.values()))if(old!=u&&old.kind==Kind.ROOSTER&&old.cast.source.equals(p))remove(old);u.target=target;u.visual=show(c,at,c.facing,new ChickenVfx.Rooster(u.anchor,false),u.entity);}
            case STAMPEDE->{if(charges.size()>=64)return fail(p,"目前雞群衝鋒已達上限");var selected=babies(p).stream().filter(u->!u.charging&&live(u)).toList();selected.forEach(u->u.charging=true);charges.add(new Charge(c,tick,selected));show(c,c.origin,c.facing,new ChickenVfx.Stampede(selected.size()),null);}
            case GOD->{Location at=aimGround(p,12);if(gods.size()>=16||gods.containsKey(p.getUniqueId())||units.values().stream().filter(u->u.kind!=Kind.BABY).count()>=160)return fail(p,"目前雞神召喚名額不足");if(at==null||!ChickenSafety.landing(at,combat::protectedAt))return fail(p,"雞神需要寬敞地面與完整的上方墜落空間");var g=new God(c,at,tick);gods.put(p.getUniqueId(),g);g.visual=show(c,at,c.facing,new ChickenVfx.God(g.anchor),null);}
        }
        return true;
    }
    private void impact(Shot s,Entity target){if(s.spent)return;s.spent=true;shots.remove(s.egg.getUniqueId());stop(s.visual);Location at=s.egg.getLocation().clone();s.egg.remove();if(!valid(s.cast)||!at.getWorld().equals(s.origin.getWorld())||at.distanceSquared(s.origin)>144.25)return;if(target instanceof LivingEntity enemy&&combat.enemy(s.cast.source,enemy))hit(s.cast,enemy,damage(s.cast,0,false),false);boolean hatched=random.getAsDouble()<.30&&hatch(s.cast,at);if(!(target instanceof LivingEntity))show(s.cast,at,s.cast.facing,new ChickenVfx.Shell(hatched),null);}
    private void updateFeed(Feed f){for(var enemy:enemies(f.cast,f.at,3)){if(boss(enemy))continue;UUID id=enemy.getUniqueId();if(enemy instanceof Player){if(f.seen.add(id))slow(f.cast,enemy,.15,30);}else if(enemy instanceof Mob&&!ControlledSummons.is(enemy)){if(!f.seen.contains(id)&&permit(f.cast,enemy,ChickenControlEvent.Kind.LURE,0,40)){f.seen.add(id);f.lured.put(id,tick+40);}if(f.lured.getOrDefault(id,0L)>tick&&tick%2==0)move(enemy,f.at,.22,false);}}}
    private void closeNest(Nest n){n.spent=true;n.block.close();nests.remove(n);}
    private void updateHealer(Unit u){var p=u.cast.source;if(tick%2==0){var face=flat(p);var side=new Vector(face.getZ(),0,-face.getX());walk(u,p.getLocation().add(face.multiply(-1.2)).add(side.multiply(u.formation*.65)),.3);}u.anchor.offset=v(u.entity.getLocation()).sub(v(u.origin));if(tick>=u.next){u.next+=20;for(var friend:u.entity.getWorld().getNearbyPlayers(u.entity.getLocation(),3))if(ally(p,friend)&&support.needs(friend)&&permit(u.cast,friend,ChickenControlEvent.Kind.SUPPLY,1,0))support.give(friend,1,2);}}
    private boolean projectile(Projectile p){return p instanceof Arrow||p instanceof SpectralArrow||p instanceof Snowball||p instanceof Egg;}
    private boolean hostile(Nest n,Projectile p){return p.getShooter() instanceof LivingEntity owner&&combat.enemy(n.cast.source,owner);}
    private boolean intercept(Nest n,Projectile p){if(n.spent||!valid(n.cast)||tick>=n.until||!p.isValid()||!projectile(p)||!hostile(n,p))return false;n.spent=true;n.anchor.triggered=true;n.block.close();p.remove();var shot=shots.remove(p.getUniqueId());if(shot!=null){shot.spent=true;stop(shot.visual);}return true;}
    private boolean crosses(Location a,Location b,Location center,double r){var d=b.toVector().subtract(a.toVector());double q=d.lengthSquared()<1e-8?0:Math.clamp(center.toVector().subtract(a.toVector()).dot(d)/d.lengthSquared(),0,1);return a.toVector().add(d.multiply(q)).distanceSquared(center.toVector())<=r*r;}
    private void updateNest(Nest n){for(var e:n.at.getWorld().getNearbyEntities(n.at,6,5,6)){if(!(e instanceof Projectile p)||!projectile(p))continue;Location at=p.getLocation(),from=at.clone().subtract(p.getVelocity());if(crosses(from,at,n.at.clone().add(0,1,0),3)&&intercept(n,p))break;}}
    private void updateCharge(Charge charge){int age=(int)(tick-charge.began);var c=charge.cast;if(age>=15&&age<=25){double front=(age-14)*10.0/11;Location center=c.origin.clone().add(c.facing.clone().multiply(5));for(var e:center.getWorld().getNearbyEntities(center,8,3,8)){if(!(e instanceof LivingEntity t)||charge.hit.contains(t.getUniqueId())||!combat.enemy(c.source,t))continue;V q=local(c.origin,c.facing,t.getLocation());if(Math.abs(q.x())>2.5||q.z()<0||q.z()>Math.min(10,front)||Math.abs(q.y())>2)continue;Location lane=c.origin.clone().add(new Vector(c.facing.getZ(),0,-c.facing.getX()).multiply(q.x())).add(0,.5,0);if(!clear(c.origin.clone().add(0,.5,0),lane)||!clear(lane,t.getEyeLocation()))continue;charge.hit.add(t.getUniqueId());if(hit(c,t,damage(c,charge.babies.size(),false),false))push(c,t,c.facing,1.5);}}
        for(int i=0;i<charge.babies.size();i++){var u=charge.babies.get(i);if(!live(u))continue;double run=Math.clamp((age-14)/11.0,0,1)*10;Location at=ground(c.origin.clone().add(c.facing.clone().multiply(run)).add(new Vector(c.facing.getZ(),0,-c.facing.getX()).multiply((i%9-4)*.4)));if(at!=null)move(u.entity,at,.65,false);}
        if(age>=35){charge.babies.forEach(this::remove);charges.remove(charge);}}
    private void updateUnit(Unit u){if(u.retire>0){animatePeck(u);if(tick>=u.retire)remove(u);return;}if(u.charging||u.kind==Kind.ROOSTER&&tick<u.until-80+6)return;var p=u.cast.source;if(u.kind==Kind.HEALER){updateHealer(u);return;}if(u.kind==Kind.BABY){if(tick%2==0){Location home=p.getLocation().add(new Vector(u.cast.facing.getZ(),0,-u.cast.facing.getX()).multiply((u.entity.getUniqueId().hashCode()%3)*.5));move(u.entity,home,.24,false);}return;}
        if(u.kind==Kind.GOD){if(u.target==null||!combat.enemy(p,u.target)||u.target.getLocation().distanceSquared(u.entity.getLocation())>36)u.target=enemies(u.cast,u.entity.getLocation(),6).stream().min(Comparator.comparingDouble(e->e.getLocation().distanceSquared(u.entity.getLocation()))).orElse(null);}if(u.target==null||!combat.enemy(p,u.target)){if(u.kind==Kind.ROOSTER)remove(u);return;}
        double reach=u.kind==Kind.GOD?9:1.6;double dist=u.entity.getLocation().distanceSquared(u.target.getLocation());if(dist<=reach&&clear(u.entity.getEyeLocation(),u.target.getEyeLocation())&&tick>=u.next){u.attacks++;u.next=tick+(u.kind==Kind.GOD?40:10);double damage=damage(u.cast,0,u.kind==Kind.GOD);if(hit(u.cast,u.target,damage,u.attacks>1)){u.anchor.pulse=6;u.entity.swingMainHand();show(u.cast,u.target.getEyeLocation(),u.cast.facing,new ChickenVfx.Peck(u.kind==Kind.GOD),u.target);if(u.kind==Kind.ROOSTER&&u.attacks==3)slow(u.cast,u.target,.2,30);}if(u.kind==Kind.ROOSTER&&u.attacks>=3)u.retire=tick+6;}else if(tick%2==0){if(walker(u))walk(u,u.target.getLocation(),.65);else move(u.entity,u.target.getLocation(),.2,true);}
        u.anchor.offset=v(u.entity.getLocation()).sub(v(u.origin));animatePeck(u);}
    private void animatePeck(Unit u){if(u.anchor.pulse>0){if(u.target!=null&&u.entity.getWorld().equals(u.target.getWorld())){var direction=u.target.getLocation().toVector().subtract(u.entity.getLocation().toVector()).setY(0);if(direction.lengthSquared()>.001){var facing=u.entity.getLocation().setDirection(direction);u.entity.setRotation(facing.getYaw(),(float)(40*Math.sin((7-u.anchor.pulse)*Math.PI/6)));}}u.anchor.pulse--;}}
    private void unsafeGod(God g){gods.remove(g.cast.source.getUniqueId());remove(g.unit);if(!g.landed&&!g.refunded){g.refunded=true;support.refund(g.cast.source);g.cast.source.sendMessage("§e雞神的落點或墜落空間失效，已退回魔力與冷卻。");}stop(g.visual);}
    private void updateGod(God g){int age=(int)(tick-g.began);if(!valid(g.cast)){remove(g.unit);gods.remove(g.cast.source.getUniqueId());stop(g.visual);return;}
        if(age==11){if(!ChickenSafety.landing(g.center,combat::protectedAt)){unsafeGod(g);return;}g.unit=spawn(g.cast,g.center.clone().add(0,ChickenSafety.DROP_HEIGHT,0),Kind.GOD);if(g.unit==null||g.unit.entity.getHeight()>ChickenSafety.GOD_HEIGHT+.1||g.unit.entity.getWidth()>ChickenSafety.GOD_WIDTH+.1){unsafeGod(g);return;}g.unit.next=g.began+70;}
        if(g.unit!=null&&(!g.unit.entity.isValid()||g.unit.entity.isDead())){remove(g.unit);gods.remove(g.cast.source.getUniqueId());stop(g.visual);return;}
        if(g.unit!=null&&age<30&&!ChickenSafety.clearVolume(g.unit.entity.getLocation(),ChickenSafety.GOD_WIDTH,ChickenSafety.GOD_HEIGHT,combat::protectedAt)){unsafeGod(g);return;}
        if(age>=23&&age<=30&&g.unit!=null){double q=(age-22)/8.0;Location next=g.center.clone().add(0,8*(1-q*q),0);if(!ChickenSafety.swept(g.unit.entity.getLocation(),next,ChickenSafety.GOD_WIDTH,ChickenSafety.GOD_HEIGHT,combat::protectedAt)){unsafeGod(g);return;}g.unit.entity.setVelocity(new Vector());if(!g.unit.entity.teleport(next)){unsafeGod(g);return;}}
        if(age==30&&g.unit!=null){g.landed=true;for(var t:enemies(g.cast,g.center,7))if(hit(g.cast,t,damage(g.cast,0,false),false))push(g.cast,t,t.getLocation().toVector().subtract(g.center.toVector()),2);}
        if(age>630&&g.unit!=null){Location at=g.unit.entity.getLocation().clone();g.anchor.offset=local(g.center,g.cast.facing,at);boolean alive=g.unit.entity.isValid()&&!g.unit.entity.isDead();remove(g.unit);gods.remove(g.cast.source.getUniqueId());if(alive)for(int i=0;i<3;i++){int index=i;later(g.cast,i*6,()->hatch(g.cast,at.clone().add((index-1)*.65,0,0)));}}
    }
    public boolean preview(Player owner,ChickenSkill s,UUID token,Location at,Vector direction){if(s!=ChickenSkill.GOD&&s!=ChickenSkill.ROOSTER&&s!=ChickenSkill.SUPPLY&&s!=ChickenSkill.NEST)return true;if(previews.size()>=16||previews.stream().anyMatch(p->p.owner.equals(owner)))return false;if(s==ChickenSkill.GOD&&!ChickenSafety.landing(at,x->false))return false;previews.add(new Preview(owner,s,token,at.clone(),direction.clone(),tick));return true;}
    private void updatePreview(Preview p){int age=(int)(tick-p.began);var fx=plugin.getVfxService();if(fx==null||!fx.runtime().active(p.token)||!p.owner.isOnline()||p.owner.isDead()||!p.owner.getWorld().equals(p.at.getWorld())){removePreview(p);return;}
        if(p.skill==ChickenSkill.NEST){
            if(age==1)try{p.display=p.at.getWorld().spawn(p.at.clone().add(-.5,0,-.5),BlockDisplay.class,e->{e.getPersistentDataContainer().set(ControlledChicken.OWNER,PersistentDataType.STRING,p.owner.getUniqueId().toString());e.getPersistentDataContainer().set(ControlledChicken.PREVIEW,PersistentDataType.BYTE,(byte)1);e.setBlock(Material.COMPOSTER.createBlockData());e.setPersistent(false);e.setVisibleByDefault(false);});if(p.display!=null)p.owner.showEntity(plugin,p.display);}catch(RuntimeException failed){stop(p.token);removePreview(p);return;}
            if(age>=120||age>1&&(p.display==null||!p.display.isValid())){stop(p.token);removePreview(p);}return;
        }
        if(p.skill==ChickenSkill.SUPPLY){
            if(age==1)try{p.entity=body.spawn(p.owner,p.at.clone().add(-.65,0,-1),Kind.HEALER,true);p.twin=body.spawn(p.owner,p.at.clone().add(.65,0,-1),Kind.HEALER,true);if(p.entity==null||p.twin==null){stop(p.token);removePreview(p);return;}p.owner.showEntity(plugin,p.entity);p.owner.showEntity(plugin,p.twin);}catch(RuntimeException failed){stop(p.token);removePreview(p);return;}
            if(age>=200||age>1&&(!p.entity.isValid()||!p.twin.isValid())){stop(p.token);removePreview(p);}return;
        }
        int born=p.skill==ChickenSkill.GOD?11:1;if(age==born){Location at=p.at.clone().add(0,p.skill==ChickenSkill.GOD?8:0,0);try{p.entity=body.spawn(p.owner,at,p.skill==ChickenSkill.GOD?Kind.GOD:Kind.ROOSTER,true);if(p.entity==null||!p.entity.isValid()){stop(p.token);removePreview(p);return;}p.owner.showEntity(plugin,p.entity);}catch(RuntimeException failed){stop(p.token);removePreview(p);return;}}
        if(p.entity==null)return;if(!p.entity.isValid()||p.entity.isDead()){stop(p.token);removePreview(p);return;}Location at=p.at.clone();if(p.skill==ChickenSkill.GOD){if(age<=22)at.add(0,8,0);else if(age<=30)at.add(0,8*(1-Math.pow((age-22)/8.0,2)),0);}else at.add(p.facing.clone().multiply(Math.min(6,age*.4)));
        if(!ChickenSafety.swept(p.entity.getLocation(),at,p.skill==ChickenSkill.GOD?4.2:.4,p.skill==ChickenSkill.GOD?7.1:.8,x->false)){stop(p.token);removePreview(p);return;}if(!p.entity.teleport(at)){stop(p.token);removePreview(p);return;}if(age>=(p.skill==ChickenSkill.GOD?630:45))removePreview(p);}
    private void removePreview(Preview p){previews.remove(p);if(p.entity!=null)p.entity.remove();if(p.twin!=null)p.twin.remove();if(p.display!=null)p.display.remove();}
    public void cancelPreviews(Player p){for(var v:new ArrayList<>(previews))if(v.owner.equals(p))removePreview(v);}
    void tick(){tick++;
        for(var g:new ArrayList<>(gods.values()))updateGod(g);
        for(var u:new ArrayList<>(units.values())){if(u.kind==Kind.GOD&&gods.containsKey(u.cast.source.getUniqueId())&&!gods.get(u.cast.source.getUniqueId()).landed)continue;if(!live(u)){remove(u);continue;}if(walker(u))fall(u);if(u.kind!=Kind.GOD||u.attacks<15)updateUnit(u);else animatePeck(u);}
        for(var s:new ArrayList<>(shots.values())){if(!valid(s.cast)||!s.egg.isValid()||tick>=s.until||!s.egg.getWorld().equals(s.origin.getWorld())||s.egg.getLocation().distanceSquared(s.origin)>144){shots.remove(s.egg.getUniqueId());stop(s.visual);s.egg.remove();}else {s.anchor.offset=v(s.egg.getLocation()).sub(v(s.origin));var velocity=s.egg.getVelocity();if(velocity.lengthSquared()>1e-8)s.anchor.travel=new V(velocity.getX(),velocity.getY(),velocity.getZ()).unit();}}
        for(var f:new ArrayList<>(feeds))if(tick>=f.until||!valid(f.cast))feeds.remove(f);else updateFeed(f);
        for(var n:new ArrayList<>(nests))if(n.spent||tick>=n.until||!valid(n.cast)||!n.block.active())closeNest(n);else updateNest(n);
        for(var charge:new ArrayList<>(charges))if(!valid(charge.cast)){charge.babies.forEach(this::remove);charges.remove(charge);}else updateCharge(charge);
        for(var j:new ArrayList<>(jobs))if(!valid(j.cast))jobs.remove(j);else if(j.at<=tick){jobs.remove(j);j.action.run();}
        slows.removeIf(s->tick>=s.until||!valid(s.cast)||!s.target.isValid()||s.target.isDead()||!s.target.getWorld().equals(s.cast.origin.getWorld())||combat.protectedAt(s.target.getLocation()));applySlows();
        for(var p:new ArrayList<>(previews))updatePreview(p);
        if(tick%20==0&&plugin.getVfxService()!=null){var r=plugin.getVfxService().runtime();visuals.values().forEach(ids->ids.removeIf(id->!r.active(id)));visuals.values().removeIf(Set::isEmpty);}
    }
    private LivingEntity attacker(EntityDamageByEntityEvent e){return e.getDamager() instanceof LivingEntity l?l:e.getDamager() instanceof Projectile p&&p.getShooter() instanceof LivingEntity l?l:null;}
    private boolean eggDamage(EntityDamageEvent e){if(e instanceof EntityDamageByEntityEvent b&&ControlledChicken.egg(b.getDamager()))return true;var source=e.getDamageSource();return source!=null&&ControlledChicken.egg(source.getDirectEntity());}
    private void guard(EntityDamageEvent e){if(eggDamage(e)){e.setCancelled(true);return;}if(ControlledChicken.is(e.getEntity())){if(ControlledChicken.preview(e.getEntity())){e.setCancelled(true);return;}var u=units.get(e.getEntity().getUniqueId());LivingEntity source=e instanceof EntityDamageByEntityEvent b?attacker(b):null;if(u==null||!live(u)||source==null||!combat.enemy(u.cast.source,source)||source.hasMetadata("beast_owner"))e.setCancelled(true);}
        if(!e.isCancelled()&&e instanceof EntityDamageByEntityEvent b&&b.getDamager() instanceof Projectile p&&e.getEntity() instanceof Player victim)for(var n:nests)if(!n.spent&&ally(n.cast.source,victim)&&victim.getLocation().distanceSquared(n.at)<=9&&intercept(n,p)){e.setCancelled(true);break;}}
    @EventHandler(priority=EventPriority.LOWEST)public void damage(EntityDamageEvent e){guard(e);}
    @EventHandler(priority=EventPriority.HIGHEST)public void enforce(EntityDamageEvent e){guard(e);}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)public void nestHit(ProjectileHitEvent e){for(var n:nests)if(e.getEntity().getWorld().equals(n.at.getWorld())&&e.getEntity().getLocation().distanceSquared(n.at.clone().add(0,1,0))<=9&&intercept(n,e.getEntity())){e.setCancelled(true);return;}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void eggHit(ProjectileHitEvent e){var shot=shots.get(e.getEntity().getUniqueId());if(shot!=null)impact(shot,e.getHitEntity());}
    @EventHandler(priority=EventPriority.HIGHEST)public void vanillaHatch(PlayerEggThrowEvent e){if(ControlledChicken.egg(e.getEgg())){e.setHatching(false);e.setNumHatches((byte)0);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interact(PlayerInteractEntityEvent e){if(ControlledChicken.is(e.getRightClicked()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void breed(EntityBreedEvent e){if(ControlledChicken.is(e.getMother())||ControlledChicken.is(e.getFather()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void target(EntityTargetLivingEntityEvent e){if(ControlledChicken.is(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void portal(EntityPortalEvent e){if(ControlledChicken.is(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST)public void death(EntityDeathEvent e){if(e.getEntity() instanceof Player p)cancel(p);if(ControlledChicken.is(e.getEntity())){e.getDrops().clear();e.setDroppedExp(0);var u=units.get(e.getEntity().getUniqueId());if(u!=null){if(u.kind==Kind.GOD){var g=gods.remove(u.cast.source.getUniqueId());if(g!=null)stop(g.visual);}remove(u);}}}
    @EventHandler public void load(EntitiesLoadEvent e){for(var t:e.getEntities())if((ControlledChicken.is(t)&&!units.containsKey(t.getUniqueId())&&previews.stream().noneMatch(p->p.entity!=null&&p.entity.equals(t)))||ControlledChicken.egg(t)&&!shots.containsKey(t.getUniqueId()))t.remove();}
    public void cancel(Player p){for(var u:new ArrayList<>(units.values()))if(u.cast.source.equals(p))remove(u);for(var s:new ArrayList<>(shots.values()))if(s.cast.source.equals(p)){s.egg.remove();shots.remove(s.egg.getUniqueId());stop(s.visual);}feeds.removeIf(f->f.cast.source.equals(p));for(var n:new ArrayList<>(nests))if(n.cast.source.equals(p))closeNest(n);charges.removeIf(c->c.cast.source.equals(p));gods.remove(p.getUniqueId());jobs.removeIf(j->j.cast.source.equals(p));slows.removeIf(s->s.cast.source.equals(p));cancelPreviews(p);var ids=visuals.remove(p.getUniqueId());if(ids!=null)ids.forEach(this::stop);applySlows();}
    public void clear(){for(var u:new ArrayList<>(units.values()))remove(u);shots.values().forEach(s->s.egg.remove());shots.clear();feeds.clear();for(var n:new ArrayList<>(nests))closeNest(n);charges.clear();gods.clear();jobs.clear();slows.clear();for(var p:new ArrayList<>(previews))removePreview(p);visuals.values().forEach(ids->ids.forEach(this::stop));visuals.clear();applySlows();}
    public record Stats(int bodies,int eggs,int previews,int fields,int queued){}
    public Stats stats(){return new Stats(units.size(),shots.size(),previews.size(),feeds.size()+nests.size()+charges.size()+gods.size(),jobs.size());}
    public void shutdown(){if(task!=null){task.cancel();task=null;}clear();if(nestBlocks!=null)nestBlocks.shutdown();}
    @EventHandler public void quit(PlayerQuitEvent e){cancel(e.getPlayer());}
    @EventHandler public void world(PlayerChangedWorldEvent e){cancel(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void mode(PlayerGameModeChangeEvent e){cancel(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void teleport(PlayerTeleportEvent e){var s=plugin.getTalentSkillManager();if(s!=null&&(s.getFrost()!=null&&s.getFrost().internalStep(e.getPlayer())||s.getEarth()!=null&&s.getEarth().internalRush(e.getPlayer())||s.getDark()!=null&&s.getDark().internalStep(e.getPlayer())))return;cancel(e.getPlayer());}
}

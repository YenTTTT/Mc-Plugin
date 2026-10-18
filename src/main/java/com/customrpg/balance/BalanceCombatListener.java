package com.customrpg.balance;

import com.customrpg.CustomRPG;
import com.customrpg.managers.MobManager;
import com.customrpg.utils.SkillDamageGuard;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Final RPG mitigation runs after racial/skill effects and before virtual-HP projection. */
public final class BalanceCombatListener implements Listener {
    private static final NamespacedKey VERSION = new NamespacedKey("customrpg","mob_balance_version");
    private static final NamespacedKey VANILLA_LEVEL = new NamespacedKey("customrpg","balance_vanilla_level");
    private static final NamespacedKey GROUP = new NamespacedKey("customrpg","balance_group_size");
    private final CustomRPG plugin;
    private final CombatBalance balance;
    private final Map<EntityDamageEvent,ExtraDamageBudget.Reservation> reservations = new IdentityHashMap<>();
    private final Map<EntityDamageEvent,Double> appliedRaw = new IdentityHashMap<>();
    private final ExtraDamageBudget healing=new ExtraDamageBudget(System::currentTimeMillis,.35,.04);
    private final Map<EntityRegainHealthEvent,ExtraDamageBudget.Reservation> healReservations=new IdentityHashMap<>();
    private final ExtraDamageBudget monsterSkills=new ExtraDamageBudget(System::currentTimeMillis,2.5,.4);
    private final Map<EntityDamageEvent,ExtraDamageBudget.Reservation> monsterReservations=new IdentityHashMap<>();
    private final Map<EntityDamageEvent,UUID> mobAttacks=new IdentityHashMap<>();
    private final Map<UUID,Long> nextMobAttack=new HashMap<>();
    private record FireOwner(UUID source,long expires) {}
    private final Map<UUID,FireOwner> fires=new HashMap<>();
    private BukkitTask cleanup;

    public BalanceCombatListener(CustomRPG plugin, CombatBalance balance) { this.plugin=plugin; this.balance=balance; }
    public void start() {
        for (Player player:plugin.getServer().getOnlinePlayers()) if (!plugin.getPlayerStatsManager().isDataAvailable(player))
            player.kickPlayer("角色資料遷移失敗；原始檔案保留，請通知管理員。");
        for (var world : plugin.getServer().getWorlds()) for (Entity entity : world.getEntities()) migrateMob(entity);
        cleanup=plugin.getServer().getScheduler().runTaskTimer(plugin,()->{
            balance.cleanup(); healing.prune(); monsterSkills.prune();
            long now=System.currentTimeMillis(); nextMobAttack.values().removeIf(until->until<now);
            fires.values().removeIf(fire->fire.expires<now);
        },200L,200L);
    }
    public void shutdown() { if (cleanup!=null) cleanup.cancel(); reservations.clear(); appliedRaw.clear(); healReservations.clear(); healing.clear(); monsterSkills.clear(); monsterReservations.clear(); mobAttacks.clear(); nextMobAttack.clear(); fires.clear(); balance.shutdown(); }

    public static boolean hostile(Entity entity) { return entity instanceof Enemy; }
    public int level(LivingEntity entity) {
        if (plugin.getMobManager().getCustomMobKey(entity)!=null) return BalanceMigration.level(plugin.getMobManager().getMobLevel(entity));
        return BalanceMigration.level(entity.getPersistentDataContainer().getOrDefault(VANILLA_LEVEL,PersistentDataType.INTEGER,1));
    }
    public InitialBalanceModel.Role role(LivingEntity entity) {
        var data=plugin.getMobManager().getMobData(plugin.getMobManager().getCustomMobKey(entity));
        if (data!=null) return data.balanceRole();
        return entity instanceof Wither || entity instanceof EnderDragon || entity instanceof Warden
                ? InitialBalanceModel.Role.WILDERNESS_BOSS : InitialBalanceModel.Role.NORMAL;
    }
    public void migrateMob(Entity entity) {
        if (!(entity instanceof LivingEntity mob) || entity instanceof Player || mob.isDead()) return;
        String key=plugin.getMobManager().getCustomMobKey(mob);
        if (key==null&&!hostile(mob)) return;
        var pdc=mob.getPersistentDataContainer();
        if (Integer.valueOf(BalanceMigration.VERSION).equals(pdc.get(VERSION,PersistentDataType.INTEGER))) return;
        double fraction=MobManager.hasVirtualHP(mob)?MobManager.getVirtualCurrentHP(mob)/MobManager.getVirtualMaxHP(mob)
                :mob.getHealth()/Math.max(.01,mob.getMaxHealth());
        if (key!=null) {
            int level=BalanceMigration.level(plugin.getMobManager().getMobLevel(mob));
            pdc.set(new NamespacedKey("customrpg","mob_level"),PersistentDataType.INTEGER,level);
            String name=mob.getCustomName();
            if (name!=null) mob.setCustomName(name.replaceFirst("Lv\\.[0-9]+","Lv."+level));
        } else {
            int zoneLevel=plugin.getWorldZoneManager()==null?1:plugin.getWorldZoneManager().getBaseLevel(mob.getLocation());
            pdc.set(VANILLA_LEVEL,PersistentDataType.INTEGER,BalanceMigration.level(zoneLevel));
        }
        double hp=InitialBalanceModel.mobHealth(level(mob),role(mob),1);
        MobManager.applyMobMaxHealth(mob,hp);
        double remaining=hp*Math.clamp(fraction,0,1);
        if (MobManager.hasVirtualHP(mob)) MobManager.setVirtualCurrentHP(mob,remaining);
        else mob.setHealth(Math.max(.01,Math.min(mob.getMaxHealth(),remaining)));
        pdc.set(VERSION,PersistentDataType.INTEGER,BalanceMigration.VERSION);
    }
    @EventHandler public void onLoad(EntitiesLoadEvent event) { event.getEntities().forEach(this::migrateMob); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onSpawn(CreatureSpawnEvent event) { migrateMob(event.getEntity()); }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof Projectile shot)) return;
        var attack=balance.calculate(player,event.getBow(),1,event.getForce());
        if (plugin.getPlayerStatsManager().getStats(player).getLevel()<attack.requiredLevel()) { event.setCancelled(true); return; }
        balance.snapshot(shot,attack);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow shot) || !(shot.getShooter() instanceof Player player)) return;
        var item=shot instanceof Trident trident?trident.getItemStack():player.getInventory().getItemInMainHand();
        var attack=balance.calculate(player,item,1,1);
        if (plugin.getPlayerStatsManager().getStats(player).getLevel()<attack.requiredLevel()) { event.setCancelled(true); return; }
        balance.snapshot(shot,attack);
    }

    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void startDamage(EntityDamageByEntityEvent event) {
        Player source=CombatBalance.playerSource(event.getDamager());
        if (source==null) return;
        var direct=balance.directBasic(source);
        if (direct!=null) { event.setDamage(direct.damage()); balance.record(event,source,direct); return; }
        if (event.getDamager() instanceof Projectile shot && !SkillDamageGuard.isSkillDamage(source)) {
            Double raw=shot.getPersistentDataContainer().get(CombatBalance.SHOT_DAMAGE,PersistentDataType.DOUBLE);
            Double dps=shot.getPersistentDataContainer().get(CombatBalance.SHOT_DPS,PersistentDataType.DOUBLE);
            if (raw!=null&&dps!=null&&Double.isFinite(raw)&&Double.isFinite(dps)) {
                double pierce=shot.getPersistentDataContainer().getOrDefault(CombatBalance.SHOT_PIERCE,PersistentDataType.DOUBLE,0.0);
                event.setDamage(raw); balance.record(event,source,raw,dps,pierce);
            }
        }
    }

    private void lockGroup(LivingEntity mob) {
        if (role(mob)!=InitialBalanceModel.Role.GROUP_BOSS || mob.getPersistentDataContainer().has(GROUP)) return;
        int count=(int)mob.getWorld().getNearbyPlayers(mob.getLocation(),48).stream()
                .filter(p->!p.isDead() && (p.getGameMode()==org.bukkit.GameMode.SURVIVAL || p.getGameMode()==org.bukkit.GameMode.ADVENTURE)).limit(8).count();
        count=Math.max(1,count);
        double fraction=MobManager.getVirtualCurrentHP(mob)/Math.max(.01,MobManager.getVirtualMaxHP(mob));
        MobManager.applyMobMaxHealth(mob,InitialBalanceModel.mobHealth(level(mob),role(mob),count));
        if (MobManager.hasVirtualHP(mob)) MobManager.setVirtualCurrentHP(mob,MobManager.getVirtualMaxHP(mob)*fraction);
        else mob.setHealth(Math.max(.01,mob.getMaxHealth()*fraction));
        mob.getPersistentDataContainer().set(GROUP,PersistentDataType.INTEGER,count);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        boolean custom=plugin.getMobManager().getCustomMobKey(target)!=null;
        if (custom||hostile(target)) migrateMob(target);
        Player source=null;
        LivingEntity mobSource=null;
        if (event instanceof EntityDamageByEntityEvent attack) {
            source=CombatBalance.playerSource(attack.getDamager());
            if (attack.getDamager() instanceof LivingEntity living) mobSource=living;
            else if (attack.getDamager() instanceof Projectile shot && shot.getShooter() instanceof LivingEntity living) mobSource=living;
            if (source!=null) {
                if (!plugin.getPlayerStatsManager().isDataAvailable(source)) { event.setCancelled(true); return; }
                if (custom) lockGroup(target);
                var pending=balance.pending(event);
                if (pending==null && attack.getDamager() instanceof AbstractArrow shot
                        && !SkillDamageGuard.isSkillDamage(source)) {
                    Double raw=shot.getPersistentDataContainer().get(CombatBalance.SHOT_DAMAGE,PersistentDataType.DOUBLE);
                    Double dps=shot.getPersistentDataContainer().get(CombatBalance.SHOT_DPS,PersistentDataType.DOUBLE);
                    if (raw==null||dps==null||!Double.isFinite(raw)||!Double.isFinite(dps)) {
                        // Old arrows cannot acquire the currently held weapon's new damage.
                        event.setCancelled(true); return;
                    }
                    event.setDamage(raw); balance.record(event,source,raw,dps); pending=balance.pending(event);
                }
                if (pending!=null) {
                    double requested=event.getDamage();
                    double armor=target instanceof Player victim?InitialBalanceModel.reduction(plugin.getPlayerStatsManager().getStats(victim).getLevel(),
                            plugin.getPlayerStatsManager().getStats(victim).getTotalDefense()+plugin.getPlayerStatsManager().getStats(victim).getBonusDefenseValue())
                            :(custom||hostile(target)?role(target).reduction:0);
                    requested+=requested*armor*Math.clamp(pending.pierce(),0,1)/Math.max(.01,1-armor);
                    if (attack.getDamager().hasMetadata("balanced_extra_arrow")) requested+=Math.max(0,attack.getDamager().getMetadata("balanced_extra_arrow").getFirst().asDouble());
                    double extra=Math.max(0,requested-pending.damage());
                    if (extra>0) {
                        var permit=balance.extras().reserve(source.getUniqueId(),target.getUniqueId(),extra,pending.baselineDps());
                        reservations.put(event,permit); event.setDamage(Math.min(requested,pending.damage()+permit.damage(pending.baselineDps())));
                    }
                } else {
                    double baseline=balance.skillDps(source);
                    double requested=event.getDamage()*Math.sqrt(InitialBalanceModel.referenceDps(plugin.getPlayerStatsManager().getStats(source).getLevel())/InitialBalanceModel.referenceDps(1));
                    var permit=balance.extras().reserve(source.getUniqueId(),target.getUniqueId(),requested,baseline);
                    reservations.put(event,permit); event.setDamage(permit.damage(baseline));
                }
            } else if (mobSource!=null && (plugin.getMobManager().getCustomMobKey(mobSource)!=null||hostile(mobSource))) {
                migrateMob(mobSource);
                double base=InitialBalanceModel.mobRawDamage(level(mobSource),role(mobSource));
                boolean skill=SkillDamageGuard.isSkillDamage(mobSource);
                if (skill) {
                    var permit=monsterSkills.reserve(mobSource.getUniqueId(),target.getUniqueId(),Math.max(0,event.getDamage()),base);
                    monsterReservations.put(event,permit); event.setDamage(permit.damage(base));
                } else {
                    boolean explosion=event.getCause()==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
                    if (!explosion&&nextMobAttack.getOrDefault(mobSource.getUniqueId(),0L)>System.currentTimeMillis()) { event.setCancelled(true); return; }
                    if (!explosion) mobAttacks.put(event,mobSource.getUniqueId());
                    if (attack.getDamager() instanceof Projectile || plugin.getMobManager().getCustomMobKey(mobSource)==null) {
                        @SuppressWarnings("deprecation") double original=event.getOriginalDamage(EntityDamageEvent.DamageModifier.BASE);
                        double ratio=original>0?Math.clamp(event.getDamage()/original,.01,2.5):1;
                        event.setDamage(base*ratio);
                    }
                else event.setDamage(Math.clamp(event.getDamage(),0,base*2.5));
                }
            }
        } else if ((target instanceof Player || custom || hostile(target)) && environment(event.getCause())) {
            double environmentalHp=target instanceof Player?target.getMaxHealth():InitialBalanceModel.mobHealth(level(target),InitialBalanceModel.Role.NORMAL,1);
            event.setDamage(event.getDamage()*environmentalHp/20);
            FireOwner fire=fires.get(target.getUniqueId());
            if (event.getCause()==EntityDamageEvent.DamageCause.FIRE_TICK&&fire!=null&&fire.expires>System.currentTimeMillis()) {
                source=plugin.getServer().getPlayer(fire.source);
                if (source==null) { event.setCancelled(true); return; }
                double dps=balance.skillDps(source);
                var permit=balance.extras().reserve(source.getUniqueId(),target.getUniqueId(),event.getDamage(),dps);
                reservations.put(event,permit); event.setDamage(permit.damage(dps));
            }
        }
        if (target instanceof Player player && !plugin.getPlayerStatsManager().isDataAvailable(player)) { event.setCancelled(true); return; }
        double reduction=0;
        if (target instanceof Player player && (source!=null||event instanceof EntityDamageByEntityEvent)) {
            var stats=plugin.getPlayerStatsManager().getStats(player);
            reduction=InitialBalanceModel.reduction(stats.getLevel(),stats.getTotalDefense()+stats.getBonusDefenseValue());
        } else if (custom||hostile(target)) reduction=role(target).reduction;
        double raw=event.getDamage();
        if (!Double.isFinite(raw)||raw<=0) { event.setCancelled(true); return; }
        double minimum=0;
        if (source!=null&&balance.pending(event)!=null) minimum=balance.pending(event).damage()*.25;
        else if (mobSource!=null&&(hostile(mobSource)||plugin.getMobManager().getCustomMobKey(mobSource)!=null))
            minimum=InitialBalanceModel.mobRawDamage(level(mobSource),role(mobSource))*.25;
        event.setDamage(Math.max(minimum,raw*(1-reduction)));
        if (event instanceof EntityDamageByEntityEvent || source!=null || custom || hostile(target)) removeNativeArmor(event);
        if (target instanceof Player player && event.getFinalDamage()>=player.getHealth() && balance.secondWind(player)) {
            event.setCancelled(true); return;
        }
        appliedRaw.put(event,event.getFinalDamage());
    }

    private static boolean environment(EntityDamageEvent.DamageCause cause) {
        return switch(cause) {
            case FALL,FIRE,FIRE_TICK,LAVA,DROWNING,SUFFOCATION,FREEZE,HOT_FLOOR,CONTACT,POISON,WITHER,STARVATION,FLY_INTO_WALL,VOID,BLOCK_EXPLOSION -> true;
            default -> false;
        };
    }
    @SuppressWarnings("deprecation")
    public static void removeNativeArmor(EntityDamageEvent event) {
        for (var modifier : new EntityDamageEvent.DamageModifier[]{EntityDamageEvent.DamageModifier.ARMOR,
                EntityDamageEvent.DamageModifier.MAGIC,EntityDamageEvent.DamageModifier.RESISTANCE})
            if (event.isApplicable(modifier)) event.setDamage(modifier,0);
        if (event.getEntity() instanceof LivingEntity target && event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)) {
            double beforeAbsorption=event.getFinalDamage()-event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION);
            event.setDamage(EntityDamageEvent.DamageModifier.ABSORPTION,-Math.min(target.getAbsorptionAmount(),Math.max(0,beforeAbsorption)));
        }
        // Native shield blocking and absorption remain effective and are consumed by Paper.
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void afterDamage(EntityDamageEvent event) {
        boolean accepted=!event.isCancelled()&&event.getFinalDamage()>0;
        var permit=reservations.remove(event); if (permit!=null) permit.finish(accepted);
        var monster=monsterReservations.remove(event); if (monster!=null) monster.finish(accepted);
        UUID mob=mobAttacks.remove(event); if (accepted&&mob!=null) nextMobAttack.put(mob,System.currentTimeMillis()+1800);
        Double rpg=appliedRaw.remove(event);
        if (accepted) {
            if (event.getEntity() instanceof Player player) balance.mark(player);
            if (event instanceof EntityDamageByEntityEvent attack) {
                Player source=CombatBalance.playerSource(attack.getDamager()); if (source!=null) balance.mark(source);
                var config=plugin.getConfigManager()==null?null:plugin.getConfigManager().getConfig("config/config.yml");
                if (config!=null&&config.getBoolean("balance.debug-damage",false)) plugin.getLogger().info("[Balance:v2] source="+attack.getDamager().getUniqueId()
                        +" target="+event.getEntity().getUniqueId()+" rpgDamage="+rpg+" nativeDamage="+event.getFinalDamage());
            }
        }
        balance.finish(event);
        balance.runAccepted(event,accepted);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getRegainReason()==EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true); // One out-of-combat recovery timer owns passive healing.
        } else if (event.getRegainReason()==EntityRegainHealthEvent.RegainReason.MAGIC
                || event.getRegainReason()==EntityRegainHealthEvent.RegainReason.MAGIC_REGEN) {
            event.setAmount(Math.min(player.getMaxHealth()*.25,event.getAmount()*player.getMaxHealth()/20));
        } else event.setAmount(Math.min(event.getAmount(),player.getMaxHealth()*.25));
        if (!event.isCancelled()&&balance.inCombat(player)) {
            var permit=healing.reserve(player.getUniqueId(),player.getUniqueId(),event.getAmount(),player.getMaxHealth());
            event.setAmount(permit.damage(player.getMaxHealth())); healReservations.put(event,permit);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void afterHeal(EntityRegainHealthEvent event) {
        var permit=healReservations.remove(event); if (permit!=null) permit.finish(!event.isCancelled()&&event.getAmount()>0);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onDurability(PlayerItemDamageEvent event) {
        String material=event.getItem().getType().name();
        if (event.getDamage()<=0 || !(material.endsWith("_HELMET")||material.endsWith("_CHESTPLATE")
                ||material.endsWith("_LEGGINGS")||material.endsWith("_BOOTS")||material.equals("SHIELD"))) return;
        // RPG HP/damage units must not make armor and shields disintegrate after a handful of ordinary hits.
        event.setDamage(Math.max(1,(int)Math.ceil(event.getDamage()*20.0/Math.max(20,event.getPlayer().getMaxHealth()))));
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { balance.detach(event.getPlayer().getUniqueId()); }
    @EventHandler(priority=EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { balance.mark(event.getPlayer()); }
    @EventHandler public void onDeath(EntityDeathEvent event) {
        UUID id=event.getEntity().getUniqueId(); nextMobAttack.remove(id); fires.remove(id);
        balance.extras().removeTarget(id); monsterSkills.removeTarget(id);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onIgnite(EntityCombustByEntityEvent event) {
        Player source=CombatBalance.playerSource(event.getCombuster());
        if (source!=null&&fires.size()<4096) fires.put(event.getEntity().getUniqueId(),
                new FireOwner(source.getUniqueId(),System.currentTimeMillis()+(long)(Math.min(10,event.getDuration())*1000)+1000));
    }
}

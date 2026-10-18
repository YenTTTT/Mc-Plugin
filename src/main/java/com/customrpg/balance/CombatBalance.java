package com.customrpg.balance;

import com.customrpg.CustomRPG;
import com.customrpg.managers.WeaponManager;
import com.customrpg.players.PlayerStats;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.function.DoubleSupplier;

/** Single canonical player attack calculator, projectile snapshots and final-event audit context. */
public final class CombatBalance {
    public static final NamespacedKey SHOT_DAMAGE = new NamespacedKey("customrpg", "balanced_shot_damage_v2");
    public static final NamespacedKey SHOT_DPS = new NamespacedKey("customrpg", "balanced_shot_dps_v2");
    public record Attack(double damage, double baselineDps, int requiredLevel, double expectedHit,
                         double critChance, double critMultiplier, InitialBalanceModel.Weapon weapon, double pierce) {}
    public record Pending(Player source, double damage, double baselineDps, double pierce) {}
    public static final NamespacedKey SHOT_PIERCE = new NamespacedKey("customrpg", "balanced_shot_pierce_v2");
    private final CustomRPG plugin;
    private final DoubleSupplier random;
    private final Map<EntityDamageEvent,Pending> attacks = new IdentityHashMap<>();
    private final Map<EntityDamageEvent,List<Runnable>> acceptedActions = new IdentityHashMap<>();
    private final Map<UUID,Attack> directBasics = new HashMap<>();
    private final Map<UUID,Long> combat = new HashMap<>();
    private final Map<UUID,Long> secondWind = new HashMap<>();
    private final ExtraDamageBudget extras = new ExtraDamageBudget(System::currentTimeMillis);

    public CombatBalance(CustomRPG plugin) { this(plugin, Math::random); }
    public CombatBalance(CustomRPG plugin, DoubleSupplier random) { this.plugin=plugin; this.random=random; }
    public ExtraDamageBudget extras() { return extras; }
    public Attack directBasic(Player source) { return directBasics.get(source.getUniqueId()); }
    public void basicDamage(LivingEntity target, Player source, Attack attack) {
        Attack previous=directBasics.put(source.getUniqueId(),attack);
        try { com.customrpg.utils.SkillDamageGuard.damage(target,attack.damage,source); }
        finally { if (previous==null) directBasics.remove(source.getUniqueId()); else directBasics.put(source.getUniqueId(),previous); }
    }
    public void afterAccepted(EntityDamageEvent event, Runnable action) {
        acceptedActions.computeIfAbsent(event,ignored->new ArrayList<>()).add(action);
    }
    public void runAccepted(EntityDamageEvent event, boolean accepted) {
        var actions=acceptedActions.remove(event);
        if (accepted&&actions!=null) actions.forEach(Runnable::run);
    }
    public static double rpgFinalDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity mob && com.customrpg.managers.MobManager.hasVirtualHP(mob))
            return event.getFinalDamage()*com.customrpg.managers.MobManager.getVirtualMaxHP(mob)/mob.getMaxHealth();
        return event.getFinalDamage();
    }

    public Attack calculate(Player player, ItemStack item, double cooldown, double force) {
        var stats = plugin.getPlayerStatsManager().getStats(player);
        String key = item == null ? null : plugin.getWeaponManager().getWeaponKey(item);
        var data = key == null ? null : plugin.getWeaponManager().getWeaponData(key);
        String material = item == null ? "AIR" : item.getType().name();
        var category = data == null ? WeaponManager.WeaponCategory.detectFromMaterial(item == null ? org.bukkit.Material.AIR : item.getType(), false) : data.getCategory();
        var weapon = BalanceItemRules.weapon(category);
        int level = data == null ? BalanceItemRules.vanillaLevel(material) : BalanceMigration.level(data.getMinLevel());
        boolean armed = data != null || category != WeaponManager.WeaponCategory.UNKNOWN;
        double unarmed = armed ? 1 : .35;
        double primary = primary(stats, weapon.primary, true);
        double corePrimary = primary(stats, weapon.primary, false);
        double coreAgility = stats.getAgility() + stats.getEquipmentAgility();
        double baseHit = InitialBalanceModel.fullHit(level, weapon, corePrimary) * unarmed;
        double baseCrit = InitialBalanceModel.critChance(coreAgility, 0);
        double expectedBase = baseHit * (1 + baseCrit * .5);
        double baselineDps = expectedBase * weapon.attacksPerSecond * InitialBalanceModel.ACCURACY;
        var races = plugin.getRaceManager(); var race = races == null ? null : races.getPlayerRaceData(player);
        var passives = plugin.getTalentPassiveEffectManager();
        double bonusChance = stats.getBonusCritChance() / 100.0;
        if (race != null) bonusChance += Math.min(.01, race.getBonusCritChance()/100.0);
        if (passives != null) bonusChance += Math.min(.02, Math.max(0,passives.getPlayerBonus(player,"crit-chance"))/100.0);
        double crit = InitialBalanceModel.critChance(stats.getTotalAgility(), bonusChance);
        double critMultiplier = InitialBalanceModel.critMultiplier(stats.getBonusCritDamage()/100.0
                + (race==null?0:Math.min(.05,race.getBonusCritDamage())));
        double permanent = data == null ? 0 : Math.min(.10, data.getRarity().ordinal() * .02);
        if (item!=null) for (var enchantment:item.getEnchantments().entrySet()) {
            String id=enchantment.getKey().getKey().getKey();
            if (id.equals("sharpness")||id.equals("power")||id.equals("impaling")) permanent+=Math.min(.05,enchantment.getValue()*.01);
        }
        if (races != null) permanent += Math.max(-.02, races.getWeaponDamageBonus(player, category.name())-1);
        if (passives != null) permanent += Math.min(.10, Math.max(0,passives.getPlayerBonus(player,"damage-bonus"))/100.0);
        double hit = (InitialBalanceModel.fullHit(level, weapon, primary) * unarmed + stats.getBonusAttackDamage()
                + weaponMastery(player,category,material,stats)) * (1+permanent);
        // Race/talent stats, crit extras and rarity share one +10% permanent budget.
        double expected = hit * (1 + crit * (critMultiplier-1));
        hit *= Math.min(1, expectedBase * 1.10 / Math.max(.001,expected));
        expected = hit * (1 + crit * (critMultiplier-1));
        double charge = .2 + .8 * Math.pow(Math.clamp(cooldown,0,1),2);
        double damage = hit * charge * Math.clamp(force,0,1) * (random.getAsDouble()<crit?critMultiplier:1);
        double pierce=data==null?0:Math.clamp(data.getDoubleExtra("armor-pierce",0)/100.0,0,1);
        return new Attack(damage,baselineDps,armed?level:1,expected,crit,critMultiplier,weapon,pierce);
    }

    private double weaponMastery(Player player, WeaponManager.WeaponCategory category,String material,PlayerStats stats) {
        var talents=plugin.getTalentManager(); if (talents==null) return 0;
        String id=material.endsWith("HORSE_ARMOR")?"weapon_firearms_mastery"
                :category==WeaponManager.WeaponCategory.STAFF?"weapon_magic_mastery"
                :category==WeaponManager.WeaponCategory.BOW?"infinite_arrows":"weapon_melee_mastery";
        var selections=talents.getPlayerTalents(player); var talent=talents.findTalent(id);
        if (selections==null||talent==null) return 0;
        var data=talent.getLevelData(selections.getTalentLevel(id)); if (data==null) return 0;
        double bonus=data.effects.getOrDefault("weaponDamageBonus",0.0);
        if (id.equals("weapon_magic_mastery")) bonus+=stats.getTotalMagic()*data.scaling.getOrDefault("MAGIC",0.0);
        if (id.equals("weapon_firearms_mastery")) bonus+=stats.getTotalAgility()*data.scaling.getOrDefault("AGILITY",0.0);
        return Math.max(0,bonus);
    }

    private static double primary(PlayerStats stats, InitialBalanceModel.Primary primary, boolean allSources) {
        return switch (primary) {
            case STRENGTH -> allSources?stats.getTotalStrength():stats.getStrength()+stats.getEquipmentStrength();
            case MAGIC -> allSources?stats.getTotalMagic():stats.getMagic()+stats.getEquipmentMagic();
            case AGILITY -> allSources?stats.getTotalAgility():stats.getAgility()+stats.getEquipmentAgility();
        };
    }

    public void record(EntityDamageEvent event, Player source, Attack attack) {
        record(event,source,attack.damage,attack.baselineDps,attack.pierce);
    }
    public void record(EntityDamageEvent event, Player source, double damage, double dps) {
        record(event,source,damage,dps,0);
    }
    public void record(EntityDamageEvent event, Player source, double damage, double dps, double pierce) {
        if (attacks.size() > 4096) throw new IllegalStateException("Unfinished balanced damage events");
        attacks.put(event,new Pending(source,damage,dps,pierce));
    }
    public Pending pending(EntityDamageEvent event) { return attacks.get(event); }
    /** Skill allowance uses character power, never the weapon held when a delayed hit arrives. */
    public double skillDps(Player player) {
        var stats=plugin.getPlayerStatsManager().getStats(player);
        double primary=Math.max(stats.getStrength()+stats.getEquipmentStrength(),
                Math.max(stats.getMagic()+stats.getEquipmentMagic(),stats.getAgility()+stats.getEquipmentAgility()));
        return InitialBalanceModel.expectedDps(stats.getLevel(),InitialBalanceModel.Weapon.SWORD,primary,
                stats.getAgility()+stats.getEquipmentAgility(),InitialBalanceModel.ACCURACY);
    }
    public void finish(EntityDamageEvent event) { attacks.remove(event); }
    public void snapshot(Projectile shot, Attack attack) {
        shot.getPersistentDataContainer().set(SHOT_DAMAGE,PersistentDataType.DOUBLE,attack.damage);
        shot.getPersistentDataContainer().set(SHOT_DPS,PersistentDataType.DOUBLE,attack.baselineDps);
        shot.getPersistentDataContainer().set(SHOT_PIERCE,PersistentDataType.DOUBLE,attack.pierce);
    }
    public void mark(Player player) {
        combat.put(player.getUniqueId(),System.currentTimeMillis()+8000);
        if (plugin.getRaceManager()!=null) plugin.getRaceManager().markCombat(player);
    }
    public boolean inCombat(Player player) { return combat.getOrDefault(player.getUniqueId(),0L)>System.currentTimeMillis(); }
    public double heal(LivingEntity target, double amount) {
        if (!Double.isFinite(amount)||amount<=0||target.isDead()) return 0;
        var event=new org.bukkit.event.entity.EntityRegainHealthEvent(target,amount,
                org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.CUSTOM);
        plugin.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled()&&Double.isFinite(event.getAmount())&&event.getAmount()>0&&!target.isDead()) {
            double before=target.getHealth();
            target.setHealth(Math.min(target.getMaxHealth(),before+event.getAmount()));
            return target.getHealth()-before;
        }
        return 0;
    }
    public void ignite(Player source,LivingEntity target,int ticks) {
        if (target.isDead()||ticks<=0) return;
        var event=new org.bukkit.event.entity.EntityCombustByEntityEvent(source,target,Math.min(200,ticks)/20f);
        plugin.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled()&&Float.isFinite(event.getDuration())&&event.getDuration()>0)
            target.setFireTicks(Math.max(target.getFireTicks(),Math.min(200,(int)(event.getDuration()*20))));
    }
    public boolean secondWind(Player player) {
        long now=System.currentTimeMillis();
        var passives=plugin.getTalentPassiveEffectManager();
        double fraction=passives==null?0:Math.clamp(passives.getPlayerBonus(player,"heal-percent")/100.0,0,.35);
        if (fraction<=0||secondWind.getOrDefault(player.getUniqueId(),0L)>now) return false;
        secondWind.put(player.getUniqueId(),now+60_000);
        return heal(player,player.getMaxHealth()*fraction)>0;
    }
    public void detach(UUID player) { combat.remove(player); }
    public void cleanup() { extras.prune(); combat.entrySet().removeIf(e -> e.getValue()<System.currentTimeMillis()); secondWind.entrySet().removeIf(e -> e.getValue()<System.currentTimeMillis()); }
    public void shutdown() { attacks.clear(); acceptedActions.clear(); directBasics.clear(); extras.clear(); combat.clear(); secondWind.clear(); }

    public static Player playerSource(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        if (entity instanceof Tameable pet && pet.getOwner() instanceof Player player) return player;
        if (entity!=null && entity.hasMetadata("beast_owner")) {
            try { return entity.getServer().getPlayer(UUID.fromString(entity.getMetadata("beast_owner").getFirst().asString())); }
            catch (IllegalArgumentException ignored) { return null; }
        }
        return null;
    }
}

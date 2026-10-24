package com.customrpg.utils;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Marks damage dealt by skills / projectiles that already computed their own final damage.
 *
 * {@link LivingEntity#damage(double, Entity)} re-fires EntityDamageByEntityEvent with the caster as
 * damager, which the basic-attack pipeline (WeaponListener) would otherwise treat as a melee hit and
 * recompute (base-damage override, stat bonus, crit, procs). Main-thread only, like all Bukkit damage calls.
 */
public final class SkillDamageGuard {

    private static final Set<UUID> ACTIVE_SOURCES = new HashSet<>();
    private static final Set<UUID> NORMALIZED_SOURCES = new HashSet<>();
    private static final Set<UUID> FIRE_TICK_SOURCES = new HashSet<>();

    private static final Set<UUID> TRANSFER_TARGETS = new HashSet<>();

    private SkillDamageGuard() {
    }

    public static void damage(LivingEntity target, double amount, Entity source) {
        if (source == null) {
            target.damage(amount);
            return;
        }
        boolean added = ACTIVE_SOURCES.add(source.getUniqueId());
        try {
            target.damage(amount, source);
        } finally {
            if (added) {
                ACTIVE_SOURCES.remove(source.getUniqueId());
            }
        }
    }

    private static final Set<UUID> SPIRIT_SOURCES=new HashSet<>();
    public static boolean isSpiritSkill(Entity source){return source!=null&&SPIRIT_SOURCES.contains(source.getUniqueId());}
    /** Chicken skills keep the shared damage pool, using spirit for its baseline instead of weapon attributes. */
    public static void damageSpirit(LivingEntity target,double amount,Entity source,boolean follow){boolean added=SPIRIT_SOURCES.add(source.getUniqueId());try{if(follow)damageFollowUp(target,amount,source,false);else damage(target,amount,source);}finally{if(added)SPIRIT_SOURCES.remove(source.getUniqueId());}}

    public static boolean isSkillDamage(Entity damager) {
        return damager != null && ACTIVE_SOURCES.contains(damager.getUniqueId());
    }
    /** V3 snapshots already use the level-scaled combat model; the shared damage pool still applies. */
    public static void damageNormalized(LivingEntity target, double amount, Entity source) {
        boolean added = NORMALIZED_SOURCES.add(source.getUniqueId());
        try { damage(target, amount, source); }
        finally { if (added) NORMALIZED_SOURCES.remove(source.getUniqueId()); }
    }
    public static boolean isNormalized(Entity source) {
        return source != null && NORMALIZED_SOURCES.contains(source.getUniqueId());
    }

    /** Replaces one native burn tick, retaining fire immunity and native player kill credit. */
    public static void damageFireTick(LivingEntity target, double amount, org.bukkit.entity.Player source) {
        var damageSource = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ON_FIRE)
                .withCausingEntity(source).withDirectEntity(source).build();
        damageFireTick(target,amount,source,damageSource);
    }
    static void damageFireTick(LivingEntity target, double amount, org.bukkit.entity.Player source,
                               org.bukkit.damage.DamageSource damageSource) {
        UUID id = source.getUniqueId();
        boolean active = ACTIVE_SOURCES.add(id);
        boolean normalized = NORMALIZED_SOURCES.add(id);
        boolean fire = FIRE_TICK_SOURCES.add(id);
        try { target.damage(amount, damageSource); }
        finally {
            if (fire) FIRE_TICK_SOURCES.remove(id);
            if (normalized) NORMALIZED_SOURCES.remove(id);
            if (active) ACTIVE_SOURCES.remove(id);
        }
    }
    public static boolean isFireTick(Entity source) {
        return source != null && FIRE_TICK_SOURCES.contains(source.getUniqueId());
    }
    /** Already-final transferred health damage. Nested transfers are rejected, with native attacker credit. */
    public static boolean isTransfer(Entity target){return target!=null&&TRANSFER_TARGETS.contains(target.getUniqueId());}
    public static void transfer(LivingEntity target,double amount,Entity attacker){
        if(amount<=0||isTransfer(target)||!TRANSFER_TARGETS.isEmpty())return;
        var source=org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC)
                .withCausingEntity(attacker).withDirectEntity(attacker).build();
        transfer(target,amount,attacker,source);
    }
    static void transfer(LivingEntity target,double amount,Entity attacker,org.bukkit.damage.DamageSource source){
        if(amount<=0||isTransfer(target)||!TRANSFER_TARGETS.isEmpty())return;
        UUID victim=target.getUniqueId(),owner=attacker.getUniqueId();boolean added=ACTIVE_SOURCES.add(owner);
        TRANSFER_TARGETS.add(victim);
        try{target.damage(amount,source);}finally{TRANSFER_TARGETS.remove(victim);if(added)ACTIVE_SOURCES.remove(owner);}
    }

    private static final Set<UUID> PERIODIC_SOURCES=new HashSet<>();
    public static boolean isPeriodic(Entity source){return source!=null&&PERIODIC_SOURCES.contains(source.getUniqueId());}
    public static void damagePeriodic(LivingEntity target,double amount,Entity source){boolean added=PERIODIC_SOURCES.add(source.getUniqueId());try{damage(target,amount,source);}finally{if(added)PERIODIC_SOURCES.remove(source.getUniqueId());}}

    /** Restricted to a confirmed multi-hit follow-up or an innate extra hit; never opens an invulnerability window after return. */
    public static void damageFollowUp(LivingEntity target,double amount,Entity source,boolean periodic){
        int previous=target.getNoDamageTicks();target.setNoDamageTicks(0);
        try{if(periodic)damagePeriodic(target,amount,source);else damage(target,amount,source);}
        finally{target.setNoDamageTicks(Math.max(previous,target.getNoDamageTicks()));}
    }
    public static void damageNormalizedPeriodic(LivingEntity target,double amount,Entity source){
        boolean added=PERIODIC_SOURCES.add(source.getUniqueId());try{damageNormalized(target,amount,source);}finally{if(added)PERIODIC_SOURCES.remove(source.getUniqueId());}
    }

}

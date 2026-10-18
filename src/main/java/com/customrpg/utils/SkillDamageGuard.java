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

    public static boolean isSkillDamage(Entity damager) {
        return damager != null && ACTIVE_SOURCES.contains(damager.getUniqueId());
    }
}

package com.customrpg.weaponSkills.managers;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * DamageManager (minimal skeleton)
 *
 * Central place to apply skill damage.
 */
public class DamageManager {

    public void dealSkillDamage(Player caster, LivingEntity target, double amount) {
        if (caster == null || target == null) {
            return;
        }
        if (amount <= 0) {
            return;
        }
        target.damage(amount, caster);
    }
}


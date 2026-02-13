package com.customrpg.weaponSkills.managers;

import com.customrpg.managers.WeaponManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Random;

/**
 * DamageManager
 *
 * Central place to apply skill damage with optional weapon stat integration.
 */
public class DamageManager {

    private final Random random = new Random();

    /**
     * Deal skill damage without weapon stat bonuses (legacy behavior)
     */
    public void dealSkillDamage(Player caster, LivingEntity target, double amount) {
        if (caster == null || target == null) {
            return;
        }
        if (amount <= 0) {
            return;
        }
        target.damage(amount, caster);
    }

    /**
     * Deal skill damage with weapon stat bonuses (damage-multiplier and crit)
     *
     * @param caster The player casting the skill
     * @param target The target entity
     * @param baseDamage Base skill damage
     * @param weaponData Weapon data for stat bonuses (can be null)
     * @param applyWeaponMultiplier Whether to apply weapon damage-multiplier
     * @param canCrit Whether this skill can crit
     */
    public void dealSkillDamageWithWeaponStats(Player caster, LivingEntity target, double baseDamage,
                                               WeaponManager.WeaponData weaponData,
                                               boolean applyWeaponMultiplier, boolean canCrit) {
        if (caster == null || target == null) {
            return;
        }
        if (baseDamage <= 0) {
            return;
        }

        double finalDamage = baseDamage;

        // Apply weapon damage multiplier
        if (applyWeaponMultiplier && weaponData != null) {
            finalDamage *= weaponData.getDamageMultiplier();
        }

        // Apply crit chance and multiplier
        if (canCrit && weaponData != null) {
            double critChance = weaponData.getDoubleExtra("crit-chance", 0.0);
            double critMultiplier = weaponData.getDoubleExtra("crit-damage-multiplier", 0.0);

            // Clamp crit chance to 0-100%
            critChance = Math.max(0.0, Math.min(100.0, critChance));

            if (critChance > 0.0 && critMultiplier > 1.0) {
                double roll = random.nextDouble() * 100.0;
                if (roll < critChance) {
                    finalDamage *= critMultiplier;
                    caster.sendMessage("§e✨ 技能暴擊！x" + critMultiplier);
                }
            }
        }

        target.damage(finalDamage, caster);
    }
}

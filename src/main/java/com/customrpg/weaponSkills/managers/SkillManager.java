package com.customrpg.weaponSkills.managers;

import com.customrpg.managers.WeaponManager;
import com.customrpg.weaponSkills.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * SkillManager
 *
 * Dispatch system: listener events -> manager -> skills.
 *
 * This is a new skill system (separate from legacy com.customrpg.managers.SkillManager).
 */
public class SkillManager {

    private final WeaponManager weaponManager;
    private final CooldownManager cooldowns;
    private final DamageManager damage;
    private final BuffManager buffs;
    private final com.customrpg.weaponSkills.util.AoEUtil aoe;
    private final com.customrpg.weaponSkills.util.ParticleUtil particles;

    private final Map<String, Skill> registry = new HashMap<>();

    // weaponKey -> skillId binding (simple)
    private final Map<String, String> weaponActiveSkill = new HashMap<>();

    public SkillManager(WeaponManager weaponManager,
                        CooldownManager cooldowns,
                        DamageManager damage,
                        BuffManager buffs,
                        com.customrpg.weaponSkills.util.AoEUtil aoe,
                        com.customrpg.weaponSkills.util.ParticleUtil particles) {
        this.weaponManager = weaponManager;
        this.cooldowns = cooldowns;
        this.damage = damage;
        this.buffs = buffs;
        this.aoe = aoe;
        this.particles = particles;
    }

    public void registerSkill(Skill skill) {
        if (skill == null) {
            return;
        }
        registry.put(skill.getId().trim().toLowerCase(), skill);
    }

    public void bindWeaponSkill(String weaponKey, String skillId) {
        if (weaponKey == null || skillId == null) {
            return;
        }
        weaponActiveSkill.put(weaponKey.trim().toLowerCase(), skillId.trim().toLowerCase());
    }

    public boolean tryCastWeaponSkill(Player player, SkillTriggerType triggerType, ItemStack itemInHand) {
        if (player == null || triggerType == null || itemInHand == null || itemInHand.getType() == Material.AIR) {
            return false;
        }

        String weaponKey = weaponManager.getWeaponKey(itemInHand);
        if (weaponKey == null) {
            return false;
        }

        String skillId = weaponActiveSkill.get(weaponKey.trim().toLowerCase());
        if (skillId == null) {
            return false;
        }

        Skill skill = registry.get(skillId);
        if (skill == null) {
            return false;
        }

        if (!skill.getSupportedTriggers().contains(triggerType)) {
            return false;
        }

        WeaponManager.WeaponData weaponData = weaponManager.getWeaponData(weaponKey);

        SkillServices services = new SkillServices(cooldowns, damage, buffs, aoe, particles);
        SkillContext ctx = new SkillContext(player, triggerType, itemInHand, weaponKey, weaponData, null, services);

        String cooldownKey = "skill:" + skill.getId() + ":" + player.getUniqueId();
        if (!cooldowns.canCast(player, cooldownKey)) {
            long rem = cooldowns.getRemainingCooldown(player, cooldownKey);
            player.sendMessage("技能冷卻中：" + Math.max(1, (rem + 999) / 1000) + "秒");
            return true;
        }

        boolean executed = skill.cast(ctx);
        if (executed) {
            long cd = Math.max(0L, skill.getCooldownMillis(ctx));
            if (cd > 0) {
                cooldowns.applyCooldown(player, cooldownKey, cd);
            }
        }

        return true;
    }

    public Skill getSkill(String id) {
        if (id == null) {
            return null;
        }
        return registry.get(id.trim().toLowerCase());
    }

    public Set<String> getRegisteredSkillIds() {
        return registry.keySet();
    }
}


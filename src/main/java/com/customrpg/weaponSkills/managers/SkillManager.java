package com.customrpg.weaponSkills.managers;

import com.customrpg.managers.WeaponManager;
import com.customrpg.weaponSkills.*;
import com.customrpg.weaponSkills.skills.ConfigDrivenWeaponSkill;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Locale;
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
    private final com.customrpg.weaponSkills.util.SoundUtil sounds;

    private final Map<String, Skill> registry = new HashMap<>();

    // weaponKey -> skillId binding (optional override)
    private final Map<String, String> weaponActiveSkill = new HashMap<>();

    public SkillManager(WeaponManager weaponManager,
                        CooldownManager cooldowns,
                        DamageManager damage,
                        BuffManager buffs,
                        com.customrpg.weaponSkills.util.AoEUtil aoe,
                        com.customrpg.weaponSkills.util.ParticleUtil particles,
                        com.customrpg.weaponSkills.util.SoundUtil sounds) {
        this.weaponManager = weaponManager;
        this.cooldowns = cooldowns;
        this.damage = damage;
        this.buffs = buffs;
        this.aoe = aoe;
        this.particles = particles;
        this.sounds = sounds;
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

        WeaponManager.WeaponData weaponData = weaponManager.getWeaponData(weaponKey);
        if (weaponData == null) {
            return false;
        }

        // 1) skill id resolve priority:
        // - explicit binding (code)
        // - weapon active-skill-name (from ConfigManager)
        String skillId = weaponActiveSkill.get(weaponKey.trim().toLowerCase());
        if (skillId == null || skillId.isBlank()) {
            skillId = String.valueOf(weaponData.getExtra().getOrDefault("active-skill-name", ""));
        }
        if (skillId == null || skillId.isBlank()) {
            return false;
        }
        skillId = skillId.trim().toLowerCase();

        // 2) trigger check (weapon config stores string like RIGHT_CLICK)
        String expectedTrigger = String.valueOf(weaponData.getExtra().getOrDefault("active-skill-trigger", ""));
        if (expectedTrigger != null && !expectedTrigger.isBlank()) {
            try {
                SkillTriggerType expected = SkillTriggerType.valueOf(expectedTrigger.trim().toUpperCase());
                if (expected != triggerType) {
                    return false;
                }
            } catch (Exception ignored) {
                // if invalid, ignore trigger restriction
            }
        }

        Skill skill = registry.get(skillId);
        if (skill == null) {
            return false;
        }

        if (!skill.getSupportedTriggers().contains(triggerType)) {
            return false;
        }

        SkillServices services = new SkillServices(cooldowns, damage, buffs, aoe, particles, sounds);
        SkillContext ctx = new SkillContext(player, triggerType, itemInHand, weaponKey, weaponData, null, services);

        // Cooldown key per-player + weapon + skill
        String cooldownKey = "weapon_skill:" + weaponKey.toLowerCase() + ":" + skill.getId() + ":" + player.getUniqueId();
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

    /**
     * Auto-register weapon skills from ConfigManager.getAllWeaponSkills().
     *
     * Each entry key is skillId.
     */
    public void registerSkillsFromConfig(Map<String, Map<String, Object>> weaponSkills) {
        if (weaponSkills == null || weaponSkills.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Map<String, Object>> e : weaponSkills.entrySet()) {
            String skillId = e.getKey();
            if (skillId == null || skillId.isBlank()) {
                continue;
            }
            Map<String, Object> data = e.getValue();

            String displayName = data == null ? skillId : String.valueOf(data.getOrDefault("display-name", skillId));
            String type = data == null ? "" : String.valueOf(data.getOrDefault("type", ""));
            String triggerStr = data == null ? "RIGHT_CLICK" : String.valueOf(data.getOrDefault("trigger", "RIGHT_CLICK"));

            Set<SkillTriggerType> triggers = Set.of(parseTrigger(triggerStr));

            registerSkill(new ConfigDrivenWeaponSkill(
                    skillId.trim().toLowerCase(Locale.ROOT),
                    displayName,
                    type,
                    triggers
            ));
        }
    }

    private SkillTriggerType parseTrigger(String s) {
        if (s == null) {
            return SkillTriggerType.RIGHT_CLICK;
        }
        String v = s.trim().toUpperCase(Locale.ROOT);
        try {
            return SkillTriggerType.valueOf(v);
        } catch (Exception ignored) {
            return SkillTriggerType.RIGHT_CLICK;
        }
    }
}

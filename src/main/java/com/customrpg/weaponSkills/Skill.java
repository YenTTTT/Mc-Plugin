package com.customrpg.weaponSkills;

import java.util.Set;

/**
 * Skill
 *
 * Core contract for any castable skill.
 * Keep this interface free of Bukkit Events; execution is done through SkillContext.
 */
public interface Skill {

    /** Unique, stable id (e.g. "dash", "fire_nova", "thorn_spike"). */
    String getId();

    /** Display name for UI. */
    String getName();

    /** Which triggers this skill supports. */
    Set<SkillTriggerType> getSupportedTriggers();

    /** Cooldown in milliseconds for this cast (can be config-driven). */
    long getCooldownMillis(SkillContext context);

    /**
     * Execute the skill.
     *
     * @return true if executed successfully (cooldown should be applied)
     */
    boolean cast(SkillContext context);
}

package com.customrpg.weaponSkills;

import com.customrpg.weaponSkills.managers.BuffManager;
import com.customrpg.weaponSkills.managers.CooldownManager;
import com.customrpg.weaponSkills.managers.DamageManager;
import com.customrpg.weaponSkills.util.AoEUtil;
import com.customrpg.weaponSkills.util.ParticleUtil;

/**
 * SkillServices
 *
 * Service locator passed into SkillContext.
 * Keeps skills loosely coupled from the plugin class.
 */
public record SkillServices(
        CooldownManager cooldowns,
        DamageManager damage,
        BuffManager buffs,
        AoEUtil aoe,
        ParticleUtil particles
) {}

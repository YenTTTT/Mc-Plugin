package com.customrpg.weaponSkills.skills;

import com.customrpg.weaponSkills.BaseSkill;
import com.customrpg.weaponSkills.SkillContext;
import com.customrpg.weaponSkills.SkillTriggerType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Set;

/** FireNovaSkill - AoE damage + explosion particles + burn debuff skeleton. */
public class FireNovaSkill extends BaseSkill {

    public FireNovaSkill() {
        super("fire_nova", "Fire Nova", Set.of(SkillTriggerType.RIGHT_CLICK, SkillTriggerType.WEAPON_USE));
    }

    @Override
    public long getCooldownMillis(SkillContext context) {
        if (context.weaponData() == null) {
            return 8000L;
        }
        int sec = context.weaponData().getIntExtra("active-skill-cooldown", 8);
        return Math.max(0L, sec) * 1000L;
    }

    @Override
    public boolean cast(SkillContext context) {
        if (context.weaponData() == null) {
            return false;
        }

        double damage = context.weaponData().getDoubleExtra("active-skill-damage", 6.0);
        double range = context.weaponData().getDoubleExtra("active-skill-range", 3.5);

        String particle = String.valueOf(context.weaponData().getExtra().getOrDefault("active-skill-particle", "EXPLOSION"));
        String sound = String.valueOf(context.weaponData().getExtra().getOrDefault("active-skill-sound", "entity.generic.explode"));

        Location center = context.caster().getLocation();

        // visuals
        context.services().particles().burst(center, particle, 40, 0.8, 0.5, 0.8, 0.02);

        if (sound != null && !sound.isBlank()) {
            context.services().sounds().playSound(center, sound, 1.0f, 1.0f);
        }

        // targets
        List<LivingEntity> targets = context.services().aoe().getRadiusTargets(context.caster(), center, range);

        for (LivingEntity t : targets) {
            // Apply weapon stats to skill damage (multiplier + crit)
            context.services().damage().dealSkillDamageWithWeaponStats(
                    context.caster(), t, Math.max(0.0, damage),
                    context.weaponData(), true, true);
            // burn debuff example (placeholder): apply to player targets only for now
            if (t instanceof org.bukkit.entity.Player p) {
                context.services().buffs().apply(p, com.customrpg.weaponSkills.managers.BuffManager.BuffType.BURN, 3000);
            }
            t.setFireTicks(Math.max(t.getFireTicks(), 60));
        }

        return true;
    }
}


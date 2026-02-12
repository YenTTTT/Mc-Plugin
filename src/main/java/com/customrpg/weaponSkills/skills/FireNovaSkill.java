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
        return 8000; // 8s default
    }

    @Override
    public boolean cast(SkillContext context) {
        Location center = context.caster().getLocation();

        // visuals
        context.services().particles().burst(center, "EXPLOSION", 40, 0.8, 0.5, 0.8, 0.02);
        center.getWorld().playSound(center, "entity.generic.explode", 1.0f, 1.0f);

        // targets
        List<LivingEntity> targets = context.services().aoe().getRadiusTargets(context.caster(), center, 3.5);

        for (LivingEntity t : targets) {
            context.services().damage().dealSkillDamage(context.caster(), t, 6.0);
            // burn debuff example (placeholder): apply to player targets only for now
            if (t instanceof org.bukkit.entity.Player p) {
                context.services().buffs().apply(p, com.customrpg.weaponSkills.managers.BuffManager.BuffType.BURN, 3000);
            }
            t.setFireTicks(Math.max(t.getFireTicks(), 60));
        }

        return true;
    }
}


package com.customrpg.weaponSkills.skills;

import com.customrpg.weaponSkills.BaseSkill;
import com.customrpg.weaponSkills.SkillContext;
import com.customrpg.weaponSkills.SkillTriggerType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Set;

/**
 * ThornSpikeSkill (藤莿)
 *
 * Config-driven via weapon active-skill.* (damage/range/aoe-width/particle/sound/cooldown).
 */
public class ThornSpikeSkill extends BaseSkill {

    public ThornSpikeSkill() {
        super("thorn_spike", "藤莿", Set.of(SkillTriggerType.RIGHT_CLICK, SkillTriggerType.WEAPON_USE));
    }

    @Override
    public long getCooldownMillis(SkillContext context) {
        if (context.weaponData() == null) {
            return 0L;
        }
        int sec = context.weaponData().getIntExtra("active-skill-cooldown", 0);
        return Math.max(0L, sec) * 1000L;
    }

    @Override
    public boolean cast(SkillContext context) {
        if (context.weaponData() == null) {
            return false;
        }

        double damage = context.weaponData().getDoubleExtra("active-skill-damage", 0.0);
        double range = context.weaponData().getDoubleExtra("active-skill-range", 3.0);
        double width = context.weaponData().getDoubleExtra("active-skill-aoe-width", 3.0);

        String particle = String.valueOf(context.weaponData().getExtra().getOrDefault("active-skill-particle", "SCULK_CHARGE"));
        String sound = String.valueOf(context.weaponData().getExtra().getOrDefault("active-skill-sound", "entity.dolphin.hurt"));

        // visuals center in front
        Location origin = context.caster().getLocation();
        Vector dir = origin.getDirection().normalize();
        Location center = origin.clone().add(dir.multiply(Math.max(0.5, range)));

        context.services().particles().burst(center, particle, 80, width / 2.0, 0.8, width / 2.0, 0.02);

        if (sound != null && !sound.isBlank()) {
            context.services().sounds().playSound(origin, sound, 1.0f, 1.0f);
        }

        // targets: box around center
        List<LivingEntity> targets = context.services().aoe().getBoxTargets(
                context.caster(),
                center,
                Math.max(0.5, width / 2.0),
                1.5,
                Math.max(0.5, width / 2.0)
        );

        for (LivingEntity t : targets) {
            context.services().damage().dealSkillDamage(context.caster(), t, Math.max(0.0, damage));
        }

        return true;
    }
}

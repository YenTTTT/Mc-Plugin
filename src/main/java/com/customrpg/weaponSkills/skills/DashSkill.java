package com.customrpg.weaponSkills.skills;

import com.customrpg.weaponSkills.BaseSkill;
import com.customrpg.weaponSkills.SkillContext;
import com.customrpg.weaponSkills.SkillTriggerType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Set;

/** DashSkill - Movement burst with particle trail. */
public class DashSkill extends BaseSkill {

    public DashSkill() {
        super("dash", "Dash", Set.of(SkillTriggerType.RIGHT_CLICK, SkillTriggerType.WEAPON_USE));
    }

    @Override
    public long getCooldownMillis(SkillContext context) {
        if (context.weaponData() == null) {
            return 3000L;
        }
        int sec = context.weaponData().getIntExtra("active-skill-cooldown", 3);
        return Math.max(0L, sec) * 1000L;
    }

    @Override
    public boolean cast(SkillContext context) {
        Player player = context.caster();

        String particle = String.valueOf(context.weaponData().getExtra().getOrDefault("active-skill-particle", "CLOUD"));
        String sound = String.valueOf(context.weaponData().getExtra().getOrDefault("active-skill-sound", "entity.player.attack.sweep"));

        Vector direction = player.getLocation().getDirection().normalize();
        direction.setY(0.25);
        direction.multiply(1.8);
        player.setVelocity(direction);

        context.services().particles().trail(player, particle, 10, 0.25);

        Location loc = player.getLocation();
        if (sound != null && !sound.isBlank()) {
            context.services().sounds().playSound(loc, sound, 1.0f, 1.2f);
        }

        return true;
    }
}

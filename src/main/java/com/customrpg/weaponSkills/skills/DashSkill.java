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
        return 3000L;
    }

    @Override
    public boolean cast(SkillContext context) {
        Player player = context.caster();

        Vector direction = player.getLocation().getDirection().normalize();
        direction.setY(0.25);
        direction.multiply(1.8);
        player.setVelocity(direction);

        context.services().particles().trail(player, "CLOUD", 10, 0.25);

        Location loc = player.getLocation();
        player.getWorld().playSound(loc, "entity.player.attack.sweep", 1.0f, 1.2f);

        return true;
    }
}

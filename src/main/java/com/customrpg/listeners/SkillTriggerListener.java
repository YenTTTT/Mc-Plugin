package com.customrpg.listeners;

import com.customrpg.talents.Talent;
import com.customrpg.weaponSkills.SkillTriggerType;
import com.customrpg.weaponSkills.managers.SkillManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * SkillTriggerListener
 *
 * Listener-based trigger routing. No skill logic here.
 */
public class SkillTriggerListener implements Listener {

    private final SkillManager skillManager;

    public SkillTriggerListener(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        Player player = event.getPlayer();
        SkillTriggerType trigger = null;
        
        boolean isSneaking = player.isSneaking();

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            trigger = isSneaking ? SkillTriggerType.RIGHT_CLICK_SNEAK : SkillTriggerType.RIGHT_CLICK;
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            trigger = isSneaking ? SkillTriggerType.LEFT_CLICK_SNEAK : SkillTriggerType.LEFT_CLICK;
        }
        
        if (trigger == null) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return;
        }

        // 1) 嘗試觸發武器內建技能 (舊系統)
        boolean handled = skillManager.tryCastWeaponSkill(player, trigger, item);
        
        // 2) 嘗試觸發天賦技能
        if (!handled) {
            com.customrpg.managers.TalentSkillManager tsm = skillManager.getWeaponManager().getPlugin().getTalentSkillManager();
            if (tsm != null) {
                handled = tsm.executeTalentSkill(player, trigger.name(), item);
            }
        }

        if (handled) {
            event.setCancelled(true);
        }
    }
}

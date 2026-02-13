package com.customrpg.listeners;

import com.customrpg.weaponSkills.SkillTriggerType;
import com.customrpg.weaponSkills.managers.SkillManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

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
        SkillTriggerType trigger = null;
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            trigger = SkillTriggerType.RIGHT_CLICK;
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            trigger = SkillTriggerType.LEFT_CLICK;
        }
        if (trigger == null) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return;
        }

        boolean handled = skillManager.tryCastWeaponSkill(player, trigger, item);
        if (handled) {
            event.setCancelled(true);
        }
    }
}

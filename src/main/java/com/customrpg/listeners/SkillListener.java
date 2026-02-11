package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.SkillManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * SkillListener - Handles skill activation events
 *
 * This listener processes player interactions with skill items and triggers
 * the corresponding skill effects. It manages cooldowns and provides feedback
 * to players about skill usage.
 *
 * Handles:
 * - Fireball: Launch projectile on right-click
 * - Heal: Restore health on left-click
 * - Dash: Quick movement on right-click
 */
public class SkillListener implements Listener {

    private final CustomRPG plugin;
    private final SkillManager skillManager;

    /**
     * Constructor for SkillListener
     * @param plugin Main plugin instance
     * @param skillManager SkillManager instance
     */
    public SkillListener(CustomRPG plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
    }

    /**
     * Handle player interact events for skill activation
     * @param event PlayerInteractEvent
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        Action action = event.getAction();
        String activationType = null;

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            activationType = "RIGHT_CLICK";
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            activationType = "LEFT_CLICK";
        }

        if (activationType == null) {
            return;
        }

        String skillKey = skillManager.findSkillByItem(item.getType(), activationType);
        if (skillKey == null) {
            return;
        }

        if (!skillManager.canUseSkill(player, skillKey)) {
            long remainingCooldown = skillManager.getRemainingCooldown(player, skillKey);
            player.sendMessage(ChatColor.RED + "⏱ Skill on cooldown! " + remainingCooldown + "s remaining");
            event.setCancelled(true);
            return;
        }

        SkillManager.SkillData skillData = skillManager.getSkillData(skillKey);
        if (skillData == null) {
            return;
        }

        if (skillManager.useSkill(player, skillKey)) {
            activateSkill(player, skillData);
            player.sendMessage(ChatColor.GREEN + "✓ " + skillData.getName() + " activated!");
            event.setCancelled(true);
        }
    }

    /**
     * Activate the skill effect
     * @param player The player using the skill
     * @param skillData The skill data
     */
    private void activateSkill(Player player, SkillManager.SkillData skillData) {
        String effect = skillData.getEffect();

        switch (effect.toLowerCase()) {
            case "fireball":
                launchFireball(player);
                break;

            case "heal":
                healPlayer(player);
                break;

            case "dash":
                dashPlayer(player);
                break;

            default:
                plugin.getLogger().warning("Unknown skill effect: " + effect);
                break;
        }
    }

    /**
     * Launch a fireball projectile
     * @param player The player
     */
    private void launchFireball(Player player) {
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setVelocity(player.getLocation().getDirection().multiply(2));
        fireball.setYield(2.0F);
        player.sendMessage(ChatColor.GOLD + "🔥 Fireball launched!");
    }

    /**
     * Heal the player
     * @param player The player
     */
    private void healPlayer(Player player) {
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double currentHealth = player.getHealth();
        double healAmount = 10.0;

        double newHealth = Math.min(currentHealth + healAmount, maxHealth);
        player.setHealth(newHealth);
        player.sendMessage(ChatColor.GREEN + "❤ Healed " + (int)healAmount + " HP!");
    }

    /**
     * Dash the player forward
     * @param player The player
     */
    private void dashPlayer(Player player) {
        Vector direction = player.getLocation().getDirection();
        direction.setY(0.3);
        direction.multiply(2.5);

        player.setVelocity(direction);
        player.sendMessage(ChatColor.AQUA + "➤ Dash!");
    }
}

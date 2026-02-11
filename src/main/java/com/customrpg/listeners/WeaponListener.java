package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.WeaponManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * WeaponListener - Handles custom weapon attack events
 *
 * This listener processes attacks made with custom weapons and applies
 * their special effects such as bonus damage, burning, lightning strikes,
 * or directional damage modifiers.
 *
 * Handles:
 * - Iron Scythe: Extra damage from behind attacks
 * - Fire Sword: Burning effect on hit
 * - Thunder Axe: Random lightning strikes
 */
public class WeaponListener implements Listener {

    private final CustomRPG plugin;
    private final WeaponManager weaponManager;
    private final Random random;

    /**
     * Constructor for WeaponListener
     * @param plugin Main plugin instance
     * @param weaponManager WeaponManager instance
     */
    public WeaponListener(CustomRPG plugin, WeaponManager weaponManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
        this.random = new Random();
    }

    /**
     * Handle entity damage events for custom weapon effects
     * @param event EntityDamageByEntityEvent
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        String weaponKey = weaponManager.getWeaponKey(weapon);
        if (weaponKey == null) {
            return;
        }

        WeaponManager.WeaponData weaponData = weaponManager.getWeaponData(weaponKey);
        if (weaponData == null) {
            return;
        }

        double baseDamage = event.getDamage();
        double newDamage = baseDamage * weaponData.getDamageMultiplier();
        event.setDamage(newDamage);

        applySpecialEffect(player, event.getEntity(), weaponData);
    }

    /**
     * Apply special weapon effects based on weapon type
     * @param attacker The attacking player
     * @param victim The victim entity
     * @param weaponData The weapon data
     */
    private void applySpecialEffect(Player attacker, org.bukkit.entity.Entity victim, WeaponManager.WeaponData weaponData) {
        String effect = weaponData.getSpecialEffect();

        switch (effect.toLowerCase()) {
            case "backstab":
                applyBackstabEffect(attacker, victim, weaponData);
                break;

            case "burn":
            case "fire":
                applyBurnEffect(attacker, victim);
                break;

            case "lightning":
                applyLightningEffect(attacker, victim);
                break;

            default:
                plugin.getLogger().warning("Unknown weapon effect: " + effect);
                break;
        }
    }

    /**
     * Apply backstab effect (extra damage from behind)
     * @param attacker The attacking player
     * @param victim The victim entity
     * @param weaponData The weapon data
     */
    private void applyBackstabEffect(Player attacker, org.bukkit.entity.Entity victim, WeaponManager.WeaponData weaponData) {
        if (!(victim instanceof LivingEntity)) {
            return;
        }

        LivingEntity livingVictim = (LivingEntity) victim;

        Vector attackerDirection = attacker.getLocation().getDirection().normalize();
        Vector victimDirection = victim.getLocation().getDirection().normalize();

        double dotProduct = attackerDirection.dot(victimDirection);

        // If attacking from behind (same direction)
        if (dotProduct > 0.5) {
            double bonusDamage = 4.0;
            livingVictim.damage(bonusDamage);
            attacker.sendMessage(ChatColor.RED + "✦ 鐵鐮刀: 背刺! +" + bonusDamage + " 額外傷害!");
            attacker.getWorld().playSound(attacker.getLocation(), "entity.player.attack.crit", 1.0f, 0.8f);
        }
    }

    /**
     * Apply burn/fire effect (sets target on fire)
     * @param attacker The attacking player
     * @param victim The victim entity
     */
    private void applyBurnEffect(Player attacker, org.bukkit.entity.Entity victim) {
        if (!(victim instanceof LivingEntity)) {
            return;
        }

        // Set fire for 5 seconds (100 ticks)
        victim.setFireTicks(100);

        // Visual and audio feedback
        attacker.sendMessage(ChatColor.GOLD + "🔥 烈焰之劍: 目標燃燒中!");

        // Play fire sound
        attacker.getWorld().playSound(attacker.getLocation(), "entity.blaze.shoot", 1.0f, 1.0f);
    }

    /**
     * Apply lightning effect (random lightning strike)
     * @param attacker The attacking player
     * @param victim The victim entity
     */
    private void applyLightningEffect(Player attacker, org.bukkit.entity.Entity victim) {
        // 30% chance to trigger lightning
        if (random.nextDouble() < 0.3) {
            Location strikeLocation = victim.getLocation();
            victim.getWorld().strikeLightning(strikeLocation);
            attacker.sendMessage(ChatColor.AQUA + "⚡ 雷霆戰斧: 召喚閃電!");
        }
    }
}

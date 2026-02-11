package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.WeaponManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
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
    @EventHandler(priority = EventPriority.HIGHEST)
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

        // 1) Base damage
        double baseDamage = event.getDamage();
        double baseDamageOverride = weaponData.getDoubleExtra("base-damage", 0.0);
        if (baseDamageOverride > 0.0) {
            baseDamage = baseDamageOverride;
        }

        // 2) damage multiplier
        double damageAfterMultiplier = baseDamage * weaponData.getDamageMultiplier();

        // 3) crit
        double critChancePercent = weaponData.getDoubleExtra("crit-chance", 0.0);
        double critDamageMultiplier = weaponData.getDoubleExtra("crit-damage-multiplier", 0.0);
        boolean isCrit = false;

        if (critChancePercent > 0.0 && critDamageMultiplier > 1.0) {
            double roll = random.nextDouble() * 100.0;
            if (roll < critChancePercent) {
                damageAfterMultiplier *= critDamageMultiplier;
                isCrit = true;
            }
        }

        event.setDamage(damageAfterMultiplier);

        if (isCrit) {
            player.sendMessage(ChatColor.YELLOW + "✨ 暴擊！x" + critDamageMultiplier);
        }

        // 4) 額外擊退推力（yml 的 knockback）
        double extraKnockback = weaponData.getDoubleExtra("knockback", 0.0);
        if (extraKnockback > 0 && event.getEntity() instanceof LivingEntity) {
            Vector dir = event.getEntity().getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
            Vector kb = dir.multiply(extraKnockback * 0.2); // 0.2: 避免太誇張
            kb.setY(Math.min(0.4, kb.getY() + 0.1));
            event.getEntity().setVelocity(event.getEntity().getVelocity().add(kb));
        }

        applySpecialEffect(player, event.getEntity(), weaponData);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        String weaponKey = weaponManager.getWeaponKey(item);
        if (weaponKey == null) {
            return;
        }

        WeaponManager.WeaponData weaponData = weaponManager.getWeaponData(weaponKey);
        if (weaponData == null) {
            return;
        }

        double mult = weaponData.getDoubleExtra("durability-cost-multiplier", 1.0);
        if (mult <= 0) {
            event.setCancelled(true);
            return;
        }

        int newDamage = (int) Math.round(event.getDamage() * mult);
        event.setDamage(Math.max(0, newDamage));
    }

    /**
     * Apply special weapon effects based on weapon type
     * @param attacker The attacking player
     * @param victim The victim entity
     * @param weaponData The weapon data
     */
    private void applySpecialEffect(Player attacker, org.bukkit.entity.Entity victim, WeaponManager.WeaponData weaponData) {
        String effect = weaponData.getSpecialEffect();
        if (effect == null) {
            return;
        }

        switch (effect.toLowerCase()) {
            case "backstab":
                applyBackstabEffect(attacker, victim, weaponData);
                break;

            case "burn":
            case "fire":
                applyBurnEffect(attacker, victim, weaponData);
                break;

            case "lightning":
                applyLightningEffect(attacker, victim, weaponData);
                break;

            default:
                // effect=none or unknown
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

        // 新格式：必須啟用背刺才生效；舊格式沒有這個欄位時，預設啟用
        boolean backstabEnabled = weaponData.getBooleanExtra("backstab-enabled", true);
        if (!backstabEnabled) {
            return;
        }

        LivingEntity livingVictim = (LivingEntity) victim;

        Vector attackerDirection = attacker.getLocation().getDirection().normalize();
        Vector victimDirection = victim.getLocation().getDirection().normalize();

        double dotProduct = attackerDirection.dot(victimDirection);

        if (dotProduct > 0.5) {
            // 以倍率做為附加傷害（基礎：原本固定 4.0）
            double multiplier = weaponData.getDoubleExtra("backstab-multiplier", 1.0);
            double bonusDamage = 4.0 * Math.max(0.0, multiplier);
            livingVictim.damage(bonusDamage);
            attacker.sendMessage(ChatColor.RED + "✦ 背刺! +" + bonusDamage + " 額外傷害!");
            attacker.getWorld().playSound(attacker.getLocation(), "entity.player.attack.crit", 1.0f, 0.8f);
        }
    }

    /**
     * Apply burn/fire effect (sets target on fire)
     * @param attacker The attacking player
     * @param victim The victim entity
     * @param weaponData The weapon data
     */
    private void applyBurnEffect(Player attacker, org.bukkit.entity.Entity victim, WeaponManager.WeaponData weaponData) {
        if (!(victim instanceof LivingEntity)) {
            return;
        }

        int durationTicks = weaponData.getIntExtra("burn-duration-ticks", 100);
        victim.setFireTicks(Math.max(0, durationTicks));

        attacker.sendMessage(ChatColor.GOLD + "🔥 目標燃燒中! (" + durationTicks + " ticks)\n");
        attacker.getWorld().playSound(attacker.getLocation(), "entity.blaze.shoot", 1.0f, 1.0f);
    }

    /**
     * Apply lightning effect (random lightning strike)
     * @param attacker The attacking player
     * @param victim The victim entity
     * @param weaponData The weapon data
     */
    private void applyLightningEffect(Player attacker, org.bukkit.entity.Entity victim, WeaponManager.WeaponData weaponData) {
        double chance = weaponData.getDoubleExtra("lightning-chance", 0.3);
        if (random.nextDouble() < chance) {
            Location strikeLocation = victim.getLocation();
            victim.getWorld().strikeLightning(strikeLocation);
            attacker.sendMessage(ChatColor.AQUA + "⚡ 召喚閃電! (" + (int) (chance * 100) + "%)");
        }
    }
}

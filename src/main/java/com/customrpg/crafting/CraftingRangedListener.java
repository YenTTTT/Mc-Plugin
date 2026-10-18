package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.function.DoubleSupplier;

/** Snapshot the actual bow/trident at launch; changing held weapons cannot change an airborne shot. */
public final class CraftingRangedListener implements Listener {
    private static final NamespacedKey DAMAGE = new NamespacedKey("customrpg", "forge_shot_damage");
    private final CustomRPG plugin; private final CraftingCatalog catalog; private final CraftingItems items; private final DoubleSupplier random;
    public CraftingRangedListener(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items) { this(plugin, catalog, items, Math::random); }
    CraftingRangedListener(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items, DoubleSupplier random) {
        this.plugin = plugin; this.catalog = catalog; this.items = items; this.random = random;
    }
    private boolean prepare(Player player, Projectile projectile, ItemStack item, float force) {
        if (plugin.getCombatBalance() != null) return true; // Global v2 snapshot owns all ranged weapons.
        String id = items.productId(item); var gear = catalog.gear().get(id);
        if (gear == null || gear.kind() != CraftingCatalog.Kind.BOW && gear.kind() != CraftingCatalog.Kind.SPEAR) return true;
        var stats = plugin.getPlayerStatsManager().getStats(player);
        if (stats.getLevel() < gear.tier().level()) { player.sendMessage("§c需要等級 " + gear.tier().level() + " 才能使用此武器。"); return false; }
        var data = plugin.getWeaponManager().getWeaponData(id); if (data == null) return false;
        double damage = (data.getDoubleExtra("base-damage", 1) + data.getCategory().calculateStatBonus(stats))
                * data.getDamageMultiplier() * data.getRarity().statMultiplier * Math.clamp(force, 0, 1);
        if (random.getAsDouble() < data.getDoubleExtra("crit-chance", 0) / 100.0) damage *= data.getDoubleExtra("crit-damage-multiplier", 1.5);
        projectile.getPersistentDataContainer().set(DAMAGE, PersistentDataType.DOUBLE, Math.clamp(damage, .1, 300)); return true;
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && event.getProjectile() instanceof Projectile shot
                && !prepare(player, shot, event.getBow(), event.getForce())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident trident && trident.getShooter() instanceof Player player
                && !prepare(player, trident, trident.getItemStack(), 1)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (plugin.getCombatBalance() != null) return;
        if (!(event.getDamager() instanceof Projectile projectile) || !(projectile.getShooter() instanceof Player)) return;
        Double damage = projectile.getPersistentDataContainer().get(DAMAGE, PersistentDataType.DOUBLE);
        if (damage != null && Double.isFinite(damage) && damage > 0) event.setDamage(Math.min(300, damage));
    }
}

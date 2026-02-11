package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.MobManager;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * MobListener - Handles custom mob behavior events
 *
 * This listener implements special behaviors for custom mobs such as
 * projectile attacks, death mechanics, and combat modifications.
 *
 * Handles:
 * - Snow Zombie: Throws snowballs periodically
 * - Fire Skeleton: Shoots fire arrows
 * - Giant Slime: Splits into smaller slimes on death
 */
public class MobListener implements Listener {

    private final CustomRPG plugin;
    private final MobManager mobManager;
    private final Random random;

    /**
     * Constructor for MobListener
     * @param plugin Main plugin instance
     * @param mobManager MobManager instance
     */
    public MobListener(CustomRPG plugin, MobManager mobManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        this.random = new Random();
        startCustomMobBehaviors();
    }

    /**
     * Start periodic behaviors for custom mobs
     */
    private void startCustomMobBehaviors() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        String mobKey = mobManager.getCustomMobKey(entity);
                        if (mobKey == null) continue;

                        MobManager.MobData mobData = mobManager.getMobData(mobKey);
                        if (mobData == null) continue;

                        applyPeriodicBehavior(entity, mobData);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 60L);
    }

    /**
     * Apply periodic behaviors to custom mobs
     * @param entity The mob entity
     * @param mobData The mob data
     */
    private void applyPeriodicBehavior(Entity entity, MobManager.MobData mobData) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }

        LivingEntity mob = (LivingEntity) entity;

        if (mobData.getSpecialBehavior().equalsIgnoreCase("snowball_thrower")) {
            if (random.nextDouble() < 0.3) {
                throwSnowball(mob);
            }
        }
    }

    /**
     * Handle custom mob death events
     * @param event EntityDeathEvent
     */
    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        String mobKey = mobManager.getCustomMobKey(event.getEntity());
        if (mobKey == null) {
            return;
        }

        MobManager.MobData mobData = mobManager.getMobData(mobKey);
        if (mobData == null) {
            return;
        }

        applyDeathBehavior(event.getEntity(), mobData);
    }

    /**
     * Apply death behaviors to custom mobs
     * @param mob The dying mob
     * @param mobData The mob data
     */
    private void applyDeathBehavior(LivingEntity mob, MobManager.MobData mobData) {
        if (mobData.getSpecialBehavior().equalsIgnoreCase("split_on_death")) {
            splitSlime(mob);
        }
    }

    /**
     * Handle projectile launch events for custom mobs
     * @param event ProjectileLaunchEvent
     */
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof LivingEntity)) {
            return;
        }

        LivingEntity mob = (LivingEntity) shooter;
        String mobKey = mobManager.getCustomMobKey(mob);
        if (mobKey == null) {
            return;
        }

        MobManager.MobData mobData = mobManager.getMobData(mobKey);
        if (mobData == null) {
            return;
        }

        if (mobData.getSpecialBehavior().equalsIgnoreCase("fire_arrows")) {
            if (event.getEntity() instanceof Arrow) {
                Arrow arrow = (Arrow) event.getEntity();
                arrow.setFireTicks(1000);
            }
        }
    }

    /**
     * Throw a snowball from the mob
     * @param mob The mob entity
     */
    private void throwSnowball(LivingEntity mob) {
        Player nearestPlayer = getNearestPlayer(mob, 15.0);
        if (nearestPlayer == null) {
            return;
        }

        Location mobLocation = mob.getEyeLocation();
        Location playerLocation = nearestPlayer.getEyeLocation();

        Vector direction = playerLocation.toVector().subtract(mobLocation.toVector()).normalize();

        Snowball snowball = (Snowball) mob.getWorld().spawnEntity(mobLocation, EntityType.SNOWBALL);
        snowball.setVelocity(direction.multiply(1.5));
        snowball.setShooter(mob);
    }

    /**
     * Split a slime into smaller slimes
     * @param slime The slime entity
     */
    private void splitSlime(LivingEntity slime) {
        Location location = slime.getLocation();

        for (int i = 0; i < 4; i++) {
            Slime smallSlime = (Slime) location.getWorld().spawnEntity(location, EntityType.SLIME);
            smallSlime.setSize(1);

            Vector velocity = new Vector(
                random.nextDouble() - 0.5,
                0.5,
                random.nextDouble() - 0.5
            ).normalize().multiply(0.5);
            smallSlime.setVelocity(velocity);
        }
    }

    /**
     * Get the nearest player to a mob within range
     * @param mob The mob entity
     * @param range The search range
     * @return The nearest player or null
     */
    private Player getNearestPlayer(LivingEntity mob, double range) {
        Player nearest = null;
        double nearestDistance = range;

        for (Entity entity : mob.getNearbyEntities(range, range, range)) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                double distance = mob.getLocation().distance(player.getLocation());
                if (distance < nearestDistance) {
                    nearest = player;
                    nearestDistance = distance;
                }
            }
        }

        return nearest;
    }
}

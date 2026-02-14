package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.MobManager;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.managers.WeaponManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
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
    private final PlayerStatsManager statsManager;
    private final WeaponManager weaponManager;
    private final Random random;

    /**
     * Constructor for MobListener
     * @param plugin Main plugin instance
     * @param mobManager MobManager instance
     */
    public MobListener(CustomRPG plugin, MobManager mobManager, PlayerStatsManager statsManager, WeaponManager weaponManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        this.statsManager = statsManager;
        this.weaponManager = weaponManager;
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
        // 排除玩家死亡
        if (event.getEntity() instanceof Player) {
            return;
        }

        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        String mobKey = mobManager.getCustomMobKey(event.getEntity());

        // 如果是自製怪物
        if (mobKey != null) {
            MobManager.MobData mobData = mobManager.getMobData(mobKey);
            if (mobData == null) {
                return;
            }

            int mobLevel = mobManager.getMobLevel(event.getEntity());

            // Handle custom mob drops or effects
            // For example, splitting slimes
            if (mobData.getSpecialBehavior().equalsIgnoreCase("split_on_death")) {
                splitSlime(event.getEntity(), mobData);
            }

            // 清除預設掉落物（如果有自定義掉落物）
            if (!mobData.getVanillaDrops().isEmpty() || !mobData.getWeaponDrops().isEmpty()) {
                event.getDrops().clear();

                // 掉落原版物品
                for (MobManager.DropItem dropItem : mobData.getVanillaDrops()) {
                    if (random.nextDouble() < dropItem.getChance()) {
                        int amount = dropItem.getMinAmount();
                        if (dropItem.getMaxAmount() > dropItem.getMinAmount()) {
                            amount = random.nextInt(dropItem.getMaxAmount() - dropItem.getMinAmount() + 1) + dropItem.getMinAmount();
                        }
                        event.getDrops().add(new ItemStack(dropItem.getMaterial(), amount));
                    }
                }

                // 掉落自定義武器
                for (MobManager.WeaponDrop weaponDrop : mobData.getWeaponDrops()) {
                    if (random.nextDouble() < weaponDrop.getChance()) {
                        ItemStack weapon = weaponManager.createWeapon(weaponDrop.getWeaponKey());
                        if (weapon != null) {
                            event.getDrops().add(weapon);
                            killer.sendMessage(ChatColor.GOLD + "★ 獲得稀有物品: " + weapon.getItemMeta().getDisplayName());
                        }
                    }
                }
            }

            // 給予經驗值（基於等級）
            int expReward = mobData.calculateExp(mobLevel);
            if (expReward > 0) {
                statsManager.addExp(killer, expReward);
                killer.sendMessage(ChatColor.YELLOW + "擊敗 [Lv." + mobLevel + "] " +
                    ChatColor.stripColor(mobData.getName()) + " 獲得 " + expReward + " 經驗值");
            }
        } else {
            // 如果是普通怪物，給予 2 點經驗值
            statsManager.addExp(killer, 2);
            killer.sendMessage(ChatColor.YELLOW + "獲得 2 經驗值");
        }
    }

    /**
     * Handle custom mob damage events (level-scaled damage)
     * 處理玩家攻擊 BlockDisplay 偽裝系統的情況
     * @param event EntityDamageByEntityEvent
     */
    @EventHandler
    public void onMobAttack(EntityDamageByEntityEvent event) {
        // === 情況 1：玩家攻擊 BlockDisplay 或名稱標籤 ArmorStand ===
        // BlockDisplay 和 ArmorStand 不是 LivingEntity，需要找到對應的隱形核心
        if (event.getDamager() instanceof Player player) {
            Entity target = event.getEntity();

            // 檢查是否攻擊到 BlockDisplay
            if (target instanceof org.bukkit.entity.BlockDisplay) {
                event.setCancelled(true); // 取消對 BlockDisplay 的傷害

                // 從 BlockDisplay 的 PersistentData 獲取核心 UUID
                String coreUuidString = target.getPersistentDataContainer().get(
                    new org.bukkit.NamespacedKey(plugin, "disguise_blockdisplay"),
                    org.bukkit.persistence.PersistentDataType.STRING
                );

                if (coreUuidString != null) {
                    java.util.UUID coreUuid = java.util.UUID.fromString(coreUuidString);
                    // 在同一世界中尋找核心實體
                    for (Entity entity : target.getWorld().getEntities()) {
                        if (entity.getUniqueId().equals(coreUuid) && entity instanceof LivingEntity core) {
                            // 找到核心，對其造成傷害

                            // 計算傷害（考慮玩家的武器）
                            double damage = 1.0;
                            ItemStack weapon = player.getInventory().getItemInMainHand();
                            if (weapon != null && !weapon.getType().isAir()) {
                                // 這裡可以加入自定義武器傷害計算
                                damage = weaponManager.getBaseDamage(weapon);
                            }

                            core.damage(damage, player);
                            plugin.getLogger().fine("玩家攻擊 BlockDisplay，轉移 " + damage + " 傷害到核心");
                            return;
                        }
                    }
                }
            }

            // 檢查是否攻擊到名稱標籤 ArmorStand
            if (target instanceof ArmorStand armorStand) {
                String coreUuidString = armorStand.getPersistentDataContainer().get(
                    new org.bukkit.NamespacedKey(plugin, "disguise_nametag"),
                    org.bukkit.persistence.PersistentDataType.STRING
                );

                if (coreUuidString != null) {
                    event.setCancelled(true); // 取消對 ArmorStand 的傷害

                    java.util.UUID coreUuid = java.util.UUID.fromString(coreUuidString);
                    // 在同一世界中尋找核心實體
                    for (Entity entity : target.getWorld().getEntities()) {
                        if (entity.getUniqueId().equals(coreUuid) && entity instanceof LivingEntity core) {
                            // 找到核心，對其造成傷害

                            // 計算傷害
                            double damage = 1.0;
                            ItemStack weapon = player.getInventory().getItemInMainHand();
                            if (weapon != null && !weapon.getType().isAir()) {
                                damage = weaponManager.getBaseDamage(weapon);
                            }

                            core.damage(damage, player);
                            plugin.getLogger().fine("玩家攻擊名稱標籤，轉移 " + damage + " 傷害到核心");
                            return;
                        }
                    }
                }
            }
        }

        // === 情況 2：自定義生物攻擊玩家（等級化傷害）===
        // 檢查是否為自定義生物攻擊玩家
        if (!(event.getDamager() instanceof LivingEntity)) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) event.getDamager();
        String mobKey = mobManager.getCustomMobKey(attacker);

        if (mobKey == null) {
            return;
        }

        MobManager.MobData mobData = mobManager.getMobData(mobKey);
        if (mobData == null) {
            return;
        }

        // 獲取生物等級並計算傷害
        int mobLevel = mobManager.getMobLevel(attacker);
        double scaledDamage = mobData.calculateDamage(mobLevel);

        // 設置等級化傷害（對於偽裝生物，確保傷害大於0）
        if (scaledDamage < 1.0) {
            scaledDamage = 1.0; // 最低傷害為 1
        }
        event.setDamage(scaledDamage);
        
        // Debug 訊息
        plugin.getLogger().info("Custom mob " + mobData.getName() + " (Lv." + mobLevel + ") attacked player with " + scaledDamage + " damage");
    }

    /**
     * Split a giant slime into smaller ones
     * @param entity The dying entity
     * @param mobData The mob data
     */
    private void splitSlime(LivingEntity entity, MobManager.MobData mobData) {
        Location location = entity.getLocation();

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

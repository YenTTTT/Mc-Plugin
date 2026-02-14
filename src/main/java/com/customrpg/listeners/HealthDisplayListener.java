package com.customrpg.listeners;

import com.customrpg.managers.HealthDisplayManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * HealthDisplayListener - 監聽血量變化事件並即時更新顯示
 */
public class HealthDisplayListener implements Listener {

    private final HealthDisplayManager healthDisplayManager;

    /**
     * Constructor for HealthDisplayListener
     * @param healthDisplayManager HealthDisplayManager instance
     */
    public HealthDisplayListener(HealthDisplayManager healthDisplayManager) {
        this.healthDisplayManager = healthDisplayManager;
    }

    /**
     * 玩家加入伺服器時，立即顯示血量
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        healthDisplayManager.updatePlayerHealthImmediately(player);
    }

    /**
     * 實體受到傷害時，立即更新血量顯示
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            // 延遲 1 tick 更新，確保血量已經改變
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("CustomRPG"),
                () -> healthDisplayManager.updatePlayerHealthImmediately(player),
                1L
            );
        } else if (event.getEntity() instanceof LivingEntity) {
            LivingEntity mob = (LivingEntity) event.getEntity();
            // 延遲 1 tick 更新怪物血量
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("CustomRPG"),
                () -> healthDisplayManager.updateMobHealthImmediately(mob),
                1L
            );
        }
    }

    /**
     * 實體恢復血量時，立即更新血量顯示
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            // 延遲 1 tick 更新
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("CustomRPG"),
                () -> healthDisplayManager.updatePlayerHealthImmediately(player),
                1L
            );
        } else if (event.getEntity() instanceof LivingEntity) {
            LivingEntity mob = (LivingEntity) event.getEntity();
            // 延遲 1 tick 更新怪物血量
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("CustomRPG"),
                () -> healthDisplayManager.updateMobHealthImmediately(mob),
                1L
            );
        }
    }
}


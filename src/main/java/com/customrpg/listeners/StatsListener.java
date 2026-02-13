package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.players.PlayerStats;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * StatsListener - 處理玩家數據相關事件
 *
 * 功能：
 * - PlayerJoinEvent: 載入玩家數據並應用 Vitality 到最大血量
 * - PlayerQuitEvent: 儲存玩家數據
 * - EntityDamageByEntityEvent:
 *   - 防禦：受害者為玩家時，根據 Defense 減免傷害
 *   - 弓箭：攻擊者為玩家弓箭時，增加 Agility 傷害加成
 */
public class StatsListener implements Listener {

    private final CustomRPG plugin;
    private final PlayerStatsManager statsManager;

    // 配置：每點 Defense 減少多少 % 傷害 (預設 0.5%)
    private static final double DEFENSE_REDUCTION_PER_POINT = 0.005;

    // 配置：每點 Agility 增加多少弓箭傷害 (預設 0.1)
    private static final double BOW_DAMAGE_PER_AGILITY = 0.1;

    public StatsListener(CustomRPG plugin, PlayerStatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }

    /**
     * 玩家加入伺服器時載入數據
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 載入玩家數據 (會自動快取)
        PlayerStats stats = statsManager.getStats(player);

        // 應用 Vitality 到最大血量
        statsManager.updateMaxHealth(player);

        plugin.getLogger().info("已載入玩家數據: " + player.getName() + " -> " + stats.toString());
    }

    /**
     * 玩家離開伺服器時儲存數據
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 儲存玩家數據
        statsManager.saveStats(player);

        // 清除快取 (可選)
        statsManager.unloadStats(player.getUniqueId());

        plugin.getLogger().info("已儲存玩家數據: " + player.getName());
    }

    /**
     * 處理傷害事件 - 防禦減免
     *
     * Priority = LOW: 在 WeaponListener (HIGHEST) 之後，所以先計算武器傷害，再套用防禦
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamageDefense(EntityDamageByEntityEvent event) {
        // 只處理受害者為玩家的情況
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        PlayerStats victimStats = statsManager.getStats(victim);
        int defense = victimStats.getDefense();

        if (defense <= 0) {
            return;
        }

        // 計算減免百分比 (例如：10 Defense = 5% 減免)
        double reductionPercent = defense * DEFENSE_REDUCTION_PER_POINT;

        // 上限 75% 減免
        reductionPercent = Math.min(0.75, reductionPercent);

        // 計算減免後的傷害
        double originalDamage = event.getDamage();
        double reducedDamage = originalDamage * (1.0 - reductionPercent);

        event.setDamage(Math.max(0.5, reducedDamage));
    }

    /**
     * 處理傷害事件 - 弓箭 Agility 加成
     *
     * Priority = HIGHEST: 在 WeaponListener 之前，優先處理弓箭傷害
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageBow(EntityDamageByEntityEvent event) {
        // 只處理弓箭攻擊
        if (!(event.getDamager() instanceof Arrow arrow)) {
            return;
        }

        // 檢查射手是否為玩家
        if (!(arrow.getShooter() instanceof Player shooter)) {
            return;
        }

        PlayerStats shooterStats = statsManager.getStats(shooter);
        int agility = shooterStats.getAgility();

        if (agility <= 0) {
            return;
        }

        // 計算 Agility 加成傷害
        double bonusDamage = agility * BOW_DAMAGE_PER_AGILITY;

        // 增加傷害
        event.setDamage(event.getDamage() + bonusDamage);
    }
}


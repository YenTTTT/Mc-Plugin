package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.players.PlayerStats;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * StatsListener - 處理玩家數據相關事件
 *
 * 功能：
 * - PlayerJoinEvent: 載入玩家數據並應用 Vitality 到最大血量
 * - PlayerQuitEvent: 儲存玩家數據
 * - 傷害計算由 BalanceCombatListener 統一處理
 */
public class StatsListener implements Listener {

    private final CustomRPG plugin;
    private final PlayerStatsManager statsManager;

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
        if (!statsManager.isDataAvailable(player)) {
            player.kickPlayer("角色資料讀取或數值遷移失敗，請通知管理員；原檔已保留。"); return;
        }

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

    // Combat mitigation and ranged scaling are owned by BalanceCombatListener.
}

package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.races.RaceManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * RaceListener - 種族系統事件監聽器
 *
 * 處理：
 * - 玩家加入 → 載入種族資料 & 套用種族屬性
 * - 玩家離開 → 儲存種族資料
 */
public class RaceListener implements Listener {

    private final CustomRPG plugin;
    private final RaceManager raceManager;

    public RaceListener(CustomRPG plugin, RaceManager raceManager) {
        this.plugin = plugin;
        this.raceManager = raceManager;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 載入種族資料
        raceManager.loadPlayerRace(player.getUniqueId());

        // 如果有種族，套用屬性加成
        if (raceManager.hasRace(player)) {
            // 延遲 1 tick 確保 PlayerStats 已載入
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                raceManager.applyRaceStats(player);
            }, 5L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        raceManager.unloadPlayer(event.getPlayer().getUniqueId());
    }
}


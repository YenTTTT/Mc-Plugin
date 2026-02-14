package com.customrpg.listeners;

import com.customrpg.managers.TalentManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * TalentListener - 天賦系統事件監聽器
 *
 * 處理玩家登入時自動應用天賦效果
 */
public class TalentListener implements Listener {

    private final TalentManager talentManager;

    public TalentListener(TalentManager talentManager) {
        this.talentManager = talentManager;
    }

    /**
     * 玩家加入時自動應用天賦效果
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 延遲應用天賦效果，確保玩家完全載入
        talentManager.getPlugin().getServer().getScheduler().runTaskLater(
            talentManager.getPlugin(),
            () -> talentManager.applyTalentEffects(player),
            40L // 2秒後執行
        );
    }
}

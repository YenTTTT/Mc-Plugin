package com.customrpg.managers;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SkillBarManager - 快捷技能列管理器
 *
 * 追蹤哪些玩家已啟用快捷鍵施放功能。
 * 啟用後，按鍵盤 1~4 對應施放技能槽 1~4 的已攜帶技能。
 */
public class SkillBarManager {

    // 已啟用快捷鍵施放功能的玩家 UUID 集合
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

    /**
     * 檢查玩家是否已啟用快捷技能列
     *
     * @param player 玩家
     * @return 是否啟用
     */
    public boolean isEnabled(Player player) {
        return enabledPlayers.contains(player.getUniqueId());
    }

    /**
     * 切換玩家的快捷技能列開關
     *
     * @param player 玩家
     * @return 切換後的狀態 (true = 開啟, false = 關閉)
     */
    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (enabledPlayers.contains(id)) {
            enabledPlayers.remove(id);
            return false;
        } else {
            enabledPlayers.add(id);
            return true;
        }
    }

    /**
     * 強制關閉玩家的快捷技能列
     *
     * @param player 玩家
     */
    public void disable(Player player) {
        enabledPlayers.remove(player.getUniqueId());
    }

    /**
     * 清除所有記錄（插件關閉時呼叫）
     */
    public void cleanup() {
        enabledPlayers.clear();
    }
}


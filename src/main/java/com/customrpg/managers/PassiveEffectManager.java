package com.customrpg.managers;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PassiveEffectManager
 *
 * 管理武器的被動效果
 *
 * 目前支援的效果：
 * - kill_crit_boost: 擊殺生物後獲得暫時暴擊率加成
 *
 * 擴展新效果的步驟：
 * 1. 在此類中添加新的狀態追蹤（類似 CritBoostState）
 * 2. 添加 apply 方法（類似 applyKillCritBoost）
 * 3. 添加 get 方法來獲取當前效果值
 * 4. 在 WeaponListener 中根據觸發條件調用對應的 apply 方法
 * 5. 在戰鬥計算中使用 get 方法來應用效果
 */
public class PassiveEffectManager {

    private static class CritBoostState {
        private double bonusCritChancePercent;
        private long expireAtMillis;

        private CritBoostState(double bonusCritChancePercent, long expireAtMillis) {
            this.bonusCritChancePercent = bonusCritChancePercent;
            this.expireAtMillis = expireAtMillis;
        }
    }

    private final Map<UUID, CritBoostState> critBoosts = new ConcurrentHashMap<>();

    /**
     * 套用暴擊率加成
     * @param player 玩家
     * @param bonusCritChancePercent 增加的暴擊率（百分比，例：50.0 代表 +50%）
     * @param durationTicks 持續時間（ticks），20 ticks = 1 秒 ， 1 tick = 0.05 秒
     */
    public void applyKillCritBoost(Player player, double bonusCritChancePercent, int durationTicks) {
        if (player == null) {
            return;
        }
        if (bonusCritChancePercent <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long durationMillis = Math.max(0L, durationTicks) * 50L;
        long expireAt = now + durationMillis;

        // 規則（測試版）：同效果直接刷新時間，取較大加成
        critBoosts.compute(player.getUniqueId(), (uuid, state) -> {
            if (state == null || state.expireAtMillis <= now) {
                return new CritBoostState(bonusCritChancePercent, expireAt);
            }

            state.bonusCritChancePercent = Math.max(state.bonusCritChancePercent, bonusCritChancePercent);
            state.expireAtMillis = Math.max(state.expireAtMillis, expireAt);
            return state;
        });
    }

    /**
     * 取得目前有效的暴擊率加成（百分比）
     */
    public double getBonusCritChancePercent(Player player) {
        if (player == null) {
            return 0.0;
        }

        CritBoostState state = critBoosts.get(player.getUniqueId());
        if (state == null) {
            return 0.0;
        }

        long now = System.currentTimeMillis();
        if (state.expireAtMillis <= now) {
            critBoosts.remove(player.getUniqueId());
            return 0.0;
        }

        return Math.max(0.0, state.bonusCritChancePercent);
    }

    /**
     * 清理玩家資料（可選）
     */
    public void clear(Player player) {
        if (player == null) {
            return;
        }
        critBoosts.remove(player.getUniqueId());
    }

    private static String cooldownKey(String passiveKey) {
        return passiveKey == null ? "" : passiveKey.trim().toLowerCase();
    }

    private final Map<UUID, Map<String, Long>> passiveCooldowns = new ConcurrentHashMap<>();

    /**
     * 檢查某個被動是否在冷卻中
     */
    public boolean isOnCooldown(Player player, String passiveKey) {
        if (player == null) {
            return false;
        }
        String k = cooldownKey(passiveKey);
        if (k.isEmpty()) {
            return false;
        }

        Map<String, Long> cdMap = passiveCooldowns.get(player.getUniqueId());
        if (cdMap == null) {
            return false;
        }

        Long until = cdMap.get(k);
        if (until == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (until <= now) {
            cdMap.remove(k);
            return false;
        }

        return true;
    }

    /**
     * 啟動冷卻
     * @param cooldownTicks 冷卻 ticks（20 ticks = 1 秒）
     */
    public void startCooldown(Player player, String passiveKey, int cooldownTicks) {
        if (player == null) {
            return;
        }
        String k = cooldownKey(passiveKey);
        if (k.isEmpty()) {
            return;
        }
        if (cooldownTicks <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long durationMillis = (long) cooldownTicks * 50L;
        long until = now + durationMillis;

        passiveCooldowns.compute(player.getUniqueId(), (uuid, cdMap) -> {
            if (cdMap == null) {
                cdMap = new ConcurrentHashMap<>();
            }
            cdMap.put(k, until);
            return cdMap;
        });
    }

    /**
     * 取得剩餘冷卻時間（ticks）。若未在冷卻中回傳 0。
     */
    public int getRemainingCooldownTicks(Player player, String passiveKey) {
        if (player == null) {
            return 0;
        }
        String k = cooldownKey(passiveKey);
        if (k.isEmpty()) {
            return 0;
        }

        Map<String, Long> cdMap = passiveCooldowns.get(player.getUniqueId());
        if (cdMap == null) {
            return 0;
        }

        Long until = cdMap.get(k);
        if (until == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        if (until <= now) {
            cdMap.remove(k);
            return 0;
        }

        long remainingMillis = until - now;
        // 1 tick = 50ms，向上取整比較符合玩家體感
        return (int) Math.max(1L, (remainingMillis + 49L) / 50L);
    }
}

package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * BloodManager - 赤血操術系的血量資源管理器
 *
 * 直接使用玩家的實際生命值 (HP) 作為施法資源
 * 赤血操術系技能消耗生命值來施放
 */
public class BloodManager {

    private final CustomRPG plugin;

    public BloodManager(CustomRPG plugin) {
        this.plugin = plugin;
    }

    /**
     * 獲取玩家當前生命值
     */
    public double getBlood(Player player) {
        return player.getHealth();
    }

    /**
     * 獲取玩家最大生命值
     */
    public double getMaxBlood(Player player) {
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        return maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
    }

    /**
     * 檢查玩家是否有足夠生命值（施法後至少保留1點血）
     */
    public boolean hasBlood(Player player, double amount) {
        // 確保施法後至少保留1點生命值
        return player.getHealth() > amount + 1.0;
    }

    /**
     * 消耗生命值
     * @return 是否成功消耗
     */
    public boolean consumeBlood(Player player, double amount) {
        if (!hasBlood(player, amount)) {
            return false;
        }

        double newHealth = player.getHealth() - amount;
        // 確保不會死亡，至少保留0.5點血
        if (newHealth < 0.5) {
            newHealth = 0.5;
        }
        player.setHealth(newHealth);

        // 顯示消耗訊息
        player.sendMessage("§c[-" + (int)amount + " HP] §7消耗生命值施放技能");

        return true;
    }

    /**
     * 增加生命值（治療）
     */
    public void addBlood(Player player, double amount) {
        double maxHealth = getMaxBlood(player);
        double newHealth = Math.min(maxHealth, player.getHealth() + amount);
        player.setHealth(newHealth);
    }

    /**
     * 擊殺敵人回復生命值
     */
    public void onKill(Player player, double baseRecovery) {
        addBlood(player, baseRecovery);
        player.sendMessage("§c[血回收] §f回復 §c" + (int)baseRecovery + " §fHP");
    }

    /**
     * 獲取血量百分比 (用於傷害加成計算)
     */
    public double getBloodPercentage(Player player) {
        return getBlood(player) / getMaxBlood(player);
    }

    /**
     * 顯示血量條 (不再需要，因為使用原生生命值)
     */
    public void showBloodBar(Player player) {
        // 不再需要，玩家可以看到自己的生命值
    }

    /**
     * 隱藏血量條 (不再需要)
     */
    public void hideBloodBar(Player player) {
        // 不再需要
    }

    /**
     * 臨時顯示血量條 (不再需要)
     */
    public void showBloodBarTemporarily(Player player, int seconds) {
        // 不再需要
    }

    /**
     * 清除玩家數據 (不再需要)
     */
    public void clearPlayer(Player player) {
        // 不再需要
    }
}

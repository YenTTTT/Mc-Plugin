package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * DamageDisplayManager - 管理傷害訊息的顯示
 *
 * 功能：
 * - 在玩家攻擊時顯示造成的傷害訊息在聊天室
 * - 顯示怪物剩餘血量
 * - 支援暴擊顯示
 */
public class DamageDisplayManager {

    private final CustomRPG plugin;

    /**
     * Constructor for DamageDisplayManager
     * @param plugin Main plugin instance
     */
    public DamageDisplayManager(CustomRPG plugin) {
        this.plugin = plugin;
    }

    /**
     * 顯示傷害訊息給玩家
     * @param player 玩家
     * @param target 被攻擊的目標
     * @param damage 傷害值
     * @param isCritical 是否為暴擊
     */
    public void showDamage(Player player, LivingEntity target, double damage, boolean isCritical) {
        // 獲取怪物名稱
        String mobName = target.getCustomName() != null ? target.getCustomName() : target.getName();

        // 獲取怪物剩餘血量和最大血量
        double currentHealth = target.getHealth();
        double maxHealth = target.getMaxHealth();

        // 構建訊息
        String message = formatDamageMessage(player.getName(), mobName, damage, isCritical, currentHealth, maxHealth);

        // 只發送給玩家自己
        player.sendMessage(message);
    }


    /**
     * 格式化傷害訊息
     * @param playerName 玩家名稱
     * @param mobName 怪物名稱
     * @param damage 傷害值
     * @param isCritical 是否為暴擊
     * @param currentHealth 怪物當前血量
     * @param maxHealth 怪物最大血量
     * @return 格式化後的訊息
     */
    private String formatDamageMessage(String playerName, String mobName, double damage,
                                      boolean isCritical, double currentHealth, double maxHealth) {
        String critText = isCritical ? ChatColor.RED + " (暴擊)" : "";

        return String.format(
            "%s%s %s對 %s %s造成了 %s%.1f%s 傷害%s %s剩餘血量 %s%.1f%s/%s%.0f HP",
            ChatColor.YELLOW, playerName,
            ChatColor.GRAY, ChatColor.stripColor(mobName),
            ChatColor.GRAY, ChatColor.RED, damage, ChatColor.GRAY,
            critText,
            ChatColor.stripColor(mobName),
            ChatColor.GREEN, currentHealth,
            ChatColor.GRAY, ChatColor.GREEN, maxHealth
        );
    }

    /**
     * 清理方法（保留以維持兼容性）
     */
    public void shutdown() {
        // 聊天室訊息不需要清理
    }
}



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
        showDamage(player, target, damage, isCritical, TalentPassiveEffectManager.AttackBonuses.NONE);
    }

    public void showDamage(Player player, LivingEntity target, double damage, boolean isCritical,
                           TalentPassiveEffectManager.AttackBonuses bonuses) {
        // 獲取怪物名稱
        String mobName = target.getCustomName() != null ? target.getCustomName() : target.getName();

        // 獲取怪物剩餘血量和最大血量
        double currentHealth = MobManager.getVirtualCurrentHP(target);
        double maxHealth = MobManager.getVirtualMaxHP(target);

        // 構建訊息
        String message = formatDamageMessage(player.getName(), mobName, damage, isCritical, currentHealth, maxHealth, bonuses);

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
    static String formatDamageMessage(String playerName, String mobName, double damage,
                                      boolean isCritical, double currentHealth, double maxHealth,
                                      TalentPassiveEffectManager.AttackBonuses bonuses) {
        String critText = isCritical ? ChatColor.RED + " (暴擊)" : "";

        String bonusText = "";
        if (bonuses.backstab() > 0) bonusText += String.format(java.util.Locale.ROOT, " §8+ 背刺§7(+%.0f%%)", bonuses.backstab() * 100);
        if (bonuses.execute() > 0) bonusText += String.format(java.util.Locale.ROOT, " §4+ 收割§7(+%.0f%%)", bonuses.execute() * 100);
        if (!bonusText.isEmpty()) bonusText += "§7（加成已計入）";

        return String.format(java.util.Locale.ROOT,
            "%s%s %s對 %s %s造成了 %s%.1f%s 傷害%s%s %s剩餘血量 %s%.1f%s/%s%.0f HP",
            ChatColor.YELLOW, playerName,
            ChatColor.GRAY, ChatColor.stripColor(mobName),
            ChatColor.GRAY, ChatColor.RED, damage, ChatColor.GRAY,
            critText, bonusText,
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



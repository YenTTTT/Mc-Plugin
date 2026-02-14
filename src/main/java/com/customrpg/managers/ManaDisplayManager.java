package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * ManaDisplayManager - 管理玩家的魔力顯示
 *
 * 功能：
 * - 魔力顯示在 ActionBar 上（血量的右邊）
 * - 與 HealthDisplayManager 協調顯示
 *
 * 注意：當顯示傷害數字時，會暫時不更新玩家血量和魔力顯示
 */
public class ManaDisplayManager {

    private final CustomRPG plugin;
    private final PlayerStatsManager statsManager;
    private BukkitRunnable playerManaTask;
    private DamageDisplayManager damageDisplayManager;
    private HealthDisplayManager healthDisplayManager;

    /**
     * Constructor for ManaDisplayManager
     * @param plugin Main plugin instance
     * @param statsManager PlayerStatsManager instance
     */
    public ManaDisplayManager(CustomRPG plugin, PlayerStatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        startManaDisplayTask();
    }

    /**
     * 設置 DamageDisplayManager（用於協調顯示）
     * @param damageDisplayManager DamageDisplayManager instance
     */
    public void setDamageDisplayManager(DamageDisplayManager damageDisplayManager) {
        this.damageDisplayManager = damageDisplayManager;
    }

    /**
     * 設置 HealthDisplayManager（用於協調顯示）
     * @param healthDisplayManager HealthDisplayManager instance
     */
    public void setHealthDisplayManager(HealthDisplayManager healthDisplayManager) {
        this.healthDisplayManager = healthDisplayManager;
    }

    /**
     * 啟動魔力顯示任務
     */
    private void startManaDisplayTask() {
        // 玩家魔力顯示任務（每 10 ticks = 0.5 秒更新一次）
        playerManaTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    updatePlayerManaDisplay(player);
                }
            }
        };
        playerManaTask.runTaskTimer(plugin, 0L, 10L);
    }

    /**
     * 更新玩家魔力顯示（ActionBar，與血量一起顯示）
     * @param player 玩家
     */
    private void updatePlayerManaDisplay(Player player) {

        PlayerStats stats = statsManager.getStats(player);
        if (stats == null) {
            return;
        }

        // 組合血量和魔力顯示
        double health = player.getHealth();
        double maxHealth = player.getMaxHealth();
        double mana = stats.getCurrentMana();
        double maxMana = stats.getMaxMana();

        // 血量顯示
        String healthColor = getHealthColor(health / maxHealth);
        String healthText = String.format("%s❤ %.1f§7/§c%.1f", healthColor, health, maxHealth);

        // 魔力顯示（在血量右邊）
        String manaColor = getManaColor(mana / maxMana);
        String manaText = String.format("%s✦ %.1f§7/§b%.0f", manaColor, mana, maxMana);

        // 合併顯示（血量 + 空格 + 魔力）
        String combinedText = healthText + "    " + manaText;

        // 使用 ActionBar 顯示
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(combinedText));
    }

    /**
     * 根據血量百分比獲取顏色
     * @param percentage 血量百分比 (0.0 - 1.0)
     * @return 顏色代碼
     */
    private String getHealthColor(double percentage) {
        if (percentage > 0.75) {
            return "§a"; // 綠色（>75%）
        } else if (percentage > 0.5) {
            return "§e"; // 黃色（>50%）
        } else if (percentage > 0.25) {
            return "§6"; // 橙色（>25%）
        } else {
            return "§c"; // 紅色（≤25%）
        }
    }

    /**
     * 根據魔力百分比獲取顏色
     * @param percentage 魔力百分比 (0.0 - 1.0)
     * @return 顏色代碼
     */
    private String getManaColor(double percentage) {
        if (percentage > 0.75) {
            return "§b"; // 青色（>75%）
        } else if (percentage > 0.5) {
            return "§3"; // 深青色（>50%）
        } else if (percentage > 0.25) {
            return "§9"; // 藍色（>25%）
        } else {
            return "§1"; // 深藍色（≤25%）
        }
    }

    /**
     * 立即更新特定玩家的魔力顯示
     * @param player 玩家
     */
    public void updatePlayerManaImmediately(Player player) {
        updatePlayerManaDisplay(player);
    }

    /**
     * 停止所有魔力顯示任務
     */
    public void shutdown() {
        if (playerManaTask != null) {
            playerManaTask.cancel();
        }
    }
}


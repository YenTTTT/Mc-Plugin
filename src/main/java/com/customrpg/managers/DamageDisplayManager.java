package com.customrpg.managers;

import com.customrpg.CustomRPG;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DamageDisplayManager - 管理傷害數字的顯示
 *
 * 功能：
 * - 在玩家攻擊時顯示造成的傷害數字
 * - 使用 ActionBar 顯示（短暫顯示後消失）
 * - 支援暴擊顯示（紅色 + 特殊符號）
 */
public class DamageDisplayManager {

    private final CustomRPG plugin;
    private final Map<UUID, DamageDisplay> activeDamageDisplays;

    /**
     * Constructor for DamageDisplayManager
     * @param plugin Main plugin instance
     */
    public DamageDisplayManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.activeDamageDisplays = new HashMap<>();
    }

    /**
     * 顯示傷害數字給玩家
     * @param player 玩家
     * @param damage 傷害值
     * @param isCritical 是否為暴擊
     */
    public void showDamage(Player player, double damage, boolean isCritical) {
        UUID playerId = player.getUniqueId();

        // 取消現有的顯示任務
        DamageDisplay existingDisplay = activeDamageDisplays.get(playerId);
        if (existingDisplay != null) {
            existingDisplay.cancel();
        }

        // 生成傷害文字
        String damageText = formatDamage(damage, isCritical);

        // 創建新的顯示任務
        DamageDisplay newDisplay = new DamageDisplay(player, damageText);
        activeDamageDisplays.put(playerId, newDisplay);
        newDisplay.start();
    }

    /**
     * 格式化傷害數字
     * @param damage 傷害值
     * @param isCritical 是否為暴擊
     * @return 格式化後的文字
     */
    private String formatDamage(double damage, boolean isCritical) {
        if (isCritical) {
            // 暴擊：紅色 + 星號
            return String.format("§c§l✦ %.1f ✦", damage);
        } else {
            // 普通：黃色
            return String.format("§e-%.1f", damage);
        }
    }

    /**
     * 檢查是否正在為某玩家顯示傷害
     * @param playerId 玩家 UUID
     * @return 是否正在顯示
     */
    public boolean isDisplaying(UUID playerId) {
        return activeDamageDisplays.containsKey(playerId);
    }

    /**
     * 清理特定玩家的傷害顯示
     * @param playerId 玩家 UUID
     */
    public void clearDisplay(UUID playerId) {
        DamageDisplay display = activeDamageDisplays.remove(playerId);
        if (display != null) {
            display.cancel();
        }
    }

    /**
     * 清理所有傷害顯示
     */
    public void shutdown() {
        for (DamageDisplay display : activeDamageDisplays.values()) {
            display.cancel();
        }
        activeDamageDisplays.clear();
    }

    /**
     * 內部類：傷害顯示任務
     */
    private class DamageDisplay {
        private final Player player;
        private final String text;
        private BukkitRunnable task;

        public DamageDisplay(Player player, String text) {
            this.player = player;
            this.text = text;
        }

        /**
         * 開始顯示傷害
         */
        public void start() {
            // 立即顯示傷害
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(text));

            // 1 秒後清除顯示（20 ticks）
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    // 清除 ActionBar（發送空白訊息）
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(""));
                    activeDamageDisplays.remove(player.getUniqueId());
                }
            };
            task.runTaskLater(plugin, 20L);
        }

        /**
         * 取消顯示任務
         */
        public void cancel() {
            if (task != null) {
                task.cancel();
            }
        }
    }
}



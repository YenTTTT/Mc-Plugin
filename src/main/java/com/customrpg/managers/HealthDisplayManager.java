package com.customrpg.managers;

import com.customrpg.CustomRPG;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * HealthDisplayManager - 管理玩家和怪物的血量顯示
 *
 * 功能：
 * - 玩家血量顯示在 ActionBar（生命值愛心上方）
 * - 怪物血量顯示在名稱旁邊
 *
 * 注意：當顯示傷害數字時，會暫時不更新玩家血量顯示
 */
public class HealthDisplayManager {

    private final CustomRPG plugin;
    private final MobManager mobManager;
    private BukkitRunnable playerHealthTask;
    private BukkitRunnable mobHealthTask;
    private DamageDisplayManager damageDisplayManager;

    /**
     * Constructor for HealthDisplayManager
     * @param plugin Main plugin instance
     * @param mobManager MobManager instance
     */
    public HealthDisplayManager(CustomRPG plugin, MobManager mobManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        startHealthDisplayTasks();
    }

    /**
     * 設置 DamageDisplayManager（用於協調顯示）
     * @param damageDisplayManager DamageDisplayManager instance
     */
    public void setDamageDisplayManager(DamageDisplayManager damageDisplayManager) {
        this.damageDisplayManager = damageDisplayManager;
    }

    /**
     * 啟動血量顯示任務
     */
    private void startHealthDisplayTasks() {
        // 玩家血量顯示任務（每 10 ticks = 0.5 秒更新一次）
        playerHealthTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    updatePlayerHealthDisplay(player);
                }
            }
        };
        playerHealthTask.runTaskTimer(plugin, 0L, 10L);

        // 怪物血量顯示任務（每 20 ticks = 1 秒更新一次）
        mobHealthTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                    for (org.bukkit.entity.Entity entity : world.getEntities()) {
                        if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                            updateMobHealthDisplay((LivingEntity) entity);
                        }
                    }
                }
            }
        };
        mobHealthTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * 更新玩家血量顯示（ActionBar）
     * @param player 玩家
     */
    private void updatePlayerHealthDisplay(Player player) {
        // 如果正在顯示傷害數字，則跳過血量更新
        if (damageDisplayManager != null && damageDisplayManager.isDisplaying(player.getUniqueId())) {
            return;
        }

        double health = player.getHealth();
        double maxHealth = player.getMaxHealth();

        // 只顯示數字，添加顏色
        String color = getHealthColor(health / maxHealth);
        String healthText = String.format("%s❤ %.1f§7/§c%.1f", color, health, maxHealth);

        // 使用 ActionBar 顯示（在愛心上方）
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(healthText));
    }

    /**
     * 更新怪物血量顯示（名稱）
     * @param mob 生物實體
     */
    private void updateMobHealthDisplay(LivingEntity mob) {
        // 檢查是否為自定義生物
        String mobKey = mobManager.getCustomMobKey(mob);
        if (mobKey == null) {
            return; // 不是自定義生物，不顯示血量
        }

        MobManager.MobData mobData = mobManager.getMobData(mobKey);
        if (mobData == null) {
            return;
        }

        double health = mob.getHealth();
        double maxHealth = mob.getMaxHealth();
        int level = mobManager.getMobLevel(mob);

        // 組合名稱：[Lv.X] 名稱 血量數字
        String displayName = mobData.getName();
        if (mobData.shouldShowLevelInName() && mobData.hasLevelSystem()) {
            displayName = "§8[§eLv." + level + "§8] " + mobData.getName();
        }

        // 只顯示數字，添加顏色
        String color = getHealthColor(health / maxHealth);
        String healthText = String.format("%s❤ %.0f§7/§c%.0f", color, health, maxHealth);
        String fullName = displayName + " " + healthText;

        mob.setCustomName(ChatColor.translateAlternateColorCodes('&', fullName));
        mob.setCustomNameVisible(true);
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
     * 立即更新特定怪物的血量顯示
     * @param mob 生物實體
     */
    public void updateMobHealthImmediately(LivingEntity mob) {
        updateMobHealthDisplay(mob);
    }

    /**
     * 立即更新特定玩家的血量顯示
     * @param player 玩家
     */
    public void updatePlayerHealthImmediately(Player player) {
        updatePlayerHealthDisplay(player);
    }

    /**
     * 停止所有血量顯示任務
     */
    public void shutdown() {
        if (playerHealthTask != null) {
            playerHealthTask.cancel();
        }
        if (mobHealthTask != null) {
            mobHealthTask.cancel();
        }
    }
}


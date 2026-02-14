package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * ManaManager - 管理玩家魔力系統
 *
 * 功能：
 * - 自動魔力回復機制
 * - 魔力顯示條 (經驗值條)
 * - 魔力消耗檢查
 * - 裝備影響魔力回復速度
 */
public class ManaManager {

    private final CustomRPG plugin;
    private final PlayerStatsManager statsManager;
    private BukkitTask regenTask;

    // 配置：基礎魔力回復間隔 (ticks)
    private static final int REGEN_INTERVAL = 20; // 1秒 = 20 ticks

    /**
     * Constructor for ManaManager
     * @param plugin Main plugin instance
     * @param statsManager PlayerStatsManager instance
     */
    public ManaManager(CustomRPG plugin, PlayerStatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        startRegenTask();
    }

    /**
     * 啟動魔力回復任務
     */
    private void startRegenTask() {
        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                regenerateMana(player);
                // 魔力顯示由 ManaDisplayManager 處理，不需要在這裡更新
            }
        }, REGEN_INTERVAL, REGEN_INTERVAL);
    }

    /**
     * 停止魔力回復任務
     */
    public void stopRegenTask() {
        if (regenTask != null) {
            regenTask.cancel();
        }
    }

    /**
     * 回復玩家魔力
     * @param player 玩家
     */
    private void regenerateMana(Player player) {
        PlayerStats stats = statsManager.getStats(player);
        if (stats == null) {
            return;
        }

        // 如果已滿，不需要回復
        if (stats.getCurrentMana() >= stats.getMaxMana()) {
            return;
        }

        // 計算總回復速度（基礎 + 裝備加成）
        double totalRegen = stats.getManaRegen() + getEquipmentManaRegen(player);

        // 回復魔力
        stats.restoreMana(totalRegen);
    }

    /**
     * 獲取裝備提供的魔力回復加成
     * @param player 玩家
     * @return 魔力回復速度加成
     */
    private double getEquipmentManaRegen(Player player) {
        double bonus = 0.0;

        // 檢查所有裝備
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack item : armor) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            // 檢查物品的 Lore 或 CustomModelData 來判斷魔力回復加成
            // 這裡可以根據你的裝備系統來調整
            bonus += getManaRegenFromItem(item);
        }

        return bonus;
    }

    /**
     * 從物品獲取魔力回復加成
     * @param item 物品
     * @return 魔力回復速度
     */
    private double getManaRegenFromItem(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0.0;
        }

        // 掃描 Lore 找尋魔力回復屬性
        for (String line : item.getItemMeta().getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped.contains("魔力回復") || stripped.contains("Mana Regen")) {
                // 嘗試解析數值，例如: "魔力回復: +2.0"
                try {
                    String[] parts = stripped.split(":");
                    if (parts.length >= 2) {
                        String numPart = parts[1].trim().replaceAll("[^0-9.]", "");
                        return Double.parseDouble(numPart);
                    }
                } catch (Exception e) {
                    // 解析失敗，忽略
                }
            }
        }

        return 0.0;
    }

    /**
     * 更新玩家的魔力顯示條（已移除經驗值條功能）
     * @param player 玩家
     */
    public void updateManaBar(Player player) {
        // 魔力顯示現在由 ManaDisplayManager 處理
        // 此方法保留以維持兼容性
    }

    /**
     * 顯示玩家的魔力條（已移除經驗值條功能）
     * @param player 玩家
     */
    public void showManaBar(Player player) {
        // 魔力顯示現在由 ManaDisplayManager 處理
    }

    /**
     * 隱藏玩家的魔力條（已移除經驗值條功能）
     * @param player 玩家
     */
    public void hideManaBar(Player player) {
        // 魔力顯示現在由 ManaDisplayManager 處理
    }

    /**
     * 移除玩家的魔力條（已移除經驗值條功能）
     * @param player 玩家
     */
    public void removeManaBar(Player player) {
        // 魔力顯示現在由 ManaDisplayManager 處理
    }

    /**
     * 檢查玩家是否有足夠的魔力
     * @param player 玩家
     * @param cost 魔力消耗
     * @return 是否有足夠的魔力
     */
    public boolean hasMana(Player player, double cost) {
        PlayerStats stats = statsManager.getStats(player);
        if (stats == null) {
            return false;
        }
        return stats.getCurrentMana() >= cost;
    }

    /**
     * 消耗玩家魔力
     * @param player 玩家
     * @param cost 魔力消耗
     * @return 是否成功消耗
     */
    public boolean consumeMana(Player player, double cost) {
        PlayerStats stats = statsManager.getStats(player);
        if (stats == null) {
            return false;
        }

        return stats.consumeMana(cost);
    }

    /**
     * 恢復玩家魔力
     * @param player 玩家
     * @param amount 恢復量
     */
    public void restoreMana(Player player, double amount) {
        PlayerStats stats = statsManager.getStats(player);
        if (stats == null) {
            return;
        }

        stats.restoreMana(amount);
    }

    /**
     * 設置玩家最大魔力
     * @param player 玩家
     * @param maxMana 最大魔力
     */
    public void setMaxMana(Player player, double maxMana) {
        PlayerStats stats = statsManager.getStats(player);
        if (stats == null) {
            return;
        }

        stats.setMaxMana(maxMana);
    }

    /**
     * 設置玩家魔力回復速度
     * @param player 玩家
     * @param regenRate 回復速度（每秒）
     */
    public void setManaRegen(Player player, double regenRate) {
        PlayerStats stats = statsManager.getStats(player);
        if (stats == null) {
            return;
        }

        stats.setManaRegen(regenRate);
    }
}


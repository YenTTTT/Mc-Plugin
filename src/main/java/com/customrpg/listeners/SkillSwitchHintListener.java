package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.SkillSwitchManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SkillSwitchHintListener - 顯示技能切換提示
 *
 * 當玩家持有可切換的技能物品時，在Action Bar顯示切換提示
 */
public class SkillSwitchHintListener implements Listener {

    private final CustomRPG plugin;
    private final SkillSwitchManager switchManager;

    // 追蹤正在顯示提示的玩家
    private final Map<UUID, BukkitRunnable> activeHints = new HashMap<>();

    public SkillSwitchHintListener(CustomRPG plugin) {
        this.plugin = plugin;
        this.switchManager = plugin.getSkillSwitchManager();
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        // 取消舊的提示任務
        BukkitRunnable oldTask = activeHints.remove(player.getUniqueId());
        if (oldTask != null) {
            oldTask.cancel();
        }

        // 檢查新物品是否有多個技能可切換
        if (newItem != null && newItem.getType() != Material.AIR) {
            String hint = switchManager.getSwitchHint(player, newItem);
            if (hint != null) {
                // 開始顯示提示
                BukkitRunnable task = new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (ticks >= 60 || !player.isOnline()) { // 3秒後停止
                            cancel();
                            activeHints.remove(player.getUniqueId());
                            return;
                        }

                        // 檢查玩家是否還在持有該物品
                        ItemStack currentItem = player.getInventory().getItemInMainHand();
                        if (currentItem.getType() != newItem.getType()) {
                            cancel();
                            activeHints.remove(player.getUniqueId());
                            return;
                        }

                        // 顯示提示
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(hint));
                        ticks++;
                    }
                };
                task.runTaskTimer(plugin, 0L, 1L);
                activeHints.put(player.getUniqueId(), task);
            }
        }
    }

    /**
     * 清理玩家數據
     */
    public void cleanup() {
        for (BukkitRunnable task : activeHints.values()) {
            task.cancel();
        }
        activeHints.clear();
    }
}


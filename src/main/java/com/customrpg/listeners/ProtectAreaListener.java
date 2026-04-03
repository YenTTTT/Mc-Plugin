package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.commands.ProtectAreaCommand;
import com.customrpg.managers.ProtectionAreaManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 保護區域 Listener
 * 1. 處理選區工具的左鍵/右鍵點擊
 * 2. 阻止保護區域內的原版怪物生成
 */
public class ProtectAreaListener implements Listener {

    private final CustomRPG plugin;
    private final ProtectionAreaManager areaManager;
    private final NamespacedKey wandKey;

    public ProtectAreaListener(CustomRPG plugin, ProtectionAreaManager areaManager) {
        this.plugin = plugin;
        this.areaManager = areaManager;
        this.wandKey = new NamespacedKey(plugin, ProtectAreaCommand.WAND_KEY);
    }

    /**
     * 處理選區工具點擊
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 檢查是否手持選區工具
        if (!isWand(item)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Action action = event.getAction();
        Location loc = block.getLocation();

        if (action == Action.LEFT_CLICK_BLOCK) {
            // 左鍵 → 座標 1
            areaManager.setPos1(player.getUniqueId(), loc);
            player.sendMessage(ChatColor.GREEN + "✓ 座標 1 已設定: " + ChatColor.AQUA
                    + "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
            event.setCancelled(true);

            // 檢查是否兩點都設好了
            ProtectionAreaManager.Selection sel = areaManager.getSelection(player.getUniqueId());
            if (sel != null && sel.isComplete()) {
                player.sendMessage(ChatColor.YELLOW + "兩個座標已就緒！輸入 " + ChatColor.GREEN + "/protectarea <名稱>" + ChatColor.YELLOW + " 來建立區域。");
            }

        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            // 右鍵 → 座標 2
            areaManager.setPos2(player.getUniqueId(), loc);
            player.sendMessage(ChatColor.GREEN + "✓ 座標 2 已設定: " + ChatColor.AQUA
                    + "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
            event.setCancelled(true);

            // 檢查是否兩點都設好了
            ProtectionAreaManager.Selection sel = areaManager.getSelection(player.getUniqueId());
            if (sel != null && sel.isComplete()) {
                player.sendMessage(ChatColor.YELLOW + "兩個座標已就緒！輸入 " + ChatColor.GREEN + "/protectarea <名稱>" + ChatColor.YELLOW + " 來建立區域。");
            }
        }
    }

    /**
     * 阻止保護區域內的原版怪物自然生成
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // 只阻止自然生成和刷怪籠
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != CreatureSpawnEvent.SpawnReason.SPAWNER
                && reason != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                && reason != CreatureSpawnEvent.SpawnReason.RAID
                && reason != CreatureSpawnEvent.SpawnReason.PATROL) {
            return; // 指令生成、蛋生成等不阻擋
        }

        Location loc = event.getLocation();
        if (areaManager.isInProtectedArea2D(loc)) {
            event.setCancelled(true);
        }
    }

    /**
     * 檢查物品是否為選區工具
     */
    private boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_HOE) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BOOLEAN);
    }
}



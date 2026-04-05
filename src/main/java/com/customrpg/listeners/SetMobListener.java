package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.commands.SetMobCommand;
import com.customrpg.managers.ZoneManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.scheduler.BukkitRunnable;

/**
 * SetMobListener — 怪物區域選區工具 + 區域內怪物生成阻擋 + Tier 切換提示
 */
public class SetMobListener implements Listener {

    private final CustomRPG plugin;
    private final ZoneManager zoneManager;
    private final NamespacedKey wandKey;

    public SetMobListener(CustomRPG plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
        this.wandKey = new NamespacedKey(plugin, SetMobCommand.WAND_KEY);

        // 啟動 tier 切換檢查任務
        startTierCheckTask();
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
            zoneManager.setPos1(player.getUniqueId(), loc);
            player.sendMessage(ChatColor.GREEN + "✓ 座標 1 已設定: " + ChatColor.AQUA
                    + "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
            event.setCancelled(true);

            // 檢查是否兩點都設好了
            checkSelectionComplete(player);

        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            // 右鍵 → 座標 2
            zoneManager.setPos2(player.getUniqueId(), loc);
            player.sendMessage(ChatColor.GREEN + "✓ 座標 2 已設定: " + ChatColor.AQUA
                    + "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
            event.setCancelled(true);

            // 檢查是否兩點都設好了
            checkSelectionComplete(player);
        }
    }

    private void checkSelectionComplete(Player player) {
        ZoneManager.Selection sel = zoneManager.getSelection(player.getUniqueId());
        if (sel != null && sel.isComplete()) {
            player.sendMessage(ChatColor.YELLOW + "兩個座標已就緒！輸入 " + ChatColor.GREEN + "/setmob <最小等級> <最大等級> <區域ID>" + ChatColor.YELLOW + " 來建立區域。");
            player.sendMessage(ChatColor.GRAY + "範例: /setmob 1 5 area1");
        }
    }

    /**
     * 阻止怪物區域內的原版怪物自然生成
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // 只阻止自然生成
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != CreatureSpawnEvent.SpawnReason.SPAWNER
                && reason != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                && reason != CreatureSpawnEvent.SpawnReason.RAID
                && reason != CreatureSpawnEvent.SpawnReason.PATROL) {
            return;
        }

        Location loc = event.getLocation();
        if (zoneManager.isInsideAnyZone(loc)) {
            event.setCancelled(true);
        }
    }

    /**
     * 啟動 tier 切換檢查任務 (每 2 秒檢查一次)
     */
    private void startTierCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    int newTier = zoneManager.checkTierChange(player);
                    if (newTier >= 0) {
                        // Tier 發生變化！
                        showTierChangeEffect(player, newTier);
                    }
                }
            }
        }.runTaskTimer(plugin, 40, 40); // 每 2 秒
    }

    /**
     * 顯示 tier 切換效果
     */
    private void showTierChangeEffect(Player player, int tier) {
        String tierName = zoneManager.getTierDisplayName(tier);
        int[] levelRange = zoneManager.calculateMobLevelRange(player.getLocation());
        ChatColor color = zoneManager.getTierColor(tier);

        // 不在第一次進入時觸發 (避免登入時就觸發)
        // ActionBar 顯示
        String actionBarMsg;
        if (tier == 0) {
            actionBarMsg = ChatColor.GREEN + "✦ 進入安全區域 ✦";
        } else {
            actionBarMsg = color + "⚠ " + tierName + ChatColor.GRAY + " | 怪物等級: Lv." + levelRange[0] + "~" + levelRange[1];
        }

        player.sendActionBar(actionBarMsg);

        // 音效
        if (tier == 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
        } else if (tier >= 4) {
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.5f, 0.7f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.0f);
        }

        // 高 tier 區域粒子效果
        if (tier >= 3) {
            Location loc = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 15, 1.5, 0.5, 1.5, 0.02);
        }
    }

    /**
     * 檢查物品是否為選區工具
     */
    private boolean isWand(ItemStack item) {
        if (item == null) return false;
        // 支援 BREEZE_ROD 和 BLAZE_ROD
        Material type = item.getType();
        boolean isBreezeRod;
        try {
            isBreezeRod = (type == Material.valueOf("BREEZE_ROD"));
        } catch (IllegalArgumentException e) {
            isBreezeRod = false;
        }
        if (type != Material.BLAZE_ROD && !isBreezeRod) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BOOLEAN);
    }
}



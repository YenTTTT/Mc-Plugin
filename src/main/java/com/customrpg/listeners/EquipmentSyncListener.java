package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.equipment.EquipmentData;
import com.customrpg.equipment.EquipmentManager;
import com.customrpg.equipment.EquipmentSlot;
import com.customrpg.gui.EquipmentGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 裝備同步監聽器
 * 處理玩家直接操作原生裝備槽位時的同步
 */
public class EquipmentSyncListener implements Listener {

    private final CustomRPG plugin;
    private final EquipmentManager equipmentManager;

    // 追踪玩家上一次的裝備狀態，避免重複處理
    private final Map<UUID, Map<EquipmentSlot, ItemStack>> lastKnownEquipment;

    public EquipmentSyncListener(CustomRPG plugin, EquipmentManager equipmentManager) {
        this.plugin = plugin;
        this.equipmentManager = equipmentManager;
        this.lastKnownEquipment = new HashMap<>();
    }

    /**
     * 監聽玩家背包中的裝備槽位點擊
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.isCancelled()) return;

        Player player = (Player) event.getWhoClicked();

        // 只處理玩家自己的背包
        if (event.getInventory().getType() != InventoryType.PLAYER) return;

        // 檢查是否點擊了裝備槽位
        int slot = event.getRawSlot();
        EquipmentSlot equipSlot = getEquipmentSlotFromRawSlot(slot);

        if (equipSlot != null) {
            // 延遲檢查，確保物品已經移動
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                checkAndSyncEquipmentSlot(player, equipSlot);
            }, 1L);
        }

        // 也檢查主手切換
        if (slot == 36 || (slot >= 27 && slot <= 35)) { // 快捷欄
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                checkAndSyncEquipmentSlot(player, EquipmentSlot.MAIN_HAND);
            }, 1L);
        }
    }

    /**
     * 監聽玩家切換主手物品
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        // 延遲檢查主手裝備
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            checkAndSyncEquipmentSlot(player, EquipmentSlot.MAIN_HAND);
        }, 1L);
    }

    /**
     * 監聽玩家離開事件，清理數據
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer().getUniqueId());
    }

    /**
     * 根據原始槽位索引獲取裝備槽位
     */
    private EquipmentSlot getEquipmentSlotFromRawSlot(int rawSlot) {
        switch (rawSlot) {
            case 5: return EquipmentSlot.HELMET;
            case 6: return EquipmentSlot.CHESTPLATE;
            case 7: return EquipmentSlot.LEGGINGS;
            case 8: return EquipmentSlot.BOOTS;
            case 45: return EquipmentSlot.OFF_HAND; // 副手槽位
            default: return null;
        }
    }

    /**
     * 檢查並同步特定裝備槽位
     */
    private void checkAndSyncEquipmentSlot(Player player, EquipmentSlot slot) {
        UUID playerId = player.getUniqueId();
        ItemStack currentItem = getItemFromMinecraftSlot(player, slot);

        // 獲取上次已知的裝備狀態
        Map<EquipmentSlot, ItemStack> playerLastEquipment = lastKnownEquipment.computeIfAbsent(playerId, k -> new HashMap<>());
        ItemStack lastItem = playerLastEquipment.get(slot);

        // 檢查是否有變化
        if (!itemsEqual(currentItem, lastItem)) {
            // 更新已知狀態
            playerLastEquipment.put(slot, currentItem != null ? currentItem.clone() : null);

            // 同步到裝備系統
            syncToEquipmentSystem(player, slot, currentItem);

            // 如果玩家正在使用裝備GUI，刷新它
            refreshPlayerEquipmentGUI(player);
        }
    }

    /**
     * 比較兩個物品是否相等
     */
    private boolean itemsEqual(ItemStack item1, ItemStack item2) {
        if (item1 == null && item2 == null) return true;
        if (item1 == null || item2 == null) return false;
        if ((item1.getType() == Material.AIR) && (item2.getType() == Material.AIR)) return true;
        if (item1.getType() == Material.AIR || item2.getType() == Material.AIR) return false;

        return item1.isSimilar(item2) && item1.getAmount() == item2.getAmount();
    }

    /**
     * 同步到裝備系統
     */
    private void syncToEquipmentSystem(Player player, EquipmentSlot slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            // 移除裝備
            equipmentManager.unequipItem(player, slot);
        } else {
            // 嘗試將物品轉換為裝備數據並裝備
            EquipmentData equipmentData = convertItemToEquipmentData(item, slot);
            if (equipmentData != null) {
                equipmentManager.equipItem(player, slot, equipmentData);
            }
        }
    }

    /**
     * 將物品轉換為裝備數據
     */
    private EquipmentData convertItemToEquipmentData(ItemStack item, EquipmentSlot slot) {
        if (item == null || item.getType() == Material.AIR) return null;

        // 檢查是否是自定義裝備
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();

            // 嘗試從模板中找到匹配的裝備
            for (EquipmentData template : equipmentManager.getEquipmentTemplates().values()) {
                if (template.getMaterial() == item.getType() && template.getSlot() == slot) {
                    String templateName = org.bukkit.ChatColor.stripColor(template.getName());
                    String itemName = org.bukkit.ChatColor.stripColor(displayName);

                    if (itemName.contains(templateName) || templateName.contains(itemName)) {
                        return equipmentManager.createEquipment(template.getId());
                    }
                }
            }
        }

        // 創建基本裝備數據
        return createBasicEquipmentData(item, slot);
    }

    /**
     * 創建基本裝備數據
     */
    private EquipmentData createBasicEquipmentData(ItemStack item, EquipmentSlot slot) {
        String itemId = "minecraft_" + item.getType().name().toLowerCase();
        String itemName = "§f" + item.getType().name().replace("_", " ");

        EquipmentData equipment = new EquipmentData(
            itemId, itemName, slot,
            com.customrpg.equipment.EquipmentRarity.COMMON,
            item.getType()
        );

        // 添加基本屬性
        Map<com.customrpg.equipment.EquipmentAttribute, Double> attributes = new HashMap<>();
        String materialName = item.getType().name();

        if (materialName.contains("DIAMOND")) {
            attributes.put(com.customrpg.equipment.EquipmentAttribute.ATTACK_DAMAGE, 8.0);
            attributes.put(com.customrpg.equipment.EquipmentAttribute.DEFENSE, 3.0);
        } else if (materialName.contains("IRON")) {
            attributes.put(com.customrpg.equipment.EquipmentAttribute.ATTACK_DAMAGE, 6.0);
            attributes.put(com.customrpg.equipment.EquipmentAttribute.DEFENSE, 2.0);
        } else if (materialName.contains("GOLD")) {
            attributes.put(com.customrpg.equipment.EquipmentAttribute.ATTACK_DAMAGE, 4.0);
            attributes.put(com.customrpg.equipment.EquipmentAttribute.LUCK, 2.0);
        } else {
            attributes.put(com.customrpg.equipment.EquipmentAttribute.ATTACK_DAMAGE, 3.0);
        }

        equipment.setBaseAttributes(attributes);
        return equipment;
    }

    /**
     * 刷新玩家的裝備GUI（如果正在使用）
     */
    private void refreshPlayerEquipmentGUI(Player player) {
        // 檢查玩家是否正在使用裝備GUI
        if (player.getOpenInventory().getTopInventory().getSize() == 54) {
            String title = player.getOpenInventory().getTitle();
            if (title != null && title.contains("角色裝備")) {
                // 延遲一點刷新GUI，確保同步完成
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    EquipmentGUI gui = plugin.getEquipmentGUI();
                    if (gui != null) {
                        gui.openEquipmentGUI(player);
                    }
                }, 2L);
            }
        }
    }

    /**
     * 從Minecraft槽位獲取物品
     */
    private ItemStack getItemFromMinecraftSlot(Player player, EquipmentSlot slot) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();

        switch (slot) {
            case HELMET: return inv.getHelmet();
            case CHESTPLATE: return inv.getChestplate();
            case LEGGINGS: return inv.getLeggings();
            case BOOTS: return inv.getBoots();
            case MAIN_HAND: return inv.getItemInMainHand();
            case OFF_HAND: return inv.getItemInOffHand();
            default: return null;
        }
    }

    /**
     * 清理玩家數據
     */
    public void cleanupPlayer(UUID playerId) {
        lastKnownEquipment.remove(playerId);
    }
}

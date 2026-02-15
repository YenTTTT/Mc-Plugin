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

        // 處理 Shift+點擊
        if (event.getClick().isShiftClick()) {
            // 不論是在哪個 Inventory，只要是 Shift+點擊都可能導致裝備變動
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                checkAndSyncAllEquipmentSlots(player);
            }, 1L);
            return;
        }

        // 處理拖放與交換
        int slot = event.getRawSlot();
        EquipmentSlot equipSlot = getEquipmentSlotFromRawSlot(slot);

        if (equipSlot != null) {
            // 延遲檢查，確保物品已經移動
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                checkAndSyncEquipmentSlot(player, equipSlot);
            }, 1L);
        }

        // 處理 Hotbar 交換 (按下 1-9 鍵交換物品到槽位)
        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                checkAndSyncAllEquipmentSlots(player);
            }, 1L);
        }

        // 也檢查主手切換 (快捷欄點擊)
        if (slot == 36 || (slot >= 27 && slot <= 35)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                checkAndSyncEquipmentSlot(player, EquipmentSlot.MAIN_HAND);
            }, 1L);
        }
    }

    /**
     * 監聽拖拽事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.isCancelled()) return;

        Player player = (Player) event.getWhoClicked();
        boolean armorSlotAffected = false;

        for (int slot : event.getRawSlots()) {
            if (getEquipmentSlotFromRawSlot(slot) != null) {
                armorSlotAffected = true;
                break;
            }
        }

        if (armorSlotAffected) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                checkAndSyncAllEquipmentSlots(player);
            }, 1L);
        }
    }

    /**
     * 監聽背包關閉，做最後的同步確認
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        checkAndSyncAllEquipmentSlots((Player) event.getPlayer());
    }

    /**
     * 監聽創意模式點擊
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreativeClick(org.bukkit.event.inventory.InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            checkAndSyncAllEquipmentSlots((Player) event.getWhoClicked());
        }, 1L);
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
     * 監聽玩家右鍵穿上裝備
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && 
            event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        String typeName = item.getType().name();
        if (typeName.contains("_HELMET") || typeName.contains("_CHESTPLATE") || 
            typeName.contains("_LEGGINGS") || typeName.contains("_BOOTS")) {
            // 右鍵穿裝備會影響多個槽位，延遲檢查全部
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                checkAndSyncAllEquipmentSlots(event.getPlayer());
            }, 1L);
        }
    }

    /**
     * 監聽玩家離開事件，清理數據
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer().getUniqueId());
    }

    /**
     * 檢查並同步所有裝備槽位
     */
    private void checkAndSyncAllEquipmentSlots(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            // 只檢查原生 Minecraft 有對應的槽位
            if (slot == EquipmentSlot.HELMET || slot == EquipmentSlot.CHESTPLATE || 
                slot == EquipmentSlot.LEGGINGS || slot == EquipmentSlot.BOOTS || 
                slot == EquipmentSlot.MAIN_HAND || slot == EquipmentSlot.OFF_HAND) {
                checkAndSyncEquipmentSlot(player, slot);
            }
        }
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
     * 同步裝備到裝備系統
     */
    private void syncToEquipmentSystem(Player player, EquipmentSlot slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            // 移除裝備
            // 直接操作數據避免反向同步 Minecraft 槽位
            Map<EquipmentSlot, EquipmentData> playerEquip = equipmentManager.getPlayerEquipment(player.getUniqueId());
            if (playerEquip.remove(slot) != null) {
                equipmentManager.updatePlayerAttributes(player);
            }
        } else {
            // 檢查是否已經是正確的裝備數據（避免重複轉換）
            Map<EquipmentSlot, EquipmentData> playerEquip = equipmentManager.getPlayerEquipment(player.getUniqueId());
            EquipmentData existing = playerEquip.get(slot);
            if (existing != null) {
                ItemStack existingItem = existing.toItemStack();
                if (itemsEqual(existingItem, item)) {
                    return; // 已經同步過了
                }
            }

            // 嘗試將物品轉換為裝備數據並裝備
            EquipmentData equipmentData = convertItemToEquipmentData(item, slot);
            if (equipmentData != null) {
                // 直接操作數據避免反向同步 Minecraft 槽位
                playerEquip.put(slot, equipmentData);
                equipmentManager.updatePlayerAttributes(player);
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
    public void refreshPlayerEquipmentGUI(Player player) {
        EquipmentGUI gui = plugin.getEquipmentGUI();
        if (gui != null && gui.isUsingEquipmentGUI(player)) {
            // 使用同步任務確保線程安全
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                gui.refreshCurrentGUI(player);
            });
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

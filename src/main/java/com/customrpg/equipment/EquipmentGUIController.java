package com.customrpg.equipment;

import com.customrpg.CustomRPG;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 裝備GUI控制器
 *
 * 負責：
 * - 創建裝備檢視GUI
 * - 處理GUI交互
 * - 動態刷新GUI
 * - 顯示裝備驗證狀態
 */
public class EquipmentGUIController implements Listener {

    private final CustomRPG plugin;
    private final EquipmentManager equipmentManager;
    private final EquipmentValidator validator;
    private final StatCalculator calculator;

    // 追蹤打開的GUI
    private final Map<UUID, Inventory> openGUIs;
    private final Map<UUID, GUIType> guiTypes;

    public enum GUIType {
        EQUIPMENT_VIEW,    // 裝備檢視
        ARMOR_SELECTION,   // 裝甲選擇
        WEAPON_SELECTION   // 武器選擇
    }

    public EquipmentGUIController(CustomRPG plugin, EquipmentManager equipmentManager) {
        this.plugin = plugin;
        this.equipmentManager = equipmentManager;
        this.validator = new EquipmentValidator(plugin, equipmentManager);
        this.calculator = new StatCalculator(plugin, equipmentManager);
        this.openGUIs = new HashMap<>();
        this.guiTypes = new HashMap<>();
    }

    /**
     * 開啟裝備檢視GUI
     */
    public void openEquipmentViewGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6§l裝備檢視");

        // 填充背景
        fillBackground(gui);

        // 顯示裝備槽位
        displayEquipmentSlots(gui, player);

        // 顯示屬性面板
        displayStatsPanel(gui, player);

        // 功能按鈕
        addFunctionButtons(gui);

        openGUIs.put(player.getUniqueId(), gui);
        guiTypes.put(player.getUniqueId(), GUIType.EQUIPMENT_VIEW);
        player.openInventory(gui);
    }

    /**
     * 開啟裝甲選擇GUI
     */
    public void openArmorSelectionGUI(Player player, EquipmentSlot slot) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6§l選擇裝甲 - " + slot.getDisplayName());

        // 獲取所有可用的裝甲
        ArmorManager armorManager = plugin.getArmorManager();
        if (armorManager != null) {
            List<ArmorData> armors = new ArrayList<>();
            for (ArmorData armor : armorManager.getArmorTemplates().values()) {
                if (armor.getSlot() == slot) {
                    armors.add(armor);
                }
            }

            // 顯示裝甲列表
            int index = 0;
            for (ArmorData armor : armors) {
                if (index >= 45) break; // 最多45個

                ItemStack item = armor.toItemStack();

                // 添加驗證狀態
                ValidationResult result = validator.validateArmor(player, armor);
                if (result != ValidationResult.SUCCESS) {
                    addValidationLore(item, result);
                }

                gui.setItem(index, item);
                index++;
            }
        }

        // 返回按鈕
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§7返回");
            backButton.setItemMeta(backMeta);
        }
        gui.setItem(49, backButton);

        openGUIs.put(player.getUniqueId(), gui);
        guiTypes.put(player.getUniqueId(), GUIType.ARMOR_SELECTION);
        player.openInventory(gui);
    }

    /**
     * 刷新裝備GUI
     */
    public void refreshGUI(Player player) {
        if (!openGUIs.containsKey(player.getUniqueId())) return;

        GUIType type = guiTypes.get(player.getUniqueId());
        if (type == GUIType.EQUIPMENT_VIEW) {
            openEquipmentViewGUI(player);
        }
    }

    /**
     * 填充背景
     */
    private void fillBackground(Inventory gui) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, filler);
        }
    }

    /**
     * 顯示裝備槽位
     */
    private void displayEquipmentSlots(Inventory gui, Player player) {
        Map<EquipmentSlot, EquipmentData> equipment = equipmentManager.getPlayerEquipment(player.getUniqueId());

        // 定義槽位位置
        Map<EquipmentSlot, Integer> slotPositions = new HashMap<>();
        slotPositions.put(EquipmentSlot.HELMET, 10);
        slotPositions.put(EquipmentSlot.CHESTPLATE, 19);
        slotPositions.put(EquipmentSlot.LEGGINGS, 28);
        slotPositions.put(EquipmentSlot.BOOTS, 37);

        for (Map.Entry<EquipmentSlot, Integer> entry : slotPositions.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            int position = entry.getValue();

            EquipmentData equip = equipment.get(slot);
            if (equip != null) {
                gui.setItem(position, equip.toItemStack());
            } else {
                gui.setItem(position, createEmptySlot(slot));
            }
        }
    }

    /**
     * 創建空槽位物品
     */
    private ItemStack createEmptySlot(EquipmentSlot slot) {
        Material material = switch (slot) {
            case HELMET -> Material.LEATHER_HELMET;
            case CHESTPLATE -> Material.LEATHER_CHESTPLATE;
            case LEGGINGS -> Material.LEATHER_LEGGINGS;
            case BOOTS -> Material.LEATHER_BOOTS;
            default -> Material.BARRIER;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8" + slot.getDisplayName() + " §7(空)");
            List<String> lore = new ArrayList<>();
            lore.add("§7點擊選擇裝備");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 顯示屬性面板
     */
    private void displayStatsPanel(Inventory gui, Player player) {
        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta meta = statsItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6§l當前屬性");

            List<String> lore = new ArrayList<>();
            lore.add("§8━━━━━━━━━━━━━━━━");

            // 使用計算器獲取最終屬性
            StatCalculator.FinalStats stats = calculator.calculateFinalStats(player);

            lore.add("§e力量: §f" + stats.getTotalStrength());
            lore.add("§b智慧: §f" + stats.getTotalMagic());
            lore.add("§a敏捷: §f" + stats.getTotalAgility());
            lore.add("§c體力: §f" + stats.getTotalVitality());
            lore.add("§7防禦: §f" + stats.getTotalDefense());

            lore.add("§8━━━━━━━━━━━━━━━━");

            // 傷害數據
            double physDmg = calculator.calculatePhysicalDamage(player);
            double magicDmg = calculator.calculateMagicDamage(player);

            lore.add("§6物理傷害: §f" + String.format("%.1f", physDmg));
            lore.add("§d魔法傷害: §f" + String.format("%.1f", magicDmg));

            meta.setLore(lore);
            statsItem.setItemMeta(meta);
        }

        gui.setItem(13, statsItem);
    }

    /**
     * 添加功能按鈕
     */
    private void addFunctionButtons(Inventory gui) {
        // 關閉按鈕
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName("§c關閉");
            closeButton.setItemMeta(closeMeta);
        }
        gui.setItem(49, closeButton);
    }

    /**
     * 添加驗證狀態到物品
     */
    private void addValidationLore(ItemStack item, ValidationResult result) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null) lore = new ArrayList<>();
            lore.add("");
            lore.add(result.getMessage());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    /**
     * 處理GUI點擊事件
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!openGUIs.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        GUIType type = guiTypes.get(player.getUniqueId());

        switch (type) {
            case EQUIPMENT_VIEW:
                handleEquipmentViewClick(player, slot, clicked);
                break;
            case ARMOR_SELECTION:
                handleArmorSelectionClick(player, slot, clicked);
                break;
        }
    }

    /**
     * 處理裝備檢視點擊
     */
    private void handleEquipmentViewClick(Player player, int slot, ItemStack clicked) {
        // 關閉按鈕
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        // 裝備槽位點擊
        Map<Integer, EquipmentSlot> slotMap = new HashMap<>();
        slotMap.put(10, EquipmentSlot.HELMET);
        slotMap.put(19, EquipmentSlot.CHESTPLATE);
        slotMap.put(28, EquipmentSlot.LEGGINGS);
        slotMap.put(37, EquipmentSlot.BOOTS);

        EquipmentSlot equipSlot = slotMap.get(slot);
        if (equipSlot != null) {
            openArmorSelectionGUI(player, equipSlot);
        }
    }

    /**
     * 處理裝甲選擇點擊
     */
    private void handleArmorSelectionClick(Player player, int slot, ItemStack clicked) {
        // 返回按鈕
        if (slot == 49) {
            openEquipmentViewGUI(player);
            return;
        }

        // 嘗試裝備選中的裝甲
        // 這裡需要從物品中提取裝甲ID並裝備
        // 暫時只是示例，實際需要從NBT或其他方式識別
        player.sendMessage("§a功能開發中...");
    }

    /**
     * 處理GUI關閉事件
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openGUIs.remove(player.getUniqueId());
            guiTypes.remove(player.getUniqueId());
        }
    }

    /**
     * 清理資源
     */
    public void cleanup() {
        openGUIs.clear();
        guiTypes.clear();
    }
}


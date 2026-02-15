package com.customrpg.gui;

import com.customrpg.CustomRPG;
import com.customrpg.equipment.*;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.players.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 裝備GUI系統
 * 顯示角色裝備界面、強化界面、符文鑲嵌界面
 */
public class EquipmentGUI implements Listener {

    private final CustomRPG plugin;
    private final EquipmentManager equipmentManager;
    private final PlayerStatsManager statsManager;

    // GUI狀態追蹤
    private final Map<UUID, EquipmentGUIType> currentGUIType;
    private final Map<UUID, Inventory> openInventories;
    private final Map<UUID, EquipmentSlot> selectedSlot; // 當前選中的裝備槽位
    private final Map<UUID, EquipmentData> selectedEquipment; // 當前選中的裝備

    // GUI配置
    private static final int EQUIPMENT_GUI_SIZE = 54; // 6行9列
    private static final int ENHANCE_GUI_SIZE = 45; // 5行9列
    private static final int RUNE_GUI_SIZE = 54; // 6行9列

    // 裝備槽位在GUI中的位置
    private static final Map<EquipmentSlot, Integer> SLOT_POSITIONS = new HashMap<>();
    // 每個裝備槽位對應的放置槽位（右邊）
    private static final Map<EquipmentSlot, Integer> PLACE_POSITIONS = new HashMap<>();
    static {
        // 主要裝備槽位佈局 - 左邊是顯示，右邊是放置
        // 第1行: [空][肩甲][放置][頭盔][放置][披風][放置][空][空]
        SLOT_POSITIONS.put(EquipmentSlot.SHOULDER, 10);
        PLACE_POSITIONS.put(EquipmentSlot.SHOULDER, 11);
        SLOT_POSITIONS.put(EquipmentSlot.HELMET, 12);
        PLACE_POSITIONS.put(EquipmentSlot.HELMET, 13);
        SLOT_POSITIONS.put(EquipmentSlot.CLOAK, 14);
        PLACE_POSITIONS.put(EquipmentSlot.CLOAK, 15);

        // 第2行: [空][主手][放置][胸甲][放置][副手][放置][空][空]
        SLOT_POSITIONS.put(EquipmentSlot.MAIN_HAND, 19);
        PLACE_POSITIONS.put(EquipmentSlot.MAIN_HAND, 20);
        SLOT_POSITIONS.put(EquipmentSlot.CHESTPLATE, 21);
        PLACE_POSITIONS.put(EquipmentSlot.CHESTPLATE, 22);
        SLOT_POSITIONS.put(EquipmentSlot.OFF_HAND, 23);
        PLACE_POSITIONS.put(EquipmentSlot.OFF_HAND, 24);

        // 第3行: [空][戒指1][放置][腿甲][放置][戒指2][放置][空][空]
        SLOT_POSITIONS.put(EquipmentSlot.RING_1, 28);
        PLACE_POSITIONS.put(EquipmentSlot.RING_1, 29);
        SLOT_POSITIONS.put(EquipmentSlot.LEGGINGS, 30);
        PLACE_POSITIONS.put(EquipmentSlot.LEGGINGS, 31);
        SLOT_POSITIONS.put(EquipmentSlot.RING_2, 32);
        PLACE_POSITIONS.put(EquipmentSlot.RING_2, 33);

        // 第4行: [空][項鍊][放置][靴子][放置][手環][放置][空][空]
        SLOT_POSITIONS.put(EquipmentSlot.NECKLACE, 37);
        PLACE_POSITIONS.put(EquipmentSlot.NECKLACE, 38);
        SLOT_POSITIONS.put(EquipmentSlot.BOOTS, 39);
        PLACE_POSITIONS.put(EquipmentSlot.BOOTS, 40);
        SLOT_POSITIONS.put(EquipmentSlot.BRACELET, 41);
        PLACE_POSITIONS.put(EquipmentSlot.BRACELET, 42);

        // 第5行: [空][護符][放置][腰帶][放置][空][空][空][空]
        SLOT_POSITIONS.put(EquipmentSlot.CHARM, 46);
        PLACE_POSITIONS.put(EquipmentSlot.CHARM, 47);
        SLOT_POSITIONS.put(EquipmentSlot.BELT, 48);
        PLACE_POSITIONS.put(EquipmentSlot.BELT, 50); // 避免與統計按鈕衝突
    }

    public enum EquipmentGUIType {
        EQUIPMENT("§6§l角色裝備"),
        ENHANCE("§c§l裝備強化"),
        RUNE("§9§l符文鑲嵌");

        private final String title;

        EquipmentGUIType(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    public EquipmentGUI(CustomRPG plugin, EquipmentManager equipmentManager) {
        this.plugin = plugin;
        this.equipmentManager = equipmentManager;
        this.statsManager = plugin.getPlayerStatsManager();

        this.currentGUIType = new HashMap<>();
        this.openInventories = new HashMap<>();
        this.selectedSlot = new HashMap<>();
        this.selectedEquipment = new HashMap<>();
    }

    /**
     * 開啟裝備GUI
     */
    public void openEquipmentGUI(Player player) {
        openGUI(player, EquipmentGUIType.EQUIPMENT);
    }

    /**
     * 開啟強化GUI
     */
    public void openEnhanceGUI(Player player, EquipmentSlot slot) {
        Map<EquipmentSlot, EquipmentData> equipment = equipmentManager.getPlayerEquipment(player.getUniqueId());
        EquipmentData equip = equipment.get(slot);

        if (equip == null) {
            player.sendMessage("§c該槽位沒有裝備！");
            return;
        }

        selectedSlot.put(player.getUniqueId(), slot);
        selectedEquipment.put(player.getUniqueId(), equip);
        openGUI(player, EquipmentGUIType.ENHANCE);
    }

    /**
     * 開啟符文GUI
     */
    public void openRuneGUI(Player player, EquipmentSlot slot) {
        Map<EquipmentSlot, EquipmentData> equipment = equipmentManager.getPlayerEquipment(player.getUniqueId());
        EquipmentData equip = equipment.get(slot);

        if (equip == null) {
            player.sendMessage("§c該槽位沒有裝備！");
            return;
        }

        selectedSlot.put(player.getUniqueId(), slot);
        selectedEquipment.put(player.getUniqueId(), equip);
        openGUI(player, EquipmentGUIType.RUNE);
    }

    /**
     * 開啟指定類型的GUI
     */
    private void openGUI(Player player, EquipmentGUIType guiType) {
        currentGUIType.put(player.getUniqueId(), guiType);

        Inventory gui;
        switch (guiType) {
            case EQUIPMENT:
                gui = createEquipmentGUI(player);
                break;
            case ENHANCE:
                gui = createEnhanceGUI(player);
                break;
            case RUNE:
                gui = createRuneGUI(player);
                break;
            default:
                return;
        }

        openInventories.put(player.getUniqueId(), gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    /**
     * 創建裝備GUI
     */
    private Inventory createEquipmentGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, EQUIPMENT_GUI_SIZE, EquipmentGUIType.EQUIPMENT.getTitle());

        // 填充背景
        fillBackground(gui, EQUIPMENT_GUI_SIZE);

        // 添加裝備槽位
        addEquipmentSlots(gui, player);

        // 添加功能按鈕
        addEquipmentButtons(gui, player);

        return gui;
    }

    /**
     * 創建強化GUI
     */
    private Inventory createEnhanceGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, ENHANCE_GUI_SIZE, EquipmentGUIType.ENHANCE.getTitle());

        EquipmentData equipment = selectedEquipment.get(player.getUniqueId());
        if (equipment == null) return gui;

        // 填充背景
        fillBackground(gui, ENHANCE_GUI_SIZE);

        // 顯示當前裝備
        gui.setItem(13, equipment.toItemStack());

        // 強化信息
        addEnhanceInfo(gui, player, equipment);

        // 強化按鈕
        addEnhanceButtons(gui, player, equipment);

        return gui;
    }

    /**
     * 創建符文GUI
     */
    private Inventory createRuneGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, RUNE_GUI_SIZE, EquipmentGUIType.RUNE.getTitle());

        EquipmentData equipment = selectedEquipment.get(player.getUniqueId());
        if (equipment == null) return gui;

        // 填充背景
        fillBackground(gui, RUNE_GUI_SIZE);

        // 顯示當前裝備
        gui.setItem(13, equipment.toItemStack());

        // 符文槽位
        addRuneSlots(gui, equipment);

        // 可用符文
        addAvailableRunes(gui, player);

        return gui;
    }

    /**
     * 填充背景
     */
    private void fillBackground(Inventory gui, int size) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < size; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
    }

    /**
     * 添加裝備槽位
     */
    private void addEquipmentSlots(Inventory gui, Player player) {
        Map<EquipmentSlot, EquipmentData> equipment = equipmentManager.getPlayerEquipment(player.getUniqueId());

        for (Map.Entry<EquipmentSlot, Integer> entry : SLOT_POSITIONS.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            int displayPosition = entry.getValue();
            int placePosition = PLACE_POSITIONS.get(slot);

            EquipmentData equip = equipment.get(slot);

            // 顯示槽位 - 顯示當前裝備或空槽位
            ItemStack displayItem;
            if (equip != null) {
                displayItem = equip.toItemStack();

                // 添加操作提示
                ItemMeta meta = displayItem.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.getLore();
                    if (lore == null) lore = new ArrayList<>();

                    lore.add("");
                    lore.add("§e左鍵: 卸除裝備");
                    lore.add("§a右鍵: 強化裝備");
                    lore.add("§9Shift+右鍵: 符文鑲嵌");

                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                }
            } else {
                displayItem = createEmptyDisplaySlotItem(slot);
            }

            // 放置槽位 - 始終顯示為放置區域
            ItemStack placeItem = createPlaceSlotItem(slot);

            // 確保槽位被正確設置
            gui.setItem(displayPosition, displayItem);
            gui.setItem(placePosition, placeItem);
        }
    }

    /**
     * 創建空的顯示槽位物品
     */
    private ItemStack createEmptyDisplaySlotItem(EquipmentSlot slot) {
        Material material = getSlotMaterial(slot);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§8" + slot.getDisplayName() + " §7(空)");

            List<String> lore = new ArrayList<>();
            lore.add("§7此槽位目前沒有裝備");
            lore.add("");
            lore.add("§e將裝備拖拽到右邊的槽位來裝備");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 創建放置槽位物品
     */
    private ItemStack createPlaceSlotItem(EquipmentSlot slot) {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§7" + slot.getDisplayName() + " §e放置區");

            List<String> lore = new ArrayList<>();
            lore.add("§8將對應裝備拖拽到這裡");
            lore.add("§8來裝備該物品");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 獲取槽位對應的材質
     */
    private Material getSlotMaterial(EquipmentSlot slot) {
        switch (slot) {
            case HELMET: return Material.LEATHER_HELMET;
            case CHESTPLATE: return Material.LEATHER_CHESTPLATE;
            case LEGGINGS: return Material.LEATHER_LEGGINGS;
            case BOOTS: return Material.LEATHER_BOOTS;
            case MAIN_HAND: return Material.WOODEN_SWORD;
            case OFF_HAND: return Material.SHIELD;
            case SHOULDER: case CLOAK: return Material.WHITE_BANNER;
            case RING_1: case RING_2: return Material.GOLD_NUGGET;
            case NECKLACE: return Material.IRON_INGOT;
            case BRACELET: return Material.STRING;
            case CHARM: return Material.TOTEM_OF_UNDYING;
            case BELT: return Material.LEATHER;
            default: return Material.BARRIER;
        }
    }

    /**
     * 添加裝備界面按鈕
     */
    private void addEquipmentButtons(Inventory gui, Player player) {
        // 統計按鈕 - 移到第6行中間
        ItemStack statsButton = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsButton.getItemMeta();
        if (statsMeta != null) {
            statsMeta.setDisplayName("§6§l屬性統計");

            List<String> statsLore = new ArrayList<>();
            statsLore.add("§8查看當前裝備提供的屬性");
            statsLore.add("");

            // 計算總屬性
            Map<EquipmentAttribute, Double> totalAttrs = calculateTotalAttributes(player);
            if (totalAttrs.isEmpty()) {
                statsLore.add("§7目前沒有裝備任何物品");
            } else {
                for (Map.Entry<EquipmentAttribute, Double> entry : totalAttrs.entrySet()) {
                    EquipmentAttribute attr = entry.getKey();
                    double value = entry.getValue();
                    if (value > 0) {
                        String valueStr = attr.formatValue(value);
                        statsLore.add(attr.getColor() + attr.getDisplayName() + ": §f+" + valueStr);
                    }
                }
            }

            statsMeta.setLore(statsLore);
            statsButton.setItemMeta(statsMeta);
        }
        gui.setItem(49, statsButton); // 第6行第5個位置

        // 關閉按鈕 - 移到第6行最右邊
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName("§c關閉");
            List<String> closeLore = new ArrayList<>();
            closeLore.add("§7點擊關閉裝備界面");
            closeMeta.setLore(closeLore);
            closeButton.setItemMeta(closeMeta);
        }
        gui.setItem(53, closeButton); // 第6行最後一個位置

        // 添加說明文字
        ItemStack helpButton = new ItemStack(Material.PAPER);
        ItemMeta helpMeta = helpButton.getItemMeta();
        if (helpMeta != null) {
            helpMeta.setDisplayName("§e§l操作說明");

            List<String> helpLore = new ArrayList<>();
            helpLore.add("§7如何使用裝備系統:");
            helpLore.add("");
            helpLore.add("§e1. §7將裝備拖拽到右側灰色槽位");
            helpLore.add("§e2. §7左鍵點擊已裝備物品可卸除");
            helpLore.add("§e3. §7右鍵點擊已裝備物品進行強化");
            helpLore.add("§e4. §7Shift+右鍵進行符文鑲嵌");
            helpLore.add("");
            helpLore.add("§a提示: §7左側顯示當前裝備");
            helpLore.add("§a提示: §7右側為放置新裝備的區域");

            helpMeta.setLore(helpLore);
            helpButton.setItemMeta(helpMeta);
        }
        gui.setItem(45, helpButton); // 第6行第1個位置
    }

    /**
     * 計算玩家總屬性
     */
    private Map<EquipmentAttribute, Double> calculateTotalAttributes(Player player) {
        Map<EquipmentSlot, EquipmentData> equipment = equipmentManager.getPlayerEquipment(player.getUniqueId());
        Map<EquipmentAttribute, Double> totalAttributes = new HashMap<>();

        // 基礎裝備屬性
        for (EquipmentData equip : equipment.values()) {
            if (equip != null) {
                for (Map.Entry<EquipmentAttribute, Double> entry : equip.getAllAttributes().entrySet()) {
                    totalAttributes.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
            }
        }

        // 套裝效果（簡化版）
        Map<String, Integer> setCount = new HashMap<>();
        for (EquipmentData equip : equipment.values()) {
            if (equip != null && equip.getSetId() != null && !equip.getSetId().isEmpty()) {
                setCount.merge(equip.getSetId(), 1, Integer::sum);
            }
        }

        return totalAttributes;
    }

    /**
     * 添加強化信息
     */
    private void addEnhanceInfo(Inventory gui, Player player, EquipmentData equipment) {
        // 強化材料顯示
        ItemStack materialItem = new ItemStack(Material.EMERALD);
        ItemMeta meta = materialItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a強化材料");

            List<String> lore = new ArrayList<>();
            lore.add("§7當前強化等級: §e+" + equipment.getEnhanceLevel());
            lore.add("§7最大強化等級: §e+" + equipment.getRarity().getMaxEnhanceLevel());
            lore.add("");

            if (equipment.getEnhanceLevel() < equipment.getRarity().getMaxEnhanceLevel()) {
                int cost = calculateEnhanceCost(equipment);
                lore.add("§6下級強化需要:");
                lore.add("§7- 綠寶石 x" + cost);
                lore.add("§7- 金錢: §e" + (cost * 100) + "G");
                lore.add("");
                lore.add("§a左鍵點擊強化");
            } else {
                lore.add("§c已達最大強化等級");
            }

            meta.setLore(lore);
            materialItem.setItemMeta(meta);
        }
        gui.setItem(31, materialItem);
    }

    /**
     * 計算強化消耗
     */
    private int calculateEnhanceCost(EquipmentData equipment) {
        int baseLevel = equipment.getEnhanceLevel();
        int rarityMultiplier = equipment.getRarity().getLevel();
        return (baseLevel + 1) * rarityMultiplier;
    }

    /**
     * 添加強化按鈕
     */
    private void addEnhanceButtons(Inventory gui, Player player, EquipmentData equipment) {
        // 返回按鈕
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta meta = backButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7返回裝備界面");
            backButton.setItemMeta(meta);
        }
        gui.setItem(36, backButton);
    }

    /**
     * 添加符文槽位
     */
    private void addRuneSlots(Inventory gui, EquipmentData equipment) {
        int maxSlots = equipment.getMaxRuneSlots();
        List<RuneData> equippedRunes = equipment.getEquippedRunes();

        int[] runeSlotPositions = {28, 29, 30, 31}; // 4個符文槽位

        for (int i = 0; i < maxSlots && i < runeSlotPositions.length; i++) {
            int position = runeSlotPositions[i];
            RuneData rune = i < equippedRunes.size() ? equippedRunes.get(i) : null;

            ItemStack slotItem;
            if (rune != null) {
                slotItem = createRuneItem(rune);
            } else {
                slotItem = createEmptyRuneSlot(i);
            }

            gui.setItem(position, slotItem);
        }
    }

    /**
     * 創建符文物品
     */
    private ItemStack createRuneItem(RuneData rune) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(rune.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add("§8" + rune.getType().getDisplayName() + " | " + rune.getRarity().getDisplayName());
            if (!rune.getDescription().isEmpty()) {
                lore.add("§7" + rune.getDescription());
            }
            lore.add("");

            // 符文屬性
            for (Map.Entry<EquipmentAttribute, Double> entry : rune.getAttributes().entrySet()) {
                EquipmentAttribute attr = entry.getKey();
                double value = rune.getAttribute(attr);
                if (value > 0) {
                    String valueStr = attr.formatValue(value);
                    lore.add(attr.getColor() + attr.getDisplayName() + ": §f+" + valueStr);
                }
            }

            if (!rune.getSpecialEffect().isEmpty()) {
                lore.add("");
                lore.add("§6特殊效果: §f" + rune.getSpecialEffect());
            }

            lore.add("");
            lore.add("§c左鍵移除符文");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 創建空符文槽
     */
    private ItemStack createEmptyRuneSlot(int slotIndex) {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§7符文槽位 " + (slotIndex + 1));

            List<String> lore = new ArrayList<>();
            lore.add("§8空符文槽位");
            lore.add("");
            lore.add("§e點擊下方符文進行鑲嵌");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 添加可用符文
     */
    private void addAvailableRunes(Inventory gui, Player player) {
        // 這裡應該從玩家背包或符文庫存中獲取可用符文
        // 暫時使用模板符文作為示例
        Map<String, RuneData> runeTemplates = equipmentManager.getRuneTemplates();

        int[] availableRunePositions = {45, 46, 47, 48, 50, 51, 52}; // 底部可用符文
        int index = 0;

        for (RuneData template : runeTemplates.values()) {
            if (index >= availableRunePositions.length) break;

            RuneData rune = equipmentManager.createRune(template.getId());
            if (rune != null) {
                ItemStack runeItem = createRuneItem(rune);

                // 修改Lore
                ItemMeta meta = runeItem.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.getLore();
                    if (lore != null) {
                        lore.removeIf(line -> line.contains("左鍵移除"));
                        lore.add("§a左鍵鑲嵌符文");
                        meta.setLore(lore);
                        runeItem.setItemMeta(meta);
                    }
                }

                gui.setItem(availableRunePositions[index], runeItem);
                index++;
            }
        }
    }

    /**
     * 處理GUI點擊事件
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!openInventories.containsKey(player.getUniqueId())) return;

        EquipmentGUIType guiType = currentGUIType.get(player.getUniqueId());
        if (guiType == null) return;

        int slot = event.getRawSlot();
        ClickType clickType = event.getClick();

        switch (guiType) {
            case EQUIPMENT:
                handleEquipmentClick(player, slot, clickType, event);
                break;
            case ENHANCE:
                event.setCancelled(true);
                handleEnhanceClick(player, slot, clickType);
                break;
            case RUNE:
                event.setCancelled(true);
                handleRuneClick(player, slot, clickType);
                break;
        }
    }

    /**
     * 處理裝備界面點擊
     */
    private void handleEquipmentClick(Player player, int slot, ClickType clickType, InventoryClickEvent event) {
        // 所有 GUI 範圍內的點擊默認取消，除非特定情況允許
        if (slot >= 0 && slot < 54) {
            event.setCancelled(true);
        }

        // 功能按鈕區域
        if (slot == 53) { // 關閉按鈕
            player.closeInventory();
            return;
        }

        if (slot == 49) { // 統計按鈕
            return;
        }

        if (slot == 45) { // 說明按鈕
            return;
        }

        // 檢查是否點擊了顯示槽位
        EquipmentSlot equipSlot = getSlotFromDisplayPosition(slot);
        if (equipSlot != null) {
            // 獲取當前裝備
            Map<EquipmentSlot, EquipmentData> currentEquipment = equipmentManager.getPlayerEquipment(player.getUniqueId());
            EquipmentData equip = currentEquipment.get(equipSlot);

            if (clickType == ClickType.LEFT) {
                // 卸除裝備
                if (equip != null) {
                    EquipmentData removed = equipmentManager.unequipItem(player, equipSlot);
                    if (removed != null) {
                        // 檢查背包是否有空間
                        if (player.getInventory().firstEmpty() != -1) {
                            player.getInventory().addItem(removed.toItemStack());
                            player.sendMessage("§a已卸除 " + removed.getName());
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
                        } else {
                            // 背包滿了，將裝備掉落到地上
                            player.getWorld().dropItemNaturally(player.getLocation(), removed.toItemStack());
                            player.sendMessage("§e背包已滿！" + removed.getName() + " 已掉落到地上");
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
                        }

                        // 延遲刷新GUI，確保inventory操作完成
                        refreshCurrentGUI(player);
                    } else {
                        player.sendMessage("§c卸除裝備失敗！");
                    }
                } else {
                    player.sendMessage("§c該槽位沒有裝備！");
                }
            } else if (clickType == ClickType.RIGHT) {
                // 打開強化界面
                if (equip != null) {
                    openEnhanceGUI(player, equipSlot);
                } else {
                    player.sendMessage("§c該槽位沒有裝備可以強化！");
                }
            } else if (clickType == ClickType.SHIFT_RIGHT) {
                // 打開符文界面
                if (equip != null) {
                    openRuneGUI(player, equipSlot);
                } else {
                    player.sendMessage("§c該槽位沒有裝備可以鑲嵌符文！");
                }
            }
            return;
        }

        // 檢查是否點擊了放置槽位
        EquipmentSlot placeSlot = getSlotFromPlacePosition(slot);
        if (placeSlot != null) {
            // 允許放置物品到放置槽位
            handleEquipmentPlacement(player, placeSlot, event);
            return;
        }

        // 處理玩家背包區域的 Shift+點擊
        if (slot >= 54 && clickType.isShiftClick()) {
            // 延遲一點點時間，讓物品移動完成後再檢查同步
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // 如果玩家還開著 GUI，刷新它
                if (isUsingEquipmentGUI(player)) {
                    refreshCurrentGUI(player);
                }
            }, 1L);
            return;
        }

        // 檢查是否在背景區域
        if (isBackgroundSlot(slot)) {
            event.setCancelled(true);
            return;
        }

        // 處理玩家背包區域的點擊
        if (slot >= 54) { // 玩家背包區域
            // 如果是 Shift+Click，嘗試自動裝備
            if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                    // 嘗試找到合適的槽位
                    EquipmentSlot suitableSlot = findSuitableSlot(clickedItem);
                    if (suitableSlot != null) {
                        event.setCancelled(true);

                        Map<EquipmentSlot, EquipmentData> currentEquipment = equipmentManager.getPlayerEquipment(player.getUniqueId());
                        EquipmentData existingEquip = currentEquipment.get(suitableSlot);

                        if (tryEquipItem(player, suitableSlot, clickedItem)) {
                            // 處理舊裝備
                            if (existingEquip != null) {
                                if (player.getInventory().firstEmpty() != -1) {
                                    player.getInventory().addItem(existingEquip.toItemStack());
                                } else {
                                    player.getWorld().dropItemNaturally(player.getLocation(), existingEquip.toItemStack());
                                    player.sendMessage("§e背包已滿！舊裝備已掉落到地上");
                                }
                            }

                            // 從背包移除物品
                            clickedItem.setAmount(0);

                            // 延遲刷新GUI
                            refreshCurrentGUI(player);
                        }
                        return;
                    }
                }
            }
            // 其他背包操作允許正常進行
            return;
        }
    }

    /**
     * 處理裝備放置
     */
    private void handleEquipmentPlacement(Player player, EquipmentSlot targetSlot, InventoryClickEvent event) {
        // 始終取消事件，防止物品真的被放入GUI
        event.setCancelled(true);

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // 如果游標有物品，嘗試裝備
        if (cursor != null && cursor.getType() != Material.AIR) {
            // 檢查是否已經有裝備在該槽位
            Map<EquipmentSlot, EquipmentData> currentEquipment = equipmentManager.getPlayerEquipment(player.getUniqueId());
            EquipmentData existingEquip = currentEquipment.get(targetSlot);

            if (tryEquipItem(player, targetSlot, cursor)) {
                // 成功裝備，處理舊裝備
                if (existingEquip != null) {
                    // 如果有舊裝備，將其放入背包或掉落
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(existingEquip.toItemStack());
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), existingEquip.toItemStack());
                        player.sendMessage("§e背包已滿！舊裝備已掉落到地上");
                    }
                }

                // 清空游標
                event.setCursor(null);

                // 立即清空放置槽位，恢復為提示物品
                Inventory inv = event.getInventory();
                int placePosition = PLACE_POSITIONS.get(targetSlot);
                inv.setItem(placePosition, createPlaceSlotItem(targetSlot));

                // 刷新GUI
                refreshCurrentGUI(player);
            } else {
                // 裝備失敗，確保放置區恢復正常
                Inventory inv = event.getInventory();
                int placePosition = PLACE_POSITIONS.get(targetSlot);
                inv.setItem(placePosition, createPlaceSlotItem(targetSlot));
            }
            return;
        }

        // 如果是拖拽操作
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.PLACE_ALL ||
            event.getAction() == org.bukkit.event.inventory.InventoryAction.PLACE_ONE ||
            event.getAction() == org.bukkit.event.inventory.InventoryAction.PLACE_SOME) {

            if (cursor != null && cursor.getType() != Material.AIR) {
                Map<EquipmentSlot, EquipmentData> currentEquipment = equipmentManager.getPlayerEquipment(player.getUniqueId());
                EquipmentData existingEquip = currentEquipment.get(targetSlot);

                if (tryEquipItem(player, targetSlot, cursor)) {
                    if (existingEquip != null) {
                        if (player.getInventory().firstEmpty() != -1) {
                            player.getInventory().addItem(existingEquip.toItemStack());
                        } else {
                            player.getWorld().dropItemNaturally(player.getLocation(), existingEquip.toItemStack());
                            player.sendMessage("§e背包已滿！舊裝備已掉落到地上");
                        }
                    }

                    event.setCursor(null);

                    // 立即清空放置槽位
                    Inventory inv = event.getInventory();
                    int placePosition = PLACE_POSITIONS.get(targetSlot);
                    inv.setItem(placePosition, createPlaceSlotItem(targetSlot));

                    // 刷新GUI
                    refreshCurrentGUI(player);
                } else {
                    // 裝備失敗，確保放置區恢復正常
                    Inventory inv = event.getInventory();
                    int placePosition = PLACE_POSITIONS.get(targetSlot);
                    inv.setItem(placePosition, createPlaceSlotItem(targetSlot));
                }
            }
            event.setCancelled(true);
            return;
        }

        // 取消其他操作
        event.setCancelled(true);
    }

    /**
     * 處理拖拽事件
     */
    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!openInventories.containsKey(player.getUniqueId())) return;

        EquipmentGUIType guiType = currentGUIType.get(player.getUniqueId());
        if (guiType != EquipmentGUIType.EQUIPMENT) return;

        // 檢查拖拽的槽位
        Set<Integer> slots = event.getRawSlots();
        for (int slot : slots) {
            if (slot >= 0 && slot < 54) { // 在GUI範圍內
                // 只允許拖拽到放置槽位
                if (getSlotFromPlacePosition(slot) == null) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /**
     * 找到適合物品的裝備槽位
     */
    private EquipmentSlot findSuitableSlot(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        Material material = item.getType();
        String materialName = material.name();

        // 檢查各種裝備類型
        if (materialName.endsWith("_HELMET")) {
            return EquipmentSlot.HELMET;
        } else if (materialName.endsWith("_CHESTPLATE")) {
            return EquipmentSlot.CHESTPLATE;
        } else if (materialName.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGGINGS;
        } else if (materialName.endsWith("_BOOTS")) {
            return EquipmentSlot.BOOTS;
        } else if (materialName.endsWith("_SWORD") || materialName.endsWith("_AXE") ||
                   materialName.endsWith("_PICKAXE") || material == Material.STICK) {
            return EquipmentSlot.MAIN_HAND;
        } else if (material == Material.SHIELD) {
            return EquipmentSlot.OFF_HAND;
        }

        return null; // 無法識別的物品類型
    }

    /**
     * 嘗試裝備物品
     */
    private boolean tryEquipItem(Player player, EquipmentSlot slot, ItemStack item) {
        if (!isValidEquipmentForSlot(item, slot)) {
            player.sendMessage("§c此物品不適合放在 " + slot.getDisplayName() + " 槽位！");
            return false;
        }

        // 將物品轉換為EquipmentData
        EquipmentData equipment = convertItemToEquipment(item, slot);
        if (equipment == null) {
            player.sendMessage("§c無法識別此裝備！");
            return false;
        }

        // 檢查是否有舊裝備需要卸除
        Map<EquipmentSlot, EquipmentData> currentEquipment = equipmentManager.getPlayerEquipment(player.getUniqueId());
        EquipmentData oldEquipment = currentEquipment.get(slot);

        // 嘗試裝備
        boolean success = equipmentManager.equipItem(player, slot, equipment);
        if (success) {
            player.sendMessage("§a已裝備 " + equipment.getName() + " 到 " + slot.getDisplayName());
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);

            // 如果有舊裝備，返回給玩家（但不在這裡處理，由調用方處理）

            return true;
        } else {
            player.sendMessage("§c無法裝備此物品！");
            return false;
        }
    }

    /**
     * 檢查物品是否適合特定槽位
     */
    private boolean isValidEquipmentForSlot(ItemStack item, EquipmentSlot slot) {
        if (item == null || item.getType() == Material.AIR) return false;

        Material material = item.getType();

        switch (slot) {
            case HELMET:
                return material.name().endsWith("_HELMET");
            case CHESTPLATE:
                return material.name().endsWith("_CHESTPLATE");
            case LEGGINGS:
                return material.name().endsWith("_LEGGINGS");
            case BOOTS:
                return material.name().endsWith("_BOOTS");
            case MAIN_HAND:
                return material.name().endsWith("_SWORD") ||
                       material.name().endsWith("_AXE") ||
                       material.name().endsWith("_PICKAXE") ||
                       material == Material.STICK; // 法杖
            case OFF_HAND:
                return material == Material.SHIELD ||
                       material.name().endsWith("_SWORD");
            default:
                return true; // 飾品和特殊槽位暫時允許所有物品
        }
    }

    /**
     * 將物品轉換為EquipmentData
     */
    private EquipmentData convertItemToEquipment(ItemStack item, EquipmentSlot slot) {
        // 檢查是否是自定義裝備（有特殊NBT或displayName）
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();

            // 嘗試從已有模板中找到匹配的裝備
            for (EquipmentData template : equipmentManager.getEquipmentTemplates().values()) {
                if (template.getMaterial() == item.getType() &&
                    template.getSlot() == slot) {

                    // 檢查名稱是否匹配（移除顏色代碼後比較）
                    String templateName = org.bukkit.ChatColor.stripColor(template.getName());
                    String itemName = org.bukkit.ChatColor.stripColor(displayName);

                    if (itemName.contains(templateName) || templateName.contains(itemName)) {
                        return equipmentManager.createEquipment(template.getId());
                    }
                }
            }
        }

        // 如果不是自定義裝備，創建一個基本的裝備數據
        String itemId = "basic_" + item.getType().name().toLowerCase();
        String itemName = "§f" + item.getType().name().replace("_", " ");

        EquipmentData equipment = new EquipmentData(
            itemId, itemName, slot,
            com.customrpg.equipment.EquipmentRarity.COMMON,
            item.getType()
        );

        // 根據物品類型添加基本屬性
        addBasicAttributes(equipment, item.getType());

        return equipment;
    }

    /**
     * 為基本物品添加基本屬性
     */
    private void addBasicAttributes(EquipmentData equipment, Material material) {
        Map<com.customrpg.equipment.EquipmentAttribute, Double> attributes = new HashMap<>();

        // 根據材質添加基本屬性
        String materialName = material.name();

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
    }

    /**
     * 處理強化界面點擊
     */
    private void handleEnhanceClick(Player player, int slot, ClickType clickType) {
        if (slot == 36) { // 返回按鈕
            openEquipmentGUI(player);
            return;
        }

        if (slot == 31 && clickType == ClickType.LEFT) { // 強化按鈕
            EquipmentData equipment = selectedEquipment.get(player.getUniqueId());
            if (equipment != null && tryEnhanceEquipment(player, equipment)) {
                player.sendMessage("§a裝備強化成功！");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    refreshCurrentGUI(player);
                }, 1L);
            }
        }
    }

    /**
     * 處理符文界面點擊
     */
    private void handleRuneClick(Player player, int slot, ClickType clickType) {
        // 實現符文鑲嵌邏輯
        player.sendMessage("§e符文系統功能開發中...");
    }

    /**
     * 根據顯示位置獲取裝備槽位
     */
    private EquipmentSlot getSlotFromDisplayPosition(int position) {
        for (Map.Entry<EquipmentSlot, Integer> entry : SLOT_POSITIONS.entrySet()) {
            if (entry.getValue() == position) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 根據放置位置獲取裝備槽位
     */
    private EquipmentSlot getSlotFromPlacePosition(int position) {
        for (Map.Entry<EquipmentSlot, Integer> entry : PLACE_POSITIONS.entrySet()) {
            if (entry.getValue() == position) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 檢查是否為背景槽位
     */
    private boolean isBackgroundSlot(int position) {
        // GUI前5行（0-44）的背景區域
        if (position >= 0 && position < 45) {
            // 不是顯示槽位也不是放置槽位
            return !SLOT_POSITIONS.containsValue(position) &&
                   !PLACE_POSITIONS.containsValue(position);
        }

        // 第6行的特定位置
        if (position >= 45 && position < 54) {
            return position != 45 && position != 49 && position != 53; // 不是說明按鈕、統計按鈕和關閉按鈕
        }

        return false;
    }

    /**
     * 根據位置獲取裝備槽位（舊版本兼容）
     */
    private EquipmentSlot getSlotFromPosition(int position) {
        // 先檢查顯示槽位
        EquipmentSlot slot = getSlotFromDisplayPosition(position);
        if (slot != null) return slot;

        // 再檢查放置槽位
        return getSlotFromPlacePosition(position);
    }

    /**
     * 嘗試強化裝備
     */
    private boolean tryEnhanceEquipment(Player player, EquipmentData equipment) {
        int currentLevel = equipment.getEnhanceLevel();
        int maxLevel = equipment.getRarity().getMaxEnhanceLevel();

        if (currentLevel >= maxLevel) {
            player.sendMessage("§c裝備已達最大強化等級！");
            return false;
        }

        int cost = calculateEnhanceCost(equipment);

        // 檢查材料（簡化版本，只檢查綠寶石）
        if (!player.getInventory().containsAtLeast(new ItemStack(Material.EMERALD), cost)) {
            player.sendMessage("§c強化材料不足！需要 " + cost + " 個綠寶石");
            return false;
        }

        // 消耗材料
        ItemStack emerald = new ItemStack(Material.EMERALD, cost);
        player.getInventory().removeItem(emerald);

        // 強化裝備
        equipment.enhance();

        return true;
    }

    /**
     * 刷新當前GUI
     */
    public void refreshCurrentGUI(Player player) {
        EquipmentGUIType guiType = currentGUIType.get(player.getUniqueId());
        if (guiType != null) {
            // 標記為正在刷新，防止關閉事件清除數據
            UUID uuid = player.getUniqueId();
            refreshingPlayers.add(uuid);

            // 延遲打開新GUI，確保舊GUI已經正確關閉
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                openGUI(player, guiType);
                refreshingPlayers.remove(uuid);
            }, 1L);
        }
    }

    // 用於追蹤正在刷新GUI的玩家，避免 close 事件清除數據
    private final Set<UUID> refreshingPlayers = new HashSet<>();

    /**
     * 檢查玩家是否正在使用任何裝備相關GUI
     */
    public boolean isUsingEquipmentGUI(Player player) {
        return currentGUIType.containsKey(player.getUniqueId());
    }

    /**
     * 處理GUI關閉事件
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            UUID uuid = player.getUniqueId();

            // 如果玩家正在刷新GUI，不清除數據
            if (refreshingPlayers.contains(uuid)) {
                return;
            }

            if (openInventories.containsKey(uuid)) {
                openInventories.remove(uuid);
                currentGUIType.remove(uuid);
                selectedSlot.remove(uuid);
                selectedEquipment.remove(uuid);
            }
        }
    }

    /**
     * 清理資源
     */
    public void cleanup() {
        openInventories.clear();
        currentGUIType.clear();
        selectedSlot.clear();
        selectedEquipment.clear();
        refreshingPlayers.clear();
    }
}


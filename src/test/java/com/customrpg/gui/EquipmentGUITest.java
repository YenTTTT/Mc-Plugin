package com.customrpg.gui;

import com.customrpg.CustomRPG;
import com.customrpg.equipment.EquipmentManager;
import com.customrpg.equipment.EquipmentData;
import com.customrpg.equipment.EquipmentSlot;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.players.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.EnumMap;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EquipmentGUITest {
    CustomRPG plugin;
    EquipmentManager equipment;
    EquipmentGUI gui;
    Player player;
    Inventory top;
    InventoryView view;
    UUID uuid;

    @BeforeEach void setup() throws Exception {
        plugin = mock(CustomRPG.class);
        equipment = mock(EquipmentManager.class);
        gui = new EquipmentGUI(plugin, equipment);
        player = mock(Player.class);
        uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        top = mock(Inventory.class);
        when(top.getSize()).thenReturn(54);
        view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        map("openInventories").put(uuid, top);
        map("currentGUIType").put(uuid, EquipmentGUI.EquipmentGUIType.EQUIPMENT);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Object> map(String name) throws Exception {
        var field = EquipmentGUI.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(gui);
    }

    private InventoryClickEvent click(int rawSlot, ClickType type, InventoryAction action) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getClick()).thenReturn(type);
        when(event.getAction()).thenReturn(action);
        return event;
    }

    @Test void handDisplayCannotGenerateDuplicateItem() {
        InventoryClickEvent event = click(19, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.onInventoryClick(event);
        verify(event).setCancelled(true);
        verifyNoInteractions(equipment);
    }

    @Test void shiftClickWeaponCannotEnterDisplayInventory() {
        InventoryClickEvent event = click(54, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.IRON_SWORD);
        when(event.getCurrentItem()).thenReturn(item);
        gui.onInventoryClick(event);
        verify(event).setCancelled(true);
        verify(item, never()).setAmount(anyInt());
    }

    @Test void collectFromPlayerInventoryCannotExtractDisplayIcons() {
        InventoryClickEvent event = click(60, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR);
        gui.onInventoryClick(event);
        verify(event).setCancelled(true);
    }

    @Test void failedEquipLeavesCursorAndBackingItemUnchanged() {
        InventoryClickEvent event = click(13, ClickType.LEFT, InventoryAction.PLACE_ALL);
        ItemStack source = mock(ItemStack.class);
        when(source.getType()).thenReturn(Material.IRON_HELMET);
        when(event.getCursor()).thenReturn(source);
        when(equipment.equipFromItem(player, com.customrpg.equipment.EquipmentSlot.HELMET, source))
                .thenReturn(new EquipmentManager.EquipResult(false, null, null));
        gui.onInventoryClick(event);
        verify(event, never()).setCursor(any());
        verify(source, never()).setAmount(anyInt());
        verify(equipment, never()).savePlayerEquipment(any());
    }

    @Test void dragIntoEquipmentPlacementOrEnhancementSlotsIsCancelled() throws Exception {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlots()).thenReturn(Set.of(13, 60));
        gui.onInventoryDrag(event);
        verify(event).setCancelled(true);
        reset(event);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlots()).thenReturn(Set.of(13));
        map("currentGUIType").put(uuid, EquipmentGUI.EquipmentGUIType.ENHANCE);
        gui.onInventoryDrag(event);
        verify(event).setCancelled(true);
    }

    @Test void unrelatedInventoryIsIgnored() {
        when(view.getTopInventory()).thenReturn(mock(Inventory.class));
        InventoryClickEvent event = click(19, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.onInventoryClick(event);
        verify(event, never()).setCancelled(anyBoolean());
        verifyNoInteractions(equipment);
    }

    @Test void closingWhileRefreshPendingDoesNotReopenInventory() {
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            gui.refreshCurrentGUI(player);
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskLater(eq(plugin), task.capture(), eq(1L));
            InventoryCloseEvent close = mock(InventoryCloseEvent.class);
            when(close.getPlayer()).thenReturn(player);
            when(close.getInventory()).thenReturn(top);
            gui.onInventoryClose(close);
            when(player.isOnline()).thenReturn(true);
            task.getValue().run();
            assertFalse(gui.isUsingEquipmentGUI(player));
            verify(player, never()).openInventory(any(Inventory.class));
        }
    }

    @Test void rendersEmptyAndEquippedHandSlotsWithoutPlacementSlots() throws Exception {
        PlayerStatsManager stats = mock(PlayerStatsManager.class);
        when(plugin.getPlayerStatsManager()).thenReturn(stats);
        when(stats.getStats(player)).thenReturn(new PlayerStats());
        EquipmentGUI rendered = new EquipmentGUI(plugin, equipment);
        Map<EquipmentSlot, EquipmentData> items = new EnumMap<>(EquipmentSlot.class);
        when(equipment.getPlayerEquipment(uuid)).thenReturn(items);
        var create = EquipmentGUI.class.getDeclaredMethod("createEquipmentGUI", Player.class);
        create.setAccessible(true);
        try (var bukkit = mockStatic(Bukkit.class);
             var stacks = mockConstruction(ItemStack.class)) {
            bukkit.when(() -> Bukkit.createInventory(null, 54, EquipmentGUI.EquipmentGUIType.EQUIPMENT.getTitle()))
                    .thenReturn(top);
            assertSame(top, create.invoke(rendered, player));
            EquipmentData hand = mock(EquipmentData.class);
            ItemStack icon = mock(ItemStack.class);
            items.put(EquipmentSlot.MAIN_HAND, hand);
            when(hand.toItemStack(anyInt())).thenReturn(icon);
            assertSame(top, create.invoke(rendered, player));
            verify(top).setItem(19, icon);
        }
    }
}

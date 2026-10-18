package com.customrpg.equipment;

import com.customrpg.CustomRPG;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.players.PlayerStats;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EquipmentManagerTest {
    @TempDir Path directory;
    EquipmentManager manager;
    Player player;
    PlayerInventory inventory;

    @BeforeEach void setup() throws Exception {
        Path configs = Files.createDirectories(directory.resolve("config/equipment"));
        for (String name : new String[]{"armors", "accessories", "sets", "runes"}) {
            Files.writeString(configs.resolve(name + ".yml"), "{}");
        }
        CustomRPG plugin = mock(CustomRPG.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        PlayerStatsManager stats = mock(PlayerStatsManager.class);
        when(plugin.getPlayerStatsManager()).thenReturn(stats);
        when(stats.getStats(any(Player.class))).thenReturn(new PlayerStats());
        manager = spy(new EquipmentManager(plugin));
        doNothing().when(manager).updatePlayerAttributes(any());
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
    }

    private ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }

    @Test void armorExchangeAndRemovalReturnActualObjects() {
        ItemStack source = item(Material.IRON_HELMET);
        ItemStack single = item(Material.IRON_HELMET);
        ItemStack previous = item(Material.DIAMOND_HELMET);
        when(source.clone()).thenReturn(single);
        when(inventory.getHelmet()).thenReturn(previous);
        EquipmentManager.EquipResult result = manager.equipFromItem(player, EquipmentSlot.HELMET, source);
        assertTrue(result.success());
        assertSame(previous, result.previousItem());
        verify(inventory).setHelmet(single);
        verify(single).setAmount(1);
        verify(source, never()).setItemMeta(any());
        when(inventory.getHelmet()).thenReturn(single);
        assertSame(single, manager.unequipToItem(player, EquipmentSlot.HELMET));
        assertNull(manager.unequipToItem(player, EquipmentSlot.MAIN_HAND));
        verify(inventory, never()).setItemInMainHand(any());
    }

    @Test void storedAccessoryCanOnlyBeRemovedOnce() {
        ItemStack source = item(Material.GOLD_NUGGET);
        ItemStack single = item(Material.GOLD_NUGGET);
        when(source.clone()).thenReturn(single);
        assertTrue(manager.equipFromItem(player, EquipmentSlot.RING_1, source).success());
        assertSame(single, manager.unequipToItem(player, EquipmentSlot.RING_1));
        assertNull(manager.unequipToItem(player, EquipmentSlot.RING_1));
    }

    @Test void handSlotsCannotConsumeOrGenerateItems() {
        ItemStack source = item(Material.IRON_SWORD);
        assertFalse(manager.equipFromItem(player, EquipmentSlot.MAIN_HAND, source).success());
        assertFalse(manager.equipFromItem(player, EquipmentSlot.OFF_HAND, source).success());
        verify(source, never()).clone();
        verifyNoInteractions(inventory);
    }

    @Test void renamedVanillaItemCannotImpersonateTemplate() {
        EquipmentData template = new EquipmentData("test", "Test Helmet", EquipmentSlot.HELMET,
                EquipmentRarity.COMMON, Material.IRON_HELMET);
        manager.getEquipmentTemplates().put("test", template);
        ItemStack item = item(Material.IRON_HELMET);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("Test");
        assertNull(manager.findTemplate(item, EquipmentSlot.HELMET));
        when(meta.hasLore()).thenReturn(true);
        assertNull(manager.findTemplate(item, EquipmentSlot.HELMET));
        when(meta.getDisplayName()).thenReturn("Test Helmet");
        assertSame(template, manager.findTemplate(item, EquipmentSlot.HELMET));
    }

    @Test void pdcRestoresEnhancementAndAcceptsRenamedEquipment() {
        EquipmentData template = new EquipmentData("test", "Test Helmet", EquipmentSlot.HELMET,
                EquipmentRarity.COMMON, Material.IRON_HELMET);
        template.getBaseAttributes().put(EquipmentAttribute.DEFENSE, 10.0);
        manager.getEquipmentTemplates().put("test", template);
        ItemStack item = item(Material.IRON_HELMET);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(EquipmentData.PDC_EQUIPMENT_ID, PersistentDataType.STRING)).thenReturn("test");
        when(data.get(EquipmentData.PDC_ENHANCE_LEVEL, PersistentDataType.INTEGER)).thenReturn(2);
        EquipmentData loaded = manager.toEquipmentData(item, EquipmentSlot.HELMET);
        assertEquals(2, loaded.getEnhanceLevel());
        assertEquals(12.0, loaded.getTotalAttribute(EquipmentAttribute.DEFENSE));
        assertEquals(0, template.getEnhanceLevel());
    }

    @Test void corruptStoredFileIsPreservedAndRejectsNewDeposits() throws Exception {
        Path folder = Files.createDirectories(directory.resolve("data/equipment"));
        Path file = folder.resolve(player.getUniqueId() + ".yml");
        String broken = "stored: [unterminated";
        Files.writeString(file, broken);
        manager.loadPlayerEquipment(player);
        ItemStack source = item(Material.GOLD_NUGGET);
        assertFalse(manager.equipFromItem(player, EquipmentSlot.RING_1, source).success());
        manager.unloadPlayerEquipment(player.getUniqueId());
        assertEquals(broken, Files.readString(file));
        verify(source, never()).clone();
    }

    @Test void validYamlWithInvalidStoredItemIsNotSilentlyEmptied() throws Exception {
        Path folder = Files.createDirectories(directory.resolve("data/equipment"));
        Path file = folder.resolve(player.getUniqueId() + ".yml");
        String invalid = "stored:\n  RING_1: not-an-item\n";
        Files.writeString(file, invalid);
        manager.loadPlayerEquipment(player);
        manager.savePlayerEquipment(player.getUniqueId());
        assertEquals(invalid, Files.readString(file));
        assertFalse(manager.equipFromItem(player, EquipmentSlot.RING_1, item(Material.GOLD_NUGGET)).success());
    }

    @Test void failedLogoutSaveRetainsAuthoritativeItemForReconnect() {
        ItemStack source = item(Material.GOLD_NUGGET);
        ItemStack single = item(Material.GOLD_NUGGET);
        when(source.clone()).thenReturn(single);
        assertTrue(manager.equipFromItem(player, EquipmentSlot.RING_1, source).success());
        try (var stats = mockStatic(PlayerStatsManager.class)) {
            stats.when(() -> PlayerStatsManager.saveAtomically(any(), any()))
                    .thenThrow(new IOException("disk full"));
            manager.unloadPlayerEquipment(player.getUniqueId());
            manager.loadPlayerEquipment(player);
            assertSame(single, manager.unequipToItem(player, EquipmentSlot.RING_1));
            assertNull(manager.unequipToItem(player, EquipmentSlot.RING_1));
        }
    }

    @Test void enhancementWritesOnlyToOriginalItemMetadata() {
        ItemStack original = item(Material.IRON_HELMET);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(inventory.getHelmet()).thenReturn(original);
        when(original.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        EquipmentData equipment = new EquipmentData("test", "Test", EquipmentSlot.HELMET,
                EquipmentRarity.COMMON, Material.IRON_HELMET);
        equipment.enhance();
        manager.persistEnhancement(player, EquipmentSlot.HELMET, equipment);
        verify(data).set(EquipmentData.PDC_ENHANCE_LEVEL, PersistentDataType.INTEGER, 1);
        verify(original).setItemMeta(meta);
        verify(inventory, never()).setHelmet(any());
    }
}

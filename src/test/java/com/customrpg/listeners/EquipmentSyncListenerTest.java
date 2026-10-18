package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.equipment.EquipmentManager;
import com.customrpg.equipment.EquipmentSlot;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumMap;
import java.util.UUID;

import static org.mockito.Mockito.*;

class EquipmentSyncListenerTest {
    @Test void loginRestoresStoredEquipmentAndSynchronizesRealArmor() {
        CustomRPG plugin = mock(CustomRPG.class);
        EquipmentManager manager = mock(EquipmentManager.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack helmet = mock(ItemStack.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(helmet);
        when(manager.getPlayerEquipment(uuid)).thenReturn(new EnumMap<>(EquipmentSlot.class));
        EquipmentSyncListener listener = new EquipmentSyncListener(plugin, manager);
        listener.onPlayerJoin(new PlayerJoinEvent(player, (String) null));
        verify(manager).loadPlayerEquipment(player);
        verify(manager).toEquipmentData(helmet, EquipmentSlot.HELMET);
        listener.onPlayerQuit(new PlayerQuitEvent(player, (String) null));
        verify(manager).unloadPlayerEquipment(uuid);
    }

    @Test void delayedSyncAfterLogoutDoesNotRecreatePlayerCache() {
        CustomRPG plugin = mock(CustomRPG.class);
        EquipmentManager manager = mock(EquipmentManager.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(false);
        EquipmentSyncListener listener = new EquipmentSyncListener(plugin, manager);
        listener.onItemHeld(new PlayerItemHeldEvent(player, 0, 1));
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), task.capture(), eq(1L));
        task.getValue().run();
        verifyNoInteractions(manager);
        verify(player, never()).getInventory();
    }
}

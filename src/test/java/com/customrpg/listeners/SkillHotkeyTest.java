package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.customrpg.players.PlayerTalents;
import com.customrpg.races.RacialSkillManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillHotkeyTest {
    CustomRPG plugin; Player player; PlayerInventory inventory; SkillBarManager manager; SkillBarListener listener;
    TalentSkillManager talents; RacialSkillManager racial; PlayerTalents selected;
    Map<NamespacedKey,Object> preferences;
    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class); player = mock(Player.class); inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.getInventory()).thenReturn(inventory);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL); when(player.isOnline()).thenReturn(true);
        when(inventory.getHeldItemSlot()).thenReturn(5);
        var data = mock(PersistentDataContainer.class); preferences = new HashMap<>(); when(player.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(), any())).thenAnswer(i -> preferences.get(i.getArgument(0)));
        doAnswer(i -> { preferences.put(i.getArgument(0), i.getArgument(2)); return null; }).when(data).set(any(), any(), any());
        var server = mock(Server.class); var scheduler = mock(BukkitScheduler.class); when(plugin.getServer()).thenReturn(server); when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        var talentManager = mock(TalentManager.class); selected = new PlayerTalents(); when(plugin.getTalentManager()).thenReturn(talentManager);
        when(talentManager.getPlayerTalents(player)).thenReturn(selected);
        talents = mock(TalentSkillManager.class); when(plugin.getTalentSkillManager()).thenReturn(talents);
        racial = mock(RacialSkillManager.class); when(plugin.getRacialSkillManager()).thenReturn(racial);
        manager = new SkillBarManager(); listener = new SkillBarListener(plugin, manager);
    }
    @Test void defaultIsEnabledAndOptOutSurvivesFreshManagerAndLogout() {
        assertTrue(manager.isEnabled(player)); assertFalse(manager.toggle(player));
        listener.onQuit(new PlayerQuitEvent(player, (net.kyori.adventure.text.Component)null, null));
        assertFalse(new SkillBarManager().isEnabled(player)); assertTrue(manager.toggle(player));
    }
    @Test void enablingOnReservedSlotMovesSelectionWithoutMovingItems() {
        manager.disable(player); when(inventory.getHeldItemSlot()).thenReturn(0);
        assertTrue(manager.toggle(player)); verify(inventory).setHeldItemSlot(5);
        verify(inventory, never()).setItem(anyInt(), any()); verify(inventory, never()).setContents(any());
    }
    @Test void repeatedFifthKeyAlwaysRoutesToRacialSlotAndKeepsHeldWeapon() {
        var first = new PlayerItemHeldEvent(player, 5, 4); var second = new PlayerItemHeldEvent(player, 5, 4);
        listener.onItemHeld(first); listener.onItemHeld(second);
        assertTrue(first.isCancelled() && second.isCancelled()); verify(racial, times(2)).cast(player);
        verifyNoInteractions(talents); verify(inventory, never()).setHeldItemSlot(anyInt());
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void talentKeysRouteToTheirEquippedSkillWithEmptyHand(int slot) {
        var air = mock(ItemStack.class); when(air.getType()).thenReturn(Material.AIR); when(inventory.getItemInMainHand()).thenReturn(air);
        selected.setSelectedSkill(slot, "star_shatter");
        var event = new PlayerItemHeldEvent(player, 5, slot); listener.onItemHeld(event);
        assertTrue(event.isCancelled()); verify(talents).castSkillDirectly(player, "star_shatter", air); verifyNoInteractions(racial);
    }
    @Test void emptyTalentSlotDoesNotCastAnotherSkill() {
        var event = new PlayerItemHeldEvent(player, 5, 0); listener.onItemHeld(event);
        assertTrue(event.isCancelled()); verifyNoInteractions(talents, racial);
    }
    @Test void disabledModeAndEquipmentSlotsRetainVanillaSelection() {
        manager.disable(player); var fifth = new PlayerItemHeldEvent(player, 5, 4); listener.onItemHeld(fifth); assertFalse(fifth.isCancelled());
        manager.toggle(player);
        for (int slot = 5; slot < 9; slot++) {
            var held = new PlayerItemHeldEvent(player, 6, slot); listener.onItemHeld(held); assertFalse(held.isCancelled());
        }
        verifyNoInteractions(talents, racial);
    }
    @Test void spectatorCannotCastFromHotbar() {
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        listener.onItemHeld(new PlayerItemHeldEvent(player, 5, 4)); verifyNoInteractions(talents, racial);
    }
}

package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.customrpg.players.PlayerTalents;
import com.customrpg.races.RacialSkillManager;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillHotkeyTest {
    CustomRPG plugin; Player player; PlayerInventory inventory; SkillBarManager manager; SkillBarListener listener;
    TalentSkillManager talents; RacialSkillManager racial; PlayerTalents selected;
    Map<NamespacedKey,Object> preferences;
    BukkitScheduler scheduler;
    BossBar hudBar;
    BukkitTask hudTask;
    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class); player = mock(Player.class); inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.getInventory()).thenReturn(inventory);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL); when(player.isOnline()).thenReturn(true);
        when(inventory.getHeldItemSlot()).thenReturn(5);
        var data = mock(PersistentDataContainer.class); preferences = new HashMap<>(); when(player.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(), any())).thenAnswer(i -> preferences.get(i.getArgument(0)));
        doAnswer(i -> { preferences.put(i.getArgument(0), i.getArgument(2)); return null; }).when(data).set(any(), any(), any());
        var server = mock(Server.class); scheduler = mock(BukkitScheduler.class); when(plugin.getServer()).thenReturn(server); when(server.getScheduler()).thenReturn(scheduler);
        hudBar = mock(BossBar.class); hudTask = mock(BukkitTask.class);
        when(server.createBossBar(anyString(), eq(BarColor.BLUE), eq(BarStyle.SOLID))).thenReturn(hudBar);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(hudTask);
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
        assertTrue(event.isCancelled()); verify(talents).castSkillDirectly(player, "star_shatter", air); verify(racial, never()).cast(player);
    }
    @Test void emptyTalentSlotDoesNotCastAnotherSkill() {
        var event = new PlayerItemHeldEvent(player, 5, 0); listener.onItemHeld(event);
        assertTrue(event.isCancelled()); verifyNoInteractions(talents); verify(racial, never()).cast(player);
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
    @Test void swapCastsFifthButSneakSwapNeverCastsRetiredUltimate() {
        selected.setSelectedSkill(4,"ordinary_fifth");selected.setUltimateSkill("ultimate");
        var fifth=new PlayerSwapHandItemsEvent(player,null,null);listener.onSwap(fifth);
        assertTrue(fifth.isCancelled());verify(talents).castSkillDirectly(player,"ordinary_fifth",null);
        when(player.isSneaking()).thenReturn(true);var ultimate=new PlayerSwapHandItemsEvent(player,null,null);listener.onSwap(ultimate);
        assertFalse(ultimate.isCancelled());verify(talents,never()).castSkillDirectly(player,"ultimate",null);verify(racial, never()).cast(player);
        verify(inventory,never()).setItemInMainHand(any());verify(inventory,never()).setItemInOffHand(any());
    }
    @Test void disabledSkillModeRetainsVanillaSwapAndCancelledEventsCannotCast() {
        manager.disable(player);var event=new PlayerSwapHandItemsEvent(player,null,null);listener.onSwap(event);
        assertFalse(event.isCancelled());verifyNoInteractions(talents);
    }
    @ParameterizedTest @ValueSource(strings={"frost_crown","earth_titan","dark_eclipse"})
    void supportedUltimateUsesSneakSwapButRetiredCoreUltimatesStillCannotCast(String id) {
        selected.setUltimateSkill(id);when(player.isSneaking()).thenReturn(true);
        var event=new PlayerSwapHandItemsEvent(player,null,null);listener.onSwap(event);
        assertTrue(event.isCancelled());verify(talents).castSkillDirectly(player,id,null);
        selected.setUltimateSkill("v3_weapon_blade_domain");var retired=new PlayerSwapHandItemsEvent(player,null,null);listener.onSwap(retired);
        assertFalse(retired.isCancelled());verify(talents,never()).castSkillDirectly(player,"v3_weapon_blade_domain",null);
    }
    @Test void skillHudStaysVisibleBeyondFiveSecondsWithoutOverwritingHealthManaActionBar() {
        selected.setSelectedSkill(0, "star_shatter");
        listener.showSkillBarHud(player, 0);
        verify(hudBar).addPlayer(player);
        verify(hudBar).setTitle(contains("1:"));
        verify(hudBar).setVisible(true);
        verify(player, never()).sendActionBar(any(net.kyori.adventure.text.Component.class));
        verify(player, never()).spigot();

        ArgumentCaptor<Runnable> updates = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), updates.capture(), eq(0L), eq(10L));
        for (int tick = 0; tick < 20; tick++) updates.getValue().run();
        verify(hudBar, never()).removeAll();
        verify(hudTask, never()).cancel();
        verify(player, never()).sendActionBar(any(net.kyori.adventure.text.Component.class));
        listener.hideSkillBarHud(player);
        verify(hudBar).removeAll();
        verify(hudTask).cancel();
    }
    @Test void refreshingSkillsReusesOneBarAndLogoutReleasesIt() {
        listener.showSkillBarHud(player, 0);
        listener.showSkillBarHud(player, 4);
        verify(plugin.getServer(), times(1)).createBossBar(anyString(), eq(BarColor.BLUE), eq(BarStyle.SOLID));
        verify(scheduler, times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(10L));
        verify(hudBar, times(1)).addPlayer(player);
        verify(hudBar, atLeastOnce()).setTitle(contains("§e5:"));
        listener.onQuit(new PlayerQuitEvent(player, (net.kyori.adventure.text.Component)null, null));
        verify(hudBar).removeAll();
        verify(hudTask, atLeastOnce()).cancel();
    }
    @Test void disablingSkillsImmediatelyRemovesTheirBar() {
        listener.showSkillBarHud(player, -1);
        manager.disable(player);
        listener.hideSkillBarHud(player);
        verify(hudBar).removeAll();
        verify(hudTask).cancel();
    }
}

package com.customrpg.gui;

import com.customrpg.CustomRPG;
import com.customrpg.managers.TalentManager;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HunterTalentGUITest {
    CustomRPG plugin; TalentManager manager; TalentTreeGUI gui; Player player; Inventory top; InventoryView view;
    Map<UUID,Inventory> views; Map<UUID,TalentBranch> branches; Map<UUID,HunterPath> pages;
    PlayerTalents learned = new PlayerTalents(); TalentTree tree = new TalentTree(TalentBranch.HUNTER, "Hunter", "");
    @SuppressWarnings("unchecked") <T> Map<UUID,T> state(String name) throws Exception {
        var field = TalentTreeGUI.class.getDeclaredField(name); field.setAccessible(true); return (Map<UUID,T>)field.get(gui);
    }
    @BeforeEach void setup() throws Exception {
        plugin = mock(CustomRPG.class); manager = mock(TalentManager.class); gui = spy(new TalentTreeGUI(plugin, manager));
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        top = mock(Inventory.class); view = mock(InventoryView.class); when(view.getTopInventory()).thenReturn(top);
        views = state("openViews"); branches = state("playerCurrentBranch"); pages = state("hunterPages");
        views.put(player.getUniqueId(), top); branches.put(player.getUniqueId(), TalentBranch.HUNTER);
        when(manager.getTalentTree(TalentBranch.HUNTER)).thenReturn(tree); when(manager.getPlayerTalents(player)).thenReturn(learned);
        doNothing().when(gui).refreshGUI(player, TalentBranch.HUNTER);
        for (String id : List.of("nen_rock", "nen_chain")) {
            var talent = mock(Talent.class); when(talent.getId()).thenReturn(id); when(talent.getGuiSlot()).thenReturn(40);
            when(talent.getType()).thenReturn(TalentType.ACTIVE); when(talent.getBranch()).thenReturn(TalentBranch.HUNTER);
            tree.registerTalent(talent); learned.setTalentLevel(id, 1);
        }
    }
    InventoryClickEvent click(int slot) {
        var event = mock(InventoryClickEvent.class); when(event.getWhoClicked()).thenReturn(player); when(event.getView()).thenReturn(view); when(event.getRawSlot()).thenReturn(slot); return event;
    }
    @Test void identicalSlotsOnDifferentCharacterPagesCannotLearnTheWrongCharactersSkill() {
        pages.put(player.getUniqueId(), HunterPath.GON); gui.onInventoryClick(click(40)); verify(manager).upgradeTalent(player, "nen_rock");
        verify(manager, never()).upgradeTalent(player, "nen_chain"); clearInvocations(manager);
        pages.put(player.getUniqueId(), HunterPath.KURAPIKA); gui.onInventoryClick(click(40)); verify(manager).upgradeTalent(player, "nen_chain");
        verify(manager, never()).upgradeTalent(player, "nen_rock");
    }
    @Test void numberKeysBindOnlyTheSkillVisibleOnTheCurrentCharacterPage() {
        pages.put(player.getUniqueId(), HunterPath.GON); var event = click(40);
        when(event.getClick()).thenReturn(ClickType.NUMBER_KEY); when(event.getHotbarButton()).thenReturn(2);
        gui.onInventoryClick(event); verify(manager).bindSkill(player, "nen_rock", 2, false);
        verify(manager, never()).bindSkill(player, "nen_chain", 2, false); verify(event).setCancelled(true);
    }
    @Test void overviewButtonsNavigateToRolesAndNeverUpgradeATalent() {
        doNothing().when(gui).openHunterPath(player, HunterPath.GON); gui.onInventoryClick(click(10));
        verify(gui).openHunterPath(player, HunterPath.GON); verify(manager, never()).upgradeTalent(any(), anyString());
    }
    @Test void backAndCommonTrainingButtonsKeepCharacterNavigationExplicit() {
        pages.put(player.getUniqueId(), HunterPath.HISOKA);
        doNothing().when(gui).open(player, TalentBranch.HUNTER); doNothing().when(gui).openHunterPath(player, HunterPath.COMMON);
        gui.onInventoryClick(click(45)); gui.onInventoryClick(click(46));
        verify(gui).open(player, TalentBranch.HUNTER); verify(gui).openHunterPath(player, HunterPath.COMMON);
    }
    @Test void fakeInventoryWithTheSameTitleCannotLearnOrBindAnySkill() {
        pages.put(player.getUniqueId(), HunterPath.GON); var other = mock(Inventory.class); when(view.getTopInventory()).thenReturn(other);
        gui.onInventoryClick(click(40)); verifyNoInteractions(manager);
    }
    @Test void draggingAndClosingAnOlderPageCannotExtractItemsOrEraseTheNewPage() {
        pages.put(player.getUniqueId(), HunterPath.GON); var drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(player); when(drag.getView()).thenReturn(view); gui.onDrag(drag); verify(drag).setCancelled(true);
        var close = mock(InventoryCloseEvent.class); when(close.getPlayer()).thenReturn(player); var old = mock(Inventory.class); when(close.getInventory()).thenReturn(old);
        gui.onClose(close); assertEquals(HunterPath.GON, pages.get(player.getUniqueId()));
        when(close.getInventory()).thenReturn(top); gui.onClose(close); assertTrue(views.isEmpty()); assertTrue(pages.isEmpty()); assertTrue(branches.isEmpty());
    }
    @Test void eachRoleFiltersOnlyItsOwnTalents() {
        assertEquals(List.of("nen_rock"), TalentTreeGUI.hunterTalents(tree, HunterPath.GON).stream().map(Talent::getId).toList());
        assertEquals(List.of("nen_chain"), TalentTreeGUI.hunterTalents(tree, HunterPath.KURAPIKA).stream().map(Talent::getId).toList());
        assertTrue(TalentTreeGUI.hunterTalents(tree, HunterPath.HISOKA).isEmpty());
    }
}

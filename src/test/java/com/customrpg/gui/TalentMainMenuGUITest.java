package com.customrpg.gui;

import com.customrpg.CustomRPG;
import com.customrpg.managers.TalentManager;
import com.customrpg.talents.TalentBranch;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TalentMainMenuGUITest {
    TalentMainMenuGUI menu; TalentTreeGUI trees; Player player; Inventory top; InventoryView view;
    Map<UUID,Object> owned;
    Object current;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        trees = mock(TalentTreeGUI.class); menu = spy(new TalentMainMenuGUI(mock(CustomRPG.class), mock(TalentManager.class), trees));
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID()); top = mock(Inventory.class); view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        var field = TalentMainMenuGUI.class.getDeclaredField("views"); field.setAccessible(true); owned = (Map<UUID,Object>)field.get(menu);
        own(null);
    }
    void own(String binding) throws Exception {
        var type=Arrays.stream(TalentMainMenuGUI.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("View")).findFirst().orElseThrow();
        var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        current=constructor.newInstance(top,binding);owned.put(player.getUniqueId(),current);
    }
    InventoryClickEvent click(int slot) {
        var event = mock(InventoryClickEvent.class); when(event.getWhoClicked()).thenReturn(player); when(event.getView()).thenReturn(view); when(event.getRawSlot()).thenReturn(slot); return event;
    }
    @ParameterizedTest @CsvSource({"10,FIRE", "11,DARK", "12,WEAPON", "13,TECH", "14,NATURE", "15,SUKUNA", "16,BLOOD", "19,ASSASSIN", "20,BEAST", "21,BOW", "22,FROST", "23,STORM", "24,EARTH", "25,LIGHT", "28,HUNTER", "29,NECROMANCER", "30,CHICKEN"})
    void allSeventeenBranchesOpenDirectly(int slot, TalentBranch branch) {
        var click = click(slot); menu.onInventoryClick(click);
        verify(click).setCancelled(true); verify(trees).open(player, branch);
    }
    @Test void removedUltimateButtonCannotBindOrOpenABranch() throws Exception {
        own("nen_rock"); menu.onInventoryClick(click(52)); verifyNoInteractions(trees);
    }
    @Test void inventoryFromAnotherPluginCannotOpenBranchesByMatchingTitle() {
        when(view.getTopInventory()).thenReturn(mock(Inventory.class)); menu.onInventoryClick(click(22)); verifyNoInteractions(trees);
    }
    @Test void bottomInventoryShiftClickAndDragCannotExtractMenuItems() {
        var click = click(55); when(click.getClick()).thenReturn(ClickType.SHIFT_LEFT); menu.onInventoryClick(click); verify(click).setCancelled(true);
        var drag = mock(InventoryDragEvent.class); when(drag.getWhoClicked()).thenReturn(player); when(drag.getView()).thenReturn(view);
        menu.onDrag(drag); verify(drag).setCancelled(true); verifyNoInteractions(trees);
    }
    @Test void closingAnOlderViewDoesNotClearANewerMenu() {
        var event = mock(InventoryCloseEvent.class); when(event.getPlayer()).thenReturn(player); when(event.getInventory()).thenReturn(mock(Inventory.class));
        menu.onClose(event); assertSame(current, owned.get(player.getUniqueId()));
        when(event.getInventory()).thenReturn(top); menu.onClose(event); assertTrue(owned.isEmpty());
    }
}

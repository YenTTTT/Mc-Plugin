package com.customrpg.gui;
import com.customrpg.CustomRPG;
import com.customrpg.races.RaceManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class RaceGUITest {
    RaceGUI gui; RaceManager races; Player player; Inventory top; InventoryView view;
    @BeforeEach void setup() throws Exception {
        races=mock(RaceManager.class); gui=new RaceGUI(mock(CustomRPG.class),races);
        player=mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        top=mock(Inventory.class); view=mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top); when(view.getTitle()).thenReturn("§6§l選擇你的種族");
        state("openViews").put(player.getUniqueId(),top);
    }
    @SuppressWarnings("unchecked") Map<UUID,Object> state(String name) throws Exception {
        var field=RaceGUI.class.getDeclaredField(name); field.setAccessible(true); return (Map<UUID,Object>)field.get(gui);
    }
    @Test void shiftClickAndDoubleClickFromBottomInventoryCannotExtractIcons() {
        var event=mock(InventoryClickEvent.class); when(event.getWhoClicked()).thenReturn(player); when(event.getView()).thenReturn(view);
        when(event.getRawSlot()).thenReturn(60); when(event.getClick()).thenReturn(ClickType.SHIFT_LEFT,ClickType.DOUBLE_CLICK);
        gui.onInventoryClick(event); gui.onInventoryClick(event); verify(event,times(2)).setCancelled(true); verifyNoInteractions(races);
    }
    @Test void dragIsBlockedOnlyForTheActualOwnedInventory() {
        var event=mock(InventoryDragEvent.class); when(event.getWhoClicked()).thenReturn(player); when(event.getView()).thenReturn(view);
        gui.onDrag(event); verify(event).setCancelled(true);
        when(view.getTopInventory()).thenReturn(mock(Inventory.class)); clearInvocations(event);
        gui.onDrag(event); verify(event,never()).setCancelled(anyBoolean());
    }
    @Test void matchingTitleOnUnownedInventoryCannotSelectRace() throws Exception {
        state("playerListSlotMap").put(player.getUniqueId(),Map.of(10,"elf"));
        when(view.getTopInventory()).thenReturn(mock(Inventory.class));
        var event=mock(InventoryClickEvent.class); when(event.getWhoClicked()).thenReturn(player); when(event.getView()).thenReturn(view); when(event.getRawSlot()).thenReturn(10);
        gui.onInventoryClick(event); verifyNoInteractions(races); verify(player,never()).closeInventory();
    }
    @Test void closeClearsStateButOldInventoryCloseDoesNotClearANewerView() throws Exception {
        state("listPages").put(player.getUniqueId(),2); state("playerDetailRace").put(player.getUniqueId(),"elf");
        var event=mock(InventoryCloseEvent.class); when(event.getPlayer()).thenReturn(player); when(event.getInventory()).thenReturn(mock(Inventory.class));
        gui.onClose(event); assertFalse(state("openViews").isEmpty());
        when(event.getInventory()).thenReturn(top); gui.onClose(event);
        assertTrue(state("openViews").isEmpty()); assertTrue(state("listPages").isEmpty()); assertTrue(state("playerDetailRace").isEmpty());
    }
}

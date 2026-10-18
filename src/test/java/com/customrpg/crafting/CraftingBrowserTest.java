package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftingBrowserTest {
    CraftingBrowser browser; CraftingItems items; Player player; Inventory top; InventoryView view; Map<UUID,Object> owned;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        var catalog = new CraftingCatalog(CraftingCatalogTest.RESOURCE.toFile()); items = mock(CraftingItems.class);
        browser = new CraftingBrowser(mock(CustomRPG.class), catalog, items, mock(VillageMerchantManager.class));
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID()); top = mock(Inventory.class); view = mock(InventoryView.class); when(view.getTopInventory()).thenReturn(top);
        var field = CraftingBrowser.class.getDeclaredField("views"); field.setAccessible(true); owned = (Map<UUID,Object>)field.get(browser);
        var type = Arrays.stream(CraftingBrowser.class.getDeclaredClasses()).filter(c -> c.getSimpleName().equals("View")).findFirst().orElseThrow();
        var constructor = type.getDeclaredConstructors()[0]; constructor.setAccessible(true); owned.put(player.getUniqueId(), constructor.newInstance(top, 0, List.of("dummy"), false, false));
    }
    @Test void shiftAndDoubleClicksInBottomInventoryCannotTakePreviewGear() {
        var event = mock(InventoryClickEvent.class); when(event.getWhoClicked()).thenReturn(player); when(event.getView()).thenReturn(view); when(event.getRawSlot()).thenReturn(60);
        when(event.getClick()).thenReturn(ClickType.SHIFT_LEFT, ClickType.DOUBLE_CLICK); browser.onClick(event); browser.onClick(event);
        verify(event, times(2)).setCancelled(true); verifyNoInteractions(items);
    }
    @Test void fakeInventoryCannotReadOrTakeTheOwnedPreview() {
        when(view.getTopInventory()).thenReturn(mock(Inventory.class)); var event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player); when(event.getView()).thenReturn(view); when(event.getRawSlot()).thenReturn(0);
        browser.onClick(event); verify(event, never()).setCancelled(anyBoolean()); verifyNoInteractions(items);
    }
    @Test void dragIsBlockedAndClosingOldInventoryDoesNotForgetNewView() {
        var drag = mock(InventoryDragEvent.class); when(drag.getWhoClicked()).thenReturn(player); when(drag.getView()).thenReturn(view); browser.onDrag(drag); verify(drag).setCancelled(true);
        var close = mock(InventoryCloseEvent.class); when(close.getPlayer()).thenReturn(player); when(close.getInventory()).thenReturn(mock(Inventory.class));
        browser.onClose(close); assertFalse(owned.isEmpty()); when(close.getInventory()).thenReturn(top); browser.onClose(close); assertTrue(owned.isEmpty());
    }
    @Test void givingMaterialsRequiresExplicitAdminPermission() {
        browser.onCommand(player, mock(Command.class), "rpgcraft", new String[]{"give", "player", "plains_essence_t1", "64"}); verifyNoInteractions(items);
        assertFalse(browser.onTabComplete(player, mock(Command.class), "rpgcraft", new String[]{""}).contains("give"));
    }
}

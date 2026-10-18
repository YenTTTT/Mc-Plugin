package com.customrpg.crafting;

import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import java.util.*;
import static org.mockito.Mockito.*;

final class CraftingTestItems {
    static PersistentDataContainer data(Map<NamespacedKey,Object> values) {
        var data = mock(PersistentDataContainer.class);
        doAnswer(i -> { values.put(i.getArgument(0), i.getArgument(2)); return null; }).when(data).set(any(), any(), any());
        when(data.get(any(), any())).thenAnswer(i -> values.get(i.getArgument(0)));
        when(data.has(any(NamespacedKey.class))).thenAnswer(i -> values.containsKey(i.getArgument(0)));
        when(data.has(any(NamespacedKey.class), any())).thenAnswer(i -> values.containsKey(i.getArgument(0)));
        doAnswer(i -> { values.remove(i.getArgument(0)); return null; }).when(data).remove(any());
        return data;
    }
    static ItemStack stack(Material type, int amount) { return stack(type, amount, new HashMap<>()); }
    static ItemStack stack(Material type, int amount, Map<NamespacedKey,Object> values) {
        var stack = mock(ItemStack.class); var meta = mock(ItemMeta.class); var data = data(values); int[] count = {amount};
        when(stack.getType()).thenReturn(type); when(stack.hasItemMeta()).thenReturn(true); when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data); when(stack.getAmount()).thenAnswer(i -> count[0]);
        doAnswer(i -> { count[0] = i.getArgument(0); return null; }).when(stack).setAmount(anyInt());
        when(stack.clone()).thenAnswer(i -> stack(type, count[0], new HashMap<>(values))); return stack;
    }
    static CraftingItems factory(CraftingCatalog catalog) {
        return new CraftingItems(catalog, g -> stack(catalog.base(g), 1, new HashMap<>(Map.of(CraftingItems.PRODUCT, g.id(), CraftingItems.VERSION, 1))),
                m -> stack(m.material(), 1, new HashMap<>(Map.of(CraftingItems.MATERIAL, m.id(), CraftingItems.VERSION, 1))));
    }
}

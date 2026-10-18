package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import org.bukkit.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftingRecipesTest {
    CustomRPG plugin; CraftingCatalog catalog; CraftingItems items; CraftingRecipes recipes;
    @BeforeEach void setup() throws Exception {
        plugin = mock(CustomRPG.class); catalog = new CraftingCatalog(CraftingCatalogTest.RESOURCE.toFile());
        items = CraftingTestItems.factory(catalog); recipes = new CraftingRecipes(plugin, catalog, items);
    }
    ItemStack[] grid(CraftingCatalog.Gear gear, boolean mirror) {
        var matrix = new ItemStack[9];
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
            char c = gear.kind().shape.get(y).charAt(mirror ? 2 - x : x);
            matrix[y * 3 + x] = c == 'C' && !gear.needsBossCore() ? CraftingTestItems.stack(Material.IRON_INGOT, 1) : items.ingredient(gear, c);
        }
        return matrix;
    }
    @ParameterizedTest @EnumSource(CraftingCatalog.Kind.class)
    void nativeMirroredPatternsAcceptCorrectIdentityAndRejectWrongTierOrExtraItems(CraftingCatalog.Kind kind) {
        var gear = catalog.gear().get("forge_forest_t3_" + kind.name().toLowerCase(Locale.ROOT));
        assertTrue(recipes.matches(gear, grid(gear, false))); assertTrue(recipes.matches(gear, grid(gear, true)));
        var wrong = grid(gear, false);
        for (int i = 0; i < 9; i++) if (gear.essence().equals(items.materialId(wrong[i]))) { wrong[i] = items.material("forest_essence_t2", 1); break; }
        assertFalse(recipes.matches(gear, wrong)); assertFalse(recipes.matches(gear, new ItemStack[4]));
    }
    @Test void highTierRequiresRealBossCoreAndInvalidRecipeCannotConsumeMaterials() {
        var gear = catalog.gear().get("forge_plains_t4_staff"); var matrix = grid(gear, false);
        assertTrue(recipes.matches(gear, matrix)); matrix[4] = CraftingTestItems.stack(Material.HEART_OF_THE_SEA, 1);
        assertFalse(recipes.matches(gear, matrix));
        var recipe = mock(ShapedRecipe.class); when(recipe.getKey()).thenReturn(new NamespacedKey("customrpg", gear.id()));
        var inventory = mock(CraftingInventory.class); when(inventory.getMatrix()).thenReturn(matrix);
        var prepare = mock(PrepareItemCraftEvent.class); when(prepare.getRecipe()).thenReturn(recipe); when(prepare.getInventory()).thenReturn(inventory);
        recipes.onPrepare(prepare); verify(inventory).setResult(null);
        var craft = mock(CraftItemEvent.class); when(craft.getRecipe()).thenReturn(recipe); when(craft.getInventory()).thenReturn(inventory);
        recipes.onCraft(craft); verify(craft).setCancelled(true); verify(inventory, never()).setMatrix(any());
    }
    @Test void vanillaRecipeCannotConvertAnRpgMaterialButOrdinaryVanillaCraftIsUnaffected() {
        var inventory = mock(CraftingInventory.class); var prepare = mock(PrepareItemCraftEvent.class); when(prepare.getInventory()).thenReturn(inventory);
        var matrix = new ItemStack[]{items.material("monster_binding_t1", 1)}; when(inventory.getMatrix()).thenReturn(matrix);
        recipes.onPrepare(prepare); verify(inventory).setResult(null);
        clearInvocations(inventory); var plain = new ItemStack[]{CraftingTestItems.stack(Material.STRING, 1)}; when(inventory.getMatrix()).thenReturn(plain);
        recipes.onPrepare(prepare); verify(inventory, never()).setResult(any());
    }
    @Test void validShiftCraftIsLeftToTheVanillaInventoryTransaction() {
        var gear = catalog.gear().get("forge_plains_t1_staff"); var recipe = mock(ShapedRecipe.class);
        when(recipe.getKey()).thenReturn(new NamespacedKey("customrpg", gear.id())); var inventory = mock(CraftingInventory.class); var matrix = grid(gear, false); when(inventory.getMatrix()).thenReturn(matrix);
        var event = mock(CraftItemEvent.class); when(event.getRecipe()).thenReturn(recipe); when(event.getInventory()).thenReturn(inventory);
        var output = items.product(gear.id()); when(event.getCurrentItem()).thenReturn(output); when(event.isShiftClick()).thenReturn(true);
        recipes.onCraft(event); verify(event, never()).setCancelled(true); verify(inventory, never()).setMatrix(any()); verify(event, never()).setCurrentItem(any());
    }
    @Test void allNativeRecipeKeysRegisterAndShutdownRemovesOnlyThoseSuccessfullyRegistered() {
        var server = mock(Server.class); when(plugin.getServer()).thenReturn(server); when(server.addRecipe(any())).thenReturn(true);
        var dummy = mock(ItemStack.class); var material = mock(Material.class); when(dummy.getType()).thenReturn(material); when(dummy.clone()).thenReturn(dummy);
        var fake = mock(CraftingItems.class); when(fake.material(anyString(), anyInt())).thenReturn(dummy); when(fake.product(anyString())).thenReturn(dummy);
        var choices = mock(CraftingRecipes.Choices.class); var choice = mock(RecipeChoice.class); when(choice.clone()).thenReturn(choice); when(choice.validate(anyBoolean())).thenReturn(choice);
        when(choices.custom(any())).thenReturn(choice); when(choices.iron()).thenReturn(choice);
        var service = new CraftingRecipes(plugin, catalog, fake, choices); service.register(); verify(server, times(987)).addRecipe(any()); verify(choices, times(420)).iron();
        service.shutdown(); verify(server, times(987)).removeRecipe(any());
    }
    @Test void bossCoreRefinementChecksOneRealCoreAndEightBindingOutput() {
        var recipe = mock(ShapelessRecipe.class); when(recipe.getKey()).thenReturn(new NamespacedKey("customrpg", "forge_refine_core_t1"));
        var inventory = mock(CraftingInventory.class); var matrix = new ItemStack[9]; matrix[0] = items.material("boss_core_t1", 1); when(inventory.getMatrix()).thenReturn(matrix);
        var event = mock(CraftItemEvent.class); when(event.getRecipe()).thenReturn(recipe); when(event.getInventory()).thenReturn(inventory);
        var output = items.material("monster_binding_t1", 8); when(event.getCurrentItem()).thenReturn(output); recipes.onCraft(event); verify(event, never()).setCancelled(true);
        matrix[1] = items.material("boss_core_t1", 1); recipes.onCraft(event); verify(event).setCancelled(true);
    }
}

package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import com.customrpg.balance.BalanceCombatListener;
import com.customrpg.balance.CombatBalance;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.players.PlayerStats;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PotionSystemTest {
    static final Path RESOURCE = Path.of("src/main/resources/config/potions.yml");
    @TempDir Path directory;
    CustomRPG plugin; CraftingCatalog crafting; CraftingItems ingredients; PotionCatalog catalog;
    PotionItems items; PotionManager potions; Player player; PlayerStats stats; CombatBalance balance;
    PluginManager events; Server server; AtomicLong clock = new AtomicLong(1000);
    Map<NamespacedKey,Object> data = new HashMap<>(); double hp = 10, maxHp = 100;
    @BeforeEach void setup() throws Exception {
        crafting = new CraftingCatalog(CraftingCatalogTest.RESOURCE.toFile()); ingredients = CraftingTestItems.factory(crafting);
        catalog = new PotionCatalog(RESOURCE.toFile(), crafting);
        items = new PotionItems(catalog, p -> CraftingTestItems.stack(Material.POTION, 1,
                new HashMap<>(Map.of(PotionItems.ID, p.id(), PotionItems.VERSION, 1))));
        plugin = mock(CustomRPG.class); server = mock(Server.class); events = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(events);
        when(server.getOnlinePlayers()).thenReturn(List.of());
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        var playerData = CraftingTestItems.data(data); when(player.getPersistentDataContainer()).thenReturn(playerData);
        when(player.getHealth()).thenAnswer(i -> hp); when(player.getMaxHealth()).thenAnswer(i -> maxHp);
        doAnswer(i -> { hp = i.getArgument(0); return null; }).when(player).setHealth(anyDouble());
        var statsManager = mock(PlayerStatsManager.class); when(plugin.getPlayerStatsManager()).thenReturn(statsManager);
        stats = new PlayerStats(); stats.setCurrentMana(0);
        when(statsManager.getStats(player)).thenReturn(stats); when(statsManager.isDataAvailable(player)).thenReturn(true);
        balance = new CombatBalance(plugin); when(plugin.getCombatBalance()).thenReturn(balance);
        potions = new PotionManager(plugin, catalog, ingredients, items, clock::get);
    }
    PlayerItemConsumeEvent drink(String id) { return new PlayerItemConsumeEvent(player, items.create(id, 1), EquipmentSlot.HAND); }
    void consume(PlayerItemConsumeEvent event) { potions.beforeDrink(event); potions.afterDrink(event); }
    ItemStack[] matrix(String id) {
        var grid = new ItemStack[9]; int index = 0;
        for (var input : catalog.potions().get(id).inputs()) grid[index++] = input.custom()
                ? ingredients.material(input.customId(), 1) : CraftingTestItems.stack(input.material(), 1);
        return grid;
    }
    @Test void threeGradesOfEachResourceHaveIncreasingRecoveryAndRarerActualLoot() {
        assertEquals(6, catalog.potions().size()); assertEquals(15000, catalog.cooldownMillis());
        for (String resource : List.of("health", "mana")) {
            var low = catalog.potions().get(resource + "_crude");
            var mid = catalog.potions().get(resource + "_refined");
            var high = catalog.potions().get(resource + "_elixir");
            assertTrue(low.restore() < mid.restore() && mid.restore() < high.restore());
            assertTrue(low.inputs().stream().anyMatch(i -> "monster_binding_t1".equals(i.customId())));
            assertTrue(mid.inputs().stream().anyMatch(i -> "monster_binding_t3".equals(i.customId())));
            assertTrue(high.inputs().stream().anyMatch(i -> "boss_core_t5".equals(i.customId())));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"restore", "unknown-material", "color", "bottle", "cooldown"})
    void invalidConfigIsRejectedBeforeRecipesAreRegistered(String problem) throws Exception {
        var config = YamlConfiguration.loadConfiguration(RESOURCE.toFile());
        switch (problem) {
            case "restore" -> config.set("potions.health_crude.restore", Double.NaN);
            case "unknown-material" -> config.set("potions.health_crude.ingredients", List.of("GLASS_BOTTLE", "WHEAT", "custom:fake_t1"));
            case "color" -> config.set("potions.health_crude.color", "red");
            case "bottle" -> config.set("potions.health_crude.ingredients", List.of("WHEAT", "SUGAR", "SWEET_BERRIES"));
            case "cooldown" -> config.set("cooldown-seconds", 0);
        }
        var file = directory.resolve("bad.yml"); config.save(file.toFile());
        assertThrows(IllegalArgumentException.class, () -> new PotionCatalog(file.toFile(), crafting));
    }
    @Test void realIdentityIsRequiredAndCopiesDoNotAliasTheirAmount() {
        var one = items.create("health_crude", 1); var sixteen = items.create("health_crude", 16);
        assertEquals(1, one.getAmount()); assertEquals(16, sixteen.getAmount()); assertNotSame(one, sixteen);
        assertNull(items.identify(CraftingTestItems.stack(Material.POTION, 1)));
        assertNull(items.identify(CraftingTestItems.stack(Material.POTION, 1, new HashMap<>(Map.of(PotionItems.ID, "fake", PotionItems.VERSION, 1)))));
        assertNull(items.identify(CraftingTestItems.stack(Material.GLASS_BOTTLE, 1, new HashMap<>(Map.of(PotionItems.ID, "health_crude", PotionItems.VERSION, 1)))));
        assertThrows(IllegalArgumentException.class, () -> items.create("health_crude", 17));
    }
    @ParameterizedTest @ValueSource(strings = {"health_crude", "health_refined", "health_elixir", "mana_crude", "mana_refined", "mana_elixir"})
    void recipesAreOrderIndependentAndRejectCounterfeitMaterialsAndTwoByTwoGrids(String id) {
        var potion = catalog.potions().get(id); var grid = matrix(id);
        assertTrue(potions.matches(potion, grid)); Collections.reverse(Arrays.asList(grid)); assertTrue(potions.matches(potion, grid));
        assertFalse(potions.matches(potion, new ItemStack[4]));
        for (int i = 0; i < 9; i++) if (ingredients.isMaterial(grid[i])) { grid[i] = CraftingTestItems.stack(grid[i].getType(), 1); break; }
        assertFalse(potions.matches(potion, grid));
    }
    @Test void potionOutputMustBeOneBottleOfTheCorrectGrade() {
        var definition = catalog.potions().get("health_crude");
        assertTrue(potions.validOutput(definition, items.create("health_crude", 1)));
        assertFalse(potions.validOutput(definition, items.create("health_elixir", 1)));
        assertFalse(potions.validOutput(definition, items.create("health_crude", 2)));
    }
    @Test void healthPotionUsesRealHealingEventsAndCapsAtMaximumHealth() {
        consume(drink("health_crude")); assertEquals(50, hp);
        clock.addAndGet(15000); consume(drink("health_elixir")); assertEquals(100, hp);
        verify(events, times(2)).callEvent(any(EntityRegainHealthEvent.class));
        verify(player, never()).getInventory(); // Native consumption owns bottles/stack decrement.
    }
    @Test void manaPotionRestoresOnlyTheActualMissingMana() {
        stats.setMaxMana(200); stats.setCurrentMana(170); consume(drink("mana_elixir"));
        assertEquals(200, stats.getCurrentMana()); assertTrue(data.containsKey(PotionManager.COOLDOWN));
        assertEquals(10, hp);
    }
    @Test void combatPotionsKeepTheirFixedAmountAndDoNotSpendTheSkillHealingPool() {
        maxHp = 1000; hp = 10; balance.mark(player);
        var listener = new BalanceCombatListener(plugin, balance);
        doAnswer(i -> { var event = (EntityRegainHealthEvent)i.getArgument(0); listener.onHeal(event); listener.afterHeal(event); return null; })
                .when(events).callEvent(any(EntityRegainHealthEvent.class));
        consume(drink("health_elixir")); assertEquals(290, hp);
        // Ordinary skill healing still has the original 25%/shared-pool limit.
        assertEquals(250, balance.heal(player, 280));
    }
    @Test void healthAndManaSharePersistentCooldownAcrossServiceRecreation() {
        consume(drink("mana_crude"));
        var replacement = new PotionManager(plugin, catalog, ingredients, items, clock::get);
        var blocked = drink("health_elixir"); replacement.beforeDrink(blocked); replacement.afterDrink(blocked);
        assertTrue(blocked.isCancelled()); assertEquals(10, hp);
        clock.addAndGet(15000); var allowed = drink("health_crude"); replacement.beforeDrink(allowed); replacement.afterDrink(allowed);
        assertFalse(allowed.isCancelled()); assertEquals(50, hp);
    }
    @Test void fullResourcesRejectConsumptionWithoutStartingCooldown() {
        hp = 100; var health = drink("health_crude"); consume(health); assertTrue(health.isCancelled());
        stats.setCurrentMana(stats.getMaxMana()); var mana = drink("mana_crude"); consume(mana); assertTrue(mana.isCancelled());
        assertFalse(data.containsKey(PotionManager.COOLDOWN)); verifyNoInteractions(events);
    }
    @Test void consumptionCancelledBeforeOrAfterValidationNeverRestoresAnything() {
        var first = drink("mana_crude"); first.setCancelled(true); potions.afterDrink(first);
        var later = drink("health_crude"); potions.beforeDrink(later); later.setCancelled(true); potions.afterDrink(later);
        assertEquals(0, stats.getCurrentMana()); assertEquals(10, hp); assertFalse(data.containsKey(PotionManager.COOLDOWN));
    }
    @Test void cancelledHealingIsRespectedAndDoesNotSetPotionCooldown() {
        doAnswer(i -> { ((EntityRegainHealthEvent)i.getArgument(0)).setCancelled(true); return null; }).when(events).callEvent(any());
        consume(drink("health_elixir")); assertEquals(10, hp); assertFalse(data.containsKey(PotionManager.COOLDOWN));
    }
    @Test void offhandAndDuplicateMonitorCallsCannotGiveDoubleRestoration() {
        var event = new PlayerItemConsumeEvent(player, items.create("mana_crude", 3), EquipmentSlot.OFF_HAND);
        consume(event); potions.afterDrink(event); assertEquals(30, stats.getCurrentMana());
        assertEquals(3, event.getItem().getAmount()); verify(player, never()).getInventory();
    }
    @Test void vanillaPotionsAreIgnoredAndUnavailablePlayerDataBlocksCustomDrinks() {
        var vanilla = new PlayerItemConsumeEvent(player, CraftingTestItems.stack(Material.POTION, 1), EquipmentSlot.HAND);
        consume(vanilla); assertFalse(vanilla.isCancelled());
        when(plugin.getPlayerStatsManager().isDataAvailable(player)).thenReturn(false);
        var custom = drink("mana_crude"); consume(custom); assertTrue(custom.isCancelled()); assertEquals(0, stats.getCurrentMana());
    }
    @Test void customPotionInputsCannotEnterVanillaRecipesOrBrewing() {
        var recipes = new CraftingRecipes(plugin, crafting, ingredients);
        var inventory = mock(CraftingInventory.class); doReturn(new ItemStack[]{items.create("health_crude", 1)}).when(inventory).getMatrix();
        var prepare = mock(PrepareItemCraftEvent.class); when(prepare.getInventory()).thenReturn(inventory);
        recipes.onPrepare(prepare); verify(inventory).setResult(null);
        var brewer = mock(BrewerInventory.class); doReturn(new ItemStack[]{items.create("health_crude", 1)}).when(brewer).getContents();
        var brew = mock(BrewEvent.class); when(brew.getContents()).thenReturn(brewer); potions.onBrew(brew); verify(brew).setCancelled(true);
    }
    @Test void actualCraftingHooksAcceptValidPotionRecipesAndRejectOutputOrInputSpoofing() {
        var recipe = mock(ShapelessRecipe.class); when(recipe.getKey()).thenReturn(new NamespacedKey("customrpg", "potion_health_crude"));
        var recipes = new CraftingRecipes(plugin, crafting, ingredients, null, potions);
        var inventory = mock(CraftingInventory.class); doReturn(matrix("health_crude")).when(inventory).getMatrix();
        var prepare = mock(PrepareItemCraftEvent.class); when(prepare.getInventory()).thenReturn(inventory); when(prepare.getRecipe()).thenReturn(recipe);
        recipes.onPrepare(prepare); verify(inventory, never()).setResult(any());
        var event = mock(CraftItemEvent.class); when(event.getInventory()).thenReturn(inventory); when(event.getRecipe()).thenReturn(recipe);
        doReturn(items.create("health_crude", 1)).when(event).getCurrentItem(); recipes.onCraft(event); verify(event, never()).setCancelled(true);
        doReturn(items.create("mana_elixir", 1)).when(event).getCurrentItem(); recipes.onCraft(event); verify(event).setCancelled(true);
        when(inventory.getMatrix()).thenReturn(new ItemStack[9]); recipes.onPrepare(prepare); verify(inventory).setResult(null);
    }
    @Test void allSixRecipeKeysRegisterAndOnlyThoseKeysAreRemovedOnShutdown() {
        when(server.addRecipe(any())).thenReturn(true);
        var choice = mock(RecipeChoice.class); when(choice.clone()).thenReturn(choice); when(choice.validate(anyBoolean())).thenReturn(choice);
        // ShapelessRecipe validates the result's native Material; use a mock material to avoid Paper registries.
        var output = mock(ItemStack.class); var material = mock(Material.class); when(output.getType()).thenReturn(material); when(output.clone()).thenReturn(output);
        var factory = mock(PotionItems.class); when(factory.create(anyString(), eq(1))).thenReturn(output);
        var service = new PotionManager(plugin, catalog, ingredients, factory, clock::get); service.register(input -> choice);
        verify(server, times(6)).addRecipe(any()); service.shutdown(); verify(server, times(6)).removeRecipe(any());
    }
    @Test void commandCompletionAndAdminGiveIncludeAllSixPotionsWithoutGrantingPlayersItems() {
        var browser = new CraftingBrowser(plugin, crafting, ingredients, mock(VillageMerchantManager.class), potions);
        var command = mock(org.bukkit.command.Command.class);
        assertTrue(browser.onTabComplete(player, command, "rpgcraft", new String[]{""}).contains("potions"));
        assertEquals(6, browser.onTabComplete(player, command, "rpgcraft", new String[]{"recipe", "health"}).size() + browser.onTabComplete(player, command, "rpgcraft", new String[]{"recipe", "mana"}).size());
        browser.onCommand(player, command, "rpgcraft", new String[]{"give", "someone", "mana_crude", "1"}); verify(player, never()).getInventory();
    }
}

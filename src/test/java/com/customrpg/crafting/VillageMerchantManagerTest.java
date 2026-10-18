package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import com.customrpg.managers.WorldZoneManager;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillageMerchantManagerTest {
    @TempDir Path directory;
    CustomRPG plugin; Server server; World world; Chunk chunk; CraftingCatalog catalog; CraftingItems items;
    VillageMerchantRegistry registry; VillageMerchantManager manager; AtomicLong clock = new AtomicLong(1000); AtomicInteger level = new AtomicInteger(18);
    Map<UUID,Villager> actors = new HashMap<>(); AtomicReference<String> structureType = new AtomicReference<>("village_plains");
    @BeforeEach void setup() throws Exception {
        catalog = new CraftingCatalog(CraftingCatalogTest.RESOURCE.toFile()); items = CraftingTestItems.factory(catalog);
        plugin = mock(CustomRPG.class); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var logger = java.util.logging.Logger.getAnonymousLogger(); logger.setLevel(java.util.logging.Level.OFF); when(plugin.getLogger()).thenReturn(logger);
        server = mock(Server.class); when(plugin.getServer()).thenReturn(server); world = mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL); when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(server.getEntity(any())).thenAnswer(i -> actors.get(i.getArgument(0)));
        when(server.getWorld(world.getUID())).thenReturn(world);
        var border = mock(WorldBorder.class); when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class))).thenReturn(63);
        var floor = mock(Block.class); when(floor.getType()).thenReturn(Material.DIRT_PATH); var air = mock(Block.class); when(air.isPassable()).thenReturn(true);
        when(world.getBlockAt(any(Location.class))).thenAnswer(i -> ((Location)i.getArgument(0)).getBlockY() == 63 ? floor : air);
        chunk = mock(Chunk.class); when(chunk.getWorld()).thenReturn(world); when(chunk.isLoaded()).thenReturn(true); when(chunk.getEntities()).thenReturn(new Entity[0]);
        when(chunk.isEntitiesLoaded()).thenReturn(true); when(world.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
        var zones = mock(WorldZoneManager.class); when(plugin.getWorldZoneManager()).thenReturn(zones); when(zones.getBaseLevel(any())).thenAnswer(i -> level.get());
        registry = new VillageMerchantRegistry(directory.resolve("data/village_merchants.yml").toFile(), logger); manager = create();
        when(world.spawn(any(Location.class), eq(Villager.class), eq(CreatureSpawnEvent.SpawnReason.CUSTOM), any())).thenAnswer(i -> {
            Villager merchant = merchant(i.getArgument(0)); Consumer<Villager> before = i.getArgument(3); before.accept(merchant);
            assertTrue(Files.readString(directory.resolve("data/village_merchants.yml")).contains(merchant.getUniqueId().toString()));
            actors.put(merchant.getUniqueId(), merchant); return merchant;
        });
    }
    VillageMerchantManager create() { return new VillageMerchantManager(plugin, catalog, items, registry, clock::get, v -> {}, l -> "plains", CraftingTestItems::stack,
            c -> List.of(new VillageMerchantManager.Footprint(structureType.get(), new BoundingBox(0, 60, 0, 15, 80, 15)))); }
    Villager merchant(Location location) {
        var merchant = mock(Villager.class); when(merchant.getUniqueId()).thenReturn(UUID.randomUUID()); when(merchant.isValid()).thenReturn(true);
        when(merchant.getLocation()).thenAnswer(i -> location.clone()); when(merchant.getWorld()).thenReturn(world);
        var pdc = CraftingTestItems.data(new HashMap<>()); when(merchant.getPersistentDataContainer()).thenReturn(pdc);
        List<MerchantRecipe> recipes = new ArrayList<>(); when(merchant.getRecipes()).thenAnswer(i -> List.copyOf(recipes));
        doAnswer(i -> { recipes.clear(); recipes.addAll(i.getArgument(0)); return null; }).when(merchant).setRecipes(anyList()); return merchant;
    }
    @Test void loadedVillageGetsExactlyOneMerchantAndIdentityIsSavedBeforeExposure() {
        manager.inspect(chunk); manager.inspect(chunk); assertEquals(1, actors.size()); assertEquals(1, registry.villages().size());
        assertEquals(28, actors.values().iterator().next().getRecipes().size());
        verify(world, times(1)).spawn(any(Location.class), eq(Villager.class), eq(CreatureSpawnEvent.SpawnReason.CUSTOM), any());
    }
    @Test void nonVillageStructuresDoNotCreateMerchantsAndNoTerrainOrLocateCallsAreUsed() {
        structureType.set("desert_pyramid"); manager.inspect(chunk);
        assertTrue(actors.isEmpty()); assertTrue(registry.villages().isEmpty()); verify(world, never()).getChunkAt(anyInt(), anyInt());
    }
    @Test void unloadedMerchantKeepsItsSlotAndCannotProduceADuplicate() {
        manager.inspect(chunk); Villager merchant = actors.values().iterator().next(); var unloadChunk = mock(Chunk.class); when(unloadChunk.getX()).thenReturn(4); when(unloadChunk.getZ()).thenReturn(7);
        var unload = mock(EntitiesUnloadEvent.class); when(unload.getEntities()).thenReturn(List.of(merchant)); when(unload.getChunk()).thenReturn(unloadChunk); manager.onUnload(unload);
        actors.clear(); when(world.isChunkLoaded(4, 7)).thenReturn(false); manager.inspect(chunk);
        verify(world, times(1)).spawn(any(Location.class), eq(Villager.class), eq(CreatureSpawnEvent.SpawnReason.CUSTOM), any());
    }
    @Test void duplicateLoadedNpcIsRemovedButTheReservedMerchantSurvives() {
        manager.inspect(chunk); var actual = actors.values().iterator().next(); var duplicate = merchant(actual.getLocation());
        duplicate.getPersistentDataContainer().set(VillageMerchantManager.VILLAGE, PersistentDataType.STRING, registry.villages().keySet().iterator().next());
        var load = mock(EntitiesLoadEvent.class); when(load.getChunk()).thenReturn(chunk); when(load.getEntities()).thenReturn(List.of(actual, duplicate)); manager.onLoad(load);
        verify(duplicate).remove(); verify(actual, never()).remove();
    }
    @Test void diskFailurePreventsActorCreationAndDoesNotOverwriteRegistry() throws Exception {
        Files.createDirectories(directory.resolve("data/village_merchants.yml.tmp")); manager.inspect(chunk); assertTrue(actors.isEmpty()); assertFalse(registry.healthy());
    }
    @Test void openingOrRestartingManagerDoesNotResetUsedOffersButTimedRestockDoes() {
        manager.inspect(chunk); var merchant = actors.values().iterator().next(); var first = merchant.getRecipes().getFirst(); first.setUses(3);
        manager.stock(merchant); assertEquals(3, merchant.getRecipes().getFirst().getUses()); create().stock(merchant); assertEquals(3, merchant.getRecipes().getFirst().getUses());
        clock.addAndGet(1800001); manager.stock(merchant); assertEquals(0, merchant.getRecipes().getFirst().getUses());
    }
    @Test void tradeStockUsesMerchantLocationAndCanUseTwoEmeraldStacks() {
        level.set(58); manager.inspect(chunk); var merchant = actors.values().iterator().next();
        assertTrue(merchant.getRecipes().stream().allMatch(r -> catalog.gear().get(items.productId(r.getResult())).tier().id() == 7));
        assertTrue(merchant.getRecipes().stream().anyMatch(r -> r.getIngredients().size() == 2));
        for (var trade : merchant.getRecipes()) {
            assertTrue(trade.shouldIgnoreDiscounts()); assertEquals(0, trade.getPriceMultiplier());
            assertEquals(catalog.gear().get(items.productId(trade.getResult())).price(), trade.getIngredients().stream().mapToInt(ItemStack::getAmount).sum());
        }
    }
    @Test void purchaseRechecksIdentityRegionTierAndSoldOutState() {
        manager.inspect(chunk); var merchant = actors.values().iterator().next(); var trade = merchant.getRecipes().getFirst();
        var event = mock(PlayerTradeEvent.class); when(event.getMerchant()).thenReturn(merchant); when(event.getTrade()).thenReturn(trade);
        manager.onTrade(event); verify(event, never()).setCancelled(true); verify(event).setIncreaseTradeUses(true);
        trade.setUses(trade.getMaxUses()); manager.onTrade(event); verify(event).setCancelled(true);
        trade.setUses(0); clearInvocations(event); level.set(58); manager.onTrade(event); verify(event).setCancelled(true);
    }
    @Test void failedPlacementDoesNotTurnVillageBlocksIntoSpawnSpace() {
        when(world.getWorldBorder().isInside(any())).thenReturn(false); manager.inspect(chunk);
        assertTrue(actors.isEmpty()); assertEquals(1, registry.villages().size());
    }
    @Test void chunkBlocksBeingLoadedDoesNotImplyItsMerchantEntitiesAreAvailable() {
        when(chunk.isEntitiesLoaded()).thenReturn(false); manager.inspect(chunk); assertTrue(actors.isEmpty());
        verify(chunk, never()).getEntities(); verify(chunk, never()).getStructures();
    }
}

package com.customrpg.worldevent;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.customrpg.worldevent.core.*;
import com.customrpg.worldevent.location.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventTestModeSafetyTest {
    @TempDir Path directory;
    CustomRPG plugin; World world; NaturalTerrainRegistry terrain; ProtectionValidator protection;
    EventLocationService locations;
    final WorldEventDefinition.Requirements requirements=new WorldEventDefinition.Requirements(12,4,9,5);
    Location center;
    Material changed;
    int adjacentHeight=64;
    boolean waterlogged;
    @BeforeEach void setup() throws Exception {
        plugin=mock(CustomRPG.class); when(plugin.getSafeZoneManager()).thenReturn(mock(SafeZoneManager.class));
        when(plugin.getProtectionAreaManager()).thenReturn(mock(ProtectionAreaManager.class));
        when(plugin.getBossZoneManager()).thenReturn(mock(BossZoneManager.class));
        var server=mock(Server.class); when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        world=mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL); when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        var border=mock(WorldBorder.class); when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        when(world.getHighestBlockYAt(anyInt(),anyInt(),eq(HeightMap.OCEAN_FLOOR))).thenAnswer(i->(int)i.getArgument(0)==1001?adjacentHeight:64);
        Map<Material,Block> blockTypes=new EnumMap<>(Material.class);
        var wet=mock(Waterlogged.class); when(wet.isWaterlogged()).thenReturn(true);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->{
            int x=i.getArgument(0),y=i.getArgument(1),z=i.getArgument(2);
            Material type=x==1000&&y==65&&z==1000&&changed!=null?changed:y<=(x==1001?adjacentHeight:64)?Material.STONE:Material.AIR;
            return blockTypes.computeIfAbsent(type,material->{
                var block=mock(Block.class,withSettings().stubOnly()); when(block.getType()).thenReturn(material);
                when(block.getBlockData()).thenAnswer(ignored->waterlogged&&material==Material.OAK_LEAVES?wet:null);
                return block;
            });
        });
        terrain=new NaturalTerrainRegistry(directory.resolve("chunks.yml")); protection=new ProtectionValidator(plugin);
        locations=new EventLocationService(terrain,protection,4); center=new Location(world,1000,65,1000);
    }
    @Test void explicitTestAcceptsOldTerrainWithoutGrantingProvenanceToNormalEvents() {
        assertNotNull(locations.validateForTesting(center,requirements,List.of()));
        assertNull(locations.validate(center,requirements,List.of())); assertTrue(locations.lastRejection.contains("Unknown"));
        assertFalse(terrain.eligible(world.getUID(),62,62));
        verify(world,never()).getChunkAt(anyInt(),anyInt()); // no structure metadata lookup or chunk generation
        verify(world,never()).getBiome(anyInt(),anyInt(),anyInt());
    }
    @Test void testSupportsCommonBuildingMaterialsTreesAndTallPlants() {
        for(Material type:List.of(Material.COBBLESTONE,Material.STONE_BRICKS,Material.OAK_PLANKS,Material.OAK_LOG,Material.OAK_LEAVES,Material.TALL_GRASS)) {
            changed=type; assertNotNull(locations.validateForTesting(center,requirements,List.of()),type.name());
            assertFalse(TerrainValidator.accepts(type));
        }
    }
    @Test void testStillRejectsContainersLiquidsGravityAndComplexBlocks() {
        for(Material type:List.of(Material.CHEST,Material.BARREL,Material.WATER,Material.LAVA,Material.SAND,Material.GRAVEL,
                Material.SPAWNER,Material.OAK_DOOR,Material.RED_BED,Material.NETHER_PORTAL,Material.PISTON)) {
            changed=type; assertNull(locations.validateForTesting(center,requirements,List.of()),type.name());
            assertTrue(locations.lastRejection.contains("Unsafe block"));
        }
    }
    @Test void waterloggedLeavesRemainRejected() {
        changed=Material.OAK_LEAVES; waterlogged=true;
        assertNull(locations.validateForTesting(center,requirements,List.of())); assertTrue(locations.lastRejection.contains("waterlogged"));
    }
    @Test void testSkipsSpawnSafeZoneButRetainsCustomClaimsBossAreasAndAdapterFailures() {
        when(plugin.getSafeZoneManager().isInSafeZone(any())).thenReturn(true);
        assertNotNull(locations.validateForTesting(center,requirements,List.of()));
        when(plugin.getProtectionAreaManager().isInProtectedArea2D(any())).thenReturn(true);
        assertNull(locations.validateForTesting(center,requirements,List.of())); assertEquals("CustomRPG protected region",locations.lastRejection);
        when(plugin.getProtectionAreaManager().isInProtectedArea2D(any())).thenReturn(false);
        when(plugin.getBossZoneManager().isInsideBossZone(any())).thenReturn(true);
        assertNull(locations.validateForTesting(center,requirements,List.of())); assertEquals("Boss region",locations.lastRejection);
        when(plugin.getBossZoneManager().isInsideBossZone(any())).thenReturn(false);
        when(plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")).thenReturn(true);
        protection.register("WorldGuard",ignored->false);
        assertNull(locations.validateForTesting(center,requirements,List.of())); assertEquals("WorldGuard denied terrain edits",locations.lastRejection);
        protection.register("WorldGuard",ignored->{throw new IllegalStateException("unavailable");});
        assertNull(locations.validateForTesting(center,requirements,List.of())); assertEquals("WorldGuard protection lookup failed",locations.lastRejection);
    }
    @Test void testRetainsLoadedChunksNonPeacefulOverworldAndEventExclusion() {
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertNull(locations.validateForTesting(center,requirements,List.of())); assertTrue(locations.lastRejection.contains("unloaded"));
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(world.getDifficulty()).thenReturn(Difficulty.PEACEFUL);
        assertNull(locations.validateForTesting(center,requirements,List.of()));
        when(world.getDifficulty()).thenReturn(Difficulty.NORMAL); when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        assertNull(locations.validateForTesting(center,requirements,List.of()));
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        var event=WorldEventLifecycleTest.event(); when(world.getUID()).thenReturn(event.world);
        assertNull(locations.validateForTesting(new Location(world,event.x,event.y,event.z),requirements,List.of(event)));
        assertTrue(locations.lastRejection.contains("Overlapping"));
    }
    @Test void testAllowsModerateSlopeButRetainsBoundedHeightAndWorldBorder() {
        adjacentHeight=72; assertNotNull(locations.validateForTesting(center,requirements,List.of()));
        adjacentHeight=77; assertNull(locations.validateForTesting(center,requirements,List.of()));
        assertTrue(locations.lastRejection.contains("Height variation"));
        adjacentHeight=64; when(world.getWorldBorder().isInside(any())).thenReturn(false);
        assertNull(locations.validateForTesting(center,requirements,List.of())); assertEquals("World border",locations.lastRejection);
    }
    @Test void missingEditorTrackingDoesNotBlockFixedAdminTestButClaimsStillDo() {
        var plugins=plugin.getServer().getPluginManager();
        when(plugins.isPluginEnabled("WorldEdit")).thenReturn(true);
        when(plugins.isPluginEnabled("FastAsyncWorldEdit")).thenReturn(true);
        assertNotNull(locations.validateForTesting(center,requirements,List.of()));
        assertTrue(protection.rejection(center).contains("WorldEdit"));
        when(plugins.isPluginEnabled("Lands")).thenReturn(true);
        assertNull(locations.validateForTesting(center,requirements,List.of()));
        assertEquals("No protection adapter for Lands",locations.lastRejection);
        protection.register("Lands",ignored->false);
        assertNull(locations.validateForTesting(center,requirements,List.of())); assertEquals("Lands denied terrain edits",locations.lastRejection);
    }
    @Test void failedEditorTrackingIsNotAClaimAndMixedCaseClaimAdaptersRemainEnforced() {
        var plugins=plugin.getServer().getPluginManager();
        when(plugins.isPluginEnabled("WorldEdit")).thenReturn(true);
        protection.register("worldedit",ignored->{throw new IllegalStateException("editor unavailable");});
        assertEquals("WorldEdit protection lookup failed",protection.rejection(center));
        assertNotNull(locations.validateForTesting(center,requirements,List.of()));
        when(plugins.isPluginEnabled("WorldGuard")).thenReturn(true);
        protection.register("worldguard",ignored->false);
        assertNull(locations.validateForTesting(center,requirements,List.of())); assertEquals("WorldGuard denied terrain edits",locations.lastRejection);
    }
}

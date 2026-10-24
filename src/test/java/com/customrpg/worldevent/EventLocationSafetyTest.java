package com.customrpg.worldevent;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.customrpg.worldevent.core.WorldEventDefinition;
import com.customrpg.worldevent.location.*;
import org.bukkit.*;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventLocationSafetyTest {
    @TempDir Path directory;
    CustomRPG plugin; World world; NaturalTerrainRegistry terrain; ProtectionValidator protection;
    @BeforeEach void setup() throws Exception {
        plugin=mock(CustomRPG.class); when(plugin.getSafeZoneManager()).thenReturn(mock(SafeZoneManager.class));
        when(plugin.getProtectionAreaManager()).thenReturn(mock(ProtectionAreaManager.class));
        when(plugin.getBossZoneManager()).thenReturn(mock(BossZoneManager.class));
        Server server=mock(Server.class); when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        world=mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL); when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.getHighestBlockYAt(anyInt(),anyInt(),eq(HeightMap.OCEAN_FLOOR))).thenReturn(64);
        WorldBorder border=mock(WorldBorder.class); when(border.isInside(any())).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        org.bukkit.block.Block stone=mock(org.bukkit.block.Block.class),air=mock(org.bukkit.block.Block.class);
        when(stone.getType()).thenReturn(Material.STONE); when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->(int)i.getArgument(1)<=64?stone:air);
        terrain=new NaturalTerrainRegistry(directory.resolve("chunks.yml")); protection=new ProtectionValidator(plugin);
    }
    @Test void spawnSafeZoneAndExistingProtectedRegionsDenyCandidate() {
        Location center=new Location(world,1000,65,1000);
        when(plugin.getSafeZoneManager().isInSafeZone(center)).thenReturn(true);
        assertNotNull(protection.rejection(center));
        when(plugin.getSafeZoneManager().isInSafeZone(center)).thenReturn(false);
        when(plugin.getProtectionAreaManager().isInProtectedArea2D(center)).thenReturn(true);
        assertNotNull(protection.rejection(center));
        when(plugin.getProtectionAreaManager().isInProtectedArea2D(center)).thenReturn(false);
        assertNull(protection.rejection(center));
    }
    @Test void missingOrFailingClaimAdapterFailsClosed() {
        Location center=new Location(world,1000,65,1000);
        when(plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")).thenReturn(true);
        assertTrue(protection.rejection(center).contains("adapter"));
        protection.register("WorldGuard",location->false); assertNotNull(protection.rejection(center));
        protection.register("WorldGuard",location->{throw new IllegalStateException("offline");}); assertNotNull(protection.rejection(center));
        protection.register("WorldGuard",location->{throw new NoClassDefFoundError("incompatible API");});
        assertEquals("WorldGuard protection lookup failed",protection.rejection(center));
        protection.register("WorldGuard",location->true); assertNull(protection.rejection(center));
    }
    @Test void oldLoadedTerrainCanBeUsedWithoutLoadingChunks() {
        var service=new EventLocationService(protection,4); Location center=new Location(world,1000,65,1000);
        assertNull(service.validate(center,new WorldEventDefinition.Requirements(12,4,9,5),List.of()));
        assertTrue(service.lastRejection.contains("unloaded"));
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        assertNotNull(service.validate(center,new WorldEventDefinition.Requirements(12,4,9,5),List.of()));
        assertFalse(terrain.eligible(world.getUID(),62,62));
        verify(world,never()).getChunkAt(anyInt(),anyInt());
        verify(world,never()).loadChunk(anyInt(),anyInt(),anyBoolean());
    }
    @Test void chunkStructureMetadataDoesNotRejectUntouchedSurface() throws Exception {
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        for(int x=61;x<=63;x++) for(int z=61;z<=63;z++) terrain.populated(world.getUID(),x,z);
        Chunk chunk=mock(Chunk.class); when(world.getChunkAt(anyInt(),anyInt())).thenReturn(chunk);
        when(chunk.getStructures()).thenReturn(List.of(mock(GeneratedStructure.class)));
        var service=new EventLocationService(protection,4);
        assertNotNull(service.validate(new Location(world,1000,65,1000),new WorldEventDefinition.Requirements(12,4,9,5),List.of()));
        verify(world,never()).getChunkAt(anyInt(),anyInt());
    }
    @Test void observedNewTerrainAndPlayerEditRevocationSurviveRestart() throws Exception {
        UUID id=world.getUID(); assertFalse(terrain.eligible(id,1,2));
        terrain.populated(id,1,2); assertTrue(terrain.eligible(id,1,2));
        var restarted=new NaturalTerrainRegistry(directory.resolve("chunks.yml")); assertTrue(restarted.eligible(id,1,2));
        restarted.revoke(id,1,2);
        assertFalse(new NaturalTerrainRegistry(directory.resolve("chunks.yml")).eligible(id,1,2));
    }
    @Test void oreCanBeRestoredButTileEntitiesAndFluidsAreNotExcavated() {
        var ore=mock(org.bukkit.block.Block.class);
        when(ore.getType()).thenReturn(Material.COPPER_ORE);
        when(ore.getBlockData()).thenReturn(mock(org.bukkit.block.data.BlockData.class));
        when(ore.getState()).thenReturn(mock(org.bukkit.block.BlockState.class));
        assertTrue(TerrainValidator.canTemporarilyReplace(ore));
        var barrel=mock(org.bukkit.block.Block.class);
        when(barrel.getType()).thenReturn(Material.BARREL);
        when(barrel.getState()).thenReturn(mock(org.bukkit.block.TileState.class));
        assertFalse(TerrainValidator.canTemporarilyReplace(barrel));
        var water=mock(org.bukkit.block.Block.class);
        when(water.getType()).thenReturn(Material.WATER);
        assertFalse(TerrainValidator.canTemporarilyReplace(water));
    }
    @Test void wrongDimensionAndOverlappingEventAreRejectedBeforeWorldAccess() {
        var service=new EventLocationService(protection,4); var e=WorldEventLifecycleTest.event();
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        assertNull(service.validate(new Location(world,1000,65,1000),new WorldEventDefinition.Requirements(12,4,9,5),List.of()));
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL); when(world.getUID()).thenReturn(e.world);
        assertNull(service.validate(new Location(world,e.x,e.y,e.z),new WorldEventDefinition.Requirements(12,4,9,5),List.of(e)));
        assertTrue(service.lastRejection.contains("Overlapping"));
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
}

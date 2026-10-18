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
    @Test void unobservedOrUnloadedChunksNeverCauseChunkLoad() {
        var service=new EventLocationService(terrain,protection,4); Location center=new Location(world,1000,65,1000);
        assertNull(service.validate(center,new WorldEventDefinition.Requirements(12,4,9,5),List.of()));
        assertTrue(service.lastRejection.contains("unloaded"));
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        assertNull(service.validate(center,new WorldEventDefinition.Requirements(12,4,9,5),List.of()));
        assertTrue(service.lastRejection.contains("Unknown"));
        verify(world,never()).getChunkAt(anyInt(),anyInt());
        verify(world,never()).loadChunk(anyInt(),anyInt(),anyBoolean());
    }
    @Test void villageOrAnyVanillaStructureRejectsCandidate() throws Exception {
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        for(int x=61;x<=63;x++) for(int z=61;z<=63;z++) terrain.populated(world.getUID(),x,z);
        Chunk chunk=mock(Chunk.class); when(world.getChunkAt(anyInt(),anyInt())).thenReturn(chunk);
        when(chunk.getStructures()).thenReturn(List.of(mock(GeneratedStructure.class)));
        var service=new EventLocationService(terrain,protection,4);
        assertNull(service.validate(new Location(world,1000,65,1000),new WorldEventDefinition.Requirements(12,4,9,5),List.of()));
        assertTrue(service.lastRejection.contains("structure"));
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
    @Test void observedNewTerrainAndPlayerEditRevocationSurviveRestart() throws Exception {
        UUID id=world.getUID(); assertFalse(terrain.eligible(id,1,2));
        terrain.populated(id,1,2); assertTrue(terrain.eligible(id,1,2));
        var restarted=new NaturalTerrainRegistry(directory.resolve("chunks.yml")); assertTrue(restarted.eligible(id,1,2));
        restarted.revoke(id,1,2);
        assertFalse(new NaturalTerrainRegistry(directory.resolve("chunks.yml")).eligible(id,1,2));
    }
    @Test void whitelistRejectsComplexAndPlayerBuildingMaterials() {
        for(Material bad:List.of(Material.CHEST,Material.BARREL,Material.SPAWNER,Material.NETHER_PORTAL,Material.SAND,
                Material.GRAVEL,Material.WATER,Material.LAVA,Material.OAK_LOG,Material.OAK_PLANKS,Material.TALL_GRASS,Material.OAK_SLAB)) {
            assertFalse(TerrainValidator.accepts(bad),bad.toString());
        }
        assertTrue(TerrainValidator.accepts(Material.SHORT_GRASS)); assertFalse(TerrainValidator.stableGround(Material.SHORT_GRASS));
        assertTrue(TerrainValidator.stableGround(Material.GRASS_BLOCK));
    }
    @Test void wrongDimensionAndOverlappingEventAreRejectedBeforeWorldAccess() {
        var service=new EventLocationService(terrain,protection,4); var e=WorldEventLifecycleTest.event();
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        assertNull(service.validate(new Location(world,1000,65,1000),new WorldEventDefinition.Requirements(12,4,9,5),List.of()));
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL); when(world.getUID()).thenReturn(e.world);
        assertNull(service.validate(new Location(world,e.x,e.y,e.z),new WorldEventDefinition.Requirements(12,4,9,5),List.of(e)));
        assertTrue(service.lastRejection.contains("Overlapping"));
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
}

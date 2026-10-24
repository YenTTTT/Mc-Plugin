package com.customrpg.worldevent;

import com.customrpg.worldevent.core.*;
import com.customrpg.worldevent.location.*;
import com.customrpg.worldevent.meteor.*;
import com.customrpg.worldevent.terrain.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeteorArenaTest {
    private MeteorImpactEvent definition() throws Exception {
        var yaml=new YamlConfiguration(); yaml.load(Path.of("src/main/resources/config/world_events.yml").toFile());
        return new MeteorImpactEvent(yaml.getConfigurationSection("world-events.meteor"));
    }
    private World world() {
        World world=mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL); when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        WorldBorder border=mock(WorldBorder.class); when(border.isInside(any())).thenReturn(true); when(world.getWorldBorder()).thenReturn(border);
        return world;
    }
    @ParameterizedTest @ValueSource(ints={63,110,240})
    void oceanCliffAndBuildingAllGetFlatArenaAboveHighestColumn(int highest) throws Exception {
        World world=world();
        when(world.getHighestBlockYAt(anyInt(),anyInt(),eq(HeightMap.WORLD_SURFACE))).thenAnswer(i->(int)i.getArgument(0)==5?highest:63);
        var service=new EventLocationService(mock(ProtectionValidator.class),4); var definition=definition();
        var site=service.validateArena(new Location(world,0,65,0),definition.requirements(),List.of(),false,definition.arenaClearance());
        assertNotNull(site); assertEquals(highest+10,site.center().getBlockY());
        var air=mock(Block.class); when(air.getType()).thenReturn(Material.AIR); when(air.isEmpty()).thenReturn(true);
        var data=mock(BlockData.class); when(data.getAsString()).thenReturn("minecraft:air"); when(air.getBlockData()).thenReturn(data);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->{
            assertTrue((int)i.getArgument(1)>highest,"Existing terrain was touched"); return air;
        });
        var snapshot=service.snapshot(UUID.randomUUID(),definition,site,42);
        assertFalse(snapshot.blocks().isEmpty());
        assertTrue(snapshot.blocks().stream().allMatch(b->b.before().equals("minecraft:air")&&!b.after().equals("minecraft:air")));
        assertTrue(snapshot.blocks().stream().allMatch(b->b.position().y()>=highest+7));
        assertTrue(snapshot.blocks().size()<2500);
        verify(world,never()).getNearbyEntities(any(Location.class),anyDouble(),anyDouble(),anyDouble());
    }
    @Test void islandHasContinuousWalkableFloorRimAndSeededCoreWithoutDamagingMagmaFloor() {
        var generator=new MeteorArenaGenerator(); var plan=generator.generate(0,100,0,12,3,3,42);
        assertEquals(plan,generator.generate(0,100,0,12,3,3,42));
        assertNotEquals(plan,generator.generate(0,100,0,12,3,3,43));
        for(int x=-9;x<=9;x++) for(int z=-9;z<=9;z++) if(Math.hypot(x,z)<=9)
            assertNotNull(plan.get(new TerrainSnapshot.Position(x,99,z)));
        assertNotNull(plan.get(new TerrainSnapshot.Position(11,101,0)));
        assertNotNull(plan.get(new TerrainSnapshot.Position(0,103,0)));
        assertTrue(plan.keySet().stream().allMatch(p->Math.abs(p.x())<=12&&Math.abs(p.z())<=12&&p.y()>=97&&p.y()<=106));
        assertFalse(plan.values().contains("minecraft:air")); assertFalse(plan.values().contains("minecraft:magma_block"));
    }
    @Test void arenaNeverSilentlySkipsNewObstructionAndStillChecksClaimsHeightAndLoadedChunks() throws Exception {
        World world=world(); var protection=mock(ProtectionValidator.class); var service=new EventLocationService(protection,4);
        var definition=definition(); var origin=new Location(world,0,65,0);
        when(world.getHighestBlockYAt(anyInt(),anyInt(),eq(HeightMap.WORLD_SURFACE))).thenReturn(64);
        var site=service.validateArena(origin,definition.requirements(),List.of(),false,6); assertNotNull(site);
        var block=mock(Block.class); when(block.getType()).thenReturn(Material.CHEST); when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);
        assertThrows(IllegalArgumentException.class,()->service.snapshot(UUID.randomUUID(),definition,site,42));
        when(protection.rejection(any())).thenReturn("Claim denied");
        assertNull(service.validateArena(origin,definition.requirements(),List.of(),false,6)); assertEquals("Claim denied",service.lastRejection);
        when(protection.rejection(any())).thenReturn(null);
        when(world.getHighestBlockYAt(anyInt(),anyInt(),eq(HeightMap.WORLD_SURFACE))).thenReturn(310);
        assertNull(service.validateArena(origin,definition.requirements(),List.of(),false,6)); assertEquals("Build height unsuitable",service.lastRejection);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertNull(service.validateArena(origin,definition.requirements(),List.of(),false,6)); assertTrue(service.lastRejection.contains("unloaded"));
        verify(world,never()).getChunkAt(anyInt(),anyInt());
    }
    @Test void generatedArenaDoesNotProtectOrFreezeTheOriginalGroundBelowIt() {
        var e=WorldEventLifecycleTest.event(); e.generatedArena=true; e.arenaDepth=3;
        assertFalse(e.occupies(e.world,e.x,e.y-9,e.z,4));
        assertTrue(e.occupies(e.world,e.x,e.y,e.z,4));
        assertTrue(e.occupies(e.world,e.x,e.y-3,e.z,4));
        e.generatedArena=false; assertTrue(e.occupies(e.world,e.x,e.y-100,e.z,4));
    }
}

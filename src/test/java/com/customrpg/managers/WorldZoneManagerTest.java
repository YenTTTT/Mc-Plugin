package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldZoneManagerTest {
    @TempDir Path directory;
    WorldZoneManager manager;
    World world;

    @BeforeEach void setup() throws Exception {
        Path file = directory.resolve("config/world_zones.yml");
        Files.createDirectories(file.getParent());
        Files.copy(Path.of("src/main/resources/config/world_zones.yml"), file);
        CustomRPG plugin = mock(CustomRPG.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        manager = new WorldZoneManager(plugin);
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getSpawnLocation()).thenAnswer(i -> new Location(world, 1000, 80, -500));
    }

    @Test void usesHorizontalDistanceFromEachWorldSpawnAtEveryBoundary() {
        int[] boundaries = {150, 700, 1600, 2800, 4500, 6500, 8500};
        String[] ids = {"starter", "frontier", "wilderness", "danger", "frontier_depths", "endgame", "endgame"};
        assertTrue(manager.isSafeZone(new Location(world, 1149, -50, -500)));
        assertFalse(manager.isSafeZone(new Location(world, 1150, 300, -500)));
        for (int i = 0; i < boundaries.length; i++) {
            Location location = new Location(world, 1000 + boundaries[i], 250, -500);
            assertEquals(ids[i], manager.getZone(location).id());
            assertEquals(boundaries[i], manager.getDistance(location));
            assertEquals(manager.getBaseLevel(location), manager.getBaseLevel(location.clone().add(0, -300, 0)));
        }
    }

    @Test void levelVariationNeverEscapesZoneAndRespectsDimensionMinimum() {
        for (int distance = 150; distance <= 10_000; distance += 11) {
            Location location = new Location(world, 1000 + distance, 64, -500);
            int level = manager.rollLevel(location);
            var zone = manager.getZone(location);
            assertTrue(level >= zone.minLevel() && level <= zone.maxLevel());
        }
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        assertFalse(manager.isSafeZone(new Location(world, 1000, 64, -500)));
        assertEquals(30, manager.getBaseLevel(new Location(world, 1000, 64, -500)));
        when(world.getEnvironment()).thenReturn(World.Environment.THE_END);
        assertEquals(50, manager.getBaseLevel(new Location(world, 1000, 64, -500)));
    }

    @Test void invalidReloadRetainsPreviouslyValidatedZones() throws Exception {
        Files.writeString(directory.resolve("config/world_zones.yml"), "zones: {}\n");
        assertThrows(IllegalArgumentException.class, manager::reload);
        assertEquals("frontier", manager.getZone(new Location(world, 1700, 64, -500)).id());
    }
}

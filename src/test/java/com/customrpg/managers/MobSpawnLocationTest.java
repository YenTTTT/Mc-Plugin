package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobSpawnLocationTest {
    @Test void exactSpawnRejectsOutOfWorldHeightWithoutMovingOrLoadingChunks() {
        CustomRPG plugin = mock(CustomRPG.class);
        when(plugin.getName()).thenReturn("CustomRPG");
        when(plugin.namespace()).thenReturn("customrpg");
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        ConfigManager config = mock(ConfigManager.class);
        when(config.getAllMobs()).thenReturn(Map.of());
        MobManager manager = new MobManager(plugin, config);
        World world = mock(World.class);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        assertNull(manager.findSafeSpawnLocationExact(new Location(world, 0, -100, 0)));
        assertNull(manager.findSafeSpawnLocationExact(new Location(world, 0, 400, 0)));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
        verify(world, never()).getHighestBlockYAt(anyInt(), anyInt());
    }
}

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
    @Test void eventTestSkipsSpawnSafeZoneWhileRetainingCustomRegionsAndCollision() {
        var plugin=mock(CustomRPG.class); when(plugin.getName()).thenReturn("CustomRPG"); when(plugin.namespace()).thenReturn("customrpg");
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var config=mock(ConfigManager.class); when(config.getAllMobs()).thenReturn(Map.of());
        var safe=mock(SafeZoneManager.class); when(plugin.getSafeZoneManager()).thenReturn(safe); when(safe.isInSafeZone(any())).thenReturn(true);
        var protection=mock(ProtectionAreaManager.class); when(plugin.getProtectionAreaManager()).thenReturn(protection);
        var manager=new MobManager(plugin,config); World world=mock(World.class);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true); when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        var border=mock(org.bukkit.WorldBorder.class); when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        var feet=mock(org.bukkit.block.Block.class); var head=mock(org.bukkit.block.Block.class); var ground=mock(org.bukkit.block.Block.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(feet);
        when(feet.getRelative(org.bukkit.block.BlockFace.UP)).thenReturn(head); when(feet.getRelative(org.bukkit.block.BlockFace.DOWN)).thenReturn(ground);
        var air=mock(org.bukkit.Material.class); when(air.isAir()).thenReturn(true);
        var stone=mock(org.bukkit.Material.class); when(stone.isSolid()).thenReturn(true);
        when(feet.getType()).thenReturn(air); when(head.getType()).thenReturn(air); when(ground.getType()).thenReturn(stone);
        var location=new Location(world,100,65,100);
        assertNull(manager.findSafeSpawnLocationExact(location)); assertNotNull(manager.findSafeSpawnLocationExactForTesting(location));
        when(protection.isInProtectedArea2D(any())).thenReturn(true); assertNull(manager.findSafeSpawnLocationExactForTesting(location));
        when(protection.isInProtectedArea2D(any())).thenReturn(false); when(feet.getType()).thenReturn(stone);
        assertNull(manager.findSafeSpawnLocationExactForTesting(location));
    }
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

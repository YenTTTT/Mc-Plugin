package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.mobs.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.*;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobAppearanceSpawnTest {
    CustomRPG plugin; MobManager manager; MobEncounterManager encounters; World world; LivingEntity actor;
    Location location; boolean exposed; Map<NamespacedKey,Object> tags;
    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class); when(plugin.getName()).thenReturn("CustomRPG"); when(plugin.namespace()).thenReturn("customrpg");
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        var profiles = mock(MobProfileRegistry.class); when(plugin.getMobProfileRegistry()).thenReturn(profiles);
        var profile = new MobProfile("bear", EntityType.PANDA, 1.3, 0xaa6633, "DUST", Map.of(), List.of(MobAbility.CHARGE), 8, 1, 3, 12, 1, 0.35, 0, "aggressive", false, "bear");
        when(profiles.get("bear")).thenReturn(profile); encounters = mock(MobEncounterManager.class); when(plugin.getMobEncounterManager()).thenReturn(encounters);
        var config = mock(ConfigManager.class); when(config.getAllMobs()).thenReturn(Map.of("bear", Map.of("type", "ZOMBIE", "base-stats", Map.of("health", 20.0), "level-range", Map.of("min", 1, "max", 20))));
        manager = new MobManager(plugin, config);
        world = mock(World.class); when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true); when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        var border = mock(WorldBorder.class); when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        Block feet = mock(Block.class), head = mock(Block.class), ground = mock(Block.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(feet); when(feet.getRelative(BlockFace.UP)).thenReturn(head); when(feet.getRelative(BlockFace.DOWN)).thenReturn(ground);
        var air = mock(Material.class); when(air.isAir()).thenReturn(true); var stone = mock(Material.class); when(stone.isSolid()).thenReturn(true);
        when(feet.getType()).thenReturn(air); when(head.getType()).thenReturn(air); when(ground.getType()).thenReturn(stone);
        actor = mock(LivingEntity.class); when(actor.getUniqueId()).thenReturn(UUID.randomUUID()); when(actor.isValid()).thenAnswer(i -> exposed);
        when(actor.getBoundingBox()).thenReturn(new BoundingBox(0.1, 64, 0.1, 0.9, 66, 0.9));
        tags = new HashMap<>(); var data = mock(PersistentDataContainer.class); when(actor.getPersistentDataContainer()).thenReturn(data);
        doAnswer(i -> { tags.put(i.getArgument(0), i.getArgument(2)); return null; }).when(data).set(any(), any(), any());
        when(world.spawnEntity(any(Location.class), eq(EntityType.PANDA), eq(SpawnReason.CUSTOM), any())).thenAnswer(i -> {
            Consumer<Entity> initializer = i.getArgument(3); initializer.accept(actor); exposed = true; return actor;
        });
        location = new Location(world, 0.5, 64, 0.5);
    }
    @Test void appearanceOverridesOldDeployedZombieAndReservesIdentityBeforeExposure() {
        try (var health = mockStatic(MobManager.class, CALLS_REAL_METHODS)) {
            health.when(() -> MobManager.applyMobMaxHealth(any(LivingEntity.class), anyDouble())).thenAnswer(i -> null);
            LivingEntity result = manager.spawnReservedMob("bear", location, 10, mob -> {
                assertFalse(exposed); assertEquals("bear", tags.get(new NamespacedKey("customrpg", "custom_mob_type")));
                assertEquals(10, tags.get(new NamespacedKey("customrpg", "mob_level")));
            });
            assertSame(actor, result); assertTrue(exposed); assertEquals(EntityType.PANDA, manager.getActualSpawnEntityType("bear"));
            verify(encounters).track(actor);
        }
    }
    @Test void enlargedBodyCollisionIsRejectedBeforeReservationAndExposure() {
        when(actor.collidesAt(any())).thenReturn(true);
        assertNull(manager.spawnReservedMob("bear", location, 10, mob -> fail("Must not reserve a colliding actor")));
        assertFalse(exposed); verify(encounters, never()).track(any());
    }
    @Test void fullBodyFootprintCannotLoadAnAdjacentChunk() {
        location = new Location(world, 15.5, 64, 0.5); when(actor.getBoundingBox()).thenReturn(new BoundingBox(14.8, 64, 0.1, 16.2, 66, 0.9));
        when(world.isChunkLoaded(1, 0)).thenReturn(false);
        assertNull(manager.spawnReservedMob("bear", location, 10, mob -> fail("Unloaded body corner"))); assertFalse(exposed);
        verify(world, never()).getChunkAt(anyInt(), anyInt()); verify(actor, never()).collidesAt(any());
    }
}

package com.customrpg.mobs;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WildernessBossManagerTest {
    @TempDir Path directory;
    CustomRPG plugin; MobManager mobs; DistanceSpawnManager spawner; WildernessBossManager manager;
    World world; Server server; MobManager.MobData template; Random random;
    AtomicLong clock = new AtomicLong(1000); List<LivingEntity> spawned = new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        Files.createDirectories(directory.resolve("config"));
        Files.copy(Path.of("src/main/resources/config/wilderness_bosses.yml"), directory.resolve("config/wilderness_bosses.yml"));
        plugin = mock(CustomRPG.class); when(plugin.getDataFolder()).thenReturn(directory.toFile()); when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        server = mock(Server.class); when(plugin.getServer()).thenReturn(server);
        mobs = mock(MobManager.class); when(plugin.getMobManager()).thenReturn(mobs);
        spawner = mock(DistanceSpawnManager.class); when(spawner.isSpawnAllowed(any())).thenReturn(true);
        world = mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        template = mock(MobManager.MobData.class); when(template.isBoss()).thenReturn(true); when(template.getTags()).thenReturn(List.of("forest", "wilderness-boss"));
        when(template.getMinLevel()).thenReturn(8); when(template.getMaxLevel()).thenReturn(60); when(mobs.getMobData("boss")).thenReturn(template);
        random = mock(Random.class); when(random.nextDouble()).thenReturn(0.0);
        manager = create();
        when(mobs.spawnReservedMob(eq("boss"), any(), anyInt(), any())).thenAnswer(invocation -> {
            // Initial cooldown is durable BEFORE an actor can be exposed.
            assertTrue(Files.exists(stateFile()));
            var mob = mock(LivingEntity.class); when(mob.getUniqueId()).thenReturn(UUID.randomUUID()); when(mob.isValid()).thenReturn(true);
            when(mob.getWorld()).thenReturn(world); when(mob.getCustomName()).thenReturn("首領");
            Map<NamespacedKey,Object> values = new HashMap<>(); var data = mock(PersistentDataContainer.class);
            doAnswer(i -> { values.put(i.getArgument(0), i.getArgument(2)); return null; }).when(data).set(any(), any(), any());
            when(data.get(any(), any())).thenAnswer(i -> values.get(i.getArgument(0))); when(mob.getPersistentDataContainer()).thenReturn(data);
            Consumer<LivingEntity> before = invocation.getArgument(3); before.accept(mob);
            assertTrue(Files.readString(stateFile()).contains(mob.getUniqueId().toString()));
            spawned.add(mob); return mob;
        });
    }
    WildernessBossManager create() { return new WildernessBossManager(plugin, spawner, clock::get, random); }
    Path stateFile() { return directory.resolve("data/wilderness_bosses.yml"); }
    Location at(int x) { return new Location(world, x, 64, 100); }
    @Test void reservationAndCooldownSurviveFreshManagerAndNormalDeath() {
        assertEquals("boss", manager.trySpawn(at(100), 20, List.of("boss"))); assertEquals(1, spawned.size());
        assertNull(create().trySpawn(at(100), 20, List.of("boss")));
        var death = mock(EntityDeathEvent.class); when(death.getEntity()).thenReturn(spawned.getFirst()); manager.onDeath(death);
        assertNull(create().trySpawn(at(100), 20, List.of("boss"))); verify(spawner, times(1)).trackWildernessBoss(any(), any(), eq(20));
    }
    @Test void registrationFailureRemovesTheActorAndRetainsDurableCooldown() throws Exception {
        doThrow(new IllegalStateException("boss bar unavailable")).when(spawner).trackWildernessBoss(any(), any(), anyInt());
        assertNull(manager.trySpawn(at(100), 20, List.of("boss")));
        verify(spawned.getFirst()).remove();
        assertNull(create().trySpawn(at(100), 20, List.of("boss")));
        assertFalse(Files.readString(stateFile()).contains(spawned.getFirst().getUniqueId().toString()));
    }
    @Test void unloadedBossesStillOccupyWorldQuotaUntilExpiry() {
        manager.trySpawn(at(100), 20, List.of("boss")); manager.trySpawn(at(1200), 20, List.of("boss"));
        assertNull(manager.trySpawn(at(2400), 20, List.of("boss"))); assertEquals(2, spawned.size());
        verify(server, never()).getWorld(any(UUID.class)); // No force-loading a world/chunk to enforce the cap.
    }
    @Test void expiredUnloadedBossIsRemovedWhenItsChunkReturns() {
        manager.trySpawn(at(100), 20, List.of("boss")); LivingEntity old = spawned.getFirst();
        clock.addAndGet(1801000); manager.tick();
        assertEquals("boss", manager.trySpawn(at(100), 20, List.of("boss")));
        var load = mock(EntitiesLoadEvent.class); when(load.getEntities()).thenReturn(List.of(old)); manager.onLoad(load); verify(old).remove();
    }
    @Test void writeFailureNeverCreatesAnActorOrOverwritesExistingState() throws Exception {
        Files.createDirectories(directory.resolve("data/wilderness_bosses.yml.tmp"));
        assertNull(manager.trySpawn(at(100), 20, List.of("boss")));
        verify(mobs, never()).spawnReservedMob(any(), any(), anyInt(), any()); assertTrue(manager.diagnostic().contains("資料暫停"));
    }
    @Test void corruptedStatePausesOnlyBossEncountersAndPreservesBytes() throws Exception {
        Files.createDirectories(stateFile().getParent()); Files.writeString(stateFile(), "records: [broken"); String bad = Files.readString(stateFile());
        var restarted = create(); assertNull(restarted.trySpawn(at(100), 20, List.of("boss"))); assertEquals(bad, Files.readString(stateFile()));
    }
    @Test void eventOnlyBossAndWrongLevelCannotEnterTheRoamingPool() {
        assertNull(manager.trySpawn(at(100), 3, List.of("boss")));
        when(template.getTags()).thenReturn(List.of("wilderness-boss", "event-only"));
        assertNull(manager.trySpawn(at(100), 20, List.of("boss"))); verify(mobs, never()).spawnReservedMob(any(), any(), anyInt(), any());
    }
    @Test void protectionAndChanceAreRespectedWithoutAReservation() {
        when(spawner.isSpawnAllowed(any())).thenReturn(false); assertNull(manager.trySpawn(at(100), 20, List.of("boss")));
        when(spawner.isSpawnAllowed(any())).thenReturn(true); when(random.nextDouble()).thenReturn(0.5);
        assertNull(manager.trySpawn(at(100), 20, List.of("boss"))); assertFalse(Files.exists(stateFile()));
    }
    @Test void negativeCoordinatesUseFloorDivisionForIndependentRegions() {
        assertEquals("boss", manager.trySpawn(at(-1), 20, List.of("boss")));
        assertNull(manager.trySpawn(at(-1024), 20, List.of("boss")));
        assertEquals("boss", manager.trySpawn(at(1), 20, List.of("boss"))); assertEquals(2, spawned.size());
    }
    @Test void invalidConfigurationCannotReplaceTheStateFile() throws Exception {
        manager.trySpawn(at(100), 20, List.of("boss")); String original = Files.readString(stateFile());
        var config = new YamlConfiguration(); config.load(directory.resolve("config/wilderness_bosses.yml").toFile());
        config.set("chance-per-spawn", 1); config.save(directory.resolve("config/wilderness_bosses.yml").toFile());
        assertFalse(manager.reload()); assertEquals(original, Files.readString(stateFile()));
    }
}

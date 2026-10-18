package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real configuration and replacement routing, with world/entity operations mocked. */
class DistanceSpawnManagerTest {
    @TempDir Path directory;
    CustomRPG plugin; MobManager mobs; SafeZoneManager safe; WorldZoneManager zones;
    World world; Player player; LivingEntity original; DistanceSpawnManager manager;
    boolean vanillaSpawning = true;
    @BeforeEach void setup() throws Exception {
        Files.createDirectories(directory.resolve("config"));
        Files.copy(Path.of("src/main/resources/config/mob_spawner.yml"), directory.resolve("config/mob_spawner.yml"));
        Files.copy(Path.of("src/main/resources/config/wilderness_bosses.yml"), directory.resolve("config/wilderness_bosses.yml"));
        plugin = mock(CustomRPG.class); when(plugin.getName()).thenReturn("CustomRPG"); when(plugin.namespace()).thenReturn("customrpg");
        when(plugin.getDataFolder()).thenReturn(directory.toFile()); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        Server server = mock(Server.class); when(plugin.getServer()).thenReturn(server);
        BukkitScheduler scheduler = mock(BukkitScheduler.class); when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        mobs = mock(MobManager.class); safe = mock(SafeZoneManager.class); zones = mock(WorldZoneManager.class);
        when(plugin.getWorldZoneManager()).thenReturn(zones);
        when(mobs.getCustomMobNamespacedKey()).thenReturn(new NamespacedKey("customrpg", "custom_mob_type"));
        world = mock(World.class); when(world.getName()).thenReturn("world_nether"); when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(world.getDifficulty()).thenReturn(Difficulty.NORMAL); when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getAllowMonsters()).thenReturn(true); when(world.getSpawnLimit(SpawnCategory.MONSTER)).thenReturn(70);
        var border = mock(WorldBorder.class); when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        Chunk chunk = mock(Chunk.class); when(chunk.getEntities()).thenReturn(new Entity[0]);
        when(world.getChunkAt(any(Location.class))).thenReturn(chunk); when(world.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
        Block block = mock(Block.class); when(world.getBlockAt(any(Location.class))).thenReturn(block);
        player = mock(Player.class); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL); when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenAnswer(i -> new Location(world, 250, 64, 0));
        when(world.getNearbyPlayers(any(Location.class), eq(128.0))).thenReturn(List.of(player));
        original = mock(LivingEntity.class); when(original.isValid()).thenReturn(true);
        when(original.getLocation()).thenAnswer(i -> new Location(world, 275, 64, 0));
        when(mobs.findSafeSpawnLocationExact(any())).thenAnswer(i -> i.getArgument(0));
        when(zones.getBaseLevel(any())).thenReturn(30); when(zones.rollLevel(any())).thenReturn(30);
        when(zones.clampLevel(any(), anyInt())).thenReturn(30);
        when(zones.getOrigin(world)).thenReturn(new Location(world, 0, 64, 0));
        when(zones.getDescription(any())).thenReturn("test zone");
        manager = spy(new DistanceSpawnManager(plugin, mobs, safe, ignored -> vanillaSpawning));
    }
    String status() { return ChatColor.stripColor(String.join("\n", manager.getDebugStatus(player))); }
    void pool(String time, List<String> tags, int min, int max) {
        var data = mock(MobManager.MobData.class); when(data.getSpawnTime()).thenReturn(time); when(data.getTags()).thenReturn(tags);
        when(data.getMinLevel()).thenReturn(min); when(data.getMaxLevel()).thenReturn(max);
        when(mobs.getNormalMobKeys()).thenReturn(List.of("candidate")); when(mobs.getMobData("candidate")).thenReturn(data);
    }
    @Test void restartRequiresExplicitActivationAndStatusExplainsIt() {
        assertFalse(manager.isActive()); assertFalse(manager.handlesNaturalSpawn(SpawnReason.NATURAL));
        assertTrue(status().contains("請先執行 /finishrpg"));
        manager.activateSystem(); assertTrue(manager.handlesNaturalSpawn(SpawnReason.NATURAL));
        assertFalse(manager.handlesNaturalSpawn(SpawnReason.COMMAND)); assertFalse(manager.handlesNaturalSpawn(SpawnReason.CUSTOM));
    }
    @Test void reloadNormalizesSpawnReasonsAndCannotEnableRecursiveCustomSpawns() throws Exception {
        Files.writeString(directory.resolve("config/mob_spawner.yml"), "enabled: true\nmode: vanilla\nnatural-spawn-reasons: [' natural ', 'custom', 'command', 'not_a_reason']\n");
        manager.activateSystem(); manager.reload();
        assertTrue(manager.isActive()); assertTrue(manager.handlesNaturalSpawn(SpawnReason.NATURAL));
        assertFalse(manager.handlesNaturalSpawn(SpawnReason.CUSTOM)); assertFalse(manager.handlesNaturalSpawn(SpawnReason.COMMAND));
    }
    @Test void worldDiagnosticsExplainIndependentVanillaBlockersWithoutChangingSettings() {
        vanillaSpawning = false;
        when(world.getAllowMonsters()).thenReturn(false); when(world.getSpawnLimit(SpawnCategory.MONSTER)).thenReturn(0);
        String warnings = String.join("\n", manager.getSpawnWarnings(player));
        assertTrue(warnings.contains("doMobSpawning=false")); assertTrue(warnings.contains("allowMonsters=false")); assertTrue(warnings.contains("上限為 0"));
        verify(world, never()).setGameRule(any(), any()); verify(world, never()).setDifficulty(any());
    }
    @Test void modeAndSafeZoneWarningsAreVisibleEvenWithoutNaturalEvents() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE); when(safe.isInSafeZone(any())).thenReturn(true);
        String value = status(); assertTrue(value.contains("創造／旁觀")); assertTrue(value.contains("位於安全區"));
        assertTrue(value.contains("未觀測到接管來源的敵怪事件"));
    }
    @Test void cancelledObservationsAreCountedWithoutSpawning() {
        manager.observeNaturalSpawn(SpawnReason.NATURAL, true);
        assertTrue(status().contains("1 / 1 / 0")); verify(mobs, never()).spawnCustomMobWithLevelExact(any(), any(), anyInt());
        manager.observeNaturalSpawn(SpawnReason.CUSTOM, false); assertTrue(status().contains("1 / 1 / 0"));
    }
    @Test void successfulReplacementRemovesOriginalOnlyAfterCustomSpawnSucceeds() {
        pool("all", List.of("nether"), 30, 40); manager.activateSystem();
        doAnswer(i -> { verify(original, never()).remove(); return true; }).when(manager).spawnTieredMob(eq("candidate"), any(), eq(30), any());
        manager.replaceNaturalMob(original, SpawnReason.NATURAL); verify(original).remove();
        assertTrue(status().contains("自然生成替換: candidate"));
    }
    @Test void failedCustomSpawnPreservesOriginalAndReportsFailure() {
        pool("all", List.of("nether"), 30, 40); manager.activateSystem();
        doReturn(false).when(manager).spawnTieredMob(any(), any(), anyInt(), any());
        manager.replaceNaturalMob(original, SpawnReason.NATURAL); verify(original, never()).remove();
        assertTrue(status().contains("自訂怪生成失敗或被取消"));
    }
    @Test void exactLocationFailurePreservesOriginalWithoutSearchingAnotherHeight() {
        manager.activateSystem(); when(mobs.findSafeSpawnLocationExact(any())).thenReturn(null);
        manager.replaceNaturalMob(original, SpawnReason.NATURAL); verify(original, never()).remove();
        assertTrue(status().contains("原版生成位置不適用")); verify(mobs, never()).findSafeSpawnLocation(any());
    }
    @Test void missingEligiblePlayerIsReportedInsteadOfSilentReturn() {
        manager.activateSystem(); when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        manager.replaceNaturalMob(original, SpawnReason.NATURAL); verify(original, never()).remove();
        assertTrue(status().contains("128 格內沒有生存／冒險玩家"));
    }
    @Test void missingTemplateShowsWhichPoolStageBecameEmptyAndDoesNotFallback() {
        pool("all", List.of("forest"), 1, 60); manager.activateSystem();
        manager.replaceNaturalMob(original, SpawnReason.NATURAL); verify(original, never()).remove();
        assertTrue(status().contains("普通 1 → 時段 1 → 環境 0 → 等級 0"));
        verify(manager, never()).spawnTieredMob(any(), any(), anyInt(), any());
    }
    @Test void outOfLevelAndWrongTimeDoNotUseUnrelatedTemplates() {
        pool("night", List.of("nether"), 30, 40); when(world.getTime()).thenReturn(6000L); manager.activateSystem();
        manager.replaceNaturalMob(original, SpawnReason.NATURAL); assertTrue(status().contains("普通 1 → 時段 0"));
        pool("all", List.of("nether"), 40, 60);
        manager.replaceNaturalMob(original, SpawnReason.NATURAL); assertTrue(status().contains("環境 1 → 等級 0"));
        verify(original, never()).remove();
    }
    @Test void supplementalModeNeverAlsoReplacesNaturalSpawns() throws Exception {
        Files.writeString(directory.resolve("config/mob_spawner.yml"), "enabled: true\nmode: supplemental\n");
        manager.activateSystem(); manager.reload(); assertTrue(manager.isActive());
        assertFalse(manager.handlesNaturalSpawn(SpawnReason.NATURAL));
    }
}

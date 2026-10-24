package com.customrpg.worldevent;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.customrpg.world.DistanceZone;
import com.customrpg.worldevent.core.*;
import com.customrpg.worldevent.location.EventLocationService;
import com.customrpg.worldevent.persistence.WorldEventRepository;
import com.customrpg.worldevent.terrain.TerrainSnapshot;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldEventManagerRecoveryTest {
    @TempDir Path directory;
    CustomRPG plugin; Server server; World world; BukkitScheduler scheduler; BukkitTask task;
    Map<TerrainSnapshot.Position,String> blocks=new HashMap<>();
    WorldEventInstance event;
    Path data;
    int writes;

    @BeforeEach void setup() throws Exception {
        Path config=directory.resolve("config/world_events.yml"); Files.createDirectories(config.getParent());
        String text=Files.readString(Path.of("src/main/resources/config/world_events.yml")).replace("blocks-per-tick: 128","blocks-per-tick: 1");
        Files.writeString(config,text);
        plugin=mock(CustomRPG.class); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        server=mock(Server.class); when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        scheduler=mock(BukkitScheduler.class); when(server.getScheduler()).thenReturn(scheduler);
        task=mock(BukkitTask.class); when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),eq(1L),eq(1L))).thenReturn(task);
        event=WorldEventLifecycleTest.event(); world=mock(World.class);
        when(world.getUID()).thenReturn(event.world); when(world.getName()).thenReturn("world");
        WorldBorder border=mock(WorldBorder.class); when(border.isInside(any())).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        when(server.getWorld(event.world)).thenReturn(world); when(server.getWorlds()).thenReturn(List.of(world));
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        Chunk chunk=mock(Chunk.class); when(chunk.getEntities()).thenReturn(new org.bukkit.entity.Entity[0]);
        when(world.getChunkAt(anyInt(),anyInt())).thenReturn(chunk);
        when(plugin.getMobManager()).thenReturn(mock(MobManager.class));
        when(plugin.getWorldZoneManager()).thenReturn(mock(WorldZoneManager.class));
        when(plugin.getWorldZoneManager().getZone(any())).thenReturn(new DistanceZone("frontier","Frontier",700,1600,8,20));
        when(plugin.getWorldZoneManager().rollLevel(any())).thenReturn(15);
        when(plugin.getWorldZoneManager().getDimensionMinLevel(world)).thenReturn(1);
        data=directory.resolve("data/world-events");
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(invocation->{
            TerrainSnapshot.Position position=new TerrainSnapshot.Position(invocation.getArgument(0),invocation.getArgument(1),invocation.getArgument(2));
            Block block=mock(Block.class);
            when(block.getBlockData()).thenAnswer(i->blockData(blocks.getOrDefault(position,"minecraft:air")));
            when(block.getType()).thenAnswer(i->Material.matchMaterial(blocks.getOrDefault(position,"minecraft:air").split("\\[")[0]));
            when(block.isPassable()).thenAnswer(i->block.getType()==Material.AIR);
            when(block.isEmpty()).thenAnswer(i->block.getType()==Material.AIR);
            when(block.isSolid()).thenAnswer(i->block.getType()!=Material.AIR);
            doAnswer(i->{ blocks.put(position,((BlockData)i.getArgument(0)).getAsString()); writes++; return null; }).when(block).setBlockData(any(),eq(false));
            return block;
        });
        when(world.getBlockAt(any(Location.class))).thenAnswer(i->{ Location p=i.getArgument(0); return world.getBlockAt(p.getBlockX(),p.getBlockY(),p.getBlockZ()); });
    }
    private BlockData blockData(String text) { BlockData data=mock(BlockData.class); when(data.getAsString()).thenReturn(text); return data; }
    private WorldEventRepository seed(WorldEventState state) throws Exception {
        var repo=new WorldEventRepository(data); event.state=state; repo.events.put(event.id,event);
        repo.nextTriggerAt=System.currentTimeMillis()+9_000_000;
        var snapshot=WorldEventPersistenceTest.snapshot(event); repo.saveSnapshot(snapshot); repo.save();
        snapshot.blocks().forEach(b->blocks.put(b.position(),b.after())); return repo;
    }
    private Runnable start(WorldEventManager manager) {
        manager.start(); ArgumentCaptor<Runnable> captor=ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin),captor.capture(),eq(1L),eq(1L)); return captor.getValue();
    }
    private void rejectLocations(WorldEventManager manager,String reason) throws Exception {
        EventLocationService locations=mock(EventLocationService.class);
        locations.lastRejection=reason;
        var field=WorldEventManager.class.getDeclaredField("locations"); field.setAccessible(true); field.set(manager,locations);
    }

    @Test void restartAbortsLiveEventAndRestoresInBatchesBeforeClosingAndReleasingTasks() throws Exception {
        var repo=seed(WorldEventState.ACTIVE);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.createBlockData(anyString())).thenAnswer(i->blockData(i.getArgument(0)));
            WorldEventManager manager=new WorldEventManager(plugin);
            assertEquals(WorldEventState.CLEANING,manager.find(event.id).state);
            Runnable tick=start(manager); tick.run();
            assertEquals(1,writes); assertEquals(WorldEventState.CLEANING,manager.find(event.id).state);
            verify(world,never()).save();
            tick.run(); assertEquals(2,writes); assertEquals(WorldEventState.CLOSED,manager.find(event.id).state);
            verify(world).save(); verify(world,atLeastOnce()).removePluginChunkTicket(anyInt(),anyInt(),eq(plugin));
            repo.load(); assertEquals(WorldEventState.CLOSED,repo.events.get(event.id).state);
            for(var change:repo.loadSnapshot(event.id).blocks()) assertEquals(change.before(),blocks.get(change.position()));
            manager.shutdown(); verify(task).cancel();
        }
    }
    @Test void restoreConflictRetainsSnapshotAndDoesNotOverwriteUnknownBuilding() throws Exception {
        seed(WorldEventState.IMPACTING); var position=WorldEventPersistenceTest.snapshot(event).blocks().getFirst().position();
        blocks.put(position,"minecraft:diamond_block");
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.createBlockData(anyString())).thenAnswer(i->blockData(i.getArgument(0)));
            var manager=new WorldEventManager(plugin); Runnable tick=start(manager); tick.run(); tick.run();
            assertEquals("minecraft:diamond_block",blocks.get(position)); assertEquals(0,writes);
            assertEquals(WorldEventState.CLEANING,manager.find(event.id).state);
            assertTrue(manager.debug(manager.find(event.id)).contains("Terrain conflict"));
            assertTrue(Files.exists(data.resolve("snapshots/"+event.id+".yml")));
            verify(world,never()).save(); manager.shutdown();
        }
    }
    @Test void completedRestartKeepsClaimEligibilityAndClosedSnapshotsNeverOverwriteLaterEdits() throws Exception {
        event.successful=true; UUID player=UUID.randomUUID(); event.contribution.put(player,20.0); event.rewards.put("meteor_fragment",12L);
        seed(WorldEventState.COMPLETED);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.createBlockData(anyString())).thenAnswer(i->blockData(i.getArgument(0)));
            var manager=new WorldEventManager(plugin); Runnable tick=start(manager); tick.run(); tick.run();
            assertTrue(manager.find(event.id).successful); assertTrue(manager.claim(event.id,player).contains("/events wallet"));
            manager.shutdown();
            var position=WorldEventPersistenceTest.snapshot(event).blocks().getFirst().position(); blocks.put(position,"minecraft:diamond_block"); writes=0;
            reset(scheduler); when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),eq(1L),eq(1L))).thenReturn(task);
            var next=new WorldEventManager(plugin); start(next).run();
            assertEquals("minecraft:diamond_block",blocks.get(position)); assertEquals(0,writes);
            assertTrue(next.claim(event.id,player).contains("已領取")); next.shutdown();
        }
    }
    @Test void corruptManifestQuarantinesSnapshotAreaAndNeverOverwritesRecoveryFiles() throws Exception {
        seed(WorldEventState.ACTIVE); String broken="version: 1\nevents: [broken";
        Files.writeString(data.resolve("state.yml"),broken);
        var manager=new WorldEventManager(plugin); manager.start();
        assertNotNull(manager.at(new Location(world,event.x,event.y,event.z)));
        assertTrue(manager.requestStart("meteor",null).contains("disabled"));
        verify(scheduler,never()).runTaskTimer(any(),any(Runnable.class),anyLong(),anyLong());
        assertEquals(0,writes); assertEquals(broken,Files.readString(data.resolve("state.yml"))); manager.shutdown();
        assertEquals(broken,Files.readString(data.resolve("state.yml")));
    }
    @Test void snapshotSaveFailurePreventsAnyTerrainOrEntityMutation() throws Exception {
        var manager=new WorldEventManager(plugin);
        EventLocationService locations=mock(EventLocationService.class);
        Location center=new Location(world,event.x,event.y,event.z);
        when(locations.validate(any(),any(),anyCollection())).thenReturn(new EventLocationService.Site(center,Map.of()));
        when(locations.validateArena(any(),any(),anyCollection(),anyBoolean(),anyInt())).thenReturn(new EventLocationService.Site(center,Map.of()));
        when(locations.snapshot(any(),any(),any(),anyLong())).thenAnswer(i->new TerrainSnapshot(i.getArgument(0),event.world,event.x,event.y,event.z,12,WorldEventPersistenceTest.snapshot(event).blocks()));
        var locationField=WorldEventManager.class.getDeclaredField("locations"); locationField.setAccessible(true); locationField.set(manager,locations);
        var repoField=WorldEventManager.class.getDeclaredField("repository"); repoField.setAccessible(true);
        WorldEventRepository repository=spy((WorldEventRepository)repoField.get(manager));
        doThrow(new IOException("snapshot disk full")).when(repository).saveSnapshot(any()); repoField.set(manager,repository);
        manager.requestStart("meteor",center); Runnable tick=start(manager); tick.run();
        assertEquals(0,writes); assertTrue(manager.events().isEmpty());
        verify(plugin.getMobManager(),never()).spawnEventMob(anyString(),any(),anyInt(),any()); manager.shutdown();
    }

    @Test void exactStartReportsUnsafeBuildHeightToRequesterWithoutCreatingAnEvent() throws Exception {
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        var manager=new WorldEventManager(plugin);
        rejectLocations(manager,"Build height unsuitable");
        List<String> feedback=new ArrayList<>();
        manager.requestStart("meteor",new Location(world,100,64,100),feedback::add);
        assertTrue(feedback.isEmpty());
        Runnable tick=start(manager); tick.run();
        assertEquals(1,feedback.size());
        assertTrue(feedback.getFirst().contains("已嘗試 1 個位置"));
        assertTrue(feedback.getFirst().contains("超出可施工高度"));
        assertTrue(feedback.getFirst().contains("×1"));
        assertTrue(manager.diagnostic().contains("Build height unsuitable"));
        assertTrue(manager.events().isEmpty()); assertEquals(0,writes);
        try(var snapshots=Files.walk(data)) {
            assertFalse(snapshots.anyMatch(path->path.getParent().getFileName().toString().equals("snapshots")&&Files.isRegularFile(path)));
        }
        tick.run(); assertEquals(1,feedback.size());
        List<String> second=new ArrayList<>();
        manager.requestStart("meteor",new Location(world,100,64,100),second::add); tick.run();
        assertEquals(1,feedback.size()); assertEquals(1,second.size());
        assertTrue(second.getFirst().contains("已嘗試 1 個位置"));
        manager.shutdown();
    }

    @Test void randomSearchReportsEveryRejectionCountAfterBoundedAttempts() throws Exception {
        var player=mock(org.bukkit.entity.Player.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(player.getWorld()).thenReturn(world); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(new Location(world,100,64,100));
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        var manager=new WorldEventManager(plugin);
        EventLocationService locations=mock(EventLocationService.class);
        int[] attempts={0};
        when(locations.validateArena(any(),any(),anyCollection(),anyBoolean(),anyInt())).thenAnswer(invocation->{
            locations.lastRejection=++attempts[0]%2==0?"Candidate touches unloaded chunk":"Build height unsuitable";
            return null;
        });
        var field=WorldEventManager.class.getDeclaredField("locations"); field.setAccessible(true); field.set(manager,locations);
        List<String> feedback=new ArrayList<>(); manager.requestStart("meteor",null,feedback::add);
        Runnable tick=start(manager);
        for(int i=0;i<31;i++) tick.run();
        assertTrue(feedback.isEmpty()); tick.run();
        assertEquals(32,attempts[0]); assertEquals(1,feedback.size());
        assertTrue(feedback.getFirst().contains("已嘗試 32 個位置"));
        assertTrue(feedback.getFirst().contains("未載入區塊"));
        assertTrue(feedback.getFirst().contains("超出可施工高度"));
        assertEquals(2,feedback.getFirst().split("×16",-1).length-1);
        assertTrue(manager.diagnostic().contains("Candidate touches unloaded chunk x16"));
        assertTrue(manager.diagnostic().contains("Build height unsuitable x16"));
        var repository=new WorldEventRepository(data); repository.load();
        assertTrue(repository.nextTriggerAt>System.currentTimeMillis()+290_000);
        assertTrue(repository.events.isEmpty()); assertEquals(0,writes);
        tick.run(); assertEquals(32,attempts[0]); manager.shutdown();
    }

    @Test void requesterIsNotifiedIfEligiblePlayersLeaveDuringSearch() throws Exception {
        var player=mock(org.bukkit.entity.Player.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        when(player.getWorld()).thenReturn(world); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(new Location(world,100,64,100));
        doReturn(List.of(player),List.of(player),List.of()).when(server).getOnlinePlayers();
        var manager=new WorldEventManager(plugin); rejectLocations(manager,"Build height unsuitable"); List<String> feedback=new ArrayList<>();
        manager.requestStart("meteor",null,feedback::add); Runnable tick=start(manager);
        tick.run(); assertTrue(feedback.isEmpty()); tick.run();
        assertEquals(1,feedback.size()); assertTrue(feedback.getFirst().contains("玩家人數不足"));
        assertTrue(feedback.getFirst().contains("已嘗試 1 個位置"));
        assertTrue(feedback.getFirst().contains("超出可施工高度"));
        assertTrue(manager.events().isEmpty()); assertEquals(0,writes); manager.shutdown();
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"false,false","true,false","false,true","true,true"})
    void fullLifecycleUsesSharedCombatRewardsRestorationAndCleansAnimation(boolean testMode,boolean arena) throws Exception {
        var manager=new WorldEventManager(plugin);
        EventLocationService locations=mock(EventLocationService.class);
        Location center=new Location(world,event.x,event.y,event.z);
        when(locations.validate(any(),any(),anyCollection())).thenReturn(new EventLocationService.Site(center,Map.of()));
        when(locations.validateForTesting(any(),any(),anyCollection())).thenReturn(new EventLocationService.Site(center,Map.of()));
        when(locations.validateArena(any(),any(),anyCollection(),anyBoolean(),anyInt())).thenReturn(new EventLocationService.Site(center,Map.of()));
        when(locations.snapshot(any(),any(),any(),anyLong())).thenAnswer(i->new TerrainSnapshot(i.getArgument(0),event.world,event.x,event.y,event.z,12,WorldEventPersistenceTest.snapshot(event).blocks()));
        var locationField=WorldEventManager.class.getDeclaredField("locations"); locationField.setAccessible(true); locationField.set(manager,locations);
        WorldEventDefinition definition=mock(WorldEventDefinition.class); when(definition.type()).thenReturn("test");
        when(definition.generatedArena()).thenReturn(arena); when(definition.arenaClearance()).thenReturn(6);
        when(definition.requirements()).thenReturn(new WorldEventDefinition.Requirements(12,4,9,5));
        when(definition.durationMillis()).thenReturn(60_000L); when(definition.cleanupDelayMillis()).thenReturn(1000L);
        when(definition.minimumDamage()).thenReturn(1.0); when(definition.minimumShare()).thenReturn(0.01);
        when(definition.rewards()).thenReturn(Map.of("meteor_fragment",12L));
        when(definition.mobs()).thenReturn(List.of(new WorldEventDefinition.MobSpec("meteor_parasite",2,"NORMAL")));
        var animation=mock(WorldEventDefinition.Animation.class); when(animation.tick()).thenReturn(true);
        when(definition.animation(any(),any(),any(),any())).thenReturn(animation); manager.register(definition);
        var mobs=plugin.getMobManager(); when(mobs.getMobData(anyString())).thenReturn(mock(MobManager.MobData.class));
        when(mobs.findSafeSpawnLocationExact(any())).thenAnswer(i->i.getArgument(0));
        when(mobs.findSafeSpawnLocationExactForTesting(any())).thenAnswer(i->i.getArgument(0));
        Map<UUID,org.bukkit.entity.LivingEntity> entities=new LinkedHashMap<>();
        org.mockito.stubbing.Answer<org.bukkit.entity.LivingEntity> spawn=i->{
            var entity=mock(org.bukkit.entity.LivingEntity.class); UUID id=UUID.randomUUID();
            when(entity.getUniqueId()).thenReturn(id); when(entity.isValid()).thenReturn(true);
            var pdc=mock(org.bukkit.persistence.PersistentDataContainer.class); Map<NamespacedKey,Object> tags=new HashMap<>();
            when(entity.getPersistentDataContainer()).thenReturn(pdc);
            doAnswer(a->{tags.put(a.getArgument(0),a.getArgument(2)); return null;}).when(pdc).set(any(),any(),any());
            when(pdc.get(any(NamespacedKey.class),eq(org.bukkit.persistence.PersistentDataType.STRING))).thenAnswer(a->tags.get(a.getArgument(0)));
            java.util.function.Consumer<org.bukkit.entity.LivingEntity> initializer=i.getArgument(3); initializer.accept(entity);
            entities.put(id,entity); return entity;
        };
        when(mobs.spawnEventMob(anyString(),any(),anyInt(),any())).thenAnswer(spawn);
        when(mobs.spawnTestEventMob(anyString(),any(),anyInt(),any())).thenAnswer(spawn);
        when(server.getEntity(any(UUID.class))).thenAnswer(i->entities.get(i.getArgument(0)));
        WorldEventPersistenceTest.snapshot(event).blocks().forEach(b->blocks.put(b.position(),b.before()));
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.createBlockData(anyString())).thenAnswer(i->blockData(i.getArgument(0)));
            if(testMode) manager.requestTestStart("test",center,message->{}); else manager.requestStart("test",center);
            Runnable tick=start(manager);
            for(int i=0;i<8;i++) { int before=writes; tick.run(); assertTrue(writes-before<=1); }
            var live=manager.events().iterator().next(); assertEquals(WorldEventState.ACTIVE,live.state); assertEquals(2,entities.size());
            assertEquals(testMode,live.testMode);
            assertEquals(arena,live.generatedArena);
            if(arena) {
                verify(locations,times(2)).validateArena(any(),any(),anyCollection(),eq(testMode),eq(6));
                verify(locations,never()).validateForTesting(any(),any(),anyCollection());
                verify(locations,never()).validate(any(),any(),anyCollection());
            } else if(testMode) {
                verify(locations,times(2)).validateForTesting(any(),any(),anyCollection());
                verify(locations,never()).validate(any(),any(),anyCollection());
                verify(mobs,never()).spawnEventMob(anyString(),any(),anyInt(),any());
            } else {
                verify(locations,never()).validateForTesting(any(),any(),anyCollection());
                verify(mobs,never()).spawnTestEventMob(anyString(),any(),anyInt(),any());
            }
            UUID player=UUID.randomUUID();
            for(var mob:entities.values()) { manager.damage(mob,player,50); manager.death(mob); manager.death(mob); }
            assertEquals(WorldEventState.COMPLETED,live.state); assertTrue(live.allDefeated());
            assertEquals(100.0,live.contribution.get(player)); assertEquals(2,live.killParticipation.get(player));
            manager.claim(live.id,player); manager.claim(live.id,player); assertEquals(12L,manager.wallet(player).get("meteor_fragment"));
            live.cleanupAt=0;
            for(int i=0;i<3;i++) tick.run();
            assertEquals(WorldEventState.CLOSED,live.state); verify(animation).close();
            for(var mob:entities.values()) verify(mob).remove();
            manager.shutdown(); verify(task).cancel();
        }
    }

    @Test void manifestSaveFailureAfterSnapshotCannotDuplicateEventOrStartTerrainWork() throws Exception {
        var manager=new WorldEventManager(plugin);
        EventLocationService locations=mock(EventLocationService.class); Location center=new Location(world,event.x,event.y,event.z);
        when(locations.validate(any(),any(),anyCollection())).thenReturn(new EventLocationService.Site(center,Map.of()));
        when(locations.validateArena(any(),any(),anyCollection(),anyBoolean(),anyInt())).thenReturn(new EventLocationService.Site(center,Map.of()));
        when(locations.snapshot(any(),any(),any(),anyLong())).thenAnswer(i->new TerrainSnapshot(i.getArgument(0),event.world,event.x,event.y,event.z,12,WorldEventPersistenceTest.snapshot(event).blocks()));
        var field=WorldEventManager.class.getDeclaredField("locations"); field.setAccessible(true); field.set(manager,locations);
        var repositoryField=WorldEventManager.class.getDeclaredField("repository"); repositoryField.setAccessible(true);
        var repository=spy((WorldEventRepository)repositoryField.get(manager));
        doThrow(new IOException("manifest full")).when(repository).save(); repositoryField.set(manager,repository);
        manager.requestStart("meteor",center); Runnable tick=start(manager); tick.run(); tick.run();
        assertEquals(1,manager.events().size()); assertEquals(WorldEventState.SCHEDULED,manager.events().iterator().next().state);
        assertEquals(0,writes); verify(server,never()).broadcastMessage(anyString());
        verify(repository,times(1)).saveSnapshot(any());
        doCallRealMethod().when(repository).save(); manager.shutdown();
    }

    @Test void evacuationDestinationAlwaysLiesOutsideConstructionAreaEvenWithMinimumPadding() throws Exception {
        Path config=directory.resolve("config/world_events.yml");
        Files.writeString(config,Files.readString(config).replace("protection-padding: 4","protection-padding: 2"));
        when(plugin.getMobManager().findSafeSpawnLocationExact(any())).thenAnswer(i->i.getArgument(0));
        var manager=new WorldEventManager(plugin); Location safe=manager.safeEdge(event,world);
        assertNotNull(safe); assertFalse(event.contains(event.world,safe.getBlockX(),safe.getBlockZ(),1)); manager.shutdown();
    }
    @Test void evacuationSearchesBeyondTheNearestUnsafeRing() throws Exception {
        when(plugin.getMobManager().findSafeSpawnLocationExact(any())).thenAnswer(invocation->{
            Location candidate=invocation.getArgument(0);
            return candidate.getBlockX()==event.x+event.radius+4+6&&candidate.getBlockZ()==event.z?candidate:null;
        });
        var manager=new WorldEventManager(plugin); Location safe=manager.safeEdge(event,world);
        assertNotNull(safe); assertEquals(event.x+event.radius+10,safe.getBlockX());
        manager.shutdown();
    }
    @Test void evacuationIncludesPlayersOnCliffsAboveTheImpactCenter() throws Exception {
        var player=mock(org.bukkit.entity.Player.class);
        when(player.getLocation()).thenReturn(new Location(world,event.x+0.5,event.y+120,event.z+0.5));
        when(world.getPlayers()).thenReturn(List.of(player));
        var manager=spy(new WorldEventManager(plugin));
        Location outside=new Location(world,event.x+event.radius+8,event.y+120,event.z);
        doReturn(outside).when(manager).safeEdge(event,world);
        when(player.teleport(outside)).thenReturn(true);
        var method=WorldEventManager.class.getDeclaredMethod("evacuate",WorldEventInstance.class,World.class);
        method.setAccessible(true);
        assertEquals(true,method.invoke(manager,event,world));
        verify(player).teleport(outside);
        manager.shutdown();
    }
    @Test void evacuationUsesWorldSpawnWhenNoNearbyExitExists() throws Exception {
        var player=mock(org.bukkit.entity.Player.class);
        when(player.getLocation()).thenReturn(new Location(world,event.x+0.5,event.y,event.z+0.5));
        when(world.getPlayers()).thenReturn(List.of(player));
        Location spawn=new Location(world,event.x+100,event.y,event.z+100);
        when(world.getSpawnLocation()).thenReturn(spawn);
        when(player.teleport(spawn)).thenReturn(true);
        var manager=spy(new WorldEventManager(plugin));
        doReturn(null).when(manager).safeEdge(event,world);
        var method=WorldEventManager.class.getDeclaredMethod("evacuate",WorldEventInstance.class,World.class);
        method.setAccessible(true);
        assertEquals(true,method.invoke(manager,event,world));
        verify(player).teleport(spawn);
        manager.shutdown();
    }

    @Test void invalidConfigurationCannotHideExistingRecoveryAreaOrRewriteManifest() throws Exception {
        seed(WorldEventState.ACTIVE); String original=Files.readString(data.resolve("state.yml"));
        Path config=directory.resolve("config/world_events.yml");
        Files.writeString(config,Files.readString(config).replace("min-interval-minutes: 45","min-interval-minutes: 0"));
        var manager=new WorldEventManager(plugin); assertNotNull(manager.at(new Location(world,event.x,event.y,event.z)));
        assertTrue(manager.requestStart("meteor",null).contains("disabled")); manager.shutdown();
        assertEquals(original,Files.readString(data.resolve("state.yml"))); assertEquals(0,writes);
    }
    @Test void actualMeteorBuildsOnlyAirSpawnsAllNineteenMobsAndStaysActiveUntilStopped() throws Exception {
        Path config=directory.resolve("config/world_events.yml");
        Files.writeString(config,Files.readString(config).replace("blocks-per-tick: 1\n","blocks-per-tick: 128\n").replace("      enabled: true","      enabled: false"));
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL); when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.getHighestBlockYAt(anyInt(),anyInt(),eq(HeightMap.WORLD_SURFACE))).thenReturn(140);
        when(plugin.getSafeZoneManager()).thenReturn(mock(SafeZoneManager.class));
        when(plugin.getProtectionAreaManager()).thenReturn(mock(ProtectionAreaManager.class));
        when(plugin.getBossZoneManager()).thenReturn(mock(BossZoneManager.class));
        var sentinel=new TerrainSnapshot.Position(event.x,140,event.z); blocks.put(sentinel,"minecraft:chest");
        var mobs=plugin.getMobManager(); when(mobs.getMobData(anyString())).thenReturn(mock(MobManager.MobData.class));
        when(mobs.findSafeSpawnLocationExact(any())).thenAnswer(i->{
            Location p=i.getArgument(0); assertEquals(150,p.getBlockY());
            assertTrue(p.clone().add(0,-1,0).getBlock().isSolid());
            assertTrue(p.getBlock().isEmpty()); return p;
        });
        Map<UUID,org.bukkit.entity.LivingEntity> entities=new LinkedHashMap<>();
        when(mobs.spawnEventMob(anyString(),any(),anyInt(),any())).thenAnswer(i->{
            var entity=mock(org.bukkit.entity.LivingEntity.class); UUID id=UUID.randomUUID(); Location p=i.getArgument(1);
            when(entity.getUniqueId()).thenReturn(id); when(entity.isValid()).thenReturn(true);
            when(entity.getLocation()).thenReturn(p); when(entity.getWorld()).thenReturn(world);
            when(entity.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
            java.util.function.Consumer<org.bukkit.entity.LivingEntity> initialize=i.getArgument(3); initialize.accept(entity);
            entities.put(id,entity); return entity;
        });
        when(server.getEntity(any(UUID.class))).thenAnswer(i->entities.get(i.getArgument(0)));
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.createBlockData(anyString())).thenAnswer(i->blockData(i.getArgument(0)));
            var manager=new WorldEventManager(plugin); manager.requestStart("meteor",new Location(world,event.x,141,event.z));
            Runnable tick=start(manager); for(int i=0;i<75;i++) tick.run();
            var live=manager.events().iterator().next(); assertEquals(WorldEventState.ACTIVE,live.state);
            assertTrue(live.generatedArena); assertEquals(19,entities.size());
            var repo=new WorldEventRepository(data); repo.load(); var snapshot=repo.loadSnapshot(live.id);
            assertFalse(snapshot.blocks().isEmpty()); assertTrue(snapshot.blocks().stream().allMatch(b->b.before().equals("minecraft:air")&&b.position().y()>140));
            for(int i=0;i<50;i++) tick.run(); assertEquals(WorldEventState.ACTIVE,live.state);
            assertEquals("minecraft:chest",blocks.get(sentinel));
            manager.stop(live.id); for(int i=0;i<40;i++) tick.run();
            assertEquals(WorldEventState.CLOSED,live.state);
            snapshot.blocks().forEach(b->assertEquals("minecraft:air",blocks.get(b.position())));
            assertEquals("minecraft:chest",blocks.get(sentinel)); entities.values().forEach(entity->verify(entity).remove());
            manager.shutdown();
        }
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={0,-20})
    void participantReturnIsSavedBeforeEntryAndWorksAfterOfflineArenaRemoval(int fallOffset) throws Exception {
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        var manager=new WorldEventManager(plugin); var repoField=WorldEventManager.class.getDeclaredField("repository"); repoField.setAccessible(true);
        var repo=(WorldEventRepository)repoField.get(manager); event.generatedArena=true; event.state=WorldEventState.ACTIVE;
        repo.events.put(event.id,event);
        var player=mock(org.bukkit.entity.Player.class); UUID id=UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id);
        Location origin=new Location(world,event.x+100.5,event.y,event.z+0.5,120,-20);
        blocks.put(new TerrainSnapshot.Position(origin.getBlockX(),origin.getBlockY()-1,origin.getBlockZ()),"minecraft:stone");
        var position=new java.util.concurrent.atomic.AtomicReference<>(origin);
        when(player.getLocation()).thenAnswer(i->position.get());
        when(plugin.getMobManager().findSafeSpawnLocationExact(any())).thenAnswer(i->i.getArgument(0));
        when(player.teleport(any(Location.class))).thenAnswer(i->{
            var disk=new WorldEventRepository(data); disk.load(); assertTrue(disk.events.get(event.id).arenaReturns.containsKey(id));
            position.set(i.getArgument(0)); return true;
        });
        assertTrue(manager.joinArena(player,null).contains("已進入")); assertEquals(event.y,position.get().getBlockY());
        assertEquals(origin.getX(),event.arenaReturns.get(id).x());
        position.set(position.get().clone().add(0,fallOffset,0));
        event.state=WorldEventState.CLOSED; repo.save(); // Simulate cleanup while this participant is offline.
        var restarted=new WorldEventManager(plugin); restarted.recoverArenaPlayer(player,false);
        assertEquals(origin,position.get()); assertFalse(restarted.find(event.id).arenaReturns.containsKey(id));
        repo.load(); assertFalse(repo.events.get(event.id).arenaReturns.containsKey(id));
    }
    @Test void cancelledParticipantReturnPausesCleanupUntilPlayerCanLeave() throws Exception {
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        event.generatedArena=true; var player=mock(org.bukkit.entity.Player.class); UUID id=UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id);
        Location origin=new Location(world,event.x+100.5,event.y,event.z+0.5);
        event.arenaReturns.put(id,new WorldEventInstance.ReturnPoint(event.world,origin.getX(),origin.getY(),origin.getZ(),0,0));
        blocks.put(new TerrainSnapshot.Position(origin.getBlockX(),origin.getBlockY()-1,origin.getBlockZ()),"minecraft:stone");
        var position=new java.util.concurrent.atomic.AtomicReference<>(new Location(world,event.x+0.5,event.y,event.z+0.5));
        when(player.getLocation()).thenAnswer(i->position.get()); when(world.getPlayers()).thenReturn(List.of(player));
        seed(WorldEventState.CLEANING);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.createBlockData(anyString())).thenAnswer(i->blockData(i.getArgument(0)));
            var manager=new WorldEventManager(plugin); Runnable tick=start(manager); tick.run();
            assertEquals(0,writes); assertEquals(WorldEventState.CLEANING,manager.find(event.id).state);
            when(player.teleport(any(Location.class))).thenAnswer(i->{position.set(i.getArgument(0));return true;});
            tick.run(); tick.run(); assertEquals(2,writes); assertEquals(WorldEventState.CLOSED,manager.find(event.id).state);
            assertEquals(origin,position.get()); assertFalse(manager.find(event.id).arenaReturns.containsKey(id)); manager.shutdown();
        }
    }
    @Test void failedReturnPointSaveOrCancelledEntryNeverLeavesAnUntrackedParticipant() throws Exception {
        var manager=new WorldEventManager(plugin); var field=WorldEventManager.class.getDeclaredField("repository"); field.setAccessible(true);
        var repo=spy((WorldEventRepository)field.get(manager)); field.set(manager,repo);
        event.generatedArena=true; event.state=WorldEventState.ACTIVE; repo.events.put(event.id,event);
        var player=mock(org.bukkit.entity.Player.class); UUID id=UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id);
        when(player.getLocation()).thenReturn(new Location(world,event.x+100,event.y,event.z));
        when(plugin.getMobManager().findSafeSpawnLocationExact(any())).thenAnswer(i->i.getArgument(0));
        doThrow(new IOException("disk full")).when(repo).save();
        assertTrue(manager.joinArena(player,null).contains("儲存失敗"));
        verify(player,never()).teleport(any(Location.class)); assertFalse(event.arenaReturns.containsKey(id));
        doCallRealMethod().when(repo).save();
        assertTrue(manager.joinArena(player,null).contains("傳送遭到取消"));
        assertFalse(event.arenaReturns.containsKey(id));
        var persisted=new WorldEventRepository(data); persisted.load(); assertTrue(persisted.events.get(event.id).arenaReturns.isEmpty());
    }
}

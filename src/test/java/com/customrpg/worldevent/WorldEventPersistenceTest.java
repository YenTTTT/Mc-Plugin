package com.customrpg.worldevent;

import com.customrpg.worldevent.core.*;
import com.customrpg.worldevent.persistence.WorldEventRepository;
import com.customrpg.worldevent.terrain.TerrainSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldEventPersistenceTest {
    @TempDir Path directory;
    static TerrainSnapshot snapshot(WorldEventInstance e) {
        return new TerrainSnapshot(e.id,e.world,e.x,e.y,e.z,e.radius,List.of(
                new TerrainSnapshot.BlockChange(new TerrainSnapshot.Position(e.x,e.y,e.z),"minecraft:air","minecraft:obsidian"),
                new TerrainSnapshot.BlockChange(new TerrainSnapshot.Position(e.x,e.y-1,e.z),"minecraft:grass_block[snowy=false]","minecraft:air")));
    }
    @Test void snapshotRoundTripsFullBlockDataAndIsImmutable() throws Exception {
        var repo=new WorldEventRepository(directory); var event=WorldEventLifecycleTest.event(); var snapshot=snapshot(event);
        repo.saveSnapshot(snapshot);
        assertEquals(snapshot,repo.loadSnapshot(event.id));
        assertThrows(IOException.class,()->repo.saveSnapshot(snapshot));
    }
    @Test void untouchedTerrainSnapshotRoundTripsAndNeedsNoBatchWrites() throws Exception {
        var repo=new WorldEventRepository(directory); var event=WorldEventLifecycleTest.event();
        var snapshot=new TerrainSnapshot(event.id,event.world,event.x,event.y,event.z,event.radius,List.of());
        repo.saveSnapshot(snapshot);
        assertEquals(snapshot,repo.loadSnapshot(event.id));
        var edit=new com.customrpg.worldevent.terrain.TerrainBatch(snapshot,false);
        assertTrue(edit.done());
        assertEquals(0,edit.step(128,(position,data)->fail("No terrain writes expected")));
    }
    @Test void testModeMarkerSurvivesRestartAndOldManifestsDefaultToNormalMode() throws Exception {
        var repo=new WorldEventRepository(directory); var event=WorldEventLifecycleTest.event(); event.testMode=true;
        repo.events.put(event.id,event); repo.save(); repo.load(); assertTrue(repo.events.get(event.id).testMode);
        Path file=directory.resolve("state.yml"); String text=Files.readString(file);
        Files.writeString(file,text.replace("  test-mode: true\n","")); repo.load();
        assertFalse(repo.events.get(event.id).testMode);
        Files.writeString(file,text.replace("test-mode: true","test-mode: invalid"));
        assertThrows(IllegalArgumentException.class,repo::load);
    }
    @Test void alteredSnapshotIsRejectedBeforeWorldAccess() throws Exception {
        var repo=new WorldEventRepository(directory); var event=WorldEventLifecycleTest.event(); repo.saveSnapshot(snapshot(event));
        Path file=directory.resolve("snapshots/"+event.id+".yml");
        Files.writeString(file,Files.readString(file).replace("minecraft:obsidian","minecraft:diamond_block"));
        assertThrows(IOException.class,()->repo.loadSnapshot(event.id));
    }
    @Test void arenaAndOfflineReturnPointsSurviveRestartWhileLegacyEventsRemainCraterBased() throws Exception {
        var repo=new WorldEventRepository(directory); var event=WorldEventLifecycleTest.event();
        event.generatedArena=true; event.arenaDepth=5; UUID player=UUID.randomUUID();
        var point=new WorldEventInstance.ReturnPoint(UUID.randomUUID(),12.5,72,-34.5,120,-20);
        event.arenaReturns.put(player,point); repo.events.put(event.id,event); repo.save(); repo.load();
        var loaded=repo.events.get(event.id); assertTrue(loaded.generatedArena); assertEquals(5,loaded.arenaDepth);
        assertEquals(point,loaded.arenaReturns.get(player));
        Path file=directory.resolve("state.yml"); String text=Files.readString(file);
        Files.writeString(file,text.replace("  generated-arena: true\n","")); repo.load(); assertFalse(repo.events.get(event.id).generatedArena);
    }
    @Test void scheduleMobDeathsContributionAndClaimsSurviveRestart() throws Exception {
        var repo=new WorldEventRepository(directory); var e=WorldEventLifecycleTest.event();
        var mob=new WorldEventInstance.EventMob(UUID.randomUUID(),"meteor_parasite","NORMAL",15);
        mob.entity=UUID.randomUUID(); mob.state=WorldEventInstance.MobState.DEAD; mob.chunkX=10; mob.chunkZ=-20;
        UUID player=UUID.randomUUID(); mob.damage.put(player,42.0); e.mobs.put(mob.id,mob); e.contribution.put(player,42.0);
        e.killParticipation.put(player,1); e.claimed.add(player); e.rewards.put("meteor_fragment",12L);
        e.state=WorldEventState.CLOSED; e.successful=true; repo.events.put(e.id,e);
        repo.wallets.put(player,new LinkedHashMap<>(Map.of("meteor_fragment",12L))); repo.nextTriggerAt=123456;
        repo.save();
        var restarted=new WorldEventRepository(directory); restarted.load(); var loaded=restarted.events.get(e.id);
        assertEquals(123456,restarted.nextTriggerAt); assertEquals(WorldEventState.CLOSED,loaded.state);
        assertEquals(WorldEventInstance.MobState.DEAD,loaded.mobs.get(mob.id).state);
        assertFalse(loaded.mobs.get(mob.id).beginSpawn()); assertEquals(-20,loaded.mobs.get(mob.id).chunkZ);
        assertEquals(42.0,loaded.contribution.get(player)); assertTrue(loaded.claimed.contains(player));
        assertEquals(12L,restarted.wallets.get(player).get("meteor_fragment"));
    }
    @Test void corruptManifestFailsClosedAndPreservesBytes() throws Exception {
        var repo=new WorldEventRepository(directory);
        Path file=directory.resolve("state.yml"); String broken="version: 1\nevents: [broken";
        Files.writeString(file,broken);
        assertThrows(Exception.class,repo::load);
        assertEquals(broken,Files.readString(file)); assertTrue(repo.events.isEmpty());
    }
    @Test void incompleteButParseableManifestCannotResetClaimsSilently() throws Exception {
        var repo=new WorldEventRepository(directory);
        Files.writeString(directory.resolve("state.yml"),"version: 1\nnext-trigger-at: 10\nevents: [not-a-map]\n");
        assertThrows(IOException.class,repo::load);
    }
    @Test void snapshotRejectsDuplicateOrOutOfRangeCoordinates() {
        var e=WorldEventLifecycleTest.event(); var block=new TerrainSnapshot.BlockChange(new TerrainSnapshot.Position(e.x,e.y,e.z),"minecraft:air","minecraft:obsidian");
        assertThrows(IllegalArgumentException.class,()->new TerrainSnapshot(e.id,e.world,e.x,e.y,e.z,12,List.of(block,block)));
        assertThrows(IllegalArgumentException.class,()->new TerrainSnapshot(e.id,e.world,e.x+100,e.y,e.z,12,List.of(block)));
    }
}

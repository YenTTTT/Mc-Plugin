package com.customrpg.worldevent;

import com.customrpg.worldevent.core.WorldEventDefinition.Column;
import com.customrpg.worldevent.meteor.MeteorCraterGenerator;
import com.customrpg.worldevent.terrain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MeteorTerrainTest {
    @Test void geometryIsSeededIrregularBoundedAndContainsRealCraterAndCore() {
        Map<Column,Integer> heights=new LinkedHashMap<>();
        for(int x=-12;x<=12;x++) for(int z=-12;z<=12;z++) heights.put(new Column(x,z),64);
        var generator=new MeteorCraterGenerator(); var plan=generator.generate(heights,0,65,0,12,4,3,42);
        assertEquals(plan,generator.generate(heights,0,65,0,12,4,3,42));
        assertNotEquals(plan,generator.generate(heights,0,65,0,12,4,3,43));
        assertTrue(plan.keySet().stream().allMatch(p->Math.abs(p.x())<=12&&Math.abs(p.z())<=12));
        assertTrue(plan.values().contains("minecraft:crying_obsidian"));
        assertTrue(plan.values().contains("minecraft:air"));
        assertTrue(plan.entrySet().stream().anyMatch(e->e.getKey().y()<64&&!e.getValue().equals("minecraft:air")));
        assertFalse(plan.containsKey(new TerrainSnapshot.Position(12,64,12)));
    }
    @Test void batchedEditsNeverExceedBudgetAndRepeatedRestoreIsIdempotent() {
        var e=WorldEventLifecycleTest.event(); var snapshot=WorldEventPersistenceTest.snapshot(e);
        Map<TerrainSnapshot.Position,String> world=new HashMap<>(); snapshot.blocks().forEach(b->world.put(b.position(),b.before()));
        var edit=new TerrainBatch(snapshot,false);
        assertEquals(1,edit.step(1,world::put)); assertFalse(edit.done());
        assertEquals(1,edit.step(1,world::put)); assertTrue(edit.done()); assertEquals(0,edit.step(1,world::put));
        for(int repeat=0;repeat<2;repeat++) {
            var restore=new TerrainBatch(snapshot,true); restore.step(100,world::put);
            assertTrue(restore.done()); snapshot.blocks().forEach(b->assertEquals(b.before(),world.get(b.position())));
        }
    }
    @Test void cliffUsesLocalSurfaceInsteadOfClearingTheEntireHeightGap() {
        Map<Column,Integer> heights=new LinkedHashMap<>();
        for(int x=-12;x<=12;x++) for(int z=-12;z<=12;z++) heights.put(new Column(x,z),x==0&&z==0?240:64);
        var plan=new MeteorCraterGenerator().generate(heights,0,241,0,12,4,3,42);
        assertTrue(plan.size()<5000,()->"cliff generated "+plan.size()+" edits");
        assertFalse(plan.containsKey(new TerrainSnapshot.Position(5,150,0)));
        assertTrue(plan.keySet().stream().allMatch(position->Math.abs(position.x())<=12&&Math.abs(position.z())<=12));
    }
    @Test void interruptedRestoreCanRestartFromZeroAndFailedWriteDoesNotAdvanceCursor() {
        var e=WorldEventLifecycleTest.event(); var snapshot=WorldEventPersistenceTest.snapshot(e);
        var restore=new TerrainBatch(snapshot,true);
        assertThrows(IllegalStateException.class,()->restore.step(1,(position,data)->{throw new IllegalStateException("blocked");}));
        assertEquals(0,restore.cursor());
        assertEquals(1,restore.step(1,(position,data)->{}));
        var restarted=new TerrainBatch(snapshot,true); assertEquals(snapshot.blocks().size(),restarted.step(100,(position,data)->{}));
        assertTrue(restarted.done());
    }
}

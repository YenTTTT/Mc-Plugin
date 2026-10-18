package com.customrpg.worldevent;

import com.customrpg.worldevent.core.*;
import com.customrpg.worldevent.scheduler.WorldEventScheduler;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldEventLifecycleTest {
    static WorldEventInstance event() {
        return new WorldEventInstance(UUID.randomUUID(),"meteor",UUID.randomUUID(),"world",1000,65,-1000,12,8,20,15,123,1000,1_201_000);
    }
    @Test void completeLifecycleAndDuplicateCompletionRejection() {
        var e=event();
        for(var next:List.of(WorldEventState.ANNOUNCED,WorldEventState.FALLING,WorldEventState.IMPACTING,WorldEventState.ACTIVE,WorldEventState.COMPLETED)) e.transition(next);
        assertTrue(e.successful);
        assertThrows(IllegalStateException.class,()->e.transition(WorldEventState.COMPLETED));
        e.transition(WorldEventState.CLEANING); e.transition(WorldEventState.CLOSED);
        assertFalse(e.reserved());
        assertThrows(IllegalStateException.class,()->e.transition(WorldEventState.ACTIVE));
    }
    @Test void invalidTransitionsCannotSkipImpactOrReactivateClosedEvent() {
        var e=event();
        assertThrows(IllegalStateException.class,()->e.transition(WorldEventState.ACTIVE));
        e.transition(WorldEventState.FAILED); e.transition(WorldEventState.CLEANING); e.transition(WorldEventState.CLOSED);
        assertFalse(e.successful);
        assertFalse(WorldEventState.CLOSED.canTransitionTo(WorldEventState.SCHEDULED));
    }
    @Test void unloadIsNotDeathAndEachMobCanOnlyBeDefeatedOnce() {
        var e=event(); var m=new WorldEventInstance.EventMob(UUID.randomUUID(),"meteor_parasite","NORMAL",15);
        e.mobs.put(m.id,m);
        assertFalse(m.die()); assertFalse(e.allDefeated());
        assertTrue(m.beginSpawn()); assertFalse(m.beginSpawn());
        m.state=WorldEventInstance.MobState.UNLOADED;
        assertFalse(m.beginSpawn()); assertFalse(e.allDefeated());
        assertTrue(m.die()); assertFalse(m.die()); assertTrue(e.allDefeated());
        assertFalse(m.beginSpawn());
    }
    @Test void schedulingIsRandomBoundedAndPreservesFutureDeadlineAcrossRestart() {
        var settings=new WorldEventSettings(true,45,120,300,1,1,32,64,256,4,128);
        var scheduler=new WorldEventScheduler(settings,new Random(3));
        Set<Long> results=new HashSet<>(); long now=10_000;
        for(int i=0;i<100;i++) {
            long next=scheduler.next(now);
            assertTrue(next>=now+45*60_000L&&next<=now+120*60_000L); results.add(next);
        }
        assertTrue(results.size()>90);
        assertEquals(now+5000,scheduler.afterRestart(now,now+5000));
        assertTrue(scheduler.afterRestart(now,now-1)>=now+45*60_000L);
        assertEquals(now+300_000,scheduler.retry(now));
    }
}

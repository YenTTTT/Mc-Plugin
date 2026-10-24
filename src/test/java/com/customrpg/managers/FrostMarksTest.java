package com.customrpg.managers;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrostMarksTest {
    UUID caster=UUID.randomUUID(),target=UUID.randomUUID();
    @Test void thirdAcceptedHitConsumesStacksAndControlImmunityIsSharedByCasters() {
        var marks=new FrostMarks();assertEquals(1,marks.hit(caster,target,0,true).stacks());
        assertEquals(2,marks.hit(caster,target,10,true).stacks());assertTrue(marks.hit(caster,target,20,true).condensed());
        assertEquals(0,marks.stacks(caster,target,21));UUID second=UUID.randomUUID();
        marks.hit(second,target,22,true);marks.hit(second,target,23,true);
        assertFalse(marks.hit(second,target,24,true).condensed());assertEquals(3,marks.stacks(second,target,24));
        marks.hit(second,target,130,true);assertTrue(marks.hit(second,target,180,true).condensed());
    }
    @Test void sixSecondExpiryRefreshesPerHitAndUltimateNeverTriggersThePassiveRoot() {
        var marks=new FrostMarks();marks.hit(caster,target,0,true);marks.hit(caster,target,100,true);
        assertEquals(2,marks.stacks(caster,target,219));assertEquals(0,marks.stacks(caster,target,220));
        marks.hit(caster,target,230,true);marks.hit(caster,target,231,true);
        assertFalse(marks.hit(caster,target,232,false).condensed());assertEquals(3,marks.stacks(caster,target,232));
        assertTrue(marks.hit(caster,target,233,true).condensed());
    }
    @Test void cleanupPrunesTargetsOwnersAndExpiredStacks() {
        var marks=new FrostMarks();marks.hit(caster,target,0,true);marks.owner(caster);assertEquals(0,marks.size());
        marks.hit(caster,target,1,true);marks.target(target);assertEquals(0,marks.size());
        marks.hit(caster,target,2,true);marks.prune(122);assertEquals(0,marks.size());
        marks.hit(caster,target,200,true);marks.clear();assertEquals(0,marks.size());
    }
}

package com.customrpg.balance;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ExtraDamageBudgetTest {
    AtomicLong clock=new AtomicLong(); UUID player=UUID.randomUUID(),target=UUID.randomUUID();
    ExtraDamageBudget budget=new ExtraDamageBudget(clock::get);
    @Test void initialBurstAndSustainedAllowanceCannotBeMultipliedByMultiHitSkills() {
        double total=0;
        for(int i=0;i<100;i++) { var permit=budget.reserve(player,target,500,100); total+=permit.damage(100); permit.finish(true); }
        assertEquals(200,total,1e-9);
        clock.set(1000); assertEquals(15,budget.reserve(player,target,500,100).damage(100),1e-9);
    }
    @Test void cancelledProtectionRefundsExactlyOnceAndSuccessfulHitsDoNotRefund() {
        var permit=budget.reserve(player,target,200,100); permit.finish(false); permit.finish(false);
        var accepted=budget.reserve(player,target,200,100); assertEquals(200,accepted.damage(100));
        accepted.finish(true); accepted.finish(false);
        assertEquals(0,budget.reserve(player,target,200,100).damage(100));
    }
    @Test void changingWeaponsDoesNotCreateANewBucketAndClockRollbackDoesNotRefill() {
        budget.reserve(player,target,200,100).finish(true);
        assertEquals(0,budget.reserve(player,target,10000,1000).damage(1000));
        clock.set(-1000); assertEquals(0,budget.reserve(player,target,100,100).damage(100));
        clock.set(1000); assertEquals(15,budget.reserve(player,target,100,100).damage(100),1e-9);
    }
    @Test void slowTimeGapsCannotStoreUnboundedBurstAndIdleEntriesCanBeReleased() {
        budget.reserve(player,target,200,100).finish(true); clock.set(1_000_000); budget.prune();
        assertEquals(200,budget.reserve(player,target,10000,100).damage(100));
    }
    @Test void healingUsesHpFractionsAndCannotHealForeverWithRapidTicks() {
        var heals=new ExtraDamageBudget(clock::get,.35,.04);
        assertEquals(350,heals.reserve(player,target,5000,1000).damage(1000),1e-9);
        clock.set(1000); assertEquals(40,heals.reserve(player,target,5000,1000).damage(1000),1e-9);
    }
    @Test void invalidRequestsNeverConsumeAllowance() {
        assertEquals(0,budget.reserve(player,target,Double.NaN,100).damage(100));
        assertEquals(200,budget.reserve(player,target,500,100).damage(100));
    }
}

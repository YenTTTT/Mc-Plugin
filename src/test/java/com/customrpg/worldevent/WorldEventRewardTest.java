package com.customrpg.worldevent;

import com.customrpg.worldevent.core.*;
import com.customrpg.worldevent.persistence.WorldEventRepository;
import com.customrpg.worldevent.reward.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldEventRewardTest {
    @TempDir Path directory;
    @Test void rewardsAllEligibleContributorsRatherThanOnlyLastHit() throws Exception {
        var repo=new WorldEventRepository(directory); var service=new EventContributionService();
        var rewards=new EventRewardService(repo,service); var e=WorldEventLifecycleTest.event(); e.state=WorldEventState.ACTIVE;
        var mob=new WorldEventInstance.EventMob(UUID.randomUUID(),"meteor_parasite","NORMAL",15); mob.state=WorldEventInstance.MobState.ALIVE;
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(),afk=UUID.randomUUID();
        service.damage(e,mob,first,80); service.damage(e,mob,second,20);
        assertTrue(service.defeat(e,mob)); assertFalse(service.defeat(e,mob));
        assertEquals(1,e.killParticipation.get(first)); assertEquals(1,e.killParticipation.get(second));
        e.successful=true; e.state=WorldEventState.COMPLETED; e.rewards.put("meteor_fragment",12L); repo.events.put(e.id,e);
        assertEquals(EventRewardService.ClaimResult.GRANTED,rewards.claim(e,first));
        assertEquals(EventRewardService.ClaimResult.GRANTED,rewards.claim(e,second));
        assertEquals(EventRewardService.ClaimResult.INELIGIBLE,rewards.claim(e,afk));
        assertEquals(12L,rewards.balance(first).get("meteor_fragment"));
    }
    @Test void doubleClickReconnectAndRestartCannotDuplicateWalletCredit() throws Exception {
        var repo=new WorldEventRepository(directory); var e=WorldEventLifecycleTest.event(); UUID player=UUID.randomUUID();
        e.successful=true; e.state=WorldEventState.CLOSED; e.contribution.put(player,100.0); e.rewards.put("meteor_fragment",12L); repo.events.put(e.id,e);
        var service=new EventRewardService(repo,new EventContributionService());
        assertEquals(EventRewardService.ClaimResult.GRANTED,service.claim(e,player));
        assertEquals(EventRewardService.ClaimResult.ALREADY_CLAIMED,service.claim(e,player));
        var restarted=new WorldEventRepository(directory); restarted.load();
        var afterRestart=new EventRewardService(restarted,new EventContributionService());
        assertEquals(EventRewardService.ClaimResult.ALREADY_CLAIMED,afterRestart.claim(restarted.events.get(e.id),player));
        assertEquals(12L,afterRestart.balance(player).get("meteor_fragment"));
    }
    @Test void failedTransactionDoesNotConsumeClaimOrPartiallyCreditWallet() throws Exception {
        var repo=spy(new WorldEventRepository(directory)); var e=WorldEventLifecycleTest.event(); UUID player=UUID.randomUUID();
        e.successful=true; e.contribution.put(player,10.0); e.rewards.put("meteor_fragment",12L);
        repo.wallets.put(player,new LinkedHashMap<>(Map.of("meteor_fragment",2L)));
        doThrow(new IOException("disk full")).when(repo).save();
        var service=new EventRewardService(repo,new EventContributionService());
        assertEquals(EventRewardService.ClaimResult.SAVE_FAILED,service.claim(e,player));
        assertFalse(e.claimed.contains(player)); assertEquals(2L,service.balance(player).get("meteor_fragment"));
        doCallRealMethod().when(repo).save();
        assertEquals(EventRewardService.ClaimResult.GRANTED,service.claim(e,player));
        assertEquals(14L,service.balance(player).get("meteor_fragment"));
    }
    @Test void failedOrExpiredEventNeverGrantsCompletionReward() throws Exception {
        var repo=new WorldEventRepository(directory); var e=WorldEventLifecycleTest.event(); UUID player=UUID.randomUUID(); e.contribution.put(player,100.0);
        var service=new EventRewardService(repo,new EventContributionService());
        for(var state:List.of(WorldEventState.FAILED,WorldEventState.EXPIRED,WorldEventState.CLEANING,WorldEventState.CLOSED)) {
            e.state=state;
            assertEquals(EventRewardService.ClaimResult.NOT_COMPLETED,service.claim(e,player));
        }
        assertTrue(repo.wallets.isEmpty());
    }
    @Test void invalidDamageAndAfkPresenceCannotBecomeParticipation() {
        var service=new EventContributionService(); var e=WorldEventLifecycleTest.event(); e.state=WorldEventState.ACTIVE;
        var mob=new WorldEventInstance.EventMob(UUID.randomUUID(),"meteor_parasite","NORMAL",15); mob.state=WorldEventInstance.MobState.ALIVE;
        UUID player=UUID.randomUUID();
        for(double damage:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) service.damage(e,mob,player,damage);
        assertTrue(e.contribution.isEmpty()); assertFalse(service.eligible(e,player));
    }
    @Test void walletDoesNotDependOnPlayerInventoryCapacity() throws Exception {
        var repo=new WorldEventRepository(directory); var e=WorldEventLifecycleTest.event(); UUID player=UUID.randomUUID();
        e.successful=true; e.contribution.put(player,5.0); e.rewards.put("astral_crystal",3L);
        var service=new EventRewardService(repo,new EventContributionService());
        assertEquals(EventRewardService.ClaimResult.GRANTED,service.claim(e,player));
        assertEquals(3L,service.balance(player).get("astral_crystal")); // No Player/Inventory API exists in the transaction.
    }
}

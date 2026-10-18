package com.customrpg.worldevent.reward;

import com.customrpg.worldevent.core.WorldEventInstance;
import com.customrpg.worldevent.persistence.WorldEventRepository;
import java.io.IOException;
import java.util.*;

/** Material definitions are a minimal shared vocabulary for future workstation/crafting providers. */
public final class EventRewardService {
    public enum ClaimResult { GRANTED, ALREADY_CLAIMED, NOT_COMPLETED, INELIGIBLE, SAVE_FAILED }
    public static final Map<String,String> MATERIALS=Map.of("meteor_fragment","隕石碎片","astral_crystal","星界水晶","meteor_core_fragment","隕石核心碎片");
    private final WorldEventRepository repository;
    private final EventContributionService contributions;
    public EventRewardService(WorldEventRepository repository,EventContributionService contributions) { this.repository=repository; this.contributions=contributions; }

    public ClaimResult claim(WorldEventInstance event,UUID player) {
        if(!event.successful) return ClaimResult.NOT_COMPLETED;
        if(event.claimed.contains(player)) return ClaimResult.ALREADY_CLAIMED;
        if(!contributions.eligible(event,player)) return ClaimResult.INELIGIBLE;
        Map<String,Long> previous=repository.wallets.get(player);
        Map<String,Long> updated=new LinkedHashMap<>(previous==null?Map.of():previous);
        try { event.rewards.forEach((key,count) -> updated.merge(key,count,Math::addExact)); }
        catch(ArithmeticException e) { return ClaimResult.SAVE_FAILED; }
        event.claimed.add(player); repository.wallets.put(player,updated);
        try { repository.save(); return ClaimResult.GRANTED; }
        catch(IOException e) {
            event.claimed.remove(player);
            if(previous==null) repository.wallets.remove(player); else repository.wallets.put(player,previous);
            return ClaimResult.SAVE_FAILED;
        }
    }
    public Map<String,Long> balance(UUID player) { return Map.copyOf(repository.wallets.getOrDefault(player,Map.of())); }
}

package com.customrpg.worldevent.reward;

import com.customrpg.worldevent.core.WorldEventInstance;
import java.util.UUID;

public final class EventContributionService {
    public void damage(WorldEventInstance event,WorldEventInstance.EventMob mob,UUID player,double effectiveDamage) {
        if(event.state!=com.customrpg.worldevent.core.WorldEventState.ACTIVE
                ||mob.state!=WorldEventInstance.MobState.ALIVE||!Double.isFinite(effectiveDamage)||effectiveDamage<=0) return;
        event.contribution.merge(player,effectiveDamage,Double::sum);
        mob.damage.merge(player,effectiveDamage,Double::sum);
    }
    public boolean defeat(WorldEventInstance event,WorldEventInstance.EventMob mob) {
        if(!mob.die()) return false;
        mob.damage.keySet().forEach(player -> event.killParticipation.merge(player,1,Integer::sum));
        return true;
    }
    public boolean eligible(WorldEventInstance event,UUID player) {
        double damage=event.contribution.getOrDefault(player,0.0);
        double total=event.contribution.values().stream().mapToDouble(Double::doubleValue).sum();
        return damage>=event.minimumDamage && total>0 && damage/total>=event.minimumShare;
    }
}

package com.customrpg.worldevent.scheduler;

import com.customrpg.worldevent.core.WorldEventSettings;
import java.util.random.RandomGenerator;

/** Pure scheduling policy; the manager owns one cancellable Bukkit task. */
public final class WorldEventScheduler {
    private final WorldEventSettings settings;
    private final RandomGenerator random;
    public WorldEventScheduler(WorldEventSettings settings, RandomGenerator random) { this.settings=settings; this.random=random; }
    public long next(long now) { return Math.addExact(now, random.nextLong(settings.minMinutes()*60_000L, settings.maxMinutes()*60_000L+1)); }
    public long retry(long now) { return Math.addExact(now, settings.retrySeconds()*1000L); }
    public long afterRestart(long now, long persisted) { return persisted>now ? persisted : next(now); }
}

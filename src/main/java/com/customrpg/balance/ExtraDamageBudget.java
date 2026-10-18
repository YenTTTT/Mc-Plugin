package com.customrpg.balance;

import java.util.*;
import java.util.function.LongSupplier;

/** Main-thread bounded burst/sustain allowance, measured in seconds of baseline DPS. */
public final class ExtraDamageBudget {
    private record Key(UUID source, UUID target) {}
    private static final class Bucket { double tokens; long last; Bucket(long now,double capacity) { last = now; tokens=capacity; } }
    private final Map<Key,Bucket> buckets = new HashMap<>();
    private final LongSupplier clock;
    private final double capacity, refill;
    public ExtraDamageBudget(LongSupplier clock) { this(clock,2,.15); }
    public ExtraDamageBudget(LongSupplier clock,double capacity,double refill) {
        this.clock = clock; this.capacity=capacity; this.refill=refill;
    }

    public final class Reservation {
        private final Bucket bucket;
        private final double spent;
        private boolean resolved;
        private Reservation(Bucket bucket, double spent) { this.bucket = bucket; this.spent = spent; }
        public double damage(double dps) { return spent * dps; }
        public void finish(boolean accepted) {
            if (resolved) return;
            resolved = true;
            if (!accepted && bucket != null) bucket.tokens = Math.min(capacity, bucket.tokens + spent);
        }
    }

    public Reservation reserve(UUID source, UUID target, double requested, double baselineDps) {
        if (!Double.isFinite(requested) || !Double.isFinite(baselineDps) || requested <= 0 || baselineDps <= 0)
            return new Reservation(null, 0);
        long now = clock.getAsLong();
        Key key = new Key(source, target);
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= 4096) { prune(); if (buckets.size() >= 4096) return new Reservation(null, 0); }
            bucket = new Bucket(now,capacity); buckets.put(key, bucket);
        }
        bucket.tokens = Math.min(capacity, bucket.tokens + Math.max(0, now - bucket.last) / 1000.0 * refill);
        bucket.last = Math.max(now, bucket.last);
        double spent = Math.min(bucket.tokens, requested / baselineDps);
        bucket.tokens -= spent;
        return new Reservation(bucket, spent);
    }
    public void prune() { long now = clock.getAsLong(); buckets.values().removeIf(b -> now - b.last > 120_000); }
    public void clear() { buckets.clear(); }
    public void removeTarget(UUID target) { buckets.keySet().removeIf(k -> k.target.equals(target)); }
    public void remove(UUID player) { buckets.keySet().removeIf(k -> k.source.equals(player) || k.target.equals(player)); }
}

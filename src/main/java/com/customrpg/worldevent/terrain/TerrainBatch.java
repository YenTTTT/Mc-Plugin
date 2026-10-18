package com.customrpg.worldevent.terrain;

import java.util.function.*;

/** A task-free bounded cursor. Restores always start from index zero, so interrupted restores are idempotent. */
public final class TerrainBatch {
    private final TerrainSnapshot snapshot;
    private final boolean restore;
    private int cursor;
    public TerrainBatch(TerrainSnapshot snapshot,boolean restore) { this.snapshot=snapshot; this.restore=restore; }
    public int step(int budget, BiConsumer<TerrainSnapshot.Position,String> writer) {
        if(budget<1) throw new IllegalArgumentException("Positive batch budget required");
        int changed=0;
        while(cursor<snapshot.blocks().size()&&changed<budget) {
            var block=snapshot.blocks().get(cursor);
            writer.accept(block.position(),restore?block.before():block.after());
            cursor++; changed++;
        }
        return changed;
    }
    public boolean done() { return cursor==snapshot.blocks().size(); }
    public int cursor() { return cursor; }
}

package com.customrpg.managers;

import java.util.*;

/** Pure tick-based rules: stacks belong to a caster, but the eight-second control immunity belongs to the victim. */
public final class FrostMarks {
    private record Key(UUID owner,UUID target) {}
    private record Mark(int stacks,long until) {}
    public record Result(int stacks,boolean condensed) {}
    private final Map<Key,Mark> marks=new HashMap<>();
    private final Map<UUID,Long> immunity=new HashMap<>();
    public Result hit(UUID owner,UUID target,long tick,boolean allowCondense) {
        var key=new Key(owner,target);var previous=marks.get(key);
        int stacks=Math.min(3,1+(previous!=null&&previous.until>tick?previous.stacks:0));
        if(stacks==3&&allowCondense&&immunity.getOrDefault(target,0L)<=tick&&(immunity.size()<4096||immunity.containsKey(target))) {
            marks.remove(key);immunity.put(target,tick+160);return new Result(0,true);
        }
        if(marks.size()<4096||marks.containsKey(key)) marks.put(key,new Mark(stacks,tick+120));
        return new Result(stacks,false);
    }
    public int stacks(UUID owner,UUID target,long tick) {
        var mark=marks.get(new Key(owner,target));return mark==null||mark.until<=tick?0:mark.stacks;
    }
    public void prune(long tick) { marks.values().removeIf(m->m.until<=tick);immunity.values().removeIf(t->t<=tick); }
    public void owner(UUID owner) { marks.keySet().removeIf(k->k.owner.equals(owner)); }
    public void target(UUID target) { marks.keySet().removeIf(k->k.target.equals(target));immunity.remove(target); }
    public void clear() { marks.clear();immunity.clear(); }
    public int size() { return marks.size(); }
}

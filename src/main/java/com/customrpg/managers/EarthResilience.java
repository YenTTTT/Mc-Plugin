package com.customrpg.managers;

import java.util.*;

/** Tick-based innate mechanic, independent of particles and wall-clock time. */
final class EarthResilience {
    private record State(int stacks,long expires,long next){}
    private final Map<UUID,State> states=new HashMap<>();
    int stacks(UUID id,long tick){var s=states.get(id);return s!=null&&s.expires>tick?s.stacks:0;}
    boolean gain(UUID id,long tick){
        var s=states.get(id);if(s!=null&&s.next>tick)return false;
        if(s==null&&states.size()>=1024)return false;
        states.put(id,new State(Math.min(3,stacks(id,tick)+1),tick+160,tick+40));return true;
    }
    boolean consume(UUID id,long tick){if(stacks(id,tick)<3)return false;var s=states.get(id);states.put(id,new State(0,tick,s.next));return true;}
    void remove(UUID id){states.remove(id);}void prune(long tick){states.values().removeIf(s->s.expires<=tick&&s.next<=tick);}void clear(){states.clear();}
}

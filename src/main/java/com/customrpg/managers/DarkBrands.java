package com.customrpg.managers;

import java.util.*;

/** Personal seven-second marks, victim-wide ten-second detonation immunity. Third stack alone never bursts. */
final class DarkBrands {
    private record Key(UUID owner,UUID victim){}
    private record Mark(int layers,long until){}
    private final Map<Key,Mark> marks=new HashMap<>();private final Map<UUID,Long> immunity=new HashMap<>();
    int layers(UUID owner,UUID victim,long tick){var s=marks.get(new Key(owner,victim));return s!=null&&s.until>tick?s.layers:0;}
    void add(UUID owner,UUID victim,long tick){var key=new Key(owner,victim);if(marks.size()<4096||marks.containsKey(key))marks.put(key,new Mark(Math.min(3,1+layers(owner,victim,tick)),tick+140));}
    boolean ready(UUID owner,UUID victim,long tick){return layers(owner,victim,tick)==3&&immunity.getOrDefault(victim,0L)<=tick&&(immunity.size()<4096||immunity.containsKey(victim));}
    boolean consume(UUID owner,UUID victim,long tick){if(!ready(owner,victim,tick))return false;marks.remove(new Key(owner,victim));immunity.put(victim,tick+200);return true;}
    void owner(UUID owner){marks.keySet().removeIf(k->k.owner.equals(owner));}void target(UUID victim){marks.keySet().removeIf(k->k.victim.equals(victim));immunity.remove(victim);}
    void prune(long tick){marks.values().removeIf(s->s.until<=tick);immunity.values().removeIf(n->n<=tick);}void clear(){marks.clear();immunity.clear();}
}

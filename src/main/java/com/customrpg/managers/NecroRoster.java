package com.customrpg.managers;

/** Pure roster rules used by the real entity controller and tests. */
final class NecroRoster {
    enum Kind {
        WARRIOR(10,240,2,4,1),KNIGHT(14,160,2,2,1),COLOSSUS(20,160,3,4,2);
        final int hp,duration,damage,attacks,slots;
        Kind(int hp,int duration,int damage,int attacks,int slots){this.hp=hp;this.duration=duration;this.damage=damage;this.attacks=attacks;this.slots=slots;}
    }
    static final class Attack {
        private int used,bonus;private long next;
        Attack(long spawned){next=spawned+40;}
        void command(){bonus=2;}
        boolean ready(Kind kind,long tick){return used<kind.attacks&&tick>=next;}
        int commit(Kind kind,long tick){if(!ready(kind,tick))return 0;used++;next=tick+40;int extra=bonus>0?1:0;if(bonus>0)bonus--;return kind.damage+extra;}
        int used(){return used;}
    }
    private NecroRoster(){}
}

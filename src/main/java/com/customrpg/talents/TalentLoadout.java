package com.customrpg.talents;

/** Explicit supported ultimates; retired v3 IDs remain persisted and inert. */
public final class TalentLoadout {
    private TalentLoadout(){}
    public static boolean ultimate(String id){return FrostSkill.ultimate(id)||EarthSkill.ultimate(id)||DarkSkill.ultimate(id)||NecroSkill.ultimate(id)||ChickenSkill.ultimate(id);}
    public static double mana(Talent talent,int rank){return ChickenSkill.handles(talent.getId())?ChickenSkill.mana(talent,rank):NecroSkill.handles(talent.getId())?NecroSkill.mana(talent,rank):DarkSkill.handles(talent.getId())?DarkSkill.mana(talent,rank):EarthSkill.handles(talent.getId())?EarthSkill.mana(talent,rank):FrostSkill.mana(talent,rank);}
}

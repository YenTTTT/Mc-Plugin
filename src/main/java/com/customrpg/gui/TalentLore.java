package com.customrpg.gui;

import com.customrpg.talents.*;
import java.math.BigDecimal;
import java.util.*;

/** Player-facing talent details. Keep every skill view on the same six fields. */
public final class TalentLore {
    private TalentLore() {}
    private static final String WEAPON = "武器基礎傷害（含力量×0.2）";
    private static final String ALL = "（力量＋魔力＋敏捷＋體力＋防禦）";
    public static List<String> describe(Talent talent, int learnedLevel) {
        int level = Math.clamp(learnedLevel, 1, talent.getMaxLevel());
        boolean active = talent.getType() == TalentType.ACTIVE;
        String trigger = active ? (TalentLoadout.ultimate(talent.getId()) ? "已攜帶後按Shift＋F／" + talent.getMechanism() + "右鍵" : TalentItemBinding.triggerName(talent.getTriggerType()) + "／已攜帶後按1～4或F")
                : talent.getType() == TalentType.WEAPON_PASSIVE ? "符合武器條件時自動觸發" : "學習後自動生效";
        String cost = active ? consumption(talent, level) : "無";
        return List.of("§f技能介紹: " + talent.getDescription(),
                "§7觸發方式: §e" + trigger,
                "§7冷卻: §f" + (active ? number(talent.getCooldown()) + "秒" : "無"),
                "§7消耗: §b" + cost,
                "§7機制物品: §f" + Objects.requireNonNullElse(talent.getMechanism(), "無"),
                "§7傷害公式: §f" + formula(talent, level));
    }
    private static String consumption(Talent t, int level) {
        var costs = new ArrayList<String>();
        double mana=TalentLoadout.mana(t,level);
        if (mana > 0) costs.add(number(mana) + " MANA");
        if (t.getHpCost() > 0) costs.add(number(t.getHpCost() * 100) + "%最大生命（可受被動減免）");
        int blood = switch (t.getId()) {
            case "piercing_blood" -> 10;
            case "blood_wave", "blood_binding" -> 15;
            case "blood_burst" -> 20;
            case "blood_frenzy" -> 30;
            default -> 0;
        };
        if (blood > 0) costs.add(blood + " HP");
        if (Set.of("critical_arrow", "burst_mode", "wind_trap").contains(t.getId())) costs.add("全部專注");
        if (t.getId().equals("burst_mode")) costs.add("全部連擊");
        if (t.getId().equals("blood_frenzy")) costs.add("持續期間每秒" + number(value(t,level,"healthDrainPerSecond",2)) + " HP");
        if (t.getId().equals("soul_burn")) costs.add("持續期間每秒3%最大生命（保留超過2 HP）");
        return costs.isEmpty() ? "無" : String.join("＋", costs);
    }
    public static String formula(Talent t, int level) {
        if (t.getType() != TalentType.ACTIVE) return "無直接傷害";
        var data = t.getLevelData(level);
        if (data == null) return "無直接傷害";
        String id = t.getId();
        if(ChickenSkill.handles(id)){
            double d=value(t,level,"baseDamage",0);String power="＋總精神×"+number(value(t,level,"spiritScaling",ChickenSkill.of(id).spiritCoefficient()*(ChickenSkill.ultimate(id)?1:1+.1*(Math.clamp(level,1,3)-1))));String f=switch(ChickenSkill.of(id)){
                case EGG->"雞蛋命中 "+number(d)+power+" 物理基礎傷害";
                case DAWN->"僅Boss "+number(d)+power+" 魔法基礎傷害；一般敵人無直接傷害";
                case ROOSTER->"每啄 "+"（"+number(d)+power+"）物理×最多3次，間隔0.5秒";
                case STAMPEDE->number(d)+power+"＋全部參戰小雞數（無上限），每敵一次物理基礎傷害";
                case SUPPLY->"兩隻成年雞，每隻每秒1生命＋2魔力，10秒；無直接傷害";
                case GOD->"落地16＋總精神×0.6物理；留場30秒，每啄2＋總精神×0.1物理，最多15次；40生命";
                default->"無直接傷害";
            };return f.equals("無直接傷害")?f:f+settled();
        }
        if(NecroSkill.handles(id)){
            double d=value(t,level,"baseDamage",0);String f=switch(NecroSkill.of(id)){
                case LANCE->"首敵 "+number(d)+"，次敵 "+number(d*5/8)+"，每敵一次";
                case SUMMON->"每名戰士2物理基礎傷害×最多4次，兩名；號令每名最多兩次各＋1";
                case COMMAND,PRISON,SACRIFICE->"魔法基礎傷害 "+number(d);
                case COLOSSUS->"巨衛3物理基礎傷害×最多4次；號令最多兩次各＋1";
                case GATE->"三波 "+number(d)+"＋"+number(d)+"＋"+number(d*1.5)+"；兩名精銳各2物理×最多2次";
                default->"無直接傷害";
            };return f.equals("無直接傷害")?f:f+settled();
        }
        if(DarkSkill.handles(id)){
            double d=value(t,level,"baseDamage",0);String formula=switch(DarkSkill.of(id)){
                case REND->"第一刃 "+number(d)+"＋第二刃 "+number(d*4/3)+"，相隔0.2秒";
                case STITCH,CURTAIN->"魔法基礎傷害 "+number(d);
                case ECHO->"min（5，min（14，4秒內自身暗黑直接實際生命傷害）×35%）；不再乘等級倍率";
                case SERVANT->"每1.2秒 "+number(d)+"，最多4次／"+number(d*4);
                case ECLIPSE->"前搖1.25秒爆發 "+number(d)+"＋侵蝕3次×"+number(d*2/13)+"，總基礎 "+number(d*19/13);
                default->"無直接傷害";
            };return formula.equals("無直接傷害")?formula:formula+settled();
        }
        if(EarthSkill.handles(id)) {
            double damage=value(t,level,"baseDamage",0);String base=number(damage);
            String formula=switch(EarthSkill.of(id)){
                case STRIKE->"物理基礎傷害 "+base;
                case SINK->"首次 "+base+"，其後每2秒 "+number(damage*2/3)+"，每敵最多 "+number(damage*7/3);
                case REPRISAL->"首次有效敵方直接受擊後反擊 "+base;
                case TITAN->"前搖1.25秒後範圍傷害 "+base;
                default->"無直接傷害";
            };
            return formula.equals("無直接傷害")?formula:formula+"（原生生命基礎值，再依既有等級換算、技能預算與減傷結算）";
        }
        if(FrostSkill.handles(id)) {
            double amount=t.getEffectDouble(level,"baseDamage",0);if(amount==0)return "無直接傷害";
            String basis=switch(FrostSkill.of(id)) {
                case MIST->"每2秒 "+number(amount)+"，最多3次";
                case PRISM->"首次 "+number(amount)+"，後續各 "+number(amount*2/3);
                case MIRROR->"首次反擊 "+number(amount);
                default->"每次 "+number(amount);
            };
            return basis+" 原生生命基礎"+settled();
        }
        if (Set.of("light_mend","light_purge","light_sanctuary","earth_bastion","earth_guard","storm_dash",
                "nen_kurapika_heal","nen_zetsu","nen_en","nen_killua_step","nen_netero_prayer").contains(id)) return "無直接傷害";
        String base;
        if (Set.of(TalentBranch.FROST,TalentBranch.STORM,TalentBranch.EARTH,TalentBranch.LIGHT,TalentBranch.HUNTER).contains(t.getBranch())) {
            base = sum(data.effects, true);
            if (base.isEmpty()) return "無直接傷害";
            base = "min（200，" + base + "）";
            return switch (id) {
                case "frost_blizzard", "storm_tempest" -> "每秒 " + base + settled();
                case "frost_lance" -> "每次 " + base + "；緩速目標再×" + number(1 + value(t,level,"chilledBonus",.2)) + settled();
                case "light_lance" -> "每次 " + base + "；不死生物再×" + number(1 + value(t,level,"undeadBonus",.2)) + settled();
                case "storm_arc" -> "第n跳 " + base + "×0.8^(n−1)" + settled();
                case "nen_gon_combo" -> "三段 " + base + "×（1.2／1／1）" + settled();
                case "nen_bodhisattva" -> "三掌 " + base + "×（1／1／1.4）" + settled();
                case "nen_kurapika_judgment" -> "命中 " + base + "；觸發審判再造成1.5倍" + settled();
                case "nen_netero_ninetynine" -> "每掌 " + base + "，最多5掌" + settled();
                case "nen_hisoka_snare" -> "每0.5秒 " + base + settled();
                default -> "每次 " + base + settled();
            };
        }
        base = sum(data.effects, false);
        String magic = "魔力×" + number(value(t,level,"magicScaling",0));
        String duration = number(value(t,level,"duration",1));
        String result = switch (id) {
            case "fire_spark", "scorching_wave", "shadow_bolt", "soul_drain", "death_mark" -> "每次 " + magic;
            case "destruction_orb" -> "每次接觸 " + magic + "（同目標每0.5秒最多一次）";
            case "flame_storm" -> "每秒 " + magic + "÷" + duration;
            case "flame_jet" -> "每0.25秒每次接觸 " + magic + "÷（" + duration + "×4）";
            case "meteor" -> "每次 " + magic + "×max（0.3，1−距離÷" + number(value(t,level,"radius",6)) + "×0.5）";
            case "inferno_ring", "reaper_domain" -> "每秒 " + magic;
            case "hell_domain" -> "每0.5秒 （" + base + "）÷2";
            case "dark_flame" -> "每次 " + magic + "×0.25";
            case "soul_burn" -> "每0.5秒 " + magic + "×0.2";
            case "death_god" -> "每秒 " + base;
            case "earth_shatter", "magic_vortex" -> "每次 " + weapon(t,level,"damageMultiplier",1);
            case "sniper_shot" -> "每次 " + weapon(t,level,"damageMultiplier",1) + "×2";
            case "suppressive_barrage", "ricochet_round", "cyclone_slash", "meteor_step", "ultimate_sword_rain" ->
                    "每次 " + weapon(t,level,"damageMultiplier",1) + attributes(data.effects);
            case "arcane_barrage" -> "每發 " + weapon(t,level,"boltDamageMultiplier",.9) + attributes(data.effects);
            case "gravity_prison" -> "每次脈衝 " + weapon(t,level,"tickDamageMultiplier",.55) + attributes(data.effects);
            case "shadow_assault", "phantom_massacre", "rapid_slash", "cross_blade", "phantom_flurry", "arc_slash", "crescent_shadow" ->
                    "每段 " + base + (id.equals("cross_blade") ? "，同時命中每多一名敵人再加" + number(value(t,level,"multiHitBonus",.15)*100) + "%" : "");
            case "tornado_lv2" -> "每段 （" + base + "）÷" + number(value(t,level,"hitCount",4));
            case "flourish_lv2" -> "每tick " + ALL + "×" + number(value(t,level,"damagePerSecond",.43)) + "÷20";
            case "cleave" -> "每次 " + number(value(t,level,"baseDamage",30)) + "＋目標最大生命×" + number(value(t,level,"maxHealthScaling",.15));
            case "piercing_blood", "blood_wave" -> "每段 " + number(value(t,level,"baseDamage",0)) + "＋消耗後生命值×" + number(value(t,level,"bloodScaling",0));
            case "blood_burst" -> "每次 " + number(value(t,level,"baseDamage",60)) + "＋消耗後生命值×" + number(value(t,level,"bloodScaling",1))
                    + "＋" + number(value(t,level,"baseDamage",60)*value(t,level,"bloodPercentBonus",.5)) + "×消耗後生命比例";
            case "blood_binding" -> "每次 " + number(value(t,level,"damage",20));
            case "shock_gun" -> "每次 " + number(value(t,level,"baseDamage",40)) + "＋魔力×0.3";
            case "grenade" -> "每次 " + number(value(t,level,"baseDamage",60)) + "＋魔力×0.4";
            case "orbital_strike" -> "每次 " + number(value(t,level,"baseDamage",150)) + "＋魔力";
            case "cluster_bomb" -> "每顆 " + number(value(t,level,"baseDamage",40)) + "＋魔力×0.3";
            case "annihilation_barrage" -> "每波 " + number(value(t,level,"baseDamage",30)) + "＋魔力×0.5";
            case "predator_dash" -> "每次 （" + number(value(t,level,"baseDamage",15)) + "＋力量×0.5）×變身倍率";
            case "critical_arrow" -> "箭矢基礎 " + number(value(t,level,"baseDamage",15)) + "＋消耗專注×" + number(value(t,level,"damagePerFocus",4)) + "；命中依箭速結算";
            case "arrow_storm" -> "每箭基礎 " + number(value(t,level,"baseDamage",6)) + "×（1＋連擊×0.05）；命中依箭速結算";
            case "split_arrow" -> "主箭基礎 " + number(value(t,level,"baseDamage",8)) + "；分裂箭×" + number(value(t,level,"splitDamageRatio",.7)) + "；命中依箭速結算";
            case "burst_mode" -> "每箭基礎 " + number(value(t,level,"damagePerArrow",5)) + "×（1＋消耗連擊×0.08）；命中依箭速結算";
            case "slow_arrow" -> "箭矢基礎 " + number(value(t,level,"slowArrowDamage",4)) + "；命中依箭速結算";
            case "wind_trap" -> "每0.5秒 " + number(value(t,level,"damagePerTick",2));
            case "bee_swarm" -> "每隻攻擊蜂 " + number(value(t,level,"attackBeeDamage",4));
            case "beast_army" -> "戰狼每擊 " + number(value(t,level,"wolfDamage",10)) + "；巨熊每擊 " + number(value(t,level,"bearDamage",15));
            case "call_of_wild", "pack_leader", "savage_summon", "beast_master" -> "召喚獸每擊 " + base;
            case "malevolent_shrine" -> "每次斬擊 " + base;
            default -> base.isEmpty() ? "無直接傷害" : "每次 " + base;
        };
        return result.equals("無直接傷害") ? result : result + settled();
    }
    private static String settled() { return "（實傷另計等級、減傷與傷害上限）"; }
    private static String weapon(Talent t,int level,String key,double fallback) {
        return WEAPON + "×" + number(value(t,level,key,fallback));
    }
    private static double value(Talent t,int level,String key,double fallback) { return t.getEffectDouble(level,key,fallback); }
    private static String sum(Map<String,Double> effects, boolean total) {
        var parts = new ArrayList<String>();
        if (effects.getOrDefault("baseDamage",0.0)!=0) parts.add(number(effects.get("baseDamage")));
        for (String key : List.of("damageMultiplier","weaponScaling","weaponDamageScaling"))
            if (effects.getOrDefault(key,0.0)!=0) parts.add(WEAPON + "×" + number(effects.get(key)));
        if (effects.getOrDefault("allStatsScaling",0.0)!=0) parts.add(ALL + "×" + number(effects.get("allStatsScaling")));
        String attributes = attributes(effects,total);
        if (!attributes.isEmpty()) parts.add(attributes.substring(1));
        return String.join("＋",parts);
    }
    private static String attributes(Map<String,Double> effects) { return attributes(effects,false); }
    private static String attributes(Map<String,Double> effects, boolean total) {
        StringBuilder result = new StringBuilder();
        String[][] keys = {{"strengthScaling","力量"},{"magicScaling","魔力"},{"agilityScaling","敏捷"},{"spiritScaling","精神"}};
        for (var key : keys) if (effects.getOrDefault(key[0],0.0)!=0)
            result.append("＋").append(total?"總":"").append(key[1]).append("×").append(number(effects.get(key[0])));
        return result.toString();
    }
    private static String number(double value) { return BigDecimal.valueOf(value).setScale(4,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
}

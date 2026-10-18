package com.customrpg.races;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Collections;
import java.util.Objects;

/**
 * RaceData - 種族資料模型
 *
 * 存儲種族的所有屬性：
 * - 基礎屬性加成
 * - 成長倍率
 * - 種族技能
 * - 武器親和度
 */
public class RaceData {

    private final String id;
    private final String displayName;
    private final String description;

    // 基礎屬性加成
    private final int baseStrength;
    private final int baseMagic;
    private final int baseAgility;
    private final int baseVitality;
    private final int baseDefense;
    private final int baseSpirit;
    private final int baseMana;          // 基礎魔力加成
    private final double baseManaRegen;  // 基礎魔力回復加成

    // 成長倍率 (每級屬性成長 = 基礎成長 * growthMultiplier)
    private final double growthStrength;
    private final double growthMagic;
    private final double growthAgility;
    private final double growthVitality;
    private final double growthDefense;
    private final double growthSpirit;
    private final double growthMana;         // 每級魔力成長
    private final double growthManaRegen;    // 每級魔力回復成長

    // 種族技能 (最多2個)
    private final List<String> skills;

    // 武器類型傷害加成 (武器類型名稱 → 傷害倍率)
    // 例如: {"SWORD": 1.3, "BOW": 0.8, "AXE": 1.5}
    private final Map<String, Double> weaponBonuses;
    private final double defaultWeaponBonus; // 未列出的武器類型使用此預設倍率

    // 被動效果
    private final double bonusCritChance;     // 額外暴擊率
    private final double bonusCritDamage;     // 額外暴擊傷害
    private final double bonusMoveSpeed;      // 移動速度加成
    private final double bonusHealthRegen;    // 生命回復加成

    private RaceData(Builder builder) {
        this.id = Objects.requireNonNull(builder.id).toLowerCase(Locale.ROOT);
        if(!id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid race ID: "+id);
        this.displayName = builder.displayName;
        this.description = builder.description;

        this.baseStrength = builder.baseStrength;
        this.baseMagic = builder.baseMagic;
        this.baseAgility = builder.baseAgility;
        this.baseVitality = builder.baseVitality;
        this.baseDefense = builder.baseDefense;
        this.baseSpirit = builder.baseSpirit;
        this.baseMana = builder.baseMana;
        this.baseManaRegen = builder.baseManaRegen;

        this.growthStrength = builder.growthStrength;
        this.growthMagic = builder.growthMagic;
        this.growthAgility = builder.growthAgility;
        this.growthVitality = builder.growthVitality;
        this.growthDefense = builder.growthDefense;
        this.growthSpirit = builder.growthSpirit;
        this.growthMana = builder.growthMana;
        this.growthManaRegen = builder.growthManaRegen;

        this.skills = List.copyOf(builder.skills);
        if(skills.size()>2) throw new IllegalArgumentException("A race supports at most two planned skills");
        Map<String,Double> bonuses=new LinkedHashMap<>();
        builder.weaponBonuses.forEach((key,value)->{
            String type=key.toUpperCase(Locale.ROOT);
            com.customrpg.managers.WeaponManager.WeaponCategory.valueOf(type);
            range("weapon multiplier",value,0.1,3); bonuses.put(type,value);
        });
        this.weaponBonuses = Collections.unmodifiableMap(bonuses);
        this.defaultWeaponBonus = builder.defaultWeaponBonus;

        this.bonusCritChance = builder.bonusCritChance;
        this.bonusCritDamage = builder.bonusCritDamage;
        this.bonusMoveSpeed = builder.bonusMoveSpeed;
        this.bonusHealthRegen = builder.bonusHealthRegen;
        for(double value:new double[]{baseStrength,baseMagic,baseAgility,baseVitality,baseDefense,baseSpirit}) range("base stat",value,0,1000);
        range("base mana",baseMana,0,10000); range("base mana regen",baseManaRegen,0,100);
        for(double value:new double[]{growthStrength,growthMagic,growthAgility,growthVitality,growthDefense,growthSpirit,growthMana}) range("growth",value,0,100);
        range("mana regen growth",growthManaRegen,0,10); range("weapon default",defaultWeaponBonus,0.1,3);
        range("critical chance",bonusCritChance,0,100); range("critical damage",bonusCritDamage,0,3);
        range("movement speed",bonusMoveSpeed,-0.5,0.5); range("health regen",bonusHealthRegen,-10,10);
    }
    private static void range(String name,double value,double min,double max) {
        if(!Double.isFinite(value)||value<min||value>max) throw new IllegalArgumentException("Invalid race "+name+": "+value);
    }
    private static long levelsGained(int level) { return Math.max(0L,(long)level-1); }
    public int[] effectiveStats(int level) {
        int baseBudget = com.customrpg.balance.BalancePlayerRules.raceBudget(1);
        int[] base = distribute(new double[]{baseStrength,baseMagic,baseAgility,baseVitality,baseDefense,baseSpirit},
                baseBudget, true);
        int[] growth = distribute(new double[]{growthStrength,growthMagic,growthAgility,growthVitality,growthDefense,growthSpirit},
                com.customrpg.balance.BalancePlayerRules.raceBudget(level) - baseBudget, false);
        for (int i = 0; i < base.length; i++) base[i] += growth[i];
        return base;
    }

    /** Fixed weights and a growing sequence of points prevent an attribute dropping on level-up. */
    private static int[] distribute(double[] weights, int budget, boolean minimumOne) {
        int[] result = new int[weights.length];
        int[] awarded = new int[weights.length];
        if (minimumOne) for (int i = 0; i < weights.length && budget > 0; i++) {
            if (weights[i] > 0) { result[i] = 1; budget--; }
        }
        for (int point = 0; point < budget; point++) {
            int best = -1;
            double highest = 0;
            for (int i = 0; i < weights.length; i++) {
                double score = weights[i] / (awarded[i] + 1);
                if (score > highest) { highest = score; best = i; }
            }
            if (best < 0) break; // Explicitly zero custom profiles must remain zero.
            result[best]++; awarded[best]++;
        }
        return result;
    }

    // ===== Getters =====

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public int getBaseStrength() { return baseStrength; }
    public int getBaseMagic() { return baseMagic; }
    public int getBaseAgility() { return baseAgility; }
    public int getBaseVitality() { return baseVitality; }
    public int getBaseDefense() { return baseDefense; }
    public int getBaseSpirit() { return baseSpirit; }
    public int getBaseMana() { return baseMana; }
    public double getBaseManaRegen() { return baseManaRegen; }

    public double getGrowthStrength() { return growthStrength; }
    public double getGrowthMagic() { return growthMagic; }
    public double getGrowthAgility() { return growthAgility; }
    public double getGrowthVitality() { return growthVitality; }
    public double getGrowthDefense() { return growthDefense; }
    public double getGrowthSpirit() { return growthSpirit; }
    public double getGrowthMana() { return growthMana; }
    public double getGrowthManaRegen() { return growthManaRegen; }

    public List<String> getSkills() { return skills; }
    public Map<String, Double> getWeaponBonuses() {
        Map<String,Double> result=new LinkedHashMap<>(); weaponBonuses.forEach((key,value)->result.put(key,Math.clamp(value,.98,1.02)));
        return Collections.unmodifiableMap(result);
    }
    public double getDefaultWeaponBonus() { return Math.clamp(defaultWeaponBonus,.98,1.02); }

    public double getBonusCritChance() { return Math.min(1,bonusCritChance); }
    public double getBonusCritDamage() { return Math.min(.05,bonusCritDamage); }
    public double getBonusMoveSpeed() { return Math.clamp(bonusMoveSpeed,-.03,.03); }
    public double getBonusHealthRegen() { return bonusHealthRegen; }

    /**
     * 取得指定武器類型的傷害加成倍率
     * @param weaponType 武器類型名稱 (如 "SWORD", "BOW" 等)
     * @return 傷害倍率 (例: 1.3 = 130% 傷害)
     */
    public double getWeaponBonus(String weaponType) {
        if (weaponType == null) return Math.clamp(defaultWeaponBonus,.98,1.02);
        Double bonus = weaponBonuses.get(weaponType.toUpperCase(Locale.ROOT));
        return Math.clamp(bonus != null ? bonus : defaultWeaponBonus,.98,1.02);
    }

    /**
     * 根據玩家等級計算種族帶來的總屬性加成
     * Base and growth settings are weights within separate bounded budgets.
     */
    public int calculateStrengthBonus(int level) {
        return effectiveStats(level)[0];
    }

    public int calculateMagicBonus(int level) {
        return effectiveStats(level)[1];
    }

    public int calculateAgilityBonus(int level) {
        return effectiveStats(level)[2];
    }

    public int calculateVitalityBonus(int level) {
        return effectiveStats(level)[3];
    }

    public int calculateDefenseBonus(int level) {
        return effectiveStats(level)[4];
    }

    public int calculateSpiritBonus(int level) {
        return effectiveStats(level)[5];
    }

    /**
     * 根據玩家等級計算種族的魔力加成
     */
    public double calculateManaBonus(int level) {
        return Math.min(10,(baseMana + levelsGained(level)*growthMana)*.05);
    }

    /**
     * 根據玩家等級計算種族的魔力回復加成
     */
    public double calculateManaRegenBonus(int level) {
        return Math.min(.25,(baseManaRegen + levelsGained(level)*growthManaRegen)*.05);
    }

    /**
     * 獲取所有基礎屬性的總覽 Map (用於 GUI 顯示)
     */
    public Map<String, Integer> getBaseStatsMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("力量", baseStrength);
        map.put("魔法", baseMagic);
        map.put("敏捷", baseAgility);
        map.put("體力", baseVitality);
        map.put("防禦", baseDefense);
        map.put("精神", baseSpirit);
        map.put("魔力", baseMana);
        return map;
    }

    /**
     * 獲取所有成長倍率的總覽 Map (用於 GUI 顯示)
     */
    public Map<String, Double> getGrowthMap() {
        Map<String, Double> map = new HashMap<>();
        map.put("力量", growthStrength);
        map.put("魔法", growthMagic);
        map.put("敏捷", growthAgility);
        map.put("體力", growthVitality);
        map.put("防禦", growthDefense);
        map.put("精神", growthSpirit);
        map.put("魔力", growthMana);
        map.put("魔力回復", growthManaRegen);
        return map;
    }

    // ===== Builder =====

    public static class Builder {
        private String id;
        private String displayName = "未知種族";
        private String description = "";

        private int baseStrength = 5;
        private int baseMagic = 5;
        private int baseAgility = 5;
        private int baseVitality = 5;
        private int baseDefense = 5;
        private int baseSpirit = 5;
        private int baseMana = 0;
        private double baseManaRegen = 0.0;

        private double growthStrength = 1.0;
        private double growthMagic = 1.0;
        private double growthAgility = 1.0;
        private double growthVitality = 1.0;
        private double growthDefense = 1.0;
        private double growthSpirit = 1.0;
        private double growthMana = 0.0;
        private double growthManaRegen = 0.0;

        private List<String> skills = new ArrayList<>();
        private Map<String, Double> weaponBonuses = new LinkedHashMap<>();
        private double defaultWeaponBonus = 1.0;

        private double bonusCritChance = 0.0;
        private double bonusCritDamage = 0.0;
        private double bonusMoveSpeed = 0.0;
        private double bonusHealthRegen = 0.0;

        public Builder(String id) {
            this.id = id;
        }

        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder description(String description) { this.description = description; return this; }

        public Builder baseStrength(int v) { this.baseStrength = v; return this; }
        public Builder baseMagic(int v) { this.baseMagic = v; return this; }
        public Builder baseAgility(int v) { this.baseAgility = v; return this; }
        public Builder baseVitality(int v) { this.baseVitality = v; return this; }
        public Builder baseDefense(int v) { this.baseDefense = v; return this; }
        public Builder baseSpirit(int v) { this.baseSpirit = v; return this; }
        public Builder baseMana(int v) { this.baseMana = v; return this; }
        public Builder baseManaRegen(double v) { this.baseManaRegen = v; return this; }

        public Builder growthStrength(double v) { this.growthStrength = v; return this; }
        public Builder growthMagic(double v) { this.growthMagic = v; return this; }
        public Builder growthAgility(double v) { this.growthAgility = v; return this; }
        public Builder growthVitality(double v) { this.growthVitality = v; return this; }
        public Builder growthDefense(double v) { this.growthDefense = v; return this; }
        public Builder growthSpirit(double v) { this.growthSpirit = v; return this; }
        public Builder growthMana(double v) { this.growthMana = v; return this; }
        public Builder growthManaRegen(double v) { this.growthManaRegen = v; return this; }

        public Builder skills(List<String> skills) { this.skills = skills; return this; }
        public Builder weaponBonuses(Map<String, Double> bonuses) { this.weaponBonuses = bonuses; return this; }
        public Builder defaultWeaponBonus(double v) { this.defaultWeaponBonus = v; return this; }

        public Builder bonusCritChance(double v) { this.bonusCritChance = v; return this; }
        public Builder bonusCritDamage(double v) { this.bonusCritDamage = v; return this; }
        public Builder bonusMoveSpeed(double v) { this.bonusMoveSpeed = v; return this; }
        public Builder bonusHealthRegen(double v) { this.bonusHealthRegen = v; return this; }

        public RaceData build() {
            return new RaceData(this);
        }
    }
}


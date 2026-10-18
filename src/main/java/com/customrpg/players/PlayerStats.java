package com.customrpg.players;

import java.util.HashMap;
import java.util.Map;

/**
 * PlayerStats - 玩家數據模型
 *
 * 儲存玩家的 RPG 屬性：
 * - Strength (物理攻擊)
 * - Magic (魔法攻擊)
 * - Agility (敏捷 - 暴擊/遠程)
 * - Vitality (生命力 - 最大血量)
 * - Defense (防禦)
 * - Spirit (精神)
 */
public class PlayerStats {

    // 基礎屬性（從點數分配）
    private int strength;    // 物理攻擊
    private int magic;       // 魔法攻擊
    private int agility;     // 敏捷 (影響暴擊率 & 弓箭傷害)
    private int vitality;    // 生命力 (影響最大血量)
    private int defense;     // 防禦 (減免傷害)
    private int spirit;      // 精神

    // 裝備屬性加成
    private int equipmentStrength;
    private int equipmentMagic;
    private int equipmentAgility;
    private int equipmentVitality;
    private int equipmentDefense;
    private int equipmentSpirit;

    // 種族屬性加成
    private int raceStrength;
    private int raceMagic;
    private int raceAgility;
    private int raceVitality;
    private int raceDefense;
    private int raceSpirit;

    // Transient talent sources; never mixed into persisted free allocations.
    private int talentStrength;
    public int getTalentStrength() { return talentStrength; }
    public void setTalentStrength(int value) { talentStrength = Math.max(0, value); }
    private int talentMagic;
    public int getTalentMagic() { return talentMagic; }
    public void setTalentMagic(int value) { talentMagic = Math.max(0, value); }
    private int talentAgility;
    public int getTalentAgility() { return talentAgility; }
    public void setTalentAgility(int value) { talentAgility = Math.max(0, value); }
    private int talentVitality;
    public int getTalentVitality() { return talentVitality; }
    public void setTalentVitality(int value) { talentVitality = Math.max(0, value); }
    private int talentDefense;
    public int getTalentDefense() { return talentDefense; }
    public void setTalentDefense(int value) { talentDefense = Math.max(0, value); }
    private int talentSpirit;
    public int getTalentSpirit() { return talentSpirit; }
    public void setTalentSpirit(int value) { talentSpirit = Math.max(0, value); }

    // 其他屬性加成（未來可擴展：天賦、BUFF等）
    private double bonusAttackDamage;
    private double bonusDefenseValue;
    private double bonusMaxHealth;
    private double formMaxHealth;
    public double getFormMaxHealth() { return formMaxHealth; }
    public void setFormMaxHealth(double value) { formMaxHealth=Math.max(0,value); }
    private double talentMaxHealth;
    public double getTalentMaxHealth() { return talentMaxHealth; }
    public void setTalentMaxHealth(double value) { talentMaxHealth=Math.max(0,value); }
    private double bonusCritChance;
    private double bonusCritDamage;
    private double bonusMaxMana;
    private double bonusManaRegen;

    private int balanceVersion = com.customrpg.balance.BalanceMigration.VERSION;
    public int getBalanceVersion() { return balanceVersion; }
    public void setBalanceVersion(int version) { balanceVersion = version; }

    private int level;
    private long exp;
    private int statPoints;
    private int talentPoints;       // 天賦點數

    // Mana 系統
    private double currentMana;     // 當前魔力值
    private double maxMana;         // 最大魔力值
    private double manaRegen;       // 魔力回復速度 (每秒回復量)

    /**
     * 預設建構子，初始屬性皆為 0
     */
    public PlayerStats() {
        this.strength = 0;
        this.magic = 0;
        this.agility = 0;
        this.vitality = 0;
        this.defense = 0;
        this.spirit = 0;

        this.equipmentStrength = 0;
        this.equipmentMagic = 0;
        this.equipmentAgility = 0;
        this.equipmentVitality = 0;
        this.equipmentDefense = 0;
        this.equipmentSpirit = 0;

        this.raceStrength = 0;
        this.raceMagic = 0;
        this.raceAgility = 0;
        this.raceVitality = 0;
        this.raceDefense = 0;
        this.raceSpirit = 0;

        this.bonusAttackDamage = 0.0;
        this.bonusDefenseValue = 0.0;
        this.bonusMaxHealth = 0.0;
        this.bonusCritChance = 0.0;
        this.bonusCritDamage = 0.0;
        this.bonusMaxMana = 0.0;
        this.bonusManaRegen = 0.0;

        this.level = 1;
        this.exp = 0;
        this.statPoints = 0;
        this.talentPoints = 0;      // 初始天賦點數為0

        this.maxMana = 80.0;
        this.currentMana = 80.0;
        this.manaRegen = 1.5; // 預設每秒回復 1 點
    }

    /**
     * 完整建構子
     */
    public PlayerStats(int strength, int magic, int agility, int vitality, int defense, int spirit, int level, long exp, int statPoints, int talentPoints) {
        this.strength = strength;
        this.magic = magic;
        this.agility = agility;
        this.vitality = vitality;
        this.defense = defense;
        this.spirit = spirit;

        this.level = level;
        this.exp = exp;
        this.statPoints = statPoints;
        this.talentPoints = talentPoints;

        this.maxMana = 80.0;
        this.currentMana = 80.0;
        this.manaRegen = 1.5;
    }

    /**
     * 完整建構子（含 Mana）
     */
    public PlayerStats(int strength, int magic, int agility, int vitality, int defense, int spirit,
                      int level, long exp, int statPoints, int talentPoints,
                      double maxMana, double currentMana, double manaRegen) {
        this.strength = strength;
        this.magic = magic;
        this.agility = agility;
        this.vitality = vitality;
        this.defense = defense;
        this.spirit = spirit;

        this.level = level;
        this.exp = exp;
        this.statPoints = statPoints;
        this.talentPoints = talentPoints;

        this.maxMana = maxMana;
        this.currentMana = currentMana;
        this.manaRegen = manaRegen;
    }

    // ===== Getters & Setters =====

    public int getStrength() {
        return strength;
    }

    public void setStrength(int strength) {
        this.strength = Math.max(0, strength);
    }

    public int getMagic() {
        return magic;
    }

    public void setMagic(int magic) {
        this.magic = Math.max(0, magic);
    }

    public int getAgility() {
        return agility;
    }

    public void setAgility(int agility) {
        this.agility = Math.max(0, agility);
    }

    public int getVitality() {
        return vitality;
    }

    public void setVitality(int vitality) {
        this.vitality = Math.max(0, vitality);
    }

    public int getDefense() {
        return defense;
    }

    public void setDefense(int defense) {
        this.defense = Math.max(0, defense);
    }

    public int getSpirit() {
        return spirit;
    }

    public void setSpirit(int spirit) {
        this.spirit = Math.max(0, spirit);
    }

    // ===== 裝備加成 Getters & Setters =====

    public int getEquipmentStrength() {
        return equipmentStrength;
    }

    public void setEquipmentStrength(int equipmentStrength) {
        this.equipmentStrength = Math.max(0, equipmentStrength);
    }

    public int getEquipmentMagic() {
        return equipmentMagic;
    }

    public void setEquipmentMagic(int equipmentMagic) {
        this.equipmentMagic = Math.max(0, equipmentMagic);
    }

    public int getEquipmentAgility() {
        return equipmentAgility;
    }

    public void setEquipmentAgility(int equipmentAgility) {
        this.equipmentAgility = Math.max(0, equipmentAgility);
    }

    public int getEquipmentVitality() {
        return equipmentVitality;
    }

    public void setEquipmentVitality(int equipmentVitality) {
        this.equipmentVitality = Math.max(0, equipmentVitality);
    }

    public int getEquipmentDefense() {
        return equipmentDefense;
    }

    public void setEquipmentDefense(int equipmentDefense) {
        this.equipmentDefense = Math.max(0, equipmentDefense);
    }

    public int getEquipmentSpirit() {
        return equipmentSpirit;
    }

    public void setEquipmentSpirit(int equipmentSpirit) {
        this.equipmentSpirit = Math.max(0, equipmentSpirit);
    }

    // ===== 種族加成 Getters & Setters =====

    public int getRaceStrength() { return raceStrength; }
    public void setRaceStrength(int raceStrength) { this.raceStrength = Math.max(0, raceStrength); }

    public int getRaceMagic() { return raceMagic; }
    public void setRaceMagic(int raceMagic) { this.raceMagic = Math.max(0, raceMagic); }

    public int getRaceAgility() { return raceAgility; }
    public void setRaceAgility(int raceAgility) { this.raceAgility = Math.max(0, raceAgility); }

    public int getRaceVitality() { return raceVitality; }
    public void setRaceVitality(int raceVitality) { this.raceVitality = Math.max(0, raceVitality); }

    public int getRaceDefense() { return raceDefense; }
    public void setRaceDefense(int raceDefense) { this.raceDefense = Math.max(0, raceDefense); }

    public int getRaceSpirit() { return raceSpirit; }
    public void setRaceSpirit(int raceSpirit) { this.raceSpirit = Math.max(0, raceSpirit); }

    // ===== 總屬性 (基礎 + 裝備加成 + 種族加成) =====

    /**
     * 獲取總力量（基礎 + 裝備 + 種族）
     */
    public int getTotalStrength() {
        return strength + equipmentStrength + raceStrength + talentStrength;
    }

    /**
     * 獲取總魔法（基礎 + 裝備 + 種族）
     */
    public int getTotalMagic() {
        return magic + equipmentMagic + raceMagic + talentMagic;
    }

    /**
     * 獲取總敏捷（基礎 + 裝備 + 種族）
     */
    public int getTotalAgility() {
        return agility + equipmentAgility + raceAgility + talentAgility;
    }

    /**
     * 獲取總生命力（基礎 + 裝備 + 種族）
     */
    public int getTotalVitality() {
        return vitality + equipmentVitality + raceVitality + talentVitality;
    }

    /**
     * 獲取總防禦（基礎 + 裝備 + 種族）
     */
    public int getTotalDefense() {
        return defense + equipmentDefense + raceDefense + talentDefense;
    }

    /**
     * 獲取總精神（基礎 + 裝備 + 種族）
     */
    public int getTotalSpirit() {
        return spirit + equipmentSpirit + raceSpirit + talentSpirit;
    }

    // ===== 其他加成 Getters & Setters =====

    public double getBonusAttackDamage() {
        return bonusAttackDamage;
    }

    public void setBonusAttackDamage(double bonusAttackDamage) {
        this.bonusAttackDamage = bonusAttackDamage;
    }

    public double getBonusDefenseValue() {
        return bonusDefenseValue;
    }

    public void setBonusDefenseValue(double bonusDefenseValue) {
        this.bonusDefenseValue = bonusDefenseValue;
    }

    public double getBonusMaxHealth() {
        return bonusMaxHealth;
    }

    public void setBonusMaxHealth(double bonusMaxHealth) {
        this.bonusMaxHealth = bonusMaxHealth;
    }

    public double getBonusCritChance() {
        return bonusCritChance;
    }

    public void setBonusCritChance(double bonusCritChance) {
        this.bonusCritChance = bonusCritChance;
    }

    public double getBonusCritDamage() {
        return bonusCritDamage;
    }

    public void setBonusCritDamage(double bonusCritDamage) {
        this.bonusCritDamage = bonusCritDamage;
    }

    public double getBonusMaxMana() {
        return bonusMaxMana;
    }

    public void setBonusMaxMana(double bonusMaxMana) {
        this.bonusMaxMana = Math.max(0, bonusMaxMana);
    }

    public double getBonusManaRegen() {
        return bonusManaRegen;
    }

    public void setBonusManaRegen(double bonusManaRegen) {
        this.bonusManaRegen = Math.max(0, bonusManaRegen);
    }

    // ===== Level, Exp, Points =====

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = com.customrpg.balance.BalanceMigration.level(level);
    }

    public long getExp() {
        return exp;
    }

    public void setExp(long exp) {
        this.exp = Math.max(0, exp);
    }

    public int getStatPoints() {
        return statPoints;
    }

    public void setStatPoints(int statPoints) {
        this.statPoints = Math.max(0, statPoints);
    }

    public int getTalentPoints() {
        return talentPoints;
    }

    public void setTalentPoints(int talentPoints) {
        this.talentPoints = Math.max(0, talentPoints);
    }

    public void addTalentPoints(int points) {
        this.talentPoints += points;
    }

    public boolean spendTalentPoints(int points) {
        if (talentPoints >= points) {
            talentPoints -= points;
            return true;
        }
        return false;
    }

    // ===== Mana Getters & Setters =====

    public double getCurrentMana() {
        return currentMana;
    }

    public void setCurrentMana(double currentMana) {
        this.currentMana = Math.max(0, Math.min(currentMana, maxMana));
    }

    public double getMaxMana() {
        return maxMana;
    }

    public void setMaxMana(double maxMana) {
        this.maxMana = Math.max(0, maxMana);
        // 確保當前魔力不超過新的最大值
        if (this.currentMana > this.maxMana) {
            this.currentMana = this.maxMana;
        }
    }

    public double getManaRegen() {
        return manaRegen;
    }

    public void setManaRegen(double manaRegen) {
        this.manaRegen = Math.max(0, manaRegen);
    }

    /**
     * 消耗魔力
     * @param amount 消耗量
     * @return 是否成功消耗（魔力是否足夠）
     */
    public boolean consumeMana(double amount) {
        if (currentMana >= amount) {
            currentMana -= amount;
            return true;
        }
        return false;
    }

    /**
     * 恢復魔力
     * @param amount 恢復量
     */
    public void restoreMana(double amount) {
        currentMana = Math.min(currentMana + amount, maxMana);
    }

    // ===== 序列化/反序列化 =====

    /**
     * 將 PlayerStats 轉換為 Map (用於儲存到 YAML)
     */
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("balance-version", balanceVersion);
        data.put("strength", strength);
        data.put("magic", magic);
        data.put("agility", agility);
        data.put("vitality", vitality);
        data.put("defense", defense);
        data.put("spirit", spirit);

        data.put("level", level);
        data.put("exp", exp);
        data.put("statPoints", statPoints);
        data.put("talentPoints", talentPoints);

        data.put("maxMana", maxMana);
        data.put("currentMana", currentMana);
        data.put("manaRegen", manaRegen);

        return data;
    }

    /**
     * 從 Map 反序列化為 PlayerStats
     */
    public static PlayerStats deserialize(Map<String, Object> data) {
        int str = getInt(data, "strength");
        int mag = getInt(data, "magic");
        int agi = getInt(data, "agility");
        int vit = getInt(data, "vitality");
        int def = getInt(data, "defense");
        int spirit = getInt(data, "spirit");

        int lvl = data.containsKey("level") ? getInt(data, "level") : 1;
        long xp = data.containsKey("exp") ? ((Number) data.get("exp")).longValue() : 0;
        int pts = data.containsKey("statPoints") ? getInt(data, "statPoints") : 0;
        int talentPts = data.containsKey("talentPoints") ? getInt(data, "talentPoints") : 0;

        double maxMana = data.containsKey("maxMana") ? getDouble(data, "maxMana") : 100.0;
        double currentMana = data.containsKey("currentMana") ? getDouble(data, "currentMana") : maxMana;
        double manaRegen = data.containsKey("manaRegen") ? getDouble(data, "manaRegen") : 1.0;

        PlayerStats result = new PlayerStats(str, mag, agi, vit, def, spirit, lvl, xp, pts, talentPts, maxMana, currentMana, manaRegen);
        result.setLevel(lvl);
        result.setBalanceVersion(data.containsKey("balance-version") ? getInt(data, "balance-version") : 0);
        return result;
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private static double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    @Override
    public String toString() {
        return "PlayerStats{" +
                "Strength=" + strength +
                ", Magic=" + magic +
                ", Agility=" + agility +
                ", Vitality=" + vitality +
                ", Defense=" + defense +
                ", Spirit=" + spirit +
                ", Level=" + level +
                ", Exp=" + exp +
                ", StatPoints=" + statPoints +
                ", MaxMana=" + maxMana +
                ", CurrentMana=" + currentMana +
                ", ManaRegen=" + manaRegen +
                '}';
    }
}

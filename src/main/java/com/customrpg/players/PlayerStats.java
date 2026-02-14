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
 */
public class PlayerStats {

    private int strength;    // 物理攻擊
    private int magic;       // 魔法攻擊
    private int agility;     // 敏捷 (影響暴擊率 & 弓箭傷害)
    private int vitality;    // 生命力 (影響最大血量)
    private int defense;     // 防禦 (減免傷害)

    private int level;
    private long exp;
    private int statPoints;

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

        this.level = 1;
        this.exp = 0;
        this.statPoints = 0;

        this.maxMana = 100.0;
        this.currentMana = 100.0;
        this.manaRegen = 1.0; // 預設每秒回復 1 點
    }

    /**
     * 完整建構子
     */
    public PlayerStats(int strength, int magic, int agility, int vitality, int defense, int level, long exp, int statPoints) {
        this.strength = strength;
        this.magic = magic;
        this.agility = agility;
        this.vitality = vitality;
        this.defense = defense;

        this.level = level;
        this.exp = exp;
        this.statPoints = statPoints;

        this.maxMana = 100.0;
        this.currentMana = 100.0;
        this.manaRegen = 1.0;
    }

    /**
     * 完整建構子（含 Mana）
     */
    public PlayerStats(int strength, int magic, int agility, int vitality, int defense,
                      int level, long exp, int statPoints,
                      double maxMana, double currentMana, double manaRegen) {
        this.strength = strength;
        this.magic = magic;
        this.agility = agility;
        this.vitality = vitality;
        this.defense = defense;

        this.level = level;
        this.exp = exp;
        this.statPoints = statPoints;

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

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
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
        data.put("strength", strength);
        data.put("magic", magic);
        data.put("agility", agility);
        data.put("vitality", vitality);
        data.put("defense", defense);

        data.put("level", level);
        data.put("exp", exp);
        data.put("statPoints", statPoints);

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

        int lvl = data.containsKey("level") ? getInt(data, "level") : 1;
        long xp = data.containsKey("exp") ? ((Number) data.get("exp")).longValue() : 0;
        int pts = data.containsKey("statPoints") ? getInt(data, "statPoints") : 0;

        double maxMana = data.containsKey("maxMana") ? getDouble(data, "maxMana") : 100.0;
        double currentMana = data.containsKey("currentMana") ? getDouble(data, "currentMana") : maxMana;
        double manaRegen = data.containsKey("manaRegen") ? getDouble(data, "manaRegen") : 1.0;

        return new PlayerStats(str, mag, agi, vit, def, lvl, xp, pts, maxMana, currentMana, manaRegen);
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
                ", Level=" + level +
                ", Exp=" + exp +
                ", StatPoints=" + statPoints +
                ", MaxMana=" + maxMana +
                ", CurrentMana=" + currentMana +
                ", ManaRegen=" + manaRegen +
                '}';
    }
}

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

    /**
     * 預設建構子，初始屬性皆為 0
     */
    public PlayerStats() {
        this.strength = 0;
        this.magic = 0;
        this.agility = 0;
        this.vitality = 0;
        this.defense = 0;
    }

    /**
     * 完整建構子
     */
    public PlayerStats(int strength, int magic, int agility, int vitality, int defense) {
        this.strength = strength;
        this.magic = magic;
        this.agility = agility;
        this.vitality = vitality;
        this.defense = defense;
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
        return new PlayerStats(str, mag, agi, vit, def);
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    @Override
    public String toString() {
        return "PlayerStats{" +
                "Strength=" + strength +
                ", Magic=" + magic +
                ", Agility=" + agility +
                ", Vitality=" + vitality +
                ", Defense=" + defense +
                '}';
    }
}


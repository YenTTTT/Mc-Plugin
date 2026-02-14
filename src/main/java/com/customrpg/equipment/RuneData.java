package com.customrpg.equipment;

import java.util.HashMap;
import java.util.Map;

/**
 * 符文數據類
 * 儲存符文的屬性和效果
 */
public class RuneData {

    private String id;
    private String name;
    private String description;
    private RuneType type;
    private RuneRarity rarity;
    private int level;

    // 屬性加成
    private Map<EquipmentAttribute, Double> attributes;

    // 特殊效果
    private String specialEffect;
    private Map<String, Object> effectData;

    public RuneData(String id, String name, RuneType type, RuneRarity rarity) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.rarity = rarity;
        this.level = 1;
        this.attributes = new HashMap<>();
        this.effectData = new HashMap<>();
    }

    /**
     * 符文類型枚舉
     */
    public enum RuneType {
        ATTACK("攻擊型", "§c"),
        DEFENSE("防禦型", "§7"),
        MAGIC("魔法型", "§5"),
        UTILITY("輔助型", "§a"),
        SPECIAL("特殊型", "§6");

        private final String displayName;
        private final String color;

        RuneType(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }

    /**
     * 符文稀有度枚舉
     */
    public enum RuneRarity {
        BASIC("基礎", "§f", 1.0),
        ADVANCED("進階", "§a", 1.5),
        EXPERT("專家", "§9", 2.0),
        MASTER("大師", "§5", 2.5),
        LEGENDARY("傳說", "§6", 3.0);

        private final String displayName;
        private final String color;
        private final double multiplier;

        RuneRarity(String displayName, String color, double multiplier) {
            this.displayName = displayName;
            this.color = color;
            this.multiplier = multiplier;
        }

        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
        public double getMultiplier() { return multiplier; }
    }

    /**
     * 獲取屬性值
     */
    public double getAttribute(EquipmentAttribute attribute) {
        double baseValue = attributes.getOrDefault(attribute, 0.0);
        return baseValue * rarity.getMultiplier() * level;
    }

    /**
     * 設置基礎屬性
     */
    public void setBaseAttribute(EquipmentAttribute attribute, double value) {
        attributes.put(attribute, value);
    }

    /**
     * 獲取顯示名稱
     */
    public String getDisplayName() {
        return rarity.getColor() + name + " §7[Lv." + level + "]";
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public RuneType getType() { return type; }
    public void setType(RuneType type) { this.type = type; }

    public RuneRarity getRarity() { return rarity; }
    public void setRarity(RuneRarity rarity) { this.rarity = rarity; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public Map<EquipmentAttribute, Double> getAttributes() { return attributes; }
    public void setAttributes(Map<EquipmentAttribute, Double> attributes) { this.attributes = attributes; }

    public String getSpecialEffect() { return specialEffect; }
    public void setSpecialEffect(String specialEffect) { this.specialEffect = specialEffect; }

    public Map<String, Object> getEffectData() { return effectData; }
    public void setEffectData(Map<String, Object> effectData) { this.effectData = effectData; }
}

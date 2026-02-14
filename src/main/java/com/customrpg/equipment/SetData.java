package com.customrpg.equipment;

import java.util.*;

/**
 * 套裝數據類
 * 儲存套裝的所有信息和效果
 */
public class SetData {

    private String id;
    private String name;
    private String description;
    private List<String> equipmentIds; // 套裝包含的裝備ID列表

    // 套裝效果 - key為裝備件數，value為效果數據
    private Map<Integer, SetBonus> setBonuses;

    public SetData(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.equipmentIds = new ArrayList<>();
        this.setBonuses = new HashMap<>();
    }

    /**
     * 套裝加成數據類
     */
    public static class SetBonus {
        private Map<EquipmentAttribute, Double> attributeBonuses;
        private List<String> specialEffects;
        private String description;

        public SetBonus(String description) {
            this.description = description;
            this.attributeBonuses = new HashMap<>();
            this.specialEffects = new ArrayList<>();
        }

        public void addAttributeBonus(EquipmentAttribute attribute, double value) {
            attributeBonuses.put(attribute, value);
        }

        public void addSpecialEffect(String effect) {
            specialEffects.add(effect);
        }

        public Map<EquipmentAttribute, Double> getAttributeBonuses() {
            return attributeBonuses;
        }

        public List<String> getSpecialEffects() {
            return specialEffects;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /**
     * 添加套裝效果
     */
    public void addSetBonus(int pieceCount, SetBonus bonus) {
        setBonuses.put(pieceCount, bonus);
    }

    /**
     * 獲取套裝效果
     */
    public SetBonus getSetBonus(int pieceCount) {
        return setBonuses.get(pieceCount);
    }

    /**
     * 獲取所有套裝效果的件數要求（排序）
     */
    public List<Integer> getSortedBonusThresholds() {
        List<Integer> thresholds = new ArrayList<>(setBonuses.keySet());
        Collections.sort(thresholds);
        return thresholds;
    }

    /**
     * 檢查裝備是否屬於此套裝
     */
    public boolean containsEquipment(String equipmentId) {
        return equipmentIds.contains(equipmentId);
    }

    /**
     * 添加套裝裝備
     */
    public void addEquipment(String equipmentId) {
        if (!equipmentIds.contains(equipmentId)) {
            equipmentIds.add(equipmentId);
        }
    }

    /**
     * 移除套裝裝備
     */
    public void removeEquipment(String equipmentId) {
        equipmentIds.remove(equipmentId);
    }

    /**
     * 獲取套裝總件數
     */
    public int getTotalPieces() {
        return equipmentIds.size();
    }

    /**
     * 創建套裝描述文本
     */
    public List<String> createDescription(int currentPieces) {
        List<String> description = new ArrayList<>();

        description.add("§6套裝: §e" + name);
        description.add("§7" + this.description);
        description.add("");
        description.add("§8當前: §7" + currentPieces + "§8/§7" + getTotalPieces() + " 件");
        description.add("");

        // 顯示所有套裝效果
        List<Integer> thresholds = getSortedBonusThresholds();
        for (int threshold : thresholds) {
            SetBonus bonus = setBonuses.get(threshold);
            String prefix = currentPieces >= threshold ? "§a" : "§8";

            description.add(prefix + "(" + threshold + "件套) " + bonus.getDescription());

            // 顯示屬性加成
            if (!bonus.getAttributeBonuses().isEmpty()) {
                for (Map.Entry<EquipmentAttribute, Double> entry : bonus.getAttributeBonuses().entrySet()) {
                    EquipmentAttribute attr = entry.getKey();
                    double value = entry.getValue();
                    String valueStr = attr.formatValue(value);
                    description.add(prefix + "  +" + valueStr + " " + attr.getDisplayName());
                }
            }

            // 顯示特殊效果
            if (!bonus.getSpecialEffects().isEmpty()) {
                for (String effect : bonus.getSpecialEffects()) {
                    description.add(prefix + "  " + effect);
                }
            }

            description.add("");
        }

        return description;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getEquipmentIds() { return equipmentIds; }
    public void setEquipmentIds(List<String> equipmentIds) { this.equipmentIds = equipmentIds; }

    public Map<Integer, SetBonus> getSetBonuses() { return setBonuses; }
    public void setSetBonuses(Map<Integer, SetBonus> setBonuses) { this.setBonuses = setBonuses; }
}

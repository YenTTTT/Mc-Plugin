package com.customrpg.equipment;

import org.bukkit.Material;
import java.util.Map;

/**
 * 裝甲數據類
 * 繼承自 EquipmentData，添加裝甲特有屬性
 */
public class ArmorData extends EquipmentData {

    private final ArmorType armorType;
    private PlayerClass requiredClass;
    private double physicalDefense;
    private double magicalDefense;
    private int durability;
    private int maxDurability;

    public ArmorData(String id, String name, ArmorType armorType, EquipmentSlot slot, Material material) {
        super(id, name, slot, EquipmentRarity.COMMON, material);
        this.armorType = armorType;
        this.requiredClass = PlayerClass.NONE;
        this.physicalDefense = 0.0;
        this.magicalDefense = 0.0;
        this.durability = 100;
        this.maxDurability = 100;
    }

    public void setRequiredClass(PlayerClass requiredClass) {
        this.requiredClass = requiredClass;
    }

    public void setPhysicalDefense(double physicalDefense) {
        this.physicalDefense = physicalDefense;
    }

    public void setMagicalDefense(double magicalDefense) {
        this.magicalDefense = magicalDefense;
    }

    public void setAttribute(EquipmentAttribute attribute, double value) {
        setBaseAttribute(attribute, value);
    }

    public ArmorType getArmorType() {
        return armorType;
    }

    public PlayerClass getRequiredClass() {
        return requiredClass;
    }

    public double getPhysicalDefense() {
        return physicalDefense;
    }

    public double getMagicalDefense() {
        return magicalDefense;
    }

    public int getDurability() {
        return durability;
    }

    public void setDurability(int durability) {
        this.durability = Math.max(0, Math.min(durability, maxDurability));
    }

    public int getMaxDurability() {
        return maxDurability;
    }

    public void setMaxDurability(int maxDurability) {
        this.maxDurability = maxDurability;
        if (this.durability > maxDurability) {
            this.durability = maxDurability;
        }
    }

    public ArmorData copy() {
        ArmorData copy = new ArmorData(getId(), getName(), armorType, getSlot(), getMaterial());
        copy.setRequiredLevel(getRequiredLevel());
        copy.setRequiredClass(this.requiredClass);
        copy.setPhysicalDefense(this.physicalDefense);
        copy.setMagicalDefense(this.magicalDefense);
        copy.setRarity(getRarity());
        copy.setDescription(getDescription());
        copy.setMaxDurability(this.maxDurability);
        copy.setDurability(this.durability);

        for (Map.Entry<EquipmentAttribute, Double> entry : getAllAttributes().entrySet()) {
            copy.setBaseAttribute(entry.getKey(), entry.getValue());
        }

        return copy;
    }
}





package com.customrpg.equipment;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import org.bukkit.entity.Player;

/**
 * 裝備驗證器
 *
 * 負責驗證玩家是否可以裝備指定裝備
 * 檢查項目：
 * - 等級需求
 * - 職業需求
 * - 槽位狀態
 * - 雙手武器限制
 * - 耐久度狀態
 */
public class EquipmentValidator {

    private final CustomRPG plugin;
    private final EquipmentManager equipmentManager;

    public EquipmentValidator(CustomRPG plugin, EquipmentManager equipmentManager) {
        this.plugin = plugin;
        this.equipmentManager = equipmentManager;
    }

    /**
     * 驗證裝甲是否可穿戴
     */
    public ValidationResult validateArmor(Player player, ArmorData armor) {
        if (armor == null) {
            return ValidationResult.INVALID_EQUIPMENT;
        }

        // 1. 檢查耐久度
        if (armor.getDurability() <= 0) {
            return ValidationResult.DURABILITY_ZERO;
        }

        // 2. 檢查等級
        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);
        if (stats.getLevel() < armor.getRequiredLevel()) {
            return ValidationResult.LEVEL_TOO_LOW;
        }

        // 3. 檢查職業
        PlayerClass playerClass = getPlayerClass(player);
        if (armor.getRequiredClass() != PlayerClass.NONE) {
            if (playerClass != armor.getRequiredClass()) {
                return ValidationResult.WRONG_CLASS;
            }
        }

        // 4. 檢查裝甲類型是否匹配職業
        if (playerClass != PlayerClass.NONE && !playerClass.canWear(armor.getArmorType())) {
            return ValidationResult.WRONG_CLASS;
        }

        return ValidationResult.SUCCESS;
    }

    /**
     * 驗證武器是否可裝備
     */
    public ValidationResult validateWeapon(Player player, EquipmentData weapon) {
        if (weapon == null) {
            return ValidationResult.INVALID_EQUIPMENT;
        }

        // 1. 檢查等級
        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);
        if (stats.getLevel() < weapon.getRequiredLevel()) {
            return ValidationResult.LEVEL_TOO_LOW;
        }

        // 2. 檢查職業（如果武器有職業需求）
        // 目前武器沒有職業限制，預留擴展

        // 3. 檢查雙手武器
        if (weapon.getSlot() == EquipmentSlot.MAIN_HAND) {
            // 檢查是否為雙手武器（通過材質判斷）
            // 預留：未來可以添加 isTwoHanded() 方法到 EquipmentData
        }

        return ValidationResult.SUCCESS;
    }

    /**
     * 驗證裝備是否可穿戴（通用方法）
     */
    public ValidationResult validate(Player player, EquipmentData equipment) {
        if (equipment == null) {
            return ValidationResult.INVALID_EQUIPMENT;
        }

        // 檢查等級
        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);
        if (stats.getLevel() < equipment.getRequiredLevel()) {
            return ValidationResult.LEVEL_TOO_LOW;
        }

        return ValidationResult.SUCCESS;
    }

    /**
     * 獲取玩家職業
     */
    private PlayerClass getPlayerClass(Player player) {
        // 從玩家數據中獲取職業
        // 目前返回 NONE，未來可以從 PlayerStats 或專門的職業管理器獲取
        return PlayerClass.NONE;
    }

    /**
     * 檢查等級需求
     */
    public boolean checkLevel(Player player, int requiredLevel) {
        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);
        return stats.getLevel() >= requiredLevel;
    }

    /**
     * 檢查職業需求
     */
    public boolean checkClass(Player player, PlayerClass requiredClass) {
        if (requiredClass == PlayerClass.NONE) {
            return true;
        }
        PlayerClass playerClass = getPlayerClass(player);
        return playerClass == requiredClass;
    }

    /**
     * 檢查槽位是否可用
     */
    public boolean checkSlot(Player player, EquipmentSlot slot) {
        return equipmentManager.getPlayerEquipment(player.getUniqueId()).get(slot) == null;
    }

    /**
     * 檢查是否有副手物品
     */
    public boolean hasOffHand(Player player) {
        return equipmentManager.getPlayerEquipment(player.getUniqueId()).get(EquipmentSlot.OFF_HAND) != null;
    }
}


package com.customrpg.equipment;

/**
 * 裝備槽位枚舉
 * 定義角色可裝備的所有部位
 */
public enum EquipmentSlot {
    // 主要裝備槽位
    HELMET("頭盔", "HELMET", 0),
    CHESTPLATE("胸甲", "CHESTPLATE", 1),
    LEGGINGS("腿甲", "LEGGINGS", 2),
    BOOTS("靴子", "BOOTS", 3),

    // 武器槽位
    MAIN_HAND("主手武器", "MAIN_HAND", 4),
    OFF_HAND("副手", "OFF_HAND", 5),

    // 額外裝備槽位
    SHOULDER("肩甲", "SHOULDER", 6),
    CLOAK("披風", "CLOAK", 7),

    // 飾品槽位
    RING_1("戒指1", "RING_1", 8),
    RING_2("戒指2", "RING_2", 9),
    NECKLACE("項鍊", "NECKLACE", 10),
    BRACELET("手環", "BRACELET", 11),

    // 特殊槽位
    CHARM("護符", "CHARM", 12),
    BELT("腰帶", "BELT", 13);

    private final String displayName;
    private final String configKey;
    private final int slotIndex;

    EquipmentSlot(String displayName, String configKey, int slotIndex) {
        this.displayName = displayName;
        this.configKey = configKey;
        this.slotIndex = slotIndex;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getConfigKey() {
        return configKey;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    /**
     * 檢查是否為主要護甲部位
     */
    public boolean isArmor() {
        return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
    }

    /**
     * 檢查是否為武器槽位
     */
    public boolean isWeapon() {
        return this == MAIN_HAND || this == OFF_HAND;
    }

    /**
     * 檢查是否為飾品槽位
     */
    public boolean isAccessory() {
        return this == RING_1 || this == RING_2 || this == NECKLACE || this == BRACELET;
    }

    /**
     * 檢查是否為額外裝備
     */
    public boolean isExtra() {
        return this == SHOULDER || this == CLOAK || this == CHARM || this == BELT;
    }

    /**
     * 根據配置鍵獲取槽位
     */
    public static EquipmentSlot fromConfigKey(String key) {
        for (EquipmentSlot slot : values()) {
            if (slot.configKey.equalsIgnoreCase(key)) {
                return slot;
            }
        }
        return null;
    }
}

package com.customrpg.equipment;

/**
 * 裝備驗證結果枚舉
 */
public enum ValidationResult {
    SUCCESS("§a可以裝備"),
    LEVEL_TOO_LOW("§c等級不足"),
    WRONG_CLASS("§c職業不符"),
    SLOT_OCCUPIED("§c槽位已佔用"),
    NEED_REMOVE_OFFHAND("§c需要卸除副手"),
    DURABILITY_ZERO("§c耐久度為0"),
    INVALID_EQUIPMENT("§c無效的裝備");

    private final String message;

    ValidationResult(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}


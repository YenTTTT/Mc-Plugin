package com.customrpg.talents;

/**
 * TalentType - 天賦類型枚舉
 *
 * 定義天賦的不同類型：
 * - PASSIVE: 被動效果（屬性加成、觸發效果）
 * - ACTIVE: 主動技能（消耗資源釋放）
 * - ATTRIBUTE: 屬性提升（直接增加基礎屬性）
 */
public enum TalentType {
    PASSIVE,           // 被動效果（自動觸發）
    ACTIVE,            // 主動技能（需操作釋放）
    ATTRIBUTE,         // 屬性提升（直接增加屬性值）
    WEAPON_PASSIVE     // 武器傷害加成（特定武器傷害提升）
}

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
    PASSIVE,    // 被動天賦 - 提供持續效果
    ACTIVE,     // 主動天賦 - 可釋放的技能
    ATTRIBUTE   // 屬性天賦 - 直接增加屬性值
}

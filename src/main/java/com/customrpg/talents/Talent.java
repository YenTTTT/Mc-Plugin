package com.customrpg.talents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talent - 天賦技能類 (重構版)
 *
 * 支援新規格：
 * - GUI Slot 指定
 * - 主動/被動觸發機制
 * - 冷卻與消耗
 * - 多級別效果定義
 */
public class Talent {
    private final String id;                    // 天賦ID
    private final String name;                  // 天賦名稱
    private final String description;           // 天賦基礎描述
    private final TalentType type;              // 天賦類型
    private final TalentBranch branch;          // 所屬分支
    private final int maxLevel;                 // 最大等級
    private final int pointsPerLevel;           // 每級消耗天賦點
    private final List<Prerequisite> prerequisites; // 前置需求列表
    private final PrerequisiteMode prerequisiteMode; // 前置條件模式 (ALL 或 ANY)
    private final int guiSlot;                  // GUI 中的位置
    private final String icon;                  // 預設圖示材質

    // 技能機制相關
    private final String mechanism;             // 機制描述 (如: 被動, 持武器蹲下右鍵)
    private final double cooldown;              // 冷卻 (秒)
    private final double manaCost;              // 消耗 (Mana)
    private final String triggerType;           // 觸發類型 (RIGHT_CLICK_SNEAK, ON_HIT, etc.)

    // 各等級的效果數據
    private final Map<Integer, TalentLevelData> levelData;

    public Talent(String id, String name, String description, TalentType type,
                  TalentBranch branch, int maxLevel, int pointsPerLevel,
                  List<Prerequisite> prerequisites, PrerequisiteMode prerequisiteMode, int guiSlot, String icon,
                  String mechanism, double cooldown, double manaCost, String triggerType) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.branch = branch;
        this.maxLevel = maxLevel;
        this.pointsPerLevel = pointsPerLevel;
        this.prerequisites = prerequisites != null ? prerequisites : new ArrayList<>();
        this.prerequisiteMode = prerequisiteMode != null ? prerequisiteMode : PrerequisiteMode.ALL;
        this.guiSlot = guiSlot;
        this.icon = icon;
        this.mechanism = mechanism;
        this.cooldown = cooldown;
        this.manaCost = manaCost;
        this.triggerType = triggerType;
        this.levelData = new HashMap<>();
    }

    public void addLevelData(int level, TalentLevelData data) {
        levelData.put(level, data);
    }

    // ===== Getters =====
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public TalentType getType() { return type; }
    public TalentBranch getBranch() { return branch; }
    public int getMaxLevel() { return maxLevel; }
    public int getPointsPerLevel() { return pointsPerLevel; }
    public List<Prerequisite> getPrerequisites() { return prerequisites; }
    public PrerequisiteMode getPrerequisiteMode() { return prerequisiteMode; }
    public int getGuiSlot() { return guiSlot; }
    public String getIcon() { return icon; }
    public String getMechanism() { return mechanism; }
    public double getCooldown() { return cooldown; }
    public double getManaCost() { return manaCost; }
    public String getTriggerType() { return triggerType; }

    public TalentLevelData getLevelData(int level) {
        return levelData.get(level);
    }

    public boolean canLearn(Map<String, Integer> playerTalents, int targetLevel) {
        if (targetLevel > maxLevel) return false;

        // 如果沒有前置條件，直接返回true
        if (prerequisites.isEmpty()) return true;

        // ANY 模式：至少滿足一個前置條件
        if (prerequisiteMode == PrerequisiteMode.ANY) {
            for (Prerequisite pre : prerequisites) {
                if (playerTalents.getOrDefault(pre.talentId, 0) >= pre.requiredLevel) {
                    return true;
                }
            }
            return false;
        }

        // ALL 模式：需要滿足所有前置條件
        for (Prerequisite pre : prerequisites) {
            if (playerTalents.getOrDefault(pre.talentId, 0) < pre.requiredLevel) {
                return false;
            }
        }
        return true;
    }

    /**
     * 前置條件模式枚舉
     */
    public enum PrerequisiteMode {
        ALL,  // 需要滿足所有前置條件 (AND)
        ANY   // 至少滿足一個前置條件 (OR)
    }

    /**
     * 前置需求類
     */
    public static class Prerequisite {
        public final String talentId;
        public final int requiredLevel;

        public Prerequisite(String talentId, int requiredLevel) {
            this.talentId = talentId;
            this.requiredLevel = requiredLevel;
        }
    }

    /**
     * 單個等級的數據
     */
    public static class TalentLevelData {
        public final Map<String, Double> effects = new HashMap<>();
        public final Map<String, Double> scaling = new HashMap<>(); // 屬性係數

        public void addEffect(String key, double value) {
            effects.put(key, value);
        }

        public void addScaling(String stat, double multiplier) {
            scaling.put(stat, multiplier);
        }
    }
}

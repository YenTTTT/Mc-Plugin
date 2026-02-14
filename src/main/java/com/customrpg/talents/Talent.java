package com.customrpg.talents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talent - 天賦技能類
 *
 * 代表一個具體的天賦技能，包含：
 * - 基本資訊（ID、名稱、描述）
 * - 學習條件（前置天賦、最大等級、每級消耗點數）
 * - 效果數據（屬性加成、技能效果）
 */
public class Talent {
    private final String id;                    // 天賦ID
    private final String name;                  // 天賦名稱
    private final String description;           // 天賦描述
    private final TalentType type;              // 天賦類型
    private final TalentBranch branch;          // 所屬分支
    private final int maxLevel;                 // 最大等級
    private final int pointsPerLevel;           // 每級消耗天賦點
    private final List<String> prerequisites;   // 前置天賦ID列表
    private final Map<String, Double> baseEffects;     // 基礎效果值
    private final Map<String, Double> levelScaling;    // 等級成長係數
    private final int tier;                     // 天賦層級（1-5）
    private final String icon;                  // GUI圖示材質

    public Talent(String id, String name, String description, TalentType type,
                  TalentBranch branch, int maxLevel, int pointsPerLevel,
                  List<String> prerequisites, Map<String, Double> baseEffects,
                  Map<String, Double> levelScaling, int tier, String icon) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.branch = branch;
        this.maxLevel = maxLevel;
        this.pointsPerLevel = pointsPerLevel;
        this.prerequisites = prerequisites != null ? prerequisites : List.of();
        this.baseEffects = baseEffects != null ? baseEffects : new HashMap<>();
        this.levelScaling = levelScaling != null ? levelScaling : new HashMap<>();
        this.tier = tier;
        this.icon = icon;
    }

    // ===== Getters =====
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public TalentType getType() { return type; }
    public TalentBranch getBranch() { return branch; }
    public int getMaxLevel() { return maxLevel; }
    public int getPointsPerLevel() { return pointsPerLevel; }
    public List<String> getPrerequisites() { return prerequisites; }
    public Map<String, Double> getBaseEffects() { return baseEffects; }
    public Map<String, Double> getLevelScaling() { return levelScaling; }
    public int getTier() { return tier; }
    public String getIcon() { return icon; }

    /**
     * 計算指定等級的效果值
     * @param effectName 效果名稱
     * @param level 天賦等級
     * @return 計算後的效果值
     */
    public double getEffectValue(String effectName, int level) {
        double base = baseEffects.getOrDefault(effectName, 0.0);
        double scaling = levelScaling.getOrDefault(effectName, 0.0);
        return base + (scaling * level);
    }

    /**
     * 獲取天賦總消耗點數
     * @param level 天賦等級
     * @return 總消耗點數
     */
    public int getTotalPointsCost(int level) {
        return pointsPerLevel * level;
    }

    /**
     * 檢查是否可以學習此天賦
     * @param playerTalents 玩家天賦數據
     * @param targetLevel 目標等級
     * @return 是否可以學習
     */
    public boolean canLearn(Map<String, Integer> playerTalents, int targetLevel) {
        // 檢查等級限制
        if (targetLevel > maxLevel) return false;

        // 檢查前置條件
        for (String prerequisiteId : prerequisites) {
            if (!playerTalents.containsKey(prerequisiteId) || playerTalents.get(prerequisiteId) == 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * 生成天賦描述文本（含等級效果）
     * @param level 當前等級
     * @return 描述文本
     */
    public String getFormattedDescription(int level) {
        StringBuilder sb = new StringBuilder();
        sb.append(description);

        if (level > 0) {
            sb.append("\n\n§6當前效果 (等級 ").append(level).append("):");
            for (Map.Entry<String, Double> entry : baseEffects.entrySet()) {
                String effectName = entry.getKey();
                double value = getEffectValue(effectName, level);
                sb.append("\n§e- ").append(effectName).append(": ").append(String.format("%.1f", value));
            }
        }

        if (level < maxLevel) {
            sb.append("\n\n§a下一級效果:");
            for (Map.Entry<String, Double> entry : baseEffects.entrySet()) {
                String effectName = entry.getKey();
                double value = getEffectValue(effectName, level + 1);
                sb.append("\n§e- ").append(effectName).append(": ").append(String.format("%.1f", value));
            }
        }

        return sb.toString();
    }
}

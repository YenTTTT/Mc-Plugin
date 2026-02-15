package com.customrpg.talents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TalentTree - 天賦樹管理類
 *
 * 管理一個分支的所有天賦：
 * - 天賦註冊與查找
 * - 層級與前置關係驗證
 * - GUI 佈局計算
 */
public class TalentTree {
    private final TalentBranch branch;
    private final String name;
    private final String description;
    private final Map<String, Talent> talents;
    private final Map<Integer, List<Talent>> tierTalents; // 層級 -> 天賦列表
    private final int maxTier;

    public TalentTree(TalentBranch branch, String name, String description) {
        this.branch = branch;
        this.name = name;
        this.description = description;
        this.talents = new HashMap<>();
        this.tierTalents = new HashMap<>();
        this.maxTier = 5; // 最大層級

        // 初始化層級映射
        for (int i = 1; i <= maxTier; i++) {
            tierTalents.put(i, new ArrayList<>());
        }
    }

    /**
     * 註冊天賦到樹中
     * @param talent 天賦
     */
    public void registerTalent(Talent talent) {
        if (talent.getBranch() != this.branch) {
            throw new IllegalArgumentException("天賦分支不匹配: " + talent.getId());
        }

        talents.put(talent.getId(), talent);
    }

    /**
     * 獲取天賦
     * @param talentId 天賦ID
     * @return 天賦物件，若不存在則返回null
     */
    public Talent getTalent(String talentId) {
        return talents.get(talentId);
    }

    /**
     * 獲取所有天賦
     * @return 天賦映射表
     */
    public Map<String, Talent> getAllTalents() {
        return talents;
    }

    /**
     * 檢查天賦是否可學習
     * @param talentId 天賦ID
     * @param playerTalents 玩家天賦數據
     * @return 是否可學習
     */
    public boolean canLearnTalent(String talentId, Map<String, Integer> playerTalents) {
        Talent talent = getTalent(talentId);
        if (talent == null) return false;

        int currentLevel = playerTalents.getOrDefault(talentId, 0);
        return talent.canLearn(playerTalents, currentLevel + 1);
    }

    /**
     * 獲取天賦的前置需求狀態
     * @param talentId 天賦ID
     * @param playerTalents 玩家天賦數據
     * @return 需求狀態描述
     */
    public String getPrerequisiteStatus(String talentId, Map<String, Integer> playerTalents) {
        Talent talent = getTalent(talentId);
        if (talent == null) return "天賦不存在";

        List<Talent.Prerequisite> prerequisites = talent.getPrerequisites();
        if (prerequisites.isEmpty()) {
            return "§a可以學習";
        }

        Talent.PrerequisiteMode mode = talent.getPrerequisiteMode();

        // ANY 模式：至少需要滿足一個
        if (mode == Talent.PrerequisiteMode.ANY) {
            boolean hasAnyMet = false;
            List<String> allPrerequisites = new ArrayList<>();

            for (Talent.Prerequisite pre : prerequisites) {
                Talent prereqTalent = getTalent(pre.talentId);
                String prereqName = prereqTalent != null ? prereqTalent.getName() : pre.talentId;
                allPrerequisites.add(prereqName + "(Lv" + pre.requiredLevel + ")");

                if (playerTalents.getOrDefault(pre.talentId, 0) >= pre.requiredLevel) {
                    hasAnyMet = true;
                }
            }

            if (hasAnyMet) {
                return "§a可以學習";
            } else {
                return "§c需要前置天賦 (至少一個): " + String.join(" §7或§c ", allPrerequisites);
            }
        }

        // ALL 模式：需要滿足所有
        List<String> missingPrerequisites = new ArrayList<>();
        for (Talent.Prerequisite pre : prerequisites) {
            if (playerTalents.getOrDefault(pre.talentId, 0) < pre.requiredLevel) {
                Talent prereqTalent = getTalent(pre.talentId);
                String prereqName = prereqTalent != null ? prereqTalent.getName() : pre.talentId;
                missingPrerequisites.add(prereqName + "(Lv" + pre.requiredLevel + ")");
            }
        }

        if (missingPrerequisites.isEmpty()) {
            return "§a可以學習";
        } else {
            return "§c需要前置天賦: " + String.join(", ", missingPrerequisites);
        }
    }

    /**
     * 獲取天賦在GUI中的位置
     * @param talentId 天賦ID
     * @return GUI slot位置 (-1 表示無效)
     */
    public int calculateGUISlot(String talentId) {
        Talent talent = getTalent(talentId);
        return talent != null ? talent.getGuiSlot() : -1;
    }

    /**
     * 獲取分支總投入點數需求（用於專精限制）
     * @param playerTalents 玩家天賦數據
     * @return 該分支的總投入點數
     */
    public int calculateBranchPoints(Map<String, Integer> playerTalents) {
        int totalPoints = 0;
        for (Map.Entry<String, Talent> entry : talents.entrySet()) {
            String talentId = entry.getKey();
            Talent talent = entry.getValue();
            int level = playerTalents.getOrDefault(talentId, 0);
            totalPoints += (talent.getPointsPerLevel() * level);
        }
        return totalPoints;
    }

    /**
     * 獲取分支統計資訊
     * @param playerTalents 玩家天賦數據
     * @return 統計資訊字符串
     */
    public String getBranchStats(Map<String, Integer> playerTalents) {
        int learnedTalents = (int) talents.keySet().stream()
            .filter(id -> playerTalents.getOrDefault(id, 0) > 0)
            .count();
        int totalTalents = talents.size();
        int pointsSpent = calculateBranchPoints(playerTalents);

        return String.format("§6已學習: %d/%d 天賦 | 投入點數: %d",
            learnedTalents, totalTalents, pointsSpent);
    }

    // ===== Getters =====
    public TalentBranch getBranch() { return branch; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getMaxTier() { return maxTier; }

    /**
     * 獲取天賦數量
     * @return 天賦總數
     */
    public int getTalentCount() {
        return talents.size();
    }
}

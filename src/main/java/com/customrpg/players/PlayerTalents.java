package com.customrpg.players;

import com.customrpg.talents.TalentBranch;
import java.util.HashMap;
import java.util.Map;

/**
 * PlayerTalents - 玩家天賦數據
 *
 * 儲存玩家的天賦投點與專精數據：
 * - 可用天賦點數
 * - 各天賦的等級
 * - 分支專精程度
 */
public class PlayerTalents {
    private int availablePoints;                    // 可用天賦點數
    private final Map<String, Integer> talentLevels;     // 天賦ID -> 等級
    private final Map<TalentBranch, Integer> branchPoints; // 分支 -> 投入點數
    private int totalPointsSpent;                   // 總消耗點數

    public PlayerTalents() {
        this.availablePoints = 0;
        this.talentLevels = new HashMap<>();
        this.branchPoints = new HashMap<>();
        this.totalPointsSpent = 0;

        // 初始化分支點數
        for (TalentBranch branch : TalentBranch.values()) {
            branchPoints.put(branch, 0);
        }
    }

    // ===== Getters & Setters =====

    public int getAvailablePoints() {
        return availablePoints;
    }

    public void setAvailablePoints(int availablePoints) {
        this.availablePoints = Math.max(0, availablePoints);
    }

    public void addPoints(int points) {
        this.availablePoints += points;
    }

    public boolean spendPoints(int points) {
        if (availablePoints >= points) {
            availablePoints -= points;
            totalPointsSpent += points;
            return true;
        }
        return false;
    }

    public Map<String, Integer> getTalentLevels() {
        return talentLevels;
    }

    public int getTalentLevel(String talentId) {
        return talentLevels.getOrDefault(talentId, 0);
    }

    public void setTalentLevel(String talentId, int level) {
        if (level <= 0) {
            talentLevels.remove(talentId);
        } else {
            talentLevels.put(talentId, level);
        }
    }

    public Map<TalentBranch, Integer> getBranchPoints() {
        return branchPoints;
    }

    public int getBranchPoints(TalentBranch branch) {
        return branchPoints.getOrDefault(branch, 0);
    }

    public void addBranchPoints(TalentBranch branch, int points) {
        branchPoints.put(branch, branchPoints.getOrDefault(branch, 0) + points);
    }

    public int getTotalPointsSpent() {
        return totalPointsSpent;
    }

    public void setTotalPointsSpent(int totalPointsSpent) {
        this.totalPointsSpent = totalPointsSpent;
    }

    /**
     * 檢查是否有足夠點數投資天賦
     * @param pointsNeeded 需要的點數
     * @return 是否有足夠點數
     */
    public boolean canAfford(int pointsNeeded) {
        return availablePoints >= pointsNeeded;
    }

    /**
     * 獲取主要專精分支
     * @return 投入最多點數的分支
     */
    public TalentBranch getMainSpecialization() {
        TalentBranch mainBranch = null;
        int maxPoints = 0;

        for (Map.Entry<TalentBranch, Integer> entry : branchPoints.entrySet()) {
            if (entry.getValue() > maxPoints) {
                maxPoints = entry.getValue();
                mainBranch = entry.getKey();
            }
        }

        return mainBranch;
    }

    /**
     * 檢查是否滿足分支專精需求
     * @param branch 分支
     * @param requiredPoints 需要的專精點數
     * @return 是否滿足需求
     */
    public boolean hasSpecialization(TalentBranch branch, int requiredPoints) {
        return getBranchPoints(branch) >= requiredPoints;
    }

    /**
     * 重置所有天賦
     */
    public void resetAllTalents() {
        // 返還所有點數
        availablePoints += totalPointsSpent;

        // 清空天賦等級
        talentLevels.clear();

        // 清空分支點數
        for (TalentBranch branch : TalentBranch.values()) {
            branchPoints.put(branch, 0);
        }

        totalPointsSpent = 0;
    }

    /**
     * 檢查是否學習過指定天賦
     * @param talentId 天賦ID
     * @return 是否學習過
     */
    public boolean hasTalent(String talentId) {
        return getTalentLevel(talentId) > 0;
    }

    /**
     * 升級天賦
     * @param talentId 天賦ID
     * @param branch 分支
     * @param pointsCost 消耗點數
     * @return 升級後的等級
     */
    public int upgradeTalent(String talentId, TalentBranch branch, int pointsCost) {
        if (spendPoints(pointsCost)) {
            int currentLevel = getTalentLevel(talentId);
            int newLevel = currentLevel + 1;
            setTalentLevel(talentId, newLevel);
            addBranchPoints(branch, pointsCost);
            return newLevel;
        }
        return getTalentLevel(talentId);
    }
}

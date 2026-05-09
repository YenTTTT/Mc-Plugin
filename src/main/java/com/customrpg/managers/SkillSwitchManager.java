package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.Talent;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SkillSwitchManager - 技能切換管理器
 *
 * 當玩家持有相同機制物品時，可以右鍵切換不同技能
 */
public class SkillSwitchManager {

    private final CustomRPG plugin;
    private final TalentManager talentManager;

    // 玩家當前選擇的技能索引 Map<UUID, Map<Material, Integer>>
    private final Map<UUID, Map<Material, Integer>> playerSkillSelection = new ConcurrentHashMap<>();

    // 機制物品對應的材質 (一個機制名可對應多個材質)
    private final Map<String, Set<Material>> mechanismMaterials = new HashMap<>();

    public SkillSwitchManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.talentManager = plugin.getTalentManager();
        initMechanismMaterials();
    }

    /**
     * 初始化機制物品材質映射
     */
    private void initMechanismMaterials() {
        addMechanism("金粒", Material.GOLD_NUGGET);
        addMechanism("骨頭", Material.BONE);
        addMechanism("木棒", Material.STICK);
        addMechanism("海靈晶體", Material.PRISMARINE_CRYSTALS);
        addMechanism("海磷碎片", Material.PRISMARINE_SHARD);
        addMechanism("地獄之星", Material.NETHER_STAR);
        addMechanism("鑽石劍", Material.DIAMOND_SWORD);
        addMechanism("紅石粉", Material.REDSTONE);
        addMechanism("蜘蛛眼", Material.SPIDER_EYE);
        addMechanism("凋零玫瑰", Material.WITHER_ROSE);
        addMechanism("烈焰粉", Material.BLAZE_POWDER);
        // 鐮刀 → 所有 HOE 類型
        addMechanism("鐮刀", Material.WOODEN_HOE);
        addMechanism("鐮刀", Material.STONE_HOE);
        addMechanism("鐮刀", Material.IRON_HOE);
        addMechanism("鐮刀", Material.GOLDEN_HOE);
        addMechanism("鐮刀", Material.DIAMOND_HOE);
        addMechanism("鐮刀", Material.NETHERITE_HOE);
        addMechanism("弓", Material.BOW);
        addMechanism("持武器右鍵", Material.WOODEN_SWORD);
        addMechanism("持武器右鍵", Material.STONE_SWORD);
        addMechanism("持武器右鍵", Material.IRON_SWORD);
        addMechanism("持武器右鍵", Material.GOLDEN_SWORD);
        addMechanism("持武器右鍵", Material.DIAMOND_SWORD);
        addMechanism("持武器右鍵", Material.NETHERITE_SWORD);
        addMechanism("持武器右鍵", Material.WOODEN_AXE);
        addMechanism("持武器右鍵", Material.STONE_AXE);
        addMechanism("持武器右鍵", Material.IRON_AXE);
        addMechanism("持武器右鍵", Material.GOLDEN_AXE);
        addMechanism("持武器右鍵", Material.DIAMOND_AXE);
        addMechanism("持武器右鍵", Material.NETHERITE_AXE);
        addMechanism("持武器右鍵", Material.WOODEN_HOE);
        addMechanism("持武器右鍵", Material.STONE_HOE);
        addMechanism("持武器右鍵", Material.IRON_HOE);
        addMechanism("持武器右鍵", Material.GOLDEN_HOE);
        addMechanism("持武器右鍵", Material.DIAMOND_HOE);
        addMechanism("持武器右鍵", Material.NETHERITE_HOE);
        addMechanism("持武器右鍵", Material.STICK);
        addMechanism("持武器右鍵", Material.BLAZE_ROD);
        addMechanism("持武器右鍵", Material.BREEZE_ROD);

        // ── 武器系三大方向 ──
        addMechanism("槍械武器", Material.IRON_HORSE_ARMOR);
        addMechanism("槍械武器", Material.GOLDEN_HORSE_ARMOR);
        addMechanism("槍械武器", Material.DIAMOND_HORSE_ARMOR);

        addMechanism("近戰武器", Material.WOODEN_SWORD);
        addMechanism("近戰武器", Material.STONE_SWORD);
        addMechanism("近戰武器", Material.IRON_SWORD);
        addMechanism("近戰武器", Material.GOLDEN_SWORD);
        addMechanism("近戰武器", Material.DIAMOND_SWORD);
        addMechanism("近戰武器", Material.NETHERITE_SWORD);
        addMechanism("近戰武器", Material.WOODEN_AXE);
        addMechanism("近戰武器", Material.STONE_AXE);
        addMechanism("近戰武器", Material.IRON_AXE);
        addMechanism("近戰武器", Material.GOLDEN_AXE);
        addMechanism("近戰武器", Material.DIAMOND_AXE);
        addMechanism("近戰武器", Material.NETHERITE_AXE);
        addMechanism("近戰武器", Material.WOODEN_HOE);
        addMechanism("近戰武器", Material.STONE_HOE);
        addMechanism("近戰武器", Material.IRON_HOE);
        addMechanism("近戰武器", Material.GOLDEN_HOE);
        addMechanism("近戰武器", Material.DIAMOND_HOE);
        addMechanism("近戰武器", Material.NETHERITE_HOE);

        addMechanism("法杖", Material.STICK);
        addMechanism("法杖", Material.BLAZE_ROD);
        addMechanism("法杖", Material.BREEZE_ROD);
        addMechanism("法杖", Material.ENCHANTED_BOOK);

        // 共通大招：三條武器路線都能使用
        for (Material material : Set.of(
                Material.IRON_HORSE_ARMOR, Material.GOLDEN_HORSE_ARMOR, Material.DIAMOND_HORSE_ARMOR,
                Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD,
                Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
                Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.GOLDEN_AXE,
                Material.DIAMOND_AXE, Material.NETHERITE_AXE,
                Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE, Material.GOLDEN_HOE,
                Material.DIAMOND_HOE, Material.NETHERITE_HOE,
                Material.STICK, Material.BLAZE_ROD, Material.BREEZE_ROD, Material.ENCHANTED_BOOK)) {
            addMechanism("武器系終極", material);
        }
    }

    /**
     * 添加機制名稱對應的材質（支援一對多）
     */
    private void addMechanism(String name, Material material) {
        mechanismMaterials.computeIfAbsent(name, k -> new HashSet<>()).add(material);
    }

    /**
     * 處理技能切換
     * @param player 玩家
     * @param item 手持物品
     * @return 是否成功切換
     */
    public boolean handleSkillSwitch(Player player, ItemStack item) {
        if (item == null) return false;

        Material material = item.getType();

        // 獲取玩家已學習且使用此機制的技能列表
        List<Talent> availableSkills = getAvailableSkillsForMechanism(player, material);

        if (availableSkills.size() <= 1) {
            // 只有一個或沒有技能，不需要切換
            return false;
        }

        // 獲取或初始化玩家的選擇索引
        Map<Material, Integer> selections = playerSkillSelection.computeIfAbsent(
            player.getUniqueId(), k -> new ConcurrentHashMap<>()
        );

        int currentIndex = selections.getOrDefault(material, 0);
        int nextIndex = (currentIndex + 1) % availableSkills.size();
        selections.put(material, nextIndex);

        // 發送切換訊息
        Talent selectedSkill = availableSkills.get(nextIndex);
        player.sendMessage("§e[技能切換] §f當前技能: §a" + selectedSkill.getName());
        player.sendMessage("§7" + selectedSkill.getDescription());
        player.sendMessage("§7冷卻: §e" + selectedSkill.getCooldown() + "秒 §7| 消耗: §b" +
                          (int)selectedSkill.getManaCost() + " MANA");

        // 播放切換音效
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);

        return true;
    }

    /**
     * 獲取玩家當前選擇的技能
     * @param player 玩家
     * @param item 手持物品
     * @return 當前選擇的技能，如果沒有則返回第一個可用技能
     */
    public Talent getCurrentSelectedSkill(Player player, ItemStack item) {
        if (item == null) return null;

        Material material = item.getType();
        List<Talent> availableSkills = getAvailableSkillsForMechanism(player, material);

        if (availableSkills.isEmpty()) {
            return null;
        }

        if (availableSkills.size() == 1) {
            return availableSkills.get(0);
        }

        // 獲取當前選擇的索引
        Map<Material, Integer> selections = playerSkillSelection.get(player.getUniqueId());
        if (selections == null) {
            return availableSkills.get(0);
        }

        int index = selections.getOrDefault(material, 0);
        if (index >= availableSkills.size()) {
            index = 0;
            selections.put(material, index);
        }

        return availableSkills.get(index);
    }

    /**
     * 獲取玩家使用指定機制的所有已選取技能
     * @param player 玩家
     * @param material 機制物品材質
     * @return 技能列表
     */
    private List<Talent> getAvailableSkillsForMechanism(Player player, Material material) {
        List<Talent> skills = new ArrayList<>();
        PlayerTalents playerTalents = talentManager.getPlayerTalents(player);

        // 只遍歷玩家已選取的 4 個技能插槽
        String[] selectedSkills = playerTalents.getSelectedSkills();
        for (String talentId : selectedSkills) {
            if (talentId == null) continue;

            // 檢查是否已學習該技能
            int level = playerTalents.getTalentLevel(talentId);
            if (level <= 0) continue;

            Talent talent = talentManager.findTalent(talentId);
            if (talent != null && talent.getMechanism() != null) {
                // 檢查機制是否匹配（支援一對多材質，例如鐮刀對應所有 HOE）
                Set<Material> talentMaterials = getMaterialsFromMechanism(talent.getMechanism());
                if (talentMaterials != null && talentMaterials.contains(material)) {
                    skills.add(talent);
                }
            }
        }

        // 按技能名稱排序以保證順序一致
        skills.sort(Comparator.comparing(Talent::getName));

        return skills;
    }

    /**
     * 從機制描述獲取對應的材質集合
     * @param mechanism 機制描述
     * @return 對應材質集合
     */
    private Set<Material> getMaterialsFromMechanism(String mechanism) {
        for (Map.Entry<String, Set<Material>> entry : mechanismMaterials.entrySet()) {
            if (mechanism.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 清除玩家的技能選擇數據
     * @param player 玩家
     */
    public void clearPlayerData(Player player) {
        playerSkillSelection.remove(player.getUniqueId());
    }

    /**
     * 獲取技能切換提示
     * @param player 玩家
     * @param item 手持物品
     * @return 提示訊息
     */
    public String getSwitchHint(Player player, ItemStack item) {
        if (item == null) return null;

        List<Talent> availableSkills = getAvailableSkillsForMechanism(player, item.getType());
        if (availableSkills.size() <= 1) {
            return null;
        }

        Talent current = getCurrentSelectedSkill(player, item);
        return "§7[§e蹲下右鍵§7切換技能] 當前: §a" + current.getName() +
               " §7(" + (availableSkills.indexOf(current) + 1) + "/" + availableSkills.size() + ")";
    }
}


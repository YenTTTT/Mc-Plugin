package com.customrpg.gui;

import com.customrpg.CustomRPG;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.managers.TalentManager;
import com.customrpg.players.PlayerStats;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.Talent;
import com.customrpg.talents.TalentBranch;
import com.customrpg.talents.TalentTree;
import com.customrpg.talents.TalentType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TalentTreeGUI - 分支技能樹 GUI (重構版)
 */
public class TalentTreeGUI implements Listener {

    private final CustomRPG plugin;
    private final TalentManager talentManager;
    private final Map<UUID, TalentBranch> playerCurrentBranch = new HashMap<>();

    private static final String GUI_TITLE_PREFIX = "§6§l天賦樹 - ";
    private static final int GUI_SIZE = 54;
    private static final int PLAYER_INFO_SLOT = 53;

    public TalentTreeGUI(CustomRPG plugin, TalentManager talentManager) {
        this.plugin = plugin;
        this.talentManager = talentManager;
    }

    public void open(Player player, TalentBranch branch) {
        playerCurrentBranch.put(player.getUniqueId(), branch);
        Inventory gui = createGUI(player, branch);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    private Inventory createGUI(Player player, TalentBranch branch) {
        String title = GUI_TITLE_PREFIX + branch.getDisplayName();
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        // 添加技能圖標
        addTalentIcons(gui, player, branch);

        // 添加玩家資訊
        addPlayerInfo(gui, player);

        return gui;
    }

    private void addTalentIcons(Inventory gui, Player player, TalentBranch branch) {
        TalentTree tree = talentManager.getTalentTree(branch);
        PlayerTalents playerTalents = talentManager.getPlayerTalents(player);

        if (tree == null) return;

        for (Talent talent : tree.getAllTalents().values()) {
            int slot = talent.getGuiSlot();
            if (slot >= 0 && slot < GUI_SIZE) {
                gui.setItem(slot, createTalentIcon(talent, playerTalents));
            }
        }
    }

    private ItemStack createTalentIcon(Talent talent, PlayerTalents playerTalents) {
        int level = playerTalents.getTalentLevel(talent.getId());
        boolean canLearn = talent.canLearn(playerTalents.getTalentLevels(), level + 1);
        boolean isMax = level >= talent.getMaxLevel();

        Material material;
        String statusPrefix;

        if (level > 0) {
            // 已學
            try {
                material = Material.valueOf(talent.getIcon());
            } catch (Exception e) {
                material = Material.ENCHANTED_BOOK;
            }
            statusPrefix = "§a[已學習] ";
        } else if (canLearn) {
            // 可學
            material = Material.WRITABLE_BOOK;
            statusPrefix = "§e[可學習] ";
        } else {
            // 鎖定
            material = Material.BOOKSHELF;
            statusPrefix = "§c[未達成前置] ";
        }

        ItemStack item = new ItemStack(material);
        if (level > 1) item.setAmount(Math.min(64, level));
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(statusPrefix + "§f" + talent.getName() + " §7(Lv" + level + "/" + talent.getMaxLevel() + ")");
            List<String> lore = new ArrayList<>();
            
            lore.add("§8" + talent.getId());
            lore.add("");
            lore.add("§f" + talent.getDescription());
            lore.add("");

            // 顯示按鍵觸發方式
            String triggerDisplay = getTriggerDisplayName(talent);
            if (triggerDisplay != null) {
                lore.add("§7觸發方式: " + triggerDisplay);
            }

            // 主動技能才顯示冷卻/消耗/機制
            if (talent.getType() == TalentType.ACTIVE) {
                lore.add("§7冷卻: §f" + talent.getCooldown() + "秒");
                if (talent.getManaCost() > 0) {
                    lore.add("§7消耗: §b" + (int)talent.getManaCost() + " MANA");
                }
                if (talent.getHpCost() > 0) {
                    lore.add("§7消耗: §c" + (int)(talent.getHpCost() * 100) + "% 生命值");
                }
                if (talent.getMechanism() != null && !talent.getMechanism().isEmpty()) {
                    lore.add("§7機制物品: §f" + talent.getMechanism());
                }
            }

            // 前置要求
            if (!talent.getPrerequisites().isEmpty()) {
                lore.add("");
                lore.add("§7前置:");
                for (Talent.Prerequisite pre : talent.getPrerequisites()) {
                    Talent preTalent = talentManager.findTalent(pre.talentId);
                    String preName = preTalent != null ? preTalent.getName() : pre.talentId;
                    boolean met = playerTalents.getTalentLevel(pre.talentId) >= pre.requiredLevel;
                    lore.add((met ? " §a✔ " : " §c✘ ") + "§7" + preName + " (Lv" + pre.requiredLevel + ")");
                }
            }

            // 等級效果
            if (level > 0) {
                lore.add("");
                lore.add("§6當前效果:");
                addLevelLore(lore, talent, level);
            }
            
            if (!isMax) {
                lore.add("");
                lore.add("§a下級效果:");
                addLevelLore(lore, talent, level + 1);
                lore.add("");
                lore.add("§e▶ 點擊消耗 " + talent.getPointsPerLevel() + " 點學習");
            } else {
                lore.add("");
                lore.add("§7已達最大等級");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addLevelLore(List<String> lore, Talent talent, int level) {
        Talent.TalentLevelData data = talent.getLevelData(level);
        if (data == null) return;

        // 收集傷害公式的元素
        List<String> formulaParts = new ArrayList<>();
        boolean hasFormula = false;

        for (Map.Entry<String, Double> entry : data.effects.entrySet()) {
            String key = entry.getKey();
            double val = entry.getValue();
            String translated = translateEffectKey(key);

            // 傷害公式相關的 scaling 值
            if (key.equals("magicScaling")) {
                formulaParts.add("§d魔力§7×§f" + formatNum(val));
                hasFormula = true;
                lore.add(" §7- " + translated + ": §f" + formatNum(val) + "倍");
            } else if (key.equals("strengthScaling")) {
                formulaParts.add("§c力量§7×§f" + formatNum(val));
                hasFormula = true;
                lore.add(" §7- " + translated + ": §f" + formatNum(val) + "倍");
            } else if (key.equals("agilityScaling")) {
                formulaParts.add("§a敏捷§7×§f" + formatNum(val));
                hasFormula = true;
                lore.add(" §7- " + translated + ": §f" + formatNum(val) + "倍");
            } else if (key.equals("baseDamage")) {
                formulaParts.add(0, "§f" + (int)val);
                hasFormula = true;
                lore.add(" §7- " + translated + ": §f" + (int)val);
            } else if (key.equals("damageMultiplier")) {
                lore.add(" §7- " + translated + ": §f" + formatNum(val) + "倍");
                hasFormula = true;
            } else if (key.endsWith("Percent") || key.endsWith("percent") || key.equals("lifeStealPercent")
                    || key.equals("leechPercent") || key.equals("bonusDamagePercent")
                    || key.equals("shieldPercent") || key.equals("hpCostReduction")
                    || key.equals("executeThreshold")) {
                lore.add(" §7- " + translated + ": §f" + (int)(val * 100) + "%");
            } else if (key.startsWith("statBonus_")) {
                String stat = key.substring("statBonus_".length());
                lore.add(" §7- " + translateStatName(stat) + ": §a+" + (int)val);
            } else if (key.equals("duration") || key.equals("burnDuration") || key.equals("delay")) {
                lore.add(" §7- " + translated + ": §f" + formatNum(val) + "秒");
            } else if (key.equals("radius") || key.equals("range")) {
                lore.add(" §7- " + translated + ": §f" + formatNum(val) + "格");
            } else if (key.equals("cooldownReduction")) {
                lore.add(" §7- " + translated + ": §f" + formatNum(val) + "秒");
            } else {
                lore.add(" §7- " + translated + ": §f" + formatNum(val));
            }
        }
        for (Map.Entry<String, Double> entry : data.scaling.entrySet()) {
            String stat = translateStatName(entry.getKey());
            lore.add(" §7- " + stat + "加乘: §f自身" + stat + " × " + formatNum(entry.getValue()));
            formulaParts.add(stat + "§7×§f" + formatNum(entry.getValue()));
            hasFormula = true;
        }

        // 傷害公式行
        if (hasFormula && !formulaParts.isEmpty()) {
            lore.add(" §e⚔ 傷害公式: §f" + String.join(" §7+ ", formulaParts));
        }
    }

    /**
     * 格式化數字：整數不帶小數，否則保留一位
     */
    private String formatNum(double val) {
        if (val == (int)val) return String.valueOf((int)val);
        return String.format("%.1f", val);
    }

    /**
     * 翻譯效果鍵名為中文
     */
    private String translateEffectKey(String key) {
        return switch (key) {
            // ===== 傷害/倍率 =====
            case "magicScaling" -> "魔力倍率";
            case "strengthScaling" -> "力量倍率";
            case "agilityScaling" -> "敏捷倍率";
            case "baseDamage" -> "基礎傷害";
            case "damageMultiplier" -> "傷害倍率";
            case "damageType" -> "傷害類型";
            case "bonusDamagePercent" -> "額外傷害";
            case "executeDamageBonus" -> "處決加傷";
            case "executeThreshold" -> "處決門檻(血量%)";

            // ===== 範圍/距離 =====
            case "range" -> "射程";
            case "radius" -> "範圍半徑";
            case "speed" -> "飛行速度";
            case "waveCount" -> "波數";
            case "waveInterval" -> "波間隔(tick)";

            // ===== 持續/時間 =====
            case "duration" -> "持續時間";
            case "burnDuration" -> "燃燒時間";
            case "delay" -> "延遲引爆";
            case "cooldownReduction" -> "冷卻縮減";

            // ===== 回復/吸血 =====
            case "leechPercent" -> "吸血比例";
            case "lifeStealPercent" -> "生命偷取";
            case "healingPerSecond" -> "每秒回復";
            case "shieldPercent" -> "護盾比例(最大HP)";

            // ===== 屬性加成 =====
            case "manaBonus" -> "魔力值加成";
            case "hpBonus" -> "生命值加成";
            case "hpCostReduction" -> "HP消耗減免";

            // ===== 增減益 =====
            case "weaknessLevel" -> "虛弱等級";
            case "slownessLevel" -> "緩速等級";
            case "damageBoost" -> "攻擊提升等級";
            case "speedBoost" -> "速度提升等級";
            case "stealthSpeedBoost" -> "隱身移速加成";

            // ===== 暴擊 =====
            case "critChanceBonus" -> "暴擊率加成";
            case "critDamageBonus" -> "暴擊傷害加成";

            // ===== 被動 =====
            case "backstabBonus" -> "背刺加傷";
            case "backstabExtraBonus" -> "背刺額外加傷";
            case "damage-bonus" -> "傷害加成(%)";
            case "crit-chance" -> "暴擊率(%)";
            case "crit-damage" -> "暴擊傷害(%)";
            case "berserker-bonus" -> "狂戰士加成";
            case "defense-bonus" -> "防禦加成(%)";
            case "dodge-chance" -> "閃避率(%)";

            // ===== 其他 =====
            case "particle" -> "粒子效果";
            case "projectileCount" -> "投射物數量";
            case "spread" -> "擴散角度";
            case "bounceCount" -> "彈跳次數";
            case "bounceRange" -> "彈跳範圍";
            case "knockback" -> "擊退距離";
            case "pullStrength" -> "拉力強度";
            case "slowDuration" -> "緩速時間";

            default -> {
                // statBonus_xxx 已經在上面處理了
                if (key.startsWith("statBonus_")) yield translateStatName(key.substring("statBonus_".length()));
                if (key.startsWith("selfEffect_")) yield "自身效果-" + key.substring("selfEffect_".length());
                if (key.startsWith("healingPerSecond_")) yield "每秒回復-" + key.substring("healingPerSecond_".length());
                if (key.startsWith("damagePerSecond_")) yield "每秒傷害-" + key.substring("damagePerSecond_".length());
                yield key;
            }
        };
    }

    /**
     * 翻譯屬性名稱
     */
    private String translateStatName(String stat) {
        return switch (stat.toUpperCase()) {
            case "STRENGTH", "strength" -> "力量";
            case "AGILITY", "agility" -> "敏捷";
            case "MAGIC", "magic" -> "魔力";
            case "SPIRIT", "spirit" -> "精神";
            case "WISDOM", "wisdom" -> "智慧";
            case "DEFENSE", "defense" -> "防禦";
            case "LUCK", "luck" -> "幸運";
            case "VITALITY", "vitality" -> "體力";
            default -> stat;
        };
    }

    /**
     * 根據天賦的觸發類型返回中文顯示名稱
     * 被動與屬性類型返回 null（不顯示按鍵觸發）
     */
    private String getTriggerDisplayName(Talent talent) {
        // 被動 / 屬性加成不需要顯示觸發方式
        if (talent.getType() == TalentType.PASSIVE || talent.getType() == TalentType.ATTRIBUTE
                || talent.getType() == TalentType.WEAPON_PASSIVE) {
            return "§d被動效果（自動觸發）";
        }

        String trigger = talent.getTriggerType();
        if (trigger == null || trigger.isEmpty()) return null;

        return switch (trigger.toUpperCase()) {
            case "RIGHT_CLICK" -> "§f手持機制物品 + §a右鍵";
            case "LEFT_CLICK" -> "§f手持機制物品 + §c左鍵";
            case "LEFT_CLICK_SNEAK" -> "§f手持機制物品 + §c蹲下左鍵";
            case "RIGHT_CLICK_SNEAK" -> "§f手持機制物品 + §a蹲下右鍵";
            case "ON_HIT" -> "§f攻擊命中時自動觸發";
            case "ON_KILL" -> "§f擊殺敵人時自動觸發";
            case "ON_DAMAGE" -> "§f受到傷害時自動觸發";
            case "PASSIVE" -> "§d被動效果（自動觸發）";
            default -> trigger;
        };
    }

    private void addPlayerInfo(Inventory gui, Player player) {
        PlayerTalents playerTalents = talentManager.getPlayerTalents(player);
        
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName("§b§l" + player.getName() + " 的天賦資訊");
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§f剩餘點數: §e" + playerTalents.getAvailablePoints());
            lore.add("");
            lore.add("§f已選取的技能天賦: ");
            
            String[] selected = playerTalents.getSelectedSkills();
            for (int i = 0; i < 4; i++) {
                String label;
                switch (i) {
                    case 0: label = "第一格: "; break;
                    case 1: label = "第二格: "; break;
                    case 2: label = "第三格: "; break;
                    case 3: label = "第四格: "; break;
                    default: label = "";
                }
                
                String skillId = selected[i];
                if (skillId != null) {
                    Talent t = talentManager.findTalent(skillId);
                    if (t != null) {
                        lore.add(label + "§5" + t.getName() + " Lv" + playerTalents.getTalentLevel(skillId) + "/" + t.getMaxLevel());
                    } else {
                        lore.add(label + "§c無");
                    }
                } else {
                    lore.add(label + "§c無");
                }
            }
            
            lore.add("");
            lore.add("§7點擊返回上一頁");
            
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        gui.setItem(PLAYER_INFO_SLOT, head);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_TITLE_PREFIX)) return;
        
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;

        TalentBranch branch = playerCurrentBranch.get(player.getUniqueId());
        if (branch == null) return;

        if (slot == PLAYER_INFO_SLOT) {
            plugin.getTalentMainMenuGUI().open(player);
            return;
        }

        TalentTree tree = talentManager.getTalentTree(branch);
        if (tree == null) return;

        // 處理數字鍵選取技能 (1-4)
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton(); // 0-8
            if (hotbarSlot >= 0 && hotbarSlot < 4) {
                for (Talent talent : tree.getAllTalents().values()) {
                    if (talent.getGuiSlot() == slot) {
                        PlayerTalents playerTalents = talentManager.getPlayerTalents(player);
                        // 必須已經學習過該天賦，且天賦類型是主動技能
                        if (playerTalents.getTalentLevel(talent.getId()) > 0 && talent.getType() == TalentType.ACTIVE) {
                            playerTalents.setSelectedSkill(hotbarSlot, talent.getId());
                            player.sendMessage("§a[天賦] 已將 §e" + talent.getName() + " §a設定到第 " + (hotbarSlot + 1) + " 格！");
                            refreshGUI(player, branch);
                        } else if (talent.getType() != TalentType.ACTIVE) {
                            player.sendMessage("§c[天賦] 只有主動技能可以被選取到技能欄！");
                        } else {
                            player.sendMessage("§c[天賦] 您必須先學習此技能才能選取！");
                        }
                        return;
                    }
                }
            }
            return;
        }

        for (Talent talent : tree.getAllTalents().values()) {
            if (talent.getGuiSlot() == slot) {
                if (talentManager.upgradeTalent(player, talent.getId())) {
                    refreshGUI(player, branch);
                }
                return;
            }
        }
    }

    private void refreshGUI(Player player, TalentBranch branch) {
        Inventory gui = createGUI(player, branch);
        player.openInventory(gui);
    }
}

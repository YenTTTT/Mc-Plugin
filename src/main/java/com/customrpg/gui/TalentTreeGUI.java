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
            lore.add("§7冷卻: §f" + talent.getCooldown() + "秒");
            lore.add("§7消耗: §f" + (int)talent.getManaCost() + " MANA");
            lore.add("§7機制: §f" + talent.getMechanism());
            
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

        for (Map.Entry<String, Double> entry : data.effects.entrySet()) {
            lore.add(" §7- " + entry.getKey() + ": §f+" + entry.getValue());
        }
        for (Map.Entry<String, Double> entry : data.scaling.entrySet()) {
            lore.add(" §7- " + entry.getKey() + "加乘: §f自身" + entry.getKey() + " * " + entry.getValue());
        }
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

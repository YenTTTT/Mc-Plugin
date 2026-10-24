package com.customrpg.gui;

import com.customrpg.CustomRPG;
import com.customrpg.managers.TalentManager;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.Talent;
import com.customrpg.talents.TalentBranch;
import com.customrpg.talents.TalentTree;
import com.customrpg.talents.TalentType;
import com.customrpg.talents.HunterPath;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
    private final Map<UUID, HunterPath> hunterPages = new HashMap<>();
    private final Map<UUID, Inventory> openViews = new HashMap<>();

    private static final String GUI_TITLE_PREFIX = "§6§l天賦樹 - ";
    private static final int GUI_SIZE = 54;
    private static final int PLAYER_INFO_SLOT = 53;

    public TalentTreeGUI(CustomRPG plugin, TalentManager talentManager) {
        this.plugin = plugin;
        this.talentManager = talentManager;
    }

    public void open(Player player, TalentBranch branch) {
        playerCurrentBranch.put(player.getUniqueId(), branch);
        hunterPages.remove(player.getUniqueId());
        Inventory gui = createGUI(player, branch);
        openViews.put(player.getUniqueId(), gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    private Inventory createGUI(Player player, TalentBranch branch) {
        if (branch == TalentBranch.HUNTER && !hunterPages.containsKey(player.getUniqueId())) return hunterOverview(player);
        String title = GUI_TITLE_PREFIX + branch.getDisplayName();
        if (branch == TalentBranch.HUNTER) title = GUI_TITLE_PREFIX + hunterPages.get(player.getUniqueId()).title;
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);
        if (branch == TalentBranch.HUNTER) {
            var path = hunterPages.get(player.getUniqueId());
            gui.setItem(4, navigation(path.icon, "§e§l" + path.title, "§7由下往上學習；鎖定圖示表示前置尚未達成", "§7滑鼠指向已學技能按1～4攜帶"));
            gui.setItem(45, navigation(Material.ARROW, "§e返回角色路線", "§7選擇小杰、奇犽、酷拉皮卡、西索、尼特羅或共通念修行"));
            if (path != HunterPath.COMMON) gui.setItem(46, navigation(Material.AMETHYST_SHARD, "§d共通念修行", "§7學習纏、練、凝等前置"));
        }

        if(branch==TalentBranch.FROST)gui.setItem(13,navigation(Material.SNOWBALL,"§b寒霜印記・自動機制",
                "§7冰霜命中疊1層，最多3層／持續6秒", "§7滿3層消耗印記，一般敵人定身0.8秒", "§7同敵8秒內最多一次；Boss改減速",
                "§7寒霧每名敵人每次施放只疊1層", "§7普通技能攜帶五招；零度王權獨立Shift＋F"));
        if(branch==TalentBranch.EARTH)gui.setItem(13,navigation(Material.STONE,"§6岩脈韌性・自動機制",
                "§7敵方直接受擊／替隊友承傷獲得岩脈", "§7每2秒最多1層；最多3層，持續8秒",
                "§7滿3層後施放防禦技能，獲4點吸收盾5秒", "§7普通技能選五招；泰坦鎮界獨立Shift＋F"));
        if(branch==TalentBranch.DARK)gui.setItem(13,navigation(Material.WITHER_ROSE,"§5蝕魂烙印・自動機制",
                "§7同次施放每敵最多1層，3層持續7秒", "§7滿層後下一次直接傷害爆發3傷害",
                "§7並降低治療15%四秒；同敵10秒冷卻", "§7七普通選五招；黯日使用Shift＋F"));
        if(branch==TalentBranch.NECROMANCER)gui.setItem(13,navigation(Material.BONE,"§7亡骸統御・自動機制",
                "§7最多兩個召喚欄位，無印記或屍體需求", "§7號令集火；普通戰士可獻祭或兩名融合",
                "§7巨衛佔兩欄，大絕精銳取代現有隊伍", "§7七普通選五招；冥府開門使用Shift＋F"));
        if(branch==TalentBranch.CHICKEN)gui.setItem(13,navigation(Material.EGG,"§e小雞隊伍・自動機制",
                "§7技能雞蛋撞擊30%孵化，數量無上限、12秒", "§7衝鋒所有普通小雞參戰，每隻加1傷害",
                "§7傷害主吃總精神；雞神真實體留場30秒", "§7七普通選五招；雞神使用Shift＋F"));
        // 添加技能圖標
        addTalentIcons(gui, player, branch);

        // 添加玩家資訊
        addPlayerInfo(gui, player);

        return gui;
    }
    private ItemStack navigation(Material material, String name, String... lore) {
        var item = new ItemStack(material); var meta = item.getItemMeta();
        meta.setDisplayName(name); meta.setLore(List.of(lore)); item.setItemMeta(meta); return item;
    }
    private Inventory hunterOverview(Player player) {
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE_PREFIX + "獵人・角色路線");
        var tree = talentManager.getTalentTree(TalentBranch.HUNTER);
        for (HunterPath path : HunterPath.values()) {
            var names = tree == null ? List.<String>of() : hunterTalents(tree, path).stream().map(t -> "§7" + t.getName()).toList();
            List<String> lore = new ArrayList<>(); lore.add("§e獨立技能頁・由下往上學習"); lore.addAll(names);
            lore.add("§a點擊進入此路線"); gui.setItem(path.menuSlot, navigation(path.icon, "§e§l" + path.title, lore.toArray(String[]::new)));
        }
        gui.setItem(4, navigation(Material.BOOK, "§6獵人・角色路線", "§7小杰在左側，其他角色各有獨立技能樹", "§7所有角色可混搭1～4／F技能槽，5是種族能力"));
        addPlayerInfo(gui, player); return gui;
    }
    public void openHunterPath(Player player, HunterPath path) {
        playerCurrentBranch.put(player.getUniqueId(), TalentBranch.HUNTER); hunterPages.put(player.getUniqueId(), path);
        refreshGUI(player, TalentBranch.HUNTER);
    }
    static List<Talent> hunterTalents(TalentTree tree, HunterPath path) {
        return tree.getAllTalents().values().stream().filter(t -> HunterPath.of(t.getId()) == path)
                .sorted(java.util.Comparator.comparingInt(Talent::getGuiSlot).reversed()).toList();
    }
    private List<Talent> visibleTalents(Player player, TalentTree tree, TalentBranch branch) {
        return branch == TalentBranch.HUNTER ? hunterTalents(tree, hunterPages.get(player.getUniqueId())) : List.copyOf(tree.getAllTalents().values());
    }

    private void addTalentIcons(Inventory gui, Player player, TalentBranch branch) {
        TalentTree tree = talentManager.getTalentTree(branch);
        PlayerTalents playerTalents = talentManager.getPlayerTalents(player);

        if (tree == null) return;

        for (Talent talent : visibleTalents(player, tree, branch)) {
            int slot = talent.getGuiSlot();
            if (slot >= 0 && slot < GUI_SIZE) {
                gui.setItem(slot, createTalentIcon(talent, playerTalents));
            }
        }
    }

    private ItemStack createTalentIcon(Talent talent, PlayerTalents playerTalents) {
        int level = playerTalents.getTalentLevel(talent.getId());
        boolean canLearn = talent.canLearn(playerTalents.effectiveLevels(), level + 1);

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
            meta.setLore(TalentLore.describe(talent, level));
            item.setItemMeta(meta);
        }
        return item;
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
            lore.add("§f已選取的技能天賦 [快捷鍵 1～4／F]: ");
            lore.add("§7滑鼠指向已學主動技能，按 1～4 綁定");
            lore.add("§d5：種族技能 §7（關閉介面後施放）");
            
            String[] selected = playerTalents.getSelectedSkills();
            for (int i = 0; i < 5; i++) {
                String label;
                switch (i) {
                    case 0: label = "第一格: "; break;
                    case 1: label = "第二格: "; break;
                    case 2: label = "第三格: "; break;
                    case 3: label = "第四格: "; break;
                    case 4: label = "第五格（F）: "; break;
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
        
        if (openViews.get(player.getUniqueId()) != event.getView().getTopInventory()) return;
        
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;

        TalentBranch branch = playerCurrentBranch.get(player.getUniqueId());
        if (branch == null) return;
        if (branch == TalentBranch.HUNTER) {
            if (!hunterPages.containsKey(player.getUniqueId())) {
                if (slot == PLAYER_INFO_SLOT) plugin.getTalentMainMenuGUI().open(player);
                else for (HunterPath path : HunterPath.values()) if (path.menuSlot == slot) { openHunterPath(player, path); break; }
                return;
            }
            if (slot == 45) { open(player, branch); return; }
            if (slot == 46) { openHunterPath(player, HunterPath.COMMON); return; }
        }

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
                for (Talent talent : visibleTalents(player, tree, branch)) {
                    if (talent.getGuiSlot() == slot) {
                        PlayerTalents playerTalents = talentManager.getPlayerTalents(player);
                        // 必須已經學習過該天賦，且天賦類型是主動技能
                        if (playerTalents.getTalentLevel(talent.getId()) > 0 && talent.getType() == TalentType.ACTIVE) {
                            if (!talentManager.bindSkill(player, talent.getId(), hotbarSlot, false)) return;
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

        for (Talent talent : visibleTalents(player, tree, branch)) {
            if (talent.getGuiSlot() == slot) {
                if (event.isRightClick() && talent.getType() == TalentType.ACTIVE) {
                    plugin.getTalentMainMenuGUI().openBindings(player, talent.getId()); return;
                }
                if (talentManager.upgradeTalent(player, talent.getId())) {
                    refreshGUI(player, branch);
                }
                return;
            }
        }
    }

    void refreshGUI(Player player, TalentBranch branch) {
        Inventory gui = createGUI(player, branch);
        openViews.put(player.getUniqueId(), gui);
        player.openInventory(gui);
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (openViews.get(event.getWhoClicked().getUniqueId()) == event.getView().getTopInventory()) event.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (openViews.remove(event.getPlayer().getUniqueId(), event.getInventory())) forget(event.getPlayer().getUniqueId());
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { openViews.remove(event.getPlayer().getUniqueId()); forget(event.getPlayer().getUniqueId()); }
    private void forget(UUID player) { hunterPages.remove(player); playerCurrentBranch.remove(player); }
    public void cleanup() {
        for (Player player : plugin.getServer().getOnlinePlayers())
            if (openViews.get(player.getUniqueId()) == player.getOpenInventory().getTopInventory()) player.closeInventory();
        openViews.clear(); hunterPages.clear(); playerCurrentBranch.clear();
    }
}

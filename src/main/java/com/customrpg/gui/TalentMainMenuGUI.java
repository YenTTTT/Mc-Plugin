package com.customrpg.gui;

import com.customrpg.CustomRPG;
import com.customrpg.managers.TalentManager;
import com.customrpg.talents.TalentBranch;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * TalentMainMenuGUI - 天賦主選單 (流派選擇畫面)
 */
public class TalentMainMenuGUI implements Listener {

    private final CustomRPG plugin;
    private final TalentManager talentManager;
    private final TalentTreeGUI talentTreeGUI;

    private static final String GUI_TITLE = "§6§l選擇天賦流派";
    private static final int GUI_SIZE = 45; // 擴大以容納更多流派

    public TalentMainMenuGUI(CustomRPG plugin, TalentManager talentManager, TalentTreeGUI talentTreeGUI) {
        this.plugin = plugin;
        this.talentManager = talentManager;
        this.talentTreeGUI = talentTreeGUI;
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // 填充背景
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, filler);
        }

        // 添加流派按鈕
        // 烈焰系 (11), 暗黑系 (12), 武器系 (13), 科技系 (14), 自然系 (15), 宿儺系 (22), 赤血操術系 (23)
        gui.setItem(11, createBranchIcon(TalentBranch.FIRE, Material.BLAZE_POWDER));
        gui.setItem(12, createBranchIcon(TalentBranch.DARK, Material.WITHER_SKELETON_SKULL));
        gui.setItem(13, createBranchIcon(TalentBranch.WEAPON, Material.NETHERITE_SWORD));
        gui.setItem(14, createBranchIcon(TalentBranch.TECH, Material.REPEATER));
        gui.setItem(15, createBranchIcon(TalentBranch.NATURE, Material.OAK_SAPLING));
        gui.setItem(22, createBranchIcon(TalentBranch.SUKUNA, Material.DIAMOND_SWORD)); // 宿儺系
        gui.setItem(23, createBranchIcon(TalentBranch.BLOOD, Material.REDSTONE)); // 赤血操術系
        gui.setItem(24, createBranchIcon(TalentBranch.ASSASSIN, Material.SPIDER_EYE)); // 刺客系
        gui.setItem(25, createBranchIcon(TalentBranch.BEAST, Material.BONE)); // 野獸系

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private ItemStack createBranchIcon(TalentBranch branch, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l" + branch.getDisplayName());
            List<String> lore = new ArrayList<>();
            lore.add("§7" + branch.getDescription());
            lore.add("");
            lore.add("§a▶ 點擊進入技能樹");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        TalentBranch branch = null;
        switch (slot) {
            case 11: branch = TalentBranch.FIRE; break;
            case 12: branch = TalentBranch.DARK; break;
            case 13: branch = TalentBranch.WEAPON; break;
            case 14: branch = TalentBranch.TECH; break;
            case 15: branch = TalentBranch.NATURE; break;
            case 22: branch = TalentBranch.SUKUNA; break;
            case 23: branch = TalentBranch.BLOOD; break;
            case 24: branch = TalentBranch.ASSASSIN; break;
            case 25: branch = TalentBranch.BEAST; break; // 修正：支援野獸系
        }

        if (branch != null) {
            talentTreeGUI.open(player, branch);
        }
    }
}

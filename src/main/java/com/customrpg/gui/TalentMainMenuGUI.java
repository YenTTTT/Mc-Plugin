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
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * TalentMainMenuGUI - 天賦主選單 (流派選擇畫面)
 */
public class TalentMainMenuGUI implements Listener {

    private final CustomRPG plugin;
    private final TalentManager talentManager;
    private final TalentTreeGUI talentTreeGUI;

    private static final String GUI_TITLE = "§6§l選擇天賦流派";
    private static final int GUI_SIZE = 54;
    private static final int[] BRANCH_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private final Map<UUID, Inventory> openViews = new HashMap<>();

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

        TalentBranch[] branches = TalentBranch.values();
        for (int i = 0; i < branches.length; i++) gui.setItem(BRANCH_SLOTS[i], createBranchIcon(branches[i], icon(branches[i])));
        openViews.put(player.getUniqueId(), gui);

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
        if (!(event.getWhoClicked() instanceof Player player)
                || openViews.get(player.getUniqueId()) != event.getView().getTopInventory()) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        TalentBranch branch = null;
        for (int i = 0; i < TalentBranch.values().length; i++)
            if (BRANCH_SLOTS[i] == slot) { branch = TalentBranch.values()[i]; break; }

        if (branch != null) {
            talentTreeGUI.open(player, branch);
        }
    }
    private Material icon(TalentBranch branch) {
        return switch (branch) {
            case FIRE -> Material.BLAZE_POWDER; case DARK -> Material.WITHER_SKELETON_SKULL;
            case WEAPON -> Material.NETHERITE_SWORD; case TECH -> Material.REPEATER;
            case NATURE -> Material.OAK_SAPLING; case SUKUNA -> Material.DIAMOND_SWORD;
            case BLOOD -> Material.REDSTONE; case ASSASSIN -> Material.SPIDER_EYE;
            case BEAST -> Material.BONE; case BOW -> Material.BOW;
            case FROST -> Material.BLUE_ICE; case STORM -> Material.LIGHTNING_ROD;
            case EARTH -> Material.SHIELD; case LIGHT -> Material.BEACON;
        };
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (openViews.get(event.getWhoClicked().getUniqueId()) == event.getView().getTopInventory()) event.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) { openViews.remove(event.getPlayer().getUniqueId(), event.getInventory()); }
    public void cleanup() { openViews.clear(); }

}

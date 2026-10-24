package com.customrpg.gui;

import com.customrpg.CustomRPG;
import com.customrpg.managers.TalentManager;
import com.customrpg.talents.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.util.*;

/** Direct navigation to the original branches, including Hunter character pages. */
public class TalentMainMenuGUI implements Listener {
    private final TalentManager talents;
    private final TalentTreeGUI trees;
    private record View(Inventory inventory, String binding) {}
    private final Map<UUID, View> views = new HashMap<>();
    private static final int[] SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30};
    public TalentMainMenuGUI(CustomRPG plugin, TalentManager talents, TalentTreeGUI trees) {
        this.talents = talents;
        this.trees = trees;
    }
    private ItemStack icon(Material material, String name, List<String> lore) {
        var item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    private Inventory create(String title) {
        var inventory = Bukkit.createInventory(null, 54, title);
        var filler = icon(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < 54; slot++) inventory.setItem(slot, filler);
        return inventory;
    }
    private void display(Player player, Inventory inventory, String binding) {
        views.put(player.getUniqueId(), new View(inventory, binding));
        player.openInventory(inventory);
    }
    private Material material(TalentBranch branch) {
        return switch (branch) {
            case FIRE -> Material.BLAZE_POWDER;
            case DARK -> Material.WITHER_ROSE;
            case WEAPON -> Material.IRON_SWORD;
            case TECH -> Material.REDSTONE;
            case NATURE -> Material.OAK_SAPLING;
            case SUKUNA -> Material.NETHERITE_SWORD;
            case BLOOD -> Material.RED_DYE;
            case ASSASSIN -> Material.IRON_AXE;
            case BEAST -> Material.BONE;
            case BOW -> Material.BOW;
            case FROST -> Material.BLUE_ICE;
            case STORM -> Material.LIGHTNING_ROD;
            case EARTH -> Material.SHIELD;
            case LIGHT -> Material.GLOWSTONE_DUST;
            case HUNTER -> Material.AMETHYST_SHARD;
            case NECROMANCER -> Material.SKELETON_SKULL;
            case CHICKEN -> Material.EGG;
        };
    }
    public void open(Player player) {
        var inventory = create("§6§l選擇天賦流派");
        for (var branch : TalentBranch.values()) {
            inventory.setItem(SLOTS[branch.ordinal()], icon(material(branch), "§e§l" + branch.getDisplayName(),
                    List.of("§7" + branch.getDescription(), "§a點擊開啟天賦樹")));
        }
        inventory.setItem(46, icon(Material.EXPERIENCE_BOTTLE,
                "§e可用天賦點數：" + talents.getPlayerTalents(player).getAvailablePoints(),
                List.of("§7右鍵已學主動技能可選擇快捷槽", "§71～4／F 施放技能；5 施放種族能力")));
        display(player, inventory, null);
    }
    public void openBindings(Player player, String id) {
        var definition = talents.findTalent(id);
        if (definition == null) return;
        if (definition.getType() != TalentType.ACTIVE || talents.getPlayerTalents(player).getTalentLevel(id) <= 0) {
            player.sendMessage("§c請選擇已學習的主動技能。");
            return;
        }
        var inventory = create("§6§l攜帶技能 - " + definition.getName());
        inventory.setItem(22, icon(Material.BOOK, "§e" + definition.getName(),
                TalentLore.describe(definition, talents.getPlayerTalents(player).getTalentLevel(id))));
        inventory.setItem(45, icon(Material.ARROW, "§e返回天賦流派", List.of()));
        var selected = talents.getPlayerTalents(player).getSelectedSkills();
        for (int slot = 0; slot < 5; slot++) {
            var equipped = talents.findTalent(selected[slot]);
            inventory.setItem(47 + slot, icon(Material.PAPER,
                    "§a技能槽 " + (slot + 1) + (slot == 4 ? "（F）" : ""),
                    List.of("§f" + (equipped == null ? "空" : equipped.getName()), "§7點擊將技能放入此槽")));
        }
        var ultimate=talents.findTalent(talents.getPlayerTalents(player).getUltimateSkill());
        inventory.setItem(52,icon(Material.NETHER_STAR,"§d大絕槽（Shift＋F）",List.of("§f"+(ultimate==null?"空":ultimate.getName()),"§7各流派共用一個大絕攜帶槽")));
        inventory.setItem(53, icon(Material.SHIELD, "§b5：種族能力", List.of("§7獨立於天賦技能槽")));
        display(player, inventory, id);
    }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var view = views.get(player.getUniqueId());
        if (view == null || view.inventory != event.getView().getTopInventory()) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        if (view.binding != null) {
            if (slot == 45) open(player);
            else if (slot >= 47 && slot <= 52 && talents.bindSkill(player, view.binding, slot - 47, slot==52))
                openBindings(player, view.binding);
            return;
        }
        for (var branch : TalentBranch.values()) if (SLOTS[branch.ordinal()] == slot) {
            trees.open(player, branch);
            return;
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        var view = views.get(event.getWhoClicked().getUniqueId());
        if (view != null && view.inventory == event.getView().getTopInventory()) event.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        var view = views.get(event.getPlayer().getUniqueId());
        if (view != null && view.inventory == event.getInventory()) views.remove(event.getPlayer().getUniqueId());
    }
    public void cleanup() { views.clear(); }
}

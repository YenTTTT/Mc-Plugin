package com.customrpg.integration.npc;

import fr.skytasul.quests.api.QuestsAPI;
import fr.skytasul.quests.api.quests.Quest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * QuestBindGUI — 管理員選擇要綁定到 NPC 的任務
 *
 * 開啟方式：對 NPC 按 Shift+右鍵（需 customrpg.npc.admin 權限）
 *          或 /rpgnpc bind <npc_id>
 *
 * 選擇任務後，系統將任務 ID 儲存到 npcs.yml。
 * 之後玩家對此 NPC 右鍵，直接觸發 quest.doNpcClick(player)。
 */
public class QuestBindGUI implements Listener {

    private static final String GUI_TITLE = "§6§l綁定任務 — 選擇目標任務";
    private final NamespacedKey QUEST_ID_KEY;

    private final JavaPlugin plugin;
    private final NpcManager npcManager;
    /** 正在開啟 GUI 的玩家 UUID → 目標 NpcEntry */
    private final Map<UUID, NpcManager.NpcEntry> pending = new HashMap<>();

    public QuestBindGUI(JavaPlugin plugin, NpcManager npcManager) {
        this.plugin     = plugin;
        this.npcManager = npcManager;
        this.QUEST_ID_KEY = new NamespacedKey(plugin, "bind_quest_id");
    }

    /**
     * 開啟給管理員的任務選擇 GUI。
     * 列出所有現有 BQ 任務，並標示目前已綁定的任務。
     */
    public void open(Player player, NpcManager.NpcEntry npc) {
        List<Quest> quests;
        try {
            quests = new ArrayList<>(QuestsAPI.getAPI().getQuestsManager().getQuests());
        } catch (Exception e) {
            player.sendMessage("§c無法取得任務列表：" + e.getMessage());
            return;
        }

        // 第一格：取消綁定；其後依序列出各任務
        int size = Math.min(54, (int) Math.ceil((quests.size() + 1) / 9.0) * 9);
        if (size < 9) size = 9;
        Inventory inv = Bukkit.createInventory(null, size, GUI_TITLE);

        // 取消綁定按鈕
        ItemStack unbindItem = new ItemStack(Material.BARRIER);
        ItemMeta um = unbindItem.getItemMeta();
        if (um != null) {
            um.setDisplayName("§c取消綁定");
            um.setLore(Collections.singletonList("§7點擊將此 NPC 調啊未綁定狀態"));
            um.getPersistentDataContainer().set(QUEST_ID_KEY, PersistentDataType.INTEGER, -1);
            unbindItem.setItemMeta(um);
        }
        inv.setItem(0, unbindItem);

        // 各任務物品
        int slot = 1;
        for (Quest quest : quests) {
            if (slot >= size) break;
            ItemStack raw = quest.getQuestItem();
            ItemStack qItem = (raw != null && raw.getType() != Material.AIR)
                    ? raw.clone() : new ItemStack(Material.PAPER);
            ItemMeta meta = qItem.getItemMeta();
            if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(qItem.getType());

            meta.setDisplayName("§e" + quest.getName());
            List<String> lore = new ArrayList<>();
            lore.add("§7任務 ID: §b" + quest.getId());
            String desc = quest.getDescription();
            if (desc != null && !desc.isEmpty()) {
                lore.add("§7描述: §f" + desc);
            }
            if (npc.getQuestId() == quest.getId()) {
                lore.add("");
                lore.add("§a§l◀ 目前綁定");
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(QUEST_ID_KEY, PersistentDataType.INTEGER, quest.getId());
            qItem.setItemMeta(meta);
            inv.setItem(slot++, qItem);
        }

        pending.put(player.getUniqueId(), npc);
        player.openInventory(inv);
        player.sendMessage("§7正在為 NPC 《§e" + npc.getName() + "§7》 選擇綁定任務...");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!GUI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;

        Integer questId = clicked.getItemMeta().getPersistentDataContainer()
                .get(QUEST_ID_KEY, PersistentDataType.INTEGER);
        if (questId == null) return;

        NpcManager.NpcEntry npc = pending.remove(player.getUniqueId());
        if (npc == null) return;

        player.closeInventory();

        if (questId < 0) {
            npcManager.bindQuest(npc, -1);
            player.sendMessage("§a已取消 NPC 《§f" + npc.getName() + "§a》 的任務綁定。");
        } else {
            npcManager.bindQuest(npc, questId);
            String questName = questId.toString();
            try {
                Quest q = QuestsAPI.getAPI().getQuestsManager().getQuest(questId);
                if (q != null) questName = q.getName();
            } catch (Exception ignored) {}
            player.sendMessage("§a已將 NPC 《§f" + npc.getName()
                    + "§a》 綁定到任務《§e" + questName + "§a》 (任務 ID: " + questId + ")");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!GUI_TITLE.equals(event.getView().getTitle())) return;
        pending.remove(player.getUniqueId());
    }
}

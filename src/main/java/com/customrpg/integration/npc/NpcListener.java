package com.customrpg.integration.npc;

import fr.skytasul.quests.api.QuestsAPI;
import fr.skytasul.quests.api.quests.Quest;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class NpcListener implements Listener {

    private final NpcManager      npcManager;
    private final QuestBindGUI    questBindGUI;
    private final JavaPlugin      plugin;

    public NpcListener(NpcManager npcManager, QuestBindGUI questBindGUI, JavaPlugin plugin) {
        this(npcManager, questBindGUI, plugin, null, null);
    }

    public NpcListener(NpcManager npcManager, QuestBindGUI questBindGUI, JavaPlugin plugin,
                       Object unused1, Object unused2) {
        this.npcManager   = npcManager;
        this.questBindGUI = questBindGUI;
        this.plugin       = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!npcManager.isNpcEntity(entity)) return;
        NpcManager.NpcEntry npc = npcManager.getNpcByEntity(entity);
        if (npc == null) return;
        event.setCancelled(true);

        Player player = event.getPlayer();

        // Shift + 右鍵（管理員） → 開啟任務綁定 GUI
        if (player.isSneaking() && player.hasPermission("customrpg.npc.admin")) {
            if (Bukkit.getPluginManager().getPlugin("BeautyQuests") != null) {
                questBindGUI.open(player, npc);
            } else {
                player.sendMessage("\u00a7eBeautyQuests \u672a\u5b89\u88dd\u3002");
            }
            return;
        }



        // 回退：BeautyQuests 任務
        int questId = npc.getQuestId();
        if (questId >= 0 && Bukkit.getPluginManager().getPlugin("BeautyQuests") != null) {
            try {
                Quest quest = QuestsAPI.getAPI().getQuestsManager().getQuest(questId);
                if (quest == null) {
                    player.sendMessage("\u00a7c\u6b64 NPC \u7d81\u5b9a\u7684\u4efb\u52d9\u5df2\u4e0d\u5b58\u5728\uff08ID: " + questId + "\uff09");
                    return;
                }
                quest.doNpcClick(player);
            } catch (Exception e) {
                plugin.getLogger().warning("[NpcListener] doNpcClick \u5931\u6557: " + e.getMessage());
                player.sendMessage("\u00a7c\u89f8\u767c\u4efb\u52d9\u6642\u767c\u751f\u932f\u8aa4\uff0c\u8acb\u806f\u7d61\u7ba1\u7406\u54e1\u3002");
            }
            return;
        }

        player.sendMessage("\u00a77\u6b64 NPC \u5c1a\u672a\u7d81\u5b9a\u4efb\u4f55\u4efb\u52d9\u3002");
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) { npcManager.handleChunkLoad(event.getChunk()); }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!npcManager.isNpcEntity(entity)) return;
        NpcManager.NpcEntry npc = npcManager.getNpcByEntity(entity);
        if (npc != null) npc.setEntity(null);
    }
}

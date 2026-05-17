package com.customrpg.integration;

import fr.skytasul.quests.api.events.QuestFinishEvent;
import fr.skytasul.quests.api.events.QuestPreLaunchEvent;
import fr.skytasul.quests.api.quests.Quest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * QuestCooldownListener -- BeautyQuests repeatable quest cooldown
 *
 * QuestFinishEvent  (MONITOR) : record completion time for repeatable quests.
 * QuestPreLaunchEvent (NORMAL): cancel start if still on cooldown.
 */
public class QuestCooldownListener implements Listener {

    private final QuestCooldownManager cooldownManager;

    public QuestCooldownListener(QuestCooldownManager cooldownManager) {
        this.cooldownManager = cooldownManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuestFinish(QuestFinishEvent event) {
        Quest quest = event.getQuest();
        if (!quest.isRepeatable()) return;
        Player player = event.getPlayer();
        cooldownManager.recordCompletion(player.getUniqueId(), quest.getId());
        long cd = QuestCooldownManager.COOLDOWN_MILLIS;
        player.sendMessage("§6[任務] §e" + quest.getName()
                + " §7已完成！冷卻時間：§b"
                + cooldownManager.formatRemaining(cd)
                + " §7後可再次接取。");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onQuestPreLaunch(QuestPreLaunchEvent event) {
        Quest quest = event.getQuest();
        if (!quest.isRepeatable()) return;
        Player player = event.getPlayer();
        long remaining = cooldownManager.getRemainingMillis(player.getUniqueId(), quest.getId());
        if (remaining <= 0) return;
        event.setCancelled(true);
        player.sendMessage("§c[任務] §e" + quest.getName()
                + " §c冷卻中，還需等待 §b"
                + cooldownManager.formatRemaining(remaining)
                + " §c才能再次接取。");
    }
}


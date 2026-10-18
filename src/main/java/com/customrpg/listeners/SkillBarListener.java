package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.SkillBarManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Vanilla hotbar selection bindings: talent slots 1-4, racial ability 5, held equipment 6-9. */
public class SkillBarListener implements Listener {
    private final CustomRPG plugin;
    private final SkillBarManager manager;
    private final Map<UUID, BukkitTask> hudTasks = new HashMap<>();

    public SkillBarListener(CustomRPG plugin, SkillBarManager manager) { this.plugin = plugin; this.manager = manager; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int slot = event.getNewSlot();
        if (slot < 0 || slot > 4 || !manager.isEnabled(player)) return;
        event.setCancelled(true);
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return;
        // Also repairs an externally-selected reserved slot before the next repeated key press.
        manager.prepareHeldSlot(player);
        boolean success = false;
        if (slot == 4) {
            if (plugin.getRacialSkillManager() != null) success = plugin.getRacialSkillManager().cast(player);
        } else if (plugin.getTalentManager() != null && plugin.getTalentSkillManager() != null) {
            String id = plugin.getTalentManager().getPlayerTalents(player).getSelectedSkills()[slot];
            if (id == null) player.sendMessage("§c技能槽 " + (slot + 1) + " 是空的，請在天賦介面攜帶已學習的主動技能。");
            else success = plugin.getTalentSkillManager().castSkillDirectly(player, id, player.getInventory().getItemInMainHand());
        }
        showSkillBarHud(player, slot);
        if (!success) player.playSound(player.getLocation(), "block.note_block.bass", 0.5f, 0.7f);
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !manager.isEnabled(player)) return;
            manager.prepareHeldSlot(player);
            player.sendMessage("§e快捷技能已啟用：1～4 天賦、5 種族；請將武器放在 6～9。/skillbar 切換模式。");
            showSkillBarHud(player, -1);
        });
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { stopHudTask(event.getPlayer()); }

    public void showSkillBarHud(Player player, int activeSlot) {
        stopHudTask(player);
        final int[] elapsed = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !manager.isEnabled(player) || elapsed[0] >= 100) { stopHudTask(player); return; }
            elapsed[0] += 10;
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(buildHudText(player, activeSlot)));
        }, 0L, 10L);
        hudTasks.put(player.getUniqueId(), task);
    }
    private String buildHudText(Player player, int activeSlot) {
        StringBuilder text = new StringBuilder("§7[");
        var talents = plugin.getTalentManager();
        String[] selected = talents == null ? new String[4] : talents.getPlayerTalents(player).getSelectedSkills();
        for (int slot = 0; slot < 4; slot++) {
            var talent = selected[slot] == null || talents == null ? null : talents.findTalent(selected[slot]);
            text.append(slot == activeSlot ? "§e" : "§f").append(slot + 1).append(":")
                    .append(talent == null ? "空" : talent.getName()).append(" §7| ");
        }
        var racial = plugin.getRacialSkillManager();
        var skill = racial == null ? null : racial.getSkill(player);
        text.append(activeSlot == 4 ? "§e" : "§d").append("5:").append(skill == null ? "未選種族" : skill.displayName());
        if (racial != null && skill != null && racial.remainingSeconds(player) > 0) text.append(" ").append(racial.remainingSeconds(player)).append("s");
        return text.append("§7]").toString();
    }
    private void stopHudTask(Player player) {
        BukkitTask task = hudTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }
    public void cleanup() { hudTasks.values().forEach(BukkitTask::cancel); hudTasks.clear(); }
}

package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.SkillBarManager;
import org.bukkit.GameMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
    private final Map<UUID, BossBar> hudBars = new HashMap<>();
    private final Map<UUID, Integer> activeSlots = new HashMap<>();

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
            player.sendMessage("§e快捷技能：1～4／F 天賦、5 種族；武器放6～9。/skillbar 切換模式。");
            showSkillBarHud(player, -1);
        });
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { hideSkillBarHud(event.getPlayer()); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!manager.isEnabled(player)) return;
        var investment = plugin.getTalentManager().getPlayerTalents(player);
        if(player.isSneaking() && !com.customrpg.talents.TalentLoadout.ultimate(investment.getUltimateSkill()))return;
        event.setCancelled(true);
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return;
        String id = player.isSneaking()?investment.getUltimateSkill():investment.getSelectedSkills()[4];
        if (id == null) player.sendMessage("§c第五技能槽尚未綁定技能。");
        else plugin.getTalentSkillManager().castSkillDirectly(player, id, player.getInventory().getItemInMainHand());
        showSkillBarHud(player, 5);
    }

    public void showSkillBarHud(Player player, int activeSlot) {
        UUID playerId = player.getUniqueId();
        activeSlots.put(playerId, activeSlot);
        // Health and mana own the ActionBar. Keep this BossBar visible while skill hotkeys are enabled.
        BossBar bar = hudBars.get(playerId);
        if (bar == null) {
            bar = plugin.getServer().createBossBar(buildHudText(player, activeSlot), BarColor.BLUE, BarStyle.SOLID);
            bar.addPlayer(player);
            hudBars.put(playerId, bar);
        }
        bar.setTitle(buildHudText(player, activeSlot));
        bar.setProgress(1.0);
        bar.setVisible(true);
        if (hudTasks.containsKey(playerId)) return;
        final BossBar displayedBar = bar;
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !manager.isEnabled(player)) {
                hideSkillBarHud(player);
                return;
            }
            displayedBar.setTitle(buildHudText(player, activeSlots.getOrDefault(playerId, -1)));
        }, 0L, 10L);
        hudTasks.put(playerId, task);
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
        if (talents != null) {
            var investment = talents.getPlayerTalents(player);
            var fifth = talents.findTalent(investment.getSelectedSkills()[4]);
            text.append(activeSlot == 5 ? "§eF:" : "§fF:").append(fifth == null ? "空" : fifth.getName()).append(" | ");
            var ultimate = talents.findTalent(investment.getUltimateSkill());
            if(ultimate!=null&&com.customrpg.talents.TalentLoadout.ultimate(ultimate.getId()))text.append("Shift+F:").append(ultimate.getName()).append(" | ");
        }
        var skill = racial == null ? null : racial.getSkill(player);
        text.append(activeSlot == 4 ? "§e" : "§d").append("5:").append(skill == null ? "未選種族" : skill.displayName());
        if (racial != null && skill != null && racial.remainingSeconds(player) > 0) text.append(" ").append(racial.remainingSeconds(player)).append("s");
        return text.append("§7]").toString();
    }
    private void stopHudTask(Player player) {
        BukkitTask task = hudTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }
    public void hideSkillBarHud(Player player) {
        stopHudTask(player);
        activeSlots.remove(player.getUniqueId());
        BossBar bar = hudBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }
    public void cleanup() {
        hudTasks.values().forEach(BukkitTask::cancel);
        hudTasks.clear();
        hudBars.values().forEach(BossBar::removeAll);
        hudBars.clear();
        activeSlots.clear();
    }
}

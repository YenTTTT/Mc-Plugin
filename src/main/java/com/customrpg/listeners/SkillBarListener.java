package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.SkillBarManager;
import com.customrpg.managers.TalentManager;
import com.customrpg.managers.TalentSkillManager;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.Talent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SkillBarListener - 快捷技能列監聽器
 *
 * 當玩家啟用快捷技能列模式後，按下鍵盤 1~4 會攔截熱鍵欄切換事件，
 * 直接施放對應技能槽中的已攜帶天賦技能。
 * 關閉模式後恢復原生熱鍵欄行為，技能仍可透過正常 mechanism 施放。
 */
public class SkillBarListener implements Listener {

    private final CustomRPG plugin;
    private final SkillBarManager skillBarManager;

    // 快捷列 HUD 顯示任務
    private final Map<UUID, BukkitRunnable> hudTasks = new HashMap<>();

    public SkillBarListener(CustomRPG plugin, SkillBarManager skillBarManager) {
        this.plugin = plugin;
        this.skillBarManager = skillBarManager;
    }

    /**
     * 攔截熱鍵欄切換事件（玩家按 1~4 時觸發）
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int newSlot = event.getNewSlot(); // 0~8 對應鍵盤 1~9

        // 只處理 0~3 (鍵盤 1~4)
        if (newSlot < 0 || newSlot > 3) return;

        // 未啟用技能列則不攔截
        if (!skillBarManager.isEnabled(player)) return;

        // 取消熱鍵欄切換，改為施放技能
        event.setCancelled(true);

        TalentManager talentManager = plugin.getTalentManager();
        if (talentManager == null) return;

        PlayerTalents pt = talentManager.getPlayerTalents(player);
        String[] selectedSkills = pt.getSelectedSkills();

        // 槽位由 newSlot (0~3) 對應
        if (selectedSkills == null || newSlot >= selectedSkills.length || selectedSkills[newSlot] == null) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(buildHudText(player, newSlot, "§c空槽")));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return;
        }

        String talentId = selectedSkills[newSlot];
        Talent talent = talentManager.findTalent(talentId);
        if (talent == null) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent("§c找不到技能: §f" + talentId));
            return;
        }

        // 使用目前手持武器作為施放基礎
        ItemStack item = player.getInventory().getItemInMainHand();

        TalentSkillManager tsm = plugin.getTalentSkillManager();
        if (tsm == null) {
            player.sendMessage(ChatColor.RED + "技能管理器尚未就緒！");
            return;
        }

        // 直接施放技能（繞過 mechanism 觸發條件）
        boolean success = tsm.castSkillDirectly(player, talentId, item);

        // 刷新 HUD
        showSkillBarHud(player, newSlot);

        if (!success) {
            // 失敗音效
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
        }
    }

    /**
     * 玩家離線時清除狀態
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        skillBarManager.disable(player);
        stopHudTask(player);
    }

    // ===== HUD 顯示 =====

    /**
     * 顯示快捷技能列 HUD（持續 5 秒然後淡出）
     *
     * @param player      玩家
     * @param activeSlot  剛觸發的槽位 (-1 表示全局開關時顯示)
     */
    public void showSkillBarHud(Player player, int activeSlot) {
        stopHudTask(player);

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            final int DURATION = 100; // 5 秒

            @Override
            public void run() {
                if (ticks >= DURATION || !player.isOnline() || !skillBarManager.isEnabled(player)) {
                    cancel();
                    hudTasks.remove(player.getUniqueId());
                    return;
                }
                String hud = buildHudText(player, activeSlot, null);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(hud));
                ticks++;
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        hudTasks.put(player.getUniqueId(), task);
    }

    /**
     * 建立技能列 HUD 文字
     *
     * @param player     玩家
     * @param activeSlot 當前觸發的槽位 (-1 = 無高亮)
     * @param extra      額外提示文字 (可為 null)
     * @return HUD 文字
     */
    private String buildHudText(Player player, int activeSlot, String extra) {
        TalentManager talentManager = plugin.getTalentManager();
        if (talentManager == null) return "§c[技能列] 載入失敗";

        PlayerTalents pt = talentManager.getPlayerTalents(player);
        String[] selectedSkills = pt.getSelectedSkills();

        StringBuilder sb = new StringBuilder();
        sb.append("§8⚡ §7[");

        for (int i = 0; i < 4; i++) {
            String skillName = "§8空";
            if (selectedSkills != null && i < selectedSkills.length && selectedSkills[i] != null) {
                Talent t = talentManager.findTalent(selectedSkills[i]);
                if (t != null) {
                    skillName = (i == activeSlot) ? "§e§l" + t.getName() : "§f" + t.getName();
                }
            } else if (i == activeSlot) {
                skillName = "§c空槽";
            }
            sb.append((i == activeSlot ? "§6" : "§7")).append(i + 1).append(":").append(skillName);
            if (i < 3) sb.append("§7 | ");
        }
        sb.append("§7]");

        if (extra != null) {
            sb.append(" ").append(extra);
        }

        return sb.toString();
    }

    /**
     * 停止並移除 HUD 任務
     */
    private void stopHudTask(Player player) {
        BukkitRunnable old = hudTasks.remove(player.getUniqueId());
        if (old != null) {
            try { old.cancel(); } catch (Exception ignored) {}
        }
    }

    /**
     * 插件關閉時清理
     */
    public void cleanup() {
        for (BukkitRunnable task : hudTasks.values()) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
        hudTasks.clear();
    }
}


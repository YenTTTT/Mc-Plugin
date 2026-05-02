package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FocusManager — 風獵者專注系統
 *
 * 管理：
 * 1. 專注值 (0-10)  → 狙擊者支線用
 * 2. 連擊數 (0-10)  → 遊俠支線用
 * 3. 靜止偵測       → 判斷玩家是否未移動
 * 4. 追蹤標記       → 標記特定目標增傷
 */
public class FocusManager {

    private final CustomRPG plugin;

    // 專注值 Map<PlayerUUID, focus>
    private final Map<UUID, Integer> focusMap = new ConcurrentHashMap<>();
    // 連擊數 Map<PlayerUUID, combo>
    private final Map<UUID, Integer> comboMap = new ConcurrentHashMap<>();
    // 上次獲得專注的時間（用於衰減延遲）
    private final Map<UUID, Long> lastFocusGainTime = new ConcurrentHashMap<>();
    // 上次命中時間（用於連擊衰減）
    private final Map<UUID, Long> lastComboTime = new ConcurrentHashMap<>();
    // 上次記錄位置（靜止偵測）
    private final Map<UUID, Location> lastPositions = new ConcurrentHashMap<>();
    // 標記目標 Map<PlayerUUID, TargetUUID>
    private final Map<UUID, UUID> markedTargets = new ConcurrentHashMap<>();
    // 標記到期時間 Map<PlayerUUID, expiryMs>
    private final Map<UUID, Long> markExpiries = new ConcurrentHashMap<>();

    public static final int MAX_FOCUS = 10;
    public static final int MAX_COMBO = 10;

    /** 專注衰減延遲（毫秒） */
    private static final long FOCUS_DECAY_DELAY_MS = 5000L;
    /** 連擊衰減延遲（毫秒） */
    private static final long COMBO_DECAY_DELAY_MS = 5000L;

    public FocusManager(CustomRPG plugin) {
        this.plugin = plugin;
        startDecayTask();
        startPositionSnapshotTask();
    }

    // ══════════════════════════════════════════
    //  專注值管理
    // ══════════════════════════════════════════

    /**
     * 增加玩家專注值
     */
    public void addFocus(Player player, int amount) {
        UUID pid = player.getUniqueId();
        int current = focusMap.getOrDefault(pid, 0);
        int newFocus = Math.min(MAX_FOCUS, current + amount);
        focusMap.put(pid, newFocus);
        lastFocusGainTime.put(pid, System.currentTimeMillis());

        showFocusBar(player, newFocus);

        if (newFocus == MAX_FOCUS && current < MAX_FOCUS) {
            player.sendMessage("§e§l[專注] §6MAX FOCUS！終極技能已解鎖！");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.6f);
            player.getWorld().spawnParticle(Particle.ENCHANT,
                    player.getLocation().add(0, 1.2, 0), 25, 0.4, 0.6, 0.4, 0.6);
        }
    }

    /** 消耗指定數量的專注 */
    public void consumeFocus(Player player, int amount) {
        UUID pid = player.getUniqueId();
        focusMap.put(pid, Math.max(0, focusMap.getOrDefault(pid, 0) - amount));
    }

    /** 消耗全部專注，回傳消耗前的數量 */
    public int consumeAllFocus(Player player) {
        UUID pid = player.getUniqueId();
        int before = focusMap.getOrDefault(pid, 0);
        focusMap.put(pid, 0);
        return before;
    }

    /** 取得玩家目前專注值 */
    public int getFocus(Player player) {
        return focusMap.getOrDefault(player.getUniqueId(), 0);
    }

    /** 直接重置專注 */
    public void resetFocus(Player player) {
        focusMap.put(player.getUniqueId(), 0);
    }

    // ══════════════════════════════════════════
    //  連擊管理
    // ══════════════════════════════════════════

    /** 增加一層連擊 */
    public void addCombo(Player player) {
        UUID pid = player.getUniqueId();
        int current = comboMap.getOrDefault(pid, 0);
        comboMap.put(pid, Math.min(MAX_COMBO, current + 1));
        lastComboTime.put(pid, System.currentTimeMillis());
    }

    /** 取得當前連擊數 */
    public int getCombo(Player player) {
        return comboMap.getOrDefault(player.getUniqueId(), 0);
    }

    /** 消耗全部連擊，回傳消耗前數量 */
    public int consumeAllCombo(Player player) {
        UUID pid = player.getUniqueId();
        int before = comboMap.getOrDefault(pid, 0);
        comboMap.put(pid, 0);
        return before;
    }

    /** 重置連擊 */
    public void resetCombo(Player player) {
        comboMap.put(player.getUniqueId(), 0);
    }

    // ══════════════════════════════════════════
    //  靜止偵測
    // ══════════════════════════════════════════

    /**
     * 判斷玩家是否在過去 0.5 秒內靜止不動（XZ 位移 < 0.15 格）
     */
    public boolean isStill(Player player) {
        Location last = lastPositions.get(player.getUniqueId());
        if (last == null) return false;
        Location cur = player.getLocation();
        double dx = cur.getX() - last.getX();
        double dz = cur.getZ() - last.getZ();
        return Math.sqrt(dx * dx + dz * dz) < 0.15;
    }

    // ══════════════════════════════════════════
    //  追蹤標記系統
    // ══════════════════════════════════════════

    /**
     * 標記目標
     * @param player 標記者
     * @param target 被標記實體
     * @param durationTicks 持續 tick 數
     */
    public void markTarget(Player player, LivingEntity target, int durationTicks) {
        UUID pid = player.getUniqueId();
        markedTargets.put(pid, target.getUniqueId());
        markExpiries.put(pid, System.currentTimeMillis() + durationTicks * 50L);

        target.setGlowing(true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isMarked(player, target)) {
                target.setGlowing(false);
            }
        }, durationTicks);
    }

    /**
     * 判斷指定實體是否被該玩家標記
     */
    public boolean isMarked(Player player, LivingEntity entity) {
        UUID pid = player.getUniqueId();
        UUID targetId = markedTargets.get(pid);
        if (targetId == null) return false;
        Long expiry = markExpiries.get(pid);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            markedTargets.remove(pid);
            markExpiries.remove(pid);
            return false;
        }
        return targetId.equals(entity.getUniqueId());
    }

    // ══════════════════════════════════════════
    //  清理
    // ══════════════════════════════════════════

    /** 玩家下線時清理所有狀態 */
    public void cleanup(Player player) {
        UUID pid = player.getUniqueId();
        focusMap.remove(pid);
        comboMap.remove(pid);
        lastFocusGainTime.remove(pid);
        lastComboTime.remove(pid);
        lastPositions.remove(pid);
        markedTargets.remove(pid);
        markExpiries.remove(pid);
    }

    // ══════════════════════════════════════════
    //  內部工具
    // ══════════════════════════════════════════

    /** 在 ActionBar 顯示專注條 */
    public void showFocusBar(Player player, int focus) {
        StringBuilder bar = new StringBuilder("§e[專注] ");
        for (int i = 0; i < MAX_FOCUS; i++) {
            bar.append(i < focus ? "§6◆" : "§8◇");
        }
        bar.append("  §f").append(focus).append("/").append(MAX_FOCUS);
        player.sendActionBar(bar.toString());
    }

    /** 啟動專注 & 連擊衰減任務（每 40 ticks / 2 秒） */
    private void startDecayTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                // 專注衰減：每次掉 1 點，直到歸零
                for (Map.Entry<UUID, Integer> entry : focusMap.entrySet()) {
                    if (entry.getValue() <= 0) continue;
                    Long last = lastFocusGainTime.get(entry.getKey());
                    if (last == null || now - last > FOCUS_DECAY_DELAY_MS) {
                        entry.setValue(entry.getValue() - 1);
                        if (entry.getValue() > 0) {
                            // 每衰減一次，重新計時（保持每 2 秒衰減一點的速度）
                            lastFocusGainTime.put(entry.getKey(), now);
                        }
                        Player p = Bukkit.getPlayer(entry.getKey());
                        if (p != null && entry.getValue() > 0) {
                            showFocusBar(p, entry.getValue());
                        }
                    }
                }

                // 連擊衰減：超過延遲後直接清零
                for (Map.Entry<UUID, Integer> entry : comboMap.entrySet()) {
                    if (entry.getValue() <= 0) continue;
                    Long last = lastComboTime.get(entry.getKey());
                    if (last == null || now - last > COMBO_DECAY_DELAY_MS) {
                        entry.setValue(0);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    /** 啟動位置快照任務（每 10 ticks 更新一次，用於靜止偵測） */
    private void startPositionSnapshotTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    lastPositions.put(p.getUniqueId(), p.getLocation().clone());
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }
}


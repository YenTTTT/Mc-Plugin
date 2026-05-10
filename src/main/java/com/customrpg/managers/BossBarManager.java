package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BossBarManager - 管理自訂怪物的 BossBar 顯示
 *
 * 功能：
 * - Boss 怪物顯示紅色 BossBar（大標題）
 * - 精英怪物顯示黃色 BossBar
 * - 普通怪物被攻擊時顯示紫色 BossBar（5 秒後消失）
 * - 定期更新血量條
 * - 怪物死亡或離開範圍時自動移除
 */
public class BossBarManager {

    private final CustomRPG plugin;
    private final MobManager mobManager;

    // mobUUID -> BossBar 實例
    private final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();
    // mobUUID -> 過期時間（僅限普通/精英怪的臨時 BossBar）
    private final Map<UUID, Long> barExpiry = new ConcurrentHashMap<>();

    // BossBar 可見距離
    private static final double BOSS_BAR_RANGE = 80.0;
    private static final double ELITE_BAR_RANGE = 40.0;
    private static final double NORMAL_BAR_RANGE = 25.0;

    // 普通怪物被攻擊後的 BossBar 顯示時間（毫秒）
    private static final long NORMAL_BAR_DURATION_MS = 5000L;
    // 精英怪物 BossBar 顯示時間
    private static final long ELITE_BAR_DURATION_MS = 15000L;

    private BukkitTask updateTask;

    public BossBarManager(CustomRPG plugin, MobManager mobManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        startUpdateTask();
    }

    /**
     * 為 Boss 怪物創建永久 BossBar（在生成時調用）
     */
    public void createBossBossBar(LivingEntity mob, int level) {
        if (activeBossBars.containsKey(mob.getUniqueId())) return;

        String name = mob.getCustomName() != null
                ? ChatColor.stripColor(mob.getCustomName()) : "Boss";

        // 若有虛擬 HP，在標題顯示實際血量
        String hpText = "";
        if (MobManager.hasVirtualHP(mob)) {
            double vMax = MobManager.getVirtualMaxHP(mob);
            hpText = ChatColor.GRAY + " [" + ChatColor.RED + formatHP(vMax)
                    + ChatColor.GRAY + " / " + ChatColor.WHITE + formatHP(vMax) + ChatColor.GRAY + "]";
        }
        String title = ChatColor.DARK_RED + "💀 " + name + ChatColor.GRAY + " Lv." + level + hpText;

        BossBar bar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SEGMENTED_10, BarFlag.CREATE_FOG);
        bar.setProgress(1.0);
        bar.setVisible(true);

        activeBossBars.put(mob.getUniqueId(), bar);
        // Boss BossBar 永不過期（-1 表示永久）
        barExpiry.put(mob.getUniqueId(), -1L);

        // 立即加入附近玩家
        addNearbyPlayers(mob, bar, BOSS_BAR_RANGE);
    }

    /**
     * 當怪物被攻擊時顯示 BossBar（精英/普通怪）
     */
    public void onMobDamaged(LivingEntity mob) {
        UUID mobId = mob.getUniqueId();

        // 如果已有 BossBar，只刷新過期時間和血量
        if (activeBossBars.containsKey(mobId)) {
            updateBarHealth(mob);
            // 刷新非永久 bar 的過期時間
            Long expiry = barExpiry.get(mobId);
            if (expiry != null && expiry != -1L) {
                String tier = mobManager.getMobTier(mob);
                long duration = "ELITE".equals(tier) ? ELITE_BAR_DURATION_MS : NORMAL_BAR_DURATION_MS;
                barExpiry.put(mobId, System.currentTimeMillis() + duration);
            }
            return;
        }

        String tier = mobManager.getMobTier(mob);
        String mobKey = mobManager.getCustomMobKey(mob);
        if (mobKey == null) return; // 非自訂怪物不顯示

        int level = mobManager.getMobLevel(mob);
        String name = mob.getCustomName() != null ? mob.getCustomName() : mobKey;

        switch (tier) {
            case "BOSS" -> {
                // Boss 應該在生成時就有 BossBar，但以防萬一
                createBossBossBar(mob, level);
            }
            case "ELITE" -> {
                String title = ChatColor.GOLD + "⚔ " + name + ChatColor.GRAY + " Lv." + level;
                BossBar bar = Bukkit.createBossBar(title, BarColor.YELLOW, BarStyle.SEGMENTED_6);
                double prog = MobManager.hasVirtualHP(mob)
                        ? MobManager.getVirtualCurrentHP(mob) / MobManager.getVirtualMaxHP(mob)
                        : mob.getHealth() / mob.getMaxHealth();
                bar.setProgress(Math.max(0.0, Math.min(1.0, prog)));
                bar.setVisible(true);
                activeBossBars.put(mobId, bar);
                barExpiry.put(mobId, System.currentTimeMillis() + ELITE_BAR_DURATION_MS);
                addNearbyPlayers(mob, bar, ELITE_BAR_RANGE);
            }
            default -> {
                // 普通怪物 - 短暫顯示
                String title = ChatColor.LIGHT_PURPLE + name + ChatColor.GRAY + " Lv." + level;
                BossBar bar = Bukkit.createBossBar(title, BarColor.PURPLE, BarStyle.SOLID);
                double prog = MobManager.hasVirtualHP(mob)
                        ? MobManager.getVirtualCurrentHP(mob) / MobManager.getVirtualMaxHP(mob)
                        : mob.getHealth() / mob.getMaxHealth();
                bar.setProgress(Math.max(0.0, Math.min(1.0, prog)));
                bar.setVisible(true);
                activeBossBars.put(mobId, bar);
                barExpiry.put(mobId, System.currentTimeMillis() + NORMAL_BAR_DURATION_MS);
                addNearbyPlayers(mob, bar, NORMAL_BAR_RANGE);
            }
        }
    }

    /**
     * 怪物死亡時移除 BossBar
     */
    public void onMobDeath(UUID mobId) {
        BossBar bar = activeBossBars.remove(mobId);
        barExpiry.remove(mobId);
        if (bar != null) {
            bar.removeAll();
        }
    }

    /**
     * 更新指定怪物的血量條（支援虛擬 HP）
     */
    private void updateBarHealth(LivingEntity mob) {
        BossBar bar = activeBossBars.get(mob.getUniqueId());
        if (bar == null) return;

        double progress;
        if (MobManager.hasVirtualHP(mob)) {
            double vCurrent = MobManager.getVirtualCurrentHP(mob);
            double vMax = MobManager.getVirtualMaxHP(mob);
            progress = vMax > 0 ? vCurrent / vMax : 0;
        } else {
            progress = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 0;
        }
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
    }

    /**
     * 加入附近玩家到 BossBar
     */
    private void addNearbyPlayers(LivingEntity mob, BossBar bar, double range) {
        for (Player player : mob.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(mob.getLocation()) <= range * range) {
                bar.addPlayer(player);
            }
        }
    }

    /**
     * 定期更新所有 BossBar
     */
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<UUID, BossBar> entry : activeBossBars.entrySet()) {
                    UUID mobId = entry.getKey();
                    BossBar bar = entry.getValue();

                    // 檢查過期
                    Long expiry = barExpiry.get(mobId);
                    if (expiry != null && expiry != -1L && now > expiry) {
                        bar.removeAll();
                        activeBossBars.remove(mobId);
                        barExpiry.remove(mobId);
                        continue;
                    }

                    // 尋找怪物實體
                    LivingEntity mob = findMob(mobId);
                    if (mob == null || mob.isDead()) {
                        bar.removeAll();
                        activeBossBars.remove(mobId);
                        barExpiry.remove(mobId);
                        continue;
                    }

                    // 更新血量（使用虛擬 HP 或 MC HP）
                    double progress;
                    if (MobManager.hasVirtualHP(mob)) {
                        double vCurrent = MobManager.getVirtualCurrentHP(mob);
                        double vMax = MobManager.getVirtualMaxHP(mob);
                        progress = vMax > 0 ? vCurrent / vMax : 0;

                        // 更新 BossBar 標題顯示實際血量數字
                        String mobKey = mobManager.getCustomMobKey(mob);
                        int level = mobManager.getMobLevel(mob);
                        String mobName = mob.getCustomName() != null
                                ? ChatColor.stripColor(mob.getCustomName()) : (mobKey != null ? mobKey : "Boss");
                        String tier = mobManager.getMobTier(mob);
                        String tierTag = switch (tier) {
                            case "ELITE" -> ChatColor.GOLD + "⚔ ";
                            case "BOSS" -> ChatColor.DARK_RED + "💀 ";
                            default -> ChatColor.LIGHT_PURPLE + "";
                        };
                        String hpText = ChatColor.GRAY + " [" + ChatColor.RED
                                + formatHP(vCurrent) + ChatColor.GRAY + " / "
                                + ChatColor.WHITE + formatHP(vMax) + ChatColor.GRAY + "]";
                        String newTitle = tierTag + ChatColor.translateAlternateColorCodes('&', mobName)
                                + ChatColor.GRAY + " Lv." + level + hpText;
                        bar.setTitle(newTitle);
                    } else {
                        progress = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 0;
                    }
                    bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

                    // 更新玩家列表（根據距離）
                    String tier = mobManager.getMobTier(mob);
                    double range = switch (tier) {
                        case "BOSS" -> BOSS_BAR_RANGE;
                        case "ELITE" -> ELITE_BAR_RANGE;
                        default -> NORMAL_BAR_RANGE;
                    };

                    for (Player player : mob.getWorld().getPlayers()) {
                        double distSq = player.getLocation().distanceSquared(mob.getLocation());
                        if (distSq <= range * range) {
                            if (!bar.getPlayers().contains(player)) {
                                bar.addPlayer(player);
                            }
                        } else {
                            bar.removePlayer(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // 每 0.5 秒更新
    }

    /**
     * 格式化 HP 數字（≥1000 時顯示 k，如 5.4k）
     */
    private static String formatHP(double hp) {
        int rounded = (int) Math.ceil(hp);
        if (rounded >= 10000) {
            return String.format("%.1fk", rounded / 1000.0);
        } else if (rounded >= 1000) {
            return String.format("%.1fk", rounded / 1000.0);
        }
        return String.valueOf(rounded);
    }

    /**
     * 在所有世界中尋找指定 UUID 的生物
     */
    private LivingEntity findMob(UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        if (entity instanceof LivingEntity le && !le.isDead()) {
            return le;
        }
        return null;
    }

    /**
     * 關閉管理器，清理所有 BossBar
     */
    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        for (BossBar bar : activeBossBars.values()) {
            bar.removeAll();
        }
        activeBossBars.clear();
        barExpiry.clear();
    }
}


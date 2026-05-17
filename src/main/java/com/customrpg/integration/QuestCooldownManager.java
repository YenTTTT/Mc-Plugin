package com.customrpg.integration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuestCooldownManager — 管理可重複任務的冷卻時間
 *
 * 功能：
 * - 當玩家完成一個「可重複（repeatable）」任務時記錄完成時間
 * - 玩家嘗試再次接取時，若冷卻未結束則阻止並提示剩餘時間
 * - 資料持久化到 config/quest_cooldowns.yml，伺服器重啟後仍有效
 *
 * 冷卻預設為 30 分鐘（{@link #COOLDOWN_MILLIS}）。
 */
public class QuestCooldownManager {

    /** 冷卻時間：30 分鐘（毫秒） */
    public static final long COOLDOWN_MILLIS = 30L * 60L * 1000L;

    private final JavaPlugin plugin;
    private final File dataFile;

    /**
     * playerUUID → ( questId → 上次完成時間 millis )
     * 使用 ConcurrentHashMap 確保非主執行緒安全讀取
     */
    private final Map<UUID, Map<Integer, Long>> cooldowns = new ConcurrentHashMap<>();

    public QuestCooldownManager(JavaPlugin plugin) {
        this.plugin  = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "config/quest_cooldowns.yml");
        load();
    }

    // ─── 冷卻管理 API ─────────────────────────────────────────────

    /**
     * 記錄玩家完成某任務的時間（設定冷卻起點）。
     * 應在 QuestFinishEvent 觸發時呼叫。
     */
    public void recordCompletion(UUID playerUUID, int questId) {
        cooldowns.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>())
                 .put(questId, System.currentTimeMillis());
        save();
    }

    /**
     * 判斷玩家是否仍在指定任務的冷卻期內。
     */
    public boolean isOnCooldown(UUID playerUUID, int questId) {
        return getRemainingMillis(playerUUID, questId) > 0;
    }

    /**
     * 取得剩餘冷卻毫秒數；若不在冷卻中則回傳 0 或負數。
     */
    public long getRemainingMillis(UUID playerUUID, int questId) {
        Map<Integer, Long> map = cooldowns.get(playerUUID);
        if (map == null) return 0;
        Long finishTime = map.get(questId);
        if (finishTime == null) return 0;
        long remaining = (finishTime + COOLDOWN_MILLIS) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * 將剩餘毫秒數格式化為 "XX 分 XX 秒" 可讀字串。
     */
    public String formatRemaining(long remainingMillis) {
        long totalSec = remainingMillis / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        if (min > 0) {
            return min + " 分 " + sec + " 秒";
        } else {
            return sec + " 秒";
        }
    }

    // ─── 持久化 ───────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = cfg.getConfigurationSection("cooldowns");
        if (root == null) return;

        for (String uuidStr : root.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); }
            catch (IllegalArgumentException e) { continue; }

            ConfigurationSection playerSec = root.getConfigurationSection(uuidStr);
            if (playerSec == null) continue;

            Map<Integer, Long> map = new ConcurrentHashMap<>();
            for (String questIdStr : playerSec.getKeys(false)) {
                try {
                    int questId = Integer.parseInt(questIdStr);
                    long ts     = playerSec.getLong(questIdStr);
                    // 已超過冷卻期的記錄就直接丟棄，不載入（節省記憶體）
                    if (ts + COOLDOWN_MILLIS > System.currentTimeMillis()) {
                        map.put(questId, ts);
                    }
                } catch (NumberFormatException ignored) {}
            }
            if (!map.isEmpty()) cooldowns.put(uuid, map);
        }

        plugin.getLogger().info("[QuestCooldownManager] 已載入 " + cooldowns.size() + " 筆任務冷卻資料。");
    }

    public void save() {
        try {
            dataFile.getParentFile().mkdirs();
            YamlConfiguration cfg = new YamlConfiguration();
            long now = System.currentTimeMillis();

            for (Map.Entry<UUID, Map<Integer, Long>> e : cooldowns.entrySet()) {
                String uuidStr = e.getKey().toString();
                for (Map.Entry<Integer, Long> qe : e.getValue().entrySet()) {
                    // 只儲存尚未到期的冷卻
                    if (qe.getValue() + COOLDOWN_MILLIS > now) {
                        cfg.set("cooldowns." + uuidStr + "." + qe.getKey(), qe.getValue());
                    }
                }
            }
            cfg.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("[QuestCooldownManager] 儲存失敗：" + ex.getMessage());
        }
    }
}


package com.customrpg.integration;

import com.customrpg.managers.MobManager;
import fr.skytasul.quests.api.editors.TextEditor;
import fr.skytasul.quests.api.editors.parsers.AbstractParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * BeautyQuestsHook — CustomRPG 與 BeautyQuests 的整合入口
 *
 * 負責：
 * 1. 在 BeautyQuests 載入後注冊 CustomRPGMobFactory
 * 2. 透過 BQ 原生 TextEditor API 提供聊天室輸入模式讓管理員輸入怪物 key
 * 3. 提供 notifyMobDeath() 讓 MobListener 通知 BeautyQuests 擊殺進度
 */
public class BeautyQuestsHook {

    private final JavaPlugin plugin;
    private final MobManager mobManager;
    private CustomRPGMobFactory factory;
    private boolean enabled = false;

    public BeautyQuestsHook(JavaPlugin plugin, MobManager mobManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
    }

    /**
     * 嘗試連接 BeautyQuests。
     * 應在 onEnable() 後以 runTask 延遲一 tick 呼叫。
     */
    public void tryEnable() {
        if (Bukkit.getPluginManager().getPlugin("BeautyQuests") == null) {
            plugin.getLogger().info("[BeautyQuestsHook] BeautyQuests 未安裝，跳過整合。");
            return;
        }

        try {
            factory = new CustomRPGMobFactory(mobManager, this);
            registerMobFactoryWithBeautyQuests(factory);

            enabled = true;
            plugin.getLogger().info("[BeautyQuestsHook] 已成功連接 BeautyQuests！已注冊 CustomRPG 怪物工廠。");
        } catch (Exception e) {
            plugin.getLogger().warning("[BeautyQuestsHook] 連接 BeautyQuests 失敗：" + e.getMessage());
        }
    }

    /**
     * 透過反射呼叫 BeautyQuests 的 QuestsAPI#getAPI().registerMobFactory(factory)，
     * 避免 IDE 偶發性無法解析 QuestsAPI 類別。
     */
    private void registerMobFactoryWithBeautyQuests(CustomRPGMobFactory mobFactory) throws Exception {
        Class<?> questsApiClass = Class.forName("fr.skytasul.quests.api.QuestsAPI");
        Object questsApi = questsApiClass.getMethod("getAPI").invoke(null);
        questsApiClass.getMethod("registerMobFactory", Class.forName("fr.skytasul.quests.api.mobs.MobFactory"))
                .invoke(questsApi, mobFactory);
    }

    // ─── BQ TextEditor 輸入模式 ──────────────────────────────────

    /**
     * 使用 BeautyQuests 原生 TextEditor API 進入怪物 key 輸入模式。
     * 由 CustomRPGMobFactory.itemClick() 呼叫。
     *
     * TextEditor 由 BQ 的 EditorManager 管理，可正確整合 BQ 的 GUI 生命週期，
     * 確保 callback 被呼叫時 ListGUI 的 objects 狀態仍然正確（不會觸發 IndexOutOfBoundsException）。
     */
    void openTextEditor(Player player, Consumer<String> callback) {
        List<MobManager.MobData> mobs = new ArrayList<>(mobManager.getMobTypes());

        // 顯示可用怪物列表
        player.sendMessage("§6§l╔══ CustomRPG 怪物選擇器 ══╗");
        player.sendMessage("§e§l 在聊天室輸入怪物 Key 來選擇目標怪物");
        player.sendMessage("§7 輸入 §ccancel §7取消");
        player.sendMessage("§6§l ──── 可用怪物 ────");
        if (mobs.isEmpty()) {
            player.sendMessage("§c  （目前沒有已注冊的自訂怪物）");
        } else {
            for (MobManager.MobData data : mobs) {
                player.sendMessage("§a  • §f" + data.getKey() + " §7— " + data.getName()
                        + " §8(" + data.getEntityType().name() + ")");
            }
        }
        player.sendMessage("§6§l╚═══════════════════╝");

        // AbstractParser：驗證輸入的 mob key 是否存在
        // 返回 null 代表無效（BQ 會繼續等待下一次輸入）
        // 返回非 null 代表有效（BQ 會呼叫 success consumer）
        AbstractParser<String> mobKeyParser = (p, input) -> {
            String trimmed = input.trim();
            MobManager.MobData data = mobManager.getMobData(trimmed);
            if (data == null) {
                p.sendMessage("§c§l[CustomRPG Quest] §r§c找不到怪物 Key：§f" + trimmed);
                p.sendMessage("§7請重新輸入正確的 Key，或輸入 §ccancel §7取消。");
                return null; // 無效→BQ 保持 editor 開啟繼續等待
            }
            return data.getKey();
        };

        // Success consumer：BQ EditorManager 在 stop() 後同步呼叫，不需額外延遲
        Consumer<String> consumer = selectedKey -> {
            callback.accept(selectedKey);
            String displayName = mobs.stream()
                    .filter(m -> m.getKey().equals(selectedKey))
                    .map(MobManager.MobData::getName)
                    .findFirst()
                    .orElse(selectedKey);
            player.sendMessage("§a§l[CustomRPG Quest] §r§a已選擇目標怪物：§f" + displayName
                    + " §7(" + selectedKey + ")");
        };

        // 使用 BQ 原生 TextEditor(player, cancelRunnable, successConsumer, parser)
        // BQ 的 EditorManager 會攔截這個玩家的下一則聊天訊息
        TextEditor<String> editor = new TextEditor<>(
                player,
                () -> player.sendMessage("§c§l[CustomRPG Quest] §r§c已取消怪物選擇。"),
                consumer,
                mobKeyParser
        );
        editor.start();
    }

    // ─── BeautyQuests 擊殺通知 ────────────────────────────────────

    /**
     * 當自訂怪物死亡時，通知 BeautyQuests 更新任務進度。
     * 由 MobListener.onMobDeath() 呼叫。
     *
     * @param deathEvent 原始的 EntityDeathEvent
     * @param mobKey     CustomRPG 怪物 key（e.g. "boss_world_ender"）
     * @param entity     死亡實體
     * @param killer     擊殺玩家（可能為 null）
     */
    public void notifyMobDeath(EntityDeathEvent deathEvent, String mobKey,
                                Entity entity, Player killer) {
        if (!enabled || factory == null || killer == null) return;
        try {
            factory.fireBeautyQuestsMobEvent(deathEvent, mobKey, entity, killer);
        } catch (Exception e) {
            plugin.getLogger().warning("[BeautyQuestsHook] callEvent 失敗：" + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}

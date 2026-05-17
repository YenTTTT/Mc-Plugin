package com.customrpg.integration;

import com.customrpg.managers.MobManager;
import fr.skytasul.quests.api.mobs.MobFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * CustomRPGMobFactory — BeautyQuests 的自訂怪物工廠
 *
 * 讓 BeautyQuests 能辨認並追蹤 CustomRPG 的自訂怪物擊殺任務。
 * T = String 代表怪物 Key（e.g. "boss_world_ender"）
 */
public class CustomRPGMobFactory implements MobFactory<String> {

    static final String FACTORY_ID = "customrpg";

    private final MobManager mobManager;
    private final BeautyQuestsHook beautyQuestsHook;

    public CustomRPGMobFactory(MobManager mobManager, BeautyQuestsHook beautyQuestsHook) {
        this.mobManager = mobManager;
        this.beautyQuestsHook = beautyQuestsHook;
    }

    // ─── MobFactory 必要實作 ───────────────────────────────────────

    @Override
    public String getID() {
        return FACTORY_ID;
    }

    /** 在 BeautyQuests 任務編輯器中顯示的工廠選擇圖示 */
    @Override
    public ItemStack getFactoryItem() {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "§6CustomRPG 自訂怪物");
            List<String> lore = new ArrayList<>();
            lore.add("§7點擊後在聊天室輸入怪物 Key");
            lore.add("§7即可選擇 CustomRPG 自訂怪物");
            lore.add("");
            lore.add("§e可用怪物：");
            for (MobManager.MobData data : mobManager.getMobTypes()) {
                lore.add("§a• §f" + data.getKey() + " §7(" + data.getName() + ")");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 管理員在任務編輯器中點擊圖示時呼叫。
     * 使用 BQ 原生 TextEditor API 進行聊天室輸入，正確整合 BQ GUI 生命週期。
     */
    @Override
    public void itemClick(Player player, Consumer<String> callback) {
        beautyQuestsHook.openTextEditor(player, callback);
    }

    /** 從 YAML 反序列化：直接回傳 key 字串 */
    @Override
    public String fromValue(String value) {
        return value;
    }

    /** 序列化為 YAML：直接儲存 key 字串 */
    @Override
    public String getValue(String mobKey) {
        return mobKey;
    }

    /** 在 BeautyQuests 任務 UI 中顯示的怪物名稱 */
    @Override
    public String getName(String mobKey) {
        MobManager.MobData data = mobManager.getMobData(mobKey);
        return data != null ? data.getName() : mobKey;
    }

    /**
     * 回傳這個怪物設定預期的 EntityType（用於 BeautyQuests 地圖標記）。
     * 若怪物設定找不到，回傳 UNKNOWN。
     */
    @Override
    public EntityType getEntityType(String mobKey) {
        MobManager.MobData data = mobManager.getMobData(mobKey);
        if (data == null) return EntityType.UNKNOWN;
        return data.getEntityType(); // MobData 已儲存 EntityType enum
    }

    /**
     * 判斷一個 Bukkit 實體是否符合這個怪物設定。
     * 只需檢查實體的 PDC 中的 customrpg:custom_mob_type 是否等於 mobKey。
     */
    @Override
    public boolean bukkitMobApplies(String mobKey, Entity entity) {
        if (entity == null) return false;
        String key = entity.getPersistentDataContainer()
                .get(mobManager.getCustomMobNamespacedKey(), PersistentDataType.STRING);
        return mobKey.equals(key);
    }

    /**
     * 通知 BeautyQuests 怪物擊殺事件。
     *
     * 使用 MobFactory 所屬的 ClassLoader 載入 BQMobDeathEvent，
     * 並動態掃描所有建構子以找到相容的版本，
     * 避免因 BQ 版本差異或 ClassLoader 隔離導致的例外。
     */
    public void fireBeautyQuestsMobEvent(Event sourceEvent, String mobKey, Entity entity, Player killer) {
        try {
            ClassLoader bqClassLoader = MobFactory.class.getClassLoader();
            Class<?> eventClass = Class.forName(
                    "fr.skytasul.quests.api.events.internal.BQMobDeathEvent",
                    true,
                    bqClassLoader
            );

            Event bqEvent = buildBQMobDeathEvent(eventClass, mobKey, entity, killer);
            if (bqEvent == null) {
                // 記錄可用的建構子清單，方便排查版本差異
                StringBuilder ctors = new StringBuilder();
                for (Constructor<?> c : eventClass.getDeclaredConstructors()) {
                    ctors.append("\n  ").append(c);
                }
                throw new NoSuchMethodException(
                        "No compatible BQMobDeathEvent constructor found. Available:" + ctors);
            }

            Bukkit.getPluginManager().callEvent(bqEvent);

        } catch (Exception e) {
            throw new RuntimeException("Failed to fire BQMobDeathEvent", e);
        }
    }

    /**
     * 掃描 BQMobDeathEvent 的所有建構子，嘗試以目前持有的參數值對應每個參數型別。
     *
     * 對應規則（依優先順序）：
     *  - Player  → killer
     *  - Entity（及它的子介面/子類，例如 LivingEntity） → entity
     *  - int / Integer → 1
     *  - Object / String（作為 pluginMob） → mobKey
     */
    @SuppressWarnings("unchecked")
    private Event buildBQMobDeathEvent(Class<?> eventClass, String mobKey,
                                       Entity entity, Player killer) throws Exception {
        for (Constructor<?> ctor : eventClass.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            Object[] args = matchConstructorArgs(params, mobKey, entity, killer);
            if (args == null) continue;

            ctor.setAccessible(true);
            return (Event) ctor.newInstance(args);
        }
        return null;
    }

    /**
     * 嘗試將現有值對應到建構子的每個參數型別。
     * 若有任何參數無法對應，回傳 null。
     *
     * 對應優先順序：
     *  1. Object / String → mobKey（pluginMob 欄位，必須最先判斷，
     *     否則 Object.isAssignableFrom(Player) == true 會錯誤地將 killer 塞進 Object 槽）
     *  2. Player（Entity 子型）→ killer（Player extends Entity，先於純 Entity 判斷）
     *  3. Entity（及子介面）→ 被殺怪物 entity
     *  4. int / Integer → 數量 1
     */
    private Object[] matchConstructorArgs(Class<?>[] params, String mobKey,
                                          Entity entity, Player killer) {
        Object[] args = new Object[params.length];
        boolean mobAssigned    = false;
        boolean killerAssigned = false;
        boolean entityAssigned = false;
        boolean amountAssigned = false;

        for (int i = 0; i < params.length; i++) {
            Class<?> p = params[i];

            // ① Object / String → pluginMob (mobKey)
            //    必須最先判斷：Object.isAssignableFrom(任何類別) 恆為 true，
            //    若放在 Player 判斷之後，Object 槽會被錯誤地指派為 killer。
            if (!mobAssigned && (p == Object.class || p == String.class)) {
                args[i] = mobKey;
                mobAssigned = true;

            // ② Player 必須在 Entity 之前判斷，因為 Player extends Entity
            } else if (!killerAssigned && killer != null && p.isAssignableFrom(killer.getClass())) {
                args[i] = killer;
                killerAssigned = true;

            // ③ Entity → 被殺怪物
            } else if (!entityAssigned && p.isAssignableFrom(entity.getClass())) {
                args[i] = entity;
                entityAssigned = true;

            // ④ int / Integer → 擊殺數量
            } else if (!amountAssigned && (p == int.class || p == Integer.class)) {
                args[i] = 1;
                amountAssigned = true;

            } else {
                return null; // 無法對應此參數
            }
        }

        return (mobAssigned && killerAssigned && entityAssigned && amountAssigned) ? args : null;
    }

}








package com.customrpg.integration;

import com.customrpg.managers.MobManager;
import fr.skytasul.quests.api.mobs.MobFactory;
import org.bukkit.event.Event;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
     * 顯式包裝 BeautyQuests 的 MobFactory#callEvent，
     * 避免在其他類中直接呼叫 default method 時出現 IDE 解析問題。
     */
    public void fireBeautyQuestsMobEvent(Event sourceEvent, String mobKey, Entity entity, Player killer) {
        MobFactory.super.callEvent(sourceEvent, mobKey, entity, killer);
    }
}








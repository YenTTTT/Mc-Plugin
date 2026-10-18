package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 玩家 HUD 的相容入口，並清理舊版怪物頭頂血條。
 * 不再建立血條實體或排程怪物血量／位置同步。
 */
public class HealthDisplayManager {

    private static final String COLOR_CODE_PATTERN = "(?:§[0-9A-FK-ORa-fk-or])*";
    private static final Pattern HEART_SUFFIX_PATTERN = Pattern.compile(
            "\\s+(?:(?:" + COLOR_CODE_PATTERN + ")[❤♡♥]){1," + 10 + "}(?:\\s+"
                    + COLOR_CODE_PATTERN + "\\[" + COLOR_CODE_PATTERN + "[0-9.,]+"
                    + COLOR_CODE_PATTERN + "/" + COLOR_CODE_PATTERN + "[0-9.,]+"
                    + COLOR_CODE_PATTERN + "\\])?$"
    );
    private static final Pattern LEGACY_NUMERIC_SUFFIX_PATTERN = Pattern.compile(
            "\\s+" + COLOR_CODE_PATTERN + "[❤♥]\\s*" + COLOR_CODE_PATTERN + "[0-9.,]+"
                    + COLOR_CODE_PATTERN + "/" + COLOR_CODE_PATTERN + "[0-9.,]+$"
    );

    private final MobManager mobManager;
    private final NamespacedKey baseNameKey;
    private final NamespacedKey disguiseNameTagKey;
    private final NamespacedKey healthTagOwnerKey;
    private ManaDisplayManager manaDisplayManager;

    public HealthDisplayManager(CustomRPG plugin, MobManager mobManager) {
        this.mobManager = mobManager;
        this.baseNameKey = new NamespacedKey(plugin, "mob_base_display_name");
        this.disguiseNameTagKey = new NamespacedKey(plugin, "disguise_nametag");
        this.healthTagOwnerKey = new NamespacedKey(plugin, "mob_health_tag_owner");
        // 只處理目前已載入的實體；其餘在 EntitiesLoadEvent 載入時清理。
        for (var world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                removeLegacyMobHealthDisplay(entity);
            }
        }
    }

    public void setDamageDisplayManager(DamageDisplayManager damageDisplayManager) {
        // 保留既有初始化入口。
    }

    public void setManaDisplayManager(ManaDisplayManager manaDisplayManager) {
        this.manaDisplayManager = manaDisplayManager;
    }

    /** 只刪除本插件的舊血條；怪物及偽裝名稱實體仍保留。 */
    public void removeLegacyMobHealthDisplay(Entity entity) {
        if (entity instanceof ArmorStand
                && entity.getPersistentDataContainer().has(healthTagOwnerKey, PersistentDataType.STRING)) {
            entity.remove();
            return;
        }
        boolean disguiseName = entity instanceof ArmorStand
                && entity.getPersistentDataContainer().has(disguiseNameTagKey, PersistentDataType.STRING);
        boolean customMob = entity instanceof LivingEntity && !(entity instanceof Player)
                && mobManager.getCustomMobKey(entity) != null;
        if (!disguiseName && !customMob) {
            return;
        }
        String currentName = entity.getCustomName();
        String cleanName = stripHealthSuffix(currentName);
        if (!Objects.equals(currentName, cleanName)) {
            entity.setCustomName(cleanName);
        }
        String storedName = entity.getPersistentDataContainer().get(baseNameKey, PersistentDataType.STRING);
        String cleanStoredName = stripHealthSuffix(storedName);
        if (!Objects.equals(storedName, cleanStoredName)) {
            entity.getPersistentDataContainer().set(baseNameKey, PersistentDataType.STRING, cleanStoredName);
        }
    }

    /** 去除舊版名稱尾端的愛心或數值血量。 */
    public static String stripHealthSuffix(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }

        Matcher heartMatcher = HEART_SUFFIX_PATTERN.matcher(name);
        if (heartMatcher.find()) {
            return name.substring(0, heartMatcher.start()).trim();
        }

        Matcher legacyMatcher = LEGACY_NUMERIC_SUFFIX_PATTERN.matcher(name);
        if (legacyMatcher.find()) {
            return name.substring(0, legacyMatcher.start()).trim();
        }

        return name;
    }

    /** 保留既有呼叫入口，僅清理舊血條名稱。 */
    public void updateMobHealthImmediately(LivingEntity mob) {
        removeLegacyMobHealthDisplay(mob);
    }

    public void updatePlayerHealthImmediately(Player player) {
        if (manaDisplayManager != null) {
            manaDisplayManager.updatePlayerManaImmediately(player);
        }
    }

    public void shutdown() {
        manaDisplayManager = null;
    }
}

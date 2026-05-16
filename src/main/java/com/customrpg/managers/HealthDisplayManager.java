package com.customrpg.managers;

import com.customrpg.CustomRPG;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HealthDisplayManager - 管理玩家和怪物的血量顯示
 *
 * 功能：
 * - 玩家血量顯示在 ActionBar（生命值愛心上方）- 已由 ManaDisplayManager 接管
 * - 怪物血量顯示在名稱旁邊
 *
 * 注意：當顯示傷害數字時，會暫時不更新玩家血量顯示
 */
public class HealthDisplayManager {

    private static final int HEART_BAR_LENGTH = 10;
    private static final double NORMAL_HEALTH_TAG_OFFSET = 0.25;
    private static final double DISGUISE_HEALTH_TAG_OFFSET = -0.28;
    private static final String COLOR_CODE_PATTERN = "(?:§[0-9A-FK-ORa-fk-or])*";
    private static final Pattern HEART_SUFFIX_PATTERN = Pattern.compile(
            "\\s+(?:(?:" + COLOR_CODE_PATTERN + ")[❤♡♥]){1," + HEART_BAR_LENGTH + "}(?:\\s+"
                    + COLOR_CODE_PATTERN + "\\[" + COLOR_CODE_PATTERN + "[0-9.,]+"
                    + COLOR_CODE_PATTERN + "/" + COLOR_CODE_PATTERN + "[0-9.,]+"
                    + COLOR_CODE_PATTERN + "\\])?$"
    );
    private static final Pattern LEGACY_NUMERIC_SUFFIX_PATTERN = Pattern.compile(
            "\\s+" + COLOR_CODE_PATTERN + "[❤♥]\\s*" + COLOR_CODE_PATTERN + "[0-9.,]+"
                    + COLOR_CODE_PATTERN + "/" + COLOR_CODE_PATTERN + "[0-9.,]+$"
    );

    private final CustomRPG plugin;
    private final MobManager mobManager;
    private final NamespacedKey baseNameKey;
    private final NamespacedKey disguiseNameTagKey;
    private final NamespacedKey healthTagOwnerKey;
    private final Map<UUID, UUID> activeHealthTags = new ConcurrentHashMap<>();
    private BukkitRunnable mobHealthTask;
    private BukkitRunnable mobHealthSyncTask;
    private ManaDisplayManager manaDisplayManager;

    /**
     * Constructor for HealthDisplayManager
     * @param plugin Main plugin instance
     * @param mobManager MobManager instance
     */
    public HealthDisplayManager(CustomRPG plugin, MobManager mobManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        this.baseNameKey = new NamespacedKey(plugin, "mob_base_display_name");
        this.disguiseNameTagKey = new NamespacedKey(plugin, "disguise_nametag");
        this.healthTagOwnerKey = new NamespacedKey(plugin, "mob_health_tag_owner");
        startHealthDisplayTasks();
    }

    /**
     * 設置 DamageDisplayManager（用於協調顯示）
     * @param damageDisplayManager DamageDisplayManager instance
     */
    public void setDamageDisplayManager(DamageDisplayManager damageDisplayManager) {
        // 保留方法以維持既有初始化流程相容性。
    }

    /**
     * 設置 ManaDisplayManager（用於協調顯示）
     * @param manaDisplayManager ManaDisplayManager instance
     */
    public void setManaDisplayManager(ManaDisplayManager manaDisplayManager) {
        this.manaDisplayManager = manaDisplayManager;
    }

    /**
     * 啟動血量顯示任務
     */
    private void startHealthDisplayTasks() {
        // 玩家血量顯示已由 ManaDisplayManager 接管（同時顯示血量和魔力）

        // 定期掃描自訂怪物，確保名稱與血條存在
        mobHealthTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                    for (org.bukkit.entity.Entity entity : world.getEntities()) {
                        if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                            updateMobHealthDisplay((LivingEntity) entity);
                        }
                    }
                }
            }
        };
        mobHealthTask.runTaskTimer(plugin, 0L, 10L);

        // 高頻同步血條位置，讓血條維持在名字下方
        mobHealthSyncTask = new BukkitRunnable() {
            @Override
            public void run() {
                syncTrackedHealthDisplays();
            }
        };
        mobHealthSyncTask.runTaskTimer(plugin, 0L, 2L);
    }


    /**
     * 更新怪物血量顯示（名稱）
     * @param mob 生物實體
     */
    private void updateMobHealthDisplay(LivingEntity mob) {
        if (!mob.isValid() || mob.isDead()) {
            removeHealthTag(mob.getUniqueId());
            return;
        }

        // 檢查是否為自定義生物
        String mobKey = mobManager.getCustomMobKey(mob);
        if (mobKey == null) {
            removeHealthTag(mob.getUniqueId());
            return; // 不是自定義生物，不顯示血量
        }

        MobManager.MobData mobData = mobManager.getMobData(mobKey);
        if (mobData == null) {
            removeHealthTag(mob.getUniqueId());
            return;
        }

        double health = MobManager.hasVirtualHP(mob)
                ? MobManager.getVirtualCurrentHP(mob)
                : mob.getHealth();
        double maxHealth = MobManager.hasVirtualHP(mob)
                ? MobManager.getVirtualMaxHP(mob)
                : mob.getMaxHealth();

        if (maxHealth <= 0) {
            return;
        }

        String displayName = resolveBaseDisplayName(mob, mobData);
        String heartBar = buildHeartBar(health, maxHealth);

        ArmorStand linkedNameTag = findLinkedNameTag(mob);
        mob.setCustomName(displayName);
        if (linkedNameTag != null) {
            linkedNameTag.customName(LegacyComponentSerializer.legacySection().deserialize(displayName));
            linkedNameTag.setCustomNameVisible(true);
            mob.setCustomNameVisible(false);
        } else {
            mob.setCustomNameVisible(true);
        }

        ArmorStand healthTag = getOrCreateHealthTag(mob);
        if (healthTag == null) {
            return;
        }

        updateHealthTagText(healthTag, heartBar);
        positionHealthTag(mob, healthTag);
    }

    /**
     * 根據目前血量建立愛心條
     */
    private String buildHeartBar(double health, double maxHealth) {
        double percentage = Math.max(0.0, Math.min(1.0, health / maxHealth));
        int filledHearts = percentage <= 0.0
                ? 0
                : Math.max(1, (int) Math.ceil(percentage * HEART_BAR_LENGTH));

        StringBuilder bar = new StringBuilder(HEART_BAR_LENGTH * 2);
        for (int i = 0; i < HEART_BAR_LENGTH; i++) {
            if (i < filledHearts) {
                bar.append(ChatColor.RED).append('♥');
            } else {
                bar.append(ChatColor.DARK_GRAY).append('♡');
            }
        }
        return bar.toString();
    }

    /**
     * 更新血條文字
     */
    private void updateHealthTagText(ArmorStand healthTag, String heartBar) {
        Component display = LegacyComponentSerializer.legacySection().deserialize(heartBar);
        if (!display.equals(healthTag.customName())) {
            healthTag.customName(display);
        }
        healthTag.setCustomNameVisible(true);
    }

    /**
     * 取得或初始化怪物的基礎名稱（不含血量資訊）
     */
    private String resolveBaseDisplayName(LivingEntity mob, MobManager.MobData mobData) {
        String storedName = mob.getPersistentDataContainer().get(baseNameKey, PersistentDataType.STRING);
        if (storedName != null && !storedName.isBlank()) {
            return storedName;
        }

        String currentName = mob.getCustomName();
        String baseName = stripHealthSuffix(currentName);
        if (baseName == null || baseName.isBlank()) {
            int level = mobManager.getMobLevel(mob);
            baseName = mobData.shouldShowLevelInName() && mobData.hasLevelSystem()
                    ? "§8[§eLv." + level + "§8] " + mobData.getName()
                    : mobData.getName();
        }

        mob.getPersistentDataContainer().set(baseNameKey, PersistentDataType.STRING, baseName);
        return baseName;
    }

    /**
     * 取得或建立怪物專用血條標籤
     */
    private ArmorStand getOrCreateHealthTag(LivingEntity mob) {
        UUID mobId = mob.getUniqueId();

        ArmorStand existing = getHealthTagEntity(activeHealthTags.get(mobId));
        if (existing != null) {
            return existing;
        }

        ArmorStand nearby = findNearbyHealthTag(mob);
        if (nearby != null) {
            activeHealthTags.put(mobId, nearby.getUniqueId());
            return nearby;
        }

        Location spawnLocation = getHealthTagLocation(mob);
        ArmorStand healthTag = mob.getWorld().spawn(spawnLocation, ArmorStand.class, armorStand -> {
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setMarker(true);
            armorStand.setSmall(true);
            armorStand.setBasePlate(false);
            armorStand.setArms(false);
            armorStand.setSilent(true);
            armorStand.setCustomNameVisible(true);
            armorStand.getPersistentDataContainer().set(healthTagOwnerKey, PersistentDataType.STRING, mobId.toString());
        });

        activeHealthTags.put(mobId, healthTag.getUniqueId());
        return healthTag;
    }

    /**
     * 尋找附近現有的血條標籤（避免重複生成）
     */
    private ArmorStand findNearbyHealthTag(LivingEntity mob) {
        String mobId = mob.getUniqueId().toString();
        for (Entity nearby : mob.getNearbyEntities(2.5, 3.5, 2.5)) {
            if (nearby instanceof ArmorStand armorStand) {
                String ownerId = armorStand.getPersistentDataContainer().get(healthTagOwnerKey, PersistentDataType.STRING);
                if (mobId.equals(ownerId)) {
                    return armorStand;
                }
            }
        }
        return null;
    }

    /**
     * 高頻同步所有已追蹤血條的位置與內容
     */
    private void syncTrackedHealthDisplays() {
        for (Map.Entry<UUID, UUID> entry : activeHealthTags.entrySet()) {
            UUID mobId = entry.getKey();
            Entity entity = Bukkit.getEntity(mobId);
            ArmorStand healthTag = getHealthTagEntity(entry.getValue());

            if (!(entity instanceof LivingEntity mob) || mob.isDead() || !mob.isValid()) {
                removeHealthTag(mobId);
                continue;
            }

            if (mobManager.getCustomMobKey(mob) == null) {
                removeHealthTag(mobId);
                continue;
            }

            if (healthTag == null) {
                activeHealthTags.remove(mobId);
                continue;
            }

            double health = MobManager.hasVirtualHP(mob)
                    ? MobManager.getVirtualCurrentHP(mob)
                    : mob.getHealth();
            double maxHealth = MobManager.hasVirtualHP(mob)
                    ? MobManager.getVirtualMaxHP(mob)
                    : mob.getMaxHealth();

            if (maxHealth <= 0) {
                continue;
            }

            updateHealthTagText(healthTag, buildHeartBar(health, maxHealth));
            positionHealthTag(mob, healthTag);
        }
    }

    /**
     * 將血條定位在怪物名稱下方
     */
    private void positionHealthTag(LivingEntity mob, ArmorStand healthTag) {
        Location targetLocation = getHealthTagLocation(mob);
        if (healthTag.getLocation().distanceSquared(targetLocation) > 0.0001) {
            healthTag.teleport(targetLocation);
        }
    }

    /**
     * 計算血條標籤應該出現的位置
     */
    private Location getHealthTagLocation(LivingEntity mob) {
        ArmorStand linkedNameTag = findLinkedNameTag(mob);
        if (linkedNameTag != null) {
            return linkedNameTag.getLocation().clone().add(0, DISGUISE_HEALTH_TAG_OFFSET, 0);
        }
        return mob.getLocation().clone().add(0, mob.getHeight() + NORMAL_HEALTH_TAG_OFFSET, 0);
    }

    /**
     * 去除名稱尾端已附加的血量資訊
     */
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

    /**
     * 尋找偽裝核心對應的名稱標籤
     */
    private ArmorStand findLinkedNameTag(LivingEntity mob) {
        String mobId = mob.getUniqueId().toString();
        for (Entity nearby : mob.getNearbyEntities(2.5, 3.5, 2.5)) {
            if (nearby instanceof ArmorStand armorStand) {
                String linkedId = armorStand.getPersistentDataContainer().get(disguiseNameTagKey, PersistentDataType.STRING);
                if (mobId.equals(linkedId)) {
                    return armorStand;
                }
            }
        }
        return null;
    }

    /**
     * 根據 UUID 取得血條標籤實體
     */
    private ArmorStand getHealthTagEntity(UUID tagId) {
        if (tagId == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(tagId);
        return entity instanceof ArmorStand armorStand && armorStand.isValid() ? armorStand : null;
    }

    /**
     * 移除指定怪物的血條標籤
     */
    private void removeHealthTag(UUID mobId) {
        UUID tagId = activeHealthTags.remove(mobId);
        ArmorStand healthTag = getHealthTagEntity(tagId);
        if (healthTag != null) {
            healthTag.remove();
        }
    }

    /**
     * 立即更新特定怪物的血量顯示
     * @param mob 生物實體
     */
    public void updateMobHealthImmediately(LivingEntity mob) {
        updateMobHealthDisplay(mob);
    }

    /**
     * 立即更新特定玩家的血量顯示
     * 注意：現在由 ManaDisplayManager 統一處理血量和魔力顯示
     * @param player 玩家
     */
    public void updatePlayerHealthImmediately(Player player) {
        // 委派給 ManaDisplayManager 處理
        if (manaDisplayManager != null) {
            manaDisplayManager.updatePlayerManaImmediately(player);
        }
    }

    /**
     * 停止所有血量顯示任務
     */
    public void shutdown() {
        if (mobHealthTask != null) {
            mobHealthTask.cancel();
        }
        if (mobHealthSyncTask != null) {
            mobHealthSyncTask.cancel();
        }
        for (UUID mobId : activeHealthTags.keySet()) {
            removeHealthTag(mobId);
        }
        activeHealthTags.clear();
    }
}


package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;

/**
 * MobManager - Manages custom mob types and spawning
 *
 * This class handles the registration and spawning of custom mobs with unique
 * properties and behaviors. Custom mobs are marked with persistent data to
 * identify them and trigger special mechanics.
 *
 * Example custom mobs:
 * - Snow Zombie: Throws snowballs
 * - Fire Skeleton: Shoots fire arrows
 * - Giant Slime: Extra large, splits on death
 */
public class MobManager {

    private final CustomRPG plugin;
    private final Map<String, MobData> mobTypes;
    private final NamespacedKey customMobKey;
    private final ConfigManager configManager;

    /**
     * Constructor for MobManager
     * @param plugin Main plugin instance
     * @param configManager Config manager for loading mob configs
     */
    public MobManager(CustomRPG plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobTypes = new HashMap<>();
        this.customMobKey = new NamespacedKey(plugin, "custom_mob_type");
        loadMobTypes();
    }

    /**
     * Load all custom mob types from config/mobs/types/ folder
     */
    private void loadMobTypes() {
        Map<String, Map<String, Object>> allMobs = configManager.getAllMobs();

        if (allMobs.isEmpty()) {
            plugin.getLogger().warning("No mobs found in config/mobs/types/ folder");
            return;
        }

        for (Map.Entry<String, Map<String, Object>> entry : allMobs.entrySet()) {
            String mobKey = entry.getKey();
            Map<String, Object> mobConfig = entry.getValue();

            MobData mobData = new MobData(
                mobKey,
                (String) mobConfig.get("name"),
                EntityType.valueOf((String) mobConfig.get("type")),
                (Double) mobConfig.get("health"),
                (Double) mobConfig.get("damage"),
                (String) mobConfig.get("special-behavior")
            );

            mobTypes.put(mobKey, mobData);
            plugin.getLogger().info("Loaded custom mob: " + mobData.getName());
        }
    }

    /**
     * Spawn a custom mob at the specified location
     * @param mobKey The mob type identifier
     * @param location The spawn location
     * @return The spawned entity, or null if mob type not found
     */
    public LivingEntity spawnCustomMob(String mobKey, Location location) {
        MobData mobData = mobTypes.get(mobKey);
        if (mobData == null) {
            return null;
        }

        Entity entity = location.getWorld().spawnEntity(location, mobData.getEntityType());
        if (!(entity instanceof LivingEntity)) {
            entity.remove();
            return null;
        }

        LivingEntity mob = (LivingEntity) entity;

        mob.setCustomName(mobData.getName());
        mob.setCustomNameVisible(true);
        mob.setMaxHealth(mobData.getHealth());
        mob.setHealth(mobData.getHealth());

        mob.getPersistentDataContainer().set(customMobKey, PersistentDataType.STRING, mobKey);

        plugin.getLogger().info("Spawned custom mob: " + mobData.getName() + " at " + location);
        return mob;
    }

    /**
     * Check if an entity is a custom mob
     * @param entity The entity to check
     * @return Mob key if it's a custom mob, null otherwise
     */
    public String getCustomMobKey(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return null;
        }

        LivingEntity mob = (LivingEntity) entity;
        return mob.getPersistentDataContainer().get(customMobKey, PersistentDataType.STRING);
    }

    /**
     * Get mob data by key
     * @param mobKey The mob identifier
     * @return MobData or null if not found
     */
    public MobData getMobData(String mobKey) {
        return mobTypes.get(mobKey);
    }

    /**
     * Get the number of registered custom mob types
     * @return Number of custom mob types
     */
    public int getMobTypeCount() {
        return mobTypes.size();
    }

    /**
     * Inner class to store custom mob data
     */
    public static class MobData {
        private final String key;
        private final String name;
        private final EntityType entityType;
        private final double health;
        private final double damage;
        private final String specialBehavior;

        public MobData(String key, String name, EntityType entityType, double health, double damage, String specialBehavior) {
            this.key = key;
            this.name = name;
            this.entityType = entityType;
            this.health = health;
            this.damage = damage;
            this.specialBehavior = specialBehavior;
        }

        public String getKey() { return key; }
        public String getName() { return name; }
        public EntityType getEntityType() { return entityType; }
        public double getHealth() { return health; }
        public double getDamage() { return damage; }
        public String getSpecialBehavior() { return specialBehavior; }
    }
}

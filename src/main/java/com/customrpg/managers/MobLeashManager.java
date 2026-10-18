package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class MobLeashManager {
    private final CustomRPG plugin;
    private final NamespacedKey worldKey;
    private final NamespacedKey homeXKey;
    private final NamespacedKey homeYKey;
    private final NamespacedKey homeZKey;
    private final NamespacedKey zoneKey;
    private final NamespacedKey zoneLevelKey;
    private double softDistance = 48;
    private double hardDistance = 80;

    public MobLeashManager(CustomRPG plugin) {
        this.plugin = plugin;
        worldKey = new NamespacedKey(plugin, "spawn_world");
        homeXKey = new NamespacedKey(plugin, "spawn_x");
        homeYKey = new NamespacedKey(plugin, "spawn_y");
        homeZKey = new NamespacedKey(plugin, "spawn_z");
        zoneKey = new NamespacedKey(plugin, "spawn_zone");
        zoneLevelKey = new NamespacedKey(plugin, "spawn_zone_level");
    }

    public void configure(double softDistance, double hardDistance) {
        this.softDistance = Math.max(1, softDistance);
        this.hardDistance = Math.max(this.softDistance, hardDistance);
    }

    public void recordHome(LivingEntity entity) {
        Location home = entity.getLocation();
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(worldKey, PersistentDataType.STRING, home.getWorld().getName());
        data.set(homeXKey, PersistentDataType.DOUBLE, home.getX());
        data.set(homeYKey, PersistentDataType.DOUBLE, home.getY());
        data.set(homeZKey, PersistentDataType.DOUBLE, home.getZ());
        data.set(zoneKey, PersistentDataType.STRING, plugin.getWorldZoneManager().getZone(home).id());
        data.set(zoneLevelKey, PersistentDataType.INTEGER, plugin.getWorldZoneManager().getZone(home).minLevel());
    }

    public boolean hasHome(Entity entity) {
        return entity.getPersistentDataContainer().has(worldKey, PersistentDataType.STRING);
    }

    private Location getHome(Entity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String worldName = data.get(worldKey, PersistentDataType.STRING);
        World world = worldName == null ? null : plugin.getServer().getWorld(worldName);
        Double homeX = data.get(homeXKey, PersistentDataType.DOUBLE);
        Double homeY = data.get(homeYKey, PersistentDataType.DOUBLE);
        Double homeZ = data.get(homeZKey, PersistentDataType.DOUBLE);
        return world == null || homeX == null || homeY == null || homeZ == null
                ? null : new Location(world, homeX, homeY, homeZ);
    }

    private boolean isLowerZone(Entity entity, Location location) {
        int minimum = entity.getPersistentDataContainer().getOrDefault(zoneLevelKey, PersistentDataType.INTEGER, 1);
        return plugin.getWorldZoneManager().getZone(location).minLevel() < minimum;
    }

    public boolean shouldCancelTarget(Mob mob, LivingEntity target) {
        Location home = getHome(mob);
        if (home == null || target == null) {
            return false;
        }
        Location targetLocation = target.getLocation();
        return !home.getWorld().equals(targetLocation.getWorld())
                || plugin.getSafeZoneManager().isInSafeZone(targetLocation)
                || isLowerZone(mob, targetLocation)
                || home.distanceSquared(targetLocation) > softDistance * softDistance;
    }

    public void update(LivingEntity entity) {
        Location home = getHome(entity);
        if (home == null) {
            return;
        }
        boolean otherWorld = !home.getWorld().equals(entity.getWorld());
        double distanceSquared = otherWorld ? Double.MAX_VALUE : home.distanceSquared(entity.getLocation());
        boolean forbidden = plugin.getSafeZoneManager().isInSafeZone(entity.getLocation())
                || isLowerZone(entity, entity.getLocation());
        if (entity instanceof Mob mob) {
            boolean invalidTarget = shouldCancelTarget(mob, mob.getTarget());
            if (distanceSquared > softDistance * softDistance || forbidden || invalidTarget) {
                mob.setTarget(null);
                if (!otherWorld && home.getWorld().isChunkLoaded(home.getBlockX() >> 4, home.getBlockZ() >> 4)) {
                    mob.getPathfinder().moveTo(home);
                }
            }
        }
        if (distanceSquared > hardDistance * hardDistance || forbidden) {
            if (home.getWorld().isChunkLoaded(home.getBlockX() >> 4, home.getBlockZ() >> 4)
                    && !plugin.getSafeZoneManager().isInSafeZone(home)
                    && plugin.getMobManager().findSafeSpawnLocationExact(home) != null) {
                entity.teleport(home);
            } else {
                entity.remove();
            }
        }
    }
}

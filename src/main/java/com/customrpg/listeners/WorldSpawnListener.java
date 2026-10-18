package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorldSpawnListener implements Listener {
    private final CustomRPG plugin;
    private final Map<UUID, String> playerZones = new HashMap<>();

    public WorldSpawnListener(CustomRPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Enemy
                && plugin.getDistanceSpawnManager().handlesNaturalSpawn(event.getSpawnReason())) {
            LivingEntity original = event.getEntity();
            CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.getDistanceSpawnManager().replaceNaturalMob(original, reason));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Mob mob
                && plugin.getDistanceSpawnManager().shouldCancelTarget(mob, event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (org.bukkit.entity.Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity living) {
                plugin.getDistanceSpawnManager().restoreTrackedMob(living);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ())) {
            notifyZone(event.getPlayer(), to);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        notifyZone(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        notifyZone(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null) {
            notifyZone(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerZones.remove(event.getPlayer().getUniqueId());
    }

    private void notifyZone(Player player, Location location) {
        String zone = plugin.getWorldZoneManager().getZoneKey(location);
        String previous = playerZones.put(player.getUniqueId(), zone);
        if (!zone.equals(previous) && plugin.getWorldZoneManager().isNotificationEnabled()) {
            player.sendTitle("§6探索區域", plugin.getWorldZoneManager().getDescription(location), 5, 45, 10);
        }
    }
}

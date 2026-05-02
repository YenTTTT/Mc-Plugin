package com.customrpg.listeners;
import com.customrpg.managers.SafeZoneManager;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
/**
 * SafeZoneSpawnListener - 阻止安全區域內的敵對怪物自然生成
 */
public class SafeZoneSpawnListener implements Listener {
    private final SafeZoneManager safeZoneManager;
    public SafeZoneSpawnListener(SafeZoneManager safeZoneManager) {
        this.safeZoneManager = safeZoneManager;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.COMMAND || reason == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        if (safeZoneManager.isInSafeZone(event.getLocation())) {
            event.setCancelled(true);
        }
    }
}
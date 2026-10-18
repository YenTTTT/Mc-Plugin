package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.DistanceSpawnManager;
import org.bukkit.Server;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class WorldSpawnListenerTest {
    CustomRPG plugin; DistanceSpawnManager manager; BukkitScheduler scheduler; WorldSpawnListener listener; Zombie zombie;
    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class); manager = mock(DistanceSpawnManager.class); when(plugin.getDistanceSpawnManager()).thenReturn(manager);
        Server server = mock(Server.class); when(plugin.getServer()).thenReturn(server); scheduler = mock(BukkitScheduler.class); when(server.getScheduler()).thenReturn(scheduler);
        listener = new WorldSpawnListener(plugin); zombie = mock(Zombie.class);
    }
    @Test void rejectedNaturalSpawnIsObservedButNeverScheduledForReplacement() {
        var event = new CreatureSpawnEvent(zombie, SpawnReason.NATURAL); event.setCancelled(true); listener.onSpawn(event);
        verify(manager).observeNaturalSpawn(SpawnReason.NATURAL, true); verifyNoInteractions(scheduler);
    }
    @Test void acceptedNaturalSpawnIsDeferredUntilOriginalHasBeenAddedToWorld() {
        when(manager.handlesNaturalSpawn(SpawnReason.NATURAL)).thenReturn(true);
        listener.onSpawn(new CreatureSpawnEvent(zombie, SpawnReason.NATURAL));
        verify(manager, never()).replaceNaturalMob(any(), any());
        var work = ArgumentCaptor.forClass(Runnable.class); verify(scheduler).runTask(eq(plugin), work.capture());
        work.getValue().run(); verify(manager).replaceNaturalMob(zombie, SpawnReason.NATURAL);
    }
}

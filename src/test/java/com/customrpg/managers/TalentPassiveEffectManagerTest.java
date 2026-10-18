package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

class TalentPassiveEffectManagerTest {
    CustomRPG plugin;
    BukkitScheduler scheduler;
    TalentPassiveEffectManager manager;
    TalentManager talents;
    Player player;

    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class);
        Server server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        talents = mock(TalentManager.class);
        manager = new TalentPassiveEffectManager(plugin, talents, mock(PlayerStatsManager.class));
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    private void apply(Map<String, Double> bonuses) throws Exception {
        var method = TalentPassiveEffectManager.class.getDeclaredMethod("applyContinuousEffects", Player.class, Map.class);
        method.setAccessible(true);
        method.invoke(manager, player, bonuses);
    }

    @Test void refreshReplacesRegenTaskAndRemovingBonusCancelsIt() throws Exception {
        BukkitTask first = mock(BukkitTask.class);
        BukkitTask second = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L)))
                .thenReturn(first, second);
        apply(Map.of("health-regen", 2.0));
        apply(Map.of("health-regen", 3.0));
        verify(first).cancel();
        verify(second, never()).cancel();
        apply(Map.of());
        verify(second).cancel();
    }

    @Test void quitAndShutdownCancelTrackedTasks() throws Exception {
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L))).thenReturn(task);
        apply(Map.of("health-regen", 2.0));
        manager.onPlayerQuit(new PlayerQuitEvent(player, (String) null));
        verify(task).cancel();
        apply(Map.of("health-regen", 2.0));
        manager.shutdown();
        verify(task, times(2)).cancel();
    }

    @Test void delayedJoinEffectDoesNotLoadTalentsAfterLogout() {
        manager.onPlayerJoin(new PlayerJoinEvent(player, (String) null));
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), task.capture(), eq(20L));
        when(player.isOnline()).thenReturn(false);
        task.getValue().run();
        verifyNoInteractions(talents);
    }
}

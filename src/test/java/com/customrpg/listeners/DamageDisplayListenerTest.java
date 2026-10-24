package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class DamageDisplayListenerTest {
    CustomRPG plugin; DamageDisplayManager display; TalentPassiveEffectManager passives;
    BukkitScheduler scheduler; DamageDisplayListener listener; Player source, target;
    EntityDamageByEntityEvent event;
    final TalentPassiveEffectManager.AttackBonuses bonuses = new TalentPassiveEffectManager.AttackBonuses(.3, .25);
    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class); var server = mock(Server.class); scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server); when(server.getScheduler()).thenReturn(scheduler);
        passives = mock(TalentPassiveEffectManager.class); when(plugin.getTalentPassiveEffectManager()).thenReturn(passives);
        display = mock(DamageDisplayManager.class); listener = new DamageDisplayListener(plugin, display);
        source = mock(Player.class); when(source.isOnline()).thenReturn(true); when(source.isOnGround()).thenReturn(true);
        target = mock(Player.class); when(target.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        event = mock(EntityDamageByEntityEvent.class); when(event.getDamager()).thenReturn(source); when(event.getEntity()).thenReturn(target);
        when(event.getFinalDamage()).thenReturn(12.5); when(passives.takeAttackBonuses(event)).thenReturn(bonuses);
    }
    Runnable pending() {
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), task.capture(), eq(1L)); return task.getValue();
    }
    @Test void playerVictimsGetOneFinalDamageMessageWithTheSameHitBonuses() {
        listener.onPlayerAttack(event); verifyNoInteractions(display); pending().run();
        verify(display).showDamage(source, target, 12.5, false, bonuses);
    }
    @Test void cancelledHitsConsumeTheirFeedbackButNeverShowDamage() {
        when(event.isCancelled()).thenReturn(true); listener.onPlayerAttack(event);
        verify(passives).takeAttackBonuses(event); verifyNoInteractions(scheduler, display);
    }
    @Test void lateCancellationSuppressesTheQueuedMessage() {
        listener.onPlayerAttack(event); when(event.isCancelled()).thenReturn(true); pending().run(); verifyNoInteractions(display);
    }
    @Test void zeroDamageDoesNotClaimASuccessfulBackstabOrExecute() {
        when(event.getFinalDamage()).thenReturn(0.0); listener.onPlayerAttack(event); verifyNoInteractions(scheduler, display);
    }
}

package com.customrpg.balance;

import com.customrpg.managers.MobManager;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VirtualHealthProjectionTest {
    LivingEntity mob; Map<String,Object> data; VirtualHealthProjection projection;
    @BeforeEach void setup() {
        mob=mock(Zombie.class); when(mob.getMaxHealth()).thenReturn(1024.0);
        data=new HashMap<>(); data.put(MobManager.VIRTUAL_MAX_HP_KEY,5000.0); data.put(MobManager.VIRTUAL_CURRENT_HP_KEY,5000.0);
        when(mob.getPersistentDataContainer()).thenReturn(BalanceTestFixtures.pdc(data)); projection=new VirtualHealthProjection();
    }
    @Test void aHundredDamageAlwaysSubtractsAHundredVirtualHp() {
        var event=BalanceTestFixtures.damage(mock(Player.class),mob,100);
        projection.projectDamage(event); assertEquals(20.48,event.getFinalDamage(),1e-8);
        assertEquals(5000,MobManager.getVirtualCurrentHP(mob));
        projection.commitDamage(event); assertEquals(4900,MobManager.getVirtualCurrentHP(mob),1e-8);
        verify(mob,never()).setHealth(anyDouble()); assertFalse(event.isCancelled());
    }
    @Test void lateProtectionCancellationNeverCommitsVirtualDamage() {
        var event=BalanceTestFixtures.damage(mock(Player.class),mob,100);
        projection.projectDamage(event); event.setCancelled(true); projection.commitDamage(event);
        assertEquals(5000,MobManager.getVirtualCurrentHP(mob));
    }
    @Test void multipleHitsInTheSameTickCannotOverwriteEachOthersHp() {
        var first=BalanceTestFixtures.damage(mock(Player.class),mob,100);
        var second=BalanceTestFixtures.damage(mock(Player.class),mob,250);
        projection.projectDamage(first); projection.projectDamage(second);
        projection.commitDamage(first); projection.commitDamage(second); projection.commitDamage(first);
        assertEquals(4650,MobManager.getVirtualCurrentHP(mob),1e-8);
    }
    @Test void lethalDamageRemainsANativeLethalEventWithOriginalSourceAndNoSyntheticKill() {
        var source=mock(Player.class); var event=BalanceTestFixtures.damage(source,mob,6000);
        projection.projectDamage(event); projection.commitDamage(event);
        assertTrue(event.getFinalDamage()>1024); assertEquals(0,MobManager.getVirtualCurrentHP(mob));
        assertSame(source,event.getDamager()); assertFalse(event.isCancelled()); verify(mob,never()).damage(anyDouble(),any(Entity.class)); verify(mob,never()).remove();
    }
    @SuppressWarnings("deprecation")
    @Test void absorptionConsumesOriginalRpgUnitsWhileOnlyRemainingDamageIsProjected() {
        var event=BalanceTestFixtures.damage(mock(Player.class),mob,200);
        event.setDamage(EntityDamageEvent.DamageModifier.ABSORPTION,-50);
        projection.projectDamage(event); projection.commitDamage(event);
        assertEquals(-50,event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION));
        assertEquals(30.72,event.getFinalDamage(),1e-8); assertEquals(4850,MobManager.getVirtualCurrentHP(mob),1e-8);
    }
    @SuppressWarnings("deprecation")
    @Test void fullyAbsorbedHitsDoNotReduceVirtualHp() {
        var event=BalanceTestFixtures.damage(mock(Player.class),mob,100);
        event.setDamage(EntityDamageEvent.DamageModifier.ABSORPTION,-100);
        projection.projectDamage(event); projection.commitDamage(event); assertEquals(5000,MobManager.getVirtualCurrentHP(mob));
    }
    @Test void healingIsOneToOneAndCancelledHealingCannotCommit() {
        data.put(MobManager.VIRTUAL_CURRENT_HP_KEY,4000.0);
        var event=new EntityRegainHealthEvent(mob,100,EntityRegainHealthEvent.RegainReason.CUSTOM);
        projection.projectHeal(event); assertEquals(20.48,event.getAmount(),1e-8);
        projection.commitHeal(event); assertEquals(4100,MobManager.getVirtualCurrentHP(mob),1e-8);
        var cancelled=new EntityRegainHealthEvent(mob,100,EntityRegainHealthEvent.RegainReason.CUSTOM);
        projection.projectHeal(cancelled); cancelled.setCancelled(true); projection.commitHeal(cancelled);
        assertEquals(4100,MobManager.getVirtualCurrentHP(mob),1e-8);
    }
}

package com.customrpg.balance;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.customrpg.players.PlayerStats;
import com.customrpg.utils.SkillDamageGuard;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnedFireDamageTest {
    CustomRPG plugin; Player owner; Zombie target; CombatBalance balance; BalanceCombatListener listener;
    AtomicLong time=new AtomicLong(1000);
    @BeforeEach void setup() {
        plugin=mock(CustomRPG.class); owner=mock(Player.class); target=mock(Zombie.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID()); when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.isOnline()).thenReturn(true); var world=mock(World.class);
        when(owner.getWorld()).thenReturn(world); when(target.getWorld()).thenReturn(world);
        var server=mock(Server.class); when(plugin.getServer()).thenReturn(server);
        when(server.getPlayer(owner.getUniqueId())).thenReturn(owner);
        when(plugin.getMobManager()).thenReturn(mock(MobManager.class));
        when(target.getPersistentDataContainer()).thenReturn(BalanceTestFixtures.pdc(new HashMap<>(Map.of("mob_balance_version",2,"balance_vanilla_level",10))));
        var stats=mock(PlayerStatsManager.class); when(plugin.getPlayerStatsManager()).thenReturn(stats);
        when(stats.getStats(owner)).thenReturn(new PlayerStats()); when(stats.isDataAvailable(owner)).thenReturn(true);
        var weapons=mock(WeaponManager.class); when(plugin.getWeaponManager()).thenReturn(weapons);
        var inventory=mock(org.bukkit.inventory.PlayerInventory.class); when(owner.getInventory()).thenReturn(inventory);
        balance=new CombatBalance(plugin,time::get); listener=new BalanceCombatListener(plugin,balance,time::get);
    }
    EntityDamageEvent tick() {
        var event=mock(EntityDamageEvent.class); var cancelled=new AtomicBoolean();
        when(event.getEntity()).thenReturn(target); when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FIRE_TICK);
        when(event.getDamage()).thenReturn(1.0); when(event.isCancelled()).thenAnswer(i->cancelled.get());
        doAnswer(i->{cancelled.set(i.getArgument(0));return null;}).when(event).setCancelled(anyBoolean());
        return event;
    }
    void ignite(float seconds) {
        var event=mock(EntityCombustByEntityEvent.class); when(event.getEntity()).thenReturn(target);
        when(event.getCombuster()).thenReturn(owner); when(event.getDuration()).thenReturn(seconds); listener.onIgnite(event);
    }
    @Test void burningAfterTheInitialHitKeepsItsCasterAndDealsOnlyOneDamageEvent() {
        ignite(10); time.addAndGet(7000); var event=tick();
        try(var guard=mockStatic(SkillDamageGuard.class)) {
            listener.attributeFireTick(event);
            assertTrue(event.isCancelled());
            double expected=InitialBalanceModel.mobHealth(10,InitialBalanceModel.Role.NORMAL,1)/20;
            guard.verify(()->SkillDamageGuard.damageFireTick(target,expected,owner));
            // A recursive typed event is entity-attributed and is never converted a second time.
            listener.attributeFireTick(BalanceTestFixtures.damage(owner,target,expected));
            guard.verifyNoMoreInteractions();
        }
    }
    @Test void cancelledExpiredAndUnownedFireCannotCreateAnotherHit() {
        try(var guard=mockStatic(SkillDamageGuard.class)) {
            var unowned=tick(); listener.attributeFireTick(unowned); assertFalse(unowned.isCancelled());
            ignite(2); var cancelled=tick(); cancelled.setCancelled(true); listener.attributeFireTick(cancelled);
            time.addAndGet(3001); var expired=tick(); listener.attributeFireTick(expired); assertFalse(expired.isCancelled());
            guard.verifyNoInteractions();
        }
    }
    @Test void anotherPlayersIgnitionReplacesTheOwnerButEnvironmentalFireClearsIt() {
        ignite(10);
        var second=mock(Player.class); when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        var world=target.getWorld();
        when(second.isOnline()).thenReturn(true); when(second.getWorld()).thenReturn(world);
        when(plugin.getServer().getPlayer(second.getUniqueId())).thenReturn(second);
        var newIgnition=mock(EntityCombustByEntityEvent.class); when(newIgnition.getEntity()).thenReturn(target);
        when(newIgnition.getCombuster()).thenReturn(second); when(newIgnition.getDuration()).thenReturn(5f);
        listener.onIgnite(newIgnition);
        try(var guard=mockStatic(SkillDamageGuard.class)) {
            listener.attributeFireTick(tick());
            guard.verify(()->SkillDamageGuard.damageFireTick(eq(target),anyDouble(),eq(second)));
            var natural=mock(EntityCombustEvent.class); when(natural.getEntity()).thenReturn(target); when(natural.getDuration()).thenReturn(5f);
            listener.onIgnite(natural); var event=tick(); listener.attributeFireTick(event); assertFalse(event.isCancelled());
            guard.verifyNoMoreInteractions();
        }
    }
    @Test void offlineOrDifferentWorldOwnerCannotDamageThroughTheSavedFireRecord() {
        ignite(10);
        try(var guard=mockStatic(SkillDamageGuard.class)) {
            when(owner.isOnline()).thenReturn(false); var offline=tick(); listener.attributeFireTick(offline); assertTrue(offline.isCancelled());
            when(owner.isOnline()).thenReturn(true); when(owner.getWorld()).thenReturn(mock(World.class));
            var changedWorld=tick(); listener.attributeFireTick(changedWorld); assertTrue(changedWorld.isCancelled());
            guard.verifyNoInteractions();
        }
    }
    @Test void attributedFireUsesOneBudgetReservationWhichCancellationRefunds() {
        double dps=balance.skillDps(owner);
        var first=BalanceTestFixtures.damage(owner,target,dps);
        // Same normalized marker used by the typed fire damage path.
        doAnswer(i->{listener.startDamage(first);listener.onDamage(first);return null;}).when(target).damage(dps,owner);
        SkillDamageGuard.damageNormalized(target,dps,owner);
        first.setCancelled(true); listener.afterDamage(first);
        assertEquals(dps*2,balance.extras().reserve(owner.getUniqueId(),target.getUniqueId(),dps*2,dps).damage(dps),1e-8);
    }
}

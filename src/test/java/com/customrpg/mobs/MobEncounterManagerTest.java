package com.customrpg.mobs;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.customrpg.utils.SkillDamageGuard;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobEncounterManagerTest {
    CustomRPG plugin; MobManager mobs; MobProfileRegistry profiles; MobEncounterManager manager;
    Server server; World world; LivingEntity mob; Player player; BukkitScheduler scheduler; BukkitTask task;
    AtomicLong clock = new AtomicLong(1000); Runnable tick;
    double playerHp = 20, mobHp = 20; Location playerLocation, mobLocation; int visualApplications, visualRemovals;
    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class); server = mock(Server.class); when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        scheduler = mock(BukkitScheduler.class); when(server.getScheduler()).thenReturn(scheduler); task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(5L), eq(5L))).thenReturn(task);
        mobs = mock(MobManager.class); when(plugin.getMobManager()).thenReturn(mobs); profiles = mock(MobProfileRegistry.class);
        world = mock(World.class); mob = mock(LivingEntity.class); when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mob.isValid()).thenReturn(true); when(mob.getWorld()).thenReturn(world); when(mob.getCustomName()).thenReturn("測試獸");
        mobLocation = new Location(world, 0, 64, 0); when(mob.getLocation()).thenAnswer(i -> mobLocation.clone());
        when(mob.getEyeLocation()).thenAnswer(i -> new Location(world, 0, 65, 0)); when(mob.getEyeHeight()).thenReturn(1.0);
        when(mob.getHealth()).thenAnswer(i -> mobHp); when(mob.getMaxHealth()).thenReturn(20.0);
        doAnswer(i -> { mobHp = i.getArgument(0); return null; }).when(mob).setHealth(anyDouble());
        when(mob.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(mobs.getCustomMobKey(mob)).thenReturn("test"); when(mobs.getMobLevel(mob)).thenReturn(10);
        var data = mock(MobManager.MobData.class); when(data.calculateDamage(10)).thenReturn(6.0); when(mobs.getMobData("test")).thenReturn(data);
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL); when(player.getWorld()).thenReturn(world); when(mob.hasLineOfSight(player)).thenReturn(true);
        playerLocation = new Location(world, 2, 64, 0); when(player.getLocation()).thenAnswer(i -> playerLocation.clone());
        when(player.getEyeLocation()).thenAnswer(i -> playerLocation.clone().add(0, 1, 0));
        when(player.getHealth()).thenAnswer(i -> playerHp); when(player.getVelocity()).thenReturn(new Vector());
        when(world.getNearbyPlayers(any(Location.class), anyDouble())).thenReturn(List.of(player));
        when(server.getPlayer(player.getUniqueId())).thenReturn(player);
        doReturn(List.of(world)).when(server).getWorlds(); when(world.getLivingEntities()).thenReturn(List.of(mob));
        manager = new MobEncounterManager(plugin, profiles, clock::get, (m,p) -> visualApplications++, m -> visualRemovals++);
    }
    void start(MobAbility ability) { start(ability, "aggressive"); }
    void start(MobAbility ability, String temperament) {
        var profile = new MobProfile("test", EntityType.ZOMBIE, 1, 0x999999, "DUST", Map.of(), List.of(ability), 10, 1, 3, 12, 1, 0, 0, temperament, false, "test");
        when(profiles.get("test")).thenReturn(profile); manager.start();
        var capture = ArgumentCaptor.forClass(Runnable.class); verify(scheduler).runTaskTimer(eq(plugin), capture.capture(), eq(5L), eq(5L)); tick = capture.getValue();
    }
    void windup() { clock.set(6000); tick.run(); }
    void resolve() { clock.set(7000); tick.run(); }
    void allowDamage() {
        doAnswer(i -> { assertTrue(SkillDamageGuard.isSkillDamage(mob)); playerHp -= (double)i.getArgument(0); return null; }).when(player).damage(anyDouble(), eq(mob));
    }
    @Test void damageWaitsForTelegraphAndIsGuardedExactlyOnce() {
        start(MobAbility.STOMP); allowDamage(); windup(); assertEquals(20, playerHp);
        resolve(); assertEquals(14, playerHp); tick.run(); assertEquals(14, playerHp); verify(player, times(1)).damage(6, mob);
        assertEquals(1, visualApplications);
    }
    @Test void leavingTheMarkedAreaDodgesTheAttack() {
        start(MobAbility.STOMP); allowDamage(); windup(); playerLocation = new Location(world, 8, 64, 0); resolve(); assertEquals(20, playerHp);
    }
    @Test void movingSidewaysDodgesALockedChargeLine() {
        playerLocation = new Location(world, 5, 64, 0); start(MobAbility.CHARGE); allowDamage(); windup();
        playerLocation = new Location(world, 5, 64, 4); resolve(); assertEquals(20, playerHp);
    }
    @Test void chargeRequiresActualBodyMovementAndDoesNotStrikeAtARemoteEndpoint() {
        playerLocation = new Location(world, 5, 64, 0); start(MobAbility.CHARGE); allowDamage(); windup(); resolve(); assertEquals(20, playerHp);
        clock.set(7250); tick.run(); assertEquals(20, playerHp); // Body blocked at the origin.
        mobLocation = new Location(world, 5, 64, 0); clock.set(7500); tick.run(); assertEquals(14, playerHp);
        clock.set(7750); tick.run(); assertEquals(14, playerHp); // One hit per charge.
    }
    @Test void losingLineOfSightOrChangingWorldCancelsPendingDamage() {
        start(MobAbility.STOMP); windup(); when(mob.hasLineOfSight(player)).thenReturn(false); resolve(); verify(player, never()).damage(anyDouble(), any(Entity.class));
        when(player.getWorld()).thenReturn(mock(World.class)); tick.run(); verify(player, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void safeZonesPreventBothTargetingAndSpellDamage() {
        var safe = mock(SafeZoneManager.class); when(plugin.getSafeZoneManager()).thenReturn(safe);
        start(MobAbility.STOMP); windup(); when(safe.isInSafeZone(any())).thenReturn(true); resolve();
        verify(player, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void ownedProjectilesRecheckProtectionAndOwnerAtImpact() {
        start(MobAbility.VOLLEY);
        var projectile = mock(Arrow.class); var data = mock(PersistentDataContainer.class);
        when(projectile.getShooter()).thenReturn(mob); when(projectile.getPersistentDataContainer()).thenReturn(data);
        when(data.get(new NamespacedKey("customrpg", "encounter_shot_damage"), PersistentDataType.DOUBLE)).thenReturn(4.5);
        var hit = mock(EntityDamageByEntityEvent.class); when(hit.getDamager()).thenReturn(projectile); when(hit.getEntity()).thenReturn(player);
        manager.onArrowDamage(hit); verify(hit).setDamage(4.5);
        var safe = mock(SafeZoneManager.class); when(plugin.getSafeZoneManager()).thenReturn(safe); when(safe.isInSafeZone(any())).thenReturn(true);
        manager.onArrowDamage(hit); verify(hit).setCancelled(true);
        manager.shutdown(); manager.onArrowDamage(hit); verify(hit, times(2)).setCancelled(true);
    }
    @Test void cancelledDamageDoesNotApplyKnockback() {
        start(MobAbility.STOMP); windup(); resolve(); verify(player).damage(6, mob); verify(player, never()).setVelocity(any());
    }
    @Test void removingAnEntityCancelsItsPendingCast() {
        start(MobAbility.STOMP); windup(); var event = mock(EntityRemoveEvent.class); when(event.getEntity()).thenReturn(mob);
        manager.onRemove(event); resolve(); verify(player, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void neutralCreaturesOnlyCastAfterProvocation() {
        start(MobAbility.STOMP, "neutral"); windup(); resolve(); verify(player, never()).sendActionBar(any(Component.class));
        var hit = mock(EntityDamageByEntityEvent.class); when(hit.getEntity()).thenReturn(mob); when(hit.getDamager()).thenReturn(player); manager.onDefense(hit);
        clock.set(8000); tick.run(); verify(player).sendActionBar(any(Component.class));
    }
    @Test void fortificationHasAFiniteDefensiveWindow() {
        start(MobAbility.FORTIFY); windup(); resolve();
        var hit = mock(EntityDamageEvent.class); when(hit.getEntity()).thenReturn(mob); when(hit.getDamage()).thenReturn(10.0);
        manager.onDefense(hit); verify(hit).setDamage(6);
        clock.set(12000); var later = mock(EntityDamageEvent.class); when(later.getEntity()).thenReturn(mob);
        manager.onDefense(later); verify(later, never()).setDamage(anyDouble());
    }
    @Test void invalidProfileReloadDoesNotCancelThePreviousValidCast() {
        start(MobAbility.STOMP); allowDamage(); windup(); when(profiles.reload()).thenReturn(false);
        assertFalse(manager.reloadProfiles()); resolve(); assertEquals(14, playerHp);
    }
    @Test void successfulProfileReloadClearsOldModifiersAndPendingCasts() {
        start(MobAbility.STOMP); windup(); when(profiles.reload()).thenReturn(true);
        assertTrue(manager.reloadProfiles()); assertEquals(1, visualRemovals); assertEquals(2, visualApplications);
        resolve(); verify(player, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void shutdownCancelsTheTimerAndRestoresOwnedVisualState() {
        start(MobAbility.STOMP); windup(); manager.shutdown(); verify(task).cancel(); assertEquals(1, visualRemovals);
        resolve(); verify(player, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void profiledCreepersAndEndermenCannotChangeTerrain() {
        start(MobAbility.STOMP);
        var prime = mock(ExplosionPrimeEvent.class); when(prime.getEntity()).thenReturn(mob); manager.onPrime(prime); verify(prime).setCancelled(true);
        var change = mock(EntityChangeBlockEvent.class); when(change.getEntity()).thenReturn(mob); manager.onBlockChange(change); verify(change).setCancelled(true);
    }
    @Test void ordinaryWindupCanBeInterruptedWithoutResolvingOrRefundingTheCooldown(){
        start(MobAbility.STOMP);allowDamage();windup();assertTrue(manager.interrupt(mob));assertFalse(manager.interrupt(mob));resolve();assertEquals(20,playerHp);verify(player,never()).damage(anyDouble(),any(Entity.class));
    }
    @Test void bossWindupsIgnoreForcedInterrupts(){
        start(MobAbility.STOMP);allowDamage();windup();when(mobs.getMobData("test").isBoss()).thenReturn(true);assertFalse(manager.interrupt(mob));resolve();assertEquals(14,playerHp);
    }

}

package com.customrpg.races;

import com.customrpg.CustomRPG;
import com.customrpg.managers.ManaManager;
import com.customrpg.utils.SkillDamageGuard;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.*;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RacialSkillManagerTest {
    CustomRPG plugin; RaceManager races; ManaManager mana; Server server; World world;
    Player player; Zombie enemy; RacialSkillManager skills; BukkitScheduler scheduler;
    Map<NamespacedKey,Object> tags, enemyTags;
    Map<NamespacedKey,AttributeModifier> modifiers;
    AtomicLong clock = new AtomicLong(1000);
    double hp = 5, enemyHp = 20;
    boolean allowFlight, flying;

    static PersistentDataContainer data(Map<NamespacedKey,Object> map) {
        var data = mock(PersistentDataContainer.class);
        doAnswer(i -> { map.put(i.getArgument(0), i.getArgument(2)); return null; }).when(data).set(any(), any(), any());
        when(data.get(any(), any())).thenAnswer(i -> map.get(i.getArgument(0)));
        when(data.has(any(), any())).thenAnswer(i -> map.containsKey(i.getArgument(0)));
        doAnswer(i -> { map.remove(i.getArgument(0)); return null; }).when(data).remove(any());
        return data;
    }
    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class); races = mock(RaceManager.class); mana = mock(ManaManager.class);
        when(plugin.getRaceManager()).thenReturn(races); when(plugin.getManaManager()).thenReturn(mana);
        when(mana.consumeMana(any(), anyDouble())).thenReturn(true);
        server = mock(Server.class); when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        scheduler = mock(BukkitScheduler.class); when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        world = mock(World.class);
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.isOnGround()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenAnswer(i -> new Location(world, 0, 64, 0));
        when(player.getEyeLocation()).thenAnswer(i -> new Location(world, 0, 65.6, 0));
        when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        tags = new HashMap<>(); var playerData = data(tags); when(player.getPersistentDataContainer()).thenReturn(playerData);
        when(player.getAllowFlight()).thenAnswer(i -> allowFlight);
        when(player.isFlying()).thenAnswer(i -> flying);
        doAnswer(i -> { allowFlight = i.getArgument(0); return null; }).when(player).setAllowFlight(anyBoolean());
        doAnswer(i -> { flying = i.getArgument(0); return null; }).when(player).setFlying(anyBoolean());
        when(player.getMaxHealth()).thenReturn(20.0);
        when(player.getHealth()).thenAnswer(i -> hp);
        doAnswer(i -> { hp = i.getArgument(0); return null; }).when(player).setHealth(anyDouble());
        var health = mock(AttributeInstance.class); when(health.getValue()).thenReturn(20.0);
        var movement = mock(AttributeInstance.class); modifiers = new HashMap<>();
        when(movement.getModifiers()).thenAnswer(i -> new ArrayList<>(modifiers.values()));
        doAnswer(i -> { var m = (AttributeModifier)i.getArgument(0); modifiers.put(m.getKey(), m); return null; }).when(movement).addTransientModifier(any());
        doAnswer(i -> { modifiers.remove(((AttributeModifier)i.getArgument(0)).getKey()); return null; }).when(movement).removeModifier(any(AttributeModifier.class));
        enemy = mock(Zombie.class); when(enemy.getHealth()).thenAnswer(i -> enemyHp);
        when(enemy.getLocation()).thenAnswer(i -> new Location(world, 0, 64, 3));
        enemyTags = new HashMap<>(); var enemyData = data(enemyTags); when(enemy.getPersistentDataContainer()).thenReturn(enemyData);
        skills = new RacialSkillManager(plugin, clock::get, p -> movement, p -> health);
    }
    void race(String id) { when(races.getPlayerRaceData(player)).thenReturn(new RaceData.Builder(id).build()); }
    EntityDamageByEntityEvent attack(Entity attacker, LivingEntity target, double damage) {
        var event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker); when(event.getEntity()).thenReturn(target);
        final double[] amount = {damage}; when(event.getDamage()).thenAnswer(i -> amount[0]);
        when(event.getFinalDamage()).thenAnswer(i -> amount[0]);
        doAnswer(i -> { amount[0] = i.getArgument(0); return null; }).when(event).setDamage(anyDouble());
        return event;
    }
    Runnable timer() {
        skills.start(); var capture = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), capture.capture(), eq(5L), eq(5L));
        return capture.getValue();
    }
    @Test void allTwelveHaveDistinctSkillsAndOldPlannedIdsAreNotExecutable() {
        assertEquals(12, RacialSkill.values().length);
        assertEquals(12, Arrays.stream(RacialSkill.values()).map(RacialSkill::raceId).distinct().count());
        assertEquals(12, Arrays.stream(RacialSkill.values()).map(RacialSkill::displayName).distinct().count());
        assertSame(RacialSkill.DRAGONKIN, RacialSkill.forRace("DRAGONKIN"));
        assertNull(RacialSkill.forRace("unimplemented_custom_race"));
    }
    @Test void cooldownSurvivesNewManagerLogoutAndRaceChange() {
        race("human"); assertTrue(skills.cast(player)); assertEquals(45, skills.remainingSeconds(player));
        skills.onQuit(new PlayerQuitEvent(player, (net.kyori.adventure.text.Component)null, null));
        race("orc");
        var restarted = new RacialSkillManager(plugin, clock::get, p -> null, p -> null);
        assertFalse(restarted.cast(player)); verify(mana, times(1)).consumeMana(any(), anyDouble());
        clock.addAndGet(45000); assertTrue(restarted.cast(player));
    }
    @Test void insufficientManaAndMissingRaceDoNotWriteCooldownOrFlight() {
        race("dragonkin"); when(mana.consumeMana(any(), anyDouble())).thenReturn(false);
        assertFalse(skills.cast(player)); assertTrue(tags.isEmpty()); assertFalse(allowFlight);
        when(races.getPlayerRaceData(player)).thenReturn(null); assertFalse(skills.cast(player));
    }
    @Test void dragonWingsExpireAndRestoreGrantedFlightWithoutTouchingEquipment() {
        race("dragonkin"); Runnable tick = timer(); assertTrue(skills.cast(player)); assertTrue(allowFlight && flying);
        tick.run(); verify(world, times(40)).spawnParticle(eq(Particle.DUST), any(Location.class), eq(1), eq(0.0), eq(0.0), eq(0.0), eq(0.0), any(Particle.DustOptions.class));
        clock.addAndGet(12000); tick.run(); assertFalse(allowFlight || flying); assertFalse(tags.containsKey(RacialSkillManager.FLIGHT_GRANTED));
        verify(player, never()).getInventory(); assertEquals(48, skills.remainingSeconds(player));
    }
    @Test void preexistingFlightAndCreativeFlightArePreserved() {
        allowFlight = true; flying = true; race("dragonkin"); assertTrue(skills.cast(player)); skills.cancel(player);
        assertTrue(allowFlight && flying); verify(player, never()).setAllowFlight(false);
        tags.put(RacialSkillManager.FLIGHT_GRANTED, (byte)1); when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        skills.cancel(player); assertTrue(allowFlight); assertFalse(tags.containsKey(RacialSkillManager.FLIGHT_GRANTED));
    }
    @Test void staleFlightGrantIsRecoveredOnLogin() {
        tags.put(RacialSkillManager.FLIGHT_GRANTED, (byte)1); allowFlight = true; flying = true;
        skills.onJoin(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component)null));
        assertFalse(allowFlight || flying); assertFalse(tags.containsKey(RacialSkillManager.FLIGHT_GRANTED));
    }
    @Test void raceChangeAndTeleportRemoveActiveFlightImmediately() {
        race("dragonkin"); skills.cast(player); race("human");
        var event = attack(player, enemy, 10); skills.onDamage(event); assertFalse(allowFlight); assertEquals(10, event.getDamage());
        tags.remove(RacialSkillManager.READY_AT); race("dragonkin"); skills.cast(player);
        var teleport = mock(PlayerTeleportEvent.class); when(teleport.getPlayer()).thenReturn(player);
        skills.onTeleport(teleport); assertFalse(allowFlight);
    }
    @Test void flightEndingInAirProvidesControlledDescentAndOneFallProtection() {
        race("dragonkin"); Runnable tick = timer(); skills.cast(player); when(player.isOnGround()).thenReturn(false);
        when(server.getPlayer(player.getUniqueId())).thenReturn(player); when(player.getVelocity()).thenReturn(new Vector(0, -1, 0));
        skills.cancel(player); tick.run(); verify(player).setVelocity(new Vector(0, -0.35, 0));
        var fall = mock(EntityDamageEvent.class); when(fall.getEntity()).thenReturn(player); when(fall.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        skills.onDamage(fall); verify(fall).setCancelled(true);
        var second = mock(EntityDamageEvent.class); when(second.getEntity()).thenReturn(player); when(second.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        skills.onDamage(second); verify(second, never()).setCancelled(true);
    }
    @Test void airborneLogoutRetainsLandingProtectionOnReconnect() {
        race("dragonkin"); skills.cast(player); when(player.isOnGround()).thenReturn(false);
        skills.onQuit(new PlayerQuitEvent(player, (net.kyori.adventure.text.Component)null, null));
        assertFalse(allowFlight); assertTrue(tags.containsKey(RacialSkillManager.LANDING_UNTIL));
        skills.onJoin(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component)null));
        var fall = mock(EntityDamageEvent.class); when(fall.getEntity()).thenReturn(player);
        when(fall.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        skills.onDamage(fall); verify(fall).setCancelled(true); assertFalse(tags.containsKey(RacialSkillManager.LANDING_UNTIL));
    }
    @ParameterizedTest @CsvSource({"human,12.0", "orc,13.5", "elf,10.0", "dragonkin,14.0"})
    void offensiveAbilityModifiesOrdinaryMeleeOnce(String id, double expected) {
        race(id); skills.cast(player); var event = attack(player, enemy, 10); skills.onDamage(event); assertEquals(expected, event.getDamage(), 1e-9);
    }
    @Test void elfBoostsProjectilesAndGuardedSkillDamageDoesNotReprocRacialAttack() {
        race("elf"); skills.cast(player); Arrow arrow = mock(Arrow.class); when(arrow.getShooter()).thenReturn(player);
        var shot = attack(arrow, enemy, 10); skills.onDamage(shot); assertEquals(12.5, shot.getDamage());
        doAnswer(i -> { var event = attack(player, enemy, 10); skills.onDamage(event); assertEquals(10, event.getDamage()); return null; })
                .when(enemy).damage(anyDouble(), eq(player));
        SkillDamageGuard.damage(enemy, 10, player);
    }
    @ParameterizedTest @CsvSource({"dwarf,6.5", "angel,8.0", "dark_elf,8.0", "golem,7.5", "orc,11.5"})
    void defensiveAbilitiesApplyBeforeVirtualHealthConversion(String id, double expected) {
        race(id); skills.cast(player); var event = attack(enemy, player, 10); skills.onDamage(event); assertEquals(expected, event.getDamage(), 1e-9);
    }
    @Test void oneShotAttackBuffIsNotSpentWhenProtectionCancels() {
        race("beastkin"); skills.cast(player);
        var cancelled = attack(player, enemy, 10); skills.onDamage(cancelled); assertEquals(16, cancelled.getDamage());
        when(cancelled.isCancelled()).thenReturn(true); skills.afterDamage(cancelled);
        var hit = attack(player, enemy, 10); skills.onDamage(hit); assertEquals(16, hit.getDamage()); skills.afterDamage(hit);
        var next = attack(player, enemy, 10); skills.onDamage(next); assertEquals(10, next.getDamage());
    }
    @Test void halflingDodgesOneDirectHitAndOnlyOwnSpeedIsRemoved() {
        var foreign = new AttributeModifier(new NamespacedKey("other", "speed"), 0.1, AttributeModifier.Operation.ADD_SCALAR);
        modifiers.put(foreign.getKey(), foreign); race("halfling"); skills.cast(player); assertEquals(2, modifiers.size());
        var first = attack(enemy, player, 10); skills.onDamage(first); verify(first).setCancelled(true);
        var second = attack(enemy, player, 10); skills.onDamage(second); verify(second, never()).setCancelled(true);
        skills.cancel(player); assertEquals(Map.of(foreign.getKey(), foreign), modifiers);
    }
    @Test void darkElfAmbushIsUsedOnceWhileDefensiveShroudContinues() {
        race("dark_elf"); skills.cast(player);
        var first = attack(player, enemy, 10); skills.onDamage(first); assertEquals(15, first.getDamage()); skills.afterDamage(first);
        var next = attack(player, enemy, 10); skills.onDamage(next); assertEquals(10, next.getDamage());
        var incoming = attack(enemy, player, 10); skills.onDamage(incoming); assertEquals(8, incoming.getDamage());
    }
    @Test void golemShockwaveKnocksBackOnlyAfterAnEffectiveHit() {
        race("golem"); when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(enemy));
        when(player.hasLineOfSight(enemy)).thenReturn(true);
        doAnswer(i -> { enemyHp -= 8; return null; }).when(enemy).damage(8, player);
        assertTrue(skills.cast(player)); verify(enemy).setVelocity(new Vector(0, 0.4, 0.7));
    }
    @Test void angelHealsVisibleNearbyTeammatesAndDoesNotHealUnrelatedPlayers() {
        race("angel"); when(player.getName()).thenReturn("caster");
        var board = mock(org.bukkit.scoreboard.Scoreboard.class); var team = mock(org.bukkit.scoreboard.Team.class);
        when(player.getScoreboard()).thenReturn(board); when(board.getEntryTeam("caster")).thenReturn(team); when(team.hasEntry("ally")).thenReturn(true);
        Player ally = mock(Player.class), stranger = mock(Player.class);
        when(ally.getName()).thenReturn("ally"); when(stranger.getName()).thenReturn("stranger");
        when(ally.getLocation()).thenReturn(new Location(world, 0, 64, 2)); when(ally.getHealth()).thenReturn(5.0); when(ally.getMaxHealth()).thenReturn(20.0);
        when(player.hasLineOfSight(ally)).thenReturn(true); when(world.getPlayers()).thenReturn(List.of(player, ally, stranger));
        assertTrue(skills.cast(player)); assertEquals(7.4, hp,1e-9); verify(ally).setHealth(7.4); verify(stranger, never()).setHealth(anyDouble());
    }
    @Test void blockedDrainCostsNothingAndCancelledDamageDoesNotHeal() {
        race("undead"); assertFalse(skills.cast(player)); verifyNoInteractions(mana);
        when(world.rayTrace(any(), any(), eq(12.0), any(), eq(true), eq(0.35), any())).thenReturn(new RayTraceResult(new Vector(), enemy));
        assertTrue(skills.cast(player)); assertEquals(5, hp); verify(enemy).damage(12, player);
    }
    @Test void drainHealsOnlyActualDamageAndHealingCancellationIsRespected() {
        race("undead"); when(world.rayTrace(any(), any(), eq(12.0), any(), eq(true), eq(0.35), any())).thenReturn(new RayTraceResult(new Vector(), enemy));
        doAnswer(i -> { assertTrue(SkillDamageGuard.isSkillDamage(player)); enemyHp -= 3; return null; }).when(enemy).damage(12, player);
        assertTrue(skills.cast(player)); assertEquals(8, hp);
        tags.remove(RacialSkillManager.READY_AT);
        var events = server.getPluginManager();
        doAnswer(i -> { if (i.getArgument(0) instanceof EntityRegainHealthEvent heal) heal.setCancelled(true); return null; })
                .when(events).callEvent(any());
        skills.cast(player); assertEquals(8, hp);
    }
    @Test void demonDoesNotDamageThroughWallsOrApplyFireWhenDamageWasCancelled() {
        race("demon"); when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(enemy));
        skills.cast(player); verify(enemy, never()).damage(anyDouble(), any(Entity.class));
        tags.remove(RacialSkillManager.READY_AT); when(player.hasLineOfSight(enemy)).thenReturn(true);
        skills.cast(player); verify(enemy).damage(10, player); verify(enemy, never()).setFireTicks(anyInt());
    }
    @Test void dragonFireDoesNotIgniteWhenProtectionCancelsButWorksWithVirtualDamage() {
        race("dragonkin"); skills.cast(player);
        var cancelled = attack(player, enemy, 10); skills.onDamage(cancelled); when(cancelled.isCancelled()).thenReturn(true);
        skills.afterDamage(cancelled); verify(enemy, never()).setFireTicks(anyInt());
        var max = new NamespacedKey("customrpg", "virtual_max_hp"); var current = new NamespacedKey("customrpg", "virtual_current_hp");
        enemyTags.put(max, 1000.0); enemyTags.put(current, 1000.0);
        var hit = attack(player, enemy, 10); skills.onDamage(hit); when(hit.isCancelled()).thenReturn(true);
        enemyTags.put(current, 900.0); skills.afterDamage(hit); verify(enemy).setFireTicks(60);
    }
}

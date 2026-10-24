package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.*;
import com.customrpg.talents.*;
import com.customrpg.utils.SkillDamageGuard;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssassinSkillTest {
    CustomRPG plugin; TalentManager talents; TalentSkillManager skills; Player source, target;
    World world; Location sourceAt; Talent talent; ItemStack sword;
    PlayerTalents selection; BukkitScheduler scheduler; MockedStatic<Bukkit> bukkit;
    List<BukkitRunnable> animations; List<Entity> nearby;
    AtomicReference<Double> health; AtomicInteger hurtFrames;
    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class); talents = mock(TalentManager.class); when(plugin.getTalentManager()).thenReturn(talents);
        var statsManager = mock(PlayerStatsManager.class); when(plugin.getPlayerStatsManager()).thenReturn(statsManager);
        var stats = new PlayerStats(); stats.setStrength(0); stats.setAgility(0); stats.setSpirit(0);
        when(statsManager.getStats(any(Player.class))).thenReturn(stats);
        var mana = mock(ManaManager.class); when(plugin.getManaManager()).thenReturn(mana); when(mana.hasMana(any(), anyDouble())).thenReturn(true);
        when(plugin.getWeaponManager()).thenReturn(mock(WeaponManager.class));
        when(plugin.getCombatBalance()).thenReturn(mock(com.customrpg.balance.CombatBalance.class));
        world = mock(World.class); when(world.getPVP()).thenReturn(true);
        source = mock(Player.class); sourceAt = new Location(world, 0, 64, 0);
        target = mock(Player.class); health = new AtomicReference<>(100.0); hurtFrames = new AtomicInteger();
        for (Player p : List.of(source, target)) {
            when(p.getUniqueId()).thenReturn(UUID.randomUUID()); when(p.getWorld()).thenReturn(world);
            when(p.isOnline()).thenReturn(true); when(p.isValid()).thenReturn(true); when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(p.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class)); when(p.getMaxHealth()).thenReturn(100.0);
        }
        when(source.getLocation()).thenAnswer(i -> sourceAt.clone()); when(source.getEyeLocation()).thenAnswer(i -> sourceAt.clone().add(0, 1.6, 0));
        when(source.hasLineOfSight(any(Entity.class))).thenReturn(true);
        when(source.teleport(any(Location.class))).thenAnswer(i -> { sourceAt = ((Location)i.getArgument(0)).clone(); return true; });
        when(target.getLocation()).thenAnswer(i -> new Location(world, 0, 64, 2));
        when(target.getHealth()).thenAnswer(i -> health.get()); when(source.getHealth()).thenReturn(50.0);
        when(target.getNoDamageTicks()).thenAnswer(i -> hurtFrames.get());
        doAnswer(i -> { hurtFrames.set(i.getArgument(0)); return null; }).when(target).setNoDamageTicks(anyInt());
        doAnswer(i -> {
            assertTrue(SkillDamageGuard.isSkillDamage(source));
            // A deliberately simple hurt-frame fixture, not a replacement for live Paper testing.
            if (hurtFrames.get() == 0) { health.updateAndGet(h -> h - (double)i.getArgument(0)); hurtFrames.set(20); }
            return null;
        }).when(target).damage(anyDouble(), eq(source));
        sword = mock(ItemStack.class); when(sword.getType()).thenReturn(Material.DIAMOND_SWORD); when(sword.clone()).thenReturn(sword);
        var inventory = mock(PlayerInventory.class); when(source.getInventory()).thenReturn(inventory); when(inventory.getItemInMainHand()).thenReturn(sword);
        nearby = new ArrayList<>(List.of(target));
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenAnswer(i -> new ArrayList<>(nearby));
        when(source.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenAnswer(i -> new ArrayList<>(nearby));
        selection = new PlayerTalents(); when(talents.getPlayerTalents(source)).thenReturn(selection);
        talent = mock(Talent.class); when(talent.getType()).thenReturn(TalentType.ACTIVE); when(talent.getBranch()).thenReturn(TalentBranch.ASSASSIN);
        when(talent.getEffectDouble(anyInt(), anyString(), anyDouble())).thenAnswer(i -> i.getArgument(2));
        when(talent.getLevelData(1)).thenReturn(new Talent.TalentLevelData()); when(talents.findTalent(anyString())).thenReturn(talent);
        scheduler = mock(BukkitScheduler.class); animations = new ArrayList<>();
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(i -> {
            animations.add((BukkitRunnable)i.getArgument(1)); var task = mock(BukkitTask.class); when(task.getTaskId()).thenReturn(animations.size()); return task;
        });
        skills = new TalentSkillManager(plugin);
    }
    @AfterEach void teardown() { skills.shutdownAssassin(); bukkit.close(); }
    boolean cast(String id) {
        when(talent.getId()).thenReturn(id); selection.setTalentLevel(id, 1); selection.setSelectedSkill(0, id);
        return skills.castSkillDirectly(source, id, sword);
    }
    void ticks(int count) { for (int i = 0; i < count; i++) for (var task : new ArrayList<>(animations)) task.run(); }

    @ParameterizedTest @ValueSource(strings = {"rapid_slash", "cross_blade", "arc_slash", "crescent_shadow", "death_god", "phantom_massacre"})
    void damagingSkillsAcceptEligibleEnemyPlayers(String id) {
        hurtFrames.set(16); // A recent weapon hit must not suppress the first skill strike.
        assertTrue(cast(id)); ticks(id.equals("death_god") ? 20 : 1);
        assertEquals(20, hurtFrames.get());
        assertTrue(health.get() < 100, id + " must deliver attributed skill damage to a player");
        assertFalse(SkillDamageGuard.isSkillDamage(source));
    }
    @Test void rapidSlashDeliversAllThreeHitsDespiteHurtFramesAndRestoresTheirProtection() {
        assertTrue(cast("rapid_slash")); ticks(3);
        assertEquals(89.5, health.get(), 1e-8); assertEquals(20, hurtFrames.get());
        verify(target, times(3)).damage(3.5, source);
    }
    @Test void cancelledDamageStillDoesNoDamageAndRestoresHurtFrames() {
        hurtFrames.set(20);
        doAnswer(i -> { assertEquals(0, hurtFrames.get()); return null; }).when(target).damage(anyDouble(), eq(source));
        assertTrue(cast("rapid_slash")); ticks(3);
        assertEquals(100, health.get(), 1e-8); assertEquals(20, hurtFrames.get());
        assertFalse(SkillDamageGuard.isSkillDamage(source));
    }
    @Test void anotherValidCastHasIndependentHitTimingWhileRetainingSharedDamageEvents() {
        assertTrue(cast("rapid_slash")); var first = animations.getFirst(); first.run();
        assertEquals(96.5, health.get(), 1e-8);
        assertTrue(cast("rapid_slash")); animations.getLast().run(); assertEquals(93, health.get(), 1e-8);
        verify(target, times(2)).damage(3.5, source); assertEquals(20, hurtFrames.get());
    }
    @Test void repeatedMassacreHitsAreNotSilentlyDropped() {
        assertTrue(cast("phantom_massacre")); ticks(4);
        assertEquals(100 - 4 * 6.3, health.get(), 1e-8); assertEquals(20, hurtFrames.get());
    }
    @Test void pvpOffPreventsPlayerDamage() {
        when(world.getPVP()).thenReturn(false); assertTrue(cast("rapid_slash")); ticks(3);
        verify(target, never()).damage(anyDouble(), eq(source));
    }
    @Test void sameTeamIsNotAnEnemy() {
        var board = mock(Scoreboard.class); var team = mock(Team.class);
        when(source.getName()).thenReturn("source"); when(target.getName()).thenReturn("target");
        when(source.getScoreboard()).thenReturn(board); when(board.getEntryTeam("source")).thenReturn(team); when(team.hasEntry("target")).thenReturn(true);
        assertTrue(cast("rapid_slash")); ticks(3); assertEquals(100, health.get(), 1e-8);
    }
    @Test void protectionAndVisibilityAreRecheckedDuringTheCast() {
        assertTrue(cast("rapid_slash")); ticks(1); assertEquals(96.5, health.get(), 1e-8);
        when(source.hasLineOfSight(target)).thenReturn(false); ticks(1); assertEquals(96.5, health.get(), 1e-8);
        when(source.hasLineOfSight(target)).thenReturn(true);
        var safe = mock(SafeZoneManager.class); when(plugin.getSafeZoneManager()).thenReturn(safe);
        when(safe.isInSafeZone(any(Location.class))).thenReturn(true); ticks(1); assertEquals(96.5, health.get(), 1e-8);
    }
    @ParameterizedTest @ValueSource(strings = {"logout", "world", "death"})
    void ongoingAttackStopsAfterSourceBecomesInvalid(String reason) {
        assertTrue(cast("rapid_slash"));
        if (reason.equals("logout")) when(source.isOnline()).thenReturn(false);
        if (reason.equals("death")) when(source.isDead()).thenReturn(true);
        if (reason.equals("world")) when(source.getWorld()).thenReturn(mock(World.class));
        ticks(1); assertEquals(100, health.get(), 1e-8); verify(scheduler).cancelTask(anyInt());
    }
    @Test void interruptedMassacreRestoresTheOriginalInvulnerabilityFlag() {
        when(source.isInvulnerable()).thenReturn(true); assertTrue(cast("phantom_massacre"));
        when(source.isOnline()).thenReturn(false); ticks(1); verify(source, times(2)).setInvulnerable(true);
        verify(source, never()).setInvulnerable(false);
    }
    @Test void shutdownRestoresMassacreProtectionWithoutChangingPriorInvulnerability() {
        assertTrue(cast("phantom_massacre")); skills.shutdownAssassin(); verify(source).setInvulnerable(false);
    }
    @Test void shadowAssaultCannotTeleportIntoAProtectedDestination() {
        var safe = mock(SafeZoneManager.class); when(plugin.getSafeZoneManager()).thenReturn(safe);
        when(safe.isInSafeZone(any(Location.class))).thenAnswer(i -> Math.abs(((Location)i.getArgument(0)).getZ() - .5) < .01);
        assertFalse(cast("shadow_assault")); verify(source, never()).teleport(any(Location.class));
        verify(target, never()).damage(anyDouble(), eq(source));
    }
    @Test void deadOrCreativePlayerTargetsCannotBeDamagedByAnOngoingAttack() {
        assertTrue(cast("rapid_slash")); when(target.isDead()).thenReturn(true); ticks(1);
        when(target.isDead()).thenReturn(false); when(target.getGameMode()).thenReturn(GameMode.CREATIVE); ticks(1);
        assertEquals(100, health.get(), 1e-8);
    }
    @Test void rejectedShadowTeleportCannotClaimADamagingBackstab() {
        when(source.teleport(any(Location.class))).thenReturn(false); assertFalse(cast("shadow_assault"));
        verify(target, never()).damage(anyDouble(), eq(source));
    }
}

package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.*;
import com.customrpg.talents.*;
import com.customrpg.utils.SkillDamageGuard;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.*;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.*;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpandedTalentSkillsTest {
    CustomRPG plugin; TalentManager talents; ExpandedTalentSkills skills; Player player; LivingEntity enemy;
    World world; Server server; PluginManager events; AtomicLong clock = new AtomicLong(1000);
    Runnable tick; double hp = 100, playerHp = 10; PlayerTalents learned; List<LivingEntity> nearby = new ArrayList<>();
    ExpandedTalentSkills.StatusEffects status;
    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class); server = mock(Server.class); when(plugin.getServer()).thenReturn(server);
        events = mock(PluginManager.class); when(server.getPluginManager()).thenReturn(events);
        var scheduler = mock(BukkitScheduler.class); when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(5L), eq(5L))).thenReturn(mock(BukkitTask.class));
        talents = mock(TalentManager.class); when(plugin.getTalentManager()).thenReturn(talents);
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL); world = mock(World.class); when(player.getWorld()).thenReturn(world);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(player.getLocation()).thenAnswer(i -> new Location(world, 0, 64, 0));
        when(player.getEyeLocation()).thenAnswer(i -> new Location(world, 0, 65.6, 0));
        when(player.hasLineOfSight(any(Entity.class))).thenReturn(true); when(player.getHealth()).thenAnswer(i -> playerHp);
        when(player.getMaxHealth()).thenReturn(20.0); doAnswer(i -> { playerHp = i.getArgument(0); return null; }).when(player).setHealth(anyDouble());
        var stats = mock(PlayerStatsManager.class); when(plugin.getPlayerStatsManager()).thenReturn(stats); when(stats.getStats(player)).thenReturn(new PlayerStats());
        learned = new PlayerTalents(); when(talents.getPlayerTalents(player)).thenReturn(learned);
        enemy = monster(0, 2); nearby.add(enemy);
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenAnswer(i -> new ArrayList<>(nearby));
        when(world.getNearbyPlayers(any(Location.class), anyDouble())).thenReturn(List.of(player));
        status = mock(ExpandedTalentSkills.StatusEffects.class);
        skills = new ExpandedTalentSkills(plugin, clock::get, status); skills.start();
        var task = ArgumentCaptor.forClass(Runnable.class); verify(scheduler).runTaskTimer(eq(plugin), task.capture(), eq(5L), eq(5L)); tick = task.getValue();
    }
    LivingEntity monster(double x, double z) {
        var target = mock(Zombie.class); when(target.getUniqueId()).thenReturn(UUID.randomUUID()); when(target.isValid()).thenReturn(true);
        when(target.getWorld()).thenReturn(world); when(target.getLocation()).thenAnswer(i -> new Location(world, x, 64, z));
        when(target.getEyeLocation()).thenAnswer(i -> new Location(world, x, 65.5, z));
        when(target.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(target.getHealth()).thenAnswer(i -> hp); when(target.hasLineOfSight(any(Entity.class))).thenReturn(true);
        doAnswer(i -> { assertTrue(SkillDamageGuard.isSkillDamage(player)); hp -= (double)i.getArgument(0); return null; }).when(target).damage(anyDouble(), eq(player));
        return target;
    }
    Talent talent(String id) {
        TalentBranch branch = TalentBranch.valueOf(id.substring(0, id.indexOf('_')).toUpperCase(Locale.ROOT));
        var t = new Talent(id, id, "test", TalentType.ACTIVE, branch, 3, 1, List.of(), Talent.PrerequisiteMode.ALL, 28, "BOOK", "HOTKEY", 5, 10, 0, "HOTKEY");
        var data = new Talent.TalentLevelData(); data.addEffect("baseDamage", 6); data.addEffect("baseHeal", 4);
        data.addEffect("duration", 5); data.addEffect("radius", 4); data.addEffect("range", 14);
        t.addLevelData(1, data); learned.setTalentLevel(id, 1); return t;
    }
    void aimed(LivingEntity target) {
        var result = new RayTraceResult(target.getEyeLocation().toVector(), target);
        when(world.rayTrace(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true), anyDouble(), any()))
                .thenReturn(result);
    }
    @ParameterizedTest @ValueSource(strings = {"frost_shard", "frost_lance", "light_lance", "storm_arc"})
    void targetedSpellsRequireAnActualTargetBeforeResourcesCanBeSpent(String id) {
        var talent = talent(id); assertFalse(skills.cast(player, talent, 1));
        aimed(enemy); assertTrue(skills.cast(player, talent, 1)); verify(enemy).damage(anyDouble(), eq(player));
    }
    @Test void successfulIceHitSlowsButCancelledDamageHasNoFollowUp() {
        var t = talent("frost_shard"); aimed(enemy); assertTrue(skills.cast(player, t, 1)); verify(status).slow(eq(enemy), anyInt(), anyInt());
        clearInvocations(status); doNothing().when(enemy).damage(anyDouble(), eq(player));
        skills.cast(player, t, 1); verify(status, never()).slow(any(), anyInt(), anyInt());
    }
    @Test void chainHitsDistinctEnemiesWithFalloffAndStopsOnCancelledFirstHit() {
        var second = monster(1, 3); nearby.add(second); aimed(enemy);
        skills.cast(player, talent("storm_arc"), 1); verify(enemy).damage(6, player); verify(second).damage(doubleThat(d -> Math.abs(d - 4.8) < .0001), eq(player));
        clearInvocations(enemy, second); doNothing().when(enemy).damage(anyDouble(), eq(player));
        skills.cast(player, talent("storm_arc"), 1); verify(second, never()).damage(anyDouble(), any(Entity.class));
    }
    @ParameterizedTest @ValueSource(strings = {"frost_nova", "storm_gust", "earth_shockwave"})
    void areaMovesActuallyDamageEligibleEnemies(String id) {
        assertTrue(skills.cast(player, talent(id), 1)); verify(enemy).damage(6, player);
    }
    @Test void wallsSafeZonesAndTamedPetsCannotBeHitByAreaMoves() {
        var safe = mock(SafeZoneManager.class); when(plugin.getSafeZoneManager()).thenReturn(safe); when(safe.isInSafeZone(any())).thenReturn(true);
        skills.cast(player, talent("frost_nova"), 1); verify(enemy, never()).damage(anyDouble(), any(Entity.class));
        when(safe.isInSafeZone(any())).thenReturn(false); when(player.hasLineOfSight(enemy)).thenReturn(false);
        skills.cast(player, talent("frost_nova"), 1); verify(enemy, never()).damage(anyDouble(), any(Entity.class));
        var pet = mock(Wolf.class); when(pet.isTamed()).thenReturn(true); when(pet.isValid()).thenReturn(true); when(pet.getWorld()).thenReturn(world);
        when(pet.getLocation()).thenReturn(new Location(world, 0, 64, 1)); nearby.clear(); nearby.add(pet);
        skills.cast(player, talent("frost_nova"), 1); verify(pet, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void groundGraspRequiresBlockTargetAndAppliesControlOnlyAfterDamage() {
        var t = talent("earth_grasp"); assertFalse(skills.cast(player, t, 1));
        var location = new Vector(0, 63.9, 2); var ray = new RayTraceResult(location, mock(Block.class), org.bukkit.block.BlockFace.UP);
        when(world.rayTraceBlocks(any(), any(), eq(14.0), eq(FluidCollisionMode.NEVER), eq(true))).thenReturn(ray);
        assertTrue(skills.cast(player, t, 1)); verify(enemy).damage(6, player); verify(status).slow(eq(enemy), anyInt(), anyInt());
    }
    @ParameterizedTest @ValueSource(strings = {"frost_blizzard", "storm_tempest"})
    void fieldsPulseOncePerSecondAndNeverReplayLaggedPulses(String id) {
        skills.cast(player, talent(id), 1); verify(enemy, never()).damage(anyDouble(), any(Entity.class));
        clock.set(2000); tick.run(); verify(enemy).damage(6, player);
        tick.run(); verify(enemy, times(1)).damage(anyDouble(), any(Entity.class));
        clock.set(4500); tick.run(); verify(enemy, times(2)).damage(anyDouble(), any(Entity.class));
        clock.set(8000); tick.run(); verify(enemy, times(2)).damage(anyDouble(), any(Entity.class));
    }
    @Test void teleportAndTalentResetCancelPendingFields() {
        skills.cast(player, talent("frost_blizzard"), 1);
        var teleport = mock(PlayerTeleportEvent.class); when(teleport.getPlayer()).thenReturn(player); skills.onTeleport(teleport);
        clock.set(2000); tick.run(); verify(enemy, never()).damage(anyDouble(), any(Entity.class));
        skills.cast(player, talent("storm_tempest"), 1); learned.resetAllTalents(); clock.set(3000); tick.run(); verify(enemy, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void guardAndBastionUseStrongestReductionAndExpireWithoutBlockingVoid() {
        var guard = talent("earth_guard"); guard.getLevelData(1).addEffect("reduction", .3);
        var field = talent("earth_bastion"); field.getLevelData(1).addEffect("reduction", .2);
        skills.cast(player, guard, 1); skills.cast(player, field, 1);
        var hit = mock(EntityDamageEvent.class); when(hit.getEntity()).thenReturn(player); when(hit.getDamage()).thenReturn(10.0);
        when(hit.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK); skills.onDamage(hit); verify(hit).setDamage(7);
        clearInvocations(hit); when(hit.getCause()).thenReturn(EntityDamageEvent.DamageCause.VOID); skills.onDamage(hit); verify(hit, never()).setDamage(anyDouble());
        when(hit.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK); clock.set(7000); skills.onDamage(hit); verify(hit, never()).setDamage(anyDouble());
    }
    @Test void healingRespectsCancellationAndFullHealthAvoidsACast() {
        var t = talent("light_mend"); assertTrue(skills.cast(player, t, 1)); assertEquals(14, playerHp);
        doAnswer(i -> { ((EntityRegainHealthEvent)i.getArgument(0)).setCancelled(true); return null; }).when(events).callEvent(any(EntityRegainHealthEvent.class));
        assertFalse(skills.cast(player, t, 1)); assertEquals(14, playerHp);
        playerHp = 20; assertFalse(skills.cast(player, t, 1));
    }
    Player teammate() {
        var friend = mock(Player.class); when(friend.getUniqueId()).thenReturn(UUID.randomUUID()); when(friend.getName()).thenReturn("friend");
        when(friend.isOnline()).thenReturn(true); when(friend.isValid()).thenReturn(true); when(friend.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(friend.getWorld()).thenReturn(world); when(friend.getLocation()).thenAnswer(i -> new Location(world, 0, 64, 2));
        when(friend.getEyeLocation()).thenAnswer(i -> new Location(world, 0, 65.6, 2)); when(friend.getHealth()).thenReturn(10.0); when(friend.getMaxHealth()).thenReturn(20.0);
        when(player.getName()).thenReturn("caster"); var board = mock(Scoreboard.class); var team = mock(Team.class);
        when(player.getScoreboard()).thenReturn(board); when(board.getEntryTeam("caster")).thenReturn(team); when(team.hasEntry("friend")).thenReturn(true);
        return friend;
    }
    @Test void alliedHealingAndBastionProtectOnlyVisiblePlayersOnTheSameTeam() {
        Player friend = teammate(); aimed(friend);
        assertTrue(skills.cast(player, talent("light_mend"), 1)); verify(friend).setHealth(14);
        skills.cast(player, talent("earth_bastion"), 1);
        var hit = mock(EntityDamageEvent.class); when(hit.getEntity()).thenReturn(friend); when(hit.getDamage()).thenReturn(10.0);
        when(hit.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK); skills.onDamage(hit); verify(hit).setDamage(8.5);
        when(player.hasLineOfSight(friend)).thenReturn(false); clearInvocations(hit); skills.onDamage(hit); verify(hit, never()).setDamage(anyDouble());
    }
    @Test void friendlyFireDisabledPvpAndCreativePlayersRemainProtected() {
        Player friend = teammate(); nearby.clear(); nearby.add(friend); when(world.getPVP()).thenReturn(true);
        var talent = talent("frost_nova"); skills.cast(player, talent, 1); verify(friend, never()).damage(anyDouble(), any(Entity.class));
        when(player.getScoreboard().getEntryTeam("caster").hasEntry("friend")).thenReturn(false); when(world.getPVP()).thenReturn(false);
        skills.cast(player, talent, 1); verify(friend, never()).damage(anyDouble(), any(Entity.class));
        when(world.getPVP()).thenReturn(true); when(friend.getGameMode()).thenReturn(GameMode.CREATIVE);
        skills.cast(player, talent, 1); verify(friend, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void bossControlDurationIsHalvedAndChilledTargetsTakeTheLanceBonus() {
        var mobs = mock(MobManager.class); when(plugin.getMobManager()).thenReturn(mobs);
        var boss = mock(MobManager.MobData.class); when(boss.isBoss()).thenReturn(true); when(mobs.getCustomMobKey(enemy)).thenReturn("boss"); when(mobs.getMobData("boss")).thenReturn(boss);
        aimed(enemy); skills.cast(player, talent("frost_shard"), 1); verify(status).slow(enemy, 20, 0);
        when(status.has(enemy, "slowness")).thenReturn(true); clearInvocations(enemy);
        skills.cast(player, talent("frost_lance"), 1); verify(enemy).damage(doubleThat(d -> Math.abs(d - 8.1) < .0001), eq(player));
    }
    @Test void purgeRemovesListedNegativeEffectsWithoutRemovingOtherPotions() {
        var t = talent("light_purge"); playerHp = 20; assertFalse(skills.cast(player, t, 1));
        when(status.has(player, "poison")).thenReturn(true);
        assertTrue(skills.cast(player, t, 1)); verify(status).remove(player, "poison");
        verify(status, never()).remove(player, "speed");
    }
    @Test void sanctuaryHealsItsCasterThenStopsOnShutdown() {
        skills.cast(player, talent("light_sanctuary"), 1); clock.set(2000); tick.run(); assertEquals(14, playerHp);
        skills.shutdown(); clock.set(3000); tick.run(); assertEquals(14, playerHp);
    }
    @Test void dashCannotCrossAnObstacleOrLoadAnUnknownChunkAndHonorsTeleportCancellation() {
        var border = mock(WorldBorder.class); when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));
        when(world.getBlockAt(any(Location.class))).thenReturn(mock(Block.class));
        when(player.collidesAt(any())).thenAnswer(i -> ((Location)i.getArgument(0)).getZ() >= 1.5);
        var t = talent("storm_dash"); when(player.teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenReturn(true);
        assertTrue(skills.cast(player, t, 1));
        var endpoint = ArgumentCaptor.forClass(Location.class); verify(player).teleport(endpoint.capture(), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)); assertEquals(1.25, endpoint.getValue().getZ());
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false); assertFalse(skills.cast(player, t, 1));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true); when(player.teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class))).thenReturn(false);
        assertFalse(skills.cast(player, t, 1));
    }
}

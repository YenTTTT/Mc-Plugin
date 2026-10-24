package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.*;
import com.customrpg.talents.*;
import com.customrpg.utils.SkillDamageGuard;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.*;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HunterTalentSkillsTest {
    CustomRPG plugin; TalentManager talents; Player player; World world; LivingEntity enemy;
    ExpandedTalentSkills combat; ExpandedTalentSkills.StatusEffects status; HunterTalentSkills skills;
    HunterTalentSkills.SpeedEffects speed; PlayerTalents learned = new PlayerTalents(); AtomicLong clock = new AtomicLong(1000);
    List<LivingEntity> nearby = new ArrayList<>(); Map<UUID,Double> health = new HashMap<>();
    Location playerAt, enemyAt; BukkitScheduler scheduler;
    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class); var server = mock(Server.class); when(plugin.getServer()).thenReturn(server);
        scheduler = mock(BukkitScheduler.class); when(server.getScheduler()).thenReturn(scheduler);
        var task = mock(BukkitTask.class); when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(5L), eq(5L))).thenReturn(task);
        talents = mock(TalentManager.class); when(plugin.getTalentManager()).thenReturn(talents);
        when(talents.getPlayerTalents(any(Player.class))).thenReturn(learned);
        var stats = mock(PlayerStatsManager.class); when(plugin.getPlayerStatsManager()).thenReturn(stats);
        when(stats.getStats(any(Player.class))).thenReturn(new PlayerStats());
        world = mock(World.class); when(world.getPVP()).thenReturn(true); when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        var border = mock(WorldBorder.class); when(border.isInside(any())).thenReturn(true); when(world.getWorldBorder()).thenReturn(border);
        playerAt = new Location(world, 0, 64, 0); enemyAt = new Location(world, 0, 64, 2);
        player = caster(); enemy = monster(); nearby.add(enemy);
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble())).thenAnswer(i -> new ArrayList<>(nearby));
        status = mock(ExpandedTalentSkills.StatusEffects.class); combat = new ExpandedTalentSkills(plugin, clock::get, status);
        speed = mock(HunterTalentSkills.SpeedEffects.class); skills = new HunterTalentSkills(plugin, combat, clock::get, speed);
    }
    Player caster() {
        var source = mock(Player.class); when(source.getUniqueId()).thenReturn(UUID.randomUUID());
        when(source.isOnline()).thenReturn(true); when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(source.getWorld()).thenReturn(world); when(source.getLocation()).thenAnswer(i -> playerAt.clone());
        when(source.getEyeLocation()).thenAnswer(i -> playerAt.clone().add(0, 1.6, 0)); when(source.hasLineOfSight(any(Entity.class))).thenReturn(true);
        return source;
    }
    LivingEntity monster() {
        var target = mock(Zombie.class); UUID id = UUID.randomUUID(); health.put(id, 100.0);
        when(target.getUniqueId()).thenReturn(id); when(target.isValid()).thenReturn(true); when(target.getWorld()).thenReturn(world);
        when(target.getLocation()).thenAnswer(i -> enemyAt.clone()); when(target.getEyeLocation()).thenAnswer(i -> enemyAt.clone().add(0, 1.5, 0));
        when(target.getHealth()).thenAnswer(i -> health.get(id));
        var data = mock(PersistentDataContainer.class); when(target.getPersistentDataContainer()).thenReturn(data);
        doAnswer(i -> { assertTrue(SkillDamageGuard.isSkillDamage(player)); health.compute(id, (k,v) -> v - (double)i.getArgument(0)); return null; })
                .when(target).damage(anyDouble(), eq(player));
        return target;
    }
    Talent talent(String id) {
        var talent = new Talent(id, id, "test", TalentType.ACTIVE, TalentBranch.HUNTER, 3, 1, List.of(), null, 20,
                "AMETHYST_SHARD", "紫水晶碎片", 10, 20, 0, "RIGHT_CLICK");
        var data = new Talent.TalentLevelData();
        for (var entry : Map.of("baseDamage", 6.0, "range", 12.0, "radius", 4.0, "duration", 4.0,
                "charge", 2.0, "moveSpeed", .25, "slowDuration", 2.0, "slowAmplifier", 3.0).entrySet()) data.addEffect(entry.getKey(), entry.getValue());
        talent.addLevelData(1, data); learned.setTalentLevel(id, 1);
        for (int i = 0; i < 4; i++) if (learned.getSelectedSkills()[i] == null) { learned.setSelectedSkill(i, id); break; }
        return talent;
    }
    @SuppressWarnings("unchecked") void aimed(LivingEntity target) {
        when(world.rayTrace(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true), anyDouble(), any()))
                .thenAnswer(i -> ((Predicate<Entity>)i.getArgument(6)).test(target) ? new RayTraceResult(target.getEyeLocation().toVector(), target) : null);
    }
    EntityDamageByEntityEvent attack() {
        var event = mock(EntityDamageByEntityEvent.class); when(event.getDamager()).thenReturn(player); when(event.getEntity()).thenReturn(enemy);
        when(event.getFinalDamage()).thenReturn(1.0); return event;
    }
    @ParameterizedTest @ValueSource(strings = {"nen_lightning", "nen_chain", "nen_bungee"})
    void targetedAbilitiesFailWithoutATargetAndOnlyControlSuccessfulHits(String id) {
        var talent = talent(id); assertFalse(skills.cast(player, talent, 1)); aimed(enemy);
        assertTrue(skills.cast(player, talent, 1)); assertEquals(94, enemy.getHealth());
        clearInvocations(status, enemy); doNothing().when(enemy).damage(anyDouble(), eq(player));
        skills.cancel(player); skills.cast(player, talent, 1); clock.addAndGet(500); skills.tick();
        verify(status, never()).slow(any(), anyInt(), anyInt()); verify(enemy, never()).setVelocity(any());
    }
    @Test void rockWaitsForItsChargeAndHitsOnlyOnce() {
        aimed(enemy); assertTrue(skills.cast(player, talent("nen_rock"), 1));
        clock.addAndGet(1999); skills.tick(); verify(enemy, never()).damage(anyDouble(), any(Entity.class));
        clock.incrementAndGet(); skills.tick(); skills.tick(); verify(enemy).damage(6, player); verify(enemy).setVelocity(any());
    }
    @ParameterizedTest @EnumSource(value = GameMode.class, names = {"SURVIVAL", "ADVENTURE", "CREATIVE"})
    void hunterCasterCanChargeAndCompleteSkillsInPlayableModes(GameMode mode) {
        when(player.getGameMode()).thenReturn(mode);
        aimed(enemy);
        assertTrue(skills.cast(player, talent("nen_rock"), 1));
        clock.addAndGet(2000); skills.tick();
        verify(enemy).damage(6, player);
    }
    @ParameterizedTest @EnumSource(value = GameMode.class, names = {"CREATIVE", "SPECTATOR"})
    void creativeCastersStillCannotAttackProtectedPlayerModes(GameMode mode) {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        var target = caster(); when(target.getGameMode()).thenReturn(mode);
        when(target.isValid()).thenReturn(true); aimed(target);
        assertFalse(skills.cast(player, talent("nen_lightning"), 1));
        verify(target, never()).damage(anyDouble(), any(Entity.class));
        verify(player).sendMessage(contains("敵人"));
    }
    @Test void protectedAreaRejectionExplainsWhyTheSkillDidNotCast() {
        var protection = mock(ProtectionAreaManager.class); when(plugin.getProtectionAreaManager()).thenReturn(protection);
        when(protection.isInProtectedArea2D(any(Location.class))).thenReturn(true);
        assertFalse(skills.cast(player, talent("nen_en"), 1));
        verify(player).sendMessage(contains("安全區或保護區"));
        verify(world, never()).spawnParticle(eq(Particle.DUST), any(Location.class), anyInt(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Particle.DustOptions.class));
    }
    @Test void safeZoneRejectionExplainsWhyTheSkillDidNotCast() {
        var zones = mock(SafeZoneManager.class); when(plugin.getSafeZoneManager()).thenReturn(zones);
        when(zones.isInSafeZone(any(Location.class))).thenReturn(true);
        assertFalse(skills.cast(player, talent("nen_en"), 1));
        verify(player).sendMessage(contains("安全區或保護區"));
    }
    @Test void spectatorCannotCastHunterSkills() {
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertFalse(skills.cast(player, talent("nen_rock"), 1));
        clock.addAndGet(2000); skills.tick();
        verify(enemy, never()).damage(anyDouble(), any(Entity.class));
    }
    @ParameterizedTest @ValueSource(strings = {"movement", "damage", "teleport", "unlearn", "unequip"})
    void chargingIsInterruptedByMovementDamageTeleportOrRemovingTheTalent(String reason) {
        aimed(enemy); skills.cast(player, talent("nen_rock"), 1);
        switch (reason) {
            case "movement" -> playerAt.add(2, 0, 0);
            case "damage" -> { var event = mock(EntityDamageEvent.class); when(event.getEntity()).thenReturn(player); when(event.getFinalDamage()).thenReturn(2.0); skills.onDamage(event); }
            case "teleport" -> { var event = mock(PlayerTeleportEvent.class); when(event.getPlayer()).thenReturn(player); skills.onTeleport(event); }
            case "unlearn" -> learned.setTalentLevel("nen_rock", 0);
            case "unequip" -> learned.setSelectedSkill(0, null);
        }
        clock.addAndGet(2000); skills.tick(); verify(enemy, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void cancelledDamageDoesNotInterruptAnExistingCharge() {
        aimed(enemy); skills.cast(player, talent("nen_rock"), 1);
        var event = mock(EntityDamageEvent.class); when(event.getEntity()).thenReturn(player); when(event.getFinalDamage()).thenReturn(2.0); when(event.isCancelled()).thenReturn(true);
        skills.onDamage(event); clock.addAndGet(2000); skills.tick(); verify(enemy).damage(6, player);
    }
    @Test void scissorsHitsOnlyVisibleFrontalTargetsAndExcludesPets() {
        var behind = monster(); when(behind.getLocation()).thenAnswer(i -> new Location(world, 0, 64, -2));
        when(behind.getEyeLocation()).thenAnswer(i -> new Location(world, 0, 65.5, -2)); nearby.add(behind);
        var pet = mock(Wolf.class); when(pet.isValid()).thenReturn(true); when(pet.isTamed()).thenReturn(true);
        when(pet.getWorld()).thenReturn(world); when(pet.getLocation()).thenAnswer(i -> enemyAt.clone()); nearby.add(pet);
        skills.cast(player, talent("nen_scissors"), 1); verify(enemy).damage(6, player);
        verify(behind, never()).damage(anyDouble(), any(Entity.class)); verify(pet, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void paperTravelsThenStopsAtTheFirstAcceptedHit() {
        var talent = talent("nen_paper"); skills.cast(player, talent, 1); skills.tick(); assertEquals(100, enemy.getHealth());
        enemyAt.setZ(6); aimed(enemy); skills.tick(); assertEquals(94, enemy.getHealth()); skills.tick(); assertEquals(94, enemy.getHealth());
    }
    @Test void paperStopsAtWallsAndNeverQueriesAnUnloadedChunk() {
        when(world.rayTrace(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true), anyDouble(), any()))
                .thenReturn(new RayTraceResult(new Vector(0, 65.6, 1)));
        skills.cast(player, talent("nen_paper"), 1); skills.tick(); verify(enemy, never()).damage(anyDouble(), any(Entity.class));
        clearInvocations(world); skills.cast(player, talent("nen_paper"), 1);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false); skills.tick();
        verify(world, never()).rayTrace(any(Location.class), any(Vector.class), anyDouble(), any(), anyBoolean(), anyDouble(), any());
    }
    @Test void bungeePullsButStopsImmediatelyWhenSightIsLost() {
        enemyAt.setZ(6); aimed(enemy); skills.cast(player, talent("nen_bungee"), 1);
        clock.addAndGet(500); skills.tick(); verify(enemy).setVelocity(argThat(v -> v.getZ() < 0 && v.length() < .7));
        clearInvocations(enemy); when(player.hasLineOfSight(enemy)).thenReturn(false); clock.addAndGet(500); skills.tick(); verify(enemy, never()).setVelocity(any());
    }
    @Test void bossesCanTakeBungeeDamageButAreNeverPulled() {
        var mobs = mock(MobManager.class); when(plugin.getMobManager()).thenReturn(mobs);
        var data = mock(MobManager.MobData.class); when(mobs.getCustomMobKey(enemy)).thenReturn("boss"); when(mobs.getMobData("boss")).thenReturn(data); when(data.isBoss()).thenReturn(true);
        enemyAt.setZ(6); aimed(enemy); skills.cast(player, talent("nen_bungee"), 1); clock.addAndGet(500); skills.tick();
        verify(enemy).damage(6, player); verify(enemy, never()).setVelocity(any());
    }
    @Test void chainSlowsBossesForHalfTheNormalDuration() {
        var mobs = mock(MobManager.class); when(plugin.getMobManager()).thenReturn(mobs);
        var data = mock(MobManager.MobData.class); when(mobs.getCustomMobKey(enemy)).thenReturn("boss"); when(mobs.getMobData("boss")).thenReturn(data); when(data.isBoss()).thenReturn(true);
        aimed(enemy); skills.cast(player, talent("nen_chain"), 1); verify(status).slow(enemy, 20, 3);
    }
    @Test void godspeedAddsASeparateTemporaryModifierAndLimitsProcsToOncePerSecond() {
        skills.cast(player, talent("nen_godspeed"), 1); verify(speed).add(player, .25);
        var event = attack(); skills.onDamage(event); skills.onDamage(event); assertEquals(94, enemy.getHealth());
        clock.addAndGet(1000); skills.onDamage(event); assertEquals(88, enemy.getHealth());
        when(event.isCancelled()).thenReturn(true); clock.addAndGet(1000); skills.onDamage(event); assertEquals(88, enemy.getHealth());
    }
    @Test void godspeedDoesNotRecursivelyProcItsOwnSkillDamage() {
        skills.cast(player, talent("nen_godspeed"), 1);
        doAnswer(i -> { assertTrue(SkillDamageGuard.isSkillDamage(player)); skills.onDamage(attack()); return null; })
                .when(enemy).damage(anyDouble(), eq(player));
        skills.onDamage(attack()); verify(enemy).damage(6, player);
    }
    @ParameterizedTest @ValueSource(strings = {"expiry", "quit", "death", "teleport", "gamemode", "clear", "shutdown", "unequip"})
    void temporarySpeedIsAlwaysRemoved(String reason) {
        skills.cast(player, talent("nen_godspeed"), 1);
        switch (reason) {
            case "expiry" -> { clock.addAndGet(4000); skills.tick(); }
            case "quit" -> { var e = mock(PlayerQuitEvent.class); when(e.getPlayer()).thenReturn(player); skills.onQuit(e); }
            case "death" -> { var e = mock(PlayerDeathEvent.class); when(e.getEntity()).thenReturn(player); skills.onDeath(e); }
            case "teleport" -> { var e = mock(PlayerTeleportEvent.class); when(e.getPlayer()).thenReturn(player); skills.onTeleport(e); }
            case "gamemode" -> { var e = mock(PlayerGameModeChangeEvent.class); when(e.getPlayer()).thenReturn(player); skills.onGameMode(e); }
            case "clear" -> skills.clear();
            case "shutdown" -> skills.shutdown();
            case "unequip" -> { learned.setSelectedSkill(0, null); skills.tick(); }
        }
        verify(speed).remove(player); clock.addAndGet(1000); skills.onDamage(attack()); verify(enemy, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void enMarksOnlyVisibleEnemiesForItsOwnerWithoutDamageOrGlobalGlowing() {
        skills.cast(player, talent("nen_en"), 1); clock.addAndGet(500); skills.tick();
        verify(player).spawnParticle(eq(Particle.DUST), any(Location.class), eq(6), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Particle.DustOptions.class));
        verify(enemy, never()).damage(anyDouble(), any(Entity.class)); verify(enemy, never()).setGlowing(anyBoolean());
    }
    @Test void bodhisattvaHasExactlyThreePulsesAndDoesNotCatchUpAfterLag() {
        skills.cast(player, talent("nen_bodhisattva"), 1);
        for (int i = 0; i < 3; i++) { clock.addAndGet(500); skills.tick(); }
        assertEquals(79.6, enemy.getHealth(), 1e-9); verify(enemy, times(3)).damage(anyDouble(), eq(player));
        skills.clear(); health.put(enemy.getUniqueId(), 100.0); clearInvocations(enemy);
        skills.cast(player, talent("nen_bodhisattva"), 1); clock.addAndGet(3500); skills.tick();
        verify(enemy).damage(6, player); // Only one pulse is processed despite the long pause.
    }
    @Test void safeZonesAndFriendlyPlayersAreNeverValidTargets() {
        var safe = mock(SafeZoneManager.class); when(plugin.getSafeZoneManager()).thenReturn(safe); when(safe.isInSafeZone(any())).thenReturn(true);
        assertFalse(skills.cast(player, talent("nen_scissors"), 1)); verify(enemy, never()).damage(anyDouble(), any(Entity.class));
        when(safe.isInSafeZone(any())).thenReturn(false); var friendly = caster(); when(friendly.isValid()).thenReturn(true); nearby.clear(); nearby.add(friendly);
        when(world.getPVP()).thenReturn(false); assertFalse(skills.cast(player, talent("nen_scissors"), 1));
        verify(friendly, never()).damage(anyDouble(), any(Entity.class));
    }
    @Test void sustainedEffectsHaveAHardGlobalLimitAndShareOneTask() {
        var talent = talent("nen_en"); for (int i = 0; i < 64; i++) assertTrue(skills.cast(caster(), talent, 1));
        assertFalse(skills.cast(caster(), talent, 1)); skills.start(); skills.start();
        verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(5L), eq(5L)); skills.shutdown();
    }
    @Test void gonComboAndYoyosHaveDistinctBoundedMultiHitPatterns() {
        aimed(enemy); skills.cast(player, talent("nen_gon_combo"), 1);
        for (int i=0;i<3;i++) { clock.addAndGet(500); skills.tick(); }
        assertEquals(80.8, enemy.getHealth(), 1e-9); skills.clear(); health.put(enemy.getUniqueId(),100.0);
        skills.cast(player, talent("nen_killua_yoyo"),1);
        for (int i=0;i<4;i++) { clock.addAndGet(500); skills.tick(); }
        assertEquals(88,enemy.getHealth());
    }
    @Test void concentratedPunchHasOneChargeAndItsDefensiveRiskEndsAfterThatHit() {
        skills.cast(player,talent("nen_gon_ko"),1);
        var incoming=mock(EntityDamageEvent.class); when(incoming.getEntity()).thenReturn(player); when(incoming.getDamage()).thenReturn(100.0);
        skills.onIncoming(incoming); verify(incoming).setDamage(doubleThat(value -> Math.abs(value - 115.0) < 0.000001));
        skills.onDamage(attack()); clock.addAndGet(500); skills.onDamage(attack()); assertEquals(94,enemy.getHealth());
        clearInvocations(incoming); skills.onIncoming(incoming); verify(incoming,never()).setDamage(anyDouble());
    }
    @Test void scarletEyeChargesAreNotConsumedByAnimationFrames() {
        skills.cast(player,talent("nen_kurapika_emperor"),1);
        for(int i=0;i<3;i++){clock.addAndGet(500);skills.tick();}
        for(int i=0;i<4;i++){skills.onDamage(attack());clock.addAndGet(500);}
        assertEquals(82,enemy.getHealth());
    }
    @Test void scarletJudgmentTriggersOnceAfterItsAnchorDistanceIsExceeded() {
        aimed(enemy); skills.cast(player,talent("nen_kurapika_judgment"),1); assertEquals(94,enemy.getHealth());
        enemyAt.add(4,0,0); skills.tick(); assertEquals(85,enemy.getHealth()); skills.tick(); assertEquals(85,enemy.getHealth());
    }
    @Test void healingChainUsesTheExistingCancellableHealingServiceAndCanFailWithoutCasting() {
        var balance=mock(com.customrpg.balance.CombatBalance.class); when(plugin.getCombatBalance()).thenReturn(balance);
        var t=talent("nen_kurapika_heal"); assertFalse(skills.cast(player,t,1));
        verify(player).sendMessage(contains("目前無法恢復生命"));
        when(balance.heal(player,30)).thenReturn(18.0); assertTrue(skills.cast(player,t,1)); verify(balance,times(2)).heal(player,30);
    }
    @Test void counterattackBuffIsKeptWhenTheIncomingHitIsCancelled() {
        skills.cast(player,talent("nen_hisoka_rebound"),1);
        var incoming=mock(EntityDamageByEntityEvent.class); when(incoming.getEntity()).thenReturn(player); when(incoming.getDamager()).thenReturn(enemy);
        when(incoming.getDamage()).thenReturn(100.0); when(incoming.getFinalDamage()).thenReturn(75.0);
        skills.onIncoming(incoming); verify(incoming).setDamage(75.0);
        when(incoming.isCancelled()).thenReturn(true); skills.onDamage(incoming); assertEquals(100,enemy.getHealth());
        when(incoming.isCancelled()).thenReturn(false); skills.onIncoming(incoming); skills.onDamage(incoming); assertEquals(94,enemy.getHealth());
        clearInvocations(incoming); skills.onIncoming(incoming); verify(incoming,never()).setDamage(anyDouble());
    }
    @Test void cardsNeedFourStateSlotsAndOneVolleyCannotDamageTheSameEnemyFourTimes() {
        var en=talent("nen_en"); for(int i=0;i<61;i++) assertTrue(skills.cast(caster(),en,1));
        var cards=talent("nen_hisoka_cards"); assertFalse(skills.cast(player,cards,1)); skills.clear();
        assertTrue(skills.cast(player,cards,1)); aimed(enemy); skills.tick(); assertEquals(94,enemy.getHealth());
    }
    @Test void stealthDropsMobAggroButBreaksWhenTheCasterIsHurt() {
        skills.cast(player,talent("nen_zetsu"),1); when(((Mob)enemy).getTarget()).thenReturn(player);
        clock.addAndGet(500); skills.tick(); verify((Mob)enemy).setTarget(null);
        var target=mock(EntityTargetLivingEntityEvent.class); when(target.getEntity()).thenReturn(enemy); when(target.getTarget()).thenReturn(player);
        skills.onTarget(target); verify(target).setCancelled(true);
        var hurt=mock(EntityDamageEvent.class); when(hurt.getEntity()).thenReturn(player); when(hurt.getFinalDamage()).thenReturn(1.0);
        skills.onDamage(hurt); clearInvocations(target); skills.onTarget(target); verify(target,never()).setCancelled(true);
    }
    @Test void goldenPrayerAndFiveWavePalmsPreserveDamageLimitsAndPulseCounts() {
        skills.cast(player,talent("nen_netero_prayer"),1);
        var hurt=mock(EntityDamageEvent.class); when(hurt.getEntity()).thenReturn(player); when(hurt.getDamage()).thenReturn(100.0);
        skills.onIncoming(hurt); verify(hurt).setDamage(80.0); skills.clear();
        skills.cast(player,talent("nen_netero_ninetynine"),1);
        for(int i=0;i<5;i++){clock.addAndGet(500);skills.tick();} assertEquals(70,enemy.getHealth());
    }
    @Test void zeroPalmWaitsForItsChargeAndCanBeInterruptedLikeRock() {
        aimed(enemy); skills.cast(player,talent("nen_netero_zero"),1); skills.tick(); assertEquals(100,enemy.getHealth());
        clock.addAndGet(2000); skills.tick(); assertEquals(94,enemy.getHealth());
        skills.cast(player,talent("nen_netero_zero"),1); playerAt.add(2,0,0); clock.addAndGet(2000); skills.tick(); assertEquals(94,enemy.getHealth());
    }
    @Test void lightningStepUsesTheSharedLoadedChunkAndCollisionChecks() {
        var t=talent("nen_killua_step"); when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertFalse(skills.cast(player,t,1)); verify(player,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        verify(player).sendMessage(contains("前方沒有可安全移動的位置"));
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true); var block=mock(org.bukkit.block.Block.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(player.teleport(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenAnswer(i->{playerAt=((Location)i.getArgument(0)).clone();return true;});
        assertTrue(skills.cast(player,t,1)); assertEquals(4,playerAt.getZ(),1e-9);
    }
    @Test void stickyFieldAppliesSlowOnlyWhenItsDamageWasAccepted() {
        var t=talent("nen_hisoka_snare"); doNothing().when(enemy).damage(anyDouble(),eq(player));
        skills.cast(player,t,1); clock.addAndGet(500); skills.tick(); verify(status,never()).slow(any(),anyInt(),anyInt());
    }
    @Test void everyAnimationStyleHasABoundedParticleBudgetAndARecognizableCharacterProfile() {
        var visuals=new HunterVisuals();
        for(String id:List.of("nen_rock","nen_scissors","nen_paper","nen_godspeed","nen_chain","nen_bungee","nen_hisoka_cards","nen_killua_yoyo","nen_kurapika_emperor","nen_netero_zero","nen_ten")) {
            clearInvocations(world); visuals.cast(player,id);
            verify(world,atMost(64)).spawnParticle(eq(Particle.DUST),any(Location.class),eq(1),anyDouble(),anyDouble(),anyDouble(),anyDouble(),any(Particle.DustOptions.class));
            verify(world).playSound(any(Location.class),anyString(),anyFloat(),anyFloat());
        }
        assertEquals(HunterVisuals.Style.THUNDER,HunterVisuals.style("nen_killua_palm"));
        assertEquals(HunterVisuals.Style.BUDDHA,HunterVisuals.style("nen_netero_zero"));
        assertEquals(HunterVisuals.Style.CARD,HunterVisuals.style("nen_hisoka_cards"));
    }
}

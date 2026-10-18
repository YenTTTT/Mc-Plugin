package com.customrpg.races;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RaceManagerTest {
    @TempDir Path directory;
    CustomRPG plugin; Player player; Server server; PlayerStatsManager stats; RaceManager races;
    BukkitScheduler scheduler; BukkitTask task;
    AtomicLong clock=new AtomicLong(1000);
    Map<NamespacedKey,AttributeModifier> speed=new HashMap<>();
    double hp=10,maxHp=20;
    @BeforeEach void setup() throws Exception {
        Files.createDirectories(directory.resolve("config"));
        for(String name:List.of("races.yml","races_additional.yml"))
            Files.copy(Path.of("src/main/resources/config/"+name),directory.resolve("config/"+name));
        plugin=mock(CustomRPG.class); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        server=mock(Server.class); when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        scheduler=mock(BukkitScheduler.class); when(server.getScheduler()).thenReturn(scheduler);
        task=mock(BukkitTask.class); when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),eq(20L),eq(20L))).thenReturn(task);
        player=mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        var movement=mock(AttributeInstance.class);
        when(movement.getModifiers()).thenAnswer(i->new ArrayList<>(speed.values()));
        doAnswer(i->{var modifier=(AttributeModifier)i.getArgument(0); speed.put(modifier.getKey(),modifier); return null;}).when(movement).addTransientModifier(any());
        doAnswer(i->{speed.remove(((AttributeModifier)i.getArgument(0)).getKey()); return null;}).when(movement).removeModifier(any(AttributeModifier.class));
        var health=mock(AttributeInstance.class);
        when(health.getValue()).thenAnswer(i->maxHp); when(player.getHealth()).thenAnswer(i->hp);
        doAnswer(i->{maxHp=i.getArgument(0); return null;}).when(health).setBaseValue(anyDouble());
        doAnswer(i->{hp=i.getArgument(0); return null;}).when(player).setHealth(anyDouble());
        stats=spy(new PlayerStatsManager(plugin)); doNothing().when(stats).updateMaxHealth(any(Player.class));
        when(plugin.getPlayerStatsManager()).thenReturn(stats);
        races=new RaceManager(plugin,clock::get,ignored->movement,ignored->health); when(plugin.getRaceManager()).thenReturn(races);
    }
    private Path raceFile() { return directory.resolve("data/races/"+player.getUniqueId()+".yml"); }
    @Test void loadsAllTwelveAndNewRacesHaveEqualAttributeBudgets() {
        assertEquals(12,races.getRaceCount());
        for(String id:List.of("dragonkin","beastkin","dark_elf","halfling","golem")) {
            var race=races.getRaceData(id);
            assertEquals(30,race.getBaseStrength()+race.getBaseAgility()+race.getBaseMagic()+race.getBaseVitality()+race.getBaseDefense()+race.getBaseSpirit());
            assertEquals(6,race.getGrowthStrength()+race.getGrowthAgility()+race.getGrowthMagic()+race.getGrowthVitality()+race.getGrowthDefense()+race.getGrowthSpirit(),1e-9);
            assertTrue(race.getSkills().isEmpty());
        }
        assertThrows(UnsupportedOperationException.class,()->races.getAllRaceIds().clear());
    }
    @Test void oneTimeChoiceCommitsBeforeGrantingAndSurvivesFreshManager() throws Exception {
        assertTrue(races.choosePlayerRace(player,"ELF")); assertFalse(races.choosePlayerRace(player,"orc"));
        assertEquals("elf",YamlConfiguration.loadConfiguration(raceFile().toFile()).getString("race"));
        var restarted=new RaceManager(plugin,clock::get); assertEquals("elf",restarted.getPlayerRaceId(player));
        assertEquals(5,stats.getStats(player).getRaceAgility());
    }
    @Test void failedWriteAndResetPreservePreviousSelectionAndBonuses() throws Exception {
        assertTrue(races.setPlayerRace(player,"elf")); String original=Files.readString(raceFile());
        Files.createDirectory(Path.of(raceFile()+".tmp"));
        assertFalse(races.setPlayerRace(player,"orc")); assertFalse(races.tryResetPlayerRace(player));
        assertEquals("elf",races.getPlayerRaceId(player)); assertEquals(original,Files.readString(raceFile()));
        assertEquals(5,stats.getStats(player).getRaceAgility());
    }
    @Test void corruptPlayerFileIsReservedAndNeverOverwritten() throws Exception {
        String broken="race: [unterminated"; Files.writeString(raceFile(),broken);
        assertTrue(races.hasRace(player)); assertFalse(races.choosePlayerRace(player,"human"));
        assertFalse(races.isPlayerDataAvailable(player));
        assertFalse(races.setPlayerRace(player,"elf")); assertFalse(races.tryResetPlayerRace(player));
        races.saveAll(); races.unloadPlayer(player.getUniqueId());
        assertEquals(broken,Files.readString(raceFile()));
    }
    @Test void allTwelveRacesHaveNonzeroStatsAndMonotonicGrowthAtEveryLevel() {
        for (var race : races.getAllRaces()) {
            int[] previous = new int[6];
            for (int level = 1; level <= 60; level++) {
                int[] current = race.effectiveStats(level);
                assertEquals(com.customrpg.balance.BalancePlayerRules.raceBudget(level), Arrays.stream(current).sum(), race.getId());
                for (int i = 0; i < 6; i++) {
                    assertTrue(current[i] >= 1, race.getId());
                    assertTrue(current[i] >= previous[i], race.getId() + " at level " + level);
                }
                previous = current;
            }
        }
    }
    @Test void recalculatingTheLargerRaceBonusesDoesNotStackOrRefillMana() {
        var state = stats.getStats(player); state.setLevel(60);
        assertTrue(races.setPlayerRace(player, "demon"));
        assertEquals(8, state.getRaceStrength()); assertEquals(8, state.getRaceMagic());
        assertEquals(4, state.getRaceSpirit());
        state.setCurrentMana(7);
        races.applyRaceStats(player); races.applyRaceStats(player);
        assertEquals(8, state.getRaceStrength()); assertEquals(8, state.getRaceMagic());
        assertEquals(7, state.getCurrentMana());
    }
    @Test void missingDefinitionDoesNotSilentlyGrantAnotherFreeSelection() throws Exception {
        Files.writeString(raceFile(),"race: retired_race\n");
        assertTrue(races.hasRace(player)); assertNull(races.getPlayerRaceData(player));
        assertFalse(races.choosePlayerRace(player,"human")); assertEquals("retired_race",races.getPlayerRaceId(player));
    }
    @Test void resettingClearsRaceManaSpeedAndStatsWithoutLosingLevelEquipmentOrTalentBonuses() {
        var state=stats.getStats(player); state.setLevel(10); state.setBonusMaxMana(30); state.setBonusManaRegen(2);
        var talents=mock(TalentPassiveEffectManager.class); when(plugin.getTalentPassiveEffectManager()).thenReturn(talents);
        when(talents.getPlayerBonus(player,"manaBonus")).thenReturn(15.0);
        assertTrue(races.setPlayerRace(player,"elf"));
        assertEquals(126.35,state.getMaxMana(),1e-9);
        state.setCurrentMana(10); races.applyRaceStats(player); assertEquals(10,state.getCurrentMana(),1e-9);
        assertTrue(races.tryResetPlayerRace(player));
        assertEquals(118,state.getMaxMana(),1e-9); assertEquals(2.225,state.getManaRegen(),1e-9);
        assertEquals(0,state.getRaceAgility()); assertTrue(speed.isEmpty());
        assertFalse(races.hasRace(player));
    }
    @Test void speedReapplicationIsIdempotentAndKeepsForeignModifiers() {
        var foreign=new AttributeModifier(new NamespacedKey("other","speed"),0.2,AttributeModifier.Operation.ADD_SCALAR);
        speed.put(foreign.getKey(),foreign); assertTrue(races.setPlayerRace(player,"golem"));
        races.applyRaceStats(player); races.applyRaceStats(player);
        assertEquals(2,speed.size()); assertEquals(-0.03,speed.get(new NamespacedKey("customrpg","race_speed")).getAmount());
        races.detach(player); assertEquals(Map.of(foreign.getKey(),foreign),speed);
    }
    @Test void badReloadPreservesRegistryAndExistingAssignments() throws Exception {
        races.setPlayerRace(player,"human");
        Files.writeString(directory.resolve("config/races.yml"),"races: [broken");
        assertFalse(races.reloadConfiguration()); assertEquals(12,races.getRaceCount()); assertEquals("human",races.getPlayerRaceId(player));
    }
    @Test void invalidNumericSettingsRejectReloadAndDoNotProduceNanStats() throws Exception {
        var file=directory.resolve("config/races_additional.yml"); var yaml=YamlConfiguration.loadConfiguration(file.toFile());
        yaml.set("races.golem.passives.move-speed",Double.NaN); yaml.save(file.toFile());
        assertFalse(races.reloadConfiguration()); assertEquals(-0.03,races.getRaceData("golem").getBonusMoveSpeed());
    }
    @Test void existingConfigCanOverrideOrDisableAdditionalRacesWithoutBeingRewritten() throws Exception {
        var file=directory.resolve("config/races.yml"); var yaml=YamlConfiguration.loadConfiguration(file.toFile());
        yaml.set("races.dragonkin.name","自訂龍裔"); yaml.set("races.golem.enabled",false); yaml.save(file.toFile());
        String original=Files.readString(file); assertTrue(races.reloadConfiguration());
        assertEquals("自訂龍裔",races.getRaceData("dragonkin").getDisplayName()); assertNull(races.getRaceData("golem"));
        assertEquals(7,races.getRaceData("dragonkin").getBaseStrength());
        assertEquals(1.02,races.getRaceData("dragonkin").getWeaponBonus("SPEAR"));
        assertEquals(original,Files.readString(file));
    }
    private Runnable recovery() {
        doReturn(List.of(player)).when(server).getOnlinePlayers(); races.start();
        var capture=ArgumentCaptor.forClass(Runnable.class); verify(scheduler).runTaskTimer(eq(plugin),capture.capture(),eq(20L),eq(20L));
        return capture.getValue();
    }
    @Test void sharedRecoveryPausesDuringCombatRespectsCancellationAndStopsOnShutdown() {
        races.setPlayerRace(player,"angel"); Runnable tick=recovery(); double before=hp;
        tick.run(); assertEquals(before,hp); // Startup/rejoin delay also prevents immediate recovery.
        clock.set(9001); tick.run(); assertEquals(before+1.05,hp,1e-9);
        races.markCombat(player); before=hp; tick.run(); assertEquals(before,hp);
        clock.set(18002);
        var plugins=server.getPluginManager();
        doAnswer(i->{((EntityRegainHealthEvent)i.getArgument(0)).setCancelled(true); return null;}).when(plugins).callEvent(any());
        before=hp; tick.run(); assertEquals(before,hp); races.shutdown(); verify(task).cancel();
    }
    @Test void negativeRecoveryIsARealPenaltyWithoutDamageAndDeadPlayersNeverRegenerate() {
        races.setPlayerRace(player,"undead"); Runnable tick=recovery(); double before=hp; tick.run(); assertEquals(before,hp);
        assertEquals(0,races.getRecoveryPerSecond(races.getRaceData("undead")));
        races.setPlayerRace(player,"angel"); before=hp; when(player.isDead()).thenReturn(true); tick.run(); assertEquals(before,hp);
    }
}

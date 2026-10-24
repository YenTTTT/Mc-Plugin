package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssassinLearningTest {
    @TempDir Path directory;
    CustomRPG plugin; TalentManager talents; TalentPassiveEffectManager passives; Player player;
    @BeforeEach void setup() throws Exception {
        Files.createDirectories(directory.resolve("config"));
        for (String file : List.of("talents.yml", "talents_additional.yml", "talents_hunter.yml"))
            Files.copy(Path.of("src/main/resources/config/" + file), directory.resolve("config/" + file));
        plugin = mock(CustomRPG.class); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var logger = java.util.logging.Logger.getAnonymousLogger(); logger.setLevel(java.util.logging.Level.OFF);
        when(plugin.getLogger()).thenReturn(logger);
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
        var stats = new PlayerStats(); stats.addTalentPoints(10);
        var statsManager = mock(PlayerStatsManager.class); when(plugin.getPlayerStatsManager()).thenReturn(statsManager);
        when(statsManager.getStats(player)).thenReturn(stats); when(statsManager.getStats(any(UUID.class))).thenReturn(stats);
        talents = new TalentManager(plugin); when(plugin.getTalentManager()).thenReturn(talents);
        passives = new TalentPassiveEffectManager(plugin, talents, statsManager);
        when(plugin.getTalentPassiveEffectManager()).thenReturn(passives);
        talents.getPlayerTalents(player).setTalentLevel("assassin_instinct", 1);
    }
    @Test void weaponPassiveBecomesEffectiveImmediatelyAfterLearningAndUpgrading() {
        assertTrue(talents.upgradeTalent(player, "vital_strike"));
        assertEquals(.3, passives.getPlayerBonus(player, "backstabBonus"), 1e-8);
        assertTrue(talents.upgradeTalent(player, "vital_strike"));
        assertEquals(.45, passives.getPlayerBonus(player, "backstabBonus"), 1e-8);
        assertEquals(2, talents.getPlayerTalents(player).getTalentLevel("vital_strike"));
    }
    @Test void resettingTalentsImmediatelyRemovesOldCombatBonuses() {
        assertTrue(talents.upgradeTalent(player, "vital_strike"));
        assertTrue(talents.resetAllTalents(player));
        assertEquals(0, passives.getPlayerBonus(player, "backstabBonus"), 1e-8);
        assertEquals(0, talents.getPlayerTalents(player).getTalentLevel("vital_strike"));
    }
}

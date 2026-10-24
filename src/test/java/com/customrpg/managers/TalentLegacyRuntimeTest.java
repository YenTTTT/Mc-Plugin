package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Retirement must remove runtime effects while preserving old and retired saved progression. */
class TalentLegacyRuntimeTest {
    @TempDir Path directory;
    CustomRPG plugin; TalentManager manager; Player player; PlayerStats stats;
    @BeforeEach void setup() throws Exception {
        Files.createDirectories(directory.resolve("config"));
        for (String name : List.of("talents.yml", "talents_additional.yml", "talents_hunter.yml"))
            Files.copy(Path.of("src/main/resources/config/" + name), directory.resolve("config/" + name));
        plugin = mock(CustomRPG.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var logger = java.util.logging.Logger.getAnonymousLogger();
        logger.setLevel(java.util.logging.Level.OFF); when(plugin.getLogger()).thenReturn(logger);
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        stats = new PlayerStats(); stats.addTalentPoints(10);
        var sm = mock(PlayerStatsManager.class); when(plugin.getPlayerStatsManager()).thenReturn(sm);
        when(sm.getStats(player)).thenReturn(stats); when(sm.getStats(any(UUID.class))).thenReturn(stats);
        manager = new TalentManager(plugin); when(plugin.getTalentManager()).thenReturn(manager);
    }
    @Test void obsoleteConfigIsIgnoredEvenWhenMalformedAndNeverCreated() throws Exception {
        assertFalse(Files.exists(directory.resolve("config/talents_core.yml")));
        verify(plugin, never()).saveResource(eq("config/talents_core.yml"), anyBoolean());
        Files.writeString(directory.resolve("config/talents_core.yml"), "core: [broken yaml");
        assertTrue(manager.reloadConfigurations());
        assertEquals(220, manager.getTotalTalentCount());
        assertEquals(17, manager.getAllTalentTrees().size());
        assertNotNull(manager.findTalent("nen_rock"));
        assertNull(manager.findTalent("v3_weapon_crescent_slash"));
    }
    @Test void retiredSkillsCannotBeLearnedBoundOrCastEvenWhenSavedAsLearned() {
        String id = "v3_weapon_crescent_slash";
        var learned = manager.getPlayerTalents(player);
        learned.setTalentLevel(id, 1); learned.setSelectedSkill(0, id);
        assertFalse(manager.upgradeTalent(player, id));
        assertFalse(manager.bindSkill(player, id, 0, false));
        var skills = new TalentSkillManager(plugin);
        assertFalse(skills.castSkillDirectly(player, id, null));
        assertEquals(10, stats.getTalentPoints());
    }
    @Test void legacyBindingsRemainAvailableIncludingFButUltimateCannotBeBound() {
        var learned = manager.getPlayerTalents(player); learned.setTalentLevel("nen_rock", 3);
        assertTrue(manager.bindSkill(player, "nen_rock", 4, false));
        assertFalse(manager.bindSkill(player, "nen_rock", 0, true));
        assertEquals("nen_rock", learned.getSelectedSkills()[4]);
        learned.setUltimateSkill("nen_paper");
        assertFalse(learned.equippedSkills().contains("nen_paper"));
    }
    @Test void bothLegacyAndRetiredRanksAndSpentPointsSurviveSaveWithoutRefundOrReset() throws Exception {
        UUID id = UUID.randomUUID(); Path file = directory.resolve("data/talents/" + id + ".yml");
        var original = new YamlConfiguration();
        original.set("balance-version", 2); original.set("loadout-version", 3);
        original.set("total-points-spent", 8); original.set("branch-points.weapon", 5);
        original.set("talents.nen_rock", 3); original.set("talents.v3_weapon_crescent_slash", 1);
        original.set("selected-skills", Arrays.asList(null, "nen_rock", "v3_weapon_crescent_slash", null, "star_shatter"));
        original.set("ultimate-skill", "v3_weapon_blade_domain"); original.save(file.toFile());
        var before = Files.readAllBytes(file); var loaded = manager.getPlayerTalents(id);
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(3, loaded.getTalentLevel("nen_rock"));
        assertEquals(1, loaded.getTalentLevel("v3_weapon_crescent_slash"));
        assertEquals(8, loaded.getTotalPointsSpent());
        manager.savePlayerTalents(id, loaded);
        var saved = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(3, saved.getInt("talents.nen_rock"));
        assertEquals(1, saved.getInt("talents.v3_weapon_crescent_slash"));
        assertEquals(8, saved.getInt("total-points-spent"));
        assertEquals(5, saved.getInt("branch-points.weapon"));
        assertEquals(original.getList("selected-skills"), saved.getList("selected-skills"));
        assertEquals(original.getString("ultimate-skill"), saved.getString("ultimate-skill"));
        assertEquals(10, stats.getTalentPoints());
    }
    @Test void oldFourSlotGapsAndRanksArePreservedWithOriginalBackup() throws Exception {
        UUID id = UUID.randomUUID(); Path file = directory.resolve("data/talents/" + id + ".yml");
        var old = new YamlConfiguration(); old.set("balance-version", 2);
        old.set("selected-skills", Arrays.asList(null, "nen_rock", null, "star_shatter"));
        old.set("talents.nen_rock", 3); old.save(file.toFile());
        byte[] before = Files.readAllBytes(file); var loaded = manager.getPlayerTalents(id);
        assertArrayEquals(new String[]{null,"nen_rock",null,"star_shatter",null}, loaded.getSelectedSkills());
        assertEquals(3, loaded.getTalentLevel("nen_rock"));
        manager.savePlayerTalents(id, loaded);
        try (var files = Files.list(directory.resolve("data/talent-backups/v3"))) {
            assertTrue(files.anyMatch(p -> {
                try { return Arrays.equals(before, Files.readAllBytes(p)); }
                catch (Exception e) { return false; }
            }));
        }
    }
    @Test void unknownFutureSaveCannotBeOverwritten() throws Exception {
        UUID id = UUID.randomUUID(); Path file = directory.resolve("data/talents/" + id + ".yml");
        var cfg = new YamlConfiguration(); cfg.set("loadout-version", 100);
        cfg.set("talents.nen_rock", 3); cfg.save(file.toFile()); byte[] before = Files.readAllBytes(file);
        var loaded = manager.getPlayerTalents(id); manager.savePlayerTalents(id, loaded);
        assertArrayEquals(before, Files.readAllBytes(file));
    }
}

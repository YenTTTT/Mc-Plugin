package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TalentExpansionConfigTest {
    @TempDir Path directory;
    CustomRPG plugin; TalentManager manager; Path primary, additional;
    @BeforeEach void setup() throws Exception {
        Files.createDirectories(directory.resolve("config"));
        primary = directory.resolve("config/talents.yml"); additional = directory.resolve("config/talents_additional.yml");
        Files.copy(Path.of("src/main/resources/config/talents.yml"), primary);
        Files.copy(Path.of("src/main/resources/config/talents_additional.yml"), additional);
        plugin = mock(CustomRPG.class); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var logger = java.util.logging.Logger.getAnonymousLogger(); logger.setLevel(java.util.logging.Level.OFF);
        when(plugin.getLogger()).thenReturn(logger); manager = new TalentManager(plugin);
    }
    @Test void oldDefinitionsArePreservedAndAllNewTreesHaveExecutableSkillsAndCompleteLevels() throws Exception {
        assertEquals(164, manager.getTotalTalentCount()); assertEquals(14, manager.getAllTalentTrees().size());
        assertEquals(Files.readString(Path.of("src/main/resources/config/talents.yml")), Files.readString(primary));
        int active = 0, passive = 0;
        for (TalentBranch branch : List.of(TalentBranch.FROST, TalentBranch.STORM, TalentBranch.EARTH, TalentBranch.LIGHT)) {
            var tree = manager.getTalentTree(branch); assertEquals(6, tree.getTalentCount());
            for (Talent talent : tree.getAllTalents().values()) {
                if (talent.getType() == TalentType.ACTIVE) { active++; assertTrue(ExpandedTalentSkills.handles(talent.getId())); }
                else passive++;
                for (int level = 1; level <= 3; level++) assertNotNull(talent.getLevelData(level));
            }
        }
        assertEquals(16, active); assertEquals(8, passive);
    }
    @Test void deployedPrimaryDefinitionTakesPriorityOverAdditionalFile() throws Exception {
        var base = YamlConfiguration.loadConfiguration(primary.toFile()); var extra = YamlConfiguration.loadConfiguration(additional.toFile());
        base.set("frost.frost_shard", extra.getConfigurationSection("frost.frost_shard"));
        base.set("frost.frost_shard.manaCost", 7); base.save(primary.toFile());
        assertTrue(manager.reloadConfigurations()); assertEquals(7, manager.findTalent("frost_shard").getManaCost()); assertEquals(164, manager.getTotalTalentCount());
    }
    @ParameterizedTest @ValueSource(strings = {"syntax", "nan", "cycle", "slot", "unknown", "missing-level", "prerequisite", "string", "unknown-effect"})
    void invalidAdditionalFileRetainsPreviousTreesAndBadFileBytes(String problem) throws Exception {
        var previous = manager.findTalent("frost_shard");
        var data = YamlConfiguration.loadConfiguration(additional.toFile());
        switch (problem) {
            case "nan" -> data.set("frost.frost_shard.levels.1.effect.range", Double.NaN);
            case "cycle" -> data.set("frost.frost_attunement.prerequisite.skill", "frost_shard");
            case "slot" -> data.set("frost.frost_shard.gui.slot", 40);
            case "unknown" -> data.set("frost.frost_unimplemented", data.getConfigurationSection("frost.frost_shard"));
            case "missing-level" -> data.set("frost.frost_shard.levels.2", null);
            case "prerequisite" -> data.set("frost.frost_shard.prerequisite.skill", "missing");
            case "string" -> data.set("frost.frost_shard.manaCost", "free please");
            case "unknown-effect" -> data.set("frost.frost_shard.levels.1.effect.unimplemented", 3);
        }
        if (problem.equals("syntax")) Files.writeString(additional, "frost: [bad"); else data.save(additional.toFile());
        String bytes = Files.readString(additional); assertFalse(manager.reloadConfigurations());
        assertSame(previous, manager.findTalent("frost_shard")); assertEquals(bytes, Files.readString(additional));
    }
    @Test void prerequisitesUnlockNewNodesAndPassiveBonusesFeedTheExistingStatCalculation() throws Exception {
        var shard = manager.findTalent("frost_shard"); var player = new PlayerTalents();
        assertFalse(shard.canLearn(player.getTalentLevels(), 1)); player.setTalentLevel("frost_attunement", 3);
        assertTrue(shard.canLearn(player.getTalentLevels(), 1)); assertFalse(shard.canLearn(player.getTalentLevels(), 4));
        player.setTalentLevel("earth_endurance", 2); player.setTalentLevel("storm_footwork", 2); player.setTalentLevel("light_devotion", 1);
        var compute = TalentManager.class.getDeclaredMethod("computeTalentStatBonuses", PlayerTalents.class); compute.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,Integer> bonus = (Map<String,Integer>)compute.invoke(manager, player);
        assertEquals(9, bonus.get("magic")); assertEquals(4, bonus.get("vitality")); assertEquals(2, bonus.get("defense"));
        assertEquals(6, bonus.get("agility")); assertEquals(3, bonus.get("spirit"));
        player.resetAllTalents();
        @SuppressWarnings("unchecked") Map<String,Integer> reset = (Map<String,Integer>)compute.invoke(manager, player);
        assertTrue(reset.values().stream().allMatch(v -> v == 0));
    }
}

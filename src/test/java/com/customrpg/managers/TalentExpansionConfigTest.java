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
        Files.copy(Path.of("src/main/resources/config/talents_hunter.yml"), directory.resolve("config/talents_hunter.yml"));
        plugin = mock(CustomRPG.class); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var logger = java.util.logging.Logger.getAnonymousLogger(); logger.setLevel(java.util.logging.Level.OFF);
        when(plugin.getLogger()).thenReturn(logger); manager = new TalentManager(plugin);
    }
    @Test void oldDefinitionsArePreservedAndAllNewTreesHaveExecutableSkillsAndCompleteLevels() throws Exception {
        assertEquals(220, manager.getTotalTalentCount()); assertEquals(17, manager.getAllTalentTrees().size());
        assertEquals(Files.readString(Path.of("src/main/resources/config/talents.yml")), Files.readString(primary));
        int active = 0, passive = 0;
        for (TalentBranch branch : List.of(TalentBranch.FROST, TalentBranch.STORM, TalentBranch.EARTH, TalentBranch.LIGHT)) {
            var tree = manager.getTalentTree(branch); assertEquals(branch==TalentBranch.FROST||branch==TalentBranch.EARTH?10:6, tree.getTalentCount());
            for (Talent talent : tree.getAllTalents().values()) {
                if (talent.getType() == TalentType.ACTIVE) { active++; assertTrue(EarthSkill.handles(talent.getId())||FrostSkill.handles(talent.getId())||ExpandedTalentSkills.handles(talent.getId())); }
                else passive++;
                for (int level = 1; level <= talent.getMaxLevel(); level++) assertNotNull(talent.getLevelData(level));
            }
        }
        assertEquals(24, active); assertEquals(8, passive);
    }
    @Test void deployedPrimaryDefinitionTakesPriorityOverAdditionalFile() throws Exception {
        var base = YamlConfiguration.loadConfiguration(primary.toFile()); var extra = YamlConfiguration.loadConfiguration(additional.toFile());
        base.set("storm.storm_arc", extra.getConfigurationSection("storm.storm_arc"));
        base.set("storm.storm_arc.manaCost", 7); base.save(primary.toFile());
        assertTrue(manager.reloadConfigurations()); assertEquals(7, manager.findTalent("storm_arc").getManaCost()); assertEquals(220, manager.getTotalTalentCount());
    }
    @Test void everyActiveTalentHasASupportedItemAndClickBinding() {
        int active = 0;
        for (var tree : manager.getAllTalentTrees().values()) for (var talent : tree.getAllTalents().values()) {
            if (talent.getType() != TalentType.ACTIVE) continue;
            active++;
            assertTrue(Set.of("LEFT_CLICK", "RIGHT_CLICK", "LEFT_CLICK_SNEAK").contains(talent.getTriggerType()), talent.getId());
            assertTrue(Arrays.stream(org.bukkit.Material.values()).anyMatch(material ->
                    TalentItemBinding.matches(talent.getMechanism(), material)), talent.getId());
        }
        assertEquals(150, active);
    }
    @Test void hotkeyOnlyDeployedFilesGainItemBindingsWithoutChangingFilesOrBalance() throws Exception {
        var base = YamlConfiguration.loadConfiguration(primary.toFile());
        var extra = YamlConfiguration.loadConfiguration(additional.toFile());
        base.set("nature.star_shatter.trigger.type", "HOTKEY");
        base.set("nature.star_shatter.mechanism", "快捷鍵 1～4");
        base.set("nature.star_shatter.manaCost", 7);
        // Expansion definitions may have been copied into the primary file by administrators.
        base.set("frost.frost_shard", extra.getConfigurationSection("frost.frost_shard"));
        base.set("frost.frost_shard.trigger.type", "HOTKEY");
        base.set("frost.frost_shard.mechanism", "快捷鍵 1～4");
        extra.set("earth.earth_guard.trigger.type", "HOTKEY");
        extra.set("earth.earth_guard.mechanism", "快捷鍵 1～4");
        base.save(primary.toFile()); extra.save(additional.toFile());
        String before = Files.readString(primary), extraBefore = Files.readString(additional);
        assertTrue(manager.reloadConfigurations());
        assertEquals("金粒", manager.findTalent("star_shatter").getMechanism());
        assertEquals("LEFT_CLICK", manager.findTalent("star_shatter").getTriggerType());
        assertEquals(7, manager.findTalent("star_shatter").getManaCost());
        assertEquals("木棒", manager.findTalent("frost_shard").getMechanism());
        assertEquals("燧石", manager.findTalent("earth_guard").getMechanism());
        assertEquals(before, Files.readString(primary)); assertEquals(extraBefore, Files.readString(additional));
    }
    @Test void administratorItemBindingsArePreservedOnReload() throws Exception {
        var base = YamlConfiguration.loadConfiguration(primary.toFile());
        base.set("nature.star_shatter.mechanism", "EMERALD");
        base.set("nature.star_shatter.trigger.type", "RIGHT_CLICK"); base.save(primary.toFile());
        assertTrue(manager.reloadConfigurations());
        assertEquals("EMERALD", manager.findTalent("star_shatter").getMechanism());
        assertEquals("RIGHT_CLICK", manager.findTalent("star_shatter").getTriggerType());
        assertTrue(TalentItemBinding.matches("EMERALD", org.bukkit.Material.EMERALD));
    }
    @ParameterizedTest @ValueSource(strings = {"syntax", "nan", "cycle", "slot", "unknown", "missing-level", "prerequisite", "string", "unknown-effect"})
    void invalidAdditionalFileRetainsPreviousTreesAndBadFileBytes(String problem) throws Exception {
        var previous = manager.findTalent("storm_arc");
        var data = YamlConfiguration.loadConfiguration(additional.toFile());
        switch (problem) {
            case "nan" -> data.set("storm.storm_arc.levels.1.effect.range", Double.NaN);
            case "cycle" -> data.set("storm.storm_attunement.prerequisite.skill", "storm_arc");
            case "slot" -> data.set("storm.storm_arc.gui.slot", 40);
            case "unknown" -> data.set("storm.storm_unimplemented", data.getConfigurationSection("storm.storm_arc"));
            case "missing-level" -> data.set("storm.storm_arc.levels.2", null);
            case "prerequisite" -> data.set("storm.storm_arc.prerequisite.skill", "missing");
            case "string" -> data.set("storm.storm_arc.manaCost", "free please");
            case "unknown-effect" -> data.set("storm.storm_arc.levels.1.effect.unimplemented", 3);
        }
        if (problem.equals("syntax")) Files.writeString(additional, "storm: [bad"); else data.save(additional.toFile());
        String bytes = Files.readString(additional); assertFalse(manager.reloadConfigurations());
        assertSame(previous, manager.findTalent("storm_arc")); assertEquals(bytes, Files.readString(additional));
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
    @Test void hunterTreeContainsNineExecutableMovesAndThreePassivesWithReachablePrerequisites() {
        var tree = manager.getTalentTree(TalentBranch.HUNTER); assertEquals(36, tree.getTalentCount());
        assertEquals(28, tree.getAllTalents().values().stream().filter(t -> t.getType() == TalentType.ACTIVE).count());
        var ranks = new HashMap<String,Integer>(); ranks.put("nen_ten", 1); ranks.put("nen_ren", 1); ranks.put("nen_rock", 1); ranks.put("nen_netero_prayer", 1);
        assertTrue(tree.getTalent("nen_bodhisattva").canLearn(ranks, 1));
        for (var talent : tree.getAllTalents().values()) {
            assertDoesNotThrow(() -> HunterTalentSkills.validate(talent));
            for (int rank = 1; rank <= 3; rank++) assertNotNull(talent.getLevelData(rank));
            if (talent.getType() == TalentType.ACTIVE) {
                assertTrue(HunterTalentSkills.handles(talent.getId()));
                assertTrue(TalentItemBinding.matches(talent.getMechanism(), org.bukkit.Material.AMETHYST_SHARD));
            }
        }
    }
    @ParameterizedTest @ValueSource(strings = {"syntax", "unknown", "cycle", "slot", "speed", "missing-level", "prerequisite"})
    void badHunterFileRetainsThePreviouslyLoadedTreesAndOriginalBytes(String problem) throws Exception {
        var path = directory.resolve("config/talents_hunter.yml");
        var config = YamlConfiguration.loadConfiguration(path.toFile()); var old = manager.findTalent("nen_godspeed");
        switch (problem) {
            case "unknown" -> config.set("hunter.nen_unsupported", config.getConfigurationSection("hunter.nen_en"));
            case "cycle" -> config.set("hunter.nen_ten.prerequisite.skill", "nen_rock");
            case "slot" -> config.set("hunter.nen_en.gui.slot", 53);
            case "speed" -> config.set("hunter.nen_godspeed.levels.1.effect.moveSpeed", 5);
            case "missing-level" -> config.set("hunter.nen_en.levels.3", null);
            case "prerequisite" -> config.set("hunter.nen_en.prerequisite.skill", "missing");
        }
        if (problem.equals("syntax")) Files.writeString(path, "hunter: [broken"); else config.save(path.toFile());
        String bytes = Files.readString(path); assertFalse(manager.reloadConfigurations());
        assertSame(old, manager.findTalent("nen_godspeed")); assertEquals(bytes, Files.readString(path));
    }
    @Test void upgradeCreatesTheSeparateHunterFileWithoutReplacingExistingTalentSettings() throws Exception {
        var path = directory.resolve("config/talents_hunter.yml"); Files.delete(path);
        String primaryBytes = Files.readString(primary), additionalBytes = Files.readString(additional);
        doAnswer(i -> { Files.copy(Path.of("src/main/resources/" + i.getArgument(0)), path); return null; })
                .when(plugin).saveResource("config/talents_hunter.yml", false);
        assertTrue(manager.reloadConfigurations()); assertEquals(36, manager.getTalentTree(TalentBranch.HUNTER).getTalentCount());
        assertEquals(primaryBytes, Files.readString(primary)); assertEquals(additionalBytes, Files.readString(additional));
    }
    @Test void nenTrainingPassivesFeedTheExistingAttributeSources() throws Exception {
        var player = new PlayerTalents(); player.setTalentLevel("nen_ten", 2); player.setTalentLevel("nen_ren", 1); player.setTalentLevel("nen_gyo", 3);
        var compute = TalentManager.class.getDeclaredMethod("computeTalentStatBonuses", PlayerTalents.class); compute.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,Integer> weights = (Map<String,Integer>) compute.invoke(manager, player);
        assertEquals(4, weights.get("vitality")); assertEquals(2, weights.get("defense"));
        assertEquals(2, weights.get("strength")); assertEquals(2, weights.get("magic"));
        assertEquals(3, weights.get("agility")); assertEquals(6, weights.get("spirit"));
    }
    @Test void everyCharacterRouteHasUniqueSlotsAndPrerequisitesFlowUpwardsWithinTheSameCharacter() {
        var tree = manager.getTalentTree(TalentBranch.HUNTER);
        var expected = Map.of(HunterPath.COMMON, 5, HunterPath.GON, 7, HunterPath.KILLUA, 7, HunterPath.KURAPIKA, 6, HunterPath.HISOKA, 5, HunterPath.NETERO, 6);
        for (HunterPath path : HunterPath.values()) {
            var nodes = tree.getAllTalents().values().stream().filter(t -> HunterPath.of(t.getId()) == path).toList();
            assertEquals(expected.get(path).intValue(), nodes.size(), path.name()); var slots = new HashSet<Integer>();
            for (var talent : nodes) {
                assertTrue(slots.add(talent.getGuiSlot()));
                for (var pre : talent.getPrerequisites()) {
                    var parent = tree.getTalent(pre.talentId); HunterPath parentPath = HunterPath.of(parent.getId());
                    assertTrue(parentPath == path || parentPath == HunterPath.COMMON, talent.getId());
                    if (parentPath == path) assertTrue(talent.getGuiSlot()/9 < parent.getGuiSlot()/9, talent.getId());
                }
            }
        }
    }
    @Test void oldTwelveNodeFileGainsCharacterRoutesWithoutRewritingConfigOrLosingLearnedRanks() throws Exception {
        var path = directory.resolve("config/talents_hunter.yml"); var old = YamlConfiguration.loadConfiguration(path.toFile());
        var original = Set.of("nen_ten", "nen_ren", "nen_gyo", "nen_rock", "nen_scissors", "nen_paper", "nen_lightning", "nen_chain", "nen_bungee", "nen_godspeed", "nen_en", "nen_bodhisattva");
        for (String id : new HashSet<>(old.getConfigurationSection("hunter").getKeys(false))) if (!original.contains(id)) old.set("hunter." + id, null);
        old.set("hunter.nen_rock.prerequisite.skill", "nen_ren"); old.set("hunter.nen_rock.gui.slot", 31);
        old.set("hunter.nen_rock.manaCost", 27); old.save(path.toFile()); String bytes = Files.readString(path);
        var player = mock(org.bukkit.entity.Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var learned = manager.getPlayerTalents(player); learned.setTalentLevel("nen_rock", 2); learned.setSelectedSkill(0, "nen_rock");
        assertTrue(manager.reloadConfigurations()); assertEquals(36, manager.getTalentTree(TalentBranch.HUNTER).getTalentCount());
        assertEquals(40, manager.findTalent("nen_rock").getGuiSlot()); assertEquals("nen_gon_instinct", manager.findTalent("nen_rock").getPrerequisites().get(0).talentId);
        assertEquals(27, manager.findTalent("nen_rock").getManaCost()); assertEquals(bytes, Files.readString(path));
        assertSame(learned, manager.getPlayerTalents(player)); assertEquals(2, learned.getTalentLevel("nen_rock")); assertEquals("nen_rock", learned.getSelectedSkills()[0]);
    }
}

package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.talents.*;
import com.customrpg.players.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FrostTalentConfigTest {
    @TempDir Path directory;CustomRPG plugin;TalentManager talents;Player player;
    @BeforeEach void setup() throws Exception {
        Files.createDirectories(directory.resolve("config"));for(String name:List.of("talents.yml","talents_additional.yml","talents_hunter.yml","talents_frost.yml"))Files.copy(Path.of("src/main/resources/config/"+name),directory.resolve("config/"+name));
        plugin=mock(CustomRPG.class);when(plugin.getDataFolder()).thenReturn(directory.toFile());var logger=java.util.logging.Logger.getAnonymousLogger();logger.setLevel(java.util.logging.Level.OFF);when(plugin.getLogger()).thenReturn(logger);
        player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());talents=new TalentManager(plugin);
    }
    @Test void oldFrostIdsHaveTheNewMovesAndRanksAndFilesArePreserved() throws Exception {
        var frostFile=directory.resolve("config/talents_frost.yml");
        var frostConfig=YamlConfiguration.loadConfiguration(frostFile.toFile());
        for(var skill:FrostSkill.values()) frostConfig.set("frost."+skill.id+".mechanism",skill.ordinal()%2==0?"雪球":"SNOWBALL");
        frostConfig.save(frostFile.toFile());byte[] frostBefore=Files.readAllBytes(frostFile);
        var primary=directory.resolve("config/talents.yml");var extra=directory.resolve("config/talents_additional.yml");byte[] before=Files.readAllBytes(primary),additional=Files.readAllBytes(extra);
        var learned=talents.getPlayerTalents(player);learned.setTalentLevel("frost_shard",3);learned.setTalentLevel("frost_blizzard",2);learned.setSelectedSkill(2,"frost_shard");learned.setTotalPointsSpent(5);
        assertTrue(talents.reloadConfigurations());assertSame(learned,talents.getPlayerTalents(player));assertEquals(3,learned.getTalentLevel("frost_shard"));assertEquals(2,learned.getTalentLevel("frost_blizzard"));assertEquals(5,learned.getTotalPointsSpent());assertEquals("frost_shard",learned.getSelectedSkills()[2]);
        assertArrayEquals(before,Files.readAllBytes(primary));assertArrayEquals(additional,Files.readAllBytes(extra));
        assertArrayEquals(frostBefore,Files.readAllBytes(frostFile));
        for(var skill:FrostSkill.values()) {
            var talent=talents.findTalent(skill.id);
            assertEquals("木棒",talent.getMechanism());
            assertTrue(TalentItemBinding.matches(talent.getMechanism(),org.bukkit.Material.STICK));
            assertFalse(TalentItemBinding.matches(talent.getMechanism(),org.bukkit.Material.SNOWBALL));
            assertTrue(com.customrpg.gui.TalentLore.describe(talent,1).get(4).contains("木棒"));
        }
        assertTrue(com.customrpg.gui.TalentLore.describe(talents.findTalent("frost_crown"),1).get(1).contains("木棒右鍵"));
        var tree=talents.getTalentTree(TalentBranch.FROST);assertEquals(10,tree.getTalentCount());assertEquals(8,tree.getAllTalents().values().stream().filter(t->t.getType()==TalentType.ACTIVE).count());
        for(var skill:FrostSkill.values()){var t=talents.findTalent(skill.id);assertEquals(skill.title,t.getName());assertEquals(skill.mana,t.getManaCost());assertEquals(skill.cooldown,t.getCooldown());assertDoesNotThrow(()->FrostTalentSkills.validate(t));}
    }
    @Test void malformedFrostReloadCannotReplaceRunningTrees() throws Exception {
        var previous=talents.findTalent("frost_crown");var file=directory.resolve("config/talents_frost.yml");var cfg=YamlConfiguration.loadConfiguration(file.toFile());
        cfg.set("frost.frost_crown.levels.1.effect.baseDamage",Double.NaN);cfg.save(file.toFile());assertFalse(talents.reloadConfigurations());assertSame(previous,talents.findTalent("frost_crown"));
    }
    @Test void utilityRanksReduceManaWhileDamageRanksKeepTheListedManaCost() {
        assertEquals(12.8,FrostSkill.mana(talents.findTalent("frost_step"),3));
        assertEquals(14.4,FrostSkill.mana(talents.findTalent("frost_lance"),3));
        assertEquals(10,FrostSkill.mana(talents.findTalent("frost_shard"),3));
    }
    @Test void ultimateCannotUseANormalSlotAndOldUltimateIdsRemainInert() {
        var learned=talents.getPlayerTalents(player);learned.setTalentLevel("frost_crown",1);learned.setTalentLevel("frost_shard",1);
        assertFalse(talents.bindSkill(player,"frost_crown",0,false));assertTrue(talents.bindSkill(player,"frost_crown",0,true));
        assertFalse(talents.bindSkill(player,"frost_shard",0,true));assertTrue(learned.equippedSkills().contains("frost_crown"));
        learned.setUltimateSkill("v3_weapon_blade_domain");assertFalse(learned.equippedSkills().contains("v3_weapon_blade_domain"));
    }
}

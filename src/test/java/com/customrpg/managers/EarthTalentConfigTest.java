package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.talents.*;
import com.customrpg.players.PlayerTalents;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EarthTalentConfigTest {
    @TempDir Path directory;TalentManager manager;Player p;
    @BeforeEach void setup()throws Exception{Files.createDirectories(directory.resolve("config"));for(String f:List.of("talents.yml","talents_additional.yml","talents_hunter.yml","talents_frost.yml","talents_earth.yml"))Files.copy(Path.of("src/main/resources/config/"+f),directory.resolve("config/"+f));var plugin=mock(CustomRPG.class);when(plugin.getDataFolder()).thenReturn(directory.toFile());var log=java.util.logging.Logger.getAnonymousLogger();log.setLevel(java.util.logging.Level.OFF);when(plugin.getLogger()).thenReturn(log);manager=new TalentManager(plugin);p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());}
    @Test void fourOriginalIdsKeepRanksInvestmentAndSelectedSlotsWithoutWritingOriginalConfigs()throws Exception{var old=directory.resolve("config/talents_additional.yml");byte[] before=Files.readAllBytes(old);var pt=manager.getPlayerTalents(p);for(var s:List.of(EarthSkill.AEGIS,EarthSkill.STRIKE,EarthSkill.GRASP,EarthSkill.SINK))pt.setTalentLevel(s.id,3);pt.setTotalPointsSpent(12);pt.setSelectedSkill(3,"earth_bastion");assertTrue(manager.reloadConfigurations());assertSame(pt,manager.getPlayerTalents(p));assertEquals(12,pt.getTotalPointsSpent());assertEquals("earth_bastion",pt.getSelectedSkills()[3]);assertArrayEquals(before,Files.readAllBytes(old));assertEquals(220,manager.getTotalTalentCount());assertEquals(10,manager.getTalentTree(TalentBranch.EARTH).getTalentCount());for(var s:EarthSkill.values()){var t=manager.findTalent(s.id);assertEquals(s.title,t.getName());assertTrue(TalentItemBinding.matches(t.getMechanism(),Material.FLINT));assertDoesNotThrow(()->EarthTalentSkills.validate(t));}assertEquals(3,pt.getTalentLevel("earth_bastion"));}
    @Test void badEarthDefinitionRetainsEveryPreviouslyLoadedTree()throws Exception{var previous=manager.findTalent("earth_titan");var file=directory.resolve("config/talents_earth.yml");var cfg=YamlConfiguration.loadConfiguration(file.toFile());cfg.set("earth.earth_titan.levels.1.effect.range",99);cfg.save(file.toFile());assertFalse(manager.reloadConfigurations());assertSame(previous,manager.findTalent("earth_titan"));assertNotNull(manager.findTalent("frost_crown"));}
    @Test void oneSharedUltimateSlotAcceptsTitanOrCrownAndRejectsNormalSkillsAndRetiredCore(){var pt=manager.getPlayerTalents(p);pt.setTalentLevel("earth_titan",1);pt.setTalentLevel("frost_crown",1);pt.setTalentLevel("earth_guard",1);assertFalse(manager.bindSkill(p,"earth_titan",0,false));assertTrue(manager.bindSkill(p,"earth_titan",0,true));assertTrue(pt.equippedSkills().contains("earth_titan"));assertTrue(manager.bindSkill(p,"frost_crown",0,true));assertFalse(pt.equippedSkills().contains("earth_titan"));assertFalse(manager.bindSkill(p,"earth_guard",0,true));pt.setUltimateSkill("v3_earth_titan");assertFalse(pt.equippedSkills().contains("v3_earth_titan"));}
    @Test void utilityRanksLowerManaWithoutExtendingReductionControlOrDuration(){assertEquals(17.6,TalentLoadout.mana(manager.findTalent("earth_guard"),3));assertEquals(14.4,TalentLoadout.mana(manager.findTalent("earth_grasp"),3));assertEquals(12,TalentLoadout.mana(manager.findTalent("earth_shockwave"),3));assertTrue(com.customrpg.gui.TalentLore.describe(manager.findTalent("earth_titan"),1).get(1).contains("Shift＋F／燧石右鍵"));assertTrue(com.customrpg.gui.TalentLore.formula(manager.findTalent("earth_bastion"),1).contains("最多 7"));}
}

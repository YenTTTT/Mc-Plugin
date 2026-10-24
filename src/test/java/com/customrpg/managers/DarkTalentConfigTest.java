package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DarkTalentConfigTest {
    @TempDir Path directory;TalentManager manager;Player p;
    @BeforeEach void setup()throws Exception{Files.createDirectories(directory.resolve("config"));for(String f:List.of("talents.yml","talents_additional.yml","talents_hunter.yml","talents_frost.yml","talents_earth.yml","talents_dark.yml"))Files.copy(Path.of("src/main/resources/config/"+f),directory.resolve("config/"+f));var plugin=mock(CustomRPG.class);when(plugin.getDataFolder()).thenReturn(directory.toFile());var log=java.util.logging.Logger.getAnonymousLogger();log.setLevel(java.util.logging.Level.OFF);when(plugin.getLogger()).thenReturn(log);manager=new TalentManager(plugin);p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());}
    @Test void twelveLegacyRanksResolveToEightMovesWithoutDeletingOriginalRecordsOrPoints()throws Exception{var pt=manager.getPlayerTalents(p);for(String id:DarkSkill.legacy().keySet())pt.setTalentLevel(id,3);pt.setTotalPointsSpent(36);pt.loadSelectedSkill(0,"shadow_bolt");pt.loadSelectedSkill(1,"soul_drain");pt.loadSelectedSkill(2,"reaper_domain");var primary=directory.resolve("config/talents.yml");byte[] before=Files.readAllBytes(primary);assertTrue(manager.reloadConfigurations());assertEquals(220,manager.getTotalTalentCount());assertEquals(11,manager.getTalentTree(TalentBranch.DARK).getTalentCount());assertEquals(12,pt.getTalentLevels().size());assertEquals(36,pt.getTotalPointsSpent());assertEquals("dark_rend",pt.getSelectedSkills()[0]);assertNull(pt.getSelectedSkills()[1]);assertNull(pt.getSelectedSkills()[2]);assertEquals("dark_eclipse",pt.getUltimateSkill());assertEquals("reaper_domain",pt.storedSelectedSkills()[2]);for(var s:DarkSkill.values()){assertEquals(3,pt.getTalentLevel(s.id));assertTrue(TalentItemBinding.matches(manager.findTalent(s.id).getMechanism(),Material.WITHER_ROSE));assertDoesNotThrow(()->DarkTalentSkills.validate(manager.findTalent(s.id)));}assertArrayEquals(before,Files.readAllBytes(primary));}
    @Test void savingAndReloadingPreservesRawAliasRanksSlotsAndExplicitUltimatePriority(){var pt=manager.getPlayerTalents(p);pt.setTalentLevel("soul_drain",2);pt.setTalentLevel("shadow_bolt",1);pt.setTalentLevel("reaper_domain",3);pt.loadSelectedSkill(0,"soul_drain");pt.loadSelectedSkill(1,"reaper_domain");pt.setUltimateSkill("frost_crown");pt.setTotalPointsSpent(6);manager.savePlayerTalents(p.getUniqueId(),pt);var loaded=new TalentManager(manager.getPlugin()).getPlayerTalents(p);assertEquals("frost_crown",loaded.getUltimateSkill());assertEquals("soul_drain",loaded.storedSelectedSkills()[0]);assertEquals(2,loaded.getTalentLevel("dark_rend"));assertEquals(2,loaded.getTalentLevels().get("soul_drain"));assertEquals(1,loaded.getTalentLevels().get("shadow_bolt"));assertEquals(6,loaded.getTotalPointsSpent());}
    @Test void badDarkReloadRetainsAllExistingTrees()throws Exception{var old=manager.findTalent("dark_eclipse");var f=directory.resolve("config/talents_dark.yml");var config=YamlConfiguration.loadConfiguration(f.toFile());config.set("dark.dark_eclipse.levels.1.effect.baseDamage",Double.NaN);config.save(f.toFile());assertFalse(manager.reloadConfigurations());assertSame(old,manager.findTalent("dark_eclipse"));assertNotNull(manager.findTalent("earth_titan"));}
    @Test void legacyReferencesAndPrerequisitesUseCanonicalSkillsAndUltimateUsesTheSharedSlot(){var pt=manager.getPlayerTalents(p);pt.setTalentLevel("shadow_bolt",1);assertTrue(manager.findTalent("dark_stitch").canLearn(pt.effectiveLevels(),1));assertEquals("dark_stitch",manager.findTalent("curse_mark").getId());pt.setTalentLevel("reaper_domain",3);assertFalse(manager.bindSkill(p,"reaper_domain",0,false));assertTrue(manager.bindSkill(p,"reaper_domain",0,true));assertEquals("dark_eclipse",pt.getUltimateSkill());assertFalse(Arrays.asList(pt.getSelectedSkills()).contains("dark_eclipse"));assertTrue(pt.equippedSkills().contains("dark_eclipse"));assertEquals(13.6,TalentLoadout.mana(manager.findTalent("dark_step"),3));assertEquals(0,manager.findTalent("dark_eclipse").getHpCost());}
}

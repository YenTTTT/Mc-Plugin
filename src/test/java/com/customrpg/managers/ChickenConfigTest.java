package com.customrpg.managers;
import com.customrpg.CustomRPG;
import com.customrpg.talents.*;
import com.customrpg.gui.TalentLore;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ChickenConfigTest {
    @TempDir Path folder;TalentManager manager;
    @BeforeEach void setup()throws Exception{Files.createDirectories(folder.resolve("config"));for(String file:List.of("talents.yml","talents_additional.yml","talents_hunter.yml","talents_frost.yml","talents_earth.yml","talents_dark.yml","talents_necro.yml","talents_chicken.yml"))Files.copy(Path.of("src/main/resources/config/"+file),folder.resolve("config/"+file));var p=mock(CustomRPG.class);when(p.getDataFolder()).thenReturn(folder.toFile());var log=java.util.logging.Logger.getAnonymousLogger();log.setLevel(java.util.logging.Level.OFF);when(p.getLogger()).thenReturn(log);manager=new TalentManager(p);}
    @Test void allEightSkillsHaveValidatedCostsBindingsAndSixFields(){assertEquals(220,manager.getTotalTalentCount());assertEquals(8,manager.getTalentTree(TalentBranch.CHICKEN).getTalentCount());for(var s:ChickenSkill.values()){var t=manager.findTalent(s.id);assertDoesNotThrow(()->ChickenTalentSkills.validate(t));assertTrue(TalentItemBinding.matches(t.getMechanism(),Material.EGG));assertEquals(s==ChickenSkill.GOD?1:3,t.getMaxLevel());assertEquals(6,TalentLore.describe(t,1).size());assertEquals(s==ChickenSkill.GOD,TalentLoadout.ultimate(s.id));}assertEquals(1.5,manager.findTalent("chicken_egg").getCooldown());assertEquals(16,TalentLoadout.mana(manager.findTalent("chicken_supply"),3));assertNotNull(manager.findTalent("necro_lance"));}
    @Test void badChickenReloadPreservesTheCompleteOldCatalog()throws Exception{var previous=manager.findTalent("chicken_god");var file=folder.resolve("config/talents_chicken.yml");var c=YamlConfiguration.loadConfiguration(file.toFile());c.set("chicken.chicken_god.levels.1.effect.range",1000);c.save(file.toFile());assertFalse(manager.reloadConfigurations());assertSame(previous,manager.findTalent("chicken_god"));assertNotNull(manager.findTalent("dark_eclipse"));}
    @Test void deployedPriorChickenConfigIsUpgradedInMemoryWithoutRewritingTheFile()throws Exception{var path=folder.resolve("config/talents_chicken.yml");var c=YamlConfiguration.loadConfiguration(path.toFile());for(var skill:ChickenSkill.values())for(int rank=1;rank<=(skill==ChickenSkill.GOD?1:3);rank++)c.set("chicken."+skill.id+".levels."+rank+".effect.spiritScaling",null);c.set("chicken.chicken_egg.description","old three-baby description");c.save(path.toFile());String before=Files.readString(path);assertTrue(manager.reloadConfigurations());assertEquals(.12,manager.findTalent("chicken_egg").getEffectDouble(1,"spiritScaling",-1));assertFalse(manager.findTalent("chicken_egg").getDescription().contains("old three-baby"));assertEquals(before,Files.readString(path));}

}

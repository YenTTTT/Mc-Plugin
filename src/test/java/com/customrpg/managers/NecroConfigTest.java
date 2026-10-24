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
class NecroConfigTest {
    @TempDir Path folder;TalentManager manager;
    @BeforeEach void setup()throws Exception{Files.createDirectories(folder.resolve("config"));for(String file:List.of("talents.yml","talents_additional.yml","talents_hunter.yml","talents_frost.yml","talents_earth.yml","talents_dark.yml","talents_necro.yml"))Files.copy(Path.of("src/main/resources/config/"+file),folder.resolve("config/"+file));var p=mock(CustomRPG.class);when(p.getDataFolder()).thenReturn(folder.toFile());var log=java.util.logging.Logger.getAnonymousLogger();log.setLevel(java.util.logging.Level.OFF);when(p.getLogger()).thenReturn(log);manager=new TalentManager(p);}
    @Test void eightNodesHaveSafeCostsRanksItemsAndSixFieldDescriptions(){assertEquals(220,manager.getTotalTalentCount());assertEquals(8,manager.getTalentTree(TalentBranch.NECROMANCER).getTalentCount());for(var s:NecroSkill.values()){var t=manager.findTalent(s.id);assertDoesNotThrow(()->NecroTalentSkills.validate(t));assertTrue(TalentItemBinding.matches(t.getMechanism(),Material.BONE));assertEquals(s==NecroSkill.GATE?1:3,t.getMaxLevel());assertEquals(6,TalentLore.describe(t,1).size());assertEquals(s==NecroSkill.GATE,TalentLoadout.ultimate(s.id));}assertEquals(20.8,TalentLoadout.mana(manager.findTalent("necro_summon"),3));assertNotNull(manager.findTalent("dark_servant"));}
    @Test void malformedReloadIsAtomicAndDoesNotAlterAnyPlayerOrExistingConfig()throws Exception{var original=manager.findTalent("necro_gate");var primary=folder.resolve("config/talents.yml");byte[] bytes=Files.readAllBytes(primary);var path=folder.resolve("config/talents_necro.yml");var c=YamlConfiguration.loadConfiguration(path.toFile());c.set("necromancer.necro_gate.levels.1.effect.baseDamage",Double.NaN);c.save(path.toFile());assertFalse(manager.reloadConfigurations());assertSame(original,manager.findTalent("necro_gate"));assertArrayEquals(bytes,Files.readAllBytes(primary));}
    @Test void attackPolicyHasIndependentCapsAndCommandNeverResetsAttackCount(){var a=new NecroRoster.Attack(0);assertFalse(a.ready(NecroRoster.Kind.WARRIOR,39));a.command();assertEquals(3,a.commit(NecroRoster.Kind.WARRIOR,40));assertEquals(0,a.commit(NecroRoster.Kind.WARRIOR,41));assertEquals(3,a.commit(NecroRoster.Kind.WARRIOR,80));assertEquals(2,a.commit(NecroRoster.Kind.WARRIOR,120));assertEquals(2,a.commit(NecroRoster.Kind.WARRIOR,160));a.command();assertEquals(0,a.commit(NecroRoster.Kind.WARRIOR,200));assertEquals(4,a.used());assertEquals(2,NecroRoster.Kind.COLOSSUS.slots);assertEquals(1,NecroRoster.Kind.KNIGHT.slots);}
}

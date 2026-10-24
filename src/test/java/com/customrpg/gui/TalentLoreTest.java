package com.customrpg.gui;

import com.customrpg.CustomRPG;
import com.customrpg.managers.TalentManager;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.*;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TalentLoreTest {
    @TempDir Path directory;
    CustomRPG plugin; TalentManager manager;
    @BeforeEach void setup() throws Exception {
        Files.createDirectories(directory.resolve("config"));
        for (String name:List.of("talents.yml","talents_additional.yml","talents_hunter.yml"))
            Files.copy(Path.of("src/main/resources/config/"+name),directory.resolve("config/"+name));
        plugin=mock(CustomRPG.class); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var log=java.util.logging.Logger.getAnonymousLogger(); log.setLevel(java.util.logging.Level.OFF);
        when(plugin.getLogger()).thenReturn(log); manager=new TalentManager(plugin);
    }
    String formula(String id,int level) { return TalentLore.formula(manager.findTalent(id),level); }
    @Test void allTwoHundredTalentsAtEveryRankExposeOnlyTheRequestedFields() throws Exception {
        int count=0; var report=new ArrayList<String>();
        String[] labels={"技能介紹: ","觸發方式: ","冷卻: ","消耗: ","機制物品: ","傷害公式: "};
        for (var tree:manager.getAllTalentTrees().values()) for (var talent:tree.getAllTalents().values()) {
            count++;
            for (int rank=0;rank<=talent.getMaxLevel();rank++) {
                var lines=TalentLore.describe(talent,rank); assertEquals(6,lines.size(),talent.getId());
                for (int i=0;i<labels.length;i++) assertTrue(ChatColor.stripColor(lines.get(i)).startsWith(labels[i]),talent.getId());
                String visible=String.join("\n",lines);
                for (String removed:List.of(talent.getId(),"當前效果:","下級效果:","前置:","statBonus_","magicScaling","null"))
                    assertFalse(visible.contains(removed),talent.getId()+" contains "+removed);
            }
            report.add(talent.getId()+"\t"+talent.getName()+"\t"+ChatColor.stripColor(String.join(" | ",TalentLore.describe(talent,1))));
        }
        assertEquals(220,count);
        Path reportFile=Path.of("target/talent-lore-review.tsv"); Files.createDirectories(reportFile.getParent());
        Files.write(reportFile,report,java.nio.charset.StandardCharsets.UTF_8);
    }
    @Test void fireDamageCadenceAndFractionalCoefficientsMatchTheExecutor() {
        assertTrue(formula("fire_spark",1).contains("魔力×1.2"));
        assertTrue(formula("flame_storm",1).contains("每秒 魔力×1.2÷5"));
        assertTrue(formula("flame_jet",1).contains("每0.25秒"));
        assertTrue(formula("hell_domain",1).contains("÷2"));
        assertTrue(formula("meteor",1).contains("max（0.3"));
        assertTrue(formula("dark_servant",1).contains("最多4次"));
    }
    @Test void healingBuffsAndTrainingCannotAdvertiseAHealingCoefficientAsDamage() {
        for (String id:List.of("light_mend","light_purge","light_sanctuary","nen_kurapika_heal","nen_en","nen_netero_prayer","fire_affinity"))
            assertEquals("無直接傷害",formula(id,1),id);
    }
    @Test void weaponAndPercentHealthAttacksDoNotLoseTheirActualDamageBasis() {
        assertTrue(formula("earth_shatter",1).contains("武器基礎傷害（含力量×0.2）"));
        assertTrue(formula("earth_shatter",1).contains("×1.7"));
        assertTrue(formula("cleave",1).contains("目標最大生命"));
        assertTrue(formula("blood_burst",1).contains("消耗後生命"));
        assertTrue(formula("critical_arrow",1).contains("消耗專注"));
        assertTrue(formula("nen_bodhisattva",1).contains("1／1／1.4"));
    }
    @Test void consumptionDistinguishesManaHealthAndFocusAndDoesNotHideZeroCosts() {
        assertTrue(TalentLore.describe(manager.findTalent("piercing_blood"),1).get(3).contains("10 HP"));
        assertTrue(TalentLore.describe(manager.findTalent("blood_frenzy"),1).get(3).contains("30 HP"));
        assertTrue(TalentLore.describe(manager.findTalent("wind_trap"),1).get(3).contains("全部專注"));
        assertEquals("§7消耗: §b無",TalentLore.describe(manager.findTalent("fire_affinity"),1).get(3));
    }
    @Test void learnedRankIsUsedWhileUnlearnedTalentPreviewsRankOne() {
        var t=manager.findTalent("flame_storm");
        assertTrue(TalentLore.describe(t,0).get(5).contains("魔力×1.2÷5"));
        assertTrue(TalentLore.describe(t,3).get(5).contains("魔力×1.8÷7"));
    }
    @Test void actualTreeItemUsesTheCompactLoreWithoutInternalIdsAndUpgradeSections() throws Exception {
        var learned=new PlayerTalents(); learned.setTalentLevel("flame_storm",2);
        var method=TalentTreeGUI.class.getDeclaredMethod("createTalentIcon",Talent.class,PlayerTalents.class); method.setAccessible(true);
        try(var items=mockConstruction(ItemStack.class,(item,ctx)->when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)))) {
            var item=(ItemStack)method.invoke(new TalentTreeGUI(plugin,manager),manager.findTalent("flame_storm"),learned);
            var lore=org.mockito.ArgumentCaptor.forClass(List.class); verify(item.getItemMeta()).setLore(lore.capture());
            assertEquals(6,lore.getValue().size());
            assertTrue(lore.getValue().get(5).toString().contains("魔力×1.5÷6"));
        }
    }
}

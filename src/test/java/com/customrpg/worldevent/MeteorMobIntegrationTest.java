package com.customrpg.worldevent;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeteorMobIntegrationTest {
    private Map<String,Object> map(ConfigurationSection section) {
        Map<String,Object> result=new LinkedHashMap<>();
        section.getKeys(false).forEach(key->{Object value=section.get(key); result.put(key,value instanceof ConfigurationSection nested?map(nested):value);});
        return result;
    }
    @Test void allFiveTemplatesLoadThroughExistingMobSystemAndNeverEnterWildPools() throws Exception {
        YamlConfiguration yaml=new YamlConfiguration(); yaml.load(Path.of("src/main/resources/config/mobs/types/meteor.yml").toFile());
        Map<String,Map<String,Object>> templates=new LinkedHashMap<>();
        yaml.getKeys(false).forEach(key->templates.put(key,map(Objects.requireNonNull(yaml.getConfigurationSection(key)))));
        CustomRPG plugin=mock(CustomRPG.class); when(plugin.namespace()).thenReturn("customrpg");
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        ConfigManager config=mock(ConfigManager.class); when(config.getAllMobs()).thenReturn(templates);
        MobManager manager=new MobManager(plugin,config);
        assertEquals(5,manager.getMobTypeCount()); assertTrue(manager.getNormalMobKeys().isEmpty()); assertTrue(manager.getBossMobKeys().isEmpty());
        assertTrue(manager.getMobData("meteor_core_guardian").isBoss());
        for(String key:templates.keySet()) {
            var mob=manager.getMobData(key); assertNotNull(mob);
            assertTrue(mob.getTags().contains("event-only")); assertEquals(1,mob.getMinLevel()); assertEquals(60,mob.getMaxLevel());
            assertEquals(com.customrpg.balance.InitialBalanceModel.mobHealth(60,mob.balanceRole(),1),mob.calculateHealth(60),1e-6); assertTrue(mob.calculateDamage(60)>0);
        }
    }
}

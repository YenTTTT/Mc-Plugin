package com.customrpg.config;

import com.customrpg.world.DistanceZone;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConfigurationTest {
    @Test void allBundledYamlParsesWithBukkitLoader() throws Exception {
        try (var files = Files.walk(Path.of("src/main/resources"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                YamlConfiguration configuration = new YamlConfiguration();
                assertDoesNotThrow(() -> configuration.load(path.toFile()), path.toString());
            }
        }
    }

    @Test void pluginDescriptorDeclaresOptionalDependenciesRecognizedByBukkit() throws Exception {
        try (var input = Files.newInputStream(Path.of("src/main/resources/plugin.yml"))) {
            PluginDescriptionFile descriptor = new PluginDescriptionFile(input);
            assertEquals(java.util.List.of("LibsDisguises", "BeautyQuests"), descriptor.getSoftDepend());
        }
    }

    @Test void shippedDistanceZonesAreContiguousAndEndAtLevelSixty() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(Path.of("src/main/resources/config/world_zones.yml").toFile());
        var zones = config.getConfigurationSection("zones");
        assertNotNull(zones);
        assertEquals(6, zones.getKeys(false).size());
        double end = 150;
        DistanceZone last = null;
        for (String id : zones.getKeys(false)) {
            var zone = zones.getConfigurationSection(id);
            assertNotNull(zone);
            last = new DistanceZone(id, zone.getString("name"), zone.getDouble("min-distance"),
                    zone.getDouble("max-distance"), zone.getInt("level-min"), zone.getInt("level-max"));
            assertEquals(end, last.minDistance());
            end = last.maxDistance();
        }
        assertNotNull(last);
        assertEquals(60, last.baseLevel(100_000));
        assertFalse(config.getBoolean("legacy-regions-enabled"));
    }
}

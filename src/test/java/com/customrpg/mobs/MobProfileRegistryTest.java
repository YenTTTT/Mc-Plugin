package com.customrpg.mobs;

import com.customrpg.CustomRPG;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobProfileRegistryTest {
    @TempDir Path directory;
    MobProfileRegistry registry; Path file;
    @BeforeEach void setup() throws Exception {
        file = directory.resolve("config/mobs/encounters.yml"); Files.createDirectories(file.getParent());
        Files.copy(Path.of("src/main/resources/config/mobs/encounters.yml"), file);
        var plugin = mock(CustomRPG.class); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        registry = new MobProfileRegistry(plugin);
    }
    @Test void everyBundledTemplateHasAValidatedProfileAndBearReallyUsesAnAnimalModel() throws Exception {
        Set<String> templates = new HashSet<>();
        try (var files = Files.list(Path.of("src/main/resources/config/mobs/types"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                var yaml = new YamlConfiguration(); yaml.load(path.toFile()); templates.addAll(yaml.getKeys(false));
            }
        }
        assertEquals(153, templates.size()); assertEquals(templates.size(), registry.all().size());
        for (String id : templates) assertNotNull(registry.get(id), id);
        assertEquals(EntityType.PANDA, registry.get("berserker_bear").entityType());
        assertTrue(registry.get("berserker_bear").scale() > 1);
        assertEquals(List.of(MobAbility.CHARGE, MobAbility.STOMP), registry.get("berserker_bear").abilities());
        assertEquals(0.35, registry.get("berserker_bear").rageThreshold());
    }
    @Test void invalidReloadRetainsPreviousDefinitionsAndNeverRewritesTheFile() throws Exception {
        Files.writeString(file, "profiles: [broken"); String invalid = Files.readString(file);
        assertFalse(registry.reload()); assertEquals(153, registry.all().size()); assertEquals(invalid, Files.readString(file));
    }
    @Test void unsafeModelAndUnboundedTimingsAreRejected() throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(file.toFile());
        yaml.set("profiles.berserker_bear.visual.entity", "ENDER_DRAGON"); yaml.save(file.toFile());
        assertFalse(registry.reload()); assertEquals(EntityType.PANDA, registry.get("berserker_bear").entityType());
        yaml.set("profiles.berserker_bear.visual.entity", "PANDA"); yaml.set("profiles.berserker_bear.mechanics.windup-seconds", Double.NaN); yaml.save(file.toFile());
        assertFalse(registry.reload());
    }
    @Test void profilesCanBeDisabledIndividuallyAndCollectionsAreImmutable() throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(file.toFile()); yaml.set("profiles.berserker_bear.enabled", false); yaml.save(file.toFile());
        assertTrue(registry.reload()); assertNull(registry.get("berserker_bear")); assertEquals(152, registry.all().size());
        assertThrows(UnsupportedOperationException.class, () -> registry.get("grass_zombie").abilities().clear());
        assertThrows(UnsupportedOperationException.class, () -> registry.get("grass_zombie").equipment().clear());
    }
    @Test void expansionCoversEveryLandEnvironmentAndKeepsBossesSeparateFromOrdinaryPools() throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(Path.of("src/main/resources/config/mobs/types/biome_expansion.yml").toFile());
        assertEquals(30, yaml.getKeys(false).size());
        assertEquals(10, yaml.getKeys(false).stream().filter(k -> yaml.getBoolean(k + ".boss")).count());
        for (String tag : List.of("plains", "forest", "desert", "swamp", "cave", "ice", "mountain", "nether", "ocean", "end")) {
            int minimum = tag.equals("nether") ? 30 : tag.equals("end") ? 50 : 1;
            for (int level = minimum; level <= 60; level++) {
                final int current = level;
                assertTrue(yaml.getKeys(false).stream().anyMatch(k -> !yaml.getBoolean(k + ".boss")
                        && yaml.getStringList(k + ".tags").contains(tag) && yaml.getString(k + ".spawn-time").equals("all")
                        && current >= yaml.getInt(k + ".level-range.min") && current <= yaml.getInt(k + ".level-range.max")), tag + " Lv." + level);
            }
            assertTrue(yaml.getKeys(false).stream().anyMatch(k -> yaml.getBoolean(k + ".boss")
                    && yaml.getStringList(k + ".tags").contains(tag) && yaml.getStringList(k + ".tags").contains("wilderness-boss")));
        }
    }
    @Test void specializedMagmaPhasesAndTimidWildlifeRemainExplicit() {
        assertTrue(registry.get("boss_magma_core").legacyBehavior());
        assertEquals("timid", registry.get("wild_chicken").temperament());
        assertEquals("neutral", registry.get("prairie_wolf").temperament());
    }
}

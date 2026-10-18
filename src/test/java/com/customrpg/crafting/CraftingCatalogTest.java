package com.customrpg.crafting;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CraftingCatalogTest {
    static final Path RESOURCE = Path.of("src/main/resources/config/crafting_progression.yml");
    @TempDir Path directory;
    CraftingCatalog catalog;
    @BeforeEach void setup() throws Exception { catalog = new CraftingCatalog(RESOURCE.toFile()); }
    @Test void completeCatalogHasAllKindsInEveryThemeAndTierWithNoRecipeShapeCollisions() {
        assertEquals(980, catalog.gear().size()); assertEquals(84, catalog.materials().size());
        assertEquals(560, catalog.gear().values().stream().filter(g -> g.kind().weapon()).count());
        assertEquals(14, Arrays.stream(CraftingCatalog.Kind.values()).map(k -> k.shape).distinct().count());
        for (var theme : catalog.themes().values()) for (var tier : catalog.tiers())
            assertEquals(14, catalog.gear().values().stream().filter(g -> g.theme().equals(theme) && g.tier().equals(tier)).count());
    }
    @ParameterizedTest @ValueSource(ints = {1,7,8,17,18,27,28,37,38,47,48,57,58,60,100})
    void shopAndDropsChooseTheHighestTierNotExceedingLocalLevel(int level) {
        var tier = catalog.tier(level); assertTrue(tier.level() <= level);
        assertTrue(catalog.tiers().stream().noneMatch(t -> t.level() <= level && t.level() > tier.level()));
    }
    @Test void everyDefinitionIntegratesWithWeaponOrEquipmentStatsAndStaysWithinTradeLimits() {
        for (var gear : catalog.gear().values()) {
            assertTrue(gear.price() > 0 && gear.price() <= 128);
            if (gear.kind().weapon()) {
                var weapon = catalog.weapon(gear); assertEquals(gear.tier().level(), weapon.getMinLevel());
                assertEquals(gear.kind().category(), weapon.getCategory()); assertTrue(weapon.getDoubleExtra("base-damage", 0) > 0);
                assertTrue(weapon.getDoubleExtra("attack-speed", 0) > -4 && weapon.getDoubleExtra("attack-speed", 0) < 0);
            } else {
                var equipment = catalog.equipment(gear); assertEquals(gear.tier().level(), equipment.getRequiredLevel());
                assertEquals(gear.kind().slot(), equipment.getSlot()); assertFalse(equipment.getBaseAttributes().isEmpty());
            }
            assertTrue(catalog.materials().containsKey(gear.essence())); assertTrue(catalog.materials().containsKey(gear.binding())); assertTrue(catalog.materials().containsKey(gear.core()));
        }
    }
    @Test void identityDoesNotUseDisplayNameAndRejectsWrongMaterialUnknownSchemaAndFakeVanilla() {
        var items = CraftingTestItems.factory(catalog); String id = "plains_essence_t1";
        assertEquals(id, items.materialId(items.material(id, 3)));
        assertNull(items.materialId(CraftingTestItems.stack(Material.RABBIT_HIDE, 1)));
        assertNull(items.materialId(CraftingTestItems.stack(Material.STONE, 1, new HashMap<>(Map.of(CraftingItems.MATERIAL, id, CraftingItems.VERSION, 1)))));
        var badVersion = CraftingTestItems.stack(Material.RABBIT_HIDE, 1, new HashMap<>(Map.of(CraftingItems.MATERIAL, id, CraftingItems.VERSION, 2)));
        assertNull(items.materialId(badVersion)); assertTrue(items.custom(badVersion));
        assertThrows(IllegalArgumentException.class, () -> items.material(id, 65));
    }
    @ParameterizedTest @ValueSource(strings = {"nan", "tier-gap", "descending", "unsafe-material", "fractional-price", "invalid-stat"})
    void invalidConfigurationIsRejectedWithoutRewritingIt(String problem) throws Exception {
        var config = YamlConfiguration.loadConfiguration(RESOURCE.toFile());
        switch (problem) {
            case "nan" -> config.set("themes.plains.damage-factor", Double.NaN);
            case "tier-gap" -> config.set("tiers.2", null);
            case "descending" -> config.set("tiers.2.required-level", 1);
            case "unsafe-material" -> config.set("themes.plains.material", "LAVA_BUCKET");
            case "fractional-price" -> config.set("tiers.2.emerald-price", 1.5);
            case "invalid-stat" -> config.set("themes.plains.primary", "FIRE_RESISTANCE");
        }
        var file = directory.resolve("bad.yml"); config.save(file.toFile()); String bytes = Files.readString(file);
        assertThrows(Exception.class, () -> new CraftingCatalog(file.toFile())); assertEquals(bytes, Files.readString(file));
    }
}

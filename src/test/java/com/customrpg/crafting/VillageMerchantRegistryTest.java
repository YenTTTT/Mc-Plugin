package com.customrpg.crafting;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class VillageMerchantRegistryTest {
    @TempDir Path directory;
    Path file; Logger logger = Logger.getAnonymousLogger();
    @BeforeEach void setup() { file = directory.resolve("data/village_merchants.yml"); logger.setLevel(java.util.logging.Level.OFF); }
    VillageMerchantRegistry create() { return new VillageMerchantRegistry(file.toFile(), logger); }
    VillageMerchantRegistry.Village village() {
        UUID world = UUID.randomUUID(); String id = VillageMerchantRegistry.identity(world, -32, -16, 32, 16);
        return new VillageMerchantRegistry.Village(id, world, -32, -16, 32, 16, -.5, 65, 1.5, null, -1, 0);
    }
    @Test void merchantIdentityAndLastUnloadedChunkSurviveANewRegistry() {
        var store = create(); var village = village().bind(UUID.randomUUID()).unloaded(-4, 7); assertTrue(store.save(village));
        assertEquals(village, create().villages().get(village.id())); assertEquals(-4, create().villages().get(village.id()).lastChunkX());
    }
    @Test void structureIdentityIsStableAcrossChunksButDistinctAcrossWorlds() {
        UUID world = UUID.randomUUID(); String first = VillageMerchantRegistry.identity(world, -32, -16, 32, 16);
        assertEquals(first, VillageMerchantRegistry.identity(world, -32, -16, 32, 16)); assertNotEquals(first, VillageMerchantRegistry.identity(UUID.randomUUID(), -32, -16, 32, 16));
    }
    @Test void corruptRegistryIsRetainedAndBlocksFurtherWrites() throws Exception {
        Files.createDirectories(file.getParent()); Files.writeString(file, "villages: [broken"); String bytes = Files.readString(file);
        var store = create(); assertFalse(store.healthy()); assertFalse(store.save(village())); assertEquals(bytes, Files.readString(file));
    }
    @Test void atomicWriteFailureRetainsExistingRegistryAndPausesSpawning() throws Exception {
        var store = create(); var village = village(); assertTrue(store.save(village)); String bytes = Files.readString(file);
        Files.createDirectory(Path.of(file + ".tmp")); assertFalse(store.save(village.bind(UUID.randomUUID())));
        assertFalse(store.healthy()); assertEquals(bytes, Files.readString(file)); assertNull(store.villages().get(village.id()).merchant());
    }
    @Test void invalidBoundsAndForeignIdentityCannotBecomeAStoredVillage() {
        var v = village(); assertThrows(IllegalArgumentException.class, () -> new VillageMerchantRegistry.Village("foreign", v.world(), -32, -16, 32, 16, 0, 65, 0, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> v.anchor(900, 65, 0)); assertThrows(UnsupportedOperationException.class, () -> create().villages().clear());
    }
    @Test void malformedLastChunkCoordinatesCannotBeGuessedAsZero() throws Exception {
        var store = create(); var village = village().bind(UUID.randomUUID()); assertTrue(store.save(village));
        var data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());
        data.set("villages." + village.id() + ".last-chunk-x", "unknown"); data.save(file.toFile());
        String bytes = Files.readString(file); var restarted = create(); assertFalse(restarted.healthy()); assertEquals(bytes, Files.readString(file));
    }
}

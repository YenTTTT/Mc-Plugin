package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerStatsManagerTest {
    @TempDir Path directory;

    private PlayerStatsManager manager() {
        CustomRPG plugin = mock(CustomRPG.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        return new PlayerStatsManager(plugin);
    }

    @Test void savesAndReloadsLongExperienceAndTalentPoints() {
        UUID uuid = UUID.randomUUID();
        PlayerStatsManager manager = manager();
        manager.getStats(uuid).setExp(8_000_000_000L);
        manager.getStats(uuid).setTalentPoints(17);
        manager.saveStats(uuid);
        manager.unloadStats(uuid);
        assertEquals(8_000_000_000L, manager.getStats(uuid).getExp());
        assertEquals(17, manager.getStats(uuid).getTalentPoints());
    }

    @Test void malformedPlayerFileIsNeverOverwrittenWithDefaults() throws Exception {
        PlayerStatsManager manager = manager();
        UUID uuid = UUID.randomUUID();
        Path file = directory.resolve("data/players/" + uuid + ".yml");
        String broken = "stats: [unterminated";
        Files.writeString(file, broken);
        manager.getStats(uuid).setStrength(99);
        manager.saveStats(uuid);
        assertEquals(broken, Files.readString(file));
        manager.unloadStats(uuid);
        manager.getStats(uuid);
        manager.saveAllStats();
        assertEquals(broken, Files.readString(file));
    }

    @Test void atomicReplacementProducesCompleteYaml() throws Exception {
        Path file = directory.resolve("player.yml");
        Files.writeString(file, "old: true\n");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("stats.name", "冒險者");
        PlayerStatsManager.saveAtomically(yaml, file.toFile());
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(file.toFile());
        assertEquals("冒險者", loaded.getString("stats.name"));
        assertFalse(loaded.contains("old"));
        assertFalse(Files.exists(directory.resolve("player.yml.tmp")));
    }

    @Test void failedTemporaryWritePreservesOriginalFile() throws Exception {
        Path file = directory.resolve("player.yml");
        Files.writeString(file, "original: true\n");
        YamlConfiguration yaml = mock(YamlConfiguration.class);
        doThrow(new IOException("disk failure")).when(yaml).save(any(java.io.File.class));
        assertThrows(IOException.class, () -> PlayerStatsManager.saveAtomically(yaml, file.toFile()));
        assertEquals("original: true\n", Files.readString(file));
    }
}

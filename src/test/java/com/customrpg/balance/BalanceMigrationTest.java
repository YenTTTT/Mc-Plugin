package com.customrpg.balance;

import com.customrpg.CustomRPG;
import com.customrpg.managers.PlayerStatsManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BalanceMigrationTest {
    @TempDir Path directory;
    private Path player() { return directory.resolve("player.yml"); }
    private YamlConfiguration legacy(int level) throws Exception {
        var yaml=new YamlConfiguration(); yaml.set("stats.level",level);
        yaml.set("stats.strength",500); yaml.set("stats.vitality",200); yaml.set("stats.statPoints",300);
        yaml.set("stats.talentPoints",20); yaml.set("stats.exp",73L); yaml.set("other.quest","keep");
        yaml.save(player().toFile()); return yaml;
    }
    @Test void exactConservedProportionsIncludeUnspentPointsAndNoAutomaticStats() {
        assertArrayEquals(new int[]{89,35,53},BalanceMigration.redistribute(new long[]{500,200,300},177));
        assertArrayEquals(new int[]{0,0,177},BalanceMigration.redistribute(new long[]{0,0,0},177));
        for(int l=1;l<=60;l++) {
            int[] result=BalanceMigration.redistribute(new long[]{Long.MAX_VALUE,3,2,1,0,0,Long.MAX_VALUE},3*(l-1));
            assertEquals(3*(l-1),Arrays.stream(result).sum());
        }
    }
    @Test void backupIsExactAndSecondMigrationDoesNotGrantOrRemovePoints() throws Exception {
        var yaml=legacy(60); byte[] original=Files.readAllBytes(player());
        assertTrue(BalanceMigration.migratePlayer(yaml,player(),directory.resolve("backups")));
        try(var files=Files.list(directory.resolve("backups"))) { assertArrayEquals(original,Files.readAllBytes(files.findFirst().orElseThrow())); }
        assertEquals(177,Arrays.stream(BalanceMigration.STATS).mapToInt(k->yaml.getInt("stats."+k)).sum());
        assertEquals("keep",yaml.getString("other.quest")); assertEquals(73,yaml.getLong("stats.exp"));
        byte[] converted=Files.readAllBytes(player());
        assertFalse(BalanceMigration.migratePlayer(yaml,player(),directory.resolve("backups")));
        assertArrayEquals(converted,Files.readAllBytes(player()));
    }
    @Test void overLevelCharactersAreClampedAndOriginalProgressRemainsInBackup() throws Exception {
        var yaml=legacy(100); yaml.set("stats.exp",500000L); yaml.save(player().toFile());
        BalanceMigration.migratePlayer(yaml,player(),directory.resolve("backups"));
        assertEquals(60,yaml.getInt("stats.level")); assertEquals(0,yaml.getLong("stats.exp"));
        assertEquals(100,yaml.getInt("balance-migration.original-level"));
        assertEquals(177,Arrays.stream(BalanceMigration.STATS).mapToInt(k->yaml.getInt("stats."+k)).sum());
    }
    @Test void manaMigrationPreservesTheOriginalFractionRatherThanRefillingOnLogin() throws Exception {
        var yaml=legacy(60); yaml.set("stats.maxMana",400); yaml.set("stats.currentMana",200); yaml.save(player().toFile());
        BalanceMigration.migratePlayer(yaml,player(),directory.resolve("backups"));
        assertEquals(198,yaml.getDouble("stats.maxMana")); assertEquals(99,yaml.getDouble("stats.currentMana"));
    }
    @Test void backupFailureNeverReplacesThePlayer() throws Exception {
        var yaml=legacy(30); byte[] original=Files.readAllBytes(player());
        Path blocked=directory.resolve("blocked"); Files.writeString(blocked,"file");
        assertThrows(IOException.class,()->BalanceMigration.migratePlayer(yaml,player(),blocked));
        assertArrayEquals(original,Files.readAllBytes(player())); assertEquals(0,yaml.getInt("stats.balance-version"));
    }
    @Test void failedAtomicReplacementCanRetryFromUnchangedFileAndVerifiedBackup() throws Exception {
        var yaml=legacy(20); byte[] original=Files.readAllBytes(player());
        Path temp=directory.resolve("player.yml.tmp"); Files.createDirectory(temp);
        assertThrows(IOException.class,()->BalanceMigration.migratePlayer(yaml,player(),directory.resolve("backups")));
        assertArrayEquals(original,Files.readAllBytes(player()));
        Files.delete(temp);
        var retry=new YamlConfiguration(); retry.load(player().toFile());
        assertTrue(BalanceMigration.migratePlayer(retry,player(),directory.resolve("backups")));
        try(var files=Files.list(directory.resolve("backups"))) { assertEquals(1,files.count()); }
    }
    @Test void malformedAndFutureVersionsAreNotSilentlyReplaced() throws Exception {
        var yaml=legacy(5); yaml.set("stats.strength",Double.NaN);
        assertThrows(IOException.class,()->BalanceMigration.migratePlayer(yaml,player(),directory.resolve("backups")));
        yaml.set("stats.strength",4); yaml.set("stats.balance-version",99);
        assertThrows(IOException.class,()->BalanceMigration.migratePlayer(yaml,player(),directory.resolve("backups")));
        yaml.set("stats.balance-version","2");
        assertThrows(IOException.class,()->BalanceMigration.migratePlayer(yaml,player(),directory.resolve("backups")));
        assertFalse(Files.exists(directory.resolve("backups")));
    }
    @Test void realPlayerManagerLoadsMigratesAndReloadsWithoutLosingTheImmutableBackup() throws Exception {
        var plugin=mock(CustomRPG.class); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        UUID id=UUID.randomUUID(); Path files=Files.createDirectories(directory.resolve("data/players"));
        var yaml=legacy(60); yaml.save(files.resolve(id+".yml").toFile());
        var first=new PlayerStatsManager(plugin); var state=first.getStats(id);
        assertEquals(89,state.getStrength()); assertEquals(35,state.getVitality()); assertEquals(53,state.getStatPoints());
        first.saveStats(id);
        var second=new PlayerStatsManager(plugin); assertEquals(89,second.getStats(id).getStrength());
        try(var backups=Files.list(directory.resolve("data/balance-backups/v2/players"))) { assertEquals(1,backups.count()); }
    }
}

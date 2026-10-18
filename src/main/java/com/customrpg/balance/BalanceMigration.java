package com.customrpg.balance;

import com.customrpg.managers.PlayerStatsManager;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** One-way versioned conversion; an immutable byte-for-byte backup precedes any replacement. */
public final class BalanceMigration {
    public static final int VERSION = 2;
    public static final String[] STATS = {"strength", "magic", "agility", "vitality", "defense", "spirit", "statPoints"};
    private BalanceMigration() {}

    public static int level(int value) { return Math.clamp(value, 1, 60); }

    /** Largest remainders preserve the relative build, including unspent points, with an exact new total. */
    public static int[] redistribute(long[] weights, int budget) {
        if (budget < 0 || weights.length == 0) throw new IllegalArgumentException("Invalid point budget");
        double sum = 0;
        for (long weight : weights) { if (weight < 0) throw new IllegalArgumentException("Negative points"); sum += weight; }
        int[] result = new int[weights.length];
        if (sum == 0) { result[result.length - 1] = budget; return result; }
        double[] remainder = new double[weights.length];
        int used = 0;
        for (int i = 0; i < weights.length; i++) {
            double exact = budget * (weights[i] / sum);
            result[i] = (int) Math.floor(exact); remainder[i] = exact - result[i]; used += result[i];
        }
        for (; used < budget; used++) {
            int best = 0;
            for (int i = 1; i < weights.length; i++) if (remainder[i] > remainder[best]) best = i;
            result[best]++; remainder[best] = -1;
        }
        return result;
    }

    public static Path backup(Path file, Path directory) throws IOException {
        byte[] original = Files.readAllBytes(file);
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
        Files.createDirectories(directory);
        Path destination = directory.resolve(file.getFileName() + "." + hash + ".bak");
        if (!Files.exists(destination)) Files.write(destination, original, java.nio.file.StandardOpenOption.CREATE_NEW);
        if (!Arrays.equals(original, Files.readAllBytes(destination))) throw new IOException("Backup verification failed: " + destination);
        return destination;
    }

    public static boolean migratePlayer(YamlConfiguration yaml, Path file, Path backups) throws IOException {
        Object rawVersion=yaml.get("stats.balance-version");
        if (rawVersion!=null&&(!(rawVersion instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<0))
            throw new IOException("Invalid balance version");
        int version = yaml.getInt("stats.balance-version", 0);
        if (version > VERSION) throw new IOException("Player uses a newer balance version");
        if (version == VERSION) return false;
        for (String stat : STATS) {
            Object raw = yaml.get("stats." + stat);
            if (raw != null && (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue())
                    || n.doubleValue() < 0 || n.doubleValue() != n.longValue())) throw new IOException("Invalid player stat: " + stat);
        }
        Object rawLevel=yaml.get("stats.level");
        if (rawLevel!=null&&(!(rawLevel instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.intValue()||n.intValue()<1))
            throw new IOException("Invalid player level");
        int oldLevel = yaml.getInt("stats.level", 1);
        int newLevel = level(oldLevel);
        long[] weights = new long[STATS.length];
        for (int i = 0; i < weights.length; i++) weights[i] = yaml.getLong("stats." + STATS[i], 0);
        int[] points = redistribute(weights, InitialBalanceModel.earnedPoints(newLevel));
        Path original = backup(file, backups);
        for (int i = 0; i < points.length; i++) yaml.set("stats." + STATS[i], points[i]);
        double oldMana=yaml.getDouble("stats.maxMana",100), oldCurrent=yaml.getDouble("stats.currentMana",oldMana);
        double mana=InitialBalanceModel.maxMana(newLevel,points[5],0);
        double fraction=Double.isFinite(oldMana)&&oldMana>0&&Double.isFinite(oldCurrent)?Math.clamp(oldCurrent/oldMana,0,1):0;
        yaml.set("stats.maxMana",mana); yaml.set("stats.currentMana",mana*fraction);
        yaml.set("stats.manaRegen",InitialBalanceModel.manaRegen(newLevel,points[5]));
        yaml.set("stats.level", newLevel);
        yaml.set("stats.talentPoints", Math.clamp(yaml.getInt("stats.talentPoints",0),0,59));
        if (oldLevel > 60) yaml.set("stats.exp", 0L);
        yaml.set("stats.balance-version", VERSION);
        yaml.set("balance-migration.original-backup", original.getFileName().toString());
        yaml.set("balance-migration.original-level", oldLevel);
        PlayerStatsManager.saveAtomically(yaml, file.toFile());
        return true;
    }
}

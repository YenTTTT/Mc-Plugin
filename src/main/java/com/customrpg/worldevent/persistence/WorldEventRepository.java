package com.customrpg.worldevent.persistence;

import com.customrpg.worldevent.core.*;
import com.customrpg.worldevent.terrain.TerrainSnapshot;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

import static java.nio.file.StandardOpenOption.*;

/** One atomic document contains schedules, participation, claim receipts and material wallets. */
public class WorldEventRepository {
    private final Path directory;
    public final Map<UUID, WorldEventInstance> events = new LinkedHashMap<>();
    public final Map<UUID, Map<String, Long>> wallets = new LinkedHashMap<>();
    public long nextTriggerAt;

    public WorldEventRepository(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory.resolve("snapshots"));
    }

    public void load() throws Exception {
        Path file = directory.resolve("state.yml");
        if (!Files.exists(file)) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile()); // Never use the convenience loader that logs errors and returns empty data.
        if (yaml.getInt("version") != 1 || !yaml.isList("events") || !yaml.contains("next-trigger-at")) throw new IOException("Unsupported/incomplete event state");
        Map<UUID, WorldEventInstance> loaded = new LinkedHashMap<>();
        for (Object value : Objects.requireNonNull(yaml.getList("events"))) {
            if(!(value instanceof Map<?,?> row)) throw new IOException("Invalid event row");
            WorldEventInstance event = decode(row);
            if (loaded.put(event.id, event) != null) throw new IOException("Duplicate event ID");
        }
        Map<UUID, Map<String, Long>> loadedWallets = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("wallets");
        if (section != null) for (String uuid : section.getKeys(false)) {
            Map<String, Long> values = new LinkedHashMap<>();
            ConfigurationSection wallet = Objects.requireNonNull(section.getConfigurationSection(uuid));
            for (String key : wallet.getKeys(false)) {
                long count = wallet.getLong(key, -1);
                if (count < 0) throw new IOException("Invalid material balance");
                values.put(key, count);
            }
            loadedWallets.put(UUID.fromString(uuid), values);
        }
        events.clear(); events.putAll(loaded); wallets.clear(); wallets.putAll(loadedWallets);
        nextTriggerAt = yaml.getLong("next-trigger-at");
    }

    public void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1); yaml.set("next-trigger-at", nextTriggerAt);
        yaml.set("events", events.values().stream().map(this::encode).toList());
        wallets.forEach((uuid, wallet) -> wallet.forEach((key, count) -> yaml.set("wallets." + uuid + "." + key, count)));
        writeDurably(directory.resolve("state.yml"), yaml.saveToString());
    }

    public void saveSnapshot(TerrainSnapshot snapshot) throws IOException {
        Path file = snapshotPath(snapshot.eventId());
        if (Files.exists(file)) throw new IOException("Snapshot is immutable: " + snapshot.eventId());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1); yaml.set("event", snapshot.eventId().toString());
        yaml.set("world", snapshot.world().toString());
        yaml.set("x", snapshot.centerX()); yaml.set("y", snapshot.centerY()); yaml.set("z", snapshot.centerZ());
        yaml.set("radius", snapshot.radius());
        List<String> blocks = snapshot.blocks().stream().map(b -> b.position().key() + "|" + b.before() + "|" + b.after()).toList();
        yaml.set("blocks", blocks); yaml.set("sha256", checksum(blocks));
        writeDurably(file, yaml.saveToString());
    }

    public TerrainSnapshot loadSnapshot(UUID id) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(snapshotPath(id).toFile());
        List<String> rows = yaml.getStringList("blocks");
        if (rows.size()>30_000 || yaml.getInt("version") != 1 || !id.toString().equals(yaml.getString("event"))
                || !checksum(rows).equals(yaml.getString("sha256"))) throw new IOException("Snapshot checksum/version mismatch: " + id);
        List<TerrainSnapshot.BlockChange> blocks = new ArrayList<>();
        for (String row : rows) {
            String[] parts = row.split("\\|", -1);
            if (parts.length != 3) throw new IOException("Invalid snapshot row");
            blocks.add(new TerrainSnapshot.BlockChange(TerrainSnapshot.Position.parse(parts[0]), parts[1], parts[2]));
        }
        return new TerrainSnapshot(id, UUID.fromString(yaml.getString("world")), yaml.getInt("x"), yaml.getInt("y"),
                yaml.getInt("z"), yaml.getInt("radius"), blocks);
    }

    private Path snapshotPath(UUID id) { return directory.resolve("snapshots").resolve(id + ".yml"); }

    public List<TerrainSnapshot> loadAllSnapshots() throws Exception {
        List<TerrainSnapshot> result=new ArrayList<>();
        try(var files=Files.newDirectoryStream(directory.resolve("snapshots"),"*.yml")) {
            for(Path path:files) result.add(loadSnapshot(UUID.fromString(path.getFileName().toString().replace(".yml",""))));
        }
        return result;
    }

    private static String checksum(List<String> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String row : rows) { digest.update(row.getBytes(StandardCharsets.UTF_8)); digest.update((byte) '\n'); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static void writeDurably(Path file, String content) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), file.getFileName().toString(), ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, WRITE, TRUNCATE_EXISTING)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(content);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            // A filesystem without atomic replacement fails closed; never begin world edits after a failed save.
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    private Map<String, Object> encode(WorldEventInstance e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.id.toString()); row.put("type", e.type); row.put("world", e.world.toString()); row.put("world-name", e.worldName);
        row.put("x", e.x); row.put("y", e.y); row.put("z", e.z); row.put("radius", e.radius);
        row.put("level-min", e.minLevel); row.put("level-max", e.maxLevel); row.put("level", e.level); row.put("seed", e.seed);
        row.put("started-at", e.startedAt); row.put("expires-at", e.expiresAt); row.put("cleanup-at", e.cleanupAt);
        row.put("state", e.state.name()); row.put("successful", e.successful); row.put("diagnostic", e.diagnostic);
        row.put("test-mode",e.testMode);
        row.put("minimum-damage", e.minimumDamage); row.put("minimum-share", e.minimumShare);
        row.put("contribution", stringKeys(e.contribution)); row.put("kill-participation", stringKeys(e.killParticipation));
        row.put("claimed", e.claimed.stream().map(UUID::toString).toList()); row.put("rewards", e.rewards);
        List<Map<String, Object>> mobs = new ArrayList<>();
        for (WorldEventInstance.EventMob m : e.mobs.values()) {
            Map<String, Object> mob = new LinkedHashMap<>();
            mob.put("id", m.id.toString()); mob.put("template", m.template); mob.put("rank", m.rank); mob.put("level", m.level);
            mob.put("entity", m.entity == null ? "" : m.entity.toString()); mob.put("state", m.state.name()); mob.put("damage", stringKeys(m.damage));
            mob.put("chunk-x",m.chunkX); mob.put("chunk-z",m.chunkZ);
            mobs.add(mob);
        }
        row.put("mobs", mobs);
        return row;
    }

    private static Map<String, Number> stringKeys(Map<UUID, ? extends Number> source) {
        Map<String, Number> result = new LinkedHashMap<>(); source.forEach((id, count) -> result.put(id.toString(), count)); return result;
    }

    private static WorldEventInstance decode(Map<?, ?> r) {
        WorldEventInstance e = new WorldEventInstance(UUID.fromString(s(r,"id")), s(r,"type"), UUID.fromString(s(r,"world")),
                s(r,"world-name"), n(r,"x").intValue(), n(r,"y").intValue(), n(r,"z").intValue(), n(r,"radius").intValue(),
                n(r,"level-min").intValue(), n(r,"level-max").intValue(), n(r,"level").intValue(), n(r,"seed").longValue(),
                n(r,"started-at").longValue(), n(r,"expires-at").longValue());
        e.state = WorldEventState.valueOf(s(r,"state")); e.successful = Boolean.TRUE.equals(r.get("successful"));
        if(r.containsKey("test-mode")&&!(r.get("test-mode") instanceof Boolean)) throw new IllegalArgumentException("Invalid test-mode flag");
        e.testMode=Boolean.TRUE.equals(r.get("test-mode"));
        e.cleanupAt = n(r,"cleanup-at").longValue(); e.diagnostic = s(r,"diagnostic");
        e.minimumDamage = n(r,"minimum-damage").doubleValue(); e.minimumShare = n(r,"minimum-share").doubleValue();
        readDoubles(r.get("contribution"), e.contribution);
        ((Map<?, ?>) r.get("kill-participation")).forEach((k,v) -> e.killParticipation.put(UUID.fromString(k.toString()), ((Number)v).intValue()));
        ((List<?>) r.get("claimed")).forEach(id -> e.claimed.add(UUID.fromString(id.toString())));
        ((Map<?, ?>) r.get("rewards")).forEach((k,v) -> { long count=((Number)v).longValue(); if(count<0) throw new IllegalArgumentException("Negative reward"); e.rewards.put(k.toString(),count); });
        for (Object value : (List<?>)r.get("mobs")) {
            Map<?, ?> m = (Map<?, ?>) value;
            WorldEventInstance.EventMob mob = new WorldEventInstance.EventMob(UUID.fromString(s(m,"id")), s(m,"template"), s(m,"rank"), n(m,"level").intValue());
            mob.state = WorldEventInstance.MobState.valueOf(s(m,"state"));
            mob.chunkX=n(m,"chunk-x").intValue(); mob.chunkZ=n(m,"chunk-z").intValue();
            if (!s(m,"entity").isBlank()) mob.entity = UUID.fromString(s(m,"entity"));
            readDoubles(m.get("damage"), mob.damage);
            if(e.mobs.put(mob.id, mob)!=null) throw new IllegalArgumentException("Duplicate mob ID");
        }
        return e;
    }
    private static String s(Map<?,?> m, String key) { return Objects.requireNonNull(m.get(key), key).toString(); }
    private static Number n(Map<?,?> m, String key) { return (Number)Objects.requireNonNull(m.get(key), key); }
    private static void readDoubles(Object source, Map<UUID,Double> target) {
        ((Map<?,?>)source).forEach((k,v) -> { double d=((Number)v).doubleValue(); if(!Double.isFinite(d)||d<0) throw new IllegalArgumentException("Invalid contribution"); target.put(UUID.fromString(k.toString()),d); });
    }
}

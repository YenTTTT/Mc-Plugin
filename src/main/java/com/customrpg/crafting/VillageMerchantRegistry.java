package com.customrpg.crafting;

import com.customrpg.managers.PlayerStatsManager;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/** An unloaded merchant keeps its identity. No second merchant is created while its last chunk is unknown. */
public final class VillageMerchantRegistry {
    public record Village(String id, UUID world, int minX, int minZ, int maxX, int maxZ,
                          double x, double y, double z, UUID merchant, int lastChunkX, int lastChunkZ) {
        public Village {
            Objects.requireNonNull(world);
            if (!id.equals(identity(world, minX, minZ, maxX, maxZ)) || minX > maxX || minZ > maxZ
                    || (long)maxX - minX > 2048 || (long)maxZ - minZ > 2048
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || x < minX || x > maxX + 1.0 || z < minZ || z > maxZ + 1.0 || y < -2048 || y > 4096)
                throw new IllegalArgumentException("Invalid village record");
        }
        public Village anchor(double nx, double ny, double nz) {
            return new Village(id, world, minX, minZ, maxX, maxZ, nx, ny, nz, merchant, ((int)Math.floor(nx)) >> 4, ((int)Math.floor(nz)) >> 4);
        }
        public Village bind(UUID entity) { return new Village(id, world, minX, minZ, maxX, maxZ, x, y, z, entity, lastChunkX, lastChunkZ); }
        public Village unloaded(int cx, int cz) { return new Village(id, world, minX, minZ, maxX, maxZ, x, y, z, merchant, cx, cz); }
    }
    public static String identity(UUID world, int minX, int minZ, int maxX, int maxZ) {
        return "v_" + UUID.nameUUIDFromBytes((world + ":" + minX + ":" + minZ + ":" + maxX + ":" + maxZ).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }
    private final File file; private final Logger logger; private boolean healthy = true;
    private Map<String,Village> villages = new LinkedHashMap<>();
    public VillageMerchantRegistry(File file, Logger logger) {
        this.file = file; this.logger = logger;
        try {
            if (!file.exists()) return;
            var data = new YamlConfiguration(); data.load(file);
            if (data.getInt("version") != 1 || data.getConfigurationSection("villages") == null) throw new IOException("Unknown village registry format");
            var root = data.getConfigurationSection("villages"); if (root.getKeys(false).size() > 20000) throw new IOException("Village registry too large");
            Set<UUID> entities = new HashSet<>();
            for (String id : root.getKeys(false)) {
                var s = root.getConfigurationSection(id); if (s == null) throw new IOException("Invalid village row");
                for (String key : List.of("min-x", "min-z", "max-x", "max-z", "x", "y", "z", "last-chunk-x", "last-chunk-z")) {
                    Object raw = s.get(key);
                    if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IOException("Invalid village coordinate: " + key);
                    if (!Set.of("x", "y", "z").contains(key) && (n.doubleValue() != Math.rint(n.doubleValue())
                            || n.doubleValue() < Integer.MIN_VALUE || n.doubleValue() > Integer.MAX_VALUE)) throw new IOException("Invalid integer coordinate: " + key);
                }
                UUID entity = s.getString("merchant", "").isEmpty() ? null : UUID.fromString(s.getString("merchant"));
                var village = new Village(id, UUID.fromString(s.getString("world")), s.getInt("min-x"), s.getInt("min-z"), s.getInt("max-x"), s.getInt("max-z"),
                        s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), entity, s.getInt("last-chunk-x"), s.getInt("last-chunk-z"));
                if (entity != null && !entities.add(entity)) throw new IOException("Duplicate merchant identity");
                villages.put(id, village);
            }
        } catch (Exception error) { healthy = false; logger.severe("[VillageMerchants] Registry unreadable; merchants paused, file retained: " + error.getMessage()); }
    }
    public boolean save(Village village) {
        if (!healthy || !villages.containsKey(village.id) && villages.size() >= 20000) return false;
        var next = new LinkedHashMap<>(villages); next.put(village.id, village);
        try {
            var data = new YamlConfiguration(); data.set("version", 1); data.createSection("villages");
            for (Village v : next.values()) {
                String p = "villages." + v.id + ".";
                data.set(p + "world", v.world.toString()); data.set(p + "min-x", v.minX); data.set(p + "min-z", v.minZ);
                data.set(p + "max-x", v.maxX); data.set(p + "max-z", v.maxZ); data.set(p + "x", v.x); data.set(p + "y", v.y); data.set(p + "z", v.z);
                data.set(p + "merchant", v.merchant == null ? "" : v.merchant.toString()); data.set(p + "last-chunk-x", v.lastChunkX); data.set(p + "last-chunk-z", v.lastChunkZ);
            }
            if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) throw new IOException("Cannot create village data directory");
            PlayerStatsManager.saveAtomically(data, file); villages = next; return true;
        } catch (IOException error) { healthy = false; logger.severe("[VillageMerchants] Save failed; merchants paused: " + error.getMessage()); return false; }
    }
    public boolean healthy() { return healthy; }
    public Map<String,Village> villages() { return Collections.unmodifiableMap(villages); }
}

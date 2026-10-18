package com.customrpg.mobs;

import com.customrpg.CustomRPG;
import com.customrpg.managers.DistanceSpawnManager;
import com.customrpg.managers.PlayerStatsManager;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import java.io.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Rare wilderness encounters reuse a normal spawn attempt; they do not create a second spawn stream. */
public final class WildernessBossManager implements Listener {
    public static final NamespacedKey ENCOUNTER = new NamespacedKey("customrpg", "wilderness_boss");
    private final CustomRPG plugin;
    private final DistanceSpawnManager spawner;
    private final LongSupplier clock;
    private final Random random;
    private final File stateFile;
    private Map<String,Reservation> records = new LinkedHashMap<>();
    private boolean enabled, healthy = true;
    private double chance = 0.01;
    private int cellSize = 1024, maxPerWorld = 2;
    private long cooldown = 1800000, lifetime = 1200000;
    private record Reservation(UUID token, UUID world, UUID entity, long nextSpawn, long expires) {}

    public WildernessBossManager(CustomRPG plugin, DistanceSpawnManager spawner) { this(plugin, spawner, System::currentTimeMillis, new Random()); }
    WildernessBossManager(CustomRPG plugin, DistanceSpawnManager spawner, LongSupplier clock, Random random) {
        this.plugin = plugin; this.spawner = spawner; this.clock = clock; this.random = random;
        stateFile = new File(plugin.getDataFolder(), "data/wilderness_bosses.yml");
        reload();
    }
    public boolean reload() {
        try {
            File file = new File(plugin.getDataFolder(), "config/wilderness_bosses.yml");
            if (!file.exists()) plugin.saveResource("config/wilderness_bosses.yml", false);
            var yaml = new YamlConfiguration(); yaml.load(file);
            boolean nextEnabled = yaml.getBoolean("enabled", true);
            double nextChance = yaml.getDouble("chance-per-spawn", 0.01);
            int nextCell = yaml.getInt("cell-size", 1024), nextMax = yaml.getInt("max-per-world", 2);
            long nextCooldown = yaml.getLong("cooldown-seconds", 1800), nextLifetime = yaml.getLong("lifetime-seconds", 1200);
            if (!Double.isFinite(nextChance) || nextChance < 0 || nextChance > 0.1 || nextCell < 256 || nextCell > 8192
                    || nextMax < 1 || nextMax > 10 || nextCooldown < 60 || nextCooldown > 86400 || nextLifetime < 60 || nextLifetime > 7200)
                throw new IllegalArgumentException("Invalid wilderness boss limits");
            Map<String,Reservation> loaded = readState();
            enabled = nextEnabled; chance = nextChance; cellSize = nextCell; maxPerWorld = nextMax;
            cooldown = nextCooldown * 1000; lifetime = nextLifetime * 1000; records = loaded; healthy = true;
            return true;
        } catch (Exception error) {
            healthy = false;
            plugin.getLogger().severe("[WildernessBosses] Paused; files retained: " + error.getMessage()); return false;
        }
    }
    private Map<String,Reservation> readState() throws Exception {
        var result = new LinkedHashMap<String,Reservation>();
        if (!stateFile.exists()) return result;
        var yaml = new YamlConfiguration(); yaml.load(stateFile);
        if (yaml.getInt("version") != 1) throw new IOException("Unknown encounter state version");
        var root = yaml.getConfigurationSection("records");
        if (root == null) throw new IOException("Missing encounter records");
        if (root.getKeys(false).size() > 4096) throw new IOException("Too many encounter records");
        for (String key : root.getKeys(false)) {
            var section = root.getConfigurationSection(key); if (section == null) throw new IOException("Invalid reservation");
            String entity = section.getString("entity", "");
            long next = section.getLong("next"), expires = section.getLong("expires");
            if (next <= 0 || expires < 0 || !key.matches("[a-zA-Z0-9_-]+")) throw new IOException("Invalid reservation values");
            result.put(key, new Reservation(UUID.fromString(section.getString("token")), UUID.fromString(section.getString("world")),
                    entity.isEmpty() ? null : UUID.fromString(entity), next, expires));
        }
        return result;
    }
    private boolean commit(Map<String,Reservation> candidate) {
        try {
            var yaml = new YamlConfiguration(); yaml.set("version", 1); yaml.createSection("records");
            for (var entry : candidate.entrySet()) {
                String path = "records." + entry.getKey() + "."; var value = entry.getValue();
                yaml.set(path + "token", value.token.toString()); yaml.set(path + "world", value.world.toString());
                yaml.set(path + "entity", value.entity == null ? "" : value.entity.toString());
                yaml.set(path + "next", value.nextSpawn); yaml.set(path + "expires", value.expires);
            }
            if (!stateFile.getParentFile().isDirectory() && !stateFile.getParentFile().mkdirs()) throw new IOException("Cannot create encounter directory");
            PlayerStatsManager.saveAtomically(yaml, stateFile); records = new LinkedHashMap<>(candidate); return true;
        } catch (IOException error) {
            healthy = false; plugin.getLogger().severe("[WildernessBosses] Persistence failed; ordinary spawns remain available: " + error.getMessage()); return false;
        }
    }
    public String trySpawn(Location location, int level, List<String> pool) {
        if (!enabled || !healthy || pool.isEmpty() || !spawner.isSpawnAllowed(location) || random.nextDouble() >= chance) return null;
        long now = clock.getAsLong(); UUID world = location.getWorld().getUID();
        String cell = world + "_" + Math.floorDiv(location.getBlockX(), cellSize) + "_" + Math.floorDiv(location.getBlockZ(), cellSize);
        Reservation previous = records.get(cell);
        if (previous != null && (previous.nextSpawn > now || previous.entity != null && previous.expires > now)) return null;
        long count = records.values().stream().filter(r -> r.world.equals(world) && r.entity != null && r.expires > now).count();
        if (count >= maxPerWorld || records.size() >= 4096) return null;
        String key = pool.get(random.nextInt(pool.size()));
        var template = plugin.getMobManager().getMobData(key);
        if (template == null || !template.isBoss() || !template.getTags().contains("wilderness-boss")
                || template.getTags().contains("event-only") || level < template.getMinLevel() || level > template.getMaxLevel()) return null;
        UUID token = UUID.randomUUID();
        var reserved = new Reservation(token, world, null, now + cooldown, now + lifetime);
        var candidate = new LinkedHashMap<>(records); candidate.put(cell, reserved);
        if (!commit(candidate)) return null;
        LivingEntity spawned;
        try {
            spawned = plugin.getMobManager().spawnReservedMob(key, location, level, mob -> {
                mob.getPersistentDataContainer().set(ENCOUNTER, PersistentDataType.STRING, token.toString());
                var identified = new LinkedHashMap<>(records);
                identified.put(cell, new Reservation(token, world, mob.getUniqueId(), reserved.nextSpawn, reserved.expires));
                if (!commit(identified)) throw new IllegalStateException("Boss identity reservation failed");
            });
        } catch (RuntimeException error) {
            plugin.getLogger().warning("[WildernessBosses] Spawn refused: " + error.getMessage()); return null;
        }
        if (spawned == null || !spawned.isValid() || spawned.isDead()) {
            release(cell); return null;
        }
        try {
            spawner.trackWildernessBoss(spawned, location, level);
        } catch (RuntimeException error) {
            spawned.remove();
            release(cell);
            plugin.getLogger().warning("[WildernessBosses] Registration failed; spawn rolled back: " + error.getMessage());
            return null;
        }
        for (Player player : location.getWorld().getNearbyPlayers(location, 80))
            if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                player.sendMessage("§6[荒野首領] §e" + ChatColor.stripColor(spawned.getCustomName()) + " §7出現在附近！注意地面蓄力提示。");
        return key;
    }
    private void release(String cell) {
        var old = records.get(cell); if (old == null) return;
        var next = new LinkedHashMap<>(records);
        next.put(cell, new Reservation(old.token, old.world, null, old.nextSpawn, 0)); commit(next);
    }
    public void tick() {
        if (!healthy) return;
        long now = clock.getAsLong(); var next = new LinkedHashMap<>(records); boolean changed = false;
        for (var entry : records.entrySet()) {
            var value = entry.getValue();
            if (value.entity != null && value.expires <= now) {
                Entity entity = plugin.getServer().getEntity(value.entity); if (entity != null) entity.remove();
                next.put(entry.getKey(), new Reservation(value.token, value.world, null, value.nextSpawn, 0)); changed = true;
            }
            if (value.entity == null && value.nextSpawn <= now) { next.remove(entry.getKey()); changed = true; }
        }
        if (changed) commit(next);
    }
    private void validateLoaded(Entity entity) {
        String token = entity.getPersistentDataContainer().get(ENCOUNTER, PersistentDataType.STRING);
        if (token == null || !healthy) return;
        boolean valid = records.values().stream().anyMatch(r -> r.token.toString().equals(token)
                && entity.getUniqueId().equals(r.entity) && r.expires > clock.getAsLong());
        if (!valid) entity.remove(); // Expired unloaded encounters never return as duplicates or award loot.
    }
    @EventHandler public void onLoad(EntitiesLoadEvent event) { for (Entity entity : event.getEntities()) validateLoaded(entity); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        for (var entry : new ArrayList<>(records.entrySet())) if (event.getEntity().getUniqueId().equals(entry.getValue().entity)) release(entry.getKey());
    }
    public void restoreLoaded() {
        for (World world : plugin.getServer().getWorlds()) for (LivingEntity mob : world.getLivingEntities()) validateLoaded(mob);
    }
    public String diagnostic() {
        long active = records.values().stream().filter(r -> r.entity != null && r.expires > clock.getAsLong()).count();
        return "荒野首領: " + (enabled ? "啟用" : "停用") + "，" + (healthy ? "資料正常" : "資料暫停，請查 console")
                + "，全服保留 " + active + "，每世界上限 " + maxPerWorld + "，機率 " + (chance * 100) + "%";
    }
    public void shutdown() {
        if (!healthy) return;
        for (var entry : new ArrayList<>(records.entrySet())) {
            UUID id = entry.getValue().entity; Entity entity = id == null ? null : plugin.getServer().getEntity(id);
            if (entity != null) { entity.remove(); release(entry.getKey()); }
        }
    }
}

package com.customrpg.integration.npc;

import fr.skytasul.quests.api.npcs.BqInternalNpc;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * NpcManager — 管理 CustomRPG NPC 實體的生命週期
 *
 * 職責：
 * 1. 從 config/npcs.yml 載入 / 儲存 NPC 資料
 * 2. 在世界/區塊載入時自動生成或找回 NPC 實體
 * 3. 實作 BqInternalNpc（每個 NpcEntry 就是一個 BQ 可辨識的 NPC）
 * 4. 提供給 NpcFactory 所需的所有資料
 */
public class NpcManager {

    /** PDC key：用來識別哪個實體是我們的 NPC，值為 internalId */
    public static final NamespacedKey NPC_ID_KEY = new NamespacedKey("customrpg", "npc_id");

    private final JavaPlugin plugin;
    /** internalId ("npc_1", "npc_2", ...) → 對應的 NpcEntry */
    private final Map<String, NpcEntry> npcs = new LinkedHashMap<>();
    private int nextId = 1;
    private final File configFile;
    private YamlConfiguration config;

    public NpcManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config/npcs.yml");
        loadConfig();
    }

    // ─── 設定檔 ───────────────────────────────────────────────────

    private void loadConfig() {
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            config = new YamlConfiguration();
            config.set("next_id", 1);
            saveConfig();
            return;
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        nextId = config.getInt("next_id", 1);

        ConfigurationSection section = config.getConfigurationSection("npcs");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection sec = section.getConfigurationSection(id);
            if (sec == null) continue;

            String name      = sec.getString("name", "NPC");
            String worldName = sec.getString("world", "world");
            double x         = sec.getDouble("x");
            double y         = sec.getDouble("y");
            double z         = sec.getDouble("z");
            float  yaw       = (float) sec.getDouble("yaw", 0.0);
            float  pitch     = (float) sec.getDouble("pitch", 0.0);
            EntityType type  = parseEntityType(sec.getString("entity_type", "VILLAGER"));
            String uuidStr   = sec.getString("entity_uuid");

            World world = Bukkit.getWorld(worldName);
            Location loc = new Location(world, x, y, z, yaw, pitch);
            NpcEntry entry = new NpcEntry(id, name, loc, type);
            if (uuidStr != null) {
                try { entry.entityUuid = UUID.fromString(uuidStr); }
                catch (IllegalArgumentException ignored) {}
            }
            entry.questId = sec.getInt("quest_id", -1);
            entry.questKey = sec.getString("quest_key", null);
            npcs.put(id, entry);
        }
        plugin.getLogger().info("[NpcManager] 已從設定檔載入 " + npcs.size() + " 個 NPC。");
    }

    private EntityType parseEntityType(String s) {
        try { return EntityType.valueOf(s); }
        catch (IllegalArgumentException e) { return EntityType.VILLAGER; }
    }

    public void saveConfig() {
        try {
            config.set("next_id", nextId);
            for (NpcEntry entry : npcs.values()) {
                writeEntryToConfig(entry);
            }
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[NpcManager] 儲存 npcs.yml 失敗：" + e.getMessage());
        }
    }

    private void writeEntryToConfig(NpcEntry entry) {
        String path = "npcs." + entry.internalId;
        config.set(path + ".name",        entry.name);
        Location loc = entry.getLocation();
        config.set(path + ".world",       loc.getWorld() != null ? loc.getWorld().getName() : "world");
        config.set(path + ".x",           loc.getX());
        config.set(path + ".y",           loc.getY());
        config.set(path + ".z",           loc.getZ());
        config.set(path + ".yaw",         (double) loc.getYaw());
        config.set(path + ".pitch",       (double) loc.getPitch());
        config.set(path + ".entity_type", entry.entityType.name());
        if (entry.entityUuid != null) {
            config.set(path + ".entity_uuid", entry.entityUuid.toString());
        }
        config.set(path + ".quest_id", entry.questId);
        config.set(path + ".quest_key", entry.questKey);
    }

    // ─── NPC 生成 / 管理 ──────────────────────────────────────────

    /**
     * 伺服器啟動後掃描所有世界，找回或重新生成所有 NPC。
     * 應在 onEnable 後延遲呼叫，確保所有世界都已載入。
     */
    public void spawnAllNpcs() {
        for (NpcEntry entry : npcs.values()) {
            if (entry.getLocation().getWorld() != null) {
                spawnOrFind(entry);
            }
        }
    }

    /** 嘗試按 UUID 找回實體；找不到時重新生成。 */
    public void spawnOrFind(NpcEntry entry) {
        if (entry.entityUuid != null) {
            for (World world : Bukkit.getWorlds()) {
                Entity found = world.getEntities().stream()
                        .filter(e -> entry.entityUuid.equals(e.getUniqueId()))
                        .findFirst().orElse(null);
                if (found != null && found.isValid()) {
                    entry.setEntity(found);
                    applyNpcProperties(found, entry);
                    return;
                }
            }
        }
        spawnEntity(entry);
    }

    private void spawnEntity(NpcEntry entry) {
        Location loc = entry.getLocation();
        if (loc.getWorld() == null) return;
        // 確保區塊載入
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            loc.getWorld().loadChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        }
        Entity entity = loc.getWorld().spawnEntity(loc, entry.entityType);
        entry.entityUuid = entity.getUniqueId();
        entry.setEntity(entity);
        applyNpcProperties(entity, entry);
        saveConfig();
    }

    private void applyNpcProperties(Entity entity, NpcEntry entry) {
        entity.setCustomName("§6" + entry.name);
        entity.setCustomNameVisible(true);
        entity.setPersistent(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.getPersistentDataContainer().set(NPC_ID_KEY, PersistentDataType.STRING, entry.internalId);

        if (entity instanceof Mob mob)       mob.setAI(false);
        if (entity instanceof LivingEntity l) {
            l.setCollidable(false);
            l.setRemoveWhenFarAway(false);
        }
        if (entity instanceof Villager v) {
            v.setProfession(Villager.Profession.NITWIT);
            v.setVillagerType(Villager.Type.PLAINS);
        }
    }

    // ─── 公開 API ─────────────────────────────────────────────────

    /**
     * 由指令 /rpgnpc create 呼叫，自動分配 internalId。
     */
    public NpcEntry createNpc(String name, Location location, EntityType type) {
        String id = "npc_" + nextId++;
        NpcEntry entry = new NpcEntry(id, name, location, type);
        npcs.put(id, entry);
        spawnEntity(entry);
        return entry;
    }

    /**
     * 由 NpcFactory.create() 呼叫（BQ 編輯器建立 NPC 時）。
     * BQ 已指定好 id，不需自動分配。
     */
    public NpcEntry createNpc(Location location, EntityType type, String name, String id) {
        NpcEntry entry = new NpcEntry(id, name, location, type);
        npcs.put(id, entry);
        spawnEntity(entry);
        return entry;
    }

    public boolean deleteNpc(String id) {
        NpcEntry entry = npcs.remove(id);
        if (entry == null) return false;
        if (entry.entity != null && entry.entity.isValid()) {
            entry.entity.remove();
        }
        config.set("npcs." + id, null);
        saveConfig();
        return true;
    }

    public void changeNpcType(NpcEntry entry, EntityType newType) {
        if (entry.entity != null && entry.entity.isValid()) {
            entry.entity.remove();
        }
        entry.setEntity(null);
        entry.entityType   = newType;
        entry.entityUuid   = null;
        spawnEntity(entry);
    }

    public void forceRespawn(NpcEntry entry) {
        if (entry.entity != null && entry.entity.isValid()) {
            entry.entity.remove();
        }
        entry.setEntity(null);
        entry.entityUuid = null;
        spawnEntity(entry);
    }

    /** 綁定任務 ID（-1 = 取消綁定），並儲存設定。 */
    public void bindQuest(NpcEntry entry, int questId) {
        entry.questId = questId;
        saveConfig();
    }

    public boolean isNpcEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(NPC_ID_KEY);
    }

    public NpcEntry getNpcByEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(NPC_ID_KEY, PersistentDataType.STRING);
        return id != null ? npcs.get(id) : null;
    }

    public NpcEntry getNpcByInternalId(String id) {
        return npcs.get(id);
    }

    public Collection<String>   getAllInternalIds() { return Collections.unmodifiableSet(npcs.keySet()); }
    public Collection<NpcEntry> getAllNpcs()        { return Collections.unmodifiableCollection(npcs.values()); }

    /** 區塊載入事件：若某個 NPC 落在此區塊中且尚未生成，延遲重新生成。 */
    public void handleChunkLoad(org.bukkit.Chunk chunk) {
        for (NpcEntry entry : npcs.values()) {
            Location loc = entry.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().equals(chunk.getWorld())) continue;
            if ((loc.getBlockX() >> 4) == chunk.getX() && (loc.getBlockZ() >> 4) == chunk.getZ()) {
                if (!entry.isSpawned()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> spawnOrFind(entry), 5L);
                }
            }
        }
    }

    /** 伺服器關閉時清理所有 NPC 實體（保留 UUID，下次啟動能找回）。 */
    public void shutdown() {
        saveConfig();
        for (NpcEntry entry : npcs.values()) {
            if (entry.entity != null && entry.entity.isValid()) {
                entry.entity.remove();
            }
        }
    }

    // ─── NpcEntry（BqInternalNpc 實作）────────────────────────────

    /**
     * 代表單個 NPC 的資料與實體指標。
     * 同時實作 BqInternalNpc，讓 BQ 可直接操作此物件。
     */
    public class NpcEntry implements BqInternalNpc {

        private final String     internalId;
        String                   name;
        private final Location   location;
        EntityType               entityType;
        UUID                     entityUuid;
        int                      questId  = -1;  // -1 = 未綁定 (BeautyQuests)
        String                   questKey = null; // null = 未綁定 (native quest system)
        private Entity           entity;

        NpcEntry(String internalId, String name, Location location, EntityType entityType) {
            this.internalId  = internalId;
            this.name        = name;
            this.location    = location.clone();
            this.entityType  = entityType;
        }

        @Override public String   getInternalId()             { return internalId; }
        @Override public String   getName()                    { return name; }
        @Override public boolean  isSpawned()                  { return entity != null && entity.isValid(); }
        @Override public Entity   getEntity()                  { return entity; }
        @Override public Location getLocation()                { return location.clone(); }
        @Override public boolean  setNavigationPaused(boolean p) { return false; }

        void setEntity(Entity entity) { this.entity = entity; }
        public EntityType getEntityType()  { return entityType; }
        public String     getId()          { return internalId; }
        public int        getQuestId()     { return questId; }
        public String     getQuestKey()    { return questKey; }
        public void       setQuestKey(String key) { this.questKey = key; }

        public void rename(String newName) {
            this.name = newName;
            if (entity != null && entity.isValid()) {
                entity.setCustomName("§6" + newName);
            }
            saveConfig();
        }
    }
}







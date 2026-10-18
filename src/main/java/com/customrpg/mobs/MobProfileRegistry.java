package com.customrpg.mobs;

import com.customrpg.CustomRPG;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import java.io.File;
import java.util.*;

/** Upgrade-safe overlay: deployed mob stat/loot files are never overwritten. */
public final class MobProfileRegistry {
    private final CustomRPG plugin;
    private Map<String,MobProfile> profiles = Map.of();
    private String lastError = "";
    public MobProfileRegistry(CustomRPG plugin) { this.plugin = plugin; reload(); }
    public MobProfile get(String id) { return id == null ? null : profiles.get(id.toLowerCase(Locale.ROOT)); }
    public Collection<MobProfile> all() { return profiles.values(); }
    public String lastError() { return lastError; }

    public boolean reload() {
        try {
            File file = new File(plugin.getDataFolder(), "config/mobs/encounters.yml");
            if (!file.exists()) plugin.saveResource("config/mobs/encounters.yml", false);
            var yaml = new YamlConfiguration(); yaml.load(file);
            var root = yaml.getConfigurationSection("profiles");
            if (root == null) throw new IllegalArgumentException("Missing profiles section");
            Map<String,MobProfile> next = new LinkedHashMap<>();
            Set<String> seen = new HashSet<>();
            for (String raw : root.getKeys(false)) {
                String id = raw.toLowerCase(Locale.ROOT);
                if (!seen.add(id)) throw new IllegalArgumentException("Duplicate profile: " + id);
                var section = root.getConfigurationSection(raw);
                if (section == null) throw new IllegalArgumentException("Invalid profile: " + raw);
                if (!section.getBoolean("enabled", true)) continue;
                next.put(id, parse(id, section));
            }
            profiles = Collections.unmodifiableMap(next); lastError = "";
            plugin.getLogger().info("[MobEncounters] Loaded " + profiles.size() + " appearance/ability profiles");
            return true;
        } catch (Exception error) {
            lastError = error.getMessage();
            plugin.getLogger().severe("[MobEncounters] Invalid configuration; previous profiles retained: " + lastError);
            return false;
        }
    }
    private MobProfile parse(String id, ConfigurationSection section) {
        List<MobAbility> abilities = section.getStringList("mechanics.abilities").stream()
                .map(s -> MobAbility.valueOf(s.toUpperCase(Locale.ROOT))).toList();
        String color = section.getString("visual.color", "#aaaaaa").replace("#", "");
        String particle = section.getString("visual.particle", "DUST").toUpperCase(Locale.ROOT);
        if (!Set.of("DUST", "CRIT", "FLAME", "SNOWFLAKE", "ENCHANT", "SPORE_BLOSSOM_AIR", "ELECTRIC_SPARK", "PORTAL", "SOUL", "CLOUD").contains(particle))
            throw new IllegalArgumentException("Unsupported aura particle: " + particle);
        Particle.valueOf(particle);
        Map<String,String> equipment = new LinkedHashMap<>();
        var items = section.getConfigurationSection("visual.equipment");
        if (items != null) for (String slot : items.getKeys(false)) {
            if (!Set.of("helmet", "chestplate", "leggings", "boots", "main-hand", "off-hand").contains(slot))
                throw new IllegalArgumentException("Unknown equipment slot: " + slot);
            String material = items.getString(slot, "AIR").toUpperCase(Locale.ROOT);
            Material.valueOf(material); equipment.put(slot, material);
        }
        return new MobProfile(id, EntityType.valueOf(section.getString("visual.entity", "ZOMBIE").toUpperCase(Locale.ROOT)),
                section.getDouble("visual.scale", 1), Integer.parseInt(color, 16), particle, equipment, abilities,
                section.getDouble("mechanics.cooldown-seconds", 8), section.getDouble("mechanics.windup-seconds", 1),
                section.getDouble("mechanics.radius", 3), section.getDouble("mechanics.range", 12),
                section.getDouble("mechanics.damage-multiplier", 1), section.getDouble("mechanics.enrage-at", 0),
                section.getDouble("mechanics.speed-bonus", 0), section.getString("mechanics.temperament", "aggressive"),
                section.getBoolean("mechanics.legacy-behavior", false), section.getString("description", id));
    }
}

package com.customrpg.crafting;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;

/** Validated alchemy definitions; custom inputs reuse existing monster material identities. */
public final class PotionCatalog {
    public enum Resource { HEALTH, MANA }
    public record Input(String customId, Material material) { public boolean custom() { return customId != null; } }
    public record Potion(String id, String name, Resource resource, double restore, int color, List<Input> inputs) { }
    private final Map<String,Potion> potions;
    private final long cooldownMillis;

    public PotionCatalog(File file, CraftingCatalog crafting) throws Exception {
        var config = new YamlConfiguration(); config.load(file);
        cooldownMillis = (long)(number(config.get("cooldown-seconds"), 1, 3600) * 1000);
        var root = config.getConfigurationSection("potions");
        if (root == null || root.getKeys(false).isEmpty() || root.getKeys(false).size() > 64)
            throw new IllegalArgumentException("Expected 1..64 potions");
        var loaded = new LinkedHashMap<String,Potion>();
        for (String id : root.getKeys(false)) {
            if (!id.matches("[a-z][a-z0-9_]{0,47}")) throw new IllegalArgumentException("Invalid potion ID: " + id);
            var section = root.getConfigurationSection(id);
            if (section == null) throw new IllegalArgumentException("Invalid potion: " + id);
            String name = section.getString("name", "");
            if (name.isBlank()) throw new IllegalArgumentException("Missing potion name: " + id);
            var resource = Resource.valueOf(section.getString("resource", "").toUpperCase(Locale.ROOT));
            double restore = number(section.get("restore"), .1, 10000);
            String color = section.getString("color", "");
            if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid potion color: " + id);
            List<Input> inputs = new ArrayList<>();
            var raw = section.getList("ingredients");
            if (raw == null || raw.size() < 3 || raw.size() > 9) throw new IllegalArgumentException("Expected 3..9 ingredients: " + id);
            for (Object value : raw) {
                if (!(value instanceof String text)) throw new IllegalArgumentException("Invalid ingredient: " + id);
                if (text.startsWith("custom:")) {
                    String key = text.substring(7); var input = crafting.materials().get(key);
                    if (input == null) throw new IllegalArgumentException("Unknown custom potion input: " + key);
                    inputs.add(new Input(key, input.material()));
                } else {
                    Material material = Material.valueOf(text);
                    if (Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.WATER, Material.LAVA).contains(material))
                        throw new IllegalArgumentException("Invalid potion ingredient: " + text);
                    inputs.add(new Input(null, material));
                }
            }
            if (inputs.stream().filter(i -> i.material == Material.GLASS_BOTTLE && !i.custom()).count() != 1)
                throw new IllegalArgumentException("Each potion needs one empty glass bottle: " + id);
            loaded.put(id, new Potion(id, name, resource, restore, Integer.parseInt(color.substring(1), 16), List.copyOf(inputs)));
        }
        potions = Collections.unmodifiableMap(loaded);
    }
    private static double number(Object value, double min, double max) {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < min || n.doubleValue() > max)
            throw new IllegalArgumentException("Invalid potion amount/cooldown");
        return n.doubleValue();
    }
    public Map<String,Potion> potions() { return potions; }
    public long cooldownMillis() { return cooldownMillis; }
}

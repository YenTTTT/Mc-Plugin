package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import java.util.*;
import java.util.function.Function;

public final class CraftingLootListener implements Listener {
    private final CustomRPG plugin; private final CraftingCatalog catalog; private final CraftingItems items; private final Random random;
    private final Function<Location,String> themeReader;
    public CraftingLootListener(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items) { this(plugin, catalog, items, new Random()); }
    CraftingLootListener(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items, Random random) {
        this(plugin, catalog, items, random, CraftingLootListener::themeAt);
    }
    CraftingLootListener(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items, Random random, Function<Location,String> themeReader) {
        this.plugin = plugin; this.catalog = catalog; this.items = items; this.random = random; this.themeReader = themeReader;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if(com.customrpg.utils.ControlledSummons.is(event.getEntity()))return;
        if (!catalog.enabled("drops.enabled")) return;
        var entity = event.getEntity(); if (entity instanceof Player || entity instanceof Tameable pet && pet.isTamed()) return;
        String key = plugin.getMobManager().getCustomMobKey(entity);
        var data = key == null ? null : plugin.getMobManager().getMobData(key);
        if (data == null && !(entity instanceof Enemy)) return;
        if (data != null && (data.getTags().contains("event-only") || data.getTags().contains("summon-only")
                || Set.of("magma_core_medium", "magma_core_small").contains(key))) return;
        Player killer = entity.getKiller();
        if (killer == null && entity.hasMetadata("beast_kill_owner")) {
            try { killer = plugin.getServer().getPlayer(UUID.fromString(entity.getMetadata("beast_kill_owner").getFirst().asString())); }
            catch (RuntimeException ignored) { }
        }
        if (killer == null || !killer.isOnline() || killer.getGameMode() != GameMode.SURVIVAL && killer.getGameMode() != GameMode.ADVENTURE) return;
        int level = data != null ? plugin.getMobManager().getMobLevel(entity) : plugin.getWorldZoneManager().getBaseLevel(entity.getLocation());
        var tier = catalog.tier(level); String theme = themeReader.apply(entity.getLocation());
        if (data != null) for (String preferred : List.of("end", "nether", "cave", "ice", "snow", "mountain", "swamp", "desert", "forest", "ocean", "plains"))
            if (data.getTags().contains(preferred)) { theme = preferred.equals("snow") ? "ice" : preferred; break; }
        var selected = catalog.theme(theme); boolean boss = data != null && data.isBoss();
        if (random.nextDouble() < catalog.chance("essence-chance"))
            event.getDrops().add(items.material(selected.id() + "_essence_t" + tier.id(), boss ? 3 + random.nextInt(3) : 1 + random.nextInt(2)));
        if (random.nextDouble() < catalog.chance("binding-chance"))
            event.getDrops().add(items.material("monster_binding_t" + tier.id(), boss ? 2 + random.nextInt(3) : 1));
        if (boss && random.nextDouble() < catalog.chance("boss-core-chance"))
            event.getDrops().add(items.material("boss_core_t" + tier.id(), 1 + random.nextInt(2)));
    }
    public static String themeAt(Location location) {
        World world = location.getWorld();
        if (world.getEnvironment() == World.Environment.NETHER) return "nether";
        if (world.getEnvironment() == World.Environment.THE_END) return "end";
        String biome = world.getBiome(location).name().toLowerCase(Locale.ROOT);
        if (location.getY() < 62 && location.getY() < world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES) - 8) return "cave";
        if (biome.contains("snow") || biome.contains("ice") || biome.contains("frozen")) return "ice";
        if (biome.contains("ocean") || biome.contains("beach") || biome.contains("shore") || biome.contains("river")) return "ocean";
        if (biome.contains("desert") || biome.contains("badlands")) return "desert";
        if (biome.contains("swamp")) return "swamp";
        if (biome.contains("peak") || biome.contains("mountain") || biome.contains("windswept") || biome.contains("slope")) return "mountain";
        if (biome.contains("forest") || biome.contains("taiga") || biome.contains("jungle")) return "forest";
        return "plains";
    }
}

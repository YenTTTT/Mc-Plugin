package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import java.io.File;

public final class CraftingModule {
    private final CustomRPG plugin; private final CraftingCatalog catalog;
    private final CraftingItems items; private final CraftingRecipes recipes;
    private final VillageMerchantManager merchants; private final CraftingBrowser browser;
    private final CraftingLootListener loot;
    private final CraftingRangedListener ranged;
    private final PotionManager potions;
    public CraftingModule(CustomRPG plugin) throws Exception {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "config/crafting_progression.yml");
        if (!file.exists()) plugin.saveResource("config/crafting_progression.yml", false);
        catalog = new CraftingCatalog(file); registerTemplates(); items = new CraftingItems(plugin, catalog);
        File potionFile = new File(plugin.getDataFolder(), "config/potions.yml");
        if (!potionFile.exists()) plugin.saveResource("config/potions.yml", false);
        var potionCatalog = new PotionCatalog(potionFile, catalog);
        potions = new PotionManager(plugin, potionCatalog, items, new PotionItems(potionCatalog));
        recipes = new CraftingRecipes(plugin, catalog, items, null, potions); merchants = new VillageMerchantManager(plugin, catalog, items);
        browser = new CraftingBrowser(plugin, catalog, items, merchants, potions);
        loot = new CraftingLootListener(plugin, catalog, items);
        ranged = new CraftingRangedListener(plugin, catalog, items);
    }
    public void registerTemplates() {
        for (var gear : catalog.gear().values()) {
            if (gear.kind().weapon()) plugin.getWeaponManager().registerSupplemental(catalog.weapon(gear));
            else plugin.getEquipmentManager().registerSupplemental(catalog.equipment(gear));
        }
    }
    public void start() {
        var events = plugin.getServer().getPluginManager();
        if (catalog.enabled("enabled")) {
            recipes.register(); potions.register(); events.registerEvents(recipes, plugin); events.registerEvents(loot, plugin); merchants.start();
        }
        events.registerEvents(potions, plugin);
        events.registerEvents(merchants, plugin); events.registerEvents(browser, plugin); events.registerEvents(ranged, plugin);
        var command = plugin.getCommand("rpgcraft");
        if (command != null) { command.setExecutor(browser); command.setTabCompleter(browser); }
        plugin.getLogger().info("[Crafting] Added " + catalog.gear().size() + " products, " + potions.catalog().potions().size() + " potions and " + catalog.materials().size() + " materials; " + merchants.status());
    }
    public void shutdown() {
        org.bukkit.event.HandlerList.unregisterAll(browser); org.bukkit.event.HandlerList.unregisterAll(recipes);
        org.bukkit.event.HandlerList.unregisterAll(merchants); org.bukkit.event.HandlerList.unregisterAll(loot);
        org.bukkit.event.HandlerList.unregisterAll(ranged);
        org.bukkit.event.HandlerList.unregisterAll(potions);
        browser.shutdown(); recipes.shutdown(); potions.shutdown(); merchants.shutdown();
    }
}

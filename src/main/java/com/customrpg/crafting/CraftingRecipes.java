package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import java.util.*;

/** Vanilla handles taking output, shift-click batches, remainders and input consumption. */
public final class CraftingRecipes implements Listener {
    private final CustomRPG plugin; private final CraftingCatalog catalog; private final CraftingItems items;
    interface Choices { RecipeChoice custom(ItemStack item); RecipeChoice iron(); }
    private final Choices choices;
    private final PotionManager potions;
    private final Map<NamespacedKey,CraftingCatalog.Gear> recipes = new LinkedHashMap<>();
    private final Map<NamespacedKey,CraftingCatalog.Tier> refinements = new LinkedHashMap<>();
    public CraftingRecipes(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items) {
        this(plugin, catalog, items, null, null);
    }
    CraftingRecipes(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items, Choices choices) {
        this(plugin, catalog, items, choices, null);
    }
    CraftingRecipes(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items, Choices choices, PotionManager potions) {
        this.potions = potions;
        this.plugin = plugin; this.catalog = catalog; this.items = items;
        this.choices = choices == null ? new Choices() {
            public RecipeChoice custom(ItemStack item) { return new RecipeChoice.ExactChoice(item); }
            public RecipeChoice iron() { return new RecipeChoice.MaterialChoice(Material.IRON_INGOT); }
        } : choices;
        for (var gear : catalog.gear().values()) recipes.put(new NamespacedKey("customrpg", gear.id()), gear);
        for (var tier : catalog.tiers()) refinements.put(new NamespacedKey("customrpg", "forge_refine_core_t" + tier.id()), tier);
    }
    public void register() {
        for (var entry : recipes.entrySet()) {
            var gear = entry.getValue(); var recipe = new ShapedRecipe(entry.getKey(), items.product(gear.id()));
            recipe.shape(gear.kind().shape.toArray(String[]::new)); recipe.setGroup("customrpg_forge_t" + gear.tier().id());
            recipe.setIngredient('M', choices.custom(items.material(gear.essence(), 1)));
            recipe.setIngredient('B', choices.custom(items.material(gear.binding(), 1)));
            if (gear.needsBossCore()) recipe.setIngredient('C', choices.custom(items.material(gear.core(), 1)));
            else recipe.setIngredient('C', choices.iron());
            if (!plugin.getServer().addRecipe(recipe)) throw new IllegalStateException("Recipe key already registered: " + entry.getKey());
            registered.add(entry.getKey());
        }
        for (var entry : refinements.entrySet()) {
            var tier = entry.getValue();
            var recipe = new ShapelessRecipe(entry.getKey(), items.material("monster_binding_t" + tier.id(), 8));
            recipe.addIngredient(choices.custom(items.material("boss_core_t" + tier.id(), 1)));
            if (!plugin.getServer().addRecipe(recipe)) throw new IllegalStateException("Refinement recipe key conflict: " + entry.getKey());
            registered.add(entry.getKey());
        }
    }
    private final List<NamespacedKey> registered = new ArrayList<>();
    private CraftingCatalog.Gear definition(Recipe recipe) { return recipe instanceof Keyed keyed ? recipes.get(keyed.getKey()) : null; }
    public boolean matches(CraftingCatalog.Gear gear, ItemStack[] grid) {
        if (grid.length != 9) return false; // Inventory 2x2 cannot produce progression gear.
        List<String> shape = gear.kind().shape;
        int minX = 3, maxX = 0;
        for (String row : shape) for (int x = 0; x < 3; x++) if (row.charAt(x) != ' ') { minX = Math.min(minX, x); maxX = Math.max(maxX, x); }
        int width = maxX - minX + 1;
        for (int offset = 0; offset <= 3 - width; offset++) for (boolean mirror : List.of(false, true)) {
            boolean match = true;
            for (int y = 0; y < 3 && match; y++) for (int x = 0; x < 3 && match; x++) {
                int local = x - offset; char expected = local < 0 || local >= width ? ' ' : shape.get(y).charAt(minX + (mirror ? width - 1 - local : local));
                ItemStack actual = grid[y * 3 + x];
                match = switch (expected) {
                    case 'M' -> gear.essence().equals(items.materialId(actual));
                    case 'B' -> gear.binding().equals(items.materialId(actual));
                    case 'C' -> gear.needsBossCore() ? gear.core().equals(items.materialId(actual))
                            : actual != null && actual.getType() == Material.IRON_INGOT && !items.custom(actual);
                    default -> CraftingItems.empty(actual);
                };
            }
            if (match) return true;
        }
        return false;
    }
    private boolean safe(Recipe recipe, ItemStack[] matrix) {
        var potion = potions == null ? null : potions.definition(recipe);
        if (potion != null) return potions.matches(potion, matrix);
        var refinement = recipe instanceof Keyed key ? refinements.get(key.getKey()) : null;
        if (refinement != null) return matrix.length == 9 && Arrays.stream(matrix).filter(s -> !CraftingItems.empty(s)).count() == 1
                && Arrays.stream(matrix).anyMatch(s -> ("boss_core_t" + refinement.id()).equals(items.materialId(s)));
        var gear = definition(recipe);
        return gear != null ? matches(gear, matrix) : Arrays.stream(matrix).noneMatch(items::custom);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!safe(event.getRecipe(), event.getInventory().getMatrix())) event.getInventory().setResult(null);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        var potion = potions == null ? null : potions.definition(event.getRecipe());
        if (potion != null) {
            if (!potions.matches(potion, event.getInventory().getMatrix()) || !potions.validOutput(potion, event.getCurrentItem())) event.setCancelled(true);
            return;
        }
        var gear = definition(event.getRecipe());
        var refinement = event.getRecipe() instanceof Keyed key ? refinements.get(key.getKey()) : null;
        if (!safe(event.getRecipe(), event.getInventory().getMatrix())
                || gear != null && !gear.id().equals(items.productId(event.getCurrentItem()))
                || refinement != null && (!("monster_binding_t" + refinement.id()).equals(items.materialId(event.getCurrentItem()))
                || event.getCurrentItem().getAmount() != 8)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmith(PrepareSmithingEvent event) {
        if (Arrays.stream(event.getInventory().getContents()).anyMatch(items::isMaterial)) event.getInventory().setResult(null);
        else for (ItemStack input : event.getInventory().getContents()) {
            String id = items.productId(input);
            if (id != null && !id.equals(items.productId(event.getInventory().getResult()))) { event.getInventory().setResult(null); break; }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { if (items.isMaterial(event.getItemInHand())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if ((event.getRightClicked() instanceof org.bukkit.entity.Frog || event.getRightClicked() instanceof org.bukkit.entity.Allay)
                && items.isMaterial(event.getPlayer().getInventory().getItem(event.getHand()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        String id = items.materialId(event.getItem().getItemStack()); if (id == null) return;
        if (id.startsWith("boss_core_t")) player.discoverRecipe(new NamespacedKey("customrpg", "forge_refine_core_t" + catalog.materials().get(id).tier()));
        if (!id.contains("_essence_t")) return;
        player.discoverRecipes(recipes.entrySet().stream().filter(e -> e.getValue().essence().equals(id)).map(Map.Entry::getKey).toList());
    }
    public void shutdown() { for (NamespacedKey key : registered) plugin.getServer().removeRecipe(key); registered.clear(); }
}

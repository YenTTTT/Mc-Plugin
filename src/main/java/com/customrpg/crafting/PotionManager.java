package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Native consumption owns removing one potion and returning the bottle, including stacked/offhand use. */
public final class PotionManager implements Listener {
    public static final NamespacedKey COOLDOWN = new NamespacedKey("customrpg", "potion_cooldown_until");
    private final CustomRPG plugin;
    private final PotionCatalog catalog;
    private final CraftingItems ingredients;
    private final PotionItems items;
    private final LongSupplier clock;
    private final Map<NamespacedKey,PotionCatalog.Potion> recipes = new LinkedHashMap<>();
    private final Set<NamespacedKey> registered = new LinkedHashSet<>();
    private final Map<PlayerItemConsumeEvent,String> pending = new IdentityHashMap<>();
    public PotionManager(CustomRPG plugin, PotionCatalog catalog, CraftingItems ingredients, PotionItems items) {
        this(plugin, catalog, ingredients, items, System::currentTimeMillis);
    }
    PotionManager(CustomRPG plugin, PotionCatalog catalog, CraftingItems ingredients, PotionItems items, LongSupplier clock) {
        this.plugin = plugin; this.catalog = catalog; this.ingredients = ingredients; this.items = items; this.clock = clock;
        for (var potion : catalog.potions().values()) recipes.put(new NamespacedKey("customrpg", "potion_" + potion.id()), potion);
    }
    public PotionCatalog catalog() { return catalog; }
    public PotionItems items() { return items; }
    public PotionCatalog.Potion definition(Recipe recipe) { return recipe instanceof Keyed key ? recipes.get(key.getKey()) : null; }
    public ItemStack ingredient(PotionCatalog.Input input) {
        return input.custom() ? ingredients.material(input.customId(), 1) : new ItemStack(input.material());
    }
    public boolean matches(PotionCatalog.Potion potion, ItemStack[] matrix) {
        if (matrix.length != 9) return false;
        List<ItemStack> remaining = new ArrayList<>(Arrays.stream(matrix).filter(s -> !CraftingItems.empty(s)).toList());
        if (remaining.size() != potion.inputs().size()) return false;
        for (var input : potion.inputs()) {
            int match = -1;
            for (int i = 0; i < remaining.size(); i++) {
                var stack = remaining.get(i);
                if (input.custom() ? input.customId().equals(ingredients.materialId(stack))
                        : stack.getType() == input.material() && !ingredients.custom(stack)) { match = i; break; }
            }
            if (match < 0) return false;
            remaining.remove(match);
        }
        return true;
    }
    public boolean validOutput(PotionCatalog.Potion potion, ItemStack output) {
        var actual = items.identify(output); return actual != null && actual.id().equals(potion.id()) && output.getAmount() == 1;
    }
    public void register() { register(input -> input.custom() ? new RecipeChoice.ExactChoice(ingredient(input)) : new RecipeChoice.MaterialChoice(input.material())); }
    void register(Function<PotionCatalog.Input,RecipeChoice> choices) {
        for (var entry : recipes.entrySet()) {
            var recipe = new ShapelessRecipe(entry.getKey(), items.create(entry.getValue().id(), 1));
            recipe.setGroup("customrpg_potions");
            for (var input : entry.getValue().inputs()) recipe.addIngredient(choices.apply(input));
            if (!plugin.getServer().addRecipe(recipe)) throw new IllegalStateException("Potion recipe conflict: " + entry.getKey());
            registered.add(entry.getKey());
        }
        for (var player : plugin.getServer().getOnlinePlayers()) player.discoverRecipes(registered);
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { event.getPlayer().discoverRecipes(registered); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (Arrays.stream(event.getContents().getContents()).anyMatch(ingredients::custom)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void beforeDrink(PlayerItemConsumeEvent event) {
        if (!PotionItems.tagged(event.getItem())) return;
        Player player = event.getPlayer(); var potion = items.identify(event.getItem());
        if (potion == null || !player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                || !plugin.getPlayerStatsManager().isDataAvailable(player)) { event.setCancelled(true); return; }
        Long until = player.getPersistentDataContainer().get(COOLDOWN, PersistentDataType.LONG);
        if (until != null && until > clock.getAsLong()) {
            player.sendMessage("§c藥水冷卻中，還需 " + (long)Math.ceil((until-clock.getAsLong())/1000.0) + " 秒。");
            event.setCancelled(true); return;
        }
        var stats = plugin.getPlayerStatsManager().getStats(player);
        boolean full = potion.resource() == PotionCatalog.Resource.HEALTH
                ? player.getHealth() >= player.getMaxHealth() : stats.getCurrentMana() >= stats.getMaxMana();
        if (full) { player.sendMessage("§e" + (potion.resource() == PotionCatalog.Resource.HEALTH ? "生命" : "魔力") + "已滿，不需要飲用。"); event.setCancelled(true); return; }
        pending.put(event, potion.id());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterDrink(PlayerItemConsumeEvent event) {
        String expected = pending.remove(event);
        if (expected == null || event.isCancelled()) return;
        var potion = items.identify(event.getItem()); var player = event.getPlayer();
        if (potion == null || !potion.id().equals(expected) || player.isDead() || !player.isOnline()) return;
        double restored;
        if (potion.resource() == PotionCatalog.Resource.HEALTH) restored = plugin.getCombatBalance().healPotion(player, potion.restore());
        else {
            var stats = plugin.getPlayerStatsManager().getStats(player); double before = stats.getCurrentMana();
            stats.restoreMana(potion.restore()); restored = stats.getCurrentMana() - before;
        }
        if (restored > 0) {
            player.getPersistentDataContainer().set(COOLDOWN, PersistentDataType.LONG, clock.getAsLong() + catalog.cooldownMillis());
            player.sendMessage("§a飲用了" + potion.name() + "，恢復 §f" + String.format(Locale.ROOT, "%.1f", restored)
                    + (potion.resource() == PotionCatalog.Resource.HEALTH ? " HP" : " MANA"));
        } else player.sendMessage("§e藥水未恢復數值；治療可能已被其他插件阻止。");
    }
    public void shutdown() {
        for (var key : registered) plugin.getServer().removeRecipe(key);
        registered.clear(); pending.clear();
    }
}

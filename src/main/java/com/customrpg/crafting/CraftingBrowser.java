package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.util.*;

/** Read-only guide. Players craft in a real crafting table, never by taking preview items. */
public final class CraftingBrowser implements Listener, CommandExecutor, TabCompleter {
    private final CustomRPG plugin; private final CraftingCatalog catalog; private final CraftingItems items; private final VillageMerchantManager merchants;
    private final PotionManager potions;
    private record View(Inventory inventory, int page, List<String> ids, boolean materials, boolean recipe) {}
    private final Map<UUID,View> views = new HashMap<>();
    public CraftingBrowser(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items, VillageMerchantManager merchants) {
        this(plugin, catalog, items, merchants, null);
    }
    public CraftingBrowser(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items, VillageMerchantManager merchants, PotionManager potions) {
        this.plugin = plugin; this.catalog = catalog; this.items = items; this.merchants = merchants;
        this.potions = potions;
    }
    private boolean potion(String id) { return potions != null && potions.catalog().potions().containsKey(id); }
    private ItemStack product(String id) { return potion(id) ? potions.items().create(id, 1) : items.product(id); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§e工坊：" + catalog.gear().size() + " 件裝備／" + catalog.materials().size() + " 種素材。");
            if (potions != null) sender.sendMessage("§e藥水：" + potions.catalog().potions().size() + " 種；/rpgcraft potions 查看配方。");
            sender.sendMessage("§e" + merchants.status()); return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("give")) return give(sender, args);
        if (!(sender instanceof Player player)) { sender.sendMessage("/rpgcraft status 或 /rpgcraft give <玩家> <素材或藥水ID> [數量]"); return true; }
        try {
            if (args.length > 0 && args[0].equalsIgnoreCase("potions") && potions != null) {
                page(player, List.copyOf(potions.catalog().potions().keySet()), 0, false); return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("recipe")) {
                if (args.length < 2 || !catalog.gear().containsKey(args[1]) && !potion(args[1])) { player.sendMessage("§c請提供有效裝備或藥水 ID，或用 /rpgcraft 開啟圖鑑。"); return true; }
                recipe(player, args[1], new View(null, 0, potion(args[1]) ? List.copyOf(potions.catalog().potions().keySet()) : List.copyOf(catalog.gear().keySet()), false, false)); return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("materials")) {
                page(player, List.copyOf(catalog.materials().keySet()), args.length >= 2 ? Integer.parseInt(args[1]) - 1 : 0, true); return true;
            }
            String theme = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
            int tier = args.length >= 3 ? Integer.parseInt(args[2]) : 0, requestedPage = args.length >= 4 ? Integer.parseInt(args[3]) - 1 : 0;
            if (!theme.equals("all") && !catalog.themes().containsKey(theme) || tier < 0 || tier > catalog.tiers().size()) throw new IllegalArgumentException();
            List<String> ids = catalog.gear().values().stream().filter(g -> (theme.equals("all") || g.theme().id().equals(theme)) && (tier == 0 || g.tier().id() == tier))
                    .map(CraftingCatalog.Gear::id).toList(); page(player, ids, requestedPage, false);
        } catch (IllegalArgumentException error) { player.sendMessage("§c用法：/rpgcraft recipes [all|地形] [階級1～" + catalog.tiers().size() + "] [頁碼]，或 /rpgcraft materials [頁碼]"); }
        return true;
    }
    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customrpg.crafting.admin")) { sender.sendMessage("§c沒有給予物品的權限。"); return true; }
        try {
            if (args.length < 3) throw new IllegalArgumentException();
            Player player = plugin.getServer().getPlayerExact(args[1]); if (player == null) throw new IllegalArgumentException();
            int amount = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
            ItemStack stack = potion(args[2]) ? potions.items().create(args[2], amount) : items.material(args[2], amount);
            var remainder = player.getInventory().addItem(stack);
            for (ItemStack extra : remainder.values()) player.getWorld().dropItemNaturally(player.getLocation(), extra);
            sender.sendMessage("§a物品已交付給 " + player.getName());
        } catch (IllegalArgumentException error) { sender.sendMessage("§c用法：/rpgcraft give <線上玩家> <素材或藥水ID> [數量]；素材1..64，藥水1..16"); }
        return true;
    }
    private ItemStack button(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material); var meta = stack.getItemMeta(); meta.setDisplayName(name); meta.setLore(List.of(lore)); stack.setItemMeta(meta); return stack;
    }
    private void page(Player player, List<String> ids, int requested, boolean materials) {
        int page = Math.clamp(requested, 0, Math.max(0, (ids.size() - 1) / 45));
        Inventory inventory = Bukkit.createInventory(null, 54, "§6工坊" + (materials ? "素材" : "配方") + "圖鑑 §7第 " + (page + 1) + " 頁");
        for (int index = 0; index < 45 && page * 45 + index < ids.size(); index++) {
            String id = ids.get(page * 45 + index); ItemStack stack = materials ? items.material(id, 1) : product(id);
            var meta = stack.getItemMeta(); List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("§8ID: " + id); if (!materials) lore.add("§e點擊查看配方；需至工作台合成"); meta.setLore(lore); stack.setItemMeta(meta); inventory.setItem(index, stack);
        }
        inventory.setItem(45, button(Material.ARROW, "§e上一頁")); inventory.setItem(53, button(Material.ARROW, "§e下一頁"));
        inventory.setItem(49, button(Material.CRAFTING_TABLE, "§6配方圖鑑", "§7圖鑑物品不能取走", "§7/rpgcraft recipes plains 1", "§7/rpgcraft materials 查看素材", "§7/rpgcraft potions 查看藥水"));
        show(player, new View(inventory, page, ids, materials, false));
    }
    private void recipe(Player player, String id, View previous) {
        if (potion(id)) {
            var potion = potions.catalog().potions().get(id);
            Inventory inventory = Bukkit.createInventory(null, 54, "§6合成配方 §7" + potion.name());
            for (int i = 0; i < potion.inputs().size(); i++) inventory.setItem(10 + i / 3 * 9 + i % 3, potions.ingredient(potion.inputs().get(i)));
            inventory.setItem(24, product(id)); inventory.setItem(49, button(Material.ARROW, "§e返回藥水圖鑑"));
            inventory.setItem(40, button(Material.CRAFTING_TABLE, "§e工作台無序合成", "§7左側每格代表一份材料，排列不限", "§7自製素材不可用改名原版物品替代", "§7每次合成1瓶，所有品級共用冷卻"));
            show(player, new View(inventory, previous.page, previous.ids, false, true)); return;
        }
        var gear = catalog.gear().get(id); Inventory inventory = Bukkit.createInventory(null, 54, "§6合成配方 §7" + gear.name());
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) inventory.setItem(10 + y * 9 + x, items.ingredient(gear, gear.kind().shape.get(y).charAt(x)));
        inventory.setItem(24, items.product(id)); inventory.setItem(49, button(Material.ARROW, "§e返回圖鑑"));
        inventory.setItem(40, button(Material.CRAFTING_TABLE, "§e照左側排列放入工作台", "§7相同素材需同地形與階級", "§7需求等級：" + gear.tier().level(), "§7新素材不可用改名原版物品替代"));
        show(player, new View(inventory, previous.page, previous.ids, false, true));
    }
    private void show(Player player, View view) { views.put(player.getUniqueId(), view); player.openInventory(view.inventory); }
    private View owned(UUID player, Inventory inventory) { var view = views.get(player); return view != null && view.inventory == inventory ? view : null; }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var view = owned(player.getUniqueId(), event.getView().getTopInventory()); if (view == null) return;
        event.setCancelled(true); int slot = event.getRawSlot();
        if (view.recipe) { if (slot == 49) page(player, view.ids, view.page, view.materials); return; }
        if (slot == 45 || slot == 53) { page(player, view.ids, view.page + (slot == 45 ? -1 : 1), view.materials); return; }
        int index = view.page * 45 + slot;
        if (!view.materials && slot >= 0 && slot < 45 && index < view.ids.size()) recipe(player, view.ids.get(index), view);
        if (view.materials && slot >= 0 && slot < 45 && index < view.ids.size() && view.ids.get(index).startsWith("boss_core_t")) {
            String id = view.ids.get(index); int tier = catalog.materials().get(id).tier();
            Inventory inventory = Bukkit.createInventory(null, 54, "§6核心拆解配方");
            inventory.setItem(20, items.material(id, 1)); inventory.setItem(24, items.material("monster_binding_t" + tier, 8));
            inventory.setItem(49, button(Material.ARROW, "§e返回素材圖鑑"));
            inventory.setItem(40, button(Material.CRAFTING_TABLE, "§e將一顆核心放入工作台", "§7可拆解為 8 份同階魔物纖維"));
            show(player, new View(inventory, view.page, view.ids, true, true));
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (owned(event.getWhoClicked().getUniqueId(), event.getView().getTopInventory()) != null) event.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (owned(event.getPlayer().getUniqueId(), event.getInventory()) != null) views.remove(event.getPlayer().getUniqueId());
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices = new ArrayList<>();
        if (args.length == 1) { choices.addAll(List.of("recipes", "recipe", "materials", "potions", "status")); if (sender.hasPermission("customrpg.crafting.admin")) choices.add("give"); }
        else if (args.length == 2 && args[0].equalsIgnoreCase("recipes")) { choices.add("all"); choices.addAll(catalog.themes().keySet()); }
        else if (args.length == 2 && args[0].equalsIgnoreCase("recipe")) { if (potions != null) choices.addAll(potions.catalog().potions().keySet()); choices.addAll(catalog.gear().keySet()); }
        else if (args.length == 3 && args[0].equalsIgnoreCase("recipes")) for (var tier : catalog.tiers()) choices.add(String.valueOf(tier.id()));
        else if (sender.hasPermission("customrpg.crafting.admin") && args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) plugin.getServer().getOnlinePlayers().forEach(p -> choices.add(p.getName()));
            if (args.length == 3) { if (potions != null) choices.addAll(potions.catalog().potions().keySet()); choices.addAll(catalog.materials().keySet()); }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.startsWith(prefix)).limit(40).toList();
    }
    public void shutdown() {
        for (Player player : plugin.getServer().getOnlinePlayers()) if (owned(player.getUniqueId(), player.getOpenInventory().getTopInventory()) != null) player.closeInventory();
        views.clear();
    }
}

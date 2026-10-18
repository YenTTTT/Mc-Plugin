package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.util.*;
import java.util.function.*;

/** Visits generated village structures in loaded chunks, without locate/generation calls or terrain edits. */
public final class VillageMerchantManager implements Listener {
    public static final NamespacedKey VILLAGE = new NamespacedKey("customrpg", "village_merchant");
    private static final NamespacedKey RESTOCK = new NamespacedKey("customrpg", "merchant_restock_at");
    private static final NamespacedKey TIER = new NamespacedKey("customrpg", "merchant_stock_tier");
    private static final NamespacedKey SCHEMA = new NamespacedKey("customrpg", "merchant_stock_version");
    private final CustomRPG plugin; private final CraftingCatalog catalog; private final CraftingItems items;
    private final VillageMerchantRegistry registry; private final LongSupplier clock; private final Consumer<Villager> appearance;
    private final Function<Location,String> themeReader;
    private final BiFunction<Material,Integer,ItemStack> stacks;
    record Footprint(String kind, org.bukkit.util.BoundingBox bounds) {}
    private final Function<Chunk,List<Footprint>> structures;
    private final LinkedHashMap<ChunkKey,Long> pending = new LinkedHashMap<>(); private final Map<UUID,Villager> live = new HashMap<>();
    private BukkitTask task; private long readyAt, nextRepair;
    private String lastIssue = "等待已載入的原版村莊；啟動後約 10 秒開始檢查";
    private record ChunkKey(UUID world, int x, int z) {}
    public VillageMerchantManager(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items) {
        this(plugin, catalog, items, new VillageMerchantRegistry(new File(plugin.getDataFolder(), "data/village_merchants.yml"), plugin.getLogger()), System::currentTimeMillis, null);
    }
    VillageMerchantManager(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items, VillageMerchantRegistry registry, LongSupplier clock, Consumer<Villager> appearance) {
        this(plugin, catalog, items, registry, clock, appearance, CraftingLootListener::themeAt, ItemStack::new);
    }
    VillageMerchantManager(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items, VillageMerchantRegistry registry, LongSupplier clock, Consumer<Villager> appearance,
                           Function<Location,String> themeReader, BiFunction<Material,Integer,ItemStack> stacks) {
        this(plugin, catalog, items, registry, clock, appearance, themeReader, stacks, null);
    }
    VillageMerchantManager(CustomRPG plugin, CraftingCatalog catalog, CraftingItems items, VillageMerchantRegistry registry, LongSupplier clock, Consumer<Villager> appearance,
                           Function<Location,String> themeReader, BiFunction<Material,Integer,ItemStack> stacks, Function<Chunk,List<Footprint>> structures) {
        this.plugin = plugin; this.catalog = catalog; this.items = items; this.registry = registry; this.clock = clock;
        this.themeReader = themeReader; this.stacks = stacks;
        this.structures = structures == null ? c -> c.getStructures().stream().map(s -> new Footprint(s.getStructure().getKey().getKey(), s.getBoundingBox())).toList() : structures;
        this.appearance = appearance == null ? villager -> {
            villager.setAdult(); villager.setAgeLock(true); villager.setProfession(Villager.Profession.ARMORER); villager.setVillagerLevel(5);
            villager.setAI(false); villager.setGravity(false); villager.setCollidable(false); villager.setInvulnerable(true);
            villager.setPersistent(true); villager.setRemoveWhenFarAway(false); villager.setCanPickupItems(false); villager.setCustomNameVisible(true);
        } : appearance;
    }
    public void start() {
        readyAt = clock.getAsLong() + 10000;
        for (World world : plugin.getServer().getWorlds()) for (Chunk chunk : world.getLoadedChunks()) {
            enqueue(chunk, 0);
            if (chunk.isEntitiesLoaded()) for (Entity entity : chunk.getEntities()) if (entity instanceof Villager villager) restore(villager);
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L);
    }
    private boolean enabled() { return catalog.enabled("enabled") && catalog.enabled("merchants.enabled") && registry.healthy(); }
    private void enqueue(Chunk chunk, long delay) {
        if (chunk.getWorld().getEnvironment() != World.Environment.NORMAL || pending.size() >= 32768) return;
        var key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (delay > 0) pending.putFirst(key, clock.getAsLong() + delay);
        else pending.putIfAbsent(key, clock.getAsLong());
    }
    private void tick() {
        if (!enabled() || clock.getAsLong() < readyAt) return;
        long now = clock.getAsLong();
        live.values().removeIf(v -> !v.isValid() || v.isDead());
        if (nextRepair <= now) {
            nextRepair = now + 10000;
            for (var v : registry.villages().values()) {
                World world = plugin.getServer().getWorld(v.world());
                if (world != null && world.isChunkLoaded(v.lastChunkX(), v.lastChunkZ())) enqueue(world.getChunkAt(v.lastChunkX(), v.lastChunkZ()), 0);
            }
        }
        int budget = catalog.setting("chunks-per-tick");
        for (var entry : new ArrayList<>(pending.entrySet())) {
            if (budget <= 0) break;
            if (entry.getValue() > now) continue;
            budget--; pending.remove(entry.getKey()); ChunkKey key = entry.getKey(); World world = plugin.getServer().getWorld(key.world);
            if (world == null || !world.isChunkLoaded(key.x, key.z)) continue;
            try { inspect(world.getChunkAt(key.x, key.z)); }
            catch (RuntimeException error) { plugin.getLogger().warning("[VillageMerchants] Chunk inspection deferred: " + error.getMessage()); }
        }
    }
    void inspect(Chunk chunk) {
        if (!enabled() || !chunk.isLoaded()) return;
        if (!chunk.isEntitiesLoaded()) { enqueue(chunk, 2000); return; }
        for (var structure : structures.apply(chunk)) {
            String kind = structure.kind;
            if (!kind.equals("village") && !kind.startsWith("village_")) continue;
            var box = structure.bounds; UUID worldId = chunk.getWorld().getUID();
            int minX = (int)Math.floor(box.getMinX()), minZ = (int)Math.floor(box.getMinZ()), maxX = (int)Math.floor(box.getMaxX()), maxZ = (int)Math.floor(box.getMaxZ());
            String id = VillageMerchantRegistry.identity(worldId, minX, minZ, maxX, maxZ);
            var village = registry.villages().get(id);
            if (village != null && village.merchant() != null) {
                Entity existing = plugin.getServer().getEntity(village.merchant());
                if (existing instanceof Villager trader && trader.isValid()) {
                    lastIssue = "已確認村莊商人存在";
                    trader.getPersistentDataContainer().set(VILLAGE, PersistentDataType.STRING, id); restore(trader); continue;
                }
                if (!chunk.getWorld().isChunkLoaded(village.lastChunkX(), village.lastChunkZ())
                        || !chunk.getWorld().getChunkAt(village.lastChunkX(), village.lastChunkZ()).isEntitiesLoaded()) continue;
            }
            if (village == null) village = new VillageMerchantRegistry.Village(id, worldId, minX, minZ, maxX, maxZ,
                    (minX + (double)maxX) / 2 + .5, 64, (minZ + (double)maxZ) / 2 + .5, null, chunk.getX(), chunk.getZ());
            Location anchor = findAnchor(chunk, village);
            if (anchor == null) { lastIssue = "村莊已發現，但本次載入範圍沒有可放置商人的穩定地面"; if (!registry.villages().containsKey(id)) registry.save(village); continue; }
            var located = village.anchor(anchor.getX(), anchor.getY(), anchor.getZ());
            if (!registry.save(located.bind(null))) return;
            try {
                var reserved = located;
                Villager spawned = chunk.getWorld().spawn(anchor, Villager.class, CreatureSpawnEvent.SpawnReason.CUSTOM, merchant -> {
                    appearance.accept(merchant);
                    if (merchant.collidesAt(anchor)) throw new IllegalStateException("Merchant body intersects a block");
                    merchant.getPersistentDataContainer().set(VILLAGE, PersistentDataType.STRING, id);
                    if (!registry.save(reserved.bind(merchant.getUniqueId()))) throw new IllegalStateException("Merchant identity could not be saved");
                });
                if (spawned.isValid() && !spawned.isDead()) { live.put(spawned.getUniqueId(), spawned); stock(spawned); lastIssue = "已補建村莊裝備商人"; }
                else lastIssue = "商人生成被取消，稍後重試";
            } catch (RuntimeException error) { lastIssue = "放置延後：" + error.getMessage(); plugin.getLogger().warning("[VillageMerchants] Placement deferred: " + error.getMessage()); }
        }
    }
    Location findAnchor(Chunk chunk, VillageMerchantRegistry.Village village) {
        var world = chunk.getWorld(); List<Location> candidates = new ArrayList<>();
        for (Entity entity : chunk.getEntities()) if (entity instanceof Villager && !marked(entity)) {
            Location at = entity.getLocation();
            for (int[] offset : new int[][]{{2,0}, {-2,0}, {0,2}, {0,-2}}) candidates.add(at.clone().add(offset[0], 0, offset[1]));
        }
        for (int x = Math.max(village.minX(), chunk.getX() * 16); x <= Math.min(village.maxX(), chunk.getX() * 16 + 15); x += 2)
            for (int z = Math.max(village.minZ(), chunk.getZ() * 16); z <= Math.min(village.maxZ(), chunk.getZ() * 16 + 15); z += 2) {
                int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                candidates.add(new Location(world, x + .5, y, z + .5));
            }
        for (Location at : candidates) {
            if (at.getX() < village.minX() || at.getX() > village.maxX() + 1.0 || at.getZ() < village.minZ() || at.getZ() > village.maxZ() + 1.0
                    || !world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4) || !world.getWorldBorder().isInside(at)
                    || at.getY() <= world.getMinHeight() || at.getY() + 2 >= world.getMaxHeight()) continue;
            var floor = at.clone().subtract(0, 1, 0).getBlock(); var feet = at.getBlock(); var head = at.clone().add(0, 1, 0).getBlock();
            if (!Set.of(Material.GRASS_BLOCK, Material.DIRT, Material.DIRT_PATH, Material.COARSE_DIRT, Material.PODZOL, Material.COBBLESTONE,
                    Material.SAND, Material.SANDSTONE, Material.SNOW, Material.SNOW_BLOCK, Material.STONE, Material.GRAVEL).contains(floor.getType())
                    || !feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()) continue;
            return new Location(world, at.getBlockX() + .5, at.getY(), at.getBlockZ() + .5);
        }
        return null;
    }
    private boolean marked(Entity entity) { return entity.getPersistentDataContainer().has(VILLAGE, PersistentDataType.STRING); }
    private boolean owned(Villager merchant) {
        String id = merchant.getPersistentDataContainer().get(VILLAGE, PersistentDataType.STRING);
        var village = registry.villages().get(id); return village != null && merchant.getUniqueId().equals(village.merchant());
    }
    private void restore(Villager merchant) {
        if (!marked(merchant) || !registry.healthy()) return;
        if (!owned(merchant)) { merchant.remove(); return; }
        appearance.accept(merchant); live.put(merchant.getUniqueId(), merchant);
    }
    List<CraftingCatalog.Gear> offers(Location location, UUID merchant) {
        var tier = catalog.tier(plugin.getWorldZoneManager().getBaseLevel(location)); var theme = catalog.theme(themeReader.apply(location));
        var themes = new ArrayList<>(catalog.themes().keySet()); int secondary = Math.floorMod(merchant.hashCode(), themes.size());
        if (themes.get(secondary).equals(theme.id())) secondary = (secondary + 1) % themes.size(); String other = themes.get(secondary);
        return catalog.gear().values().stream().filter(g -> g.tier().id() == tier.id() && (g.theme().id().equals(theme.id()) || g.theme().id().equals(other))).toList();
    }
    private static final NamespacedKey BALANCE_VERSION=new NamespacedKey("customrpg","merchant_balance_version");
    private void refreshBalancedOffers(Villager merchant) {
        var pdc=merchant.getPersistentDataContainer();
        if (Integer.valueOf(2).equals(pdc.get(BALANCE_VERSION,PersistentDataType.INTEGER))) return;
        List<MerchantRecipe> updated=new ArrayList<>(); boolean changed=false;
        for (var old:merchant.getRecipes()) {
            String id=items.productId(old.getResult());
            if (id==null) { updated.add(old); continue; }
            var recipe=new MerchantRecipe(items.product(id),old.getUses(),old.getMaxUses(),false,0,0,true);
            recipe.setIngredients(old.getIngredients()); updated.add(recipe); changed=true;
        }
        if (changed) merchant.setRecipes(updated);
        pdc.set(BALANCE_VERSION,PersistentDataType.INTEGER,2);
    }
    void stock(Villager merchant) {
        int tier = catalog.tier(plugin.getWorldZoneManager().getBaseLevel(merchant.getLocation())).id(); var data = merchant.getPersistentDataContainer();
        Long restock = data.get(RESTOCK, PersistentDataType.LONG);
        if (restock != null && restock > clock.getAsLong() && Integer.valueOf(tier).equals(data.get(TIER, PersistentDataType.INTEGER))
                && Integer.valueOf(1).equals(data.get(SCHEMA, PersistentDataType.INTEGER)) && !merchant.getRecipes().isEmpty()) { refreshBalancedOffers(merchant); return; }
        List<MerchantRecipe> offers = new ArrayList<>();
        for (var gear : offers(merchant.getLocation(), merchant.getUniqueId())) {
            var recipe = new MerchantRecipe(items.product(gear.id()), 0, catalog.setting("uses-per-offer"), false, 0, 0, true);
            recipe.addIngredient(stacks.apply(Material.EMERALD, Math.min(64, gear.price())));
            if (gear.price() > 64) recipe.addIngredient(stacks.apply(Material.EMERALD, gear.price() - 64)); offers.add(recipe);
        }
        data.set(BALANCE_VERSION,PersistentDataType.INTEGER,2);
        merchant.setRecipes(offers); merchant.setCustomName("§6裝備商人 §7· T" + tier + " / Lv." + catalog.tiers().get(tier - 1).level());
        data.set(RESTOCK, PersistentDataType.LONG, clock.getAsLong() + catalog.setting("restock-seconds") * 1000L);
        data.set(TIER, PersistentDataType.INTEGER, tier); data.set(SCHEMA, PersistentDataType.INTEGER, 1);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager merchant) || !marked(merchant)) return;
        event.setCancelled(true); if (event.getHand() != EquipmentSlot.HAND) return;
        if (!enabled() || !owned(merchant)) { event.getPlayer().sendMessage("§c裝備商人暫停服務，請查看 /rpgcraft status。"); return; }
        if (merchant.getTrader() != null && !merchant.getTrader().equals(event.getPlayer())) { event.getPlayer().sendMessage("§e商人正在服務其他玩家。"); return; }
        stock(merchant); event.getPlayer().openMerchant(merchant, false);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        if (!(event.getMerchant() instanceof Villager merchant) || !marked(merchant)) return;
        var gear = catalog.gear().get(items.productId(event.getTrade().getResult()));
        int tier = catalog.tier(plugin.getWorldZoneManager().getBaseLevel(merchant.getLocation())).id();
        int price = event.getTrade().getIngredients().stream().mapToInt(ItemStack::getAmount).sum();
        boolean knownOffer = gear != null && merchant.getRecipes().stream().anyMatch(r -> gear.id().equals(items.productId(r.getResult())));
        if (!enabled() || !owned(merchant) || !knownOffer || gear.tier().id() != tier || price != gear.price()
                || event.getTrade().getIngredients().stream().anyMatch(i -> i.getType() != Material.EMERALD || items.custom(i))
                || event.getTrade().getUses() >= event.getTrade().getMaxUses()) { event.setCancelled(true); return; }
        event.setRewardExp(false); event.setIncreaseTradeUses(true);
    }
    @EventHandler public void onChunk(ChunkLoadEvent event) { enqueue(event.getChunk(), 2000); }
    @EventHandler public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) if (entity instanceof Villager villager) restore(villager);
        enqueue(event.getChunk(), 2000);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) if (entity instanceof Villager merchant && marked(merchant) && owned(merchant)) {
            var village = registry.villages().get(merchant.getPersistentDataContainer().get(VILLAGE, PersistentDataType.STRING));
            registry.save(village.unloaded(event.getChunk().getX(), event.getChunk().getZ())); live.remove(merchant.getUniqueId());
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) { if (marked(event.getEntity())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) { if (marked(event.getEntity())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) { if (marked(event.getEntity())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnter(VehicleEnterEvent event) { if (marked(event.getEntered())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAcquire(VillagerAcquireTradeEvent event) { if (marked(event.getEntity())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRestock(VillagerReplenishTradeEvent event) { if (marked(event.getEntity())) event.setCancelled(true); }
    public String status() { return "村莊商人：" + (enabled() ? "運作中" : "暫停") + "，已發現 " + registry.villages().size() + " 村，載入 " + live.size() + "，待檢查區塊 " + pending.size() + "；" + lastIssue; }
    public void shutdown() { if (task != null) { task.cancel(); task = null; } pending.clear(); live.clear(); }
}

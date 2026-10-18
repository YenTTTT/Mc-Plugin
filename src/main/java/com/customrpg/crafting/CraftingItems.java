package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import com.customrpg.equipment.EquipmentData;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.function.Function;

/** Canonical stacks keep native ExactChoice recipes and villager transactions authoritative. */
public final class CraftingItems {
    public static final NamespacedKey MATERIAL = new NamespacedKey("customrpg", "craft_material");
    public static final NamespacedKey PRODUCT = new NamespacedKey("customrpg", "craft_product");
    public static final NamespacedKey VERSION = new NamespacedKey("customrpg", "craft_schema");
    private static final NamespacedKey WEAPON = new NamespacedKey("customrpg", "custom_weapon_key");
    private final CraftingCatalog catalog;
    private final Function<CraftingCatalog.Gear,ItemStack> gearFactory;
    private final Function<CraftingCatalog.Ingredient,ItemStack> materialFactory;
    private final Map<String,ItemStack> materials = new HashMap<>(), products = new HashMap<>();
    public CraftingItems(CustomRPG plugin, CraftingCatalog catalog) {
        this(catalog, g -> {
            ItemStack result = g.kind().weapon() ? plugin.getWeaponManager().createWeapon(g.id()) : plugin.getEquipmentManager().createEquipment(g.id()).toItemStack();
            if (result == null) throw new IllegalStateException("Missing equipment template: " + g.id());
            var meta = result.getItemMeta(); meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(PRODUCT, PersistentDataType.STRING, g.id());
            meta.getPersistentDataContainer().set(VERSION, PersistentDataType.INTEGER, 1); result.setItemMeta(meta); return result;
        }, m -> {
            ItemStack result = new ItemStack(m.material()); var meta = result.getItemMeta();
            meta.setDisplayName((m.tier() >= 6 ? "§5" : m.tier() >= 4 ? "§9" : "§a") + m.name());
            meta.setLore(List.of("§7自製鍛造素材 · T" + m.tier(), "§7放入工作台可製作自製裝備", "§e/rpgcraft §7查看配方"));
            meta.getPersistentDataContainer().set(MATERIAL, PersistentDataType.STRING, m.id());
            meta.getPersistentDataContainer().set(VERSION, PersistentDataType.INTEGER, 1); result.setItemMeta(meta); return result;
        });
    }
    CraftingItems(CraftingCatalog catalog, Function<CraftingCatalog.Gear,ItemStack> gearFactory,
                  Function<CraftingCatalog.Ingredient,ItemStack> materialFactory) {
        this.catalog = catalog; this.gearFactory = gearFactory; this.materialFactory = materialFactory;
    }
    public ItemStack material(String id, int amount) {
        var definition = catalog.materials().get(id);
        if (definition == null || amount < 1 || amount > 64) throw new IllegalArgumentException("Unknown material/amount: " + id);
        ItemStack result = materials.computeIfAbsent(id, key -> materialFactory.apply(definition)).clone(); result.setAmount(amount); return result;
    }
    public ItemStack product(String id) {
        var definition = catalog.gear().get(id); if (definition == null) throw new IllegalArgumentException("Unknown equipment: " + id);
        return products.computeIfAbsent(id, key -> gearFactory.apply(definition)).clone();
    }
    private String identity(ItemStack stack, NamespacedKey key) {
        if (empty(stack) || !stack.hasItemMeta()) return null;
        var data = stack.getItemMeta().getPersistentDataContainer();
        if (!Integer.valueOf(1).equals(data.get(VERSION, PersistentDataType.INTEGER))) return null;
        return data.get(key, PersistentDataType.STRING);
    }
    public String materialId(ItemStack stack) {
        String id = identity(stack, MATERIAL); var definition = catalog.materials().get(id);
        return definition != null && stack.getType() == definition.material() ? id : null;
    }
    public String productId(ItemStack stack) {
        String id = identity(stack, PRODUCT); var definition = catalog.gear().get(id);
        return definition != null && stack.getType() == catalog.base(definition) ? id : null;
    }
    public boolean custom(ItemStack stack) {
        if (empty(stack) || !stack.hasItemMeta()) return false;
        var data = stack.getItemMeta().getPersistentDataContainer();
        return data.has(MATERIAL) || data.has(PRODUCT) || data.has(WEAPON) || data.has(EquipmentData.PDC_EQUIPMENT_ID);
    }
    public boolean isMaterial(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(MATERIAL);
    }
    static boolean empty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getType() == Material.CAVE_AIR || stack.getType() == Material.VOID_AIR;
    }
    public ItemStack ingredient(CraftingCatalog.Gear gear, char symbol) {
        return switch (symbol) {
            case 'M' -> material(gear.essence(), 1); case 'B' -> material(gear.binding(), 1);
            case 'C' -> gear.needsBossCore() ? material(gear.core(), 1) : new ItemStack(Material.IRON_INGOT);
            default -> null;
        };
    }
}

package com.customrpg.crafting;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import java.util.*;
import java.util.function.Function;

public final class PotionItems {
    public static final NamespacedKey ID = new NamespacedKey("customrpg", "potion_id");
    public static final NamespacedKey VERSION = new NamespacedKey("customrpg", "potion_version");
    private final PotionCatalog catalog;
    private final Function<PotionCatalog.Potion,ItemStack> factory;
    public PotionItems(PotionCatalog catalog) { this(catalog, potion -> {
        ItemStack stack = new ItemStack(Material.POTION);
        var meta = (PotionMeta) stack.getItemMeta();
        meta.setBasePotionType(PotionType.WATER); meta.clearCustomEffects();
        meta.setColor(Color.fromRGB(potion.color())); meta.setMaxStackSize(16);
        meta.setDisplayName((potion.resource() == PotionCatalog.Resource.HEALTH ? "§c" : "§b") + potion.name());
        meta.setLore(List.of("§7飲用恢復 §f" + potion.restore() + (potion.resource() == PotionCatalog.Resource.HEALTH ? " HP" : " MANA"),
                "§7所有自製藥水共用 " + catalog.cooldownMillis()/1000.0 + " 秒冷卻", "§7右鍵喝下 · 最多恢復至上限", "§e/rpgcraft potions §7查看配方"));
        meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, potion.id());
        meta.getPersistentDataContainer().set(VERSION, PersistentDataType.INTEGER, 1);
        stack.setItemMeta(meta); return stack;
    }); }
    PotionItems(PotionCatalog catalog, Function<PotionCatalog.Potion,ItemStack> factory) { this.catalog = catalog; this.factory = factory; }
    public ItemStack create(String id, int amount) {
        var potion = catalog.potions().get(id);
        if (potion == null || amount < 1 || amount > 16) throw new IllegalArgumentException("Unknown potion/amount (1..16): " + id);
        var result = factory.apply(potion).clone(); result.setAmount(amount); return result;
    }
    public PotionCatalog.Potion identify(ItemStack stack) {
        if (stack == null || stack.getType() != Material.POTION || !stack.hasItemMeta()) return null;
        var data = stack.getItemMeta().getPersistentDataContainer();
        if (!Integer.valueOf(1).equals(data.get(VERSION, PersistentDataType.INTEGER))) return null;
        return catalog.potions().get(data.get(ID, PersistentDataType.STRING));
    }
    public static boolean tagged(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(ID);
    }
}

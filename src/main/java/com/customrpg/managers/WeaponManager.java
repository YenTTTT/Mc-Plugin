package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WeaponManager - Manages all custom weapons
 *
 * This class handles the creation, storage, and retrieval of custom weapons.
 * Each weapon has unique properties like damage multipliers, special effects,
 * and visual appearance defined in config.yml.
 *
 * Example weapons:
 * - Iron Scythe: Extra damage from behind
 * - Fire Sword: Adds burning effect
 * - Thunder Axe: Lightning strike chance
 */
public class WeaponManager {

    private final CustomRPG plugin;
    private final Map<String, WeaponData> weapons;
    private final ConfigManager configManager;

    /**
     * Constructor for WeaponManager
     * @param plugin Main plugin instance
     * @param configManager Config manager for loading weapon configs
     */
    public WeaponManager(CustomRPG plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.weapons = new HashMap<>();
        loadWeapons();
    }

    /**
     * Load all weapons from configs/weapons/ folder
     */
    private void loadWeapons() {
        Map<String, Map<String, Object>> allWeapons = configManager.getAllWeapons();

        if (allWeapons.isEmpty()) {
            plugin.getLogger().warning("No weapons found in configs/weapons/ folder");
            return;
        }

        for (Map.Entry<String, Map<String, Object>> entry : allWeapons.entrySet()) {
            String weaponKey = entry.getKey();
            Map<String, Object> weaponConfig = entry.getValue();

            WeaponData weaponData = new WeaponData(
                weaponKey,
                (String) weaponConfig.get("name"),
                Material.valueOf((String) weaponConfig.get("material")),
                (Double) weaponConfig.get("damage-multiplier"),
                (String) weaponConfig.get("special-effect"),
                (List<String>) weaponConfig.get("lore")
            );

            weapons.put(weaponKey, weaponData);
            plugin.getLogger().info("Loaded weapon: " + weaponData.getDisplayName());
        }
    }

    /**
     * Create a custom weapon ItemStack
     * @param weaponKey The weapon identifier
     * @return ItemStack of the custom weapon, or null if not found
     */
    public ItemStack createWeapon(String weaponKey) {
        WeaponData weaponData = weapons.get(weaponKey);
        if (weaponData == null) {
            return null;
        }

        ItemStack weapon = new ItemStack(weaponData.getMaterial());
        ItemMeta meta = weapon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', weaponData.getDisplayName()));

            List<String> lore = new ArrayList<>();
            for (String line : weaponData.getLore()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);

            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            weapon.setItemMeta(meta);
        }

        return weapon;
    }

    /**
     * Check if an ItemStack is a custom weapon
     * @param item ItemStack to check
     * @return Weapon key if it's a custom weapon, null otherwise
     */
    public String getWeaponKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }

        String displayName = meta.getDisplayName();
        for (Map.Entry<String, WeaponData> entry : weapons.entrySet()) {
            String weaponDisplayName = ChatColor.translateAlternateColorCodes('&', entry.getValue().getDisplayName());
            if (displayName.equals(weaponDisplayName)) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Get weapon data by key
     * @param weaponKey The weapon identifier
     * @return WeaponData or null if not found
     */
    public WeaponData getWeaponData(String weaponKey) {
        return weapons.get(weaponKey);
    }

    /**
     * Get all weapon keys
     * @return List of all weapon keys
     */
    public List<String> getWeaponKeys() {
        return new ArrayList<>(weapons.keySet());
    }

    /**
     * Get the number of registered weapons
     * @return Number of weapons
     */
    public int getWeaponCount() {
        return weapons.size();
    }

    /**
     * Inner class to store weapon data
     */
    public static class WeaponData {
        private final String key;
        private final String displayName;
        private final Material material;
        private final double damageMultiplier;
        private final String specialEffect;
        private final List<String> lore;

        public WeaponData(String key, String displayName, Material material, double damageMultiplier,
                         String specialEffect, List<String> lore) {
            this.key = key;
            this.displayName = displayName;
            this.material = material;
            this.damageMultiplier = damageMultiplier;
            this.specialEffect = specialEffect;
            this.lore = lore != null ? lore : new ArrayList<>();
        }

        public String getKey() { return key; }
        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public double getDamageMultiplier() { return damageMultiplier; }
        public String getSpecialEffect() { return specialEffect; }
        public List<String> getLore() { return lore; }
    }
}

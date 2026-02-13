package com.customrpg.managers;

import com.customrpg.CustomRPG;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
    private final NamespacedKey weaponKeyData;
    private final NamespacedKey attackSpeedModifierKey;
    private final NamespacedKey knockbackModifierKey;

    /**
     * Constructor for WeaponManager
     * @param plugin Main plugin instance
     * @param configManager Config manager for loading weapon configs
     */
    public WeaponManager(CustomRPG plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.weapons = new HashMap<>();
        this.weaponKeyData = new NamespacedKey(plugin, "custom_weapon_key");
        this.attackSpeedModifierKey = new NamespacedKey(plugin, "customrpg_attack_speed");
        this.knockbackModifierKey = new NamespacedKey(plugin, "customrpg_attack_knockback");
        loadWeapons();
    }

    /**
     * Load all weapons from config/weapons/types/ folder
     */
    private void loadWeapons() {
        Map<String, Map<String, Object>> allWeapons = configManager.getAllWeapons();

        if (allWeapons.isEmpty()) {
            plugin.getLogger().warning("No weapons found in config/weapons/types/ folder");
            return;
        }

        for (Map.Entry<String, Map<String, Object>> entry : allWeapons.entrySet()) {
            String weaponKey = entry.getKey();
            Map<String, Object> weaponConfig = entry.getValue();

            String name = (String) weaponConfig.get("name");
            String displayName = (String) weaponConfig.get("display-name");
            if (displayName == null || displayName.isEmpty()) {
                displayName = name;
            }

            int customModelData = 0;
            Object cmdObj = weaponConfig.get("custom-model-data");
            if (cmdObj instanceof Integer) {
                customModelData = (Integer) cmdObj;
            }

            boolean enchantedGlow = false;
            Object glowObj = weaponConfig.get("enchanted-glow");
            if (glowObj instanceof Boolean) {
                enchantedGlow = (Boolean) glowObj;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> extra = weaponConfig.get("extra") instanceof Map
                    ? (Map<String, Object>) weaponConfig.get("extra")
                    : new HashMap<>();

            // stats (新格式) 會放在 extra 裡
            extra.putIfAbsent("base-damage", weaponConfig.getOrDefault("base-damage", 0.0));
            extra.putIfAbsent("attack-speed", weaponConfig.getOrDefault("attack-speed", 0.0));
            extra.putIfAbsent("crit-chance", weaponConfig.getOrDefault("crit-chance", 0.0));
            extra.putIfAbsent("crit-damage-multiplier", weaponConfig.getOrDefault("crit-damage-multiplier", 0.0));
            extra.putIfAbsent("knockback", weaponConfig.getOrDefault("knockback", 0.0));
            extra.putIfAbsent("durability-cost-multiplier", weaponConfig.getOrDefault("durability-cost-multiplier", 1.0));

            // 安全的取得 lore（避免 ClassCastException）
            List<String> lore = new ArrayList<>();
            Object loreObj = weaponConfig.get("lore");
            if (loreObj instanceof List<?>) {
                for (Object item : (List<?>) loreObj) {
                    if (item instanceof String) {
                        lore.add((String) item);
                    }
                }
            }

            WeaponData weaponData = new WeaponData(
                    weaponKey,
                    displayName,
                    Material.valueOf((String) weaponConfig.get("material")),
                    weaponConfig.get("damage-multiplier") instanceof Double ? (Double) weaponConfig.get("damage-multiplier") : 1.0,
                    (String) weaponConfig.get("special-effect"),
                    lore,
                    customModelData,
                    enchantedGlow,
                    extra
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
    @SuppressWarnings("deprecation")
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

            if (weaponData.getCustomModelData() > 0) {
                meta.setCustomModelData(weaponData.getCustomModelData());
            }

            if (weaponData.isEnchantedGlow()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            // 標記武器 key，讓 listener 不用靠 displayName
            meta.getPersistentDataContainer().set(weaponKeyData, PersistentDataType.STRING, weaponKey);

            // 套用攻擊速度（AttributeModifier）。
            // 注意：這是對「物品」的屬性，不是玩家永久屬性。
            double attackSpeed = weaponData.getDoubleExtra("attack-speed", 0.0);
            if (attackSpeed != 0.0) {
                AttributeModifier mod = new AttributeModifier(
                        attackSpeedModifierKey,
                        attackSpeed,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.HAND
                );
                meta.addAttributeModifier(Attribute.ATTACK_SPEED, mod);
            }

            // 擊退強度（Knockback Resistance 不是擊退，這裡採用 attack knockback add）
            double knockback = weaponData.getDoubleExtra("knockback", 0.0);
            if (knockback != 0.0) {
                AttributeModifier mod = new AttributeModifier(
                        knockbackModifierKey,
                        knockback,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.HAND
                );
                meta.addAttributeModifier(Attribute.ATTACK_KNOCKBACK, mod);
            }

            weapon.setItemMeta(meta);
        }

        return weapon;
    }

    /**
     * Check if an ItemStack is a custom weapon
     * @param item ItemStack to check
     * @return Weapon key if it's a custom weapon, null otherwise
     */
    @SuppressWarnings("deprecation")
    public String getWeaponKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        // 優先讀 PDC
        String keyFromPdc = meta.getPersistentDataContainer().get(weaponKeyData, PersistentDataType.STRING);
        if (keyFromPdc != null && weapons.containsKey(keyFromPdc)) {
            return keyFromPdc;
        }

        // fallback：舊物品用 displayName 比對
        if (!meta.hasDisplayName()) {
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
     * Reload weapons, clearing the current list and reloading from config
     */
    public void reloadWeapons() {
        weapons.clear();
        loadWeapons();
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
        private final int customModelData;
        private final boolean enchantedGlow;
        private final Map<String, Object> extra;

        public WeaponData(String key,
                          String displayName,
                          Material material,
                          double damageMultiplier,
                          String specialEffect,
                          List<String> lore,
                          int customModelData,
                          boolean enchantedGlow,
                          Map<String, Object> extra) {
            this.key = key;
            this.displayName = displayName;
            this.material = material;
            this.damageMultiplier = damageMultiplier;
            this.specialEffect = specialEffect != null ? specialEffect : "none";
            this.lore = lore != null ? lore : new ArrayList<>();
            this.customModelData = customModelData;
            this.enchantedGlow = enchantedGlow;
            this.extra = extra != null ? extra : new HashMap<>();
        }

        public String getKey() { return key; }
        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public double getDamageMultiplier() { return damageMultiplier; }
        public String getSpecialEffect() { return specialEffect; }
        public List<String> getLore() { return lore; }
        public int getCustomModelData() { return customModelData; }
        public boolean isEnchantedGlow() { return enchantedGlow; }
        public Map<String, Object> getExtra() { return extra; }

        public boolean getBooleanExtra(String key, boolean def) {
            Object v = extra.get(key);
            return v instanceof Boolean ? (Boolean) v : def;
        }

        public double getDoubleExtra(String key, double def) {
            Object v = extra.get(key);
            return v instanceof Number ? ((Number) v).doubleValue() : def;
        }

        public int getIntExtra(String key, int def) {
            Object v = extra.get(key);
            return v instanceof Number ? ((Number) v).intValue() : def;
        }
    }
}

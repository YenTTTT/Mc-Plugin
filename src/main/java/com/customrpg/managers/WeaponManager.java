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

    // ========================================
    // ===== 武器稀有度 =====
    // ========================================
    public enum WeaponRarity {
        COMMON("普通", "§f", "§7", 1.0),
        UNCOMMON("優良", "§a", "§2", 1.1),
        RARE("稀有", "§9", "§1", 1.25),
        EPIC("史詩", "§5", "§d", 1.45),
        LEGENDARY("傳說", "§6", "§e", 1.7),
        MYTHIC("神話", "§c", "§4", 2.0);

        public final String displayName;
        public final String color;       // 主色
        public final String borderColor; // 邊框色
        public final double statMultiplier;

        WeaponRarity(String displayName, String color, String borderColor, double statMultiplier) {
            this.displayName = displayName;
            this.color = color;
            this.borderColor = borderColor;
            this.statMultiplier = statMultiplier;
        }

        public static WeaponRarity fromName(String name) {
            if (name == null || name.isEmpty()) return COMMON;
            for (WeaponRarity r : values()) {
                if (r.displayName.equals(name) || r.name().equalsIgnoreCase(name)) return r;
            }
            return COMMON;
        }
    }

    // ========================================
    // ===== 武器類別 =====
    // ========================================
    public enum WeaponCategory {
        SWORD("劍", new String[]{"STRENGTH"}, new double[]{0.3}),
        STAFF("法杖", new String[]{"MAGIC"}, new double[]{0.4}),
        SCYTHE("鐮刀", new String[]{"AGILITY"}, new double[]{0.35}),
        TWO_HAND_SCYTHE("雙手鐮刀", new String[]{"AGILITY", "STRENGTH"}, new double[]{0.3, 0.15}),
        AXE("斧", new String[]{"STRENGTH", "VITALITY"}, new double[]{0.25, 0.15}),
        TWO_HAND_AXE("雙手斧", new String[]{"STRENGTH", "VITALITY"}, new double[]{0.3, 0.2}),
        BOW("弓", new String[]{"AGILITY", "SPIRIT"}, new double[]{0.25, 0.15}),
        SPEAR("長槍", new String[]{"STRENGTH", "AGILITY"}, new double[]{0.2, 0.2}),
        UNKNOWN("未知", new String[]{}, new double[]{});

        public final String displayName;
        public final String[] scalingStats;
        public final double[] scalingWeights;

        WeaponCategory(String displayName, String[] scalingStats, double[] scalingWeights) {
            this.displayName = displayName;
            this.scalingStats = scalingStats;
            this.scalingWeights = scalingWeights;
        }

        /**
         * 根據武器的 Material 自動判斷類別
         */
        public static WeaponCategory detectFromMaterial(Material mat, boolean twoHanded) {
            String name = mat.name();
            if (name.contains("SWORD")) return SWORD;
            if (name.contains("BOW") || name.equals("CROSSBOW")) return BOW;
            if (name.contains("HOE")) return twoHanded ? TWO_HAND_SCYTHE : SCYTHE;
            if (name.contains("AXE")) return twoHanded ? TWO_HAND_AXE : AXE;
            if (mat == Material.BLAZE_ROD || mat == Material.STICK || mat == Material.BREEZE_ROD) return STAFF;
            if (mat == Material.FISHING_ROD || mat == Material.TRIDENT) return SPEAR;
            return UNKNOWN;
        }

        public static WeaponCategory fromName(String name) {
            if (name == null || name.isEmpty()) return null;
            for (WeaponCategory c : values()) {
                if (c.name().equalsIgnoreCase(name) || c.displayName.equals(name)) return c;
            }
            return null;
        }

        /**
         * 計算屬性加成傷害
         */
        public double calculateStatBonus(com.customrpg.players.PlayerStats stats) {
            double bonus = 0;
            for (int i = 0; i < scalingStats.length; i++) {
                int statValue = switch (scalingStats[i]) {
                    case "STRENGTH" -> stats.getTotalStrength();
                    case "MAGIC" -> stats.getTotalMagic();
                    case "AGILITY" -> stats.getTotalAgility();
                    case "VITALITY" -> stats.getTotalVitality();
                    case "DEFENSE" -> stats.getTotalDefense();
                    case "SPIRIT" -> stats.getTotalSpirit();
                    default -> 0;
                };
                bonus += statValue * scalingWeights[i];
            }
            return bonus;
        }
    }

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

    public CustomRPG getPlugin() {
        return plugin;
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
            try {
                Map<String, Object> weaponConfig = entry.getValue();

                String name = (String) weaponConfig.get("name");
                String displayName = (String) weaponConfig.get("display-name");
                if (displayName == null || displayName.isEmpty()) {
                    displayName = name;
                }

                // material 是必填欄位
                String materialStr = (String) weaponConfig.get("material");
                if (materialStr == null || materialStr.isEmpty()) {
                    plugin.getLogger().warning("Weapon '" + weaponKey + "' 缺少 material 欄位，跳過");
                    continue;
                }
                Material material;
                try {
                    material = Material.valueOf(materialStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Weapon '" + weaponKey + "' material 無效: " + materialStr + "，跳過");
                    continue;
                }

                int customModelData = 0;
                Object cmdObj = weaponConfig.get("custom-model-data");
                if (cmdObj instanceof Number) {
                    customModelData = ((Number) cmdObj).intValue();
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

                int minLevel = 0;
                Object mlObj = weaponConfig.get("min-level");
                if (mlObj instanceof Number) {
                    minLevel = ((Number) mlObj).intValue();
                }

                // 解析稀有度
                String rarityStr = String.valueOf(weaponConfig.getOrDefault("rarity", "普通"));
                WeaponRarity rarity = WeaponRarity.fromName(rarityStr);

                // 解析類別
                String categoryStr = String.valueOf(weaponConfig.getOrDefault("category", ""));
                WeaponCategory category = WeaponCategory.fromName(categoryStr);

                // 雙手武器標記放進 extra
                Object twoHandedObj = weaponConfig.get("two-handed");
                if (twoHandedObj instanceof Boolean) {
                    extra.put("two-handed", twoHandedObj);
                }

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

                // 取得傷害倍率
                double damageMultiplier = 1.0;
                Object dmObj = weaponConfig.get("damage-multiplier");
                if (dmObj instanceof Number) {
                    damageMultiplier = ((Number) dmObj).doubleValue();
                    if (damageMultiplier <= 0) damageMultiplier = 1.0;
                }

                WeaponData weaponData = new WeaponData(
                        weaponKey,
                        displayName,
                        material,
                        damageMultiplier,
                        (String) weaponConfig.get("special-effect"),
                        lore,
                        customModelData,
                        enchantedGlow,
                        extra,
                        minLevel,
                        rarity,
                        category
                );

                weapons.put(weaponKey, weaponData);
                plugin.getLogger().info("Loaded weapon: " + rarity.color + "[" + rarity.displayName + "] "
                        + ChatColor.RESET + weaponData.getDisplayName()
                        + " (" + weaponData.getCategory().displayName + ")");

            } catch (Exception e) {
                plugin.getLogger().severe("載入武器 '" + weaponKey + "' 時發生錯誤: " + e.getMessage());
                e.printStackTrace();
            }
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
            // 稀有度顏色 + 武器名稱
            WeaponRarity rarity = weaponData.getRarity();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    rarity.color + "&l" + ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', weaponData.getDisplayName()))));

            // === MMORPG 風格 Lore ===
            List<String> lore = new ArrayList<>();
            String rc = rarity.color;   // 稀有度主色
            String bc = rarity.borderColor; // 邊框色
            String g = "§7";  // 灰色 (描述)
            String w = "§f";  // 白色 (數值)
            String y = "§e";  // 黃色 (標籤)

            // ─── 頂部邊框 ───
            lore.add(bc + "━━━━━━━━━━━━━━━━━━━━━━");

            // 稀有度 + 類別標籤
            WeaponCategory cat = weaponData.getCategory();
            lore.add(rc + "✦ " + rarity.displayName + g + " | " + y + cat.displayName);

            // 原始 Lore (描述文字)
            if (weaponData.getLore() != null && !weaponData.getLore().isEmpty()) {
                lore.add("");
                for (String line : weaponData.getLore()) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            }

            // ─── 戰鬥屬性 ───
            lore.add("");
            lore.add(y + "⚔ 戰鬥屬性");
            lore.add(bc + "──────────────────────");

            double baseDamage = weaponData.getDoubleExtra("base-damage", 0.0);
            if (baseDamage > 0) {
                lore.add(g + "  ▸ 基礎傷害: " + w + String.format("%.0f", baseDamage));
            }

            double damageMultiplier = weaponData.getDamageMultiplier();
            if (damageMultiplier != 1.0) {
                lore.add(g + "  ▸ 傷害倍率: " + w + String.format("%.1f", damageMultiplier) + "x");
            }

            double attackSpeed = weaponData.getDoubleExtra("attack-speed", 0.0);
            if (attackSpeed > 0) {
                lore.add(g + "  ▸ 攻擊速度: " + w + String.format("%.1f", attackSpeed));
            }

            double critChance = weaponData.getDoubleExtra("crit-chance", 0.0);
            if (critChance > 0) {
                lore.add(g + "  ▸ 暴擊機率: " + w + String.format("%.1f%%", critChance));
            }

            double critDmgMult = weaponData.getDoubleExtra("crit-damage-multiplier", 0.0);
            if (critDmgMult > 0) {
                lore.add(g + "  ▸ 暴擊倍率: " + w + String.format("%.1f", critDmgMult) + "x");
            }

            double knockback = weaponData.getDoubleExtra("knockback", 0.0);
            if (knockback > 0) {
                lore.add(g + "  ▸ 擊退強度: " + w + String.format("%.1f", knockback));
            }

            // 屬性加成說明
            if (cat != WeaponCategory.UNKNOWN && cat.scalingStats.length > 0) {
                StringBuilder sb = new StringBuilder();
                sb.append(g).append("  ▸ 屬性加成: ");
                for (int i = 0; i < cat.scalingStats.length; i++) {
                    if (i > 0) sb.append(g).append(" + ");
                    String statName = switch (cat.scalingStats[i]) {
                        case "STRENGTH" -> "§c力量";
                        case "MAGIC" -> "§b魔力";
                        case "AGILITY" -> "§a敏捷";
                        case "VITALITY" -> "§2體力";
                        case "DEFENSE" -> "§9防禦";
                        case "SPIRIT" -> "§d精神";
                        default -> g + cat.scalingStats[i];
                    };
                    sb.append(statName).append(g).append("×").append(w).append(String.format("%.0f%%", cat.scalingWeights[i] * 100));
                }
                lore.add(sb.toString());
            }

            // ─── 元素效果 ───
            String elementType = String.valueOf(weaponData.getExtra().getOrDefault("element-type", "NONE")).toUpperCase();
            if (!elementType.equals("NONE") && !elementType.isEmpty()) {
                lore.add("");
                String elemIcon = switch (elementType) {
                    case "FIRE", "BURN" -> "§c🔥 火焰";
                    case "ICE", "FREEZE" -> "§b❄ 冰霜";
                    case "LIGHTNING", "THUNDER" -> "§e⚡ 雷電";
                    case "POISON" -> "§2☠ 劇毒";
                    case "WATER" -> "§9💧 水流";
                    default -> "§7✧ " + elementType;
                };
                lore.add(y + "✧ 元素: " + elemIcon);
            }

            // ─── 被動效果 ───
            String passiveName = weaponData.getExtra().containsKey("passive-name")
                    ? String.valueOf(weaponData.getExtra().get("passive-name")) : "";
            if (!passiveName.isEmpty() && !passiveName.equals("無") && !passiveName.equals("null")) {
                lore.add("");
                lore.add("§d✦ 被動: " + w + passiveName);
            }

            // ─── 底部資訊 ───
            lore.add("");
            lore.add(bc + "──────────────────────");

            // 雙手武器
            boolean twoHanded = weaponData.getBooleanExtra("two-handed", false);
            if (twoHanded) {
                lore.add("§c⚠ 雙手武器");
            }

            // 等級需求
            if (weaponData.getMinLevel() > 0) {
                lore.add("§8🔒 等級需求: §c" + weaponData.getMinLevel());
            }

            // 稀有度倍率提示
            if (rarity.statMultiplier > 1.0) {
                lore.add(rc + "★ 稀有度加成: §f+" + String.format("%.0f%%", (rarity.statMultiplier - 1.0) * 100));
            }

            // 底部邊框
            lore.add(bc + "━━━━━━━━━━━━━━━━━━━━━━");

            meta.setLore(lore);

            if (weaponData.getCustomModelData() > 0) {
                meta.setCustomModelData(weaponData.getCustomModelData());
            }

            if (weaponData.isEnchantedGlow()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            // 標記武器 key
            meta.getPersistentDataContainer().set(weaponKeyData, PersistentDataType.STRING, weaponKey);

            // 攻擊速度
            if (attackSpeed != 0.0) {
                AttributeModifier mod = new AttributeModifier(
                        attackSpeedModifierKey,
                        attackSpeed,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.HAND
                );
                meta.addAttributeModifier(Attribute.ATTACK_SPEED, mod);
            }

            // 擊退
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
     * Get base damage for an item (custom weapon or vanilla weapon)
     * 獲取物品的基礎傷害（自定義武器或原版武器）
     * @param item The item to check
     * @return Base damage value
     */
    public double getBaseDamage(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 1.0; // 空手傷害
        }

        // 檢查是否為自定義武器
        String weaponKey = getWeaponKey(item);
        if (weaponKey != null) {
            WeaponData weaponData = weapons.get(weaponKey);
            if (weaponData != null) {
                // 獲取自定義武器的基礎傷害
                double baseDamage = weaponData.getDoubleExtra("base-damage", 1.0);
                return baseDamage;
            }
        }

        // 原版武器傷害
        return switch (item.getType()) {
            case WOODEN_SWORD, GOLDEN_SWORD -> 4.0;
            case STONE_SWORD -> 5.0;
            case IRON_SWORD -> 6.0;
            case DIAMOND_SWORD -> 7.0;
            case NETHERITE_SWORD -> 8.0;
            case WOODEN_AXE, GOLDEN_AXE -> 7.0;
            case STONE_AXE -> 9.0;
            case IRON_AXE -> 9.0;
            case DIAMOND_AXE -> 9.0;
            case NETHERITE_AXE -> 10.0;
            case TRIDENT -> 9.0;
            default -> 1.0; // 其他物品/空手
        };
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
        private final int minLevel;
        private final WeaponRarity rarity;
        private final WeaponCategory category;

        public WeaponData(String key, String displayName, Material material, double damageMultiplier,
                          String specialEffect, List<String> lore, int customModelData, boolean enchantedGlow,
                          Map<String, Object> extra) {
            this(key, displayName, material, damageMultiplier, specialEffect, lore, customModelData, enchantedGlow, extra, 0, WeaponRarity.COMMON, null);
        }

        public WeaponData(String key, String displayName, Material material, double damageMultiplier,
                          String specialEffect, List<String> lore, int customModelData, boolean enchantedGlow,
                          Map<String, Object> extra, int minLevel) {
            this(key, displayName, material, damageMultiplier, specialEffect, lore, customModelData, enchantedGlow, extra, minLevel, WeaponRarity.COMMON, null);
        }

        public WeaponData(String key, String displayName, Material material, double damageMultiplier,
                          String specialEffect, List<String> lore, int customModelData, boolean enchantedGlow,
                          Map<String, Object> extra, int minLevel, WeaponRarity rarity, WeaponCategory category) {
            this.key = key;
            this.displayName = displayName;
            this.material = material;
            this.damageMultiplier = damageMultiplier;
            this.specialEffect = specialEffect;
            this.lore = lore;
            this.customModelData = customModelData;
            this.enchantedGlow = enchantedGlow;
            this.extra = extra;
            this.minLevel = minLevel;
            this.rarity = rarity != null ? rarity : WeaponRarity.COMMON;
            // 自動偵測類別
            boolean twoHanded = extra != null && Boolean.TRUE.equals(extra.get("two-handed"));
            this.category = category != null ? category : WeaponCategory.detectFromMaterial(material, twoHanded);
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
        public int getMinLevel() { return minLevel; }
        public WeaponRarity getRarity() { return rarity; }
        public WeaponCategory getCategory() { return category; }

        public double getDoubleExtra(String key, double defaultValue) {
            Object val = extra.get(key);
            if (val instanceof Number) {
                return ((Number) val).doubleValue();
            }
            return defaultValue;
        }

        public boolean getBooleanExtra(String key, boolean defaultValue) {
            Object val = extra.get(key);
            if (val instanceof Boolean) {
                return (Boolean) val;
            }
            return defaultValue;
        }

        public int getIntExtra(String key, int defaultValue) {
            Object val = extra.get(key);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
            return defaultValue;
        }
    }
}

package com.customrpg.crafting;

import com.customrpg.equipment.*;
import com.customrpg.managers.WeaponManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;

/** Pure catalog data. ItemStacks and Paper registries are only touched by the item factory. */
public final class CraftingCatalog {
    public enum Kind {
        SWORD("長劍", " M ", "CMC", " B "), GREATSWORD("巨劍", "MMM", "MCM", " B "),
        AXE("戰斧", "MM ", "MC ", " B "), GREATAXE("重斧", "MMM", "BCM", " B "),
        SCYTHE("鐮刀", "MMM", "  C", " B "), STAFF("法杖", " M ", " C ", " B "),
        BOW("長弓", " MC", "M B", " MC"), SPEAR("長槍", " M ", " CB", " B "),
        HELMET("頭盔", "MMM", "M M", "BCB"), CHESTPLATE("胸甲", "M M", "MCM", "MBM"),
        LEGGINGS("護腿", "MMM", "MCM", "B B"), BOOTS("戰靴", "M M", "MCM", "B B"),
        RING("戒指", " M ", "MCM", " B "), NECKLACE("項鍊", " B ", "MCM", " M ");
        public final String name;
        public final List<String> shape;
        Kind(String name, String... shape) { this.name = name; this.shape = List.of(shape); }
        public boolean weapon() { return ordinal() < HELMET.ordinal(); }
        public EquipmentSlot slot() { return this == RING ? EquipmentSlot.RING_1 : EquipmentSlot.valueOf(name()); }
        public WeaponManager.WeaponCategory category() {
            return switch (this) {
                case GREATSWORD -> WeaponManager.WeaponCategory.TWO_HAND_SWORD;
                case GREATAXE -> WeaponManager.WeaponCategory.TWO_HAND_AXE;
                default -> WeaponManager.WeaponCategory.valueOf(name());
            };
        }
        public boolean twoHanded() { return Set.of(GREATSWORD, GREATAXE, BOW, SPEAR, SCYTHE).contains(this); }
    }
    public record Tier(int id, int level, int price, String name) {}
    public record Theme(String id, String name, String materialName, Material material,
                        EquipmentAttribute primary, EquipmentAttribute secondary, String trimPattern,
                        String trimMaterial, double damageFactor, double speedFactor, double critBonus) {}
    public record Gear(String id, Theme theme, Tier tier, Kind kind) {
        public String name() { return tier.name + "·" + theme.name + kind.name; }
        public int price() { return Math.min(128, (int)Math.ceil(tier.price * (kind == Kind.CHESTPLATE || kind == Kind.GREATSWORD || kind == Kind.GREATAXE ? 1.5 : 1))); }
        public String essence() { return theme.id + "_essence_t" + tier.id; }
        public String binding() { return "monster_binding_t" + tier.id; }
        public String core() { return "boss_core_t" + tier.id; }
        public boolean needsBossCore() { return tier.id >= 4; }
    }
    public record Ingredient(String id, String name, Material material, int tier) {}
    private final List<Tier> tiers;
    private final Map<String,Theme> themes;
    private final Map<String,Gear> gear;
    private final Map<String,Ingredient> materials;
    private final YamlConfiguration config;
    public CraftingCatalog(File file) throws Exception {
        config = new YamlConfiguration(); config.load(file);
        List<Tier> stages = new ArrayList<>(); var tierSection = required(config, "tiers");
        for (String key : tierSection.getKeys(false)) {
            var stage = required(tierSection, key); int id = Integer.parseInt(key);
            if (id < 1 || id > 10) throw new IllegalArgumentException("Tier ID outside 1..10");
            stages.add(new Tier(id, integer(stage, "required-level", 1, 200), integer(stage, "emerald-price", 1, 85), stage.getString("name", key)));
        }
        stages.sort(Comparator.comparingInt(Tier::id));
        if (stages.isEmpty() || stages.size() > 10 || stages.getFirst().level != 1) throw new IllegalArgumentException("Missing starting tier");
        for (int i = 0; i < stages.size(); i++) if (stages.get(i).id != i + 1 || i > 0 && stages.get(i).level <= stages.get(i - 1).level)
            throw new IllegalArgumentException("Tiers must have consecutive IDs and increasing levels");
        tiers = List.copyOf(stages);
        var loadedThemes = new LinkedHashMap<String,Theme>(); var themeSection = required(config, "themes");
        for (String id : themeSection.getKeys(false)) {
            if (!id.matches("[a-z][a-z0-9_]{0,31}")) throw new IllegalArgumentException("Invalid theme ID");
            var s = required(themeSection, id); Material material = Material.valueOf(s.getString("material", "AMETHYST_SHARD"));
            if (!Set.of(Material.RABBIT_HIDE, Material.NETHER_BRICK, Material.FLINT, Material.SLIME_BALL, Material.AMETHYST_SHARD,
                    Material.PRISMARINE_CRYSTALS, Material.ECHO_SHARD, Material.PRISMARINE_SHARD, Material.POPPED_CHORUS_FRUIT).contains(material))
                throw new IllegalArgumentException("Unsupported crafting material: " + material);
            var primary = EquipmentAttribute.valueOf(s.getString("primary", "STRENGTH"));
            var secondary = EquipmentAttribute.valueOf(s.getString("secondary", "VITALITY"));
            if (!primary.isBasicAttribute() || !secondary.isBasicAttribute()) throw new IllegalArgumentException("Theme requires basic stat attributes");
            loadedThemes.put(id, new Theme(id, s.getString("name", id), s.getString("material-name", id), material, primary, secondary,
                    s.getString("trim-pattern", "sentry"), s.getString("trim-material", "iron"), number(s, "damage-factor", .8, 1.2),
                    number(s, "speed-factor", .8, 1.2), number(s, "crit-bonus", 0, 6)));
        }
        if (!loadedThemes.containsKey("plains") || loadedThemes.size() > 16) throw new IllegalArgumentException("Need plains fallback and at most 16 themes");
        themes = Collections.unmodifiableMap(loadedThemes);
        Map<String,Gear> products = new LinkedHashMap<>(); Map<String,Ingredient> inputs = new LinkedHashMap<>();
        for (Tier tier : tiers) {
            inputs.put("monster_binding_t" + tier.id, new Ingredient("monster_binding_t" + tier.id, tier.name + "·魔物纖維", Material.STRING, tier.id));
            inputs.put("boss_core_t" + tier.id, new Ingredient("boss_core_t" + tier.id, tier.name + "·首領核心", Material.HEART_OF_THE_SEA, tier.id));
            for (Theme theme : themes.values()) {
                String essence = theme.id + "_essence_t" + tier.id;
                inputs.put(essence, new Ingredient(essence, tier.name + "·" + theme.materialName, theme.material, tier.id));
                for (Kind kind : Kind.values()) {
                    String id = "forge_" + theme.id + "_t" + tier.id + "_" + kind.name().toLowerCase(Locale.ROOT);
                    products.put(id, new Gear(id, theme, tier, kind));
                }
            }
        }
        gear = Collections.unmodifiableMap(products); materials = Collections.unmodifiableMap(inputs);
        number(config, "drops.essence-chance", 0, 1); number(config, "drops.binding-chance", 0, 1); number(config, "drops.boss-core-chance", 0, 1);
        integer(config, "merchants.restock-seconds", 60, 86400); integer(config, "merchants.uses-per-offer", 1, 64); integer(config, "merchants.chunks-per-tick", 1, 8);
    }
    private static ConfigurationSection required(ConfigurationSection root, String key) {
        var section = root.getConfigurationSection(key); if (section == null) throw new IllegalArgumentException("Missing section: " + key); return section;
    }
    private static int integer(ConfigurationSection root, String key, int min, int max) {
        double number = number(root, key, min, max); if (number != Math.rint(number)) throw new IllegalArgumentException("Not an integer: " + key); return (int)number;
    }
    private static double number(ConfigurationSection root, String key, double min, double max) {
        Object value = root.get(key);
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < min || n.doubleValue() > max)
            throw new IllegalArgumentException("Invalid number at " + key);
        return n.doubleValue();
    }
    public Map<String,Gear> gear() { return gear; }
    public Map<String,Ingredient> materials() { return materials; }
    public Map<String,Theme> themes() { return themes; }
    public List<Tier> tiers() { return tiers; }
    public Tier tier(int level) { Tier selected = tiers.getFirst(); for (Tier t : tiers) if (t.level <= level) selected = t; return selected; }
    public Theme theme(String id) { return themes.getOrDefault(id, themes.get("plains")); }
    public boolean enabled(String key) { return config.getBoolean(key, true); }
    public double chance(String key) { return config.getDouble("drops." + key); }
    public int setting(String key) { return config.getInt("merchants." + key); }
    public Material base(Gear gear) {
        int stage = gear.tier.id;
        String tool = stage <= 1 ? "STONE" : stage <= 3 ? "IRON" : stage <= 5 ? "DIAMOND" : "NETHERITE";
        return switch (gear.kind) {
            case SWORD, GREATSWORD -> Material.valueOf(tool + "_SWORD");
            case AXE, GREATAXE -> Material.valueOf(tool + "_AXE");
            case SCYTHE -> Material.valueOf(tool + "_HOE");
            case STAFF -> Material.BLAZE_ROD; case BOW -> Material.BOW; case SPEAR -> Material.TRIDENT;
            case RING -> Material.GOLD_NUGGET; case NECKLACE -> Material.NAUTILUS_SHELL;
            default -> Material.valueOf((stage == 1 ? "LEATHER" : stage == 2 ? "CHAINMAIL" : stage <= 4 ? "IRON" : stage <= 6 ? "DIAMOND" : "NETHERITE") + "_" + gear.kind.name());
        };
    }
    public EquipmentData equipment(Gear gear) {
        if (gear.kind.weapon()) throw new IllegalArgumentException("Weapon is not an armor/accessory");
        var rarity = EquipmentRarity.valueOf(rarity(gear.tier).name());
        var data = new EquipmentData(gear.id, gear.name(), gear.kind.slot(), rarity, base(gear));
        data.setLevel(gear.tier.level); data.setRequiredLevel(gear.tier.level);
        data.setDescription(gear.theme.name + "工藝；可由工作台合成，或向當地村莊裝備商購買。");
        Map<EquipmentAttribute,Double> stats = new EnumMap<>(EquipmentAttribute.class);
        stats.putAll(com.customrpg.balance.BalanceItemRules.equipment(gear.tier.level, gear.kind.slot(), gear.theme.primary));
        if (gear.kind.slot().isArmor()) {
            data.setArmorTrimPattern(gear.theme.trimPattern); data.setArmorTrimMaterial(gear.theme.trimMaterial);
        }
        data.setBaseAttributes(stats); return data;
    }
    public WeaponManager.WeaponData weapon(Gear gear) {
        if (!gear.kind.weapon()) throw new IllegalArgumentException("Not a weapon");
        var model = com.customrpg.balance.BalanceItemRules.weapon(gear.kind.category());
        double speed = model.attacksPerSecond;
        Map<String,Object> stats = new HashMap<>();
        stats.put("base-damage", com.customrpg.balance.InitialBalanceModel.weaponAttack(gear.tier.level, model));
        stats.put("attack-speed", speed - 4); // Vanilla attack speed starts at four.
        stats.put("crit-chance", 5.0); stats.put("crit-damage-multiplier", 1.5);
        stats.put("two-handed", gear.kind.twoHanded());
        return new WeaponManager.WeaponData(gear.id, gear.name(), base(gear), 1, "none",
                List.of("&7" + gear.theme.name + "工藝 · 工作台合成／村莊販售"), 0, false, stats, gear.tier.level, rarity(gear.tier), gear.kind.category());
    }
    private WeaponManager.WeaponRarity rarity(Tier tier) {
        return tier.id <= 1 ? WeaponManager.WeaponRarity.COMMON : tier.id <= 3 ? WeaponManager.WeaponRarity.UNCOMMON
                : tier.id <= 5 ? WeaponManager.WeaponRarity.RARE : WeaponManager.WeaponRarity.EPIC;
    }
}

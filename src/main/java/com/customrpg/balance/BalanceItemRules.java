package com.customrpg.balance;

import com.customrpg.equipment.*;
import com.customrpg.managers.WeaponManager;
import java.util.*;

/** Canonical template budgets; old item IDs retain identity but cannot retain old combat numbers. */
public final class BalanceItemRules {
    private BalanceItemRules() {}

    public static InitialBalanceModel.Weapon weapon(WeaponManager.WeaponCategory category) {
        if (category == null) return InitialBalanceModel.Weapon.SWORD;
        return switch (category) {
            case TWO_HAND_SWORD -> InitialBalanceModel.Weapon.GREATSWORD;
            case AXE -> InitialBalanceModel.Weapon.AXE;
            case TWO_HAND_AXE -> InitialBalanceModel.Weapon.GREATAXE;
            case SCYTHE, TWO_HAND_SCYTHE -> InitialBalanceModel.Weapon.SCYTHE;
            case STAFF -> InitialBalanceModel.Weapon.STAFF;
            case BOW -> InitialBalanceModel.Weapon.BOW;
            case SPEAR, TWO_HAND_STAFF -> InitialBalanceModel.Weapon.SPEAR;
            default -> InitialBalanceModel.Weapon.SWORD;
        };
    }

    public static int vanillaLevel(String material) {
        if (material.contains("NETHERITE")) return 58;
        if (material.contains("DIAMOND") || material.equals("MACE")) return 38;
        if (material.equals("TRIDENT")) return 28;
        if (material.contains("IRON")) return 18;
        if (material.contains("STONE") || material.contains("GOLD") || material.contains("CHAINMAIL")) return 8;
        return 1;
    }

    public static WeaponManager.WeaponData normalize(WeaponManager.WeaponData data) {
        int level = BalanceMigration.level(data.getMinLevel());
        var model = weapon(data.getCategory());
        Map<String,Object> extra = new HashMap<>(data.getExtra());
        extra.put("base-damage", InitialBalanceModel.weaponAttack(level, model));
        extra.put("attack-speed", model.attacksPerSecond - 4);
        extra.put("crit-chance", 5.0);
        extra.put("crit-damage-multiplier", 1.5);
        extra.put("balance-version", BalanceMigration.VERSION);
        return new WeaponManager.WeaponData(data.getKey(), data.getDisplayName(), data.getMaterial(), 1,
                data.getSpecialEffect(), data.getLore(), data.getCustomModelData(), data.isEnchantedGlow(),
                extra, level, data.getRarity(), data.getCategory());
    }

    private static int index(EquipmentSlot slot) {
        return switch (slot) {
            case HELMET -> 0; case CHESTPLATE -> 1; case LEGGINGS -> 2; case BOOTS -> 3;
            case NECKLACE -> 5; default -> 4;
        };
    }

    /** A complete six-slot kit preserves the model totals exactly, including at low levels. */
    public static Map<EquipmentAttribute,Double> equipment(int level, EquipmentSlot slot, EquipmentAttribute primary) {
        if (slot.isWeapon()) return new EnumMap<>(EquipmentAttribute.class);
        var budget = InitialBalanceModel.referenceGear(BalanceMigration.level(level));
        int i = index(slot);
        int attack = BalanceMigration.redistribute(new long[]{1,1,1,1,1,1}, (int) budget.primary())[i];
        int vitality = BalanceMigration.redistribute(new long[]{15,30,25,15,5,10}, (int) budget.vitality())[i];
        int defense = 1 + BalanceMigration.redistribute(new long[]{15,30,25,15,5,10}, (int) budget.defense()-6)[i];
        var result = new EnumMap<EquipmentAttribute,Double>(EquipmentAttribute.class);
        if (attack > 0) result.put(primary, (double) attack);
        if (vitality > 0) result.merge(EquipmentAttribute.VITALITY, (double) vitality, Double::sum);
        if (defense > 0) result.merge(EquipmentAttribute.DEFENSE, (double) defense, Double::sum);
        return result;
    }

    public static void normalize(EquipmentData data) {
        if (data.getId().startsWith("forge_")) return; // CraftingCatalog already emits the canonical budget.
        var original = data.getBaseAttributes();
        EquipmentAttribute primary = EquipmentAttribute.STRENGTH;
        double best = 0;
        for (EquipmentAttribute a : List.of(EquipmentAttribute.STRENGTH, EquipmentAttribute.INTELLIGENCE,
                EquipmentAttribute.AGILITY, EquipmentAttribute.SPIRIT, EquipmentAttribute.VITALITY)) {
            double value = original.getOrDefault(a, 0.0);
            if (value > best) { best = value; primary = a; }
        }
        if (best == 0 && original.getOrDefault(EquipmentAttribute.MAX_HEALTH, 0.0) > 0) primary = EquipmentAttribute.VITALITY;
        int level = BalanceMigration.level(Math.max(data.getLevel(), data.getRequiredLevel()));
        data.setLevel(level); data.setRequiredLevel(level);
        data.setBaseAttributes(equipment(level, data.getSlot(), primary));
    }
}

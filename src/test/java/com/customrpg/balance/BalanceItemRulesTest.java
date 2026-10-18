package com.customrpg.balance;

import com.customrpg.equipment.*;
import com.customrpg.managers.WeaponManager;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BalanceItemRulesTest {
    @Test void sixPieceSetsExactlyMatchTheModelAcrossAllLevelsAndNeverSellAnEmptyStarterRing() {
        for(int level=1;level<=60;level++) {
            var total=new EnumMap<EquipmentAttribute,Double>(EquipmentAttribute.class);
            for(var slot:List.of(EquipmentSlot.HELMET,EquipmentSlot.CHESTPLATE,EquipmentSlot.LEGGINGS,EquipmentSlot.BOOTS,EquipmentSlot.RING_1,EquipmentSlot.NECKLACE)) {
                var attrs=BalanceItemRules.equipment(level,slot,EquipmentAttribute.STRENGTH);
                assertFalse(attrs.isEmpty()); attrs.forEach((key,value)->total.merge(key,value,Double::sum));
            }
            var expected=InitialBalanceModel.referenceGear(level);
            assertEquals(expected.primary(),total.getOrDefault(EquipmentAttribute.STRENGTH,0.0));
            assertEquals(expected.vitality(),total.getOrDefault(EquipmentAttribute.VITALITY,0.0));
            assertEquals(expected.defense(),total.getOrDefault(EquipmentAttribute.DEFENSE,0.0));
        }
    }
    @Test void ancientHighDamageWeaponsRetainIdentityAndMechanicsButUseCanonicalAttackAndSpeed() {
        var old=new WeaponManager.WeaponData("ancient","Name",Material.DIAMOND_SWORD,100,"burn",List.of("lore"),7,true,
                new HashMap<>(Map.of("base-damage",99999.0,"attack-speed",30,"two-handed",true)),58,
                WeaponManager.WeaponRarity.MYTHIC,WeaponManager.WeaponCategory.TWO_HAND_SWORD);
        var balanced=BalanceItemRules.normalize(old);
        assertEquals("ancient",balanced.getKey()); assertEquals("burn",balanced.getSpecialEffect()); assertEquals(7,balanced.getCustomModelData());
        assertEquals(1,balanced.getDamageMultiplier()); assertEquals(-3.1,balanced.getDoubleExtra("attack-speed",0),1e-9);
        assertEquals(InitialBalanceModel.weaponAttack(58,InitialBalanceModel.Weapon.GREATSWORD),balanced.getDoubleExtra("base-damage",0));
        assertEquals(99999,old.getDoubleExtra("base-damage",0)); // The config object is not mutated.
    }
    @Test void equipmentAboveSixtyIsConvertedOnceAndOverpoweredFlatHpDoesNotSurvive() {
        var data=new EquipmentData("old","Old",EquipmentSlot.CHESTPLATE,EquipmentRarity.EPIC,Material.DIAMOND_CHESTPLATE);
        data.setLevel(100); data.setRequiredLevel(100); data.setBaseAttributes(new HashMap<>(Map.of(EquipmentAttribute.MAX_HEALTH,99999.0)));
        BalanceItemRules.normalize(data); var first=new HashMap<>(data.getBaseAttributes()); BalanceItemRules.normalize(data);
        assertEquals(first,data.getBaseAttributes()); assertEquals(60,data.getRequiredLevel());
        assertFalse(data.getBaseAttributes().containsKey(EquipmentAttribute.MAX_HEALTH));
        assertTrue(data.getBaseAttributes().get(EquipmentAttribute.VITALITY)<20);
    }
    @Test void vanillaMaterialProgressionDoesNotScaleAutomaticallyWithItsHolder() {
        assertEquals(1,BalanceItemRules.vanillaLevel("WOODEN_SWORD")); assertEquals(18,BalanceItemRules.vanillaLevel("IRON_SWORD"));
        assertEquals(38,BalanceItemRules.vanillaLevel("DIAMOND_SWORD")); assertEquals(58,BalanceItemRules.vanillaLevel("NETHERITE_SWORD"));
        assertTrue(BalanceItemRules.equipment(60,EquipmentSlot.MAIN_HAND,EquipmentAttribute.STRENGTH).isEmpty());
    }
}

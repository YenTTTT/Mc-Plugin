package com.customrpg.balance;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.customrpg.players.PlayerStats;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombatBalanceTest {
    CustomRPG plugin; Player player; PlayerStats stats; WeaponManager weapons; ItemStack sword; CombatBalance balance;
    @BeforeEach void setup() {
        plugin=mock(CustomRPG.class); player=mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        stats=new PlayerStats(); stats.setLevel(60); stats.setStrength(106); stats.setVitality(44); stats.setDefense(27);
        stats.setEquipmentStrength(23); stats.setEquipmentVitality(11); stats.setEquipmentDefense(39);
        var manager=mock(PlayerStatsManager.class); when(plugin.getPlayerStatsManager()).thenReturn(manager); when(manager.getStats(player)).thenReturn(stats);
        weapons=mock(WeaponManager.class); when(plugin.getWeaponManager()).thenReturn(weapons);
        sword=mock(ItemStack.class); when(sword.getType()).thenReturn(Material.NETHERITE_SWORD);
        when(weapons.getWeaponKey(sword)).thenReturn("sword");
        when(weapons.getWeaponData("sword")).thenReturn(data("sword",60,WeaponManager.WeaponCategory.SWORD,WeaponManager.WeaponRarity.COMMON));
        balance=new CombatBalance(plugin,()->1);
    }
    private WeaponManager.WeaponData data(String id,int level,WeaponManager.WeaponCategory category,WeaponManager.WeaponRarity rarity) {
        return new WeaponManager.WeaponData(id,id,Material.NETHERITE_SWORD,100,"none",List.of(),0,false,new HashMap<>(),level,rarity,category);
    }
    @Test void realCalculatorMatchesTheReferenceWithoutLegacyWeaponMultipliers() {
        var attack=balance.calculate(player,sword,1,1);
        assertEquals(66.8,attack.damage(),1e-9); assertEquals(87.6416,attack.baselineDps(),1e-9);
        assertEquals(.05,attack.critChance()); assertEquals(1.5,attack.critMultiplier());
    }
    @Test void racesTalentsCritAndRarityShareThePermanentBudget() {
        stats.setRaceStrength(100); stats.setTalentStrength(100); stats.setBonusAttackDamage(10000);
        stats.setBonusCritChance(100); stats.setBonusCritDamage(100);
        when(weapons.getWeaponData("sword")).thenReturn(data("sword",60,WeaponManager.WeaponCategory.SWORD,WeaponManager.WeaponRarity.MYTHIC));
        var boosted=balance.calculate(player,sword,1,1);
        assertTrue(boosted.expectedHit()<=66.8*1.025*1.10+1e-8);
        assertTrue(boosted.critChance()<=.30); assertTrue(boosted.critMultiplier()<=1.75);
    }
    @Test void weakVanillaWeaponsDoNotGrowToTheHoldersLevelAndPartialChargeLosesDamage() {
        var old=mock(ItemStack.class); when(old.getType()).thenReturn(Material.WOODEN_SWORD);
        double weak=balance.calculate(player,old,1,1).damage();
        assertEquals(40.25,weak,1e-9); assertTrue(weak<balance.calculate(player,sword,1,1).damage());
        assertEquals(66.8*.4,balance.calculate(player,sword,.5,1).damage(),1e-9);
    }
    @Test void delayedSkillAllowanceIsIndependentOfHeldWeapons() {
        double first=balance.skillDps(player);
        when(weapons.getWeaponData("sword")).thenReturn(data("sword",1,WeaponManager.WeaponCategory.STAFF,WeaponManager.WeaponRarity.MYTHIC));
        assertEquals(first,balance.skillDps(player));
    }
    @Test void projectileSnapshotHasTheRolledDamageAndBaselineRatherThanAWeaponIdLookup() {
        var projectile=mock(Arrow.class); var pdc=mock(PersistentDataContainer.class); when(projectile.getPersistentDataContainer()).thenReturn(pdc);
        var attack=balance.calculate(player,sword,1,.5); balance.snapshot(projectile,attack);
        verify(pdc).set(CombatBalance.SHOT_DAMAGE,PersistentDataType.DOUBLE,33.4);
        var value=org.mockito.ArgumentCaptor.forClass(Double.class);
        verify(pdc).set(eq(CombatBalance.SHOT_DPS),eq(PersistentDataType.DOUBLE),value.capture());
        assertEquals(87.6416,value.getValue(),1e-8);
    }
    @Test void rejectedAttacksNeverExecuteDeferredLeechOrProcs() {
        var event=mock(org.bukkit.event.entity.EntityDamageEvent.class); var action=mock(Runnable.class);
        balance.afterAccepted(event,action); balance.runAccepted(event,false); verifyNoInteractions(action);
        balance.afterAccepted(event,action); balance.runAccepted(event,true); balance.runAccepted(event,true); verify(action,times(1)).run();
    }
    @Test void reloggingCannotRefillExtraDamageBudget() {
        UUID target=UUID.randomUUID();long started=System.currentTimeMillis();
        balance.extras().reserve(player.getUniqueId(),target,200,100).finish(true);
        balance.detach(player.getUniqueId());
        double allowance=balance.extras().reserve(player.getUniqueId(),target,200,100).damage(100);
        // Real-clock budget refills 15 damage per second at 100 baseline DPS; detach must not reset its burst.
        double naturalRefill=Math.max(0,System.currentTimeMillis()-started)*.015;
        assertTrue(allowance<=naturalRefill+1e-9,"Logout may retain natural refill, but must not reset the 200-damage burst");
    }
    @Test void chickenAllowanceUsesTotalSpiritAndIgnoresStrengthMagicAndAgility(){double initial=balance.spiritSkillDps(player);stats.setSpirit(100);stats.setEquipmentSpirit(20);stats.setRaceSpirit(10);stats.setTalentSpirit(5);double spirit=balance.spiritSkillDps(player);assertTrue(spirit>initial);stats.setStrength(10000);stats.setMagic(10000);stats.setAgility(10000);assertEquals(spirit,balance.spiritSkillDps(player));}

}

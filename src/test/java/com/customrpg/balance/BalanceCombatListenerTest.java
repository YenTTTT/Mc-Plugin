package com.customrpg.balance;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.customrpg.players.PlayerStats;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BalanceCombatListenerTest {
    CustomRPG plugin; Player player; PlayerStats stats; Zombie mob; MobManager mobs;
    CombatBalance balance; BalanceCombatListener listener; ItemStack item;
    @BeforeEach void setup() {
        plugin=mock(CustomRPG.class); when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration()); player=mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        stats=new PlayerStats(); stats.setLevel(60); stats.setStrength(106); stats.setEquipmentStrength(23);
        stats.setVitality(44); stats.setEquipmentVitality(11); stats.setDefense(27); stats.setEquipmentDefense(39);
        when(player.getHealth()).thenReturn(737.0); when(player.getMaxHealth()).thenReturn(737.0);
        var manager=mock(PlayerStatsManager.class); when(plugin.getPlayerStatsManager()).thenReturn(manager);
        when(manager.getStats(player)).thenReturn(stats); when(manager.isDataAvailable(player)).thenReturn(true);
        mobs=mock(MobManager.class); when(plugin.getMobManager()).thenReturn(mobs);
        mob=mock(Zombie.class); when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mob.getPersistentDataContainer()).thenReturn(BalanceTestFixtures.pdc(new HashMap<>(Map.of("mob_balance_version",2,"balance_vanilla_level",60))));
        when(mob.getMaxHealth()).thenReturn(438.208); when(mob.getHealth()).thenReturn(438.208);
        var weapons=mock(WeaponManager.class); when(plugin.getWeaponManager()).thenReturn(weapons);
        item=mock(ItemStack.class); when(item.getType()).thenReturn(Material.NETHERITE_SWORD); when(weapons.getWeaponKey(item)).thenReturn("reference");
        var data=new WeaponManager.WeaponData("reference","reference",Material.NETHERITE_SWORD,1,"none",List.of(),0,false,new HashMap<>(),60,
                WeaponManager.WeaponRarity.COMMON,WeaponManager.WeaponCategory.SWORD);
        when(weapons.getWeaponData("reference")).thenReturn(data);
        balance=new CombatBalance(plugin,()->1); listener=new BalanceCombatListener(plugin,balance);
    }
    @SuppressWarnings("deprecation")
    @Test void playerDefenseIncludesEquipmentAndNativeArmorCannotReduceDamageAgain() {
        var event=BalanceTestFixtures.damage(mob,player,10);
        event.setDamage(EntityDamageEvent.DamageModifier.ARMOR,-8);
        listener.onDamage(event);
        assertEquals(737.0/6,event.getFinalDamage(),1e-8);
        assertEquals(0,event.getDamage(EntityDamageEvent.DamageModifier.ARMOR));
    }
    @Test void repeatedOrdinaryMobHitsCannotIgnoreTheOnePointEightSecondAttackInterval() {
        var first=BalanceTestFixtures.damage(mob,player,10); listener.onDamage(first); listener.afterDamage(first);
        var second=BalanceTestFixtures.damage(mob,player,10); listener.onDamage(second); assertTrue(second.isCancelled());
    }
    @Test void aCancelledMobHitDoesNotConsumeItsAttackOpportunity() {
        var first=BalanceTestFixtures.damage(mob,player,10); listener.onDamage(first); first.setCancelled(true); listener.afterDamage(first);
        var second=BalanceTestFixtures.damage(mob,player,10); listener.onDamage(second); assertFalse(second.isCancelled());
    }
    @Test void ordinaryPlayerAttacksPassThroughWithoutBeingChargedAsSkills() {
        var attack=balance.calculate(player,item,1,1); var event=BalanceTestFixtures.damage(player,mob,attack.damage());
        balance.record(event,player,attack); listener.onDamage(event); listener.afterDamage(event);
        assertEquals(66.8,event.getFinalDamage(),1e-8);
        assertEquals(200,balance.extras().reserve(player.getUniqueId(),mob.getUniqueId(),200,100).damage(100),1e-8);
    }
    @Test void legacyMultiHitSkillsAreBoundedAndProtectionRefundsTheirBudget() {
        var rejected=BalanceTestFixtures.damage(player,mob,99999); listener.onDamage(rejected);
        assertEquals(balance.skillDps(player)*2,rejected.getFinalDamage(),1e-8);
        rejected.setCancelled(true); listener.afterDamage(rejected);
        var accepted=BalanceTestFixtures.damage(player,mob,99999); listener.onDamage(accepted);
        assertEquals(balance.skillDps(player)*2,accepted.getFinalDamage(),1e-8); listener.afterDamage(accepted);
        var repeated=BalanceTestFixtures.damage(player,mob,99999); listener.onDamage(repeated);
        assertTrue(repeated.isCancelled()||repeated.getFinalDamage()<.2); // At most the tiny elapsed-time refill.
    }
    @Test void allReferenceLevelsStillRequireSevenNonCriticalSwordHitsThroughTheActualFinalListener() {
        var weapons=plugin.getWeaponManager();
        for(int l=1;l<=60;l++) {
            var allocation=InitialBalanceModel.allocation(l,InitialBalanceModel.Build.BALANCED); var gear=InitialBalanceModel.referenceGear(l);
            stats.setLevel(l); stats.setStrength(allocation.primary()); stats.setEquipmentStrength((int)gear.primary());
            when(weapons.getWeaponData("reference")).thenReturn(new WeaponManager.WeaponData("reference","reference",Material.NETHERITE_SWORD,1,"none",List.of(),0,false,
                    new HashMap<>(),l,WeaponManager.WeaponRarity.COMMON,WeaponManager.WeaponCategory.SWORD));
            var attack=balance.calculate(player,item,1,1); double health=InitialBalanceModel.mobHealth(l,InitialBalanceModel.Role.NORMAL,1); int hits=0;
            while(health>1e-8&&hits<20) {
                var event=BalanceTestFixtures.damage(player,mob,attack.damage()); balance.record(event,player,attack);
                listener.onDamage(event); listener.afterDamage(event); health-=event.getFinalDamage(); hits++;
            }
            assertEquals(7,hits,"Level "+l);
        }
    }
    @Test void bossArmorAndVirtualProjectionTogetherKeepARealFiveThousandHpPool() {
        var data=mock(MobManager.MobData.class); when(mobs.getCustomMobKey(mob)).thenReturn("boss"); when(mobs.getMobData("boss")).thenReturn(data);
        when(data.balanceRole()).thenReturn(InitialBalanceModel.Role.WILDERNESS_BOSS); when(mobs.getMobLevel(mob)).thenReturn(60);
        var state=new HashMap<String,Object>(Map.of("mob_balance_version",2,"virtual_max_hp",5000.0,"virtual_current_hp",5000.0));
        when(mob.getPersistentDataContainer()).thenReturn(BalanceTestFixtures.pdc(state)); when(mob.getMaxHealth()).thenReturn(1024.0);
        var attack=balance.calculate(player,item,1,1); var event=BalanceTestFixtures.damage(player,mob,attack.damage()); balance.record(event,player,attack);
        listener.onDamage(event); var projection=new VirtualHealthProjection(); projection.projectDamage(event);
        listener.afterDamage(event); projection.commitDamage(event);
        assertEquals(5000-66.8*.8,MobManager.getVirtualCurrentHP(mob),1e-8);
        assertEquals(66.8*.8*1024/5000,event.getFinalDamage(),1e-8);
    }
    @Test void unreadableOrUnmigratablePlayerDataCannotDealDamage() {
        when(plugin.getPlayerStatsManager().isDataAvailable(player)).thenReturn(false);
        var event=BalanceTestFixtures.damage(player,mob,100); listener.onDamage(event); assertTrue(event.isCancelled());
    }
    @Test void armorDurabilityUsesVanillaEquivalentDamageUnitsButWeaponsRemainUnchanged() {
        var armor=mock(ItemStack.class); when(armor.getType()).thenReturn(Material.DIAMOND_CHESTPLATE);
        var event=mock(org.bukkit.event.player.PlayerItemDamageEvent.class);
        when(event.getItem()).thenReturn(armor); when(event.getPlayer()).thenReturn(player); when(event.getDamage()).thenReturn(123);
        listener.onDurability(event); verify(event).setDamage(4);
        var sword=mock(ItemStack.class); when(sword.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(event.getItem()).thenReturn(sword); clearInvocations(event); listener.onDurability(event); verify(event,never()).setDamage(anyInt());
    }
    @SuppressWarnings("deprecation")
    @Test void absorptionIsRecalculatedAfterNativeArmorIsReplaced() {
        when(player.getAbsorptionAmount()).thenReturn(50.0);
        var event=BalanceTestFixtures.damage(mob,player,10);
        event.setDamage(EntityDamageEvent.DamageModifier.ARMOR,-8);
        event.setDamage(EntityDamageEvent.DamageModifier.ABSORPTION,-2);
        listener.onDamage(event); assertEquals(737.0/6-50,event.getFinalDamage(),1e-8);
        assertEquals(-50,event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION));
    }

}

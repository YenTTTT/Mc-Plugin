package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerTalents;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssassinPassiveTest {
    TalentPassiveEffectManager passives;
    Player attacker;
    LivingEntity target;
    World world;

    @BeforeEach void setup() throws Exception {
        var plugin = mock(CustomRPG.class);
        var talents = mock(TalentManager.class);
        when(talents.getPlayerTalents(any(Player.class))).thenReturn(new PlayerTalents());
        passives = new TalentPassiveEffectManager(plugin, talents, mock(PlayerStatsManager.class));
        attacker = mock(Player.class); when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        world = mock(World.class);
        target = mock(Player.class); // Weapon passives must work against players as well as mobs.
        when(target.getWorld()).thenReturn(world);
        when(target.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(target.getLocation()).thenAnswer(i -> new Location(world, 0, 64, 0, 0, 0));
        when(target.getHealth()).thenReturn(20.0); when(target.getMaxHealth()).thenReturn(20.0);
        var cache = TalentPassiveEffectManager.class.getDeclaredField("playerBonusCache"); cache.setAccessible(true);
        @SuppressWarnings("unchecked") var map = (Map<UUID, Map<String, Double>>) cache.get(passives);
        map.put(attacker.getUniqueId(), Map.of("backstabBonus", .3, "backstabExtraBonus", .5,
                "executeDamageBonus", .25, "executeThreshold", .3));
    }
    EntityDamageByEntityEvent hit() {
        var event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker); when(event.getEntity()).thenReturn(target);
        var amount = new AtomicReference<>(10.0);
        when(event.getDamage()).thenAnswer(i -> amount.get());
        doAnswer(i -> { amount.set(i.getArgument(0)); return null; }).when(event).setDamage(anyDouble());
        passives.onEntityDamageByEntity(event);
        return event;
    }
    @ParameterizedTest @CsvSource({"0,-2,0,18", "0,2,0,10", "2,0,0,10", "0,0,0,10", "0,-2,90,18", "0,-2,-90,18"})
    void rearWeaponHitsTriggerRegardlessOfPitchButFrontSideAndCoincidentHitsDoNot(double x, double z, float pitch, double expected) {
        when(attacker.getLocation()).thenReturn(new Location(world, x, 64, z));
        when(target.getLocation()).thenAnswer(i -> new Location(world, 0, 64, 0, 0, pitch));
        var event = hit();
        assertEquals(expected, event.getDamage(), 1e-8);
        assertEquals(expected > 10 ? .8 : 0, passives.takeAttackBonuses(event).backstab(), 1e-8);
        verify(attacker, never()).sendMessage(anyString());
    }
    @Test void rearConeRotatesWithVictimYawAndAllowsHeightDifference() {
        when(target.getLocation()).thenAnswer(i -> new Location(world, 0, 64, 0, 90, 70));
        when(attacker.getLocation()).thenReturn(new Location(world, 2, 65, 0));
        assertEquals(18, hit().getDamage(), 1e-8);
    }
    @Test void backstabAndExecuteAreAppliedOnceAndFeedbackStaysWithItsOwnEvent() {
        when(attacker.getLocation()).thenReturn(new Location(world, 0, 64, -2));
        when(target.getHealth()).thenReturn(5.0);
        var first = hit(); assertEquals(22.5, first.getDamage(), 1e-8);
        when(attacker.getLocation()).thenReturn(new Location(world, 0, 64, 2));
        when(target.getHealth()).thenReturn(20.0);
        var second = hit(); assertEquals(10, second.getDamage(), 1e-8);
        assertEquals(TalentPassiveEffectManager.AttackBonuses.NONE, passives.takeAttackBonuses(second));
        var bonuses = passives.takeAttackBonuses(first);
        assertEquals(.8, bonuses.backstab(), 1e-8); assertEquals(.25, bonuses.execute(), 1e-8);
        assertEquals(TalentPassiveEffectManager.AttackBonuses.NONE, passives.takeAttackBonuses(first));
        verify(attacker, never()).sendMessage(anyString());
    }
    @Test void shutdownDropsUnconsumedHitFeedback() {
        when(attacker.getLocation()).thenReturn(new Location(world, 0, 64, -2));
        var event = hit(); passives.shutdown();
        assertEquals(TalentPassiveEffectManager.AttackBonuses.NONE, passives.takeAttackBonuses(event));
    }
    @Test void damageTextIncludesBothBonusesAndOneFinalTotal() {
        String text = ChatColor.stripColor(DamageDisplayManager.formatDamageMessage("玩家", "敵人", 22.5, false,
                77.5, 100, new TalentPassiveEffectManager.AttackBonuses(.8, .25)));
        assertTrue(text.contains("玩家 對 敵人 造成了 22.5 傷害 + 背刺(+80%) + 收割(+25%)（加成已計入）"), text);
        assertTrue(text.endsWith("77.5/100 HP"), text);
    }
    @Test void normalDamageTextDoesNotBorrowAnyAssassinLabels() {
        String text = ChatColor.stripColor(DamageDisplayManager.formatDamageMessage("玩家", "敵人", 10, true,
                90, 100, TalentPassiveEffectManager.AttackBonuses.NONE));
        assertFalse(text.contains("背刺")); assertFalse(text.contains("收割")); assertTrue(text.contains("暴擊"));
    }
}

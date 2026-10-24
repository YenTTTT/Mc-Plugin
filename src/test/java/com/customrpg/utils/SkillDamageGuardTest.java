package com.customrpg.utils;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillDamageGuardTest {
    @Test void attributedFireUsesTypedDamageAndAllGuardsUnwindEvenWhenDamageThrows() {
        var caster=mock(org.bukkit.entity.Player.class); when(caster.getUniqueId()).thenReturn(UUID.randomUUID());
        var target=mock(LivingEntity.class); var source=mock(org.bukkit.damage.DamageSource.class);
        doAnswer(i->{
            assertTrue(SkillDamageGuard.isSkillDamage(caster));
            assertTrue(SkillDamageGuard.isNormalized(caster));
            assertTrue(SkillDamageGuard.isFireTick(caster));
            throw new IllegalStateException("downstream listener");
        }).when(target).damage(5.0,source);
        assertThrows(IllegalStateException.class,()->SkillDamageGuard.damageFireTick(target,5,caster,source));
        verify(target,never()).damage(anyDouble(),eq(caster));
        assertFalse(SkillDamageGuard.isSkillDamage(caster));
        assertFalse(SkillDamageGuard.isNormalized(caster));
        assertFalse(SkillDamageGuard.isFireTick(caster));
    }
    @Test void nestedDamageRetainsGuardUntilOutermostCallReturns() {
        Entity caster = mock(Entity.class);
        when(caster.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity first = mock(LivingEntity.class);
        LivingEntity second = mock(LivingEntity.class);
        doAnswer(invocation -> {
            assertTrue(SkillDamageGuard.isSkillDamage(caster));
            return null;
        }).when(second).damage(5.0, caster);
        doAnswer(invocation -> {
            assertTrue(SkillDamageGuard.isSkillDamage(caster));
            SkillDamageGuard.damage(second, 5, caster);
            assertTrue(SkillDamageGuard.isSkillDamage(caster));
            return null;
        }).when(first).damage(10.0, caster);
        SkillDamageGuard.damage(first, 10, caster);
        assertFalse(SkillDamageGuard.isSkillDamage(caster));
    }

    @Test void failedDamageAlwaysReleasesGuard() {
        Entity caster = mock(Entity.class);
        when(caster.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity target = mock(LivingEntity.class);
        doThrow(new IllegalArgumentException("invalid damage")).when(target).damage(10.0, caster);
        assertThrows(IllegalArgumentException.class, () -> SkillDamageGuard.damage(target, 10, caster));
        assertFalse(SkillDamageGuard.isSkillDamage(caster));
    }
    @Test void normalizedSnapshotsKeepBothGuardsAndCleanThemAfterExceptions() {
        Entity caster=mock(Entity.class);when(caster.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity target=mock(LivingEntity.class);
        doAnswer(i->{assertTrue(SkillDamageGuard.isSkillDamage(caster));assertTrue(SkillDamageGuard.isNormalized(caster));throw new IllegalStateException("cancelled downstream");}).when(target).damage(10.0,caster);
        assertThrows(IllegalStateException.class,()->SkillDamageGuard.damageNormalized(target,10,caster));
        assertFalse(SkillDamageGuard.isSkillDamage(caster));assertFalse(SkillDamageGuard.isNormalized(caster));
    }
    @Test void transferredDamageCannotRecursivelyTransferOrLeakGuardsWhenListenersThrow(){
        var attacker=mock(LivingEntity.class);when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        var target=mock(LivingEntity.class);when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        var nested=mock(LivingEntity.class);when(nested.getUniqueId()).thenReturn(UUID.randomUUID());
        var source=mock(org.bukkit.damage.DamageSource.class);
        doAnswer(i->{assertTrue(SkillDamageGuard.isTransfer(target));assertTrue(SkillDamageGuard.isSkillDamage(attacker));SkillDamageGuard.transfer(nested,5,attacker,source);throw new IllegalStateException("cancelled downstream");}).when(target).damage(5.0,source);
        assertThrows(IllegalStateException.class,()->SkillDamageGuard.transfer(target,5,attacker,source));
        verify(nested,never()).damage(anyDouble(),any(org.bukkit.damage.DamageSource.class));assertFalse(SkillDamageGuard.isTransfer(target));assertFalse(SkillDamageGuard.isSkillDamage(attacker));
    }

    @Test void confirmedFollowUpRestoresHurtFramesAndGuardsEvenWhenDamageThrows(){var source=mock(Entity.class);when(source.getUniqueId()).thenReturn(UUID.randomUUID());var target=mock(LivingEntity.class);var frames=new java.util.concurrent.atomic.AtomicInteger(18);when(target.getNoDamageTicks()).thenAnswer(x->frames.get());doAnswer(x->{frames.set(x.getArgument(0));return null;}).when(target).setNoDamageTicks(anyInt());doAnswer(x->{assertEquals(0,frames.get());assertTrue(SkillDamageGuard.isSkillDamage(source));throw new IllegalStateException("listener");}).when(target).damage(4.0,source);assertThrows(IllegalStateException.class,()->SkillDamageGuard.damageFollowUp(target,4,source,false));assertEquals(18,frames.get());assertFalse(SkillDamageGuard.isSkillDamage(source));}
    @Test void normalizedPeriodicEchoRetainsBothScopesAndCleansThemAfterReturn(){var source=mock(Entity.class);when(source.getUniqueId()).thenReturn(UUID.randomUUID());var target=mock(LivingEntity.class);doAnswer(x->{assertTrue(SkillDamageGuard.isNormalized(source));assertTrue(SkillDamageGuard.isPeriodic(source));return null;}).when(target).damage(4.9,source);SkillDamageGuard.damageNormalizedPeriodic(target,4.9,source);assertFalse(SkillDamageGuard.isNormalized(source));assertFalse(SkillDamageGuard.isPeriodic(source));assertFalse(SkillDamageGuard.isSkillDamage(source));}

    @Test void spiritDamageScopeProtectsHurtFramesAndNeverLeaksAfterAnException(){var source=mock(Entity.class);when(source.getUniqueId()).thenReturn(UUID.randomUUID());var target=mock(LivingEntity.class);when(target.getNoDamageTicks()).thenReturn(12);doAnswer(i->{assertTrue(SkillDamageGuard.isSpiritSkill(source));assertTrue(SkillDamageGuard.isSkillDamage(source));throw new IllegalStateException("rejected hit");}).when(target).damage(14.0,source);assertThrows(IllegalStateException.class,()->SkillDamageGuard.damageSpirit(target,14,source,true));assertFalse(SkillDamageGuard.isSpiritSkill(source));assertFalse(SkillDamageGuard.isSkillDamage(source));verify(target).setNoDamageTicks(12);}

}

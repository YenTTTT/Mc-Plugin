package com.customrpg.utils;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillDamageGuardTest {
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
}

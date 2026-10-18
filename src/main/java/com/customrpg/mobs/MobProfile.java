package com.customrpg.mobs;

import org.bukkit.entity.EntityType;
import java.util.*;

/** No ItemStacks or registry-backed attribute constants are constructed while reading configuration. */
public record MobProfile(String id, EntityType entityType, double scale, int color, String particle,
                         Map<String,String> equipment, List<MobAbility> abilities, double cooldown,
                         double windup, double radius, double range, double damageMultiplier,
                         double rageThreshold, double speedBonus, String temperament,
                         boolean legacyBehavior, String description) {
    public MobProfile {
        Objects.requireNonNull(id); Objects.requireNonNull(entityType); Objects.requireNonNull(description);
        equipment = Map.copyOf(equipment); abilities = List.copyOf(abilities);
        bounded(scale, 0.4, 2.0, "scale"); bounded(cooldown, 3, 60, "cooldown");
        bounded(windup, 0.5, 5, "windup"); bounded(radius, 1, 6, "radius");
        if (cooldown < windup + 1) throw new IllegalArgumentException("Cooldown must leave at least one second after the windup");
        bounded(range, 3, 20, "range"); bounded(damageMultiplier, 0.25, 2, "damage multiplier");
        bounded(rageThreshold, 0, 0.6, "rage threshold"); bounded(speedBonus, -0.5, 0.5, "speed");
        if (abilities.isEmpty() || abilities.size() > 4) throw new IllegalArgumentException(id + ": 1-4 abilities required");
        if (!Set.of("aggressive", "neutral", "timid").contains(temperament)) throw new IllegalArgumentException("Invalid temperament: " + temperament);
        if (!entityType.isAlive() || Set.of(EntityType.PLAYER, EntityType.ARMOR_STAND, EntityType.ENDER_DRAGON,
                EntityType.WITHER, EntityType.WARDEN).contains(entityType)) throw new IllegalArgumentException("Unsafe appearance type: " + entityType);
        if (color < 0 || color > 0xffffff) throw new IllegalArgumentException("Invalid RGB color");
    }
    private static void bounded(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid " + name + ": " + value);
    }
}

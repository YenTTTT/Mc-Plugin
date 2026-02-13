package com.customrpg.weaponSkills.skills;

import com.customrpg.weaponSkills.BaseSkill;
import com.customrpg.weaponSkills.SkillContext;
import com.customrpg.weaponSkills.SkillTriggerType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ConfigDrivenWeaponSkill
 *
 * A generic weapon skill driven by config/weapons/skills/*.yml.
 *
 * Supported types (for now):
 * - line_damage: forward box targeting (thorn_spike)
 * - radius_damage: radius AoE (fire_nova)
 * - dash: movement burst (dash)
 */
public class ConfigDrivenWeaponSkill extends BaseSkill {

    private final String defaultType;
    private final Set<SkillTriggerType> triggers;

    public ConfigDrivenWeaponSkill(String id, String displayName, String type, Set<SkillTriggerType> triggers) {
        super(id, displayName == null ? id : displayName, triggers);
        this.defaultType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        this.triggers = triggers;
    }

    @Override
    public long getCooldownMillis(SkillContext context) {
        // cooldown is expected to already be merged into weaponData.extra by ConfigManager
        if (context.weaponData() == null) {
            return 0L;
        }
        int sec = context.weaponData().getIntExtra("active-skill-cooldown", 0);
        return Math.max(0L, sec) * 1000L;
    }

    private String resolveType(SkillContext context) {
        if (context == null || context.weaponData() == null) {
            return defaultType;
        }
        Object t = context.weaponData().getExtra().get("active-skill-type");
        String type = t == null ? "" : String.valueOf(t).trim().toLowerCase(Locale.ROOT);
        if (type.isBlank()) {
            return defaultType;
        }
        return type;
    }

    @Override
    public boolean cast(SkillContext context) {
        if (context.weaponData() == null) {
            return false;
        }

        String type = resolveType(context);

        double damage = context.weaponData().getDoubleExtra("active-skill-damage", 0.0);
        double range = context.weaponData().getDoubleExtra("active-skill-range", 0.0);
        double width = context.weaponData().getDoubleExtra("active-skill-aoe-width", 0.0);

        // Fallback single values
        String particle = String.valueOf(context.weaponData().getExtra().getOrDefault("active-skill-particle", ""));
        String sound = String.valueOf(context.weaponData().getExtra().getOrDefault("active-skill-sound", ""));

        // Preferred complex visuals
        Object visualsObj = context.weaponData().getExtra().get("active-skill-visuals");
        @SuppressWarnings("unchecked")
        Map<String, Object> visuals = visualsObj instanceof Map ? (Map<String, Object>) visualsObj : null;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> particleList = visuals != null && visuals.get("particles") instanceof List
                ? (List<Map<String, Object>>) visuals.get("particles")
                : null;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> soundList = visuals != null && visuals.get("sounds") instanceof List
                ? (List<Map<String, Object>>) visuals.get("sounds")
                : null;

        Location origin = context.caster().getLocation();

        switch (type) {
            case "dash" -> {
                Vector direction = origin.getDirection().normalize();
                direction.setY(0.25);
                direction.multiply(1.8);
                context.caster().setVelocity(direction);

                if (particleList != null && !particleList.isEmpty()) {
                    context.services().particles().playParticles(origin, direction, null, particleList);
                } else if (particle != null && !particle.isBlank()) {
                    context.services().particles().trail(context.caster(), particle, 10, 0.25);
                }

                if (soundList != null && !soundList.isEmpty()) {
                    context.services().sounds().playSounds(origin, soundList);
                } else if (sound != null && !sound.isBlank()) {
                    context.services().sounds().playSound(origin, sound, 1.0f, 1.2f);
                }
                return true;
            }
            case "radius_damage" -> {
                if (particleList != null && !particleList.isEmpty()) {
                    context.services().particles().playParticles(origin, origin.getDirection(), origin, particleList);
                } else if (particle != null && !particle.isBlank()) {
                    context.services().particles().burst(origin, particle, 40, 0.8, 0.5, 0.8, 0.02);
                }

                if (soundList != null && !soundList.isEmpty()) {
                    context.services().sounds().playSounds(origin, soundList);
                } else if (sound != null && !sound.isBlank()) {
                    context.services().sounds().playSound(origin, sound, 1.0f, 1.0f);
                }

                for (LivingEntity t : context.services().aoe().getRadiusTargets(context.caster(), origin, Math.max(0.5, range))) {
                    // Apply weapon stats to skill damage (multiplier + crit)
                    context.services().damage().dealSkillDamageWithWeaponStats(
                            context.caster(), t, Math.max(0.0, damage),
                            context.weaponData(), true, true);
                }
                return true;
            }
            case "line_damage" -> {
                Vector dir = origin.getDirection().normalize();
                Location center = origin.clone().add(dir.multiply(Math.max(0.5, range)));

                if (particleList != null && !particleList.isEmpty()) {
                    context.services().particles().playParticles(origin, dir, center, particleList);
                } else if (particle != null && !particle.isBlank()) {
                    context.services().particles().burst(center, particle, 80, width / 2.0, 0.8, width / 2.0, 0.02);
                }

                if (soundList != null && !soundList.isEmpty()) {
                    context.services().sounds().playSounds(origin, soundList);
                } else if (sound != null && !sound.isBlank()) {
                    context.services().sounds().playSound(origin, sound, 1.0f, 1.0f);
                }

                double half = Math.max(0.5, width / 2.0);
                for (LivingEntity t : context.services().aoe().getBoxTargets(context.caster(), center, half, 1.5, half)) {
                    // Apply weapon stats to skill damage (multiplier + crit)
                    context.services().damage().dealSkillDamageWithWeaponStats(
                            context.caster(), t, Math.max(0.0, damage),
                            context.weaponData(), true, true);
                }
                return true;
            }
            case "target_damage" -> {
                double maxRange = Math.max(1.0, range);
                LivingEntity target = context.caster().getTargetEntity((int) Math.ceil(maxRange)) instanceof LivingEntity le ? le : null;
                if (target == null || target.equals(context.caster())) {
                    return false;
                }

                double healPlayer = context.weaponData().getDoubleExtra("active-skill-heal-player", 0.0);

                Location from = origin.clone().add(0, 1.3, 0);
                Location to = target.getLocation().clone().add(0, Math.max(0.8, target.getHeight() * 0.6), 0);
                Vector dir = to.toVector().subtract(from.toVector()).normalize();

                // draw line particles
                if (particleList != null && !particleList.isEmpty()) {
                    context.services().particles().playParticles(from, dir, to, particleList);
                } else if (particle != null && !particle.isBlank()) {
                    // fallback to line steps using trail-ish behaviour
                    context.services().particles().playParticles(from, dir, to, java.util.List.of(java.util.Map.of(
                            "id", particle,
                            "count", 6,
                            "offset-x", 0.02,
                            "offset-y", 0.02,
                            "offset-z", 0.02,
                            "extra", 0.0,
                            "animation", java.util.Map.of("style", "LINE_STEPS", "steps", 18, "step-size", 0.6)
                    )));
                }

                // sounds
                if (soundList != null && !soundList.isEmpty()) {
                    context.services().sounds().playSounds(origin, soundList);
                } else if (sound != null && !sound.isBlank()) {
                    context.services().sounds().playSound(origin, sound, 1.0f, 1.0f);
                }

                // damage + heal
                // Apply weapon stats to skill damage (multiplier + crit)
                context.services().damage().dealSkillDamageWithWeaponStats(
                        context.caster(), target, Math.max(0.0, damage),
                        context.weaponData(), true, true);

                if (healPlayer > 0) {
                    @SuppressWarnings("deprecation")
                    double maxHp = getMaxHealthSafe(context.caster());
                    double newHp = Math.min(maxHp, context.caster().getHealth() + healPlayer);
                    context.caster().setHealth(Math.max(0.0, newHp));
                }
                return true;
            }

            default -> {
                // fallback: no-op
                return false;
            }
        }
    }

    /**
     * Safe method to get max health (handles deprecated API)
     */
    @SuppressWarnings("deprecation")
    private static double getMaxHealthSafe(Player player) {
        try {
            var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (attr != null) {
                return attr.getValue();
            }
        } catch (Exception ignored) {
        }
        return player.getMaxHealth();
    }

    public String getType() { return defaultType; }

    public Set<SkillTriggerType> getTriggers() {
        return triggers;
    }

    @SuppressWarnings("unchecked")
    public static String readTypeFromConfig(Map<String, Object> weaponSkillData) {
        if (weaponSkillData == null) {
            return "";
        }
        Object t = weaponSkillData.get("type");
        return t == null ? "" : String.valueOf(t);
    }
}

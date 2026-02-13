package com.customrpg.weaponSkills.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * AoEUtil
 *
 * 圓形範圍
 */
public class AoEUtil {

    public List<LivingEntity> getRadiusTargets(Player caster, Location center, double radius) {
        if (caster == null || center == null) {
            return List.of();
        }
        World world = center.getWorld();
        if (world == null) {
            return List.of();
        }

        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le)) {
                continue;
            }
            if (le.equals(caster)) {
                continue;
            }
            out.add(le);
        }
        return out;
    }

    /**
     * 前方線型
     */
    public List<LivingEntity> getLineTargets(Player caster, double range, double width, double height) {
        if (caster == null) {
            return List.of();
        }
        Location origin = caster.getLocation();
        Vector dir = origin.getDirection().normalize();

        double r = Math.max(0.5, range);
        Location center = origin.clone().add(dir.multiply(r));
        double halfW = Math.max(0.5, width / 2.0);
        double halfH = Math.max(0.5, height / 2.0);

        return getBoxTargets(caster, center, halfW, halfH, halfW);
    }

    /**
     * 方形區域
     */
    public List<LivingEntity> getBoxTargets(Player caster, Location center, double halfX, double halfY, double halfZ) {
        if (caster == null || center == null) {
            return List.of();
        }
        World world = center.getWorld();
        if (world == null) {
            return List.of();
        }

        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(center, halfX, halfY, halfZ)) {
            if (!(e instanceof LivingEntity le)) {
                continue;
            }
            if (le.equals(caster)) {
                continue;
            }
            out.add(le);
        }
        return out;
    }
}

package com.customrpg.managers;

import com.customrpg.talents.HunterPath;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Vanilla-only character silhouettes. Each rendering call emits at most 64 particle positions. */
public final class HunterVisuals {
    public enum Style { FIST, BLADE, ORB, THUNDER, CHAIN, RUBBER, CARD, YOYO, EYES, BUDDHA, AURA }
    public static Style style(String id) {
        if (id.equals("nen_rock") || id.equals("nen_gon_ko") || id.equals("nen_gon_rush")) return Style.FIST;
        if (id.equals("nen_scissors") || id.equals("nen_gon_combo")) return Style.BLADE;
        if (id.equals("nen_paper")) return Style.ORB;
        if (id.equals("nen_killua_yoyo")) return Style.YOYO;
        if (id.equals("nen_kurapika_emperor") || id.equals("nen_gyo")) return Style.EYES;
        if (HunterPath.of(id) == HunterPath.KILLUA) return Style.THUNDER;
        if (HunterPath.of(id) == HunterPath.KURAPIKA) return Style.CHAIN;
        if (id.equals("nen_hisoka_cards")) return Style.CARD;
        if (HunterPath.of(id) == HunterPath.HISOKA) return Style.RUBBER;
        if (HunterPath.of(id) == HunterPath.NETERO) return Style.BUDDHA;
        return Style.AURA;
    }
    private int remaining;
    private void dot(Location at, int color, float size) {
        if (remaining-- <= 0) return;
        at.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(color), size));
    }
    private Vector forward(Player player) {
        Vector f = player.getEyeLocation().getDirection().setY(0);
        return f.lengthSquared() < .01 ? new Vector(0, 0, 1) : f.normalize();
    }
    private Vector right(Player player) { Vector f = forward(player); return new Vector(f.getZ(), 0, -f.getX()); }
    private void circle(Location center, Vector horizontal, double radius, int color, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            dot(center.clone().add(horizontal.clone().multiply(Math.cos(a) * radius)).add(0, Math.sin(a) * radius, 0), color, 1);
        }
    }
    public void cast(Player player, String id) {
        pulse(player, id, player.getLocation(), 0);
        String sound = switch (style(id)) {
            case THUNDER -> "entity.lightning_bolt.impact";
            case CHAIN -> "block.chain.place";
            case RUBBER -> "entity.slime.jump";
            case CARD, BLADE -> "entity.player.attack.sweep";
            case BUDDHA -> "block.bell.resonate";
            default -> "block.amethyst_block.chime";
        };
        player.getWorld().playSound(player.getLocation(), sound, .65f, id.equals("nen_netero_zero") ? .6f : 1.15f);
    }
    public void pulse(Player player, String id, Location center, int phase) {
        remaining = 64; Style style = style(id); int color = HunterPath.of(id).color;
        if (id.equals("nen_ten")) color = 0xEFEFFF;
        if (id.equals("nen_ren")) color = 0xFFB65A;
        if (id.equals("nen_zetsu")) color = 0x767989;
        Vector f = forward(player), r = right(player); Location eye = center.clone().add(0, 1.5, 0);
        switch (style) {
            case FIST, ORB -> {
                Location fist = eye.clone().add(f.clone().multiply(.7)).add(r.clone().multiply(.4));
                for (int i = 0; i < 32; i++) {
                    double a = i * Math.PI / 7 + phase;
                    dot(fist.clone().add(Math.cos(a) * .35, (i % 8) * .06 - .2, Math.sin(a) * .35), i % 3 == 0 ? 0xFFF3B2 : color, 1.2f);
                }
            }
            case BLADE -> {
                for (int i = 0; i < 28; i++) {
                    double a = -1.2 + i * 2.4 / 27;
                    dot(eye.clone().add(f.clone().multiply(Math.cos(a) * 2)).add(r.clone().multiply(Math.sin(a) * 2)), 0x84FFB0, 1.3f);
                }
            }
            case THUNDER -> {
                for (int strand = 0; strand < 3; strand++) for (int i = 0; i < 14; i++) {
                    double a = i * 1.7 + strand * 2 + phase;
                    dot(center.clone().add(Math.cos(a) * .55, .1 + i * .13, Math.sin(a) * .55), i % 2 == 0 ? 0xE8FAFF : color, 1);
                }
            }
            case CHAIN -> {
                for (int i = 0; i < 5; i++) circle(eye.clone().add(f.clone().multiply(.3)).add(r.clone().multiply((i - 2) * .26)), r, .13, color, 8);
                if (id.equals("nen_kurapika_heal")) for (int i = -4; i <= 4; i++) {
                    dot(eye.clone().add(r.clone().multiply(i * .13)), 0x7DFFC3, 1.2f);
                    dot(eye.clone().add(0, i * .13, 0), 0x7DFFC3, 1.2f);
                }
            }
            case RUBBER -> {
                for (int i = 0; i < 32; i++) {
                    double a = i * Math.PI * 2 / 32;
                    dot(center.clone().add(Math.cos(a) * 1.2, .15 + Math.sin(a * 3 + phase) * .1, Math.sin(a) * 1.2), color, 1.2f);
                }
            }
            case CARD -> card(eye.clone().add(f), r, color);
            case YOYO -> {
                for (int side : new int[]{-1, 1}) {
                    Location disk = eye.clone().add(r.clone().multiply(side * (1 + Math.sin(phase) * .4))).add(f.clone().multiply(.6 + phase * .3));
                    circle(disk, r, .3, 0xDEE4F4, 12); circle(disk, r, .1, color, 6);
                }
            }
            case EYES -> {
                int eyeColor = id.equals("nen_gyo") ? 0xFFF8A6 : 0xFF203D;
                for (int side : new int[]{-1, 1}) for (int i = 0; i < 8; i++)
                    dot(eye.clone().add(0, .12, 0).add(f.clone().multiply(.35)).add(r.clone().multiply(side * .15 + (i - 4) * .015)), eyeColor, 1);
                circle(eye.clone().add(0, .4, 0), r, .65, eyeColor, 24);
            }
            case BUDDHA -> {
                Location statue = eye.clone().subtract(f.clone().multiply(1.5)).add(0, 1, 0);
                circle(statue.clone().add(0, 1, 0), r, .4, color, 12);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 7;
                    for (int side : new int[]{-1, 1}) for (int segment = 1; segment <= 3; segment++)
                        dot(statue.clone().add(r.clone().multiply(side * Math.sin(a) * segment * .45)).add(0, Math.cos(a) * segment * .45, 0), color, 1.2f);
                }
            }
            case AURA -> {
                for (int i = 0; i < 32; i++) {
                    double a = i * .65 + phase;
                    dot(center.clone().add(Math.cos(a) * .6, (i % 16) * .12, Math.sin(a) * .6), color, 1);
                }
            }
        }
    }
    private void card(Location center, Vector right, int color) {
        for (int i = 0; i < 6; i++) {
            double d = i * .144 - .36;
            dot(center.clone().add(right.clone().multiply(d)).add(0, -.5, 0), 0xF7F3EA, 1);
            dot(center.clone().add(right.clone().multiply(d)).add(0, .5, 0), 0xF7F3EA, 1);
            dot(center.clone().add(right.clone().multiply(-.36)).add(0, d * 1.4, 0), color, 1);
            dot(center.clone().add(right.clone().multiply(.36)).add(0, d * 1.4, 0), color, 1);
        }
        for (int i = -3; i <= 3; i++) dot(center.clone().add(right.clone().multiply(i * .06)).add(0, Math.abs(i) * .08 - .12, 0), 0xE83565, 1);
    }
    public void link(Player source, String id, Location start, Location end) {
        remaining = 64; Vector delta = end.toVector().subtract(start.toVector()); double length = delta.length();
        if (length < .01) return; delta.normalize(); Vector side = right(source); int color = HunterPath.of(id).color;
        if (style(id) == Style.ORB) color = 0x83F4AA;
        int steps = Math.min(32, Math.max(2, (int)Math.ceil(length / .4)));
        if (style(id) == Style.BUDDHA) steps = Math.min(20, steps);
        for (int i = 0; i <= steps; i++) {
            Location at = start.clone().add(delta.clone().multiply(length * i / steps));
            switch (style(id)) {
                case CHAIN -> { dot(at.clone().add(side.clone().multiply(i % 2 == 0 ? .1 : -.1)), color, 1); dot(at, 0xFFF1C4, .8f); }
                case THUNDER -> dot(at.add(side.clone().multiply(i % 2 == 0 ? .18 : -.18)), i % 3 == 0 ? 0xFFFFFF : color, 1.2f);
                case RUBBER -> { dot(at.clone().add(side.clone().multiply(Math.sin(i) * .12)), color, 1.3f); dot(at, 0xFDD5EC, .8f); }
                case BUDDHA -> {
                    dot(at, 0xFFF5CF, 1.6f); dot(at.clone().add(side.clone().multiply(.22)), color, 1.4f); dot(at.clone().subtract(side.clone().multiply(.22)), color, 1.4f);
                }
                default -> dot(at, color, 1.3f);
            }
        }
        if (style(id) == Style.CARD) card(end, side, color);
    }
    public void afterimage(Player source, Location at) {
        remaining = 64; Vector r = right(source); int color = 0xA8ECFF;
        circle(at.clone().add(0, 1.65, 0), r, .18, color, 10);
        for (int i = 0; i < 9; i++) {
            double y = .25 + i * .14;
            dot(at.clone().add(0, y, 0), color, 1);
            if (i > 4) for (int side : new int[]{-1, 1}) dot(at.clone().add(r.clone().multiply(side * .35)).add(0, y, 0), color, .8f);
        }
    }
}

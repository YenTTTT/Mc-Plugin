package com.customrpg.balance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * Initial Lv.1-60 PvE specification and offline calculator.
 * Pure formulas without Bukkit dependencies; CombatBalance and the migration adapters apply them at runtime.
 * All damage and HP use the same RPG units; reference damage assumes a single mitigation layer.
 */
public final class InitialBalanceModel {
    public static final int MAX_LEVEL = 60;
    public static final int POINTS_PER_LEVEL = 3;
    public static final double ACCURACY = .80;
    public static final double NORMAL_TTK = 5.0;
    public static final double NORMAL_HITS_TO_DIE = 6.0;
    public static final double MOB_ATTACK_INTERVAL = 1.8;
    private static final int[] GEAR_LEVELS = {1, 8, 18, 28, 38, 48, 58};

    private InitialBalanceModel() {}

    public enum Primary { STRENGTH, MAGIC, AGILITY }

    /** DPS allocation includes the entire hit, including its attribute contribution. */
    public enum Weapon {
        SWORD(1.6, 1.00, Primary.STRENGTH),
        GREATSWORD(.9, 1.03, Primary.STRENGTH),
        AXE(1.1, 1.00, Primary.STRENGTH),
        GREATAXE(.8, 1.03, Primary.STRENGTH),
        SCYTHE(1.2, .95, Primary.AGILITY),
        STAFF(1.4, .85, Primary.MAGIC),
        BOW(1.0, .95, Primary.AGILITY),
        SPEAR(1.2, .97, Primary.STRENGTH);

        public final double attacksPerSecond;
        public final double dpsBudget;
        public final Primary primary;

        Weapon(double attacksPerSecond, double dpsBudget, Primary primary) {
            this.attacksPerSecond = attacksPerSecond;
            this.dpsBudget = dpsBudget;
            this.primary = primary;
        }
    }

    public enum Build {
        BALANCED(.60, .25), GLASS(1.00, 0), TANK(.25, .45);
        final double primaryShare;
        final double vitalityShare;
        Build(double primaryShare, double vitalityShare) {
            this.primaryShare = primaryShare;
            this.vitalityShare = vitalityShare;
        }
    }

    /** The remaining points go to defense; no points are created by rounding. */
    public record Allocation(int primary, int vitality, int defense) {}
    public record Gear(double primary, double vitality, double defense) {}

    /** A role's damage multiplier changes hit pressure, not just its health. */
    public enum Role {
        NORMAL(1, 1, 0), ELITE(3, 1.35, .10),
        WILDERNESS_BOSS(14, 1.40, .20), GROUP_BOSS(24, 1.40, .20);
        public final double healthMultiplier;
        public final double damageMultiplier;
        public final double reduction;
        Role(double healthMultiplier, double damageMultiplier, double reduction) {
            this.healthMultiplier = healthMultiplier;
            this.damageMultiplier = damageMultiplier;
            this.reduction = reduction;
        }
    }

    private static void level(int value) {
        if (value < 1 || value > MAX_LEVEL) throw new IllegalArgumentException("Level must be 1..60");
    }

    private static void nonnegative(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Expected finite nonnegative value");
    }

    public static int earnedPoints(int level) {
        level(level);
        return POINTS_PER_LEVEL * (level - 1);
    }

    public static Allocation allocation(int level, Build build) {
        int points = earnedPoints(level);
        int primary = (int) Math.floor(points * build.primaryShare);
        int vitality = (int) Math.floor(points * build.vitalityShare);
        return new Allocation(primary, vitality, points - primary - vitality);
    }

    /** Total budget across four armor pieces and two accessories, not per item. */
    public static Gear referenceGear(int gearLevel) {
        level(gearLevel);
        int n = gearLevel - 1;
        return new Gear(Math.floor(.4 * n), Math.floor(.2 * n), 10 + Math.floor(.5 * n));
    }

    public static int availableCraftingLevel(int playerLevel) {
        level(playerLevel);
        int result = 1;
        for (int anchor : GEAR_LEVELS) if (anchor <= playerLevel) result = anchor;
        return result;
    }

    public static double playerHealth(int level, double vitality, double flatHealth) {
        level(level); nonnegative(vitality); nonnegative(flatHealth);
        return 100 + 8 * (level - 1) + 3 * vitality + flatHealth;
    }

    public static double reduction(int defenderLevel, double defense) {
        level(defenderLevel); nonnegative(defense);
        return Math.min(.60, defense / (defense + 60 + 2 * defenderLevel));
    }

    public static double critChance(double agility, double bonusChance) {
        nonnegative(agility); nonnegative(bonusChance);
        return Math.min(.30, .05 + .001 * agility + bonusChance);
    }

    public static double critMultiplier(double bonusMultiplier) {
        nonnegative(bonusMultiplier);
        return Math.min(1.75, 1.50 + bonusMultiplier);
    }

    /** Base damage on the item, before primary attribute scaling and crits. */
    public static double weaponAttack(int itemLevel, Weapon weapon) {
        level(itemLevel);
        return (8 + .45 * (itemLevel - 1)) * 1.6 / weapon.attacksPerSecond * weapon.dpsBudget;
    }

    public static double fullHit(int itemLevel, Weapon weapon, double primary) {
        nonnegative(primary);
        return weaponAttack(itemLevel, weapon) + .25 * primary * 1.6 / weapon.attacksPerSecond * weapon.dpsBudget;
    }

    /** All weapons have the same primary contribution per second before their explicit DPS budget. */
    public static double expectedDps(int itemLevel, Weapon weapon, double primary, double agility, double accuracy) {
        nonnegative(accuracy);
        if (accuracy > 1) throw new IllegalArgumentException("Accuracy must be <= 1");
        return fullHit(itemLevel, weapon, primary) * weapon.attacksPerSecond * accuracy
                * (1 + critChance(agility, 0) * (critMultiplier(0) - 1));
    }

    public static double referenceHealth(int level) {
        return playerHealth(level, allocation(level, Build.BALANCED).vitality + referenceGear(level).vitality, 0);
    }

    public static double referenceReduction(int level) {
        return reduction(level, allocation(level, Build.BALANCED).defense + referenceGear(level).defense);
    }

    public static double referenceDps(int level) {
        return expectedDps(level, Weapon.SWORD,
                allocation(level, Build.BALANCED).primary + referenceGear(level).primary, 0, ACCURACY);
    }

    /** Monster values depend only on their level and role, never on an attacking player's build. */
    public static double mobHealth(int mobLevel, Role role, int participants) {
        if (participants < 1 || participants > 8) throw new IllegalArgumentException("Participants must be 1..8");
        double group = role == Role.GROUP_BOSS ? 1 + .75 * (participants - 1) : 1;
        // Compensate role armor so HP/(1-DR) is exactly the intended effective HP budget.
        return referenceDps(mobLevel) * NORMAL_TTK * role.healthMultiplier * (1 - role.reduction) * group;
    }

    public static double mobRawDamage(int mobLevel, Role role) {
        return referenceHealth(mobLevel) / (NORMAL_HITS_TO_DIE * (1 - referenceReduction(mobLevel)))
                * role.damageMultiplier;
    }

    public static double maxMana(int level, double spirit, double flatMana) {
        level(level); nonnegative(spirit); nonnegative(flatMana);
        return 80 + 2 * (level - 1) + 2 * spirit + flatMana;
    }

    public static double manaRegen(int level, double spirit) {
        level(level); nonnegative(spirit);
        return 1.5 + .025 * (level - 1) + .06 * spirit;
    }

    /** Discrete no-crit kill time, first landed hit at t=0; missed hits are replaced by their mean interval. */
    public static double discreteKillSeconds(double hp, double hit, double interval) {
        nonnegative(hp); nonnegative(hit); nonnegative(interval);
        if (hit == 0 || interval == 0) throw new IllegalArgumentException("Hit and interval must be positive");
        return Math.max(0, Math.ceil(hp / hit - 1e-10) - 1) * interval;
    }

    /** Generates reviewable model artifacts with the same formulas used by the Java implementation. */
    public static void export(Path directory) throws IOException {
        Files.createDirectories(directory);
        StringBuilder levels = new StringBuilder("\ufefflevel,points_this_level,total_points,primary_alloc,vit_alloc,def_alloc,gear_primary,gear_vit,gear_def,player_hp,player_dr,sword_base,sword_noncrit_hit,expected_dps,normal_hp,normal_raw_hit,normal_received_hit,hit_equivalents_to_die,continuous_ttk_s,noncrit_discrete_ttk_s,elite_hp,wilderness_boss_hp,group_boss_4p_hp,group_boss_4p_ttk_s,mana,mana_regen,available_crafting_level\n");
        StringBuilder scenarios = new StringBuilder("\ufeffplayer_level,mob_level,build,weapon,gear_level,accuracy,player_hp,player_dr,dps,normal_hp,continuous_ttk_s,hit_equivalents_to_die,noncrit_discrete_ttk_s\n");
        StringBuilder weapons = new StringBuilder("\ufeffitem_level,weapon,primary_stat,attacks_per_second,dps_budget,base_attack,damage_per_primary,balanced_noncrit_hit,balanced_dps\n");
        for (int l = 1; l <= MAX_LEVEL; l++) {
            Allocation a = allocation(l, Build.BALANCED); Gear g = referenceGear(l);
            double hit = fullHit(l, Weapon.SWORD, a.primary + g.primary);
            double received = mobRawDamage(l, Role.NORMAL) * (1 - referenceReduction(l));
            levels.append(String.format(Locale.ROOT,
                    "%d,%d,%d,%d,%d,%d,%.0f,%.0f,%.0f,%.2f,%.6f,%.3f,%.3f,%.4f,%.3f,%.3f,%.3f,%.4f,%.4f,%.4f,%.3f,%.3f,%.3f,%.4f,%.2f,%.4f,%d%n",
                    l, l == 1 ? 0 : POINTS_PER_LEVEL, earnedPoints(l), a.primary, a.vitality, a.defense,
                    g.primary, g.vitality, g.defense, referenceHealth(l), referenceReduction(l),
                    weaponAttack(l, Weapon.SWORD), hit, referenceDps(l), mobHealth(l, Role.NORMAL, 1),
                    mobRawDamage(l, Role.NORMAL), received, referenceHealth(l) / received,
                    mobHealth(l, Role.NORMAL, 1) / referenceDps(l),
                    discreteKillSeconds(mobHealth(l, Role.NORMAL, 1), hit, 1 / (1.6 * ACCURACY)),
                    mobHealth(l, Role.ELITE, 1), mobHealth(l, Role.WILDERNESS_BOSS, 1), mobHealth(l, Role.GROUP_BOSS, 4),
                    mobHealth(l, Role.GROUP_BOSS, 4) / ((1 - Role.GROUP_BOSS.reduction) * 4 * referenceDps(l)),
                    maxMana(l, 0, 0), manaRegen(l, 0), availableCraftingLevel(l)));
            for (Weapon w : Weapon.values()) {
                double primary = a.primary + g.primary;
                weapons.append(String.format(Locale.ROOT, "%d,%s,%s,%.2f,%.2f,%.4f,%.4f,%.4f,%.4f%n",
                        l, w, w.primary, w.attacksPerSecond, w.dpsBudget, weaponAttack(l, w),
                        .25 * 1.6 / w.attacksPerSecond * w.dpsBudget, fullHit(l, w, primary),
                        expectedDps(l, w, primary, w.primary == Primary.AGILITY ? primary : 0, ACCURACY)));
            }
            for (Build b : Build.values()) for (Weapon w : Weapon.values()) {
                for (int gearLevel : Arrays.stream(new int[]{l, availableCraftingLevel(l), Math.max(1, l - 10)}).distinct().toArray()) {
                    for (double accuracy : new double[]{.65, ACCURACY, .95}) {
                        scenario(scenarios, l, l, b, w, gearLevel, accuracy);
                    }
                }
            }
            for (int gap : new int[]{-10, -5, 5, 10}) {
                if (l + gap >= 1 && l + gap <= MAX_LEVEL) {
                    scenario(scenarios, l, l + gap, Build.BALANCED, Weapon.SWORD, l, ACCURACY);
                }
            }
        }
        Files.writeString(directory.resolve("BALANCE_LEVELS_1_60.csv"), levels, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("BALANCE_SCENARIOS.csv"), scenarios, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("BALANCE_WEAPONS.csv"), weapons, StandardCharsets.UTF_8);
    }

    private static void scenario(StringBuilder output, int playerLevel, int mobLevel, Build build, Weapon weapon,
                                 int gearLevel, double accuracy) {
        Allocation a = allocation(playerLevel, build); Gear gear = referenceGear(gearLevel);
        double primary = a.primary + gear.primary;
        double hp = playerHealth(playerLevel, a.vitality + gear.vitality, 0);
        double dr = reduction(playerLevel, a.defense + gear.defense);
        double agility = weapon.primary == Primary.AGILITY ? primary : 0;
        double dps = expectedDps(gearLevel, weapon, primary, agility, accuracy);
        double mobHp = mobHealth(mobLevel, Role.NORMAL, 1);
        output.append(String.format(Locale.ROOT, "%d,%d,%s,%s,%d,%.2f,%.2f,%.6f,%.4f,%.3f,%.4f,%.4f,%.4f%n",
                playerLevel, mobLevel, build, weapon, gearLevel, accuracy, hp, dr, dps, mobHp,
                mobHp / dps, hp / (mobRawDamage(mobLevel, Role.NORMAL) * (1 - dr)),
                discreteKillSeconds(mobHp, fullHit(gearLevel, weapon, primary), 1 / (weapon.attacksPerSecond * accuracy))));
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: InitialBalanceModel <output-directory>");
        export(Path.of(args[0]));
    }
}

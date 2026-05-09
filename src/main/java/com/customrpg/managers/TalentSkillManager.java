package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.Talent;
import com.customrpg.talents.TalentType;
import com.customrpg.weaponSkills.managers.CooldownManager;
import com.customrpg.weaponSkills.managers.DamageManager;
import com.customrpg.weaponSkills.util.AoEUtil;
import com.customrpg.weaponSkills.util.ParticleUtil;
import com.customrpg.weaponSkills.util.SoundUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Bee;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * TalentSkillManager - 處理天賦主動技能的執行邏輯
 */
public class TalentSkillManager {

    private final CustomRPG plugin;
    private final TalentManager talentManager;
    private final PlayerStatsManager statsManager;
    private final ManaManager manaManager;
    private final BloodManager bloodManager;
    private final CooldownManager cooldownManager;
    private final DamageManager damageManager;
    private final AoEUtil aoeUtil;
    private final ParticleUtil particleUtil;
    private final SoundUtil soundUtil;

    public TalentSkillManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.talentManager = plugin.getTalentManager();
        this.statsManager = plugin.getPlayerStatsManager();
        this.manaManager = plugin.getManaManager();
        this.bloodManager = plugin.getBloodManager();

        // 獲取與武器技能系統共用的管理器與工具
        // 注意：這裡假設 CustomRPG 已經初始化了這些組件
        this.cooldownManager = new CooldownManager(); // 獨立的天賦冷卻
        this.damageManager = new DamageManager();
        this.damageManager.setStatsManager(statsManager);
        this.aoeUtil = new AoEUtil();
        this.particleUtil = new ParticleUtil();
        this.soundUtil = new SoundUtil();
    }

    /**
     * 嘗試執行天賦技能
     */
    public boolean executeTalentSkill(Player player, String triggerType, ItemStack item) {
        // 檢查是否應該切換技能（蹲下右鍵）
        if (triggerType.equals("RIGHT_CLICK_SNEAK")) {
            SkillSwitchManager switchManager = plugin.getSkillSwitchManager();
            if (switchManager.handleSkillSwitch(player, item)) {
                return true; // 切換成功，不執行技能
            }
        }

        PlayerTalents pt = talentManager.getPlayerTalents(player);

        // 使用技能切換系統獲取當前選擇的技能
        SkillSwitchManager switchManager = plugin.getSkillSwitchManager();
        Talent selectedTalent = switchManager.getCurrentSelectedSkill(player, item);

        if (selectedTalent != null) {
            int level = pt.getTalentLevel(selectedTalent.getId());
            if (level > 0 && selectedTalent.getTriggerType() != null &&
                selectedTalent.getTriggerType().equalsIgnoreCase(triggerType)) {
                return castSkill(player, selectedTalent, level, item);
            }
        }

        return false;
    }

    private boolean castSkill(Player player, Talent talent, int level, ItemStack item) {
        String cdKey = "talent:" + talent.getId() + ":" + player.getUniqueId();
        
        // 1. 檢查冷卻
        if (!cooldownManager.canCast(player, cdKey)) {
            long remaining = cooldownManager.getRemainingCooldown(player, cdKey);
            player.sendMessage("§c天賦技能 " + talent.getName() + " 冷卻中 (" + (remaining / 1000 + 1) + "秒)");
            return false;
        }

        // 2. 檢查 Mana
        if (!manaManager.hasMana(player, talent.getManaCost())) {
            player.sendMessage("§c魔力不足！需要 " + (int)talent.getManaCost() + " MANA");
            return false;
        }

        // 2.5 檢查 HP 消耗（暗黑系技能）
        double hpCostPercent = talent.getHpCost();
        if (hpCostPercent > 0) {
            // 檢查暗黑韌性被動減免
            PlayerTalents pt2 = talentManager.getPlayerTalents(player);
            int resilienceLevel = pt2.getTalentLevel("dark_resilience");
            if (resilienceLevel > 0) {
                // 每級減免15%的HP消耗
                double reduction = resilienceLevel * 0.15;
                hpCostPercent *= (1.0 - reduction);
            }
            // 檢查暗黑神化 buff（免除HP消耗）
            if (player.hasMetadata("dark_apotheosis")) {
                hpCostPercent = 0;
            }
            double hpCost = player.getMaxHealth() * hpCostPercent;
            if (hpCost > 0 && player.getHealth() - hpCost < 2.0) {
                player.sendMessage("§4血量不足！此技能需要消耗 §c" + (int)(talent.getHpCost() * 100) + "% §4生命值");
                return false;
            }
        }

        // 3. 執行具體技能邏輯
        boolean success = false;
        switch (talent.getId()) {
            case "earth_shatter":
                success = executeEarthShatter(player, talent, level, item);
                break;
            case "magic_vortex":
                success = executeMagicVortex(player, talent, level, item);
                break;
            case "sniper_shot":
                success = executeSniperShot(player, talent, level, item);
                break;
            // 武器系新增技能
            case "suppressive_barrage":
                success = executeSuppressiveBarrage(player, talent, level, item);
                break;
            case "ricochet_round":
                success = executeRicochetRound(player, talent, level, item);
                break;
            case "cyclone_slash":
                success = executeCycloneSlash(player, talent, level, item);
                break;
            case "meteor_step":
                success = executeMeteorStep(player, talent, level, item);
                break;
            case "arcane_barrage":
                success = executeArcaneBarrage(player, talent, level, item);
                break;
            case "gravity_prison":
                success = executeGravityPrison(player, talent, level, item);
                break;
            case "ultimate_sword_rain":
                success = executeUltimateSwordRain(player, talent, level, item);
                break;
            // 自然系天賦
            case "star_shatter":
                success = executeStarShatter(player, talent, level, item);
                break;
            case "lightning_strike":
                success = executeLightningStrike(player, talent, level, item);
                break;
            case "wind_blade":
                success = executeWindBlade(player, talent, level, item);
                break;
            case "earth_collapse":
                success = executeEarthCollapse(player, talent, level, item);
                break;
            case "tornado_lv2":
                success = executeTornadoLv2(player, talent, level, item);
                break;
            case "flourish_lv2":
                success = executeFlourishLv2(player, talent, level, item);
                break;
            case "chain_lightning_lv2":
                success = executeChainLightningLv2(player, talent, level, item);
                break;
            // ==================== 宿儺系技能 ====================
            case "dismantle":
                success = executeDismantle(player, talent, level, item);
                break;
            case "cleave":
                success = executeCleave(player, talent, level, item);
                break;
            case "piercing_slash":
                success = executePiercingSlash(player, talent, level, item);
                break;
            case "flame_technique":
                success = executeFlameTechnique(player, talent, level, item);
                break;
            case "malevolent_shrine":
                success = executeMalevolentShrine(player, talent, level, item);
                break;
            // ==================== 赤血操術系技能 ====================
            case "piercing_blood":
                success = executePiercingBlood(player, talent, level, item);
                break;
            case "blood_wave":
                success = executeBloodWave(player, talent, level, item);
                break;
            case "blood_burst":
                success = executeBloodBurst(player, talent, level, item);
                break;
            case "blood_binding":
                success = executeBloodBinding(player, talent, level, item);
                break;
            case "blood_frenzy":
                success = executeBloodFrenzy(player, talent, level, item);
                break;
            // ==================== 烈焰系技能 ====================
            case "fire_spark":
                success = executeFireSpark(player, talent, level, item);
                break;
            case "destruction_orb":
                success = executeDestructionOrb(player, talent, level, item);
                break;
            case "flame_storm":
                success = executeFlameStorm(player, talent, level, item);
                break;
            case "meteor":
                success = executeMeteor(player, talent, level, item);
                break;
            case "flame_jet":
                success = executeFlameJet(player, talent, level, item);
                break;
            case "scorching_wave":
                success = executeScorchingWave(player, talent, level, item);
                break;
            case "inferno_ring":
                success = executeInfernoRing(player, talent, level, item);
                break;
            case "hell_domain":
                success = executeHellDomain(player, talent, level, item);
                break;
            // ==================== 刺客系技能 ====================
            case "stealth":
                success = executeStealth(player, talent, level, item);
                break;
            case "shadow_assault":
                success = executeShadowAssault(player, talent, level, item);
                break;
            case "phantom_massacre":
                success = executePhantomMassacre(player, talent, level, item);
                break;
            case "rapid_slash":
                success = executeRapidSlash(player, talent, level, item);
                break;
            case "cross_blade":
                success = executeCrossBlade(player, talent, level, item);
                break;
            case "phantom_flurry":
                success = executePhantomFlurry(player, talent, level, item);
                break;
            case "arc_slash":
                success = executeArcSlash(player, talent, level, item);
                break;
            case "crescent_shadow":
                success = executeCrescentShadow(player, talent, level, item);
                break;
            case "death_god":
                success = executeDeathGod(player, talent, level, item);
                break;
            // ==================== 科技系技能 ====================
            case "shock_gun":
                success = executeShockGun(player, talent, level, item);
                break;
            case "grenade":
                success = executeGrenade(player, talent, level, item);
                break;
            case "mini_turret":
                success = executeMiniTurret(player, talent, level, item);
                break;
            case "energy_shield":
                success = executeEnergyShield(player, talent, level, item);
                break;
            case "tracking_drone":
                success = executeTrackingDrone(player, talent, level, item);
                break;
            case "orbital_strike":
                success = executeOrbitalStrike(player, talent, level, item);
                break;
            case "proximity_mine":
                success = executeProximityMine(player, talent, level, item);
                break;
            case "slow_trap":
                success = executeSlowTrap(player, talent, level, item);
                break;
            case "cluster_bomb":
                success = executeClusterBomb(player, talent, level, item);
                break;
            case "annihilation_barrage":
                success = executeAnnihilationBarrage(player, talent, level, item);
                break;
            // ==================== 暗黑系技能 ====================
            case "shadow_bolt":
                success = executeShadowBolt(player, talent, level, item);
                break;
            case "soul_drain":
                success = executeSoulDrain(player, talent, level, item);
                break;
            case "curse_mark":
                success = executeCurseMark(player, talent, level, item);
                break;
            case "blood_sacrifice":
                success = executeBloodSacrifice(player, talent, level, item);
                break;
            case "dark_flame":
                success = executeDarkFlame(player, talent, level, item);
                break;
            case "weakening_curse":
                success = executeWeakeningCurse(player, talent, level, item);
                break;
            case "soul_burn":
                success = executeSoulBurn(player, talent, level, item);
                break;
            case "dark_shield":
                success = executeDarkShield(player, talent, level, item);
                break;
            case "death_mark":
                success = executeDeathMark(player, talent, level, item);
                break;
            case "void_eruption":
                success = executeVoidEruption(player, talent, level, item);
                break;
            case "reaper_domain":
                success = executeReaperDomain(player, talent, level, item);
                break;
            case "dark_apotheosis":
                success = executeDarkApotheosis(player, talent, level, item);
                break;
            // ==================== 野獸系技能 ====================
            case "call_of_wild":
                success = executeCallOfWild(player, talent, level, item);
                break;
            case "pack_leader":
                success = executePackLeader(player, talent, level, item);
                break;
            case "beast_frenzy":
                success = executeBeastFrenzy(player, talent, level, item);
                break;
            case "savage_summon":
                success = executeSavageSummon(player, talent, level, item);
                break;
            case "beast_army":
                success = executeBeastArmy(player, talent, level, item);
                break;
            case "beast_master":
                success = executeBeastMaster(player, talent, level, item);
                break;
            case "bee_swarm":
                success = executeBeeSwarm(player, talent, level, item);
                break;
            case "wolf_form":
                success = executeWolfForm(player, talent, level, item);
                break;
            case "bear_form":
                success = executeBearForm(player, talent, level, item);
                break;
            case "predator_dash":
                success = executePredatorDash(player, talent, level, item);
                break;
            case "fox_form":
                success = executeFoxForm(player, talent, level, item);
                break;
            case "apex_predator":
                success = executeApexPredator(player, talent, level, item);
                break;
            case "primal_wrath":
                success = executePrimalWrath(player, talent, level, item);
                break;
            // ── 風獵者之道 (Bow) ──
            case "critical_arrow":
                success = executeCriticalArrow(player, talent, level, item);
                break;
            case "arrow_storm":
                success = executeArrowStorm(player, talent, level, item);
                break;
            case "split_arrow":
                success = executeSplitArrow(player, talent, level, item);
                break;
            case "burst_mode":
                success = executeBurstMode(player, talent, level, item);
                break;
            case "tracking_mark":
                success = executeTrackingMark(player, talent, level, item);
                break;
            case "slow_arrow":
                success = executeSlowArrow(player, talent, level, item);
                break;
            case "net_trap":
                success = executeNetTrap(player, talent, level, item);
                break;
            case "wind_trap":
                success = executeWindTrap(player, talent, level, item);
                break;
            default:
                player.sendMessage("§c技能尚未接入執行器: §f" + talent.getName() + " §7(" + talent.getId() + ")");
                plugin.getLogger().warning("[TalentSkillManager] Unhandled talent skill: " + talent.getId());
                break;
        }

        // 4. 消耗資源與套用冷卻
        if (success) {
            manaManager.consumeMana(player, talent.getManaCost());
            // 暗黑系 HP 消耗
            if (hpCostPercent > 0) {
                double hpToDeduct = player.getMaxHealth() * hpCostPercent;
                player.setHealth(Math.max(2.0, player.getHealth() - hpToDeduct));
                player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.01);
                player.sendMessage("§4[暗黑] §c消耗了 " + (int)(hpToDeduct) + " 生命值");
            }
            if (talent.getCooldown() > 0) {
                cooldownManager.applyCooldown(player, cdKey, (long)(talent.getCooldown() * 1000));
            }
        }

        return success;
    }

    /**
     * 裂地擊 - 造成前方物理傷害
     */
    private boolean executeEarthShatter(Player player, Talent talent, int level, ItemStack item) {
        Location origin = player.getLocation();
        Vector dir = origin.getDirection().setY(0).normalize();
        
        player.sendMessage("§6[天賦] §f你使用了 §e" + talent.getName() + "§f！");
        
        // 粒子效果：地裂感
        for (int i = 1; i <= 5; i++) {
            Location pLoc = origin.clone().add(dir.clone().multiply(i));
            pLoc.getWorld().spawnParticle(Particle.BLOCK, pLoc, 20, 0.5, 0.1, 0.5, 0.1, Material.DIRT.createBlockData());
            pLoc.getWorld().spawnParticle(Particle.CRIT, pLoc, 10, 0.3, 0.3, 0.3, 0.2);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 0.8f);

        // 傷害邏輯
        Talent.TalentLevelData data = talent.getLevelData(level);
        double multiplier = data.effects.getOrDefault("damageMultiplier", 1.7);
        
        // 獲取武器基礎傷害
        double baseDamage = calculateBaseWeaponDamage(player, item);
        double totalDamage = baseDamage * multiplier;

        List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, origin.clone().add(dir.multiply(3)), 3.0);
        for (LivingEntity target : targets) {
            damageManager.dealSkillDamage(player, target, totalDamage);
            target.setVelocity(new Vector(0, 0.4, 0)); // 輕微擊飛
        }

        return true;
    }

    /**
     * 魔法漩渦 - 控制與魔法傷害
     */
    private boolean executeMagicVortex(Player player, Talent talent, int level, ItemStack item) {
        Location targetLoc = player.getTargetBlock(null, 8).getLocation();
        if (targetLoc.getBlock().getType() == Material.AIR) {
            targetLoc = player.getLocation().add(player.getLocation().getDirection().multiply(5));
        }

        player.sendMessage("§6[天賦] §f施放了 §b" + talent.getName() + "§f！");

        // 粒子效果：漩渦
        targetLoc.getWorld().spawnParticle(Particle.PORTAL, targetLoc.clone().add(0, 1, 0), 100, 1, 1, 1, 0.2);
        targetLoc.getWorld().spawnParticle(Particle.ENCHANT, targetLoc.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);

        Talent.TalentLevelData data = talent.getLevelData(level);
        double multiplier = data.effects.getOrDefault("damageMultiplier", 1.5);
        double duration = data.effects.getOrDefault("controlDuration", 0.5);
        
        double baseDamage = calculateBaseWeaponDamage(player, item);
        double totalDamage = baseDamage * multiplier;

        List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, targetLoc, 3.5);
        for (LivingEntity target : targets) {
            damageManager.dealSkillDamage(player, target, totalDamage);
            
            // 捲至半格高並定身
            target.setVelocity(new Vector(0, 0.2, 0));
            
            // 緩速效果
            if (data.effects.containsKey("slowLevel")) {
                int slowLv = data.effects.get("slowLevel").intValue();
                int slowDur = data.effects.get("slowDuration").intValue();
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDur * 20, slowLv - 1));
            }
        }

        return true;
    }

    /**
     * 狙殺 - 延遲射擊
     */
    private boolean executeSniperShot(Player player, Talent talent, int level, ItemStack item) {
        if (item.getType() != Material.PRISMARINE_SHARD) {
            // 如果不是要求的物品，雖然 trigger 匹配但不執行（根據需求：手持Prismarine Shard按左鍵）
            return false;
        }

        player.sendMessage("§7[狙殺] §f正在瞄準...");
        player.getWorld().playSound(player.getLocation(), "entity.breeze.charge", 1.0f, 1.2f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 1)); // 0.5s 緩速 II

        // 延遲 10 ticks (0.5s) 執行射擊
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            
            Location eye = player.getEyeLocation();
            Vector dir = eye.getDirection();
            
            player.getWorld().playSound(eye, "entity.breeze.death", 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.FIREWORK, eye.clone().add(dir), 1);

            Talent.TalentLevelData data = talent.getLevelData(level);
            double multiplier = data.effects.getOrDefault("damageMultiplier", 1.0);
            double baseDamage = calculateBaseWeaponDamage(player, item);
            
            // 射線檢測傷害
            org.bukkit.util.RayTraceResult result = eye.getWorld().rayTraceEntities(eye, dir, 50, 0.5, (e) -> e instanceof LivingEntity && !e.equals(player));
            if (result != null && result.getHitEntity() instanceof LivingEntity target) {
                damageManager.dealSkillDamage(player, target, baseDamage * multiplier * 2.0); // 假設狙殺有額外倍率或基礎較高
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 20);
                player.sendMessage("§c[狙殺] §f成功命中 §7" + target.getName());
            }

            // 彈道顯示
            for (double d = 0; d < 30; d += 0.5) {
                player.getWorld().spawnParticle(Particle.SMOKE, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0.01);
            }
        }, 10L);

        return true;
    }

    /**
     * 槍械武器：壓制彈幕
     */
    private boolean executeSuppressiveBarrage(Player player, Talent talent, int level, ItemStack item) {
        if (!isFirearmWeapon(item)) {
            player.sendMessage("§c需要手持槍械武器才能使用 §e" + talent.getName());
            return false;
        }

        Talent.TalentLevelData data = talent.getLevelData(level);
        int shots = data.effects.getOrDefault("shotCount", 6.0).intValue();
        double shotMultiplier = data.effects.getOrDefault("damageMultiplier", 0.7);
        double agilityScaling = data.effects.getOrDefault("agilityScaling", 0.25);
        double range = data.effects.getOrDefault("range", 24.0);
        double spread = data.effects.getOrDefault("spread", 0.10);

        PlayerStats stats = statsManager.getStats(player);
        double shotDamage = calculateBaseWeaponDamage(player, item) * shotMultiplier + (stats.getAgility() * agilityScaling);

        player.sendMessage("§6[壓制彈幕] §f連續掃射前方區域！");
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.35f);

        new BukkitRunnable() {
            int fired = 0;
            @Override
            public void run() {
                if (!player.isOnline() || fired >= shots) {
                    cancel();
                    return;
                }

                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection().normalize().add(new Vector(
                        (Math.random() - 0.5) * spread,
                        (Math.random() - 0.5) * spread * 0.35,
                        (Math.random() - 0.5) * spread)).normalize();

                org.bukkit.util.RayTraceResult result = eye.getWorld().rayTraceEntities(
                        eye, dir, range, 0.5,
                        e -> e instanceof LivingEntity le && !le.isDead() && !e.equals(player));

                Location end = eye.clone().add(dir.clone().multiply(range));
                LivingEntity target = null;
                if (result != null && result.getHitEntity() instanceof LivingEntity le) {
                    target = le;
                    end = result.getHitPosition().toLocation(eye.getWorld());
                }

                for (double d = 0; d < eye.distance(end); d += 0.5) {
                    Location p = eye.clone().add(dir.clone().multiply(d));
                    eye.getWorld().spawnParticle(Particle.CRIT, p, 1, 0, 0, 0, 0);
                    if (d % 1.5 < 0.1) {
                        eye.getWorld().spawnParticle(Particle.SMOKE, p, 1, 0.02, 0.02, 0.02, 0.001);
                    }
                }

                eye.getWorld().playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.45f, 1.8f);

                if (target != null) {
                    damageManager.dealSkillDamage(player, target, shotDamage);
                    target.setVelocity(target.getVelocity().add(dir.clone().multiply(0.15)));
                    target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 6, 0.15, 0.15, 0.15, 0.08);
                }
                fired++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        return true;
    }

    /**
     * 槍械武器：彈射穿甲彈
     */
    private boolean executeRicochetRound(Player player, Talent talent, int level, ItemStack item) {
        if (!isFirearmWeapon(item)) {
            player.sendMessage("§c需要手持槍械武器才能使用 §e" + talent.getName());
            return false;
        }

        Talent.TalentLevelData data = talent.getLevelData(level);
        double damageMultiplier = data.effects.getOrDefault("damageMultiplier", 1.8);
        double agilityScaling = data.effects.getOrDefault("agilityScaling", 0.35);
        int bounceCount = data.effects.getOrDefault("bounceCount", 3.0).intValue();
        double bounceRadius = data.effects.getOrDefault("bounceRadius", 6.0);
        double falloff = data.effects.getOrDefault("falloff", 0.75);
        double range = data.effects.getOrDefault("range", 30.0);

        LivingEntity firstTarget = rayTraceLivingTarget(player, range, 0.6);
        if (firstTarget == null) {
            player.sendMessage("§c[彈射穿甲彈] 視線內沒有可命中的敵人！");
            return false;
        }

        PlayerStats stats = statsManager.getStats(player);
        double damage = calculateBaseWeaponDamage(player, item) * damageMultiplier + stats.getAgility() * agilityScaling;

        Set<UUID> hitTargets = new HashSet<>();
        LivingEntity current = firstTarget;
        Location from = player.getEyeLocation();

        for (int hitIndex = 0; hitIndex < bounceCount && current != null; hitIndex++) {
            drawGoldenLine(from, current.getLocation().add(0, 1, 0));
            current.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, current.getLocation().add(0, 1, 0), 8, 0.2, 0.2, 0.2, 0.06);
            damageManager.dealSkillDamage(player, current, damage);
            hitTargets.add(current.getUniqueId());

            LivingEntity next = null;
            double bestDistance = Double.MAX_VALUE;
            for (Entity entity : current.getWorld().getNearbyEntities(current.getLocation(), bounceRadius, bounceRadius, bounceRadius)) {
                if (!(entity instanceof LivingEntity le) || le.isDead() || entity instanceof Player) continue;
                if (hitTargets.contains(entity.getUniqueId())) continue;
                double distance = entity.getLocation().distanceSquared(current.getLocation());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    next = le;
                }
            }

            from = current.getLocation().add(0, 1, 0);
            current = next;
            damage *= falloff;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 1.4f);
        player.sendMessage("§6[彈射穿甲彈] §f子彈在敵群之間反彈穿透！");
        return true;
    }

    /**
     * 近戰武器：旋風斬
     */
    private boolean executeCycloneSlash(Player player, Talent talent, int level, ItemStack item) {
        if (!isMeleeWeapon(item)) {
            player.sendMessage("§c需要手持近戰武器才能使用 §e" + talent.getName());
            return false;
        }

        Talent.TalentLevelData data = talent.getLevelData(level);
        double radius = data.effects.getOrDefault("radius", 4.0);
        double damageMultiplier = data.effects.getOrDefault("damageMultiplier", 1.8);
        double strengthScaling = data.effects.getOrDefault("strengthScaling", 0.45);

        PlayerStats stats = statsManager.getStats(player);
        double totalDamage = calculateBaseWeaponDamage(player, item) * damageMultiplier + stats.getStrength() * strengthScaling;

        Location center = player.getLocation();
        for (double angle = 0; angle < Math.PI * 2; angle += 0.18) {
            Location p = center.clone().add(Math.cos(angle) * radius, 1.0, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p, 1, 0, 0, 0, 0);
        }
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);

        List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, center, radius);
        for (LivingEntity target : targets) {
            damageManager.dealSkillDamage(player, target, totalDamage);
            Vector knockback = target.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.8).setY(0.25);
            target.setVelocity(knockback);
        }
        player.sendMessage("§6[旋風斬] §f捲起周圍敵人！");
        return true;
    }

    /**
     * 近戰武器：流星步
     */
    private boolean executeMeteorStep(Player player, Talent talent, int level, ItemStack item) {
        if (!isMeleeWeapon(item)) {
            player.sendMessage("§c需要手持近戰武器才能使用 §e" + talent.getName());
            return false;
        }

        Talent.TalentLevelData data = talent.getLevelData(level);
        double dashDistance = data.effects.getOrDefault("dashDistance", 7.0);
        double width = data.effects.getOrDefault("width", 2.2);
        double damageMultiplier = data.effects.getOrDefault("damageMultiplier", 2.0);
        double strengthScaling = data.effects.getOrDefault("strengthScaling", 0.6);

        PlayerStats stats = statsManager.getStats(player);
        double totalDamage = calculateBaseWeaponDamage(player, item) * damageMultiplier + stats.getStrength() * strengthScaling;
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        Location start = player.getLocation().clone();
        player.setVelocity(dir.clone().multiply(1.6).setY(0.15));
        player.playSound(start, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f);

        new BukkitRunnable() {
            int step = 0;
            final Set<UUID> hit = new HashSet<>();

            @Override
            public void run() {
                if (step > 10 || !player.isOnline()) {
                    cancel();
                    return;
                }

                Location point = start.clone().add(dir.clone().multiply((dashDistance / 10.0) * step));
                point.getWorld().spawnParticle(Particle.SWEEP_ATTACK, point.clone().add(0, 1, 0), 2, width * 0.15, 0.15, width * 0.15, 0.0);
                point.getWorld().spawnParticle(Particle.CRIT, point.clone().add(0, 1, 0), 6, width * 0.18, 0.2, width * 0.18, 0.08);

                for (Entity entity : point.getWorld().getNearbyEntities(point, width, 1.5, width)) {
                    if (!(entity instanceof LivingEntity target) || entity instanceof Player || target.isDead()) continue;
                    if (!hit.add(target.getUniqueId())) continue;
                    damageManager.dealSkillDamage(player, target, totalDamage);
                    target.setVelocity(dir.clone().multiply(0.7).setY(0.25));
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.sendMessage("§6[流星步] §f化作一道劍光衝刺斬擊！");
        return true;
    }

    /**
     * 法杖：秘法連星
     */
    private boolean executeArcaneBarrage(Player player, Talent talent, int level, ItemStack item) {
        if (!isMagicWeapon(item)) {
            player.sendMessage("§c需要手持法杖才能使用 §e" + talent.getName());
            return false;
        }

        Talent.TalentLevelData data = talent.getLevelData(level);
        int boltCount = data.effects.getOrDefault("boltCount", 5.0).intValue();
        double boltDamage = data.effects.getOrDefault("boltDamageMultiplier", 0.9) * calculateBaseWeaponDamage(player, item)
                + statsManager.getStats(player).getMagic() * data.effects.getOrDefault("magicScaling", 0.55);
        double seekRadius = data.effects.getOrDefault("seekRadius", 16.0);

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        player.sendMessage("§d[秘法連星] §f釋放追蹤秘法飛劍！");

        new BukkitRunnable() {
            int fired = 0;
            @Override
            public void run() {
                if (!player.isOnline() || fired >= boltCount) {
                    cancel();
                    return;
                }

                LivingEntity target = findNearestEnemy(player.getLocation(), seekRadius);
                if (target == null) {
                    target = rayTraceLivingTarget(player, seekRadius, 0.7);
                }
                if (target == null) {
                    fired++;
                    return;
                }

                Location from = player.getEyeLocation().clone();
                Location to = target.getLocation().add(0, 1, 0);
                drawArcaneLine(from, to);
                to.getWorld().spawnParticle(Particle.ENCHANT, to, 12, 0.25, 0.35, 0.25, 0.12);
                to.getWorld().playSound(to, Sound.ENTITY_EVOKER_CAST_SPELL, 0.35f, 1.6f);
                damageManager.dealSkillDamage(player, target, boltDamage);
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 30, 0, false, true));
                fired++;
            }
        }.runTaskTimer(plugin, 0L, 4L);

        return true;
    }

    /**
     * 法杖：重力牢籠
     */
    private boolean executeGravityPrison(Player player, Talent talent, int level, ItemStack item) {
        if (!isMagicWeapon(item)) {
            player.sendMessage("§c需要手持法杖才能使用 §e" + talent.getName());
            return false;
        }

        Talent.TalentLevelData data = talent.getLevelData(level);
        double radius = data.effects.getOrDefault("radius", 4.0);
        int durationTicks = data.effects.getOrDefault("durationTicks", 80.0).intValue();
        double tickDamage = data.effects.getOrDefault("tickDamageMultiplier", 0.55) * calculateBaseWeaponDamage(player, item)
                + statsManager.getStats(player).getMagic() * data.effects.getOrDefault("magicScaling", 0.45);
        double pullForce = data.effects.getOrDefault("pullForce", 0.22);

        Location center = player.getTargetBlock(null, 14).getLocation().add(0.5, 1.0, 0.5);
        if (center.getBlock().getType().isAir()) {
            center = player.getLocation().add(player.getLocation().getDirection().multiply(6)).add(0, 1, 0);
        }

        Location prisonCenter = center;
        prisonCenter.getWorld().playSound(prisonCenter, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.1f, 1.4f);
        player.sendMessage("§5[重力牢籠] §f在前方張開拘束領域！");

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= durationTicks) {
                    cancel();
                    return;
                }

                for (double angle = 0; angle < Math.PI * 2; angle += 0.25) {
                    Location ring = prisonCenter.clone().add(Math.cos(angle) * radius, 0.2 + ((t % 20) * 0.03), Math.sin(angle) * radius);
                    prisonCenter.getWorld().spawnParticle(Particle.PORTAL, ring, 1, 0, 0, 0, 0.02);
                    prisonCenter.getWorld().spawnParticle(Particle.ENCHANT, ring, 1, 0, 0, 0, 0.01);
                }

                if (t % 10 == 0) {
                    for (LivingEntity target : aoeUtil.getRadiusTargets(player, prisonCenter, radius)) {
                        Vector pull = prisonCenter.toVector().subtract(target.getLocation().toVector()).normalize().multiply(pullForce);
                        target.setVelocity(target.getVelocity().add(pull));
                        damageManager.dealSkillDamage(player, target, tickDamage);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 2, false, true));
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /**
     * 共通最終技：終極劍雨
     */
    private boolean executeUltimateSwordRain(Player player, Talent talent, int level, ItemStack item) {
        if (!isFirearmWeapon(item) && !isMeleeWeapon(item) && !isMagicWeapon(item)) {
            player.sendMessage("§c需要手持槍械 / 近戰武器 / 法杖 才能施放 §6終極劍雨");
            return false;
        }

        Location center = player.getLocation().clone();
        double radius = talent.getEffectDouble(level, "radius", 5.5);
        int swordCount = (int) Math.round(talent.getEffectDouble(level, "swordCount", 28.0));
        int chargeTicks = (int) Math.round(talent.getEffectDouble(level, "chargeTicks", 40.0));
        int fallTicks = (int) Math.round(talent.getEffectDouble(level, "fallTicks", 60.0));
        double perHitDamage = calculateBaseWeaponDamage(player, item) * talent.getEffectDouble(level, "damageMultiplier", 1.4)
                + statsManager.getStats(player).getStrength() * talent.getEffectDouble(level, "strengthScaling", 0.25)
                + statsManager.getStats(player).getMagic() * talent.getEffectDouble(level, "magicScaling", 0.25)
                + statsManager.getStats(player).getAgility() * talent.getEffectDouble(level, "agilityScaling", 0.25);

        List<SwordRainBlade> blades = new ArrayList<>();

        for (int i = 0; i < swordCount; i++) {
            double angle = Math.random() * Math.PI * 2;
            double dist = Math.sqrt(Math.random()) * radius;
            Location anchor = center.clone().add(Math.cos(angle) * dist, 6.0 + Math.random() * 1.6, Math.sin(angle) * dist);
            int revealTick = Math.min(chargeTicks - 1, (int) Math.floor((i / (double) Math.max(1, swordCount)) * chargeTicks));
            double driftRadius = 0.25 + Math.random() * 0.35;
            double swayPhase = Math.random() * Math.PI * 2;
            int waveDelay = (int) Math.floor((i / (double) Math.max(1, swordCount)) * Math.max(1, fallTicks - 12));
            double fallSpeed = 1.15 + Math.random() * 0.28;
            int activeFallTicks = 7 + (int) Math.round(Math.random() * 2.0);
            blades.add(new SwordRainBlade(anchor, revealTick, driftRadius, swayPhase, waveDelay, fallSpeed, activeFallTicks));
        }

        center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.1f);
        player.sendMessage("§6§l[終極劍雨] §e金色劍陣正在你頭頂凝聚...");

        new BukkitRunnable() {
            int tick = 0;
            final Map<UUID, Integer> hitCooldown = new HashMap<>();

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanup();
                    cancel();
                    return;
                }

                if (tick < chargeTicks) {
                    for (SwordRainBlade blade : blades) {
                        if (blade.stand == null && tick >= blade.revealTick) {
                            blade.spawn(center.getWorld());
                        }

                        if (blade.stand != null) {
                            Location current = blade.anchor.clone().add(
                                    Math.cos((tick * 0.18) + blade.swayPhase) * blade.driftRadius,
                                    Math.sin((tick * 0.12) + blade.swayPhase) * 0.08,
                                    Math.sin((tick * 0.18) + blade.swayPhase) * blade.driftRadius);
                            blade.stand.teleport(current);
                            current.getWorld().spawnParticle(Particle.GLOW, current.clone().add(0, 0.25, 0), 1, 0.03, 0.08, 0.03, 0.0);
                            if (tick == blade.revealTick) {
                                current.getWorld().spawnParticle(Particle.END_ROD, current.clone().add(0, 0.2, 0), 8, 0.12, 0.22, 0.12, 0.015);
                                current.getWorld().playSound(current, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.35f, 1.9f);
                            }
                        }
                    }

                    center.getWorld().spawnParticle(Particle.ENCHANT, center.clone().add(0, 1.2, 0), 24,
                            radius, 0.8, radius, 0.05);
                    center.getWorld().spawnParticle(Particle.GLOW, center.clone().add(0, 6.0, 0), 10,
                            radius * 0.75, 0.5, radius * 0.75, 0.02);
                    if (tick % 10 == 0) {
                        center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);
                    }
                } else if (tick == chargeTicks) {
                    center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.4f, 1.9f);
                    center.getWorld().playSound(center, Sound.ITEM_TRIDENT_THUNDER, 0.9f, 1.8f);
                    player.sendMessage("§6§l[終極劍雨] §c萬劍墜落！");
                } else if (tick <= chargeTicks + fallTicks) {
                    int fallTick = tick - chargeTicks;
                    for (SwordRainBlade blade : blades) {
                        if (blade.ended) continue;
                        if (blade.stand == null) {
                            blade.spawn(center.getWorld());
                        }
                        if (blade.stand == null) continue;

                        if (fallTick < blade.waveDelay) {
                            Location suspended = blade.getHoverLocation(fallTick);
                            blade.stand.teleport(suspended);
                            if (fallTick % 6 == 0) {
                                suspended.getWorld().spawnParticle(Particle.GLOW, suspended.clone().add(0, 0.2, 0), 1, 0.03, 0.08, 0.03, 0.0);
                            }
                            continue;
                        }

                        int bladeFallTick = fallTick - blade.waveDelay;
                        Location current = blade.getFallLocation(bladeFallTick);
                        blade.stand.teleport(current);
                        current.getWorld().spawnParticle(Particle.GLOW, current.clone().add(0, 0.15, 0), 2, 0.02, 0.18, 0.02, 0.0);
                        current.getWorld().spawnParticle(Particle.CRIT, current.clone().add(0, 0.1, 0), 1, 0.02, 0.08, 0.02, 0.01);

                        for (Entity entity : current.getWorld().getNearbyEntities(current, 0.9, 1.4, 0.9)) {
                            if (!(entity instanceof LivingEntity target) || entity instanceof Player || target.isDead()) continue;
                            int lastHit = hitCooldown.getOrDefault(target.getUniqueId(), -999);
                            if (tick - lastHit < 8) continue;
                            hitCooldown.put(target.getUniqueId(), tick);
                            damageManager.dealSkillDamage(player, target, perHitDamage);
                            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.2, 0.3, 0.2, 0.06);
                        }

                        if (bladeFallTick >= blade.activeFallTicks || current.getBlock().getType().isSolid() || current.getY() <= center.getY() - 2.0) {
                            current.getWorld().spawnParticle(Particle.END_ROD, current.clone().add(0, 0.2, 0), 6, 0.18, 0.08, 0.18, 0.01);
                            current.getWorld().spawnParticle(Particle.CRIT, current.clone().add(0, 0.2, 0), 6, 0.12, 0.05, 0.12, 0.02);
                            current.getWorld().playSound(current, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.35f, 1.4f);
                            blade.remove();
                        }
                    }
                } else {
                    cleanup();
                    cancel();
                    return;
                }

                tick++;
            }

            private void cleanup() {
                for (SwordRainBlade blade : blades) {
                    blade.remove();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    private static class SwordRainBlade {
        private final Location anchor;
        private final int revealTick;
        private final double driftRadius;
        private final double swayPhase;
        private final int waveDelay;
        private final double fallSpeed;
        private final int activeFallTicks;
        private ArmorStand stand;
        private boolean ended;

        private SwordRainBlade(Location anchor, int revealTick, double driftRadius, double swayPhase, int waveDelay, double fallSpeed, int activeFallTicks) {
            this.anchor = anchor;
            this.revealTick = revealTick;
            this.driftRadius = driftRadius;
            this.swayPhase = swayPhase;
            this.waveDelay = waveDelay;
            this.fallSpeed = fallSpeed;
            this.activeFallTicks = activeFallTicks;
        }

        private Location getHoverLocation(int tick) {
            return anchor.clone().add(
                    Math.cos((tick * 0.16) + swayPhase) * driftRadius,
                    Math.sin((tick * 0.10) + swayPhase) * 0.08,
                    Math.sin((tick * 0.16) + swayPhase) * driftRadius);
        }

        private Location getFallLocation(int bladeFallTick) {
            return anchor.clone().add(0, -(bladeFallTick * fallSpeed), 0);
        }

        private void spawn(org.bukkit.World world) {
            if (stand != null || ended) return;
            stand = world.spawn(anchor, ArmorStand.class);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setRightArmPose(new EulerAngle(Math.toRadians(90), 0.0, 0.0));
            stand.setHeadPose(new EulerAngle(0.0, 0.0, 0.0));
            if (stand.getEquipment() != null) {
                stand.getEquipment().setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
            }
        }

        private void remove() {
            ended = true;
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
    }

    private boolean isFirearmWeapon(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.IRON_HORSE_ARMOR || type == Material.GOLDEN_HORSE_ARMOR || type == Material.DIAMOND_HORSE_ARMOR;
    }

    private boolean isMeleeWeapon(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.contains("SWORD") || name.contains("AXE") || name.contains("HOE");
    }

    private boolean isMagicWeapon(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.STICK || type == Material.BLAZE_ROD || type == Material.BREEZE_ROD || type == Material.ENCHANTED_BOOK;
    }

    private LivingEntity rayTraceLivingTarget(Player player, double range, double hitbox) {
        org.bukkit.util.RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), range, hitbox,
                entity -> entity instanceof LivingEntity le && !le.isDead() && !entity.equals(player));
        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            return target;
        }
        return null;
    }

    private LivingEntity findNearestEnemy(Location center, double radius) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity le) || entity instanceof Player || le.isDead()) continue;
            double dist = entity.getLocation().distanceSquared(center);
            if (dist < bestDistance) {
                bestDistance = dist;
                best = le;
            }
        }
        return best;
    }

    private void drawGoldenLine(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double length = dir.length();
        if (length <= 0.01) return;
        dir.normalize();
        for (double d = 0; d <= length; d += 0.5) {
            Location p = from.clone().add(dir.clone().multiply(d));
            p.getWorld().spawnParticle(Particle.GLOW, p, 1, 0, 0, 0, 0);
            if (((int) (d * 10)) % 8 == 0) {
                p.getWorld().spawnParticle(Particle.CRIT, p, 1, 0.01, 0.01, 0.01, 0.01);
            }
        }
    }

    private void drawArcaneLine(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double length = dir.length();
        if (length <= 0.01) return;
        dir.normalize();
        for (double d = 0; d <= length; d += 0.4) {
            Location p = from.clone().add(dir.clone().multiply(d));
            p.getWorld().spawnParticle(Particle.ENCHANT, p, 1, 0, 0, 0, 0.02);
            if (((int) (d * 10)) % 7 == 0) {
                p.getWorld().spawnParticle(Particle.WITCH, p, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private double calculateBaseWeaponDamage(Player player, ItemStack item) {
        WeaponManager wm = plugin.getWeaponManager();
        String key = wm.getWeaponKey(item);
        double base = 5.0; // 預設基礎

        if (key != null) {
            WeaponManager.WeaponData data = wm.getWeaponData(key);
            if (data != null) {
                base = data.getDoubleExtra("base-damage", 5.0);
            }
        } else {
            // 普通物品，根據材質給予一點傷害
            switch (item.getType()) {
                case IRON_SWORD: base = 6; break;
                case DIAMOND_SWORD: base = 7; break;
                case NETHERITE_SWORD: base = 8; break;
            }
        }
        
        // 加上玩家力量加成
        PlayerStats stats = statsManager.getStats(player);
        base += stats.getStrength() * 0.2;
        
        return base;
    }

    /**
     * 星碎 - 投射3發自動追蹤的星光碎片
     */
    private boolean executeStarShatter(Player player, Talent talent, int level, ItemStack item) {
        if (item == null || item.getType() != Material.GOLD_NUGGET) {
            return false;
        }

        player.sendMessage("§e[星碎] §f發射星光碎片！");

        Location eyeLoc = player.getEyeLocation();
        PlayerStats stats = statsManager.getStats(player);

        Talent.TalentLevelData data = talent.getLevelData(level);
        double baseDamage = data.effects.getOrDefault("baseDamage", 33.0);
        double allStatsScaling = data.effects.getOrDefault("allStatsScaling", 0.05);

        // 計算全基礎屬性
        double totalStats = stats.getStrength() + stats.getMagic() + stats.getAgility() +
                           stats.getVitality() + stats.getDefense();
        double finalDamage = baseDamage + totalStats * allStatsScaling;

        // 獲取附近的敵人列表
        List<LivingEntity> nearbyTargets = aoeUtil.getRadiusTargets(player, eyeLoc, 20.0);

        // 發射3發星光碎片
        for (int i = 0; i < 3 && i < nearbyTargets.size(); i++) {
            LivingEntity target = nearbyTargets.get(i);

            // 延遲發射製造連續效果
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                launchHomingProjectile(player, target, finalDamage, Particle.FIREWORK, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH)
            , i * 3L);
        }

        player.getWorld().playSound(eyeLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
        return true;
    }

    /**
     * 落雷 - 對指定敵人發出落雷
     */
    private boolean executeLightningStrike(Player player, Talent talent, int level, ItemStack item) {
        if (item == null || item.getType() != Material.BONE) {
            return false;
        }

        // 獲取玩家視線目標
        LivingEntity target = getTargetEntity(player, 30.0);
        if (target == null) {
            player.sendMessage("§c未找到目標！");
            return false;
        }

        player.sendMessage("§b[落雷] §f召喚天雷！");

        Location targetLoc = target.getLocation();
        PlayerStats stats = statsManager.getStats(player);

        Talent.TalentLevelData data = talent.getLevelData(level);
        double baseDamage = data.effects.getOrDefault("baseDamage", 75.0);
        double allStatsScaling = data.effects.getOrDefault("allStatsScaling", 0.2);
        double weaponScaling = data.effects.getOrDefault("weaponDamageScaling", 0.16);
        int maxTargets = data.effects.getOrDefault("maxTargets", 3.0).intValue();

        double totalStats = stats.getStrength() + stats.getMagic() + stats.getAgility() +
                           stats.getVitality() + stats.getDefense();
        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double finalDamage = baseDamage + totalStats * allStatsScaling + weaponDamage * weaponScaling;

        // 閃電效果
        targetLoc.getWorld().strikeLightningEffect(targetLoc);
        targetLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, targetLoc.clone().add(0, 3, 0), 50, 0.3, 2, 0.3, 0.1);
        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.2f);

        // 對周圍至多3個目標造成傷害
        List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, targetLoc, 4.0);
        int hitCount = 0;
        for (LivingEntity t : targets) {
            if (hitCount >= maxTargets) break;
            damageManager.dealSkillDamage(player, t, finalDamage);
            t.getWorld().spawnParticle(Particle.ENCHANTED_HIT, t.getLocation().add(0, 1, 0), 15);
            hitCount++;
        }

        return true;
    }

    /**
     * 直線風壓 - 放出強風割傷敵人並加速
     */
    private boolean executeWindBlade(Player player, Talent talent, int level, ItemStack item) {
        if (item == null || item.getType() != Material.STICK) {
            return false;
        }

        player.sendMessage("§a[直線風壓] §f疾風斬！");

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        PlayerStats stats = statsManager.getStats(player);

        Talent.TalentLevelData data = talent.getLevelData(level);
        double baseDamage = data.effects.getOrDefault("baseDamage", 55.0);
        double allStatsScaling = data.effects.getOrDefault("allStatsScaling", 0.14);
        double weaponScaling = data.effects.getOrDefault("weaponDamageScaling", 0.1);

        double totalStats = stats.getStrength() + stats.getMagic() + stats.getAgility() +
                           stats.getVitality() + stats.getDefense();
        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double finalDamage = baseDamage + totalStats * allStatsScaling + weaponDamage * weaponScaling;

        // 給予玩家速度效果
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0));

        // 風刃軌跡
        for (double d = 0; d < 10; d += 0.5) {
            Location particleLoc = eyeLoc.clone().add(direction.clone().multiply(d));
            particleLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 1, 0.2, 0.2, 0.2, 0);
            particleLoc.getWorld().spawnParticle(Particle.CLOUD, particleLoc, 3, 0.3, 0.3, 0.3, 0.02);

            // 檢測沿路敵人
            List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, particleLoc, 1.5);
            for (LivingEntity target : targets) {
                damageManager.dealSkillDamage(player, target, finalDamage);
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 5);
            }
        }

        player.getWorld().playSound(eyeLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.3f);
        return true;
    }

    /**
     * 土崩 - 重組土壤結構，噴飛目標
     */
    private boolean executeEarthCollapse(Player player, Talent talent, int level, ItemStack item) {
        if (item == null || item.getType() != Material.STICK) {
            return false;
        }

        player.sendMessage("§6[土崩] §f大地之力！");

        Location playerLoc = player.getLocation();
        Vector direction = playerLoc.getDirection().setY(0).normalize();
        Location targetLoc = playerLoc.clone().add(direction.multiply(3)); // 前方3格

        PlayerStats stats = statsManager.getStats(player);
        Talent.TalentLevelData data = talent.getLevelData(level);

        double baseDamage = data.effects.getOrDefault("baseDamage", 70.0);
        double allStatsScaling = data.effects.getOrDefault("allStatsScaling", 0.27);
        double weaponScaling = data.effects.getOrDefault("weaponDamageScaling", 0.22);

        double totalStats = stats.getStrength() + stats.getMagic() + stats.getAgility() +
                           stats.getVitality() + stats.getDefense();
        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double finalDamage = baseDamage + totalStats * allStatsScaling + weaponDamage * weaponScaling;

        // 地面裂開效果 - 顯示土塊破碎粒子
        for (int i = 1; i <= 3; i++) {
            Location effectLoc = playerLoc.clone().add(direction.clone().multiply(i));
            effectLoc.getWorld().spawnParticle(Particle.BLOCK, effectLoc, 30, 0.5, 0.1, 0.5, 0.1,
                Material.DIRT.createBlockData());
            effectLoc.getWorld().spawnParticle(Particle.EXPLOSION, effectLoc, 2, 0.3, 0.1, 0.3, 0);
        }

        // 音效
        playerLoc.getWorld().playSound(playerLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
        playerLoc.getWorld().playSound(playerLoc, Sound.BLOCK_GRAVEL_BREAK, 1.0f, 0.5f);

        // 延遲造成傷害和擊飛效果
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // 對範圍內的敵人造成傷害並擊飛
            List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, targetLoc, 2.5);

            for (LivingEntity target : targets) {
                // 造成傷害
                damageManager.dealSkillDamage(player, target, finalDamage);

                // 計算擊飛方向（向上和略微向前）
                Vector knockback = new Vector(
                    direction.getX() * 0.3,
                    1.2, // 向上
                    direction.getZ() * 0.3
                );
                target.setVelocity(knockback);

                // 砂礫噴射效果
                target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 0.5, 0),
                    50, 0.5, 0.5, 0.5, 0.1, Material.GRAVEL.createBlockData());
                target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 3);
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10);
            }

            // 爆炸音效
            targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        }, 5L); // 0.25秒延遲

        return true;
    }

    /**
     * 龍捲風LV2 - 增強版龍捲風，範圍更大
     */
    private boolean executeTornadoLv2(Player player, Talent talent, int level, ItemStack item) {
        if (item == null || item.getType() != Material.PRISMARINE_CRYSTALS) {
            return false;
        }

        player.sendMessage("§6[龍捲風LV2] §f召喚強化龍捲風！");

        Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().multiply(1));
        PlayerStats stats = statsManager.getStats(player);

        Talent.TalentLevelData data = talent.getLevelData(level);
        double allStatsScaling = data.effects.getOrDefault("allStatsScaling", 1.0);
        double weaponScaling = data.effects.getOrDefault("weaponDamageScaling", 0.72);
        int hitCount = data.effects.getOrDefault("hitCount", 4.0).intValue();

        double totalStats = stats.getStrength() + stats.getMagic() + stats.getAgility() +
                           stats.getVitality() + stats.getDefense();
        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double damagePerHit = (totalStats * allStatsScaling + weaponDamage * weaponScaling) / hitCount;

        // 龍捲風持續效果 (增加範圍)
        spawnTornado(player, spawnLoc, damagePerHit, hitCount, 4.5, true);

        return true;
    }

    /**
     * 煥發LV2 - 範圍治療與持續傷害
     */
    private boolean executeFlourishLv2(Player player, Talent talent, int level, ItemStack item) {
        if (item == null || item.getType() != Material.PRISMARINE_CRYSTALS) {
            return false;
        }

        Location targetLoc = player.getTargetBlock(null, 15).getLocation();
        if (targetLoc.getBlock().getType() == Material.AIR) {
            targetLoc = player.getLocation().add(player.getLocation().getDirection().multiply(8));
        }

        player.sendMessage("§2[煥發] §f生機綻放！");

        PlayerStats stats = statsManager.getStats(player);
        Talent.TalentLevelData data = talent.getLevelData(level);

        int duration = data.effects.getOrDefault("duration", 10.0).intValue();
        double radius = data.effects.getOrDefault("radius", 8.0);
        double healMultiplier = 0.5; // Spirit * 0.5 (使用Magic屬性代替)
        double damageScaling = data.effects.getOrDefault("damagePerSecond", 0.43);

        double healPerTick = stats.getMagic() * healMultiplier / 20.0; // 每tick的治療量
        double totalStats = stats.getStrength() + stats.getMagic() + stats.getAgility() +
                           stats.getVitality() + stats.getDefense();
        double damagePerTick = totalStats * damageScaling / 20.0;

        targetLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, targetLoc.clone().add(0, 1, 0), 100, radius, 1, radius, 0);
        targetLoc.getWorld().playSound(targetLoc, Sound.BLOCK_GRASS_PLACE, 1.0f, 0.8f);

        // 持續效果區域
        final Location finalLoc = targetLoc;
        final int[] ticksRemaining = {duration * 20}; // 轉換為ticks

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (ticksRemaining[0] <= 0) {
                task.cancel();
                return;
            }

            // 每秒顯示粒子效果
            if (ticksRemaining[0] % 20 == 0) {
                finalLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, finalLoc.clone().add(0, 1, 0), 30, radius, 1, radius, 0);
            }

            // 治療隊友
            for (Player p : finalLoc.getWorld().getPlayers()) {
                if (p.getLocation().distance(finalLoc) <= radius) {
                    double currentHealth = p.getHealth();
                    org.bukkit.attribute.AttributeInstance maxHealthAttr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    if (maxHealthAttr != null) {
                        double maxHealth = maxHealthAttr.getValue();
                        if (currentHealth < maxHealth) {
                            p.setHealth(Math.min(maxHealth, currentHealth + healPerTick));
                        }
                    }
                }
            }

            // 傷害敵人
            List<LivingEntity> enemies = aoeUtil.getRadiusTargets(player, finalLoc, radius);
            for (LivingEntity enemy : enemies) {
                damageManager.dealSkillDamage(player, enemy, damagePerTick);
                if (ticksRemaining[0] % 20 == 0) {
                    enemy.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, enemy.getLocation().add(0, 1, 0), 3);
                }
            }

            ticksRemaining[0]--;
        }, 0L, 1L);

        return true;
    }

    /**
     * 連鎖閃電LV2 - 連鎖多個目標並暈眩
     */
    private boolean executeChainLightningLv2(Player player, Talent talent, int level, ItemStack item) {
        if (item == null || item.getType() != Material.GOLD_NUGGET) {
            return false;
        }

        // 獲取初始目標
        LivingEntity initialTarget = getTargetEntity(player, 25.0);
        if (initialTarget == null) {
            player.sendMessage("§c未找到目標！");
            return false;
        }

        player.sendMessage("§b[連鎖閃電] §f電流迸發！");

        PlayerStats stats = statsManager.getStats(player);
        Talent.TalentLevelData data = talent.getLevelData(level);

        double allStatsScaling = data.effects.getOrDefault("allStatsScaling", 0.35);
        double weaponScaling = data.effects.getOrDefault("weaponDamageScaling", 0.28);
        int maxChains = data.effects.getOrDefault("maxChains", 6.0).intValue();
        double stunDuration = data.effects.getOrDefault("stunDuration", 0.5);

        double totalStats = stats.getStrength() + stats.getMagic() + stats.getAgility() +
                           stats.getVitality() + stats.getDefense();
        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double finalDamage = totalStats * allStatsScaling + weaponDamage * weaponScaling;

        // 連鎖閃電邏輯
        chainLightning(player, initialTarget, finalDamage, maxChains, stunDuration);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.5f);
        return true;
    }

    /**
     * 繪製領域邊界粒子環
     */
    private void spawnDomainBoundaryRing(Location center, double radius, int tick) {
        int points = 36; // 每環36個點
        double angleOffset = tick * 0.05; // 旋轉效果
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI / points) * i + angleOffset;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particleLoc = center.clone().add(x, 0.2, z);

            // 交替使用不同粒子增加層次感
            if (i % 3 == 0) {
                particleLoc.getWorld().spawnParticle(Particle.SOUL, particleLoc, 1, 0, 0, 0, 0);
            } else {
                particleLoc.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 1, 0.05, 0.05, 0.05, 0);
            }
        }

        // 上層較小的旋轉環
        for (int i = 0; i < 18; i++) {
            double angle = (2 * Math.PI / 18) * i - angleOffset * 1.5;
            double x = Math.cos(angle) * radius * 0.95;
            double z = Math.sin(angle) * radius * 0.95;
            Location particleLoc = center.clone().add(x, 2.5, z);
            particleLoc.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 領域開場爆發粒子效果
     */
    private void spawnDomainBurstParticles(Location center, double radius) {
        // 地面擴散波
        for (double r = 0; r <= radius; r += 0.5) {
            int points = (int)(r * 12);
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI / points) * i;
                double x = Math.cos(angle) * r;
                double z = Math.sin(angle) * r;
                Location loc = center.clone().add(x, 0.1, z);
                loc.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0, 0.1, 0, 0.02);
            }
        }
        // 中心柱
        for (double y = 0; y < 5; y += 0.3) {
            center.getWorld().spawnParticle(Particle.SOUL, center.clone().add(0, y, 0), 3, 0.3, 0, 0.3, 0.01);
            center.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, center.clone().add(0, y, 0), 2, 0.5, 0, 0.5, 0.01);
        }
    }

    // ==================== 輔助方法 ====================

    /**
     * 發射追蹤彈射物
     */
    private void launchHomingProjectile(Player caster, LivingEntity target, double damage, Particle particle, Sound sound) {
        // 使用數組來保存當前位置，確保在 lambda 中可以修改
        final Location[] currentLoc = {caster.getEyeLocation().clone()};
        final int[] ticks = {0};
        final int maxTicks = 100; // 5秒最大追蹤時間（100 ticks = 5秒）
        final double speed = 0.8; // 每tick移動0.8格，提高速度

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (ticks[0] >= maxTicks || !target.isValid() || target.isDead()) {
                task.cancel();
                return;
            }

            // 計算朝向目標的方向
            Vector direction = target.getEyeLocation().toVector()
                .subtract(currentLoc[0].toVector())
                .normalize();

            // 移動彈射物位置（提高速度）
            currentLoc[0].add(direction.multiply(speed));

            // 粒子軌跡
            currentLoc[0].getWorld().spawnParticle(particle, currentLoc[0], 2, 0.1, 0.1, 0.1, 0);

            // 檢測命中 - 檢查與目標中心的距離
            double distanceToTarget = currentLoc[0].distance(target.getLocation().add(0, 1, 0));
            if (distanceToTarget < 1.5) {
                // 命中目標
                damageManager.dealSkillDamage(caster, target, damage);
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 20);
                target.getWorld().spawnParticle(Particle.ENCHANTED_HIT, target.getLocation().add(0, 1, 0), 10);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                task.cancel();
                return;
            }

            ticks[0]++;
        }, 0L, 1L);

        // 發射音效
        caster.getWorld().playSound(caster.getLocation(), sound, 0.7f, 1.2f);
    }

    /**
     * 獲取玩家視線目標實體
     */
    private LivingEntity getTargetEntity(Player player, double maxDistance) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        org.bukkit.util.RayTraceResult result = eyeLoc.getWorld().rayTraceEntities(
            eyeLoc, direction, maxDistance, 0.5,
            (e) -> e instanceof LivingEntity && !e.equals(player)
        );

        if (result != null && result.getHitEntity() instanceof LivingEntity) {
            return (LivingEntity) result.getHitEntity();
        }
        return null;
    }

    /**
     * 生成龍捲風效果
     */
    private void spawnTornado(Player caster, Location startLoc, double damagePerHit, int hitCount, double radius, boolean enhanced) {
        Vector direction = caster.getLocation().getDirection().setY(0).normalize();
        final Location[] currentLoc = {startLoc.clone()};
        final int[] hits = {0};
        final int tickDuration = 60; // 3秒持續時間
        final int[] ticks = {0};

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (ticks[0] >= tickDuration) {
                // 最後拋飛效果
                List<LivingEntity> finalTargets = aoeUtil.getRadiusTargets(caster, currentLoc[0], radius);
                for (LivingEntity target : finalTargets) {
                    target.setVelocity(new Vector(0, 1.2, 0).add(direction.clone().multiply(0.5)));
                    target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 1);
                }
                task.cancel();
                return;
            }

            // 龍捲風視覺效果
            for (double y = 0; y < 4; y += 0.3) {
                double angle = (ticks[0] + y) * 0.5;
                double x = Math.cos(angle) * (radius - y * 0.2);
                double z = Math.sin(angle) * (radius - y * 0.2);
                Location particleLoc = currentLoc[0].clone().add(x, y, z);
                particleLoc.getWorld().spawnParticle(Particle.CLOUD, particleLoc, 1, 0, 0, 0, 0);
                if (enhanced) {
                    particleLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 1, 0, 0, 0, 0);
                }
            }

            // 每15 ticks造成一次傷害並拉扯
            if (ticks[0] % 15 == 0 && hits[0] < hitCount) {
                List<LivingEntity> targets = aoeUtil.getRadiusTargets(caster, currentLoc[0], radius);
                for (LivingEntity target : targets) {
                    damageManager.dealSkillDamage(caster, target, damagePerHit);

                    // 拉向龍捲風中心
                    Vector pull = currentLoc[0].toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.3);
                    pull.setY(0.2);
                    target.setVelocity(pull);

                    target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10);
                }
                hits[0]++;
                currentLoc[0].getWorld().playSound(currentLoc[0], Sound.ENTITY_PHANTOM_FLAP, 0.8f, 0.7f);
            }

            // 龍捲風前進
            currentLoc[0].add(direction.clone().multiply(0.15));
            ticks[0]++;
        }, 0L, 1L);

        startLoc.getWorld().playSound(startLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.5f);
    }

    /**
     * 連鎖閃電效果
     */
    private void chainLightning(Player caster, LivingEntity initialTarget, double damage, int maxChains, double stunDuration) {
        List<LivingEntity> alreadyHit = new java.util.ArrayList<>();
        LivingEntity currentTarget = initialTarget;

        for (int chain = 0; chain < maxChains && currentTarget != null; chain++) {
            final LivingEntity target = currentTarget;
            final int chainIndex = chain;

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // 造成傷害
                damageManager.dealSkillDamage(caster, target, damage);
                alreadyHit.add(target);

                // 暈眩效果 (緩速+致盲模擬)
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(stunDuration * 20), 10));
                target.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 0));

                // 視覺效果
                target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                target.getWorld().spawnParticle(Particle.ENCHANTED_HIT, target.getLocation().add(0, 1, 0), 15);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);

                // 尋找下一個目標
                if (chainIndex < maxChains - 1) {
                    List<LivingEntity> nearbyTargets = aoeUtil.getRadiusTargets(caster, target.getLocation(), 6.0);
                    for (LivingEntity next : nearbyTargets) {
                        if (!alreadyHit.contains(next)) {
                            // 連接線效果
                            drawLightningLine(target.getLocation().add(0, 1, 0), next.getLocation().add(0, 1, 0));
                            chainLightning(caster, next, damage, maxChains - chainIndex - 1, stunDuration);
                            return;
                        }
                    }
                }
            }, chain * 5L);

            // 尋找下一個最近的未命中目標
            LivingEntity nextTarget = null;
            double closestDist = 6.0;
            List<LivingEntity> nearby = aoeUtil.getRadiusTargets(caster, currentTarget.getLocation(), 6.0);

            for (LivingEntity entity : nearby) {
                if (!alreadyHit.contains(entity)) {
                    double dist = entity.getLocation().distance(currentTarget.getLocation());
                    if (dist < closestDist) {
                        closestDist = dist;
                        nextTarget = entity;
                    }
                }
            }

            if (nextTarget != null) {
                drawLightningLine(currentTarget.getLocation().add(0, 1, 0), nextTarget.getLocation().add(0, 1, 0));
            }

            currentTarget = nextTarget;
        }
    }

    /**
     * 繪製閃電連接線
     */
    private void drawLightningLine(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();

        for (double d = 0; d < distance; d += 0.3) {
            Location particleLoc = from.clone().add(direction.clone().multiply(d));
            particleLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, particleLoc, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    // ==================== 宿儺系技能實作 ====================

    /**
     * 解體 (Dismantle) - 高速斬擊投射物
     * 發射快速不可見的斬擊投射物，命中第一個敵人造成傷害
     */
    private boolean executeDismantle(Player player, Talent talent, int level, ItemStack item) {

        player.sendMessage("§4[解體] §c斬！");

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        PlayerStats stats = statsManager.getStats(player);

        Talent.TalentLevelData data = talent.getLevelData(level);
        double baseDamage = data.effects.getOrDefault("baseDamage", 50.0);
        double strengthScaling = data.effects.getOrDefault("strengthScaling", 0.3);
        double agilityScaling = data.effects.getOrDefault("agilityScaling", 0.2);
        double range = data.effects.getOrDefault("range", 10.0);

        double finalDamage = baseDamage + stats.getStrength() * strengthScaling + stats.getAgility() * agilityScaling;

        // 音效：揮砍聲
        player.getWorld().playSound(eyeLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);

        // 高速斬擊投射物（幾乎瞬間）
        final Location[] currentLoc = {eyeLoc.clone()};
        final int[] ticks = {0};
        final double speed = 2.5; // 非常快的速度
        final boolean[] hasHit = {false};

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (hasHit[0] || ticks[0] > (int)(range / speed) + 5) {
                task.cancel();
                return;
            }

            // 每tick移動並檢測
            for (int i = 0; i < 3; i++) { // 每tick檢測多次，確保不會穿過敵人
                currentLoc[0].add(direction.clone().multiply(speed / 3));

                // 斬擊粒子效果
                currentLoc[0].getWorld().spawnParticle(Particle.SWEEP_ATTACK, currentLoc[0], 1, 0, 0, 0, 0);
                currentLoc[0].getWorld().spawnParticle(Particle.CRIT, currentLoc[0], 3, 0.1, 0.1, 0.1, 0.05);

                // 檢測命中
                List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, currentLoc[0], 1.0);
                if (!targets.isEmpty()) {
                    LivingEntity target = targets.get(0);
                    damageManager.dealSkillDamage(player, target, finalDamage);

                    // 命中特效
                    target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 3);
                    target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
                    target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 5);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);

                    hasHit[0] = true;
                    task.cancel();
                    return;
                }

                // 距離檢查
                if (currentLoc[0].distance(eyeLoc) > range) {
                    task.cancel();
                    return;
                }
            }

            ticks[0]++;
        }, 0L, 1L);

        return true;
    }

    /**
     * 捌 (Cleave) - 基於目標最大生命值的斬擊
     * 對目標造成基於其最大生命值百分比的傷害
     */
    private boolean executeCleave(Player player, Talent talent, int level, ItemStack item) {

        // 獲取視線目標
        Talent.TalentLevelData data = talent.getLevelData(level);
        double range = data.effects.getOrDefault("range", 5.0);

        LivingEntity target = getTargetEntity(player, range);
        if (target == null) {
            player.sendMessage("§c未找到目標！");
            return false;
        }

        player.sendMessage("§4[捌] §c裂開！");

        PlayerStats stats = statsManager.getStats(player);
        double baseDamage = data.effects.getOrDefault("baseDamage", 30.0);
        double maxHealthScaling = data.effects.getOrDefault("maxHealthScaling", 0.15);

        // 獲取目標最大生命值
        org.bukkit.attribute.AttributeInstance maxHealthAttr = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double targetMaxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;

        double finalDamage = baseDamage + targetMaxHealth * maxHealthScaling;

        // 瞬間傳送到目標面前的視覺效果
        Location targetLoc = target.getLocation();

        // 斬擊音效
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.6f);

        // 造成傷害
        damageManager.dealSkillDamage(player, target, finalDamage);

        // 視覺效果 - 巨大的斬擊
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, targetLoc.clone().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0);
        target.getWorld().spawnParticle(Particle.CRIT, targetLoc.clone().add(0, 1.5, 0), 30, 0.5, 0.5, 0.5, 0.2);
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, targetLoc.clone().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);

        // 創建X形斬痕效果
        createCrossSlashEffect(targetLoc.clone().add(0, 1, 0));

        return true;
    }

    /**
     * 創建X形斬痕效果
     */
    private void createCrossSlashEffect(Location center) {
        // 斜線1
        for (double i = -1; i <= 1; i += 0.2) {
            Location loc = center.clone().add(i, i * 0.5, 0);
            loc.getWorld().spawnParticle(Particle.CRIT, loc, 1, 0, 0, 0, 0);
        }
        // 斜線2
        for (double i = -1; i <= 1; i += 0.2) {
            Location loc = center.clone().add(i, -i * 0.5, 0);
            loc.getWorld().spawnParticle(Particle.CRIT, loc, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 貫穿斬 (Piercing Slash) - 穿透斬擊
     * 發射穿透型斬擊，貫穿並傷害路徑上的所有敵人，留下粒子軌跡
     */
    private boolean executePiercingSlash(Player player, Talent talent, int level, ItemStack item) {

        player.sendMessage("§4[貫穿斬] §c貫穿！");

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        PlayerStats stats = statsManager.getStats(player);

        Talent.TalentLevelData data = talent.getLevelData(level);
        double baseDamage = data.effects.getOrDefault("baseDamage", 80.0);
        double strengthScaling = data.effects.getOrDefault("strengthScaling", 0.4);
        double range = data.effects.getOrDefault("range", 12.0);
        int maxPierce = data.effects.getOrDefault("pierceCount", 3.0).intValue();

        double finalDamage = baseDamage + stats.getStrength() * strengthScaling;

        // 音效
        player.getWorld().playSound(eyeLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        player.getWorld().playSound(eyeLoc, Sound.ENTITY_PHANTOM_SWOOP, 1.0f, 1.5f);

        // 穿透斬擊
        final Location[] currentLoc = {eyeLoc.clone()};
        final int[] ticks = {0};
        final double speed = 1.5;
        final int[] pierceCount = {0};
        final java.util.Set<java.util.UUID> hitEntities = new java.util.HashSet<>();

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (pierceCount[0] >= maxPierce || currentLoc[0].distance(eyeLoc) > range) {
                task.cancel();
                return;
            }

            // 移動
            currentLoc[0].add(direction.clone().multiply(speed));

            // 粒子軌跡 - 醒目的斬擊軌跡
            currentLoc[0].getWorld().spawnParticle(Particle.SWEEP_ATTACK, currentLoc[0], 2, 0.2, 0.2, 0.2, 0);
            currentLoc[0].getWorld().spawnParticle(Particle.CRIT, currentLoc[0], 5, 0.3, 0.3, 0.3, 0.05);
            currentLoc[0].getWorld().spawnParticle(Particle.END_ROD, currentLoc[0], 2, 0.1, 0.1, 0.1, 0.02);

            // 檢測命中（穿透）
            List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, currentLoc[0], 1.2);
            for (LivingEntity target : targets) {
                if (!hitEntities.contains(target.getUniqueId())) {
                    hitEntities.add(target.getUniqueId());

                    // 造成傷害
                    damageManager.dealSkillDamage(player, target, finalDamage);
                    pierceCount[0]++;

                    // 命中特效
                    target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 3);
                    target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.15);
                    target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 8);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);

                    if (pierceCount[0] >= maxPierce) {
                        task.cancel();
                        return;
                    }
                }
            }

            ticks[0]++;
        }, 0L, 1L);

        return true;
    }

    /**
     * 火之術式 (Flame Technique) - 咒力火焰爆炸
     * 在目標位置引發火焰爆炸，造成範圍傷害並點燃敵人
     */
    private boolean executeFlameTechnique(Player player, Talent talent, int level, ItemStack item) {

        // 獲取目標位置
        Location targetLoc = player.getTargetBlock(null, 15).getLocation();
        if (targetLoc.getBlock().getType() == Material.AIR) {
            targetLoc = player.getLocation().add(player.getLocation().getDirection().multiply(10));
        }
        targetLoc.add(0, 1, 0); // 稍微往上

        player.sendMessage("§4[火之術式] §c■開！");

        PlayerStats stats = statsManager.getStats(player);
        Talent.TalentLevelData data = talent.getLevelData(level);

        double baseDamage = data.effects.getOrDefault("baseDamage", 100.0);
        double magicScaling = data.effects.getOrDefault("magicScaling", 0.5);
        double radius = data.effects.getOrDefault("radius", 4.0);
        int burnDuration = data.effects.getOrDefault("burnDuration", 3.0).intValue();

        double finalDamage = baseDamage + stats.getMagic() * magicScaling;

        // 蓄力效果
        final Location explosionLoc = targetLoc.clone();

        // 標記目標位置
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);

        // 蓄力粒子
        for (int i = 0; i < 3; i++) {
            final int delay = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                explosionLoc.getWorld().spawnParticle(Particle.FLAME, explosionLoc, 30, 0.3, 0.3, 0.3, 0.05);
                explosionLoc.getWorld().spawnParticle(Particle.SMOKE, explosionLoc, 10, 0.2, 0.2, 0.2, 0.02);
            }, delay * 3L);
        }

        // 延遲爆炸
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // 爆炸效果
            explosionLoc.getWorld().spawnParticle(Particle.FLAME, explosionLoc, 100, radius * 0.5, radius * 0.5, radius * 0.5, 0.1);
            explosionLoc.getWorld().spawnParticle(Particle.LAVA, explosionLoc, 30, radius * 0.3, 0.5, radius * 0.3, 0);
            explosionLoc.getWorld().spawnParticle(Particle.EXPLOSION, explosionLoc, 5, 1, 1, 1, 0);
            explosionLoc.getWorld().spawnParticle(Particle.SMOKE, explosionLoc, 50, radius * 0.4, 1, radius * 0.4, 0.05);

            // 音效
            explosionLoc.getWorld().playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
            explosionLoc.getWorld().playSound(explosionLoc, Sound.ENTITY_BLAZE_HURT, 1.0f, 0.5f);

            // 對範圍內敵人造成傷害
            List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, explosionLoc, radius);
            for (LivingEntity target : targets) {
                damageManager.dealSkillDamage(player, target, finalDamage);

                // 點燃效果
                target.setFireTicks(burnDuration * 20);

                // 額外視覺效果
                target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
            }
        }, 10L);

        return true;
    }

    /**
     * 領域展開 - 伏魔御廚子 (Malevolent Shrine)
     * 展開必中領域，領域內所有目標持續受到無差別斬擊攻擊
     */
    private boolean executeMalevolentShrine(Player player, Talent talent, int level, ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) {
            return false;
        }

        player.sendMessage("§4[領域展開] §c伏魔御廚子！");

        Talent.TalentLevelData data = talent.getLevelData(level);
        double baseDamage = data.effects.getOrDefault("baseDamage", 25.0);
        double strengthScaling = data.effects.getOrDefault("strengthScaling", 0.2);
        double agilityScaling = data.effects.getOrDefault("agilityScaling", 0.1);
        double radius = data.effects.getOrDefault("radius", 8.0);
        int durationSeconds = data.effects.getOrDefault("duration", 6.0).intValue();
        int damageInterval = data.effects.getOrDefault("damageInterval", 5.0).intValue();

        PlayerStats stats = statsManager.getStats(player);
        double damagePerTick = baseDamage + stats.getStrength() * strengthScaling + stats.getAgility() * agilityScaling;

        // 領域中心
        final Location domainCenter = player.getLocation().clone();

        // === 開場效果 ===
        // 震撼音效
        domainCenter.getWorld().playSound(domainCenter, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
        domainCenter.getWorld().playSound(domainCenter, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.7f);

        // 開場斬擊粒子爆發
        spawnMalevolentShrineOpening(domainCenter, radius);

        // 給予玩家效果
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationSeconds * 20 + 20, 1, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationSeconds * 20 + 20, 1, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationSeconds * 20 + 20, 0, true, false));

        // === 持續效果 ===
        final int totalTicks = durationSeconds * 20;
        final int[] ticksElapsed = {0};
        final double finalRadius = radius;

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (ticksElapsed[0] >= totalTicks || !player.isOnline() || player.isDead()) {
                // === 結束效果 ===
                domainCenter.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, domainCenter.clone().add(0, 2, 0), 5, 2, 2, 2, 0);
                domainCenter.getWorld().playSound(domainCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);

                // 最終斬擊波
                for (int i = 0; i < 20; i++) {
                    double angle = Math.random() * 2 * Math.PI;
                    double r = Math.random() * finalRadius;
                    Location slashLoc = domainCenter.clone().add(Math.cos(angle) * r, 1, Math.sin(angle) * r);
                    slashLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, slashLoc, 1, 0, 0, 0, 0);
                }
                task.cancel();
                return;
            }

            // === 領域視覺效果 ===
            if (ticksElapsed[0] % 2 == 0) {
                // 隨機斬擊效果遍布領域
                for (int i = 0; i < 5; i++) {
                    double angle = Math.random() * 2 * Math.PI;
                    double r = Math.random() * finalRadius;
                    Location slashLoc = domainCenter.clone().add(Math.cos(angle) * r, Math.random() * 2 + 0.5, Math.sin(angle) * r);
                    slashLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, slashLoc, 1, 0, 0, 0, 0);
                    slashLoc.getWorld().spawnParticle(Particle.CRIT, slashLoc, 2, 0.1, 0.1, 0.1, 0.02);
                }
            }

            // 邊界效果
            if (ticksElapsed[0] % 5 == 0) {
                spawnMalevolentShrineBoundary(domainCenter, finalRadius, ticksElapsed[0]);
            }

            // 氛圍音效
            if (ticksElapsed[0] % 15 == 0) {
                domainCenter.getWorld().playSound(domainCenter, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.2f);
            }

            // === 自動傷害所有敵人 (必中效果) ===
            if (ticksElapsed[0] % damageInterval == 0) {
                List<LivingEntity> enemies = aoeUtil.getRadiusTargets(player, domainCenter, finalRadius);
                for (LivingEntity enemy : enemies) {
                    // 造成傷害 - 必中斬擊
                    damageManager.dealSkillDamage(player, enemy, damagePerTick);

                    // 斬擊效果直接出現在目標身上
                    enemy.getWorld().spawnParticle(Particle.SWEEP_ATTACK, enemy.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
                    enemy.getWorld().spawnParticle(Particle.CRIT, enemy.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);
                    enemy.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, enemy.getLocation().add(0, 1.2, 0), 3, 0.2, 0.2, 0.2, 0);

                    // 命中音效
                    enemy.getWorld().playSound(enemy.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.4f, 1.5f);
                }
            }

            ticksElapsed[0]++;
        }, 0L, 1L);

        return true;
    }

    /**
     * 伏魔御廚子 - 開場效果
     */
    private void spawnMalevolentShrineOpening(Location center, double radius) {
        // 從中心向外擴散的斬擊波
        for (double r = 0; r <= radius; r += 0.5) {
            final double currentR = r;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                int points = (int)(currentR * 8);
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI / points) * i;
                    double x = Math.cos(angle) * currentR;
                    double z = Math.sin(angle) * currentR;
                    Location loc = center.clone().add(x, 0.1, z);
                    loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0, 0, 0, 0);
                    loc.getWorld().spawnParticle(Particle.CRIT, loc, 2, 0.1, 0.1, 0.1, 0.02);
                }
            }, (long)(r * 2));
        }

        // 中心黑紅柱
        for (double y = 0; y < 6; y += 0.3) {
            Location loc = center.clone().add(0, y, 0);
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 5, 0.3, 0, 0.3, 0.02);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.2, 0, 0.2, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 1.5f)); // 深紅色
        }
    }

    /**
     * 伏魔御廚子 - 邊界效果
     */
    private void spawnMalevolentShrineBoundary(Location center, double radius, int tick) {
        int points = 24;
        double angleOffset = tick * 0.1;

        // 底部邊界環
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI / points) * i + angleOffset;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location loc = center.clone().add(x, 0.2, z);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 1.0f));

            // 隨機斬擊
            if (Math.random() < 0.2) {
                loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, Math.random() * 2, 0), 1, 0, 0, 0, 0);
            }
        }

        // 頂部較小的邊界環
        for (int i = 0; i < points / 2; i++) {
            double angle = (2 * Math.PI / (points / 2)) * i - angleOffset * 1.5;
            double x = Math.cos(angle) * radius * 0.9;
            double z = Math.sin(angle) * radius * 0.9;
            Location loc = center.clone().add(x, 3, z);
            loc.getWorld().spawnParticle(Particle.CRIT, loc, 1, 0, 0, 0, 0);
        }
    }

    // ==================== 赤血操術系技能實作 ====================

    /**
     * 穿血 (Piercing Blood) - 高速穿透血箭
     * 發射高速直線血箭，穿透多個敵人造成高傷害
     */
    private boolean executePiercingBlood(Player player, Talent talent, int level, ItemStack item) {
        // 檢查血量資源 (使用玩家HP)
        double bloodCost = 10;
        if (!bloodManager.hasBlood(player, bloodCost)) {
            player.sendMessage("§c生命值不足！需要至少 " + (int)(bloodCost + 1) + " HP");
            return false;
        }

        // 先消耗生命值
        if (!bloodManager.consumeBlood(player, bloodCost)) {
            return false;
        }

        player.sendMessage("§4[穿血] §c貫穿！");

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        Talent.TalentLevelData data = talent.getLevelData(level);
        double baseDamage = data.effects.getOrDefault("baseDamage", 80.0);
        double bloodScaling = data.effects.getOrDefault("bloodScaling", 0.5);
        double range = data.effects.getOrDefault("range", 20.0);
        int maxPierce = data.effects.getOrDefault("pierceCount", 3.0).intValue();

        // 傷害 = 基礎傷害 + 當前血量 * 血量係數
        double currentBlood = bloodManager.getBlood(player);
        double finalDamage = baseDamage + currentBlood * bloodScaling;

        // 音效
        player.getWorld().playSound(eyeLoc, Sound.ENTITY_ARROW_SHOOT, 1.2f, 0.5f);
        player.getWorld().playSound(eyeLoc, Sound.BLOCK_SLIME_BLOCK_BREAK, 1.0f, 0.8f);

        // 高速穿透投射物
        final Location[] currentLoc = {eyeLoc.clone()};
        final int[] ticks = {0};
        final double speed = 3.0;
        final int[] pierceCount = {0};
        final java.util.Set<java.util.UUID> hitEntities = new java.util.HashSet<>();

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (pierceCount[0] >= maxPierce || currentLoc[0].distance(eyeLoc) > range) {
                task.cancel();
                return;
            }

            // 移動
            currentLoc[0].add(direction.clone().multiply(speed));

            // 血紅色粒子軌跡
            currentLoc[0].getWorld().spawnParticle(Particle.DUST, currentLoc[0], 5, 0.1, 0.1, 0.1, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 1.2f));
            currentLoc[0].getWorld().spawnParticle(Particle.BLOCK, currentLoc[0], 2, 0.1, 0.1, 0.1, 0,
                Material.REDSTONE_BLOCK.createBlockData());

            // 檢測命中（穿透）
            List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, currentLoc[0], 1.0);
            for (LivingEntity target : targets) {
                if (!hitEntities.contains(target.getUniqueId())) {
                    hitEntities.add(target.getUniqueId());

                    damageManager.dealSkillDamage(player, target, finalDamage);
                    pierceCount[0]++;

                    // 命中特效
                    target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0,
                        new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
                    target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 8);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);

                    if (pierceCount[0] >= maxPierce) {
                        task.cancel();
                        return;
                    }
                }
            }

            ticks[0]++;
        }, 0L, 1L);

        return true;
    }

    /**
     * 血潮 (Blood Wave) - 向前推進的血浪
     * 向前釋放血浪，持續推進並造成多段傷害
     */
    private boolean executeBloodWave(Player player, Talent talent, int level, ItemStack item) {
        double bloodCost = 15;
        if (!bloodManager.hasBlood(player, bloodCost)) {
            player.sendMessage("§c生命值不足！需要至少 " + (int)(bloodCost + 1) + " HP");
            return false;
        }

        // 先消耗生命值
        if (!bloodManager.consumeBlood(player, bloodCost)) {
            return false;
        }

        player.sendMessage("§4[血潮] §c湧動！");

        Location startLoc = player.getLocation().clone();
        Vector direction = startLoc.getDirection().setY(0).normalize();

        Talent.TalentLevelData data = talent.getLevelData(level);
        double baseDamage = data.effects.getOrDefault("baseDamage", 25.0);
        double bloodScaling = data.effects.getOrDefault("bloodScaling", 0.2);
        double range = data.effects.getOrDefault("range", 8.0);
        double width = data.effects.getOrDefault("width", 3.0);
        int hitCount = data.effects.getOrDefault("hitCount", 4.0).intValue();
        double pushForce = data.effects.getOrDefault("pushForce", 0.3);

        double currentBlood = bloodManager.getBlood(player);
        double damagePerHit = baseDamage + currentBlood * bloodScaling;

        // 音效
        player.getWorld().playSound(startLoc, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.6f);

        // 血浪推進
        final Location[] waveLoc = {startLoc.clone()};
        final int[] hits = {0};
        final int[] ticks = {0};
        final int ticksPerHit = 60 / hitCount; // 3秒內完成所有傷害段
        final java.util.Map<java.util.UUID, Integer> entityHitCount = new java.util.HashMap<>();

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (waveLoc[0].distance(startLoc) > range || hits[0] >= hitCount) {
                task.cancel();
                return;
            }

            // 移動血浪
            waveLoc[0].add(direction.clone().multiply(0.5));

            // 血浪視覺效果
            Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
            for (double w = -width / 2; w <= width / 2; w += 0.5) {
                for (double h = 0; h < 1.5; h += 0.3) {
                    Location particleLoc = waveLoc[0].clone().add(perpendicular.clone().multiply(w)).add(0, h, 0);
                    particleLoc.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 0, 0), 1.0f));
                }
            }

            // 每隔一定tick造成傷害
            if (ticks[0] % ticksPerHit == 0) {
                List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, waveLoc[0], width);
                for (LivingEntity target : targets) {
                    int targetHits = entityHitCount.getOrDefault(target.getUniqueId(), 0);
                    if (targetHits < hitCount) {
                        damageManager.dealSkillDamage(player, target, damagePerHit);
                        entityHitCount.put(target.getUniqueId(), targetHits + 1);

                        // 推動效果
                        Vector push = direction.clone().multiply(pushForce).setY(0.1);
                        target.setVelocity(target.getVelocity().add(push));

                        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 3);
                    }
                }
                hits[0]++;

                // 波浪音效
                waveLoc[0].getWorld().playSound(waveLoc[0], Sound.ENTITY_GENERIC_SPLASH, 0.5f, 0.7f);
            }

            ticks[0]++;
        }, 0L, 1L);

        return true;
    }

    /**
     * 血爆 (Blood Burst) - 範圍血液爆炸
     * 引爆指定位置的血液能量，造成範圍傷害，傷害隨血量資源增加
     */
    private boolean executeBloodBurst(Player player, Talent talent, int level, ItemStack item) {
        double bloodCost = 20;
        if (!bloodManager.hasBlood(player, bloodCost)) {
            player.sendMessage("§c生命值不足！需要至少 " + (int)(bloodCost + 1) + " HP");
            return false;
        }

        // 獲取目標位置
        Location targetLoc = player.getTargetBlock(null, 15).getLocation();
        if (targetLoc.getBlock().getType() == Material.AIR) {
            targetLoc = player.getLocation().add(player.getLocation().getDirection().multiply(8));
        }
        targetLoc.add(0, 1, 0);

        // 先消耗生命值
        if (!bloodManager.consumeBlood(player, bloodCost)) {
            return false;
        }

        player.sendMessage("§4[血爆] §c爆裂！");

        Talent.TalentLevelData data = talent.getLevelData(level);
        double baseDamage = data.effects.getOrDefault("baseDamage", 60.0);
        double bloodScaling = data.effects.getOrDefault("bloodScaling", 1.0);
        double bloodPercentBonus = data.effects.getOrDefault("bloodPercentBonus", 0.5);
        double radius = data.effects.getOrDefault("radius", 4.0);

        double currentBlood = bloodManager.getBlood(player);
        double bloodPercent = bloodManager.getBloodPercentage(player);
        // 傷害 = 基礎 + 血量*係數 + 基礎傷害*血量百分比*百分比加成
        double finalDamage = baseDamage + currentBlood * bloodScaling + baseDamage * bloodPercent * bloodPercentBonus;

        // 蓄力效果
        final Location explosionLoc = targetLoc.clone();

        // 標記位置
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SLIME_BLOCK_PLACE, 1.0f, 0.5f);

        // 蓄力粒子（血液聚集）
        for (int i = 0; i < 5; i++) {
            final int delay = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (int j = 0; j < 8; j++) {
                    double angle = Math.random() * 2 * Math.PI;
                    double r = radius + 1;
                    Location from = explosionLoc.clone().add(Math.cos(angle) * r, Math.random() * 2, Math.sin(angle) * r);
                    Vector toCenter = explosionLoc.toVector().subtract(from.toVector()).normalize().multiply(0.5);

                    from.getWorld().spawnParticle(Particle.DUST, from, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
                }
            }, delay * 2L);
        }

        // 爆炸
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // 爆炸視覺效果
            explosionLoc.getWorld().spawnParticle(Particle.DUST, explosionLoc, 100, radius * 0.5, radius * 0.5, radius * 0.5, 0.1,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 2.0f));
            explosionLoc.getWorld().spawnParticle(Particle.BLOCK, explosionLoc, 50, radius * 0.4, 1, radius * 0.4, 0,
                Material.REDSTONE_BLOCK.createBlockData());
            explosionLoc.getWorld().spawnParticle(Particle.EXPLOSION, explosionLoc, 3, 1, 1, 1, 0);

            // 音效
            explosionLoc.getWorld().playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
            explosionLoc.getWorld().playSound(explosionLoc, Sound.BLOCK_SLIME_BLOCK_BREAK, 1.5f, 0.5f);

            // 造成傷害
            List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, explosionLoc, radius);
            for (LivingEntity target : targets) {
                damageManager.dealSkillDamage(player, target, finalDamage);
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 10);
            }
        }, 12L);

        return true;
    }

    /**
     * 血縛 (Blood Binding) - 束縛敵人
     * 以血液束縛敵人，使其無法移動
     */
    private boolean executeBloodBinding(Player player, Talent talent, int level, ItemStack item) {
        double bloodCost = 15;
        if (!bloodManager.hasBlood(player, bloodCost)) {
            player.sendMessage("§c生命值不足！需要至少 " + (int)(bloodCost + 1) + " HP");
            return false;
        }

        Talent.TalentLevelData data = talent.getLevelData(level);
        double range = data.effects.getOrDefault("range", 10.0);

        // 獲取目標
        LivingEntity target = getTargetEntity(player, range);
        if (target == null) {
            player.sendMessage("§c未找到目標！");
            return false;
        }

        // 先消耗生命值
        if (!bloodManager.consumeBlood(player, bloodCost)) {
            return false;
        }

        player.sendMessage("§4[血縛] §c束縛！");

        double rootDuration = data.effects.getOrDefault("rootDuration", 2.0);
        double damage = data.effects.getOrDefault("damage", 20.0);

        // 音效
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.7f);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.5f);

        // 造成傷害
        damageManager.dealSkillDamage(player, target, damage);

        // 定身效果
        int rootTicks = (int)(rootDuration * 20);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, rootTicks, 127, true, false)); // 完全定身
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, rootTicks, 128, true, false)); // 禁止跳躍

        // 血液鎖鏈視覺效果
        final Location playerLoc = player.getLocation().clone().add(0, 1, 0);
        final LivingEntity boundTarget = target;
        final int[] ticksRemaining = {rootTicks};

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (ticksRemaining[0] <= 0 || !boundTarget.isValid() || boundTarget.isDead()) {
                task.cancel();
                return;
            }

            // 繪製血液鎖鏈
            Location targetLoc = boundTarget.getLocation().add(0, 1, 0);
            Vector direction = targetLoc.toVector().subtract(playerLoc.toVector());
            double distance = direction.length();
            direction.normalize();

            for (double d = 0; d < distance; d += 0.5) {
                Location chainLoc = playerLoc.clone().add(direction.clone().multiply(d));
                chainLoc.getWorld().spawnParticle(Particle.DUST, chainLoc, 1, 0.05, 0.05, 0.05, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 0.8f));
            }

            // 目標周圍的束縛環
            if (ticksRemaining[0] % 5 == 0) {
                for (int i = 0; i < 8; i++) {
                    double angle = (2 * Math.PI / 8) * i + ticksRemaining[0] * 0.1;
                    double x = Math.cos(angle) * 0.8;
                    double z = Math.sin(angle) * 0.8;
                    Location ringLoc = boundTarget.getLocation().add(x, 0.5, z);
                    ringLoc.getWorld().spawnParticle(Particle.DUST, ringLoc, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
                }
            }

            ticksRemaining[0]--;
        }, 0L, 1L);

        return true;
    }

    /**
     * 血狂 (Blood Frenzy) - 狂暴狀態
     * 進入血狂狀態，大幅提升攻擊與技能傷害，但持續消耗生命值
     */
    private boolean executeBloodFrenzy(Player player, Talent talent, int level, ItemStack item) {
        double bloodCost = 30;
        if (!bloodManager.hasBlood(player, bloodCost)) {
            player.sendMessage("§c生命值不足！需要至少 " + (int)(bloodCost + 1) + " HP");
            return false;
        }

        // 先消耗生命值
        if (!bloodManager.consumeBlood(player, bloodCost)) {
            return false;
        }

        player.sendMessage("§4[血狂] §c狂化！");

        Talent.TalentLevelData data = talent.getLevelData(level);
        int duration = data.effects.getOrDefault("duration", 8.0).intValue();
        double damageBoost = data.effects.getOrDefault("damageBoost", 0.3);
        double attackSpeedBoost = data.effects.getOrDefault("attackSpeedBoost", 0.2);
        double healthDrain = data.effects.getOrDefault("healthDrainPerSecond", 2.0);


        // 開場效果
        Location loc = player.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.5f);
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

        // 血液爆发粒子
        loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 50, 1, 1, 1, 0.1,
            new Particle.DustOptions(org.bukkit.Color.RED, 2.0f));

        // 給予增益效果
        int durationTicks = duration * 20;
        int strengthLevel = (int)(damageBoost * 5); // 0.3 -> 1級, 0.5 -> 2級
        int hasteLevel = (int)(attackSpeedBoost * 5);

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, strengthLevel, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, durationTicks, hasteLevel, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 1, true, false));

        // 持續效果 - 每秒消耗生命值並顯示粒子
        final int[] ticksRemaining = {durationTicks};
        final double healthDrainPerTick = healthDrain / 20.0;

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (ticksRemaining[0] <= 0 || !player.isOnline() || player.isDead()) {
                // 結束效果
                if (player.isOnline()) {
                    player.sendMessage("§4[血狂] §c狂化狀態結束");
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1.0f, 0.7f);
                }
                task.cancel();
                return;
            }

            // 消耗生命值
            double currentHealth = player.getHealth();
            double newHealth = currentHealth - healthDrainPerTick;
            if (newHealth <= 1.0) {
                // 保留至少1點血量，取消狂化
                player.setHealth(1.0);
                player.sendMessage("§4[血狂] §c生命值過低，狂化強制結束！");
                player.removePotionEffect(PotionEffectType.STRENGTH);
                player.removePotionEffect(PotionEffectType.HASTE);
                player.removePotionEffect(PotionEffectType.SPEED);
                task.cancel();
                return;
            }
            player.setHealth(newHealth);

            // 血液環繞效果
            if (ticksRemaining[0] % 5 == 0) {
                Location playerLoc = player.getLocation();
                for (int i = 0; i < 4; i++) {
                    double angle = (ticksRemaining[0] * 0.1) + (Math.PI / 2) * i;
                    double x = Math.cos(angle) * 1.2;
                    double z = Math.sin(angle) * 1.2;
                    Location particleLoc = playerLoc.clone().add(x, 1, z);
                    particleLoc.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.RED, 1.2f));
                }
            }

            // 每秒播放心跳聲
            if (ticksRemaining[0] % 20 == 0) {
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.5f, 0.5f);
            }

            ticksRemaining[0]--;
        }, 0L, 1L);

        return true;
    }

    // ==================== 烈焰系技能實作 ====================

    /**
     * 火花 — 發射一顆小型火球
     */
    private boolean executeFireSpark(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 1.2);
        double range = talent.getEffectDouble(level, "range", 15);
        double damage = stats.getMagic() * magicScaling;

        Location start = player.getEyeLocation().clone();
        Vector dir = start.getDirection().normalize();

        player.getWorld().playSound(start, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.5f);

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            Location current = start.clone();
            double traveled = 0;

            @Override
            public void run() {
                for (int i = 0; i < 2; i++) {
                    current.add(dir.clone().multiply(0.8));
                    traveled += 0.8;

                    if (traveled > range || current.getBlock().getType().isSolid()) {
                        current.getWorld().spawnParticle(Particle.LAVA, current, 8, 0.3, 0.3, 0.3, 0);
                        current.getWorld().playSound(current, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.0f);
                        cancel();
                        return;
                    }

                    current.getWorld().spawnParticle(Particle.FLAME, current, 3, 0.1, 0.1, 0.1, 0.01);
                    current.getWorld().spawnParticle(Particle.SMOKE, current, 1, 0.05, 0.05, 0.05, 0);

                    for (org.bukkit.entity.Entity entity : current.getWorld().getNearbyEntities(current, 0.8, 0.8, 0.8)) {
                        if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                        damageManager.dealSkillDamage(player, target, damage);
                        target.setFireTicks(40); // 2秒燃燒
                        current.getWorld().spawnParticle(Particle.LAVA, target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0);
                        current.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 1.0f);
                        player.sendMessage("§6[火花] §f命中! 造成 §c" + String.format("%.0f", damage) + " §f傷害");
                        cancel();
                        return;
                    }
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * 毀滅之球 — 巨型火球緩慢前進，持續傷害觸碰敵人
     */
    private boolean executeDestructionOrb(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 1.4);
        double range = talent.getEffectDouble(level, "range", 15);
        double speed = talent.getEffectDouble(level, "speed", 0.3);
        double radius = talent.getEffectDouble(level, "radius", 2.0);
        int duration = (int) talent.getEffectDouble(level, "duration", 5);
        double damage = stats.getMagic() * magicScaling;

        Location start = player.getEyeLocation().clone();
        Vector dir = start.getDirection().normalize();

        player.getWorld().playSound(start, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        player.sendMessage("§6[毀滅之球] §f召喚巨型火球！");

        java.util.Set<java.util.UUID> hitCooldown = java.util.concurrent.ConcurrentHashMap.newKeySet();

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            Location current = start.clone();
            int ticks = 0;
            final int maxTicks = duration * 20;

            @Override
            public void run() {
                ticks++;
                if (ticks > maxTicks || current.getBlock().getType().isSolid()) {
                    // 爆炸！
                    current.getWorld().spawnParticle(Particle.EXPLOSION, current, 3, 0.5, 0.5, 0.5, 0);
                    current.getWorld().spawnParticle(Particle.FLAME, current, 30, radius * 0.5, 0.5, radius * 0.5, 0.1);
                    current.getWorld().spawnParticle(Particle.SMOKE, current, 20, radius * 0.3, 0.3, radius * 0.3, 0.05);
                    current.getWorld().playSound(current, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
                    cancel();
                    return;
                }

                current.add(dir.clone().multiply(speed));

                // 火球粒子 — 圓球
                for (int i = 0; i < 15; i++) {
                    double offsetX = (Math.random() - 0.5) * radius * 2;
                    double offsetY = (Math.random() - 0.5) * radius * 2;
                    double offsetZ = (Math.random() - 0.5) * radius * 2;
                    if (offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ <= radius * radius) {
                        current.getWorld().spawnParticle(Particle.FLAME, current.clone().add(offsetX, offsetY, offsetZ), 1, 0, 0, 0, 0);
                    }
                }
                current.getWorld().spawnParticle(Particle.LAVA, current, 2, 0.3, 0.3, 0.3, 0);

                // 每10 tick清除碰撞冷卻允許重複傷害
                if (ticks % 10 == 0) {
                    hitCooldown.clear();
                }

                // 碰撞檢測
                for (org.bukkit.entity.Entity entity : current.getWorld().getNearbyEntities(current, radius, radius, radius)) {
                    if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                    if (hitCooldown.contains(target.getUniqueId())) continue;
                    hitCooldown.add(target.getUniqueId());
                    target.damage(damage, player);
                    target.setFireTicks(60);
                    current.getWorld().spawnParticle(Particle.LAVA, target.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0);
                }

                // 音效
                if (ticks % 10 == 0) {
                    current.getWorld().playSound(current, Sound.BLOCK_FIRE_AMBIENT, 0.5f, 0.5f);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * 烈焰風暴 — 範圍持續傷害
     */
    private boolean executeFlameStorm(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 1.2);
        double radius = talent.getEffectDouble(level, "radius", 5);
        int duration = (int) talent.getEffectDouble(level, "duration", 5);
        double damagePerTick = stats.getMagic() * magicScaling / duration; // 分攤到每秒

        // 目標位置：玩家看向的方向前方 10 格
        Location target = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(10));
        target.setY(player.getLocation().getY());

        player.getWorld().playSound(target, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
        player.sendMessage("§6[烈焰風暴] §f召喚火焰風暴！");

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = duration * 20;

            @Override
            public void run() {
                ticks++;
                if (ticks > maxTicks) {
                    target.getWorld().spawnParticle(Particle.SMOKE, target, 30, radius / 2, 1, radius / 2, 0.02);
                    cancel();
                    return;
                }

                // 粒子：火焰圓柱
                if (ticks % 2 == 0) {
                    for (int i = 0; i < 10; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double r = Math.random() * radius;
                        double x = Math.cos(angle) * r;
                        double z = Math.sin(angle) * r;
                        double y = Math.random() * 3;
                        target.getWorld().spawnParticle(Particle.FLAME, target.clone().add(x, y, z), 1, 0, 0, 0, 0.02);
                    }
                }

                // 每秒造成傷害
                if (ticks % 20 == 0) {
                    for (org.bukkit.entity.Entity entity : target.getWorld().getNearbyEntities(target, radius, 3, radius)) {
                        if (entity == player || !(entity instanceof LivingEntity t) || t.isDead()) continue;
                        t.damage(damagePerTick, player);
                        t.setFireTicks(40);
                    }
                    target.getWorld().playSound(target, Sound.BLOCK_FIRE_AMBIENT, 0.8f, 0.8f);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * 隕石術 — 召喚隕石
     */
    private boolean executeMeteor(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 5.0);
        double radius = talent.getEffectDouble(level, "radius", 6);
        int fallDelay = (int) talent.getEffectDouble(level, "fallDelay", 40);
        int burnDuration = (int) talent.getEffectDouble(level, "burnDuration", 5);
        double damage = stats.getMagic() * magicScaling;

        // 落點：玩家前方 15 格
        Location impactLoc = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(15));
        impactLoc.setY(player.getLocation().getY());
        Location spawnLoc = impactLoc.clone().add(0, 30, 0);

        player.sendMessage("§6[隕石術] §f天空出現裂縫...隕石即將墜落！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 2.0f);

        // 預警粒子：落點畫圓
        org.bukkit.scheduler.BukkitRunnable warningTask = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks >= fallDelay) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 20; i++) {
                    double angle = (Math.PI * 2 / 20) * i;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    impactLoc.getWorld().spawnParticle(Particle.DUST, impactLoc.clone().add(x, 0.1, z), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
                }
                // 向上的光柱
                for (double y = 0; y < 15; y += 0.5) {
                    impactLoc.getWorld().spawnParticle(Particle.DUST, impactLoc.clone().add(0, y, 0), 1, 0.1, 0, 0.1, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 100, 100), 0.8f));
                }
                impactLoc.getWorld().playSound(impactLoc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.5f + (ticks / (float) fallDelay) * 1.5f);
            }
        };
        warningTask.runTaskTimer(plugin, 0L, 2L);

        // 隕石墜落
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // 墜落動畫
            org.bukkit.scheduler.BukkitRunnable fallTask = new org.bukkit.scheduler.BukkitRunnable() {
                Location current = spawnLoc.clone();
                int ticks = 0;

                @Override
                public void run() {
                    ticks++;
                    current.add(0, -2, 0);

                    // 火焰尾跡
                    current.getWorld().spawnParticle(Particle.FLAME, current, 20, 1, 1, 1, 0.05);
                    current.getWorld().spawnParticle(Particle.LAVA, current, 5, 0.5, 0.5, 0.5, 0);
                    current.getWorld().spawnParticle(Particle.SMOKE, current, 10, 0.8, 0.8, 0.8, 0.02);

                    if (current.getY() <= impactLoc.getY() + 1 || ticks > 30) {
                        // 撞擊！
                        impactLoc.getWorld().spawnParticle(Particle.EXPLOSION, impactLoc, 5, 1, 1, 1, 0);
                        impactLoc.getWorld().spawnParticle(Particle.FLAME, impactLoc, 100, radius, 2, radius, 0.1);
                        impactLoc.getWorld().spawnParticle(Particle.LAVA, impactLoc, 50, radius, 1, radius, 0);
                        impactLoc.getWorld().playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                        impactLoc.getWorld().playSound(impactLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.5f);

                        // 造成傷害
                        for (org.bukkit.entity.Entity e : impactLoc.getWorld().getNearbyEntities(impactLoc, radius, radius, radius)) {
                            if (e == player || !(e instanceof LivingEntity target) || target.isDead()) continue;
                            double dist = target.getLocation().distance(impactLoc);
                            double falloff = Math.max(0.3, 1.0 - (dist / radius) * 0.5);
                            damageManager.dealSkillDamage(player, target, damage * falloff);
                            target.setFireTicks(burnDuration * 20);
                        }

                        cancel();
                    }
                }
            };
            fallTask.runTaskTimer(plugin, 0L, 1L);
        }, fallDelay);

        return true;
    }

    /**
     * 烈焰噴射 — 持續射出火焰
     */
    private boolean executeFlameJet(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 1.0);
        double range = talent.getEffectDouble(level, "range", 8);
        int duration = (int) talent.getEffectDouble(level, "duration", 3);
        int burnDuration = (int) talent.getEffectDouble(level, "burnDuration", 3);
        double damagePerHit = stats.getMagic() * magicScaling / (duration * 4); // 每tick/5 一次

        player.sendMessage("§6[烈焰噴射] §f釋放烈焰！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.2f);

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = duration * 20;

            @Override
            public void run() {
                ticks++;
                if (ticks > maxTicks || !player.isOnline()) {
                    cancel();
                    return;
                }

                // 取玩家當前方向（可以邊轉邊噴）
                Location eyeLoc = player.getEyeLocation();
                Vector direction = eyeLoc.getDirection().normalize();

                // 噴射粒子
                for (double d = 0.5; d <= range; d += 0.5) {
                    Location particleLoc = eyeLoc.clone().add(direction.clone().multiply(d));
                    particleLoc.getWorld().spawnParticle(Particle.FLAME, particleLoc, 1, 0.1, 0.1, 0.1, 0.01);
                    if (d > range * 0.5) {
                        particleLoc.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                    }
                }

                // 每5 tick造成傷害
                if (ticks % 5 == 0) {
                    for (double d = 1; d <= range; d += 1) {
                        Location checkLoc = eyeLoc.clone().add(direction.clone().multiply(d));
                        for (org.bukkit.entity.Entity entity : checkLoc.getWorld().getNearbyEntities(checkLoc, 1, 1, 1)) {
                            if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                            target.damage(damagePerHit, player);
                            target.setFireTicks(burnDuration * 20);
                        }
                    }
                }

                // 音效
                if (ticks % 10 == 0) {
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.5f);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * 熾熱波紋 — 發射火焰波紋
     */
    private boolean executeScorchingWave(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 1.1);
        double range = talent.getEffectDouble(level, "range", 10);
        double width = talent.getEffectDouble(level, "width", 3);
        int burnDuration = (int) talent.getEffectDouble(level, "burnDuration", 3);
        double damage = stats.getMagic() * magicScaling;

        Location start = player.getLocation().add(0, 0.5, 0);
        Vector dir = player.getEyeLocation().getDirection().normalize().setY(0).normalize();

        player.getWorld().playSound(start, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.8f);
        player.sendMessage("§6[熾熱波紋] §f發出火焰波紋！");

        java.util.Set<java.util.UUID> alreadyHit = java.util.concurrent.ConcurrentHashMap.newKeySet();

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            double traveled = 0;

            @Override
            public void run() {
                traveled += 1.0;
                if (traveled > range) {
                    cancel();
                    return;
                }

                Location center = start.clone().add(dir.clone().multiply(traveled));

                // 橫向粒子波紋
                Vector perpendicular = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
                for (double w = -width / 2; w <= width / 2; w += 0.5) {
                    Location particleLoc = center.clone().add(perpendicular.clone().multiply(w));
                    particleLoc.getWorld().spawnParticle(Particle.FLAME, particleLoc, 2, 0.1, 0.2, 0.1, 0.01);
                    particleLoc.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                }

                // 碰撞
                for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, width / 2, 1.5, 1)) {
                    if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                    if (alreadyHit.contains(target.getUniqueId())) continue;
                    alreadyHit.add(target.getUniqueId());
                    target.damage(damage, player);
                    target.setFireTicks(burnDuration * 20);
                    center.getWorld().spawnParticle(Particle.LAVA, target.getLocation().add(0, 1, 0), 8, 0.2, 0.2, 0.2, 0);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    /**
     * 熾焰之環 — 以自身為中心持續AOE燃燒
     */
    private boolean executeInfernoRing(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 0.8);
        double radius = talent.getEffectDouble(level, "radius", 5);
        int duration = (int) talent.getEffectDouble(level, "duration", 5);
        int burnDuration = (int) talent.getEffectDouble(level, "burnDuration", 3);
        double damagePerSecond = stats.getMagic() * magicScaling;

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
        player.sendMessage("§6[熾焰之環] §f點燃周圍一切！");

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = duration * 20;

            @Override
            public void run() {
                ticks++;
                if (ticks > maxTicks || !player.isOnline()) {
                    cancel();
                    return;
                }

                Location center = player.getLocation();

                // 火焰環粒子
                if (ticks % 2 == 0) {
                    for (int i = 0; i < 20; i++) {
                        double angle = (Math.PI * 2 / 20) * i + (ticks * 0.05);
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(x, 0.3, z), 1, 0, 0, 0, 0.02);
                    }
                    // 內圈粒子
                    for (int i = 0; i < 5; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double r = Math.random() * radius;
                        center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(Math.cos(angle) * r, Math.random() * 1.5, Math.sin(angle) * r), 1, 0, 0, 0, 0);
                    }
                }

                // 每秒造成傷害
                if (ticks % 20 == 0) {
                    for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, radius, 3, radius)) {
                        if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                        target.damage(damagePerSecond, player);
                        target.setFireTicks(burnDuration * 20);
                    }
                    center.getWorld().playSound(center, Sound.BLOCK_FIRE_AMBIENT, 0.6f, 0.8f);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * 地獄領域 — 大範圍地面火海
     */
    private boolean executeHellDomain(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 0.6);
        double spiritScaling = talent.getEffectDouble(level, "spiritScaling", 0.3);
        double radius = talent.getEffectDouble(level, "radius", 8);
        int duration = (int) talent.getEffectDouble(level, "duration", 8);
        int burnDuration = (int) talent.getEffectDouble(level, "burnDuration", 5);
        double damagePerTick = stats.getMagic() * magicScaling + stats.getSpirit() * spiritScaling;

        Location center = player.getLocation().clone();

        player.getWorld().playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
        player.getWorld().playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.3f);
        player.sendMessage("§4[地獄領域] §c腳下化為灼熱的地獄！");

        // 起始爆發粒子
        for (int i = 0; i < 50; i++) {
            double angle = Math.random() * Math.PI * 2;
            double r = Math.random() * radius;
            center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r), 1, 0, 0.5, 0, 0.05);
        }

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = duration * 20;

            @Override
            public void run() {
                ticks++;
                if (ticks > maxTicks) {
                    center.getWorld().spawnParticle(Particle.SMOKE, center, 30, radius / 2, 1, radius / 2, 0.02);
                    cancel();
                    return;
                }

                // 持續火海粒子
                if (ticks % 3 == 0) {
                    for (int i = 0; i < 15; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double r = Math.random() * radius;
                        double x = Math.cos(angle) * r;
                        double z = Math.sin(angle) * r;
                        center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(x, 0.1, z), 1, 0, 0.3, 0, 0.01);
                    }
                    // 邊界火焰
                    for (int i = 0; i < 10; i++) {
                        double angle = (Math.PI * 2 / 10) * i + (ticks * 0.03);
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(x, 0.5, z), 2, 0, 0.5, 0, 0.02);
                    }
                    // 隨機岩漿飛濺
                    center.getWorld().spawnParticle(Particle.LAVA, center, 3, radius / 2, 0.1, radius / 2, 0);
                }

                // 每半秒造成傷害
                if (ticks % 10 == 0) {
                    for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, radius, 3, radius)) {
                        if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                        target.damage(damagePerTick / 2, player); // 每0.5秒造成一半傷害
                        target.setFireTicks(burnDuration * 20);
                        // 緩速效果
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false));
                    }
                }

                // 環境音效
                if (ticks % 20 == 0) {
                    center.getWorld().playSound(center, Sound.BLOCK_FIRE_AMBIENT, 0.8f, 0.5f);
                    center.getWorld().playSound(center, Sound.BLOCK_LAVA_AMBIENT, 0.5f, 0.8f);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ==================== 刺客系技能實作 ====================

    /**
     * 隱身 — 進入隱形狀態
     */
    private boolean executeStealth(Player player, Talent talent, int level, ItemStack item) {
        int duration = (int) talent.getEffectDouble(level, "duration", 5);
        int speedBoost = (int) talent.getEffectDouble(level, "speedBoost", 0);

        // 隱形效果
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration * 20, 0, false, false));
        if (speedBoost > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration * 20, speedBoost - 1, false, false));
        }

        // 粒子：消失效果
        Location loc = player.getLocation();
        loc.getWorld().spawnParticle(Particle.SMOKE, loc.add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 20, 0.5, 0.5, 0.5, 0.5);
        loc.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.2f);

        player.sendMessage("§8[隱身] §7你融入了暗影之中... (" + duration + "秒)");

        // 移除附近敵對生物的目標
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(20, 10, 20)) {
            if (entity instanceof org.bukkit.entity.Mob mob) {
                if (mob.getTarget() == player) {
                    mob.setTarget(null);
                }
            }
        }

        return true;
    }

    /**
     * 影襲 — 瞬移到敵人背後並背刺
     */
    private boolean executeShadowAssault(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double weaponScaling = talent.getEffectDouble(level, "weaponScaling", 1.5);
        double agilityScaling = talent.getEffectDouble(level, "agilityScaling", 1.0);
        double range = talent.getEffectDouble(level, "range", 10);

        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double damage = weaponDamage * weaponScaling + stats.getAgility() * agilityScaling;

        // 找最近的目標
        LivingEntity target = null;
        double closestDist = range;
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(range, range, range)) {
            if (entity == player || !(entity instanceof LivingEntity le) || le.isDead()) continue;
            double dist = entity.getLocation().distance(player.getLocation());
            if (dist < closestDist) {
                closestDist = dist;
                target = le;
            }
        }

        if (target == null) {
            player.sendMessage("§c[影襲] 附近沒有目標！");
            return false;
        }

        // 瞬移到目標背後
        Location targetLoc = target.getLocation();
        Vector behindDir = targetLoc.getDirection().normalize().multiply(-1.5);
        Location teleportLoc = targetLoc.clone().add(behindDir);
        teleportLoc.setY(targetLoc.getY());
        teleportLoc.setYaw(targetLoc.getYaw()); // 面對目標的方向
        teleportLoc.setPitch(0);

        // 出發粒子
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);

        // 瞬移
        player.teleport(teleportLoc);

        // 到達粒子
        player.getWorld().spawnParticle(Particle.SMOKE, teleportLoc.clone().add(0, 1, 0), 15, 0.2, 0.3, 0.2, 0.03);
        player.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.3);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0);
        player.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);

        // 造成傷害
        target.damage(damage, player);
        player.sendMessage("§8[影襲] §7背刺！造成 §c" + String.format("%.0f", damage) + " §7傷害");

        // 解除隱身
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        return true;
    }

    /**
     * 鬼哭神嚎 — 連續背刺多名敵人
     */
    private boolean executePhantomMassacre(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double weaponScaling = talent.getEffectDouble(level, "weaponScaling", 0.9);
        double agilityScaling = talent.getEffectDouble(level, "agilityScaling", 0.9);
        int strikeCount = (int) talent.getEffectDouble(level, "strikeCount", 4);
        double stealthAfter = talent.getEffectDouble(level, "stealthAfter", 2.5);
        double searchRadius = talent.getEffectDouble(level, "searchRadius", 10);

        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double damagePerStrike = weaponDamage * weaponScaling + stats.getAgility() * agilityScaling;

        // 收集附近目標
        java.util.List<LivingEntity> targets = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(searchRadius, searchRadius, searchRadius)) {
            if (entity == player || !(entity instanceof LivingEntity le) || le.isDead()) continue;
            targets.add(le);
        }

        if (targets.isEmpty()) {
            player.sendMessage("§c[鬼哭神嚎] 附近沒有目標！");
            return false;
        }

        player.sendMessage("§4[鬼哭神嚎] §c開始連續背刺！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.6f, 2.0f);

        // 讓玩家無敵
        player.setInvulnerable(true);

        final int[] strikeIndex = {0};
        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (strikeIndex[0] >= strikeCount || !player.isOnline()) {
                    // 結束後進入隱身
                    player.setInvulnerable(false);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, (int)(stealthAfter * 20), 0, false, false));
                    player.sendMessage("§8[鬼哭神嚎] §7結束！進入隱身 " + stealthAfter + " 秒");
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.0f);
                    cancel();
                    return;
                }

                // 選擇目標（循環）
                // 重新掃描存活目標
                java.util.List<LivingEntity> aliveTargets = new java.util.ArrayList<>();
                for (org.bukkit.entity.Entity entity : player.getNearbyEntities(searchRadius, searchRadius, searchRadius)) {
                    if (entity == player || !(entity instanceof LivingEntity le) || le.isDead()) continue;
                    aliveTargets.add(le);
                }
                if (aliveTargets.isEmpty()) {
                    strikeIndex[0] = strikeCount; // 強制結束
                    return;
                }
                LivingEntity target = aliveTargets.get(strikeIndex[0] % aliveTargets.size());

                // 出發粒子
                player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 10, 0.2, 0.3, 0.2, 0.05);

                // 瞬移到目標背後
                Location targetLoc = target.getLocation();
                Vector behindDir = targetLoc.getDirection().normalize().multiply(-1.5);
                Location teleportLoc = targetLoc.clone().add(behindDir);
                teleportLoc.setY(targetLoc.getY());
                teleportLoc.setYaw(targetLoc.getYaw());
                player.teleport(teleportLoc);

                // 攻擊
                target.damage(damagePerStrike, player);

                // 效果
                player.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.3);
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 2, 0.2, 0.2, 0.2, 0);
                player.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.03);
                player.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.5f);

                strikeIndex[0]++;
            }
        };
        task.runTaskTimer(plugin, 0L, 6L); // 每6 tick (0.3秒) 一次背刺

        return true;
    }

    /**
     * 快速斬擊 — 瞬間連續斬擊
     */
    private boolean executeRapidSlash(Player player, Talent talent, int level, ItemStack item) {
        double weaponScaling = talent.getEffectDouble(level, "weaponScaling", 0.5);
        int hitCount = (int) talent.getEffectDouble(level, "hitCount", 3);
        double range = talent.getEffectDouble(level, "range", 4);

        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double damagePerHit = weaponDamage * weaponScaling;

        Location origin = player.getLocation();
        Vector dir = origin.getDirection().setY(0).normalize();

        player.sendMessage("§c[快速斬擊] §f連續斬擊！");

        final int[] hitIndex = {0};
        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (hitIndex[0] >= hitCount || !player.isOnline()) {
                    cancel();
                    return;
                }

                Location currentLoc = player.getLocation();
                Vector currentDir = currentLoc.getDirection().setY(0).normalize();

                // 扇形檢測
                for (org.bukkit.entity.Entity entity : currentLoc.getWorld().getNearbyEntities(currentLoc, range, 2, range)) {
                    if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                    Vector toEntity = target.getLocation().toVector().subtract(currentLoc.toVector()).normalize();
                    double dot = currentDir.dot(toEntity);
                    if (dot > 0.3) { // 前方約120度
                        target.damage(damagePerHit, player);
                    }
                }

                // 粒子與音效
                currentLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, currentLoc.clone().add(currentDir.clone().multiply(2)).add(0, 1, 0), 3, 0.5, 0.3, 0.5, 0);
                currentLoc.getWorld().spawnParticle(Particle.CRIT, currentLoc.clone().add(currentDir.clone().multiply(2)).add(0, 1, 0), 8, 0.5, 0.3, 0.5, 0.2);
                currentLoc.getWorld().playSound(currentLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f + (hitIndex[0] * 0.15f));

                hitIndex[0]++;
            }
        };
        task.runTaskTimer(plugin, 0L, 4L); // 每4 tick (0.2秒) 一次斬擊

        return true;
    }

    /**
     * 十字刃 — 發射十字形斬擊波
     */
    private boolean executeCrossBlade(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double weaponScaling = talent.getEffectDouble(level, "weaponScaling", 1.2);
        double agilityScaling = talent.getEffectDouble(level, "agilityScaling", 0.8);
        double range = talent.getEffectDouble(level, "range", 10);
        double multiHitBonus = talent.getEffectDouble(level, "multiHitBonus", 0.15);

        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double baseDamage = weaponDamage * weaponScaling + stats.getAgility() * agilityScaling;

        Location start = player.getEyeLocation().clone();
        Vector dir = start.getDirection().normalize().setY(0).normalize();
        Vector perpendicular = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

        player.getWorld().playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
        player.sendMessage("§c[十字刃] §f發射十字斬擊！");

        java.util.Set<java.util.UUID> hitEntities = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final int[] hitCount = {0};

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            double traveled = 0;

            @Override
            public void run() {
                traveled += 1.0;
                if (traveled > range) {
                    cancel();
                    return;
                }

                Location center = start.clone().add(dir.clone().multiply(traveled));
                center.setY(player.getLocation().getY() + 1);

                // 十字形粒子：水平 + 垂直
                for (double w = -2; w <= 2; w += 0.5) {
                    // 水平臂
                    Location hLoc = center.clone().add(perpendicular.clone().multiply(w));
                    hLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, hLoc, 1, 0, 0, 0, 0);
                    hLoc.getWorld().spawnParticle(Particle.CRIT, hLoc, 1, 0.1, 0.1, 0.1, 0.05);
                }
                for (double h = -1.5; h <= 1.5; h += 0.5) {
                    // 垂直臂
                    Location vLoc = center.clone().add(0, h, 0);
                    vLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, vLoc, 1, 0, 0, 0, 0);
                }

                // 碰撞：橫向3格
                for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, 2.5, 2, 2.5)) {
                    if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                    if (hitEntities.contains(target.getUniqueId())) continue;
                    hitEntities.add(target.getUniqueId());
                    hitCount[0]++;
                    double finalDamage = baseDamage * (1.0 + (hitCount[0] - 1) * multiHitBonus);
                    target.damage(finalDamage, player);
                    target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.2);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.3f);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /**
     * 幻影連斬 — 快速斬擊周圍敵人
     */
    private boolean executePhantomFlurry(Player player, Talent talent, int level, ItemStack item) {
        double weaponScaling = talent.getEffectDouble(level, "weaponScaling", 0.6);
        int hitCount = (int) talent.getEffectDouble(level, "hitCount", 10);
        double radius = talent.getEffectDouble(level, "radius", 5);
        int duration = (int) talent.getEffectDouble(level, "duration", 3);

        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double damagePerHit = weaponDamage * weaponScaling;

        player.sendMessage("§5[幻影連斬] §d化為幻影！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.5f, 2.0f);

        // 給予速度加成
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration * 20, 2, false, false));

        int ticksPerHit = (duration * 20) / hitCount;
        if (ticksPerHit < 2) ticksPerHit = 2;

        final int[] hitIndex = {0};
        final int finalTicksPerHit = ticksPerHit;
        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (hitIndex[0] >= hitCount || !player.isOnline()) {
                    player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
                    cancel();
                    return;
                }

                if (ticks % finalTicksPerHit == 0) {
                    Location loc = player.getLocation();

                    // 隨機斬擊方向的粒子
                    double angle = Math.random() * Math.PI * 2;
                    double x = Math.cos(angle) * 1.5;
                    double z = Math.sin(angle) * 1.5;
                    loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(x, 1.2, z), 2, 0.3, 0.2, 0.3, 0);
                    loc.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(x, 1, z), 5, 0.3, 0.3, 0.3, 0.2);

                    // 對範圍內敵人造成傷害
                    for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, radius, 3, radius)) {
                        if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                        target.damage(damagePerHit, player);
                    }

                    loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.0f + (float)(Math.random() * 0.5));
                    hitIndex[0]++;
                }

                // 幻影殘影
                if (ticks % 3 == 0) {
                    Location pLoc = player.getLocation().add(0, 1, 0);
                    pLoc.getWorld().spawnParticle(Particle.DUST, pLoc, 3, 0.2, 0.4, 0.2, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(128, 0, 255), 1.0f));
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /**
     * 弧形斬 — 前方扇形範圍攻擊
     */
    private boolean executeArcSlash(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double weaponScaling = talent.getEffectDouble(level, "weaponScaling", 1.0);
        double agilityScaling = talent.getEffectDouble(level, "agilityScaling", 0.5);
        double radius = talent.getEffectDouble(level, "radius", 3);
        double arcAngle = talent.getEffectDouble(level, "angle", 120);

        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double damage = weaponDamage * weaponScaling + stats.getAgility() * agilityScaling;

        Location origin = player.getLocation();
        Vector dir = origin.getDirection().setY(0).normalize();
        double halfAngle = Math.toRadians(arcAngle / 2);

        // 扇形粒子
        for (double a = -halfAngle; a <= halfAngle; a += 0.15) {
            double rx = dir.getX() * Math.cos(a) - dir.getZ() * Math.sin(a);
            double rz = dir.getX() * Math.sin(a) + dir.getZ() * Math.cos(a);
            for (double d = 1; d <= radius; d += 0.8) {
                Location pLoc = origin.clone().add(rx * d, 1, rz * d);
                pLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, pLoc, 1, 0, 0, 0, 0);
                pLoc.getWorld().spawnParticle(Particle.CRIT, pLoc, 1, 0.1, 0.1, 0.1, 0.05);
            }
        }
        origin.getWorld().playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);

        // 扇形內敵人受傷
        for (org.bukkit.entity.Entity entity : origin.getWorld().getNearbyEntities(origin, radius, 2, radius)) {
            if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
            Vector toEntity = target.getLocation().toVector().subtract(origin.toVector()).setY(0).normalize();
            double angle = Math.acos(Math.max(-1, Math.min(1, dir.dot(toEntity))));
            if (angle <= halfAngle) {
                target.damage(damage, player);
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 8, 0.2, 0.2, 0.2, 0.2);
            }
        }
        return true;
    }

    /**
     * 次月影 — 月牙形範圍斬擊
     */
    private boolean executeCrescentShadow(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double weaponScaling = talent.getEffectDouble(level, "weaponScaling", 1.3);
        double agilityScaling = talent.getEffectDouble(level, "agilityScaling", 1.0);
        double radius = talent.getEffectDouble(level, "radius", 5);

        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double damage = weaponDamage * weaponScaling + stats.getAgility() * agilityScaling;

        Location center = player.getLocation();
        Vector dir = center.getDirection().setY(0).normalize();

        // 月牙形粒子（前方半圓）
        for (double a = -Math.PI / 2; a <= Math.PI / 2; a += 0.1) {
            double rx = dir.getX() * Math.cos(a) - dir.getZ() * Math.sin(a);
            double rz = dir.getX() * Math.sin(a) + dir.getZ() * Math.cos(a);
            Location edgeLoc = center.clone().add(rx * radius, 0.5, rz * radius);
            edgeLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, edgeLoc, 1, 0, 0, 0, 0);
            edgeLoc.getWorld().spawnParticle(Particle.DUST, edgeLoc, 2, 0.1, 0.1, 0.1, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 0, 255), 1.5f));
        }
        // 填充內部
        for (int i = 0; i < 20; i++) {
            double a = (Math.random() - 0.5) * Math.PI;
            double r = Math.random() * radius;
            double rx = dir.getX() * Math.cos(a) - dir.getZ() * Math.sin(a);
            double rz = dir.getX() * Math.sin(a) + dir.getZ() * Math.cos(a);
            Location fillLoc = center.clone().add(rx * r, 0.5 + Math.random(), rz * r);
            fillLoc.getWorld().spawnParticle(Particle.SMOKE, fillLoc, 1, 0.1, 0.1, 0.1, 0.02);
        }

        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
        center.getWorld().playSound(center, Sound.ENTITY_WITHER_SHOOT, 0.4f, 2.0f);

        // 傷害前方半球內敵人
        for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, radius, 3, radius)) {
            if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
            Vector toEntity = target.getLocation().toVector().subtract(center.toVector()).setY(0).normalize();
            if (dir.dot(toEntity) > 0) { // 前方
                target.damage(damage, player);
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.3);
                target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
            }
        }
        return true;
    }

    /**
     * 死神降臨 — 大範圍旋轉攻擊 + 吸血
     */
    private boolean executeDeathGod(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double weaponScaling = talent.getEffectDouble(level, "weaponScaling", 0.8);
        double agilityScaling = talent.getEffectDouble(level, "agilityScaling", 0.6);
        double spiritScaling = talent.getEffectDouble(level, "spiritScaling", 0.3);
        double radius = talent.getEffectDouble(level, "radius", 6);
        int duration = (int) talent.getEffectDouble(level, "duration", 5);
        double lifestealPercent = talent.getEffectDouble(level, "lifestealPercent", 0.15);

        double weaponDamage = calculateBaseWeaponDamage(player, player.getInventory().getItemInMainHand());
        double damagePerTick = weaponDamage * weaponScaling + stats.getAgility() * agilityScaling + stats.getSpirit() * spiritScaling;

        player.sendMessage("§4[死神降臨] §8死神的鐮刀降臨！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

        // 開場黑暗粒子
        Location startLoc = player.getLocation();
        for (int i = 0; i < 40; i++) {
            double angle = Math.random() * Math.PI * 2;
            double r = Math.random() * radius;
            startLoc.getWorld().spawnParticle(Particle.SMOKE, startLoc.clone().add(Math.cos(angle) * r, Math.random() * 2, Math.sin(angle) * r), 1, 0, 0.5, 0, 0.05);
        }

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = duration * 20;

            @Override
            public void run() {
                ticks++;
                if (ticks > maxTicks || player.isDead()) {
                    cancel();
                    return;
                }

                // 旋轉鐮刀粒子
                if (ticks % 2 == 0) {
                    double spinAngle = ticks * 0.3;
                    for (int i = 0; i < 3; i++) {
                        double a = spinAngle + (Math.PI * 2 / 3) * i;
                        for (double d = 1; d <= radius; d += 0.8) {
                            double x = Math.cos(a) * d;
                            double z = Math.sin(a) * d;
                            Location pLoc = player.getLocation().clone().add(x, 0.5, z);
                            pLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, pLoc, 1, 0, 0, 0, 0);
                            if (d > radius - 1) {
                                pLoc.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 0, 80), 1.5f));
                            }
                        }
                    }
                }

                // 每秒造成傷害 + 吸血
                if (ticks % 20 == 0) {
                    double totalDamageDealt = 0;
                    for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), radius, 3, radius)) {
                        if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                        damageManager.dealSkillDamage(player, target, damagePerTick);
                        double heal = damagePerTick * lifestealPercent;
                        totalDamageDealt += heal;
                        // 汲取視覺效果
                        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 180, 0), 0.8f));
                    }
                    if (totalDamageDealt > 0) {
                        double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + totalDamageDealt);
                        player.setHealth(newHealth);
                        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 3, 0.3, 0.3, 0.3, 0);
                    }
                }

                // 暗影環境音效
                if (ticks % 40 == 0) {
                    player.getWorld().playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.3f, 0.5f);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ==================== 暗黑系技能實作 ====================

    /**
     * 暗影彈 — 發射暗影投射物
     */
    private boolean executeShadowBolt(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 1.5);
        double range = talent.getEffectDouble(level, "range", 20);
        double damage = stats.getMagic() * magicScaling;

        Location start = player.getEyeLocation().clone();
        Vector dir = start.getDirection().normalize();

        player.getWorld().playSound(start, Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.5f);

        new BukkitRunnable() {
            Location current = start.clone();
            double traveled = 0;
            @Override
            public void run() {
                for (int i = 0; i < 2; i++) {
                    current.add(dir.clone().multiply(1.0));
                    traveled += 1.0;
                    if (traveled > range || current.getBlock().getType().isSolid()) {
                        cancel(); return;
                    }
                    current.getWorld().spawnParticle(Particle.DUST, current, 3, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 0, 120), 1.2f));
                    current.getWorld().spawnParticle(Particle.SMOKE, current, 1, 0.05, 0.05, 0.05, 0);

                    for (org.bukkit.entity.Entity entity : current.getWorld().getNearbyEntities(current, 0.8, 0.8, 0.8)) {
                        if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                        damageManager.dealSkillDamage(player, target, damage);
                        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 0, 150), 1.5f));
                        cancel(); return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * 靈魂汲取 — 對目標造成傷害並回復自身生命
     */
    private boolean executeSoulDrain(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 1.2);
        double leechPercent = talent.getEffectDouble(level, "leechPercent", 0.3);
        double range = talent.getEffectDouble(level, "range", 10);
        double damage = stats.getMagic() * magicScaling;

        LivingEntity target = getTargetEntity(player, range);
        if (target == null) { player.sendMessage("§c未找到目標！"); return false; }

        damageManager.dealSkillDamage(player, target, damage);
        double heal = damage * leechPercent;
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + heal));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7f, 1.2f);
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 180, 0), 1.0f));
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0);
        player.sendMessage("§5[靈魂汲取] §f吸取了 §a" + String.format("%.0f", heal) + " §f生命值");
        return true;
    }

    /**
     * 詛咒標記 — 標記敵人使其受到更多傷害
     */
    private boolean executeCurseMark(Player player, Talent talent, int level, ItemStack item) {
        double range = talent.getEffectDouble(level, "range", 15);
        double duration = talent.getEffectDouble(level, "duration", 8);
        double damageAmp = talent.getEffectDouble(level, "damageAmplify", 0.2);

        LivingEntity target = getTargetEntity(player, range);
        if (target == null) { player.sendMessage("§c未找到目標！"); return false; }

        int durationTicks = (int)(duration * 20);
        target.setMetadata("curse_mark", new org.bukkit.metadata.FixedMetadataValue(plugin, damageAmp));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durationTicks, 0, false, false));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.6f, 0.5f);
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 2.3, 0), 10, 0.2, 0.1, 0.2, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 0, 200), 1.5f));
        player.sendMessage("§5[詛咒標記] §f標記了 " + target.getName() + "§f！受到傷害增加 §c" + (int)(damageAmp * 100) + "%");

        // 定時移除標記
        new BukkitRunnable() {
            @Override public void run() { if (target.isValid()) target.removeMetadata("curse_mark", plugin); }
        }.runTaskLater(plugin, durationTicks);
        return true;
    }

    /**
     * 鮮血獻祭 — 消耗自身HP提升傷害
     */
    private boolean executeBloodSacrifice(Player player, Talent talent, int level, ItemStack item) {
        double hpCostPercent = talent.getEffectDouble(level, "hpCost", 0.2);
        double damageBoost = talent.getEffectDouble(level, "damageBoost", 0.3);
        double duration = talent.getEffectDouble(level, "duration", 10);

        double hpCost = player.getMaxHealth() * hpCostPercent;
        if (player.getHealth() - hpCost < 2.0) { player.sendMessage("§c血量不足！"); return false; }

        player.setHealth(player.getHealth() - hpCost);
        int durationTicks = (int)(duration * 20);
        int strengthLevel = Math.max(0, (int)(damageBoost * 5));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, strengthLevel, false, false, true));

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SLIME_BLOCK_BREAK, 1.0f, 0.5f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0,
                new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
        player.sendMessage("§4[鮮血獻祭] §c以鮮血換取力量！攻擊提升 " + (int)(duration) + " 秒");
        return true;
    }

    /**
     * 暗焰 — 釋放暗黑火焰AoE
     */
    private boolean executeDarkFlame(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 1.8);
        double radius = talent.getEffectDouble(level, "radius", 5);
        double burnDuration = talent.getEffectDouble(level, "burnDuration", 3);
        double damage = stats.getMagic() * magicScaling;

        Location targetLoc = player.getTargetBlock(null, 15).getLocation().add(0, 1, 0);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        player.sendMessage("§4[暗焰] §c釋放暗黑火焰！");

        // 暗焰爆發粒子
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 40) {
                    cancel();
                    return;
                }
                if (ticks % 5 == 0) {
                    for (int i = 0; i < 360; i += 20) {
                        double rad = Math.toRadians(i + ticks * 9);
                        double x = Math.cos(rad) * radius;
                        double z = Math.sin(rad) * radius;
                        Location pLoc = targetLoc.clone().add(x, 0.2, z);
                        pLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 2, 0.1, 0.1, 0.1, 0.02);
                        pLoc.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 0, 120), 1.5f));
                    }
                    // 每波傷害
                    for (org.bukkit.entity.Entity entity : targetLoc.getWorld().getNearbyEntities(targetLoc, radius, 3, radius)) {
                        if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                        damageManager.dealSkillDamage(player, target, damage * 0.25);
                        target.setFireTicks((int)(burnDuration * 20));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * 虛弱詛咒 — AoE降低敵人攻擊力和移動速度
     */
    private boolean executeWeakeningCurse(Player player, Talent talent, int level, ItemStack item) {
        double radius = talent.getEffectDouble(level, "radius", 6);
        double duration = talent.getEffectDouble(level, "duration", 6);
        int weaknessLevel = (int) talent.getEffectDouble(level, "weaknessLevel", 1) - 1;
        int slownessLevel = (int) talent.getEffectDouble(level, "slownessLevel", 1) - 1;

        Location origin = player.getLocation();
        player.getWorld().playSound(origin, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.8f);
        player.sendMessage("§5[虛弱詛咒] §f釋放詛咒波！");

        // 詛咒擴散效果
        new BukkitRunnable() {
            double currentRadius = 0;
            @Override
            public void run() {
                if (currentRadius > radius) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 360; i += 15) {
                    double rad = Math.toRadians(i);
                    double x = Math.cos(rad) * currentRadius;
                    double z = Math.sin(rad) * currentRadius;
                    Location pLoc = origin.clone().add(x, 0.3, z);
                    pLoc.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 0, 90), 1.2f));
                }
                currentRadius += 0.8;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // 對範圍內敵人施加效果
        int durationTicks = (int)(duration * 20);
        for (org.bukkit.entity.Entity entity : origin.getWorld().getNearbyEntities(origin, radius, 3, radius)) {
            if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, durationTicks, weaknessLevel));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, slownessLevel));
            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 0, 120), 1.0f));
        }
        return true;
    }

    /**
     * 靈魂燃燒 — 點燃自身靈魂，持續對周圍造成傷害
     */
    private boolean executeSoulBurn(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 0.8);
        double radius = talent.getEffectDouble(level, "radius", 4);
        double duration = talent.getEffectDouble(level, "duration", 6);
        double damagePerTick = stats.getMagic() * magicScaling * 0.2; // 每0.5秒的傷害

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 0.8f, 0.5f);
        player.sendMessage("§4[靈魂燃燒] §c點燃靈魂！周圍敵人持續受傷！");

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int)(duration * 20);
            @Override
            public void run() {
                if (ticks >= maxTicks || player.isDead()) {
                    cancel();
                    return;
                }
                // 靈魂火焰粒子
                if (ticks % 5 == 0) {
                    Location loc = player.getLocation();
                    for (int i = 0; i < 360; i += 30) {
                        double rad = Math.toRadians(i + ticks * 6);
                        double x = Math.cos(rad) * radius * 0.7;
                        double z = Math.sin(rad) * radius * 0.7;
                        loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(x, 0.5, z), 1, 0, 0.2, 0, 0.01);
                    }
                    loc.getWorld().spawnParticle(Particle.DUST, loc.add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 0, 150), 1.0f));
                }
                // 每10 tick (0.5秒) 造成一次傷害
                if (ticks % 10 == 0) {
                    Location loc = player.getLocation();
                    for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, radius, 3, radius)) {
                        if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                        damageManager.dealSkillDamage(player, target, damagePerTick);
                    }
                    // 每秒消耗少量HP
                    if (ticks % 20 == 0) {
                        double selfDamage = player.getMaxHealth() * 0.03;
                        if (player.getHealth() - selfDamage > 2.0) {
                            player.setHealth(player.getHealth() - selfDamage);
                            player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0);
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * 暗黑護盾 — 消耗HP轉化為吸收護盾
     */
    private boolean executeDarkShield(Player player, Talent talent, int level, ItemStack item) {
        double shieldPercent = talent.getEffectDouble(level, "shieldPercent", 0.15);
        double duration = talent.getEffectDouble(level, "duration", 10);

        double shieldAmount = player.getMaxHealth() * shieldPercent;

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.5f);
        player.sendMessage("§5[暗黑護盾] §f將生命轉化為護盾！獲得 §e" + String.format("%.0f", shieldAmount) + " §f吸收量");

        // 給予吸收效果
        int durationTicks = (int)(duration * 20);
        int absorptionLevel = Math.max(0, (int)(shieldAmount / 4) - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, durationTicks, absorptionLevel, false, false, true));

        // 護盾粒子效果
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= durationTicks || player.isDead()) {
                    cancel();
                    return;
                }
                if (ticks % 10 == 0) {
                    Location loc = player.getLocation().add(0, 1, 0);
                    for (int i = 0; i < 360; i += 45) {
                        double rad = Math.toRadians(i + ticks * 3);
                        double x = Math.cos(rad) * 0.8;
                        double z = Math.sin(rad) * 0.8;
                        loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(x, 0, z), 1, 0, 0, 0, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(50, 0, 80), 1.0f));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * 死亡標記 — 標記敵人，4秒後引爆造成巨額傷害
     */
    private boolean executeDeathMark(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 3.0);
        double range = talent.getEffectDouble(level, "range", 15);
        double delay = talent.getEffectDouble(level, "delay", 4);
        double damage = stats.getMagic() * magicScaling;

        Location start = player.getEyeLocation().clone();
        Vector dir = start.getDirection().normalize();

        player.getWorld().playSound(start, Sound.ENTITY_WITHER_SHOOT, 0.6f, 0.5f);

        // 射線找目標
        for (double d = 0; d < range; d += 0.5) {
            Location point = start.clone().add(dir.clone().multiply(d));
            for (org.bukkit.entity.Entity entity : point.getWorld().getNearbyEntities(point, 1.0, 1.0, 1.0)) {
                if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;

                player.sendMessage("§4[死亡標記] §f標記了 " + target.getName() + "§f！§c" + (int)delay + " §f秒後引爆！");

                // 倒計時粒子
                new BukkitRunnable() {
                    int ticks = 0;
                    final int detonationTick = (int)(delay * 20);
                    @Override
                    public void run() {
                        if (target.isDead()) {
                            cancel();
                            return;
                        }
                        // 標記粒子
                        if (ticks % 5 == 0) {
                            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 2.3, 0), 5, 0.2, 0.1, 0.2, 0,
                                    new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 0, 0), 1.5f));
                            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 2.5, 0), 3, 0.1, 0.05, 0.1, 0,
                                    new Particle.DustOptions(org.bukkit.Color.BLACK, 1.0f));
                        }
                        // 引爆
                        if (ticks >= detonationTick) {
                            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.6f);
                            target.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, target.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
                            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0,
                                    new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 0, 0), 2.0f));
                            damageManager.dealSkillDamage(player, target, damage);
                            player.sendMessage("§4[死亡標記] §f引爆！造成 §c" + String.format("%.0f", damage) + " §f傷害！");
                            cancel();
                            return;
                        }
                        ticks++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
                return true;
            }
        }
        player.sendMessage("§4[死亡標記] §7未命中目標");
        return true;
    }

    /**
     * 虛空爆發 — 巨大AoE爆發，消耗大量HP
     */
    private boolean executeVoidEruption(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 2.5);
        double strengthScaling = talent.getEffectDouble(level, "strengthScaling", 1.5);
        double radius = talent.getEffectDouble(level, "radius", 8);
        double damage = (stats.getMagic() * magicScaling) + (stats.getStrength() * strengthScaling);

        Location origin = player.getLocation();
        player.getWorld().playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.5f);
        player.sendMessage("§4[虛空爆發] §c以生命為代價，引爆虛空之力！");

        // 虛空爆發動畫
        new BukkitRunnable() {
            double r = 0;
            @Override
            public void run() {
                if (r > radius) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 360; i += 10) {
                    double rad = Math.toRadians(i);
                    double x = Math.cos(rad) * r;
                    double z = Math.sin(rad) * r;
                    Location pLoc = origin.clone().add(x, 0.3, z);
                    pLoc.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(20, 0, 40), 2.0f));
                    pLoc.getWorld().spawnParticle(Particle.PORTAL, pLoc, 1, 0, 0.5, 0, 0.5);
                }
                r += 1.0;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // 延遲一點造成傷害讓粒子先擴散
        new BukkitRunnable() {
            @Override
            public void run() {
                origin.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, origin.clone().add(0, 1, 0), 3, 2, 1, 2, 0);
                List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, origin, radius);
                for (LivingEntity target : targets) {
                    damageManager.dealSkillDamage(player, target, damage);
                    target.setVelocity(target.getLocation().toVector().subtract(origin.toVector()).normalize().multiply(1.2).setY(0.6));
                }
                if (!targets.isEmpty()) {
                    player.sendMessage("§4[虛空爆發] §f命中 §c" + targets.size() + " §f個目標，造成 §c" + String.format("%.0f", damage) + " §f傷害！");
                }
            }
        }.runTaskLater(plugin, 10L);
        return true;
    }

    /**
     * 死神領域 — 終極技能：建立死亡領域，持續汲取範圍內所有敵人生命
     */
    private boolean executeReaperDomain(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double magicScaling = talent.getEffectDouble(level, "magicScaling", 1.0);
        double radius = talent.getEffectDouble(level, "radius", 8);
        double duration = talent.getEffectDouble(level, "duration", 10);
        double leechPercent = talent.getEffectDouble(level, "leechPercent", 0.2);
        double damagePerSecond = stats.getMagic() * magicScaling;

        Location origin = player.getLocation().clone();
        player.getWorld().playSound(origin, Sound.ENTITY_WARDEN_ROAR, 0.8f, 0.3f);
        player.sendMessage("§4[死神領域] §c展開死亡領域！所有敵人的生命都將被汲取！");

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int)(duration * 20);
            @Override
            public void run() {
                if (ticks >= maxTicks || player.isDead()) {
                    player.sendMessage("§5[死神領域] §7領域消散");
                    cancel();
                    return;
                }
                // 領域邊界粒子
                if (ticks % 5 == 0) {
                    for (int i = 0; i < 360; i += 10) {
                        double rad = Math.toRadians(i + ticks * 2);
                        double x = Math.cos(rad) * radius;
                        double z = Math.sin(rad) * radius;
                        Location pLoc = origin.clone().add(x, 0.1, z);
                        pLoc.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(30, 0, 50), 1.5f));
                    }
                    // 領域內漂浮粒子
                    origin.getWorld().spawnParticle(Particle.DUST, origin.clone().add(0, 1.5, 0), 15, radius * 0.6, 1, radius * 0.6, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 0, 120), 0.8f));
                    origin.getWorld().spawnParticle(Particle.SOUL, origin.clone().add(0, 0.5, 0), 3, radius * 0.5, 0.5, radius * 0.5, 0.02);
                }
                // 每秒造成傷害和回復
                if (ticks % 20 == 0) {
                    double totalHealed = 0;
                    for (org.bukkit.entity.Entity entity : origin.getWorld().getNearbyEntities(origin, radius, 4, radius)) {
                        if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                        damageManager.dealSkillDamage(player, target, damagePerSecond);
                        double heal = damagePerSecond * leechPercent;
                        totalHealed += heal;
                        // 汲取視覺效果
                        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 180, 0), 0.8f));
                    }
                    if (totalHealed > 0) {
                        double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + totalHealed);
                        player.setHealth(newHealth);
                        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 3, 0.3, 0.3, 0.3, 0);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * 暗黑神化 — 終極技能：變身暗黑形態，所有技能不消耗HP
     */
    private boolean executeDarkApotheosis(Player player, Talent talent, int level, ItemStack item) {
        double duration = talent.getEffectDouble(level, "duration", 10);
        double damageBoost = talent.getEffectDouble(level, "damageBoost", 0.3);
        double speedBoost = talent.getEffectDouble(level, "speedBoost", 1);

        int durationTicks = (int)(duration * 20);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
        player.sendMessage("§4§l[暗黑神化] §c§l化身為暗黑之力的容器！技能不再消耗生命值！");

        // 設定暗黑神化狀態
        player.setMetadata("dark_apotheosis", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

        // 增益效果
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, (int)damageBoost, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, (int)speedBoost, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durationTicks, 0, false, false, true));

        // 暗黑光環粒子
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= durationTicks || player.isDead()) {
                    player.removeMetadata("dark_apotheosis", plugin);
                    player.sendMessage("§5[暗黑神化] §7暗黑之力消退...");
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 0.8f);
                    cancel();
                    return;
                }
                if (ticks % 4 == 0) {
                    Location loc = player.getLocation();
                    // 雙螺旋上升粒子
                    double angle = ticks * 0.3;
                    for (int j = 0; j < 2; j++) {
                        double offsetAngle = angle + (j * Math.PI);
                        double x = Math.cos(offsetAngle) * 0.8;
                        double z = Math.sin(offsetAngle) * 0.8;
                        double y = (ticks % 40) * 0.05;
                        loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(x, y, z), 1, 0, 0, 0, 0,
                                new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 0, 200), 1.3f));
                    }
                    // 腳下暗影
                    loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.1, 0), 3, 0.4, 0, 0.4, 0,
                            new Particle.DustOptions(org.bukkit.Color.BLACK, 2.0f));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ═══════════════════════════════════════════════════════════
    //  科技系技能
    // ═══════════════════════════════════════════════════════════

    private boolean executeShockGun(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double damage = talent.getEffectDouble(level, "baseDamage", 40) + stats.getMagic() * 0.3;
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        player.getWorld().playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.5f);
        player.sendMessage("§b[電擊槍] §f發射電擊！");
        for (double d = 0; d < 15; d += 0.5) {
            Location loc = eye.clone().add(dir.clone().multiply(d));
            loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 2, 0.05, 0.05, 0.05, 0);
            List<LivingEntity> targets = aoeUtil.getRadiusTargets(player, loc, 0.8);
            if (!targets.isEmpty()) {
                damageManager.dealSkillDamage(player, targets.get(0), damage);
                targets.get(0).getWorld().spawnParticle(Particle.ELECTRIC_SPARK, targets.get(0).getLocation().add(0,1,0), 20, 0.3, 0.3, 0.3, 0.1);
                break;
            }
        }
        return true;
    }

    private boolean executeGrenade(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double damage = talent.getEffectDouble(level, "baseDamage", 60) + stats.getMagic() * 0.4;
        double radius = talent.getEffectDouble(level, "radius", 4);
        Location target = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(10));
        target.setY(player.getLocation().getY());

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.5f);
        player.sendMessage("§b[手榴彈] §f投擲手榴彈！");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            target.getWorld().spawnParticle(Particle.EXPLOSION, target, 3, 0.5, 0.5, 0.5, 0);
            target.getWorld().playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
            for (LivingEntity e : aoeUtil.getRadiusTargets(player, target, radius)) {
                damageManager.dealSkillDamage(player, e, damage);
            }
        }, 20L);
        return true;
    }

    private boolean executeMiniTurret(Player player, Talent talent, int level, ItemStack item) {
        double damage = talent.getEffectDouble(level, "baseDamage", 15);
        int duration = (int) talent.getEffectDouble(level, "duration", 10);
        Location loc = player.getLocation().clone();
        player.sendMessage("§b[迷你砲塔] §f部署砲塔！持續 " + duration + " 秒");
        player.getWorld().playSound(loc, Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= duration * 20) { cancel(); return; }
                if (ticks % 20 == 0) {
                    loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1.5, 0), 5, 0.2, 0.2, 0.2, 0);
                    for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, 10, 5, 10)) {
                        if (e instanceof LivingEntity le && !(e instanceof Player) && !le.isDead()) {
                            damageManager.dealSkillDamage(player, le, damage);
                            loc.getWorld().playSound(loc, Sound.ENTITY_ARROW_SHOOT, 0.5f, 2.0f);
                            break;
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean executeEnergyShield(Player player, Talent talent, int level, ItemStack item) {
        double duration = talent.getEffectDouble(level, "duration", 8);
        int absorptionLevel = (int) talent.getEffectDouble(level, "absorptionLevel", 2);
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, (int)(duration * 20), absorptionLevel, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, (int)(duration * 20), 0, false, false, true));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
        player.sendMessage("§b[能量護盾] §f啟動護盾！持續 " + (int)duration + " 秒");
        return true;
    }

    private boolean executeTrackingDrone(Player player, Talent talent, int level, ItemStack item) {
        double damage = talent.getEffectDouble(level, "baseDamage", 20);
        int duration = (int) talent.getEffectDouble(level, "duration", 15);
        player.sendMessage("§b[追蹤無人機] §f部署追蹤無人機！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BEE_LOOP, 0.6f, 2.0f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= duration * 20 || !player.isOnline()) { cancel(); return; }
                if (ticks % 30 == 0) {
                    Location pLoc = player.getLocation().add(0, 3, 0);
                    pLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, pLoc, 3, 0.2, 0.1, 0.2, 0);
                    for (org.bukkit.entity.Entity e : pLoc.getWorld().getNearbyEntities(pLoc, 12, 6, 12)) {
                        if (e instanceof LivingEntity le && !(e instanceof Player) && !le.isDead()) {
                            damageManager.dealSkillDamage(player, le, damage);
                            le.getWorld().spawnParticle(Particle.CRIT, le.getLocation().add(0,1,0), 10, 0.2, 0.2, 0.2, 0.1);
                            break;
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean executeOrbitalStrike(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double damage = talent.getEffectDouble(level, "baseDamage", 150) + stats.getMagic() * 1.0;
        double radius = talent.getEffectDouble(level, "radius", 6);
        Location target = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(15));
        target.setY(player.getLocation().getY());

        player.sendMessage("§b[軌道打擊] §f鎖定目標...3秒後打擊！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);

        final Location impactLoc = target.clone();
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= 60) { cancel(); return; }
                for (int i = 0; i < 24; i++) {
                    double a = Math.toRadians(i * 15);
                    impactLoc.getWorld().spawnParticle(Particle.DUST, impactLoc.clone().add(Math.cos(a)*radius, 0.1, Math.sin(a)*radius), 1, 0, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.AQUA, 1.2f));
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            impactLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, impactLoc.clone().add(0, 1, 0), 3, 1, 1, 1, 0);
            impactLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, impactLoc.clone().add(0, 10, 0), 100, 1, 8, 1, 0.1);
            impactLoc.getWorld().playSound(impactLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.5f);
            for (LivingEntity e : aoeUtil.getRadiusTargets(player, impactLoc, radius)) {
                damageManager.dealSkillDamage(player, e, damage);
            }
        }, 60L);
        return true;
    }

    private boolean executeProximityMine(Player player, Talent talent, int level, ItemStack item) {
        double damage = talent.getEffectDouble(level, "baseDamage", 80);
        double radius = talent.getEffectDouble(level, "radius", 3);
        Location mineLoc = player.getLocation().clone();

        player.sendMessage("§b[感應地雷] §f已部署地雷！");
        player.getWorld().playSound(mineLoc, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 600) { cancel(); return; }
                mineLoc.getWorld().spawnParticle(Particle.DUST, mineLoc.clone().add(0, 0.2, 0), 1, 0, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.RED, 0.5f));
                for (org.bukkit.entity.Entity e : mineLoc.getWorld().getNearbyEntities(mineLoc, radius, 2, radius)) {
                    if (e instanceof LivingEntity le && !(e instanceof Player) && !le.isDead()) {
                        mineLoc.getWorld().spawnParticle(Particle.EXPLOSION, mineLoc, 3, 0.5, 0.5, 0.5, 0);
                        mineLoc.getWorld().playSound(mineLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
                        for (LivingEntity t : aoeUtil.getRadiusTargets(player, mineLoc, radius)) {
                            damageManager.dealSkillDamage(player, t, damage);
                        }
                        cancel();
                        return;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 20L, 5L);
        return true;
    }

    private boolean executeSlowTrap(Player player, Talent talent, int level, ItemStack item) {
        double radius = talent.getEffectDouble(level, "radius", 4);
        int duration = (int) talent.getEffectDouble(level, "duration", 5);
        Location trapLoc = player.getLocation().clone();

        player.sendMessage("§b[減速陷阱] §f已部署陷阱！");
        player.getWorld().playSound(trapLoc, Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.0f, 1.0f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= duration * 20) { cancel(); return; }
                if (ticks % 10 == 0) {
                    trapLoc.getWorld().spawnParticle(Particle.DUST, trapLoc.clone().add(0, 0.1, 0), 5, radius * 0.5, 0, radius * 0.5, 0, new Particle.DustOptions(org.bukkit.Color.BLUE, 0.8f));
                    for (org.bukkit.entity.Entity e : trapLoc.getWorld().getNearbyEntities(trapLoc, radius, 2, radius)) {
                        if (e instanceof LivingEntity le && !(e instanceof Player)) {
                            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, false));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean executeClusterBomb(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double damage = talent.getEffectDouble(level, "baseDamage", 40) + stats.getMagic() * 0.3;
        int bombCount = (int) talent.getEffectDouble(level, "bombCount", 5);
        Location target = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(10));
        target.setY(player.getLocation().getY());

        player.sendMessage("§b[集束炸彈] §f發射集束炸彈！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.8f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < bombCount; i++) {
                Location bombLoc = target.clone().add((Math.random()-0.5)*6, 0, (Math.random()-0.5)*6);
                bombLoc.getWorld().spawnParticle(Particle.EXPLOSION, bombLoc, 1, 0, 0, 0, 0);
                bombLoc.getWorld().spawnParticle(Particle.FLAME, bombLoc, 10, 0.5, 0.5, 0.5, 0.05);
                for (LivingEntity e : aoeUtil.getRadiusTargets(player, bombLoc, 2.5)) {
                    damageManager.dealSkillDamage(player, e, damage);
                }
            }
            target.getWorld().playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
        }, 15L);
        return true;
    }

    private boolean executeAnnihilationBarrage(Player player, Talent talent, int level, ItemStack item) {
        PlayerStats stats = statsManager.getStats(player);
        double damage = talent.getEffectDouble(level, "baseDamage", 30) + stats.getMagic() * 0.5;
        int salvos = (int) talent.getEffectDouble(level, "salvos", 8);
        double radius = talent.getEffectDouble(level, "radius", 10);

        player.sendMessage("§b§l[殲滅彈幕] §f展開全面火力壓制！");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);

        Location center = player.getLocation().clone();
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count >= salvos) { cancel(); return; }
                Location bombLoc = center.clone().add((Math.random()-0.5)*radius*2, 0, (Math.random()-0.5)*radius*2);
                bombLoc.getWorld().spawnParticle(Particle.EXPLOSION, bombLoc, 2, 0.3, 0.3, 0.3, 0);
                bombLoc.getWorld().spawnParticle(Particle.FLAME, bombLoc, 15, 0.5, 1, 0.5, 0.05);
                bombLoc.getWorld().playSound(bombLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                for (LivingEntity e : aoeUtil.getRadiusTargets(player, bombLoc, 3)) {
                    damageManager.dealSkillDamage(player, e, damage);
                }
                count++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
        return true;
    }

    // ═══════════════════════════════════════════════════════════
    //  野獸系技能 — 馴服野獸支線
    // ═══════════════════════════════════════════════════════════

    private boolean executeCallOfWild(Player player, Talent talent, int level, ItemStack item) {
        BeastManager bm = plugin.getBeastManager();
        double baseDmg = talent.getEffectDouble(level, "baseDamage", 5);
        double hp = talent.getEffectDouble(level, "beastHealth", 30);
        int duration = (int) talent.getEffectDouble(level, "duration", 30);

        boolean ok = bm.summonBeast(player, EntityType.WOLF, "戰狼", hp, baseDmg, duration, 1);
        if (ok) {
            player.sendMessage("§6§l[野獸召喚] §a你召喚了一隻 §f戰狼§a！");
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.2f, 1.0f);
        }
        return ok;
    }

    private boolean executePackLeader(Player player, Talent talent, int level, ItemStack item) {
        BeastManager bm = plugin.getBeastManager();
        double baseDmg = talent.getEffectDouble(level, "baseDamage", 8);
        double hp = talent.getEffectDouble(level, "beastHealth", 50);
        int duration = (int) talent.getEffectDouble(level, "duration", 40);
        int count = (int) talent.getEffectDouble(level, "summonCount", 2);

        boolean ok = bm.summonBeast(player, EntityType.WOLF, "精銳戰狼", hp, baseDmg, duration, count);
        if (ok) {
            player.sendMessage("§6§l[狼群首領] §a你召喚了 §f" + count + " §a隻 §f精銳戰狼§a！");
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.5f, 0.8f);
        }
        return ok;
    }

    private boolean executeBeastFrenzy(Player player, Talent talent, int level, ItemStack item) {
        BeastManager bm = plugin.getBeastManager();
        int count = bm.getBeastCount(player);
        if (count == 0) { player.sendMessage("§c你沒有召喚中的野獸！"); return false; }

        double healPercent = talent.getEffectDouble(level, "healPercent", 0.3);
        int buffDuration = (int) talent.getEffectDouble(level, "duration", 15);

        UUID pid = player.getUniqueId();
        for (org.bukkit.entity.Entity e : player.getWorld().getEntities()) {
            if (e instanceof LivingEntity le && e.hasMetadata("beast_owner")) {
                String owner = e.getMetadata("beast_owner").get(0).asString();
                if (owner.equals(pid.toString())) {
                    double maxHp = le.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue();
                    le.setHealth(Math.min(maxHp, le.getHealth() + maxHp * healPercent));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, buffDuration * 20, 1, true, false));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, buffDuration * 20, 1, true, false));
                    le.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, le.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0);
                }
            }
        }
        player.sendMessage("§6§l[野獸狂熱] §c你的野獸進入狂暴狀態！傷害提升 " + buffDuration + " 秒");
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        return true;
    }

    private boolean executeSavageSummon(Player player, Talent talent, int level, ItemStack item) {
        BeastManager bm = plugin.getBeastManager();
        double baseDmg = talent.getEffectDouble(level, "baseDamage", 12);
        double hp = talent.getEffectDouble(level, "beastHealth", 80);
        int duration = (int) talent.getEffectDouble(level, "duration", 45);

        boolean ok = bm.summonBeast(player, EntityType.POLAR_BEAR, "巨熊", hp, baseDmg, duration, 1);
        if (ok) {
            player.sendMessage("§6§l[野獸召喚] §a你召喚了一隻 §f巨熊§a！");
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
        }
        return ok;
    }

    private boolean executeBeastArmy(Player player, Talent talent, int level, ItemStack item) {
        BeastManager bm = plugin.getBeastManager();
        double wolfDmg = talent.getEffectDouble(level, "wolfDamage", 10);
        double wolfHp = talent.getEffectDouble(level, "wolfHealth", 60);
        double bearDmg = talent.getEffectDouble(level, "bearDamage", 15);
        double bearHp = talent.getEffectDouble(level, "bearHealth", 100);
        int duration = (int) talent.getEffectDouble(level, "duration", 50);
        int wolfCount = (int) talent.getEffectDouble(level, "wolfCount", 2);

        boolean ok1 = bm.summonBeast(player, EntityType.WOLF, "精銳戰狼", wolfHp, wolfDmg, duration, wolfCount);
        boolean ok2 = bm.summonBeast(player, EntityType.POLAR_BEAR, "猛獸", bearHp, bearDmg, duration, 1);

        if (ok1 || ok2) {
            player.sendMessage("§6§l[野獸軍團] §a你召喚了一支野獸部隊！");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.5f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.3);
        }
        return ok1 || ok2;
    }

    private boolean executeBeastMaster(Player player, Talent talent, int level, ItemStack item) {
        BeastManager bm = plugin.getBeastManager();
        bm.removeAllBeasts(player);

        double baseDmg = talent.getEffectDouble(level, "baseDamage", 25);
        double hp = talent.getEffectDouble(level, "beastHealth", 200);
        int duration = (int) talent.getEffectDouble(level, "duration", 60);

        boolean ok = bm.summonBeast(player, EntityType.RAVAGER, "§4§l野獸之王", hp, baseDmg, duration, 1);
        if (ok) {
            bm.summonBeast(player, EntityType.WOLF, "護衛狼", 80, 12, duration, 2);
            player.sendMessage("§6§l[野獸之王] §c§l你召喚了 §4§l野獸之王 §c§l及其護衛！");
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.5, 0), 50, 1, 1.5, 1, 0.5);
            player.getWorld().spawnParticle(Particle.LAVA, player.getLocation(), 30, 1, 1, 1, 0);
        }
        return ok;
    }

    // ═══════════════════════════════════════════════════════════
    //  野獸系技能 — 化身野獸支線
    // ═══════════════════════════════════════════════════════════

    private boolean executeWolfForm(Player player, Talent talent, int level, ItemStack item) {
        BeastManager bm = plugin.getBeastManager();
        int duration = (int) talent.getEffectDouble(level, "duration", 20);
        double strengthBonus = talent.getEffectDouble(level, "strengthBonus", 5);
        double healthBonus = talent.getEffectDouble(level, "healthBonus", 10);
        int speedLevel = (int) talent.getEffectDouble(level, "speedLevel", 2);
        return bm.activateBeastForm(player, "wolf", duration, strengthBonus, healthBonus, speedLevel, 0);
    }

    private boolean executeBearForm(Player player, Talent talent, int level, ItemStack item) {
        BeastManager bm = plugin.getBeastManager();
        int duration = (int) talent.getEffectDouble(level, "duration", 25);
        double strengthBonus = talent.getEffectDouble(level, "strengthBonus", 15);
        double healthBonus = talent.getEffectDouble(level, "healthBonus", 40);
        int damageBoost = (int) talent.getEffectDouble(level, "damageBoost", 1);
        return bm.activateBeastForm(player, "polar_bear", duration, strengthBonus, healthBonus, 0, damageBoost);
    }

    private boolean executePredatorDash(Player player, Talent talent, int level, ItemStack item) {
        double dashDmg = talent.getEffectDouble(level, "baseDamage", 15);
        PlayerStats stats = statsManager.getStats(player);
        BeastManager bm = plugin.getBeastManager();
        double bonusMult = bm.isInBeastForm(player) ? 1.5 : 1.0;

        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        player.setVelocity(dir.multiply(1.8).setY(0.3));
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.5f, 1.0f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 10) { cancel(); return; }
                Location loc = player.getLocation();
                loc.getWorld().spawnParticle(Particle.CRIT, loc, 5, 0.3, 0.3, 0.3, 0.1);
                for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, 2, 2, 2)) {
                    if (e instanceof LivingEntity le && e != player && !e.hasMetadata("beast_owner")) {
                        double totalDmg = (dashDmg + stats.getStrength() * 0.5) * bonusMult;
                        damageManager.dealSkillDamage(player, le, totalDmg);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    private boolean executeFoxForm(Player player, Talent talent, int level, ItemStack item) {
        BeastManager bm = plugin.getBeastManager();
        int duration = (int) talent.getEffectDouble(level, "duration", 20);
        double strengthBonus = talent.getEffectDouble(level, "strengthBonus", 8);
        double healthBonus = talent.getEffectDouble(level, "healthBonus", 5);
        int speedLevel = (int) talent.getEffectDouble(level, "speedLevel", 3);

        boolean ok = bm.activateBeastForm(player, "fox", duration, strengthBonus, healthBonus, speedLevel, 0);
        if (ok) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration * 20, 0, true, false));
        }
        return ok;
    }

    private boolean executeApexPredator(Player player, Talent talent, int level, ItemStack item) {
        BeastManager bm = plugin.getBeastManager();
        int duration = (int) talent.getEffectDouble(level, "duration", 30);
        double strengthBonus = talent.getEffectDouble(level, "strengthBonus", 25);
        double healthBonus = talent.getEffectDouble(level, "healthBonus", 50);
        int speedLevel = (int) talent.getEffectDouble(level, "speedLevel", 2);
        int damageBoost = (int) talent.getEffectDouble(level, "damageBoost", 2);
        return bm.activateBeastForm(player, "ravager", duration, strengthBonus, healthBonus, speedLevel, damageBoost);
    }

    private boolean executePrimalWrath(Player player, Talent talent, int level, ItemStack item) {        BeastManager bm = plugin.getBeastManager();
        int duration = (int) talent.getEffectDouble(level, "duration", 35);
        double strengthBonus = talent.getEffectDouble(level, "strengthBonus", 40);
        double healthBonus = talent.getEffectDouble(level, "healthBonus", 80);
        bm.activateBeastForm(player, "apex", duration, strengthBonus, healthBonus, 3, 3);
        bm.summonBeast(player, EntityType.WOLF, "原始戰狼", 100, 15, duration, 2);

        double radius = talent.getEffectDouble(level, "radius", 10);
        int fearDuration = (int) talent.getEffectDouble(level, "fearDuration", 5);

        for (org.bukkit.entity.Entity e : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
            if (e instanceof LivingEntity le && e != player && !e.hasMetadata("beast_owner")) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, fearDuration * 20, 2, true, false));
                le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, fearDuration * 20, 1, true, false));
            }
        }

        player.sendMessage("§6§l[原始怒吼] §c§l你釋放了原始之力！所有敵人陷入恐懼！");
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 3, 1, 1, 1, 0);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 2, 0), 60, 2, 2, 2, 0.5);
        return true;
    }

    // ═══════════════════════════════════════════════════════════
    //  野獸系技能 — 馴服野獸支線 · 終極：蜂巢誓盟
    // ═══════════════════════════════════════════════════════════

    /**
     * 蜂巢誓盟 — 召喚4隻忠誠蜜蜂
     *  · 1隻守護蜂：永遠跟隨玩家，每4秒回血並出現粒子效果
     *  · 3隻攻擊蜂：跟隨玩家攻擊的目標
     *  · 蜜蜂不會因被玩家攻擊而反攻主人
     */
    private boolean executeBeeSwarm(Player player, Talent talent, int level, ItemStack item) {
        BeastManager bm = plugin.getBeastManager();

        double beeHealth       = talent.getEffectDouble(level, "beeHealth",      30);
        double attackBeeDamage = talent.getEffectDouble(level, "attackBeeDamage", 4);
        double healAmount      = talent.getEffectDouble(level, "healAmount",      3);
        int    duration        = (int) talent.getEffectDouble(level, "duration",  60);

        Location spawnBase = player.getLocation().clone();

        // ── 召喚1隻守護蜂 ──
        Bee companion = (Bee) player.getWorld().spawnEntity(
                spawnBase.clone().add(0.5, 1.2, 0.5), EntityType.BEE);
        initBee(companion, player, beeHealth, attackBeeDamage,
                "§e✿ 守護蜂", true);
        bm.registerExistingBeast(player, companion, 0); // 守護蜂傷害為0

        // ── 召喚3隻攻擊蜂 ──
        List<UUID> attackBeeIds = new ArrayList<>();
        double[] offX = {1.2, -1.2, 0};
        double[] offZ = {0,    0,    1.2};
        for (int i = 0; i < 3; i++) {
            Bee atk = (Bee) player.getWorld().spawnEntity(
                    spawnBase.clone().add(offX[i], 1.2, offZ[i]), EntityType.BEE);
            initBee(atk, player, beeHealth, attackBeeDamage,
                    "§c⚔ 攻擊蜂", false);
            bm.registerExistingBeast(player, atk, attackBeeDamage);
            attackBeeIds.add(atk.getUniqueId());
        }

        // 召喚特效
        Location center = spawnBase.clone().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.HEART,          center, 10, 0.6, 0.4, 0.6, 0);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 20, 0.8, 0.6, 0.8, 0);
        player.getWorld().playSound(spawnBase, Sound.ENTITY_BEE_LOOP, 1.2f, 1.0f);
        player.sendMessage("§6§l[蜂巢誓盟] §e召喚了1隻守護蜂與3隻攻擊蜂！");

        // ── 攻擊蜂行為：完全手動控制（繞過蜜蜂自然 AI 的逃跑行為）──
        final double finalAtkDmg = attackBeeDamage;
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = duration * 20;

            @Override
            public void run() {
                if (ticks >= maxTicks || !player.isOnline()) { cancel(); return; }

                for (UUID id : attackBeeIds) {
                    org.bukkit.entity.Entity e = org.bukkit.Bukkit.getEntity(id);
                    if (e == null || e.isDead()) continue;
                    Bee bee = (Bee) e;

                    // 讀目標 metadata
                    LivingEntity target = null;
                    if (bee.hasMetadata("bee_attack_target")) {
                        try {
                            UUID tid = UUID.fromString(
                                    bee.getMetadata("bee_attack_target").get(0).asString());
                            org.bukkit.entity.Entity te = org.bukkit.Bukkit.getEntity(tid);
                            if (te instanceof LivingEntity le && !le.isDead()) {
                                target = le;
                            } else {
                                bee.removeMetadata("bee_attack_target", plugin);
                            }
                        } catch (Exception ignored) {}
                    }

                    if (target == null) continue;

                    // 強制清除逃跑狀態，設定追擊目標
                    bee.setAnger(600);
                    bee.setTarget(target);

                    // 手動飛向目標（覆蓋自然 AI 位移）
                    double dist = bee.getLocation().distance(target.getLocation());
                    if (dist > 1.8) {
                        org.bukkit.util.Vector dir = target.getLocation().clone().add(0, 0.5, 0)
                                .toVector().subtract(bee.getLocation().toVector())
                                .normalize().multiply(Math.min(0.7, dist * 0.25));
                        bee.setVelocity(dir);
                    }

                    // 每 20 ticks (1秒) 對鄰近目標造成手動傷害
                    if (ticks % 20 == 0 && dist <= 2.5) {
                        target.damage(finalAtkDmg, bee);
                        // 攻擊粒子
                        target.getWorld().spawnParticle(Particle.CRIT,
                                target.getLocation().add(0, 1, 0), 4, 0.2, 0.2, 0.2, 0.05);
                        target.getWorld().spawnParticle(Particle.DUST,
                                target.getLocation().add(0, 1.2, 0), 2, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.2f));
                    }
                }

                ticks += 10;
            }
        }.runTaskTimer(plugin, 10L, 10L); // 每0.5秒執行一次，移動更流暢

        // ── 守護蜂行為：跟隨玩家 + 每4秒回血 ──
        final UUID companionId  = companion.getUniqueId();
        final double finalHeal  = healAmount;

        new BukkitRunnable() {
            int seconds     = 0;
            final int maxSec = duration;

            @Override
            public void run() {
                if (seconds >= maxSec || !player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }

                org.bukkit.entity.Entity ent = org.bukkit.Bukkit.getEntity(companionId);
                if (ent == null || ent.isDead()) {
                    cancel();
                    return;
                }

                Bee comp = (Bee) ent;

                // 守護蜂永不攻擊（清除目標即可，EntityTargetEvent 會阻止牠攻擊主人）
                comp.setTarget(null);

                // 跟隨玩家
                double dist = comp.getLocation().distance(player.getLocation());
                if (dist > 12) {
                    // 太遠則瞬移
                    comp.teleport(player.getLocation().clone().add(
                            (Math.random() - 0.5) * 1.5, 1.5, (Math.random() - 0.5) * 1.5));
                } else if (dist > 3) {
                    // 飛向玩家
                    Vector dir = player.getLocation().clone().add(0, 1.2, 0)
                            .toVector().subtract(comp.getLocation().toVector())
                            .normalize().multiply(0.35);
                    comp.setVelocity(dir);
                }

                // 每4秒回血並出現粒子效果
                if (seconds > 0 && seconds % 4 == 0) {
                    // 安全取得最大血量
                    org.bukkit.attribute.AttributeInstance maxHpAttr =
                            player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
                    player.setHealth(Math.min(maxHp, player.getHealth() + finalHeal));

                    // 玩家身上的效果：愛心 + 治癒綠光
                    Location pLoc = player.getLocation().clone().add(0, 1.0, 0);
                    player.getWorld().spawnParticle(Particle.HEART, pLoc,
                            6, 0.4, 0.5, 0.4, 0.02);
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, pLoc,
                            10, 0.4, 0.6, 0.4, 0);

                    // 守護蜂旁邊的愛心
                    Location bLoc = comp.getLocation().clone().add(0, 0.6, 0);
                    comp.getWorld().spawnParticle(Particle.HEART, bLoc,
                            3, 0.2, 0.2, 0.2, 0);

                    player.playSound(player.getLocation(),
                            Sound.ENTITY_BEE_POLLINATE, 0.7f, 1.3f);
                }

                seconds++;
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒執行一次

        // 定時移除所有蜜蜂（由 BeastManager 在 summonBeast 中已處理到期邏輯，
        // 但這裡我們另起一個 timer 確保到期刪除）
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            org.bukkit.entity.Entity ent = org.bukkit.Bukkit.getEntity(companionId);
            if (ent != null && !ent.isDead()) {
                ent.getWorld().spawnParticle(Particle.SMOKE,
                        ent.getLocation().add(0, 0.5, 0), 10, 0.3, 0.5, 0.3, 0.02);
                ent.remove();
            }
        }, duration * 20L);

        return true;
    }

    /**
     * 初始化蜜蜂屬性與 metadata
     */
    private void initBee(Bee bee, Player owner, double health, double damage,
                         String displayName, boolean isCompanion) {
        // 設定血量
        if (bee.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
            bee.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(health);
            bee.setHealth(health);
        }
        // 設定攻擊力
        if (bee.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE) != null) {
            bee.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE)
               .setBaseValue(isCompanion ? 0 : damage);
        }
        bee.setCustomName(displayName);
        bee.setCustomNameVisible(true);
        bee.setRemoveWhenFarAway(false);
        bee.setPersistent(true);
        // 初始清除目標（避免召喚後立刻攻擊）
        bee.setTarget(null);

        if (isCompanion) {
            bee.setMetadata("bee_companion",
                    new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        }
    }

    // ══════════════════════════════════════════════════════
    //  風獵者之道 — 主動技能
    // ══════════════════════════════════════════════════════

    /** 取得 FocusManager 捷徑 */
    private FocusManager fm() {
        return plugin.getFocusManager();
    }

    /**
     * 極息穿心箭：消耗全部專注，射出穿透箭
     */
    private boolean executeCriticalArrow(Player player, Talent talent, int level, ItemStack item) {
        int focusConsumed = fm().consumeAllFocus(player);
        double baseDamage    = talent.getEffectDouble(level, "baseDamage", 15);
        double damagePerFocus= talent.getEffectDouble(level, "damagePerFocus", 4);
        int    piercing      = (int) talent.getEffectDouble(level, "piercing", 3);

        double totalDamage = baseDamage + focusConsumed * damagePerFocus;

        org.bukkit.entity.Arrow arrow = player.getWorld()
                .spawn(player.getEyeLocation(), org.bukkit.entity.Arrow.class);
        arrow.setVelocity(player.getLocation().getDirection().multiply(4.5));
        arrow.setShooter(player);
        arrow.setDamage(totalDamage);
        arrow.setPierceLevel(piercing);
        arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setGlowing(true);
        arrow.setMetadata("bow_critical_arrow", new FixedMetadataValue(plugin, true));

        // 特效
        player.getWorld().spawnParticle(Particle.ENCHANT,
                player.getEyeLocation(), 25, 0.3, 0.3, 0.3, 0.6);
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.5f, 0.6f);
        player.sendMessage("§6§l[極息穿心箭] §e消耗 " + focusConsumed
                + " 層專注 → 傷害 §f" + (int) totalDamage);
        return true;
    }

    /**
     * 箭雨：瞬間射出多支箭矢
     */
    private boolean executeArrowStorm(Player player, Talent talent, int level, ItemStack item) {
        int    count     = (int) talent.getEffectDouble(level, "arrowCount", 3);
        double baseDmg   = talent.getEffectDouble(level, "baseDamage", 6);
        double spread    = talent.getEffectDouble(level, "spread", 0.2);

        // 連擊加成
        int combo = fm().getCombo(player);
        double comboMult = 1.0 + combo * 0.05;

        for (int i = 0; i < count; i++) {
            double ox = (Math.random() - 0.5) * spread * 2;
            double oz = (Math.random() - 0.5) * spread * 2;
            Vector dir = player.getLocation().getDirection()
                    .clone().add(new Vector(ox, 0, oz)).normalize();

            org.bukkit.entity.Arrow a = player.getWorld()
                    .spawn(player.getEyeLocation(), org.bukkit.entity.Arrow.class);
            a.setVelocity(dir.multiply(3.8));
            a.setShooter(player);
            a.setDamage(baseDmg * comboMult);
            a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        }

        player.getWorld().spawnParticle(Particle.CLOUD,
                player.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.08);
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.1f);
        player.sendMessage("§a[箭雨] §f射出 " + count + " 支箭矢！");
        return true;
    }

    /**
     * 分裂箭：射出一箭，命中後分裂（由 BowTalentListener 處理）
     */
    private boolean executeSplitArrow(Player player, Talent talent, int level, ItemStack item) {
        double baseDmg       = talent.getEffectDouble(level, "baseDamage", 8);
        int    splitCount    = (int) talent.getEffectDouble(level, "splitCount", 2);
        double splitDmgRatio = talent.getEffectDouble(level, "splitDamageRatio", 0.70);

        org.bukkit.entity.Arrow arrow = player.getWorld()
                .spawn(player.getEyeLocation(), org.bukkit.entity.Arrow.class);
        arrow.setVelocity(player.getLocation().getDirection().multiply(3.5));
        arrow.setShooter(player);
        arrow.setDamage(baseDmg);
        arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setGlowing(true);
        arrow.setMetadata("bow_split_arrow", new FixedMetadataValue(plugin, true));
        arrow.setMetadata("bow_split_count", new FixedMetadataValue(plugin, (double) splitCount));
        arrow.setMetadata("bow_split_damage", new FixedMetadataValue(plugin, baseDmg * splitDmgRatio));

        player.getWorld().spawnParticle(Particle.CRIT,
                player.getEyeLocation(), 6, 0.1, 0.1, 0.1, 0.15);
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
        player.sendMessage("§a[分裂箭] §f射出！命中後將分裂成 " + splitCount + " 箭！");
        return true;
    }

    /**
     * 龍捲箭風：消耗連擊與專注，連續射出多支箭
     */
    private boolean executeBurstMode(Player player, Talent talent, int level, ItemStack item) {
        int comboConsumed = fm().consumeAllCombo(player);
        fm().consumeAllFocus(player);

        int    arrowCount     = (int) talent.getEffectDouble(level, "arrowCount", 10);
        double dmgPerArrow    = talent.getEffectDouble(level, "damagePerArrow", 5);
        int    durationTicks  = (int) talent.getEffectDouble(level, "durationTicks", 60);

        double finalDmg = dmgPerArrow * (1 + comboConsumed * 0.08);
        int interval = Math.max(2, durationTicks / arrowCount);

        player.sendMessage("§6§l[龍捲箭風] §e消耗 " + comboConsumed
                + " 層連擊，連射 " + arrowCount + " 箭！");

        new BukkitRunnable() {
            int fired = 0;
            @Override
            public void run() {
                if (fired >= arrowCount || !player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                org.bukkit.entity.Arrow a = player.getWorld()
                        .spawn(player.getEyeLocation(), org.bukkit.entity.Arrow.class);
                a.setVelocity(player.getLocation().getDirection().multiply(3.8));
                a.setShooter(player);
                a.setDamage(finalDmg);
                a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                player.getWorld().spawnParticle(Particle.CRIT,
                        player.getEyeLocation(), 2, 0.05, 0.05, 0.05, 0.05);
                player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.3f);
                fired++;
            }
        }.runTaskTimer(plugin, 2L, interval);

        return true;
    }

    /**
     * 追蹤標記：標記玩家視線前方的敵人
     */
    private boolean executeTrackingMark(Player player, Talent talent, int level, ItemStack item) {
        int    markDuration    = (int) talent.getEffectDouble(level, "markDurationTicks", 200);
        double markedDmgBonus  = talent.getEffectDouble(level, "markedDamageBonus", 0.25);
        int    focusGain       = (int) talent.getEffectDouble(level, "focusOnMark", 2);

        // 射線追蹤目標
        var rayResult = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                30, 0.6,
                e -> e instanceof LivingEntity && !(e instanceof Player) && !e.equals(player));

        LivingEntity target = null;
        if (rayResult != null && rayResult.getHitEntity() instanceof LivingEntity le) {
            target = le;
        }

        if (target == null) {
            player.sendMessage("§c[追蹤標記] 視線內沒有目標！");
            return false;
        }

        fm().markTarget(player, target, markDuration);
        fm().addFocus(player, focusGain);

        player.getWorld().spawnParticle(Particle.ENCHANT,
                target.getLocation().add(0, 1.5, 0), 20, 0.3, 0.3, 0.3, 0.4);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        player.sendMessage("§a[追蹤標記] §f已標記目標！傷害+" + (int)(markedDmgBonus * 100)
                + "% +" + focusGain + " 專注");
        return true;
    }

    /**
     * 緩速箭：射出帶緩速效果的特殊箭
     */
    private boolean executeSlowArrow(Player player, Talent talent, int level, ItemStack item) {
        int    slowLv    = (int) talent.getEffectDouble(level, "slowLevel", 1);
        int    slowDur   = (int) talent.getEffectDouble(level, "slowDurationTicks", 60);
        double arrowDmg  = talent.getEffectDouble(level, "slowArrowDamage", 4);

        org.bukkit.entity.Arrow arrow = player.getWorld()
                .spawn(player.getEyeLocation(), org.bukkit.entity.Arrow.class);
        arrow.setVelocity(player.getLocation().getDirection().multiply(3.5));
        arrow.setShooter(player);
        arrow.setDamage(arrowDmg);
        arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setGlowing(true);
        arrow.setMetadata("bow_slow_arrow", new FixedMetadataValue(plugin, true));
        arrow.setMetadata("bow_slow_level", new FixedMetadataValue(plugin, (double) slowLv));
        arrow.setMetadata("bow_slow_duration", new FixedMetadataValue(plugin, (double) slowDur));

        player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                player.getEyeLocation(), 8, 0.1, 0.1, 0.1, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.8f);
        player.sendMessage("§b[緩速箭] §f射出！命中後施加緩速 " + slowLv + " 級！");
        return true;
    }

    /**
     * 羅網陷阱：在腳下放置束縛陷阱
     */
    private boolean executeNetTrap(Player player, Talent talent, int level, ItemStack item) {
        double trapRadius  = talent.getEffectDouble(level, "trapRadius", 1.5);
        int    snareDur    = (int) talent.getEffectDouble(level, "snareDurationTicks", 40);
        int    trapDurSecs = (int) talent.getEffectDouble(level, "trapDurationSecs", 30);

        Location trapLoc = player.getLocation().clone();

        // 用隱形盔甲架作為陷阱標記
        org.bukkit.entity.ArmorStand trap = (org.bukkit.entity.ArmorStand)
                player.getWorld().spawnEntity(trapLoc, org.bukkit.entity.EntityType.ARMOR_STAND);
        trap.setVisible(false);
        trap.setGravity(false);
        trap.setSmall(true);
        trap.setCustomName("§c[羅網陷阱]");
        trap.setMetadata("bow_trap_owner",
                new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        // 陷阱圓圈粒子特效
        for (double ang = 0; ang < 2 * Math.PI; ang += 0.25) {
            Location pp = trapLoc.clone().add(
                    Math.cos(ang) * trapRadius, 0.05, Math.sin(ang) * trapRadius);
            player.getWorld().spawnParticle(Particle.SPIT, pp, 1, 0, 0, 0, 0);
        }
        player.playSound(trapLoc, Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.2f);

        // 陷阱觸發循環
        new BukkitRunnable() {
            int secs = 0;
            final Set<UUID> snaredIds = new java.util.HashSet<>();

            @Override
            public void run() {
                if (secs >= trapDurSecs || trap.isDead()) {
                    trap.remove();
                    cancel();
                    return;
                }
                // 每秒刷新陷阱粒子
                if (secs % 2 == 0) {
                    trapLoc.getWorld().spawnParticle(Particle.SPIT,
                            trapLoc.clone().add(0, 0.15, 0), 5, (float) trapRadius, 0.05f, (float) trapRadius, 0);
                }
                // 偵測進入的怪物
                for (Entity e : trapLoc.getWorld().getNearbyEntities(
                        trapLoc, trapRadius, 1.5, trapRadius)) {
                    if (!(e instanceof LivingEntity le)) continue;
                    if (e instanceof Player) continue;
                    if (snaredIds.contains(e.getUniqueId())) continue;

                    final UUID eid = e.getUniqueId();
                    snaredIds.add(eid);
                    le.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS,
                            snareDur, 10, false, true, true));
                    le.getWorld().spawnParticle(Particle.SPIT,
                            le.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0);
                    player.sendMessage("§a[羅網] §f束縛了 " + le.getType().name());

                    // 延遲後允許再次觸發
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> snaredIds.remove(eid), snareDur + 20L);
                }
                secs++;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        player.sendMessage("§a[羅網陷阱] §f陷阱已放置！持續 " + trapDurSecs + " 秒");
        return true;
    }

    /**
     * 風暴漩渦：消耗全部專注，形成持續吸入傷害的漩渦
     */
    private boolean executeWindTrap(Player player, Talent talent, int level, ItemStack item) {
        int focusConsumed = fm().consumeAllFocus(player);
        double radius      = talent.getEffectDouble(level, "radius", 5);
        double dmgPerTick  = talent.getEffectDouble(level, "damagePerTick", 2);
        int    durationTicks = (int) talent.getEffectDouble(level, "durationTicks", 100);
        double pullForce   = talent.getEffectDouble(level, "pullForce", 0.30);

        Location center = player.getLocation().clone();

        player.sendMessage("§6§l[風暴漩渦] §e消耗 " + focusConsumed
                + " 層專注，形成風暴！持續 " + (durationTicks / 20) + " 秒");
        center.getWorld().playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);

        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (t >= durationTicks) { cancel(); return; }

                // 螺旋粒子效果
                double ang = t * 0.4;
                for (double r = 0.5; r <= radius; r += 1.0) {
                    Location pLoc = center.clone().add(
                            Math.cos(ang + r) * r,
                            0.3 + (t % 20) * 0.03,
                            Math.sin(ang + r) * r);
                    center.getWorld().spawnParticle(Particle.CLOUD, pLoc, 1, 0, 0, 0, 0);
                }

                // 每 10 ticks 拉引並傷害周圍敵人
                if (t % 10 == 0) {
                    for (Entity e : center.getWorld().getNearbyEntities(
                            center, radius, radius, radius)) {
                        if (!(e instanceof LivingEntity le)) continue;
                        if (e instanceof Player) continue;
                        // 拉向中心
                        Vector pull = center.toVector()
                                .subtract(le.getLocation().toVector())
                                .normalize().multiply(pullForce);
                        le.setVelocity(le.getVelocity().add(pull));
                        // 傷害
                        le.damage(dmgPerTick, player);
                        le.getWorld().spawnParticle(Particle.CRIT,
                                le.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0.05);
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }
}



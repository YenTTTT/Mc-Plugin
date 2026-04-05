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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

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
        }

        // 4. 消耗資源與套用冷卻
        if (success) {
            manaManager.consumeMana(player, talent.getManaCost());
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

        // 血液爆發粒子
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
                        target.damage(damage, player);
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
                    // 爆炸效果
                    current.getWorld().spawnParticle(Particle.EXPLOSION, current, 3, 0.5, 0.5, 0.5, 0);
                    current.getWorld().spawnParticle(Particle.LAVA, current, 30, 2, 2, 2, 0);
                    current.getWorld().playSound(current, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
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
                        for (org.bukkit.entity.Entity entity : impactLoc.getWorld().getNearbyEntities(impactLoc, radius, radius, radius)) {
                            if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                            double dist = entity.getLocation().distance(impactLoc);
                            double falloff = 1.0 - (dist / (radius * 1.5));
                            if (falloff < 0.3) falloff = 0.3;
                            target.damage(damage * falloff, player);
                            target.setFireTicks(burnDuration * 20);
                            // 擊飛
                            Vector knockback = target.getLocation().toVector().subtract(impactLoc.toVector()).normalize().multiply(1.5).setY(0.8);
                            target.setVelocity(knockback);
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
            center.getWorld().spawnParticle(Particle.FLAME,
                center.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r),
                1, 0, 0.5, 0, 0.05);
        }

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = duration * 20;

            @Override
            public void run() {
                ticks++;
                if (ticks > maxTicks) {
                    // 結束效果
                    center.getWorld().spawnParticle(Particle.SMOKE, center, 50, radius / 2, 1, radius / 2, 0.05);
                    center.getWorld().playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.5f);
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
                traveled += 1.2;
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
                    center.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.2);
                    center.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.3f);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /**
     * 幻影連斬 — 快速斬擊周圍敵人10次，可移動施放
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
            fillLoc.getWorld().spawnParticle(Particle.SMOKE, fillLoc, 1, 0, 0, 0, 0.02);
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
            startLoc.getWorld().spawnParticle(Particle.SMOKE, startLoc.clone().add(Math.cos(angle) * r, Math.random() * 2, Math.sin(angle) * r), 1, 0, 0, 0, 0.03);
        }

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = duration * 20;

            @Override
            public void run() {
                ticks++;
                if (ticks > maxTicks || !player.isOnline()) {
                    // 結束效果
                    player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.4f, 1.5f);
                    cancel();
                    return;
                }

                Location center = player.getLocation();

                // 旋轉鐮刀粒子
                if (ticks % 2 == 0) {
                    double spinAngle = ticks * 0.3;
                    for (int i = 0; i < 3; i++) {
                        double a = spinAngle + (Math.PI * 2 / 3) * i;
                        for (double d = 1; d <= radius; d += 0.8) {
                            double x = Math.cos(a) * d;
                            double z = Math.sin(a) * d;
                            Location pLoc = center.clone().add(x, 0.5, z);
                            pLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, pLoc, 1, 0, 0, 0, 0);
                            if (d > radius - 1) {
                                pLoc.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                                    new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 0, 80), 1.2f));
                            }
                        }
                    }
                }

                // 每秒造成傷害 + 吸血
                if (ticks % 20 == 0) {
                    double totalDamageDealt = 0;
                    for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, radius, 3, radius)) {
                        if (entity == player || !(entity instanceof LivingEntity target) || target.isDead()) continue;
                        target.damage(damagePerTick, player);
                        totalDamageDealt += damagePerTick;
                        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0);
                    }

                    // 吸血
                    if (totalDamageDealt > 0) {
                        double healAmount = totalDamageDealt * lifestealPercent;
                        double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + healAmount);
                        player.setHealth(newHealth);
                        player.getWorld().spawnParticle(Particle.HEART, center.clone().add(0, 2, 0), 3, 0.3, 0.3, 0.3, 0);
                    }

                    center.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 0.6f);
                }

                // 暗影環境音效
                if (ticks % 40 == 0) {
                    center.getWorld().playSound(center, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.3f, 0.5f);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }
}

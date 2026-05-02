package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.FocusManager;
import com.customrpg.managers.TalentManager;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.Talent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BowTalentListener — 風獵者之道 弓箭天賦事件處理器
 *
 * 負責：
 * 1. 被動增益應用（靜止傷害、距離傷害、爆頭、連擊、標記）
 * 2. 專注/連擊積累
 * 3. 幻影步伐自動閃避
 * 4. 分裂箭命中效果
 * 5. 緩速箭命中效果
 * 6. 獵人本能周期掃描
 */
public class BowTalentListener implements Listener {

    private final CustomRPG plugin;
    private final FocusManager focusManager;
    private final TalentManager talentManager;

    /** 幻影步伐冷卻 Map<PlayerUUID, lastUsedMs> */
    private final Map<UUID, Long> phantomStepCooldowns = new ConcurrentHashMap<>();

    public BowTalentListener(CustomRPG plugin, FocusManager focusManager, TalentManager talentManager) {
        this.plugin = plugin;
        this.focusManager = focusManager;
        this.talentManager = talentManager;
        startPassiveTasks();
    }

    // ══════════════════════════════════════════════════════
    //  1. 箭矢命中事件 — 被動乘數應用
    // ══════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowHitEntity(EntityDamageByEntityEvent event) {
        // 只處理「箭矢射手是玩家」的情況
        if (!(event.getDamager() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // 視覺型箭矢不造成傷害
        if (arrow.hasMetadata("bow_skill_visual")) {
            event.setCancelled(true);
            return;
        }

        PlayerTalents pt = talentManager.getPlayerTalents(player);
        double dmg = event.getDamage();
        double mult = 1.0;

        // ─ 1. 靜止姿態（still_stance）─
        int stillLv = pt.getTalentLevel("still_stance");
        if (stillLv > 0 && focusManager.isStill(player)) {
            Talent t = talentManager.findTalent("still_stance");
            mult += t.getEffectDouble(stillLv, "stillDamageBonus", 0.10);
        }

        // ─ 2. 鷹眼（eagle_eye）：距離加成 ─
        int eagleLv = pt.getTalentLevel("eagle_eye");
        if (eagleLv > 0) {
            Talent t = talentManager.findTalent("eagle_eye");
            double perBlock = t.getEffectDouble(eagleLv, "damagePerBlock", 0.01);
            double maxBonus = t.getEffectDouble(eagleLv, "maxDistanceBonus", 0.30);
            double dist = arrow.getLocation().distance(player.getLocation());
            mult += Math.min(maxBonus, dist * perBlock);
        }

        // ─ 3. 致命瞄準（deadly_aim）：專注加成 ─
        int deadlyLv = pt.getTalentLevel("deadly_aim");
        if (deadlyLv > 0) {
            Talent t = talentManager.findTalent("deadly_aim");
            double perFocus = t.getEffectDouble(deadlyLv, "damagePerFocus", 0.04);
            mult += focusManager.getFocus(player) * perFocus;
        }

        // ─ 4. 連擊強化（combo_shot）+ 急速殺機（rapid_fire）─
        int comboShotLv = pt.getTalentLevel("combo_shot");
        if (comboShotLv > 0) {
            Talent t = talentManager.findTalent("combo_shot");
            double perCombo = t.getEffectDouble(comboShotLv, "damagePerCombo", 0.05);
            int combo = focusManager.getCombo(player);
            mult += combo * perCombo;

            // 急速殺機：連擊滿時觸發倍率
            int rapidLv = pt.getTalentLevel("rapid_fire");
            if (rapidLv > 0 && combo >= FocusManager.MAX_COMBO) {
                Talent rtl = talentManager.findTalent("rapid_fire");
                double bonus = rtl.getEffectDouble(rapidLv, "maxComboBonus", 1.5);
                mult *= bonus;
                focusManager.resetCombo(player); // 消耗連擊
                player.sendMessage("§c§l[急速殺機] §e連擊爆發！");
            }
        }

        // ─ 5. 追蹤標記（tracking_mark）：標記加成 ─
        if (focusManager.isMarked(player, target)) {
            int markLv = pt.getTalentLevel("tracking_mark");
            if (markLv > 0) {
                Talent t = talentManager.findTalent("tracking_mark");
                mult += t.getEffectDouble(markLv, "markedDamageBonus", 0.25);
            }
        }

        // ─ 套用乘數 ─
        if (mult != 1.0) {
            event.setDamage(dmg * mult);
        }

        // ─ 6. 爆頭精通（headshot_mastery）─
        int headshotLv = pt.getTalentLevel("headshot_mastery");
        if (headshotLv > 0) {
            double arrowY  = arrow.getLocation().getY();
            double eyeY    = target.getEyeLocation().getY();
            if (Math.abs(arrowY - eyeY) < 0.35) { // 命中頭部範圍
                Talent t = talentManager.findTalent("headshot_mastery");
                double hsMult = t.getEffectDouble(headshotLv, "headshotMultiplier", 1.5);
                int focusGain = (int) t.getEffectDouble(headshotLv, "focusOnHeadshot", 2);

                event.setDamage(event.getDamage() * hsMult);
                focusManager.addFocus(player, focusGain);

                player.sendMessage("§6[爆頭！] §e+" + focusGain + " 專注");
                target.getWorld().spawnParticle(Particle.CRIT,
                        target.getEyeLocation(), 12, 0.2, 0.2, 0.2, 0.3);
                player.playSound(player.getLocation(),
                        Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.8f);
            }
        }

        // ─ 7. 連擊計數（quick_draw）─
        int quickDrawLv = pt.getTalentLevel("quick_draw");
        if (quickDrawLv > 0) {
            focusManager.addCombo(player);
        }

        // ─ 8. 獵人鎮定：命中獲得 1 專注（靜止時才觸發）─
        int huntersCalmLv = pt.getTalentLevel("hunters_calm");
        if (huntersCalmLv > 0 && focusManager.isStill(player)) {
            focusManager.addFocus(player, 1);
        }
    }

    // ══════════════════════════════════════════════════════
    //  2. 箭矢命中方塊/實體 — 特殊技能箭效果
    // ══════════════════════════════════════════════════════

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;

        // ── 分裂箭 ──
        if (arrow.hasMetadata("bow_split_arrow")) {
            Entity hitEntity = event.getHitEntity();
            if (hitEntity == null) return; // 只在命中實體時分裂

            int splitCount = (int) arrow.getMetadata("bow_split_count").get(0).asDouble();
            double splitDmg = arrow.getMetadata("bow_split_damage").get(0).asDouble();
            Location hitLoc = hitEntity.getLocation().clone();

            for (int i = 0; i < splitCount; i++) {
                double angle = (2 * Math.PI / splitCount) * i;
                Vector dir = new Vector(Math.cos(angle), 0.15, Math.sin(angle))
                        .normalize().multiply(2.2);
                Arrow splitArrow = player.getWorld().spawn(
                        hitLoc.clone().add(0, 0.6, 0), Arrow.class);
                splitArrow.setVelocity(dir);
                splitArrow.setShooter(player);
                splitArrow.setDamage(splitDmg);
                splitArrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            }
            hitLoc.getWorld().spawnParticle(Particle.CRIT,
                    hitLoc.add(0, 1, 0), 18, 0.5, 0.5, 0.5, 0.2);
            player.playSound(hitLoc, Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.3f);
            arrow.remove();
            return;
        }

        // ── 緩速箭 ──
        if (arrow.hasMetadata("bow_slow_arrow")) {
            Entity hitEntity = event.getHitEntity();
            if (!(hitEntity instanceof LivingEntity le)) return;

            int slowLv  = (int) arrow.getMetadata("bow_slow_level").get(0).asDouble();
            int slowDur = (int) arrow.getMetadata("bow_slow_duration").get(0).asDouble();
            le.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, slowDur, slowLv - 1, false, true, true));

            le.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    le.getLocation().add(0, 1, 0), 18, 0.3, 0.5, 0.3, 0);
            le.getWorld().playSound(le.getLocation(),
                    Sound.BLOCK_POWDER_SNOW_PLACE, 1.0f, 1.0f);
        }
    }

    // ══════════════════════════════════════════════════════
    //  3. 幻影步伐（phantom_step）— 受傷時自動閃避
    // ══════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.isCancelled()) return;

        PlayerTalents pt = talentManager.getPlayerTalents(player);
        int psLv = pt.getTalentLevel("phantom_step");
        if (psLv < 1) return;

        UUID pid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = phantomStepCooldowns.get(pid);

        Talent talent = talentManager.findTalent("phantom_step");
        long cdMs = (long) (talent.getEffectDouble(psLv, "cooldownSecs", 20) * 1000);
        if (last != null && now - last < cdMs) return;

        phantomStepCooldowns.put(pid, now);

        double dashForce   = talent.getEffectDouble(psLv, "dashForce", 1.2);
        int    invisTicks  = (int) talent.getEffectDouble(psLv, "invisDurationTicks", 20);
        double dmgReduction= talent.getEffectDouble(psLv, "damageReduction", 0.30);

        // 向後衝刺 (multiply(-1) = 反向)
        Vector dash = player.getLocation().getDirection().clone()
                .multiply(-1).setY(0.4).normalize().multiply(dashForce);
        player.setVelocity(dash);

        // 減免傷害
        event.setDamage(event.getDamage() * (1 - dmgReduction));

        // 短暫隱身
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY, invisTicks, 0, false, false, false));

        // 特效
        player.getWorld().spawnParticle(Particle.CLOUD,
                player.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
        player.playSound(player.getLocation(),
                Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
        player.sendActionBar("§b§l[幻影步伐] §7閃避！");
    }

    // ══════════════════════════════════════════════════════
    //  4. 標記目標死亡時清除標記
    // ══════════════════════════════════════════════════════

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // 如果死亡的實體被某個玩家標記，清除標記
        UUID deadId = event.getEntity().getUniqueId();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (focusManager.isMarked(p, event.getEntity())) {
                event.getEntity().setGlowing(false);
            }
        }
    }

    // ══════════════════════════════════════════════════════
    //  5. 玩家離線清理
    // ══════════════════════════════════════════════════════

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        focusManager.cleanup(event.getPlayer());
        phantomStepCooldowns.remove(event.getPlayer().getUniqueId());
    }

    // ══════════════════════════════════════════════════════
    //  6. 被動週期任務
    // ══════════════════════════════════════════════════════

    private void startPassiveTasks() {
        // ─ 獵人鎮定：靜止時每秒積累專注 ─
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!focusManager.isStill(p)) continue;
                    PlayerTalents pt = talentManager.getPlayerTalents(p);
                    int lv = pt.getTalentLevel("hunters_calm");
                    if (lv < 1) continue;
                    if (focusManager.getFocus(p) >= FocusManager.MAX_FOCUS) continue;
                    Talent t = talentManager.findTalent("hunters_calm");
                    int fps = (int) t.getEffectDouble(lv, "focusPerSecond", 1);
                    focusManager.addFocus(p, fps);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒

        // ─ 獵人本能：每 3 秒掃描周圍敵人並使其發光 ─
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    PlayerTalents pt = talentManager.getPlayerTalents(p);
                    int lv = pt.getTalentLevel("hunters_instinct");
                    if (lv < 1) continue;
                    Talent t = talentManager.findTalent("hunters_instinct");
                    double radius     = t.getEffectDouble(lv, "detectRadius", 20);
                    int    glowTicks  = (int) t.getEffectDouble(lv, "glowDurationTicks", 80);

                    for (Entity e : p.getNearbyEntities(radius, radius / 2, radius)) {
                        if (!(e instanceof LivingEntity le)) continue;
                        if (e instanceof Player) continue;
                        le.setGlowing(true);
                        Bukkit.getScheduler().runTaskLater(plugin,
                                () -> le.setGlowing(false), glowTicks);
                    }
                }
            }
        }.runTaskTimer(plugin, 60L, 60L); // 每 3 秒
    }
}



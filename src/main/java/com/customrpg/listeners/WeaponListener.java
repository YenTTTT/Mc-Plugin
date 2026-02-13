package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.PassiveEffectManager;
import com.customrpg.managers.WeaponManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WeaponListener - Handles custom weapon attack events
 *
 * This listener processes attacks made with custom weapons and applies
 * their special effects such as bonus damage, burning, lightning strikes,
 * or directional damage modifiers.
 *
 * Handles:
 * - Iron Scythe: Extra damage from behind attacks
 * - Fire Sword: Burning effect on hit
 * - Thunder Axe: Random lightning strikes
 */
public class WeaponListener implements Listener {

    private final CustomRPG plugin;
    private final WeaponManager weaponManager;
    private final Random random;
    private final PassiveEffectManager passiveEffectManager;
    private final com.customrpg.managers.PlayerStatsManager statsManager;

    private final Map<UUID, Map<String, Long>> passiveCooldownNotify = new ConcurrentHashMap<>();

    // 這個用來判斷「最後一下是否為玩家造成」
    // （EntityDeathEvent 的 getKiller 在某些情況會是 null，例如環境傷害）

    /**
     * Constructor for WeaponListener
     * 
     * @param plugin        Main plugin instance
     * @param weaponManager WeaponManager instance
     * @param statsManager  PlayerStatsManager instance
     */
    public WeaponListener(CustomRPG plugin, WeaponManager weaponManager,
                         com.customrpg.managers.PlayerStatsManager statsManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
        this.statsManager = statsManager;
        this.random = new Random();
        this.passiveEffectManager = new PassiveEffectManager();
    }

    /**
     * Handle entity damage events for custom weapon effects
     * 
     * @param event EntityDamageByEntityEvent
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        // 取得玩家屬性（無論是否使用自訂武器都需要）
        com.customrpg.players.PlayerStats playerStats = statsManager.getStats(player);

        // 檢查是否為自訂武器
        String weaponKey = weaponManager.getWeaponKey(weapon);
        WeaponManager.WeaponData weaponData = null;

        if (weaponKey != null) {
            weaponData = weaponManager.getWeaponData(weaponKey);
        }

        // === 處理自訂武器的等級需求 ===
        if (weaponData != null && playerStats.getLevel() < weaponData.getMinLevel()) {
            player.sendMessage(ChatColor.RED + "你必須達到等級 " + weaponData.getMinLevel() + " 才能使用此武器！");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            event.setCancelled(true);
            return;
        }

        // === 1) Base damage ===
        double baseDamage = event.getDamage();

        // 如果有自訂武器且設定了基礎傷害，則覆蓋
        if (weaponData != null) {
            double baseDamageOverride = weaponData.getDoubleExtra("base-damage", 0.0);
            if (baseDamageOverride > 0.0) {
                baseDamage = baseDamageOverride;
            }
        }

        // === 1.5) 加入 Strength (物理攻擊) 加成 ===
        // 無論是否使用自訂武器，都套用物理攻擊加成
        double strengthBonus = playerStats.getStrength() * 0.2; // 每點 Strength +0.5 傷害
        baseDamage += strengthBonus;

        // === 2) Damage multiplier ===
        double damageMultiplier = 1.0;
        if (weaponData != null) {
            damageMultiplier = weaponData.getDamageMultiplier();
        }
        double damageAfterMultiplier = baseDamage * damageMultiplier;

        // === 3) Crit ===
        double critChancePercent = 0.0;
        double critDamageMultiplier = 1.0;

        // 從自訂武器取得暴擊屬性
        if (weaponData != null) {
            critChancePercent = weaponData.getDoubleExtra("crit-chance", 0.0);
            critDamageMultiplier = weaponData.getDoubleExtra("crit-damage-multiplier", 0.0);
        }

        // 套用「被動增益」的暴擊率加成
        double bonusCrit = passiveEffectManager.getBonusCritChancePercent(player);
        if (bonusCrit > 0.0) {
            critChancePercent += bonusCrit;
        }

        // === 3.5) 加入 Agility (敏捷) 暴擊率加成 ===
        // 無論是否使用自訂武器，都套用敏捷暴擊加成
        double agilityBonus = playerStats.getAgility() * 0.2; // 每點 Agility +0.2% 暴擊率
        critChancePercent += agilityBonus;

        // 防呆：暴擊率上限 100%
        critChancePercent = Math.max(0.0, Math.min(100.0, critChancePercent));

        boolean isCrit = false;
        if (critChancePercent > 0.0 && critDamageMultiplier > 1.0) {
            double roll = random.nextDouble() * 100.0;
            if (roll < critChancePercent) {
                damageAfterMultiplier *= critDamageMultiplier;
                isCrit = true;
            }
        }

        event.setDamage(damageAfterMultiplier);

        if (isCrit) {
            player.sendMessage(ChatColor.YELLOW + "✨ 暴擊！x" + critDamageMultiplier);
        }

        // === 4) 額外擊退推力（僅限自訂武器） ===
        if (weaponData != null) {
            double extraKnockback = weaponData.getDoubleExtra("knockback", 0.0);
            if (extraKnockback > 0 && event.getEntity() instanceof LivingEntity) {
                Vector dir = event.getEntity().getLocation().toVector().subtract(player.getLocation().toVector())
                        .normalize();
                Vector kb = dir.multiply(extraKnockback * 0.2); // 0.2: 避免太誇張
                kb.setY(Math.min(0.4, kb.getY() + 0.1));
                event.getEntity().setVelocity(event.getEntity().getVelocity().add(kb));
            }

            // === 5) 特殊效果（僅限自訂武器） ===
            applySpecialEffect(player, event.getEntity(), weaponData, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        String weaponKey = weaponManager.getWeaponKey(item);
        if (weaponKey == null) {
            return;
        }

        WeaponManager.WeaponData weaponData = weaponManager.getWeaponData(weaponKey);
        if (weaponData == null) {
            return;
        }

        double mult = weaponData.getDoubleExtra("durability-cost-multiplier", 1.0);
        if (mult <= 0) {
            event.setCancelled(true);
            return;
        }

        int newDamage = (int) Math.round(event.getDamage() * mult);
        event.setDamage(Math.max(0, newDamage));
    }

    /**
     * Apply special weapon effects based on weapon type
     * 
     * @param attacker   The attacking player
     * @param victim     The victim entity
     * @param weaponData The weapon data
     * @param event      The damage event (needed for Armor Pierce)
     */
    private void applySpecialEffect(Player attacker, org.bukkit.entity.Entity victim,
            WeaponManager.WeaponData weaponData, EntityDamageByEntityEvent event) {
        // 1. Mechanics
        // Backstab
        if (weaponData.getBooleanExtra("backstab-enabled", false)) {
            applyBackstabEffect(attacker, victim, weaponData);
        }

        // Armor Pierce
        double pierce = weaponData.getDoubleExtra("armor-pierce", 0.0);
        if (pierce > 0.0 && event != null) {
            applyArmorPierce(attacker, victim, pierce, event);
        }

        // Life Steal
        double lifeSteal = weaponData.getDoubleExtra("life-steal", 0.0);
        if (lifeSteal > 0.0 && event != null) {
            applyLifeSteal(attacker, lifeSteal, event.getFinalDamage());
        }

        // AOE (Not fully implemented in plan, but good to have placeholder or simple
        // logic)
        // double aoe = weaponData.getDoubleExtra("aoe-radius", 0.0);

        // 2. Elements
        String elementType = String.valueOf(weaponData.getExtra().getOrDefault("element-type", "NONE")).toUpperCase();
        if (!elementType.equals("NONE")) {
            applyElementEffect(attacker, victim, elementType, weaponData);
        }
    }

    /**
     * Apply armor pierce effect
     * Note: Uses deprecated DamageModifier API. Will need update in future Paper versions.
     */
    @SuppressWarnings("deprecation")
    private void applyArmorPierce(Player attacker, org.bukkit.entity.Entity victim, double piercePercent,
            EntityDamageByEntityEvent event) {
        if (piercePercent > 100.0)
            piercePercent = 100.0;
        if (piercePercent <= 0.0)
            return;

        // Try to reduce the armor reduction
        if (event.isApplicable(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.ARMOR)) {
            double armorReduction = event.getDamage(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.ARMOR); // Usually
                                                                                                                     // negative
            // If armor reduction is -10, and pierce is 20% (0.2), we want new reduction to
            // be -8.
            // new = old * (1 - 0.2) = -10 * 0.8 = -8.
            double newReduction = armorReduction * (1.0 - (piercePercent / 100.0));
            event.setDamage(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.ARMOR, newReduction);
        }
    }

    /**
     * Apply life steal effect
     * Note: Uses deprecated getMaxHealth() API. Will need update in future Paper versions.
     */
    @SuppressWarnings("deprecation")
    private void applyLifeSteal(Player attacker, double percent, double damageDealt) {
        if (damageDealt <= 0)
            return;
        double heal = damageDealt * (percent / 100.0);
        double maxHealth = attacker.getMaxHealth();
        attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + heal));
    }

    private void applyElementEffect(Player attacker, org.bukkit.entity.Entity victim, String elementType,
            WeaponManager.WeaponData weaponData) {
        switch (elementType) {
            case "FIRE":
            case "BURN":
                applyBurnEffect(attacker, victim, weaponData);
                break;
            case "LIGHTNING":
            case "THUNDER":
                applyLightningEffect(attacker, victim, weaponData);
                break;
            // Add ICE, POISON later
            default:
                break;
        }
    }

    /**
     * Apply backstab effect (extra damage from behind)
     * 
     * @param attacker   The attacking player
     * @param victim     The victim entity
     * @param weaponData The weapon data
     */
    private void applyBackstabEffect(Player attacker, org.bukkit.entity.Entity victim,
            WeaponManager.WeaponData weaponData) {
        if (!(victim instanceof LivingEntity)) {
            return;
        }

        boolean backstabEnabled = weaponData.getBooleanExtra("backstab-enabled", true);
        if (!backstabEnabled) {
            return;
        }

        // 計算玩家跟敵對玩家的面對方向
        LivingEntity livingVictim = (LivingEntity) victim;

        Vector attackerDirection = attacker.getLocation().getDirection().normalize();
        Vector victimDirection = victim.getLocation().getDirection().normalize();

        double dotProduct = attackerDirection.dot(victimDirection);

        // dotProduct > 0.5 是完全背對，改成0.3讓背刺比較好觸發
        if (dotProduct > 0.3) {
            double multiplier = weaponData.getDoubleExtra("backstab-multiplier", 1.0);
            double bonusDamage = 4.0 * Math.max(0.0, multiplier);
            livingVictim.damage(bonusDamage);

            // 視覺：直接依 yml 內容決定（目前只做 enchanted_hit）
            String particleName = String.valueOf(weaponData.getExtra().getOrDefault("backstab-particle", ""));
            if (particleName != null && particleName.equalsIgnoreCase("enchanted_hit")) {
                victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1.0, 0), 20, 0.3, 0.6,
                        0.3, 0.0);
            }

            // 音效：直接用 yml 提供的 sound key 字串播放
            String soundKey = String.valueOf(weaponData.getExtra().getOrDefault("backstab-sound", ""));
            if (soundKey != null && !soundKey.isBlank()) {
                attacker.getWorld().playSound(victim.getLocation(), soundKey.trim().toLowerCase(), 1.0f, 1.0f);
            } else {
                attacker.getWorld().playSound(victim.getLocation(), "entity.player.attack.crit", 1.0f, 0.8f);
            }

            attacker.sendMessage(ChatColor.RED + "✦ 背刺! +" + bonusDamage + " 額外傷害!");
        }
    }

    /**
     * Apply burn/fire effect (sets target on fire)
     * 
     * @param attacker   The attacking player
     * @param victim     The victim entity
     * @param weaponData The weapon data
     */
    private void applyBurnEffect(Player attacker, org.bukkit.entity.Entity victim,
            WeaponManager.WeaponData weaponData) {
        if (!(victim instanceof LivingEntity)) {
            return;
        }

        int durationTicks = weaponData.getIntExtra("burn-duration-ticks", 100);
        victim.setFireTicks(Math.max(0, durationTicks));

        attacker.sendMessage(ChatColor.GOLD + "🔥 目標燃燒中! (" + durationTicks + " ticks)\n");
        attacker.getWorld().playSound(attacker.getLocation(), "entity.blaze.shoot", 1.0f, 1.0f);
    }

    /**
     * Apply lightning effect (random lightning strike)
     * 
     * @param attacker   The attacking player
     * @param victim     The victim entity
     * @param weaponData The weapon data
     */
    private void applyLightningEffect(Player attacker, org.bukkit.entity.Entity victim,
            WeaponManager.WeaponData weaponData) {
        double chance = weaponData.getDoubleExtra("lightning-chance", 0.3);
        if (random.nextDouble() < chance) {
            Location strikeLocation = victim.getLocation();
            victim.getWorld().strikeLightning(strikeLocation);
            attacker.sendMessage(ChatColor.AQUA + "⚡ 召喚閃電! (" + (int) (chance * 100) + "%)");
        }
    }

    /**
     * 被動效果（測試）：用自訂武器擊殺生物時，獲得暴擊率 +50%
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        // 避免 PVP（玩家死亡）干擾；你要支援 PVP 再加 PlayerDeathEvent
        if (event.getEntity() instanceof Player) {
            return;
        }

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        String weaponKey = weaponManager.getWeaponKey(weapon);
        if (weaponKey == null) {
            return;
        }

        WeaponManager.WeaponData weaponData = weaponManager.getWeaponData(weaponKey);
        if (weaponData == null) {
            return;
        }

        // 讀取被動設定
        String passiveEffect = String.valueOf(weaponData.getExtra().getOrDefault("passive-effect", ""));
        if (passiveEffect == null || passiveEffect.isBlank()) {
            return;
        }

        String passiveName = String.valueOf(weaponData.getExtra().getOrDefault("passive-name", ""));
        if (passiveName == null || passiveName.isBlank()) {
            passiveName = passiveEffect;
        }

        // 只先做這個測試效果
        if (!passiveEffect.equalsIgnoreCase("kill_crit_boost")) {
            return;
        }

        // 不同武器/不同被動各自獨立冷卻
        String cooldownKey = buildPassiveCooldownKey(weaponKey, passiveEffect);

        // 冷卻（ticks）
        int cooldownTicks = weaponData.getIntExtra("passive-cooldown-ticks", 0);
        if (cooldownTicks > 0 && passiveEffectManager.isOnCooldown(killer, cooldownKey)) {
            if (shouldNotifyCooldown(killer, cooldownKey)) {
                int remainingTicks = passiveEffectManager.getRemainingCooldownTicks(killer, cooldownKey);
                double remainingSeconds = remainingTicks / 20.0;
                killer.sendMessage(ChatColor.RED + "【" + passiveName + "】 冷卻中：剩餘 "
                        + String.format(Locale.ROOT, "%.1f", remainingSeconds) + " 秒");
            }
            return;
        }

        double value = weaponData.getDoubleExtra("passive-value", 0.0);
        int durationTicks = weaponData.getIntExtra("passive-duration-ticks", 200);

        // chance：支援 0~1 或 0~100
        double chance = weaponData.getDoubleExtra("passive-chance", 1.0);
        if (chance > 1.0) {
            chance = chance / 100.0;
        }
        chance = Math.max(0.0, Math.min(1.0, chance));

        if (random.nextDouble() > chance) {
            return;
        }

        // 套用 buff
        passiveEffectManager.applyKillCritBoost(killer, value, durationTicks);
        if (cooldownTicks > 0) {
            passiveEffectManager.startCooldown(killer, cooldownKey, cooldownTicks);
        }

        killer.sendMessage(ChatColor.GREEN + "[" + passiveName + "] 觸發：暴擊率 +" + value + "% (" + durationTicks + "ticks)"
                + (cooldownTicks > 0 ? (" CD " + cooldownTicks + "ticks") : ""));
    }

    private String buildPassiveCooldownKey(String weaponKey, String passiveEffect) {
        String w = weaponKey == null ? "" : weaponKey.trim().toLowerCase();
        String p = passiveEffect == null ? "" : passiveEffect.trim().toLowerCase();
        if (w.isEmpty()) {
            return p;
        }
        if (p.isEmpty()) {
            return w;
        }
        return w + ":" + p;
    }

    private boolean shouldNotifyCooldown(Player player, String passiveKey) {
        long now = System.currentTimeMillis();
        java.util.Map<String, Long> perPlayer = passiveCooldownNotify.computeIfAbsent(player.getUniqueId(),
                k -> new java.util.concurrent.ConcurrentHashMap<>());
        String key = passiveKey == null ? "" : passiveKey.trim().toLowerCase();
        long last = perPlayer.getOrDefault(key, 0L);
        if (now - last < 1000L) {
            return false;
        }
        perPlayer.put(key, now);
        return true;
    }
}

package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.PassiveEffectManager;
import com.customrpg.talents.Talent;
import com.customrpg.talents.TalentType;
import com.customrpg.managers.WeaponManager;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Iterator;
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
    // 法杖普通攻擊冷卻追蹤 (UUID -> 上次攻擊時間)
    private final Map<UUID, Long> staffAttackCooldown = new ConcurrentHashMap<>();
    private static final long STAFF_ATTACK_COOLDOWN_MS = 1000L; // 1 秒冷卻

    // ── 火焰疊層灼燒 DOT 系統 ──
    private final Map<UUID, Integer> burnStacks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> burnLastHit = new ConcurrentHashMap<>();
    private static final int BURN_MAX_STACKS_DEFAULT = 10;
    private static final double BURN_DAMAGE_PER_STACK = 1.5; // 每層每秒傷害
    private static final long BURN_STACK_DECAY_MS = 3000L;   // 3秒不被命中才開始消退

    /**
     * 防止 AoE 傷害遞迴的守衛 Set。
     * 當玩家正在傳播 AoE 傷害時，其 UUID 會被加入此 Set。
     * onEntityDamage 偵測到 UUID 在 Set 中時直接返回，避免無限觸發。
     */
    private final java.util.Set<UUID> aoeGuard =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

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
        startBurnDotTask();
    }

    /** 啟動火焰 DOT 定時器（每秒跑一次） */
    private void startBurnDotTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (burnStacks.isEmpty()) return;
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Integer>> iter = burnStacks.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<UUID, Integer> entry = iter.next();
                    UUID entityId = entry.getKey();
                    int stacks = entry.getValue();

                    // 找到實體
                    org.bukkit.entity.Entity entity = null;
                    for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
                        entity = w.getEntity(entityId);
                        if (entity != null) break;
                    }
                    if (entity == null || !(entity instanceof LivingEntity le) || le.isDead()) {
                        iter.remove();
                        burnLastHit.remove(entityId);
                        continue;
                    }

                    // 扣血
                    double dotDmg = stacks * BURN_DAMAGE_PER_STACK;
                    le.damage(dotDmg);
                    le.getWorld().spawnParticle(Particle.FLAME,
                            le.getLocation().add(0, 0.5, 0), Math.min(stacks * 3, 30), 0.3, 0.6, 0.3, 0.03);

                    // 如果 3 秒內未被命中則消退一層
                    long lastHit = burnLastHit.getOrDefault(entityId, 0L);
                    if (now - lastHit > BURN_STACK_DECAY_MS) {
                        int newStacks = stacks - 1;
                        if (newStacks <= 0) {
                            iter.remove();
                            burnLastHit.remove(entityId);
                        } else {
                            entry.setValue(newStacks);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
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

        // ★ AoE 防遞迴守衛：若此玩家正在傳播 AoE 傷害，直接放行原始傷害值，不再疊加任何武器效果
        if (aoeGuard.contains(player.getUniqueId())) {
            return;
        }
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

        // === 1.5) 屬性加成 (基於武器類別的屬性縮放) ===
        double statBonus;
        if (weaponData != null) {
            // 自訂武器：使用武器類別的屬性加成
            statBonus = weaponData.getCategory().calculateStatBonus(playerStats);
            // 稀有度加成
            statBonus *= weaponData.getRarity().statMultiplier;
        } else {
            // 非自訂武器：使用基礎力量加成
            statBonus = playerStats.getTotalStrength() * 0.2;
        }
        baseDamage += statBonus;

        // === 1.6) 加入天賦武器加成 (Talent Weapon Mastery) ===
        baseDamage += calculateTalentWeaponBonus(player, weapon, playerStats);

        // === 1.7) 處理天賦被動觸發 (例如: 疾行者) ===
        baseDamage = handleAttackPassiveTalents(player, baseDamage, event);

        // === 1.8) 種族武器類型傷害加成 ===
        com.customrpg.races.RaceManager raceManager = plugin.getRaceManager();
        if (raceManager != null && raceManager.hasRace(player)) {
            // 取得武器類別名稱
            String weaponTypeName;
            if (weaponData != null) {
                weaponTypeName = weaponData.getCategory().name();
            } else {
                // 非自訂武器：根據 Material 偵測
                weaponTypeName = WeaponManager.WeaponCategory.detectFromMaterial(weapon.getType(), false).name();
            }

            // 種族對此武器類型的傷害加成
            double raceWeaponBonus = raceManager.getWeaponDamageBonus(player, weaponTypeName);
            baseDamage *= raceWeaponBonus;
        }

        // === 2) Damage multiplier (含稀有度加成) ===
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

        // === 3.6) 種族被動暴擊率加成 ===
        if (raceManager != null && raceManager.hasRace(player)) {
            com.customrpg.races.RaceData raceData = raceManager.getPlayerRaceData(player);
            if (raceData != null) {
                critChancePercent += raceData.getBonusCritChance();
                if (raceData.getBonusCritDamage() > 0 && critDamageMultiplier > 1.0) {
                    critDamageMultiplier += raceData.getBonusCritDamage();
                }
            }
        }

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

    private double handleAttackPassiveTalents(Player player, double currentDamage, EntityDamageByEntityEvent event) {
        com.customrpg.managers.TalentManager tm = plugin.getTalentManager();
        if (tm == null) return currentDamage;

        com.customrpg.players.PlayerTalents pt = tm.getPlayerTalents(player);
        double finalDamage = currentDamage;

        // 1. 疾行者 (Swift Striker)
        int swiftStrikerLv = pt.getTalentLevel("swift_striker");
        if (swiftStrikerLv > 0) {
            String cdKey = "talent_proc:swift_striker:" + player.getUniqueId();
            // 這裡我們暫時使用一個簡單的冷卻檢查，或者可以在 TalentSkillManager 中暴露冷卻器
            // 為了簡化且不依賴其他管理器，我們檢查是否有加速效果作為簡單冷卻，或者直接執行
            
            // 根據需求：15%機率觸發
            if (new Random().nextDouble() < 0.15) {
                // 檢查 Mana (消耗 1 MANA)
                com.customrpg.managers.ManaManager mm = plugin.getManaManager();
                if (mm != null && mm.consumeMana(player, 1.0)) {
                    // 效果：加速 II 6秒
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 120, 1));
                    
                    // 傷害加成：4.3倍 (注意：這是對當前基礎傷害的加成)
                    finalDamage *= 4.3;
                    
                    player.sendMessage("§b§l[天賦] §f觸發了 §e疾行者§f！ (4.3倍傷害 & 加速)");
                    player.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, player.getLocation(), 20, 0.5, 1, 0.5, 0.1);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.5f);
                }
            }
        }

        return finalDamage;
    }

    private double calculateTalentWeaponBonus(Player player, ItemStack weapon, com.customrpg.players.PlayerStats stats) {
        com.customrpg.managers.TalentManager tm = plugin.getTalentManager();
        if (tm == null) return 0;
        
        com.customrpg.players.PlayerTalents pt = tm.getPlayerTalents(player);
        double bonus = 0;
        
        Material mat = weapon.getType();
        String weaponKey = weaponManager.getWeaponKey(weapon);
        WeaponManager.WeaponData weaponData = weaponKey != null ? weaponManager.getWeaponData(weaponKey) : null;

        boolean isTwoHanded = false;
        if (weaponData != null) {
            isTwoHanded = weaponData.getBooleanExtra("two-handed", false);
        }

        boolean isSword = mat.name().contains("SWORD");
        boolean isAxe = mat.name().contains("AXE");
        boolean isScythe = mat.name().contains("HOE");
        boolean isMelee = isSword || isAxe || isScythe;
        boolean isFirearm = mat == Material.IRON_HORSE_ARMOR || mat == Material.GOLDEN_HORSE_ARMOR || mat == Material.DIAMOND_HORSE_ARMOR;
        boolean isWand = mat == Material.BLAZE_ROD || mat == Material.STICK || mat == Material.ENCHANTED_BOOK;
        
        for (Map.Entry<String, Integer> entry : pt.getTalentLevels().entrySet()) {
            if (entry.getValue() <= 0) continue;
            Talent t = tm.findTalent(entry.getKey());
            if (t == null || t.getType() != com.customrpg.talents.TalentType.WEAPON_PASSIVE) continue;
            
            Talent.TalentLevelData data = t.getLevelData(entry.getValue());
            if (data == null) continue;
            
            String tid = t.getId();

            // 冷兵器 (涵蓋所有近戰武器)
            if (tid.equals("weapon_melee_mastery") && isMelee) {
                bonus += data.effects.getOrDefault("weaponDamageBonus", 0.0);
            }
            // 熱兵器加成
            else if (tid.equals("weapon_firearms_mastery") && isFirearm) {
                bonus += data.effects.getOrDefault("weaponDamageBonus", 0.0);
                if (data.scaling.containsKey("AGILITY")) {
                    bonus += stats.getAgility() * data.scaling.get("AGILITY");
                }
            }
            // 魔法兵器加成
            else if (tid.equals("weapon_magic_mastery") && isWand) {
                bonus += data.effects.getOrDefault("weaponDamageBonus", 0.0);
                if (data.scaling.containsKey("MAGIC")) {
                    bonus += stats.getMagic() * data.scaling.get("MAGIC");
                }
            }
            // 無限箭制 (實體箭矢)
            else if (tid.equals("infinite_arrows") && mat == Material.BOW) {
                bonus += data.effects.getOrDefault("weaponDamageBonus", 0.0);
            }
        }
        return bonus;
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
        // Backstab (position-based OR chance-based for scythe)
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
        if (lifeSteal > 0.0) {
            applyLifeSteal(attacker, lifeSteal, event.getFinalDamage());
        }

        // 新：近戰 AoE（單手劍 / 雙手長棍）
        if (weaponData.getBooleanExtra("melee-aoe-enabled", false)) {
            applyMeleeAoE(attacker, victim, weaponData, event.getFinalDamage());
        }

        // 新：流血效果（斧類）
        if (weaponData.getBooleanExtra("bleed-enabled", false)) {
            applyBleedEffect(attacker, victim, weaponData);
        }

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
            case "ICE":
            case "FREEZE":
                applyIceEffect(attacker, victim, weaponData);
                break;
            case "WATER":
                applyWaterEffect(attacker, victim, weaponData);
                break;
            case "POISON":
                applyPoisonEffect(attacker, victim, weaponData);
                break;
            default:
                break;
        }
    }

    /**
     * Apply backstab effect.
     * 支援兩種模式：
     * - 位置型（預設）：攻擊者在目標背後（dotProduct > 0.3）時觸發
     * - 機率型（backstab-any-direction: true）：每次攻擊有 backstab-chance 機率觸發（鐮刀）
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

        LivingEntity livingVictim = (LivingEntity) victim;
        boolean anyDirection = weaponData.getBooleanExtra("backstab-any-direction", false);
        boolean shouldTrigger;

        if (anyDirection) {
            // 機率型背刺（鐮刀）：忽略方向，每次攻擊有機率觸發
            double chance = weaponData.getDoubleExtra("backstab-chance", 0.3);
            shouldTrigger = random.nextDouble() < chance;
        } else {
            // 位置型背刺：判斷攻擊者是否在目標背後
            Vector attackerDirection = attacker.getLocation().getDirection().normalize();
            Vector victimDirection = victim.getLocation().getDirection().normalize();
            double dotProduct = attackerDirection.dot(victimDirection);
            shouldTrigger = dotProduct > 0.3;
        }

        if (shouldTrigger) {
            double multiplier = weaponData.getDoubleExtra("backstab-multiplier", 1.0);
            double bonusDamage = 4.0 * Math.max(0.0, multiplier);
            livingVictim.damage(bonusDamage);

            // 視覺
            String particleName = String.valueOf(weaponData.getExtra().getOrDefault("backstab-particle", ""));
            if (particleName != null && particleName.equalsIgnoreCase("enchanted_hit")) {
                victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1.0, 0), 20, 0.3,
                        0.6, 0.3, 0.0);
            } else {
                // 預設粒子
                victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1.0, 0), 10, 0.2,
                        0.5, 0.2, 0.0);
            }

            // 音效
            String soundKey = String.valueOf(weaponData.getExtra().getOrDefault("backstab-sound", ""));
            if (soundKey != null && !soundKey.isBlank()) {
                attacker.getWorld().playSound(victim.getLocation(), soundKey.trim().toLowerCase(), 1.0f, 1.0f);
            } else {
                attacker.getWorld().playSound(victim.getLocation(), "entity.player.attack.crit", 1.0f, 0.8f);
            }

            if (anyDirection) {
                attacker.sendMessage(ChatColor.LIGHT_PURPLE + "✦ 背刺觸發！ +" + String.format("%.1f", bonusDamage) + " 額外傷害！");
            } else {
                attacker.sendMessage(ChatColor.RED + "✦ 背刺! +" + String.format("%.1f", bonusDamage) + " 額外傷害!");
            }
        }
    }

    /**
     * apply melee AoE splash damage to nearby entities (excluding primary target).
     * Used by: SWORD (小範圍), TWO_HAND_STAFF (大範圍穩定)
     *
     * ★ 使用 aoeGuard 防止 AoE 傷害遞迴觸發（target.damage 會再次觸發 EntityDamageByEntityEvent）
     */
    private void applyMeleeAoE(Player attacker, org.bukkit.entity.Entity primaryVictim,
            WeaponManager.WeaponData weaponData, double finalDamage) {
        double radius = weaponData.getDoubleExtra("melee-aoe-radius", 2.0);
        double damageRatio = weaponData.getDoubleExtra("melee-aoe-damage-ratio", 0.6);
        double aoeDamage = finalDamage * damageRatio;
        if (aoeDamage <= 0) return;

        Location center = primaryVictim.getLocation().clone().add(0, 0.5, 0);

        // 將攻擊者加入守衛 Set，onEntityDamage 偵測到後會跳過武器特效處理
        UUID attackerUuid = attacker.getUniqueId();
        aoeGuard.add(attackerUuid);
        try {
            boolean hitAny = false;
            for (org.bukkit.entity.Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (nearby == attacker) continue;
                if (nearby == primaryVictim) continue;
                if (!(nearby instanceof LivingEntity)) continue;
                LivingEntity target = (LivingEntity) nearby;
                if (target.isDead()) continue;
                // damage() 會觸發 EntityDamageByEntityEvent，但 aoeGuard 會讓 handler 直接放行
                target.damage(aoeDamage, attacker);
                hitAny = true;
            }

            // 揮砍粒子特效
            if (hitAny) {
                center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 4,
                        radius * 0.35, 0.1, radius * 0.35, 0.0);
            }
        } finally {
            // 無論如何都移除守衛，確保不會卡住玩家後續正常攻擊
            aoeGuard.remove(attackerUuid);
        }
    }

    /**
     * Apply bleed (wither) effect. Used by AXE / TWO_HAND_AXE.
     */
    private void applyBleedEffect(Player attacker, org.bukkit.entity.Entity victim,
            WeaponManager.WeaponData weaponData) {
        if (!(victim instanceof LivingEntity)) return;
        LivingEntity target = (LivingEntity) victim;
        int durationTicks = weaponData.getIntExtra("bleed-duration-ticks", 60);
        int level = weaponData.getIntExtra("bleed-level", 0);

        target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.WITHER, durationTicks, level, false, true));

        victim.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                victim.getLocation().add(0, 1.0, 0), 6, 0.3, 0.5, 0.3, 0.02);
        attacker.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 0.8f);
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
     * Apply ice effect (freeze enemy for a duration with chance)
     *
     * @param attacker   The attacking player
     * @param victim     The victim entity
     * @param weaponData The weapon data
     */
    private void applyIceEffect(Player attacker, org.bukkit.entity.Entity victim,
            WeaponManager.WeaponData weaponData) {
        if (!(victim instanceof LivingEntity)) {
            return;
        }

        double chance = weaponData.getDoubleExtra("ice-chance", 0.3);
        if (random.nextDouble() >= chance) {
            return;
        }

        LivingEntity livingVictim = (LivingEntity) victim;
        int durationTicks = weaponData.getIntExtra("ice-duration-ticks", 40); // 預設 2 秒 (40 ticks)

        // 凍結效果：使用緩速 10 級 + 挖掘疲勞來模擬凍結
        livingVictim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS, durationTicks, 10, false, true));
        livingVictim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.MINING_FATIGUE, durationTicks, 2, false, true));
        livingVictim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.JUMP_BOOST, durationTicks, 128, false, false)); // 負值跳躍阻止跳躍

        // 視覺效果：冰雪粒子
        victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation().add(0, 1.0, 0), 30, 0.3, 0.6, 0.3);
        victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1.0, 0), 15, 0.3, 0.6, 0.3);

        // 音效
        attacker.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);

        double seconds = durationTicks / 20.0;
        attacker.sendMessage(ChatColor.AQUA + "❄ 凍結目標! (" + String.format("%.1f", seconds) + " 秒, " + (int) (chance * 100) + "% 機率)");
    }

    /**
     * Apply water effect (slowness on enemy)
     *
     * @param attacker   The attacking player
     * @param victim     The victim entity
     * @param weaponData The weapon data
     */
    private void applyWaterEffect(Player attacker, org.bukkit.entity.Entity victim,
            WeaponManager.WeaponData weaponData) {
        if (!(victim instanceof LivingEntity)) {
            return;
        }

        LivingEntity livingVictim = (LivingEntity) victim;
        int durationTicks = weaponData.getIntExtra("water-duration-ticks", 60); // 預設 3 秒
        int amplifier = weaponData.getIntExtra("water-slowness-level", 1); // 預設緩速 II (amplifier 1 = level 2)

        // 套用緩速效果
        livingVictim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS, durationTicks, amplifier, false, true));

        // 視覺效果：水滴粒子
        victim.getWorld().spawnParticle(Particle.SPLASH, victim.getLocation().add(0, 1.0, 0), 25, 0.3, 0.6, 0.3);
        victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation().add(0, 0.5, 0), 15, 0.4, 0.4, 0.4);

        // 音效
        attacker.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_SPLASH, 1.0f, 1.0f);

        double seconds = durationTicks / 20.0;
        attacker.sendMessage(ChatColor.BLUE + "💧 水流緩速! (緩速 " + (amplifier + 1) + ", " + String.format("%.1f", seconds) + " 秒)");
    }

    /**
     * Apply poison effect (reduce enemy armor)
     *
     * @param attacker   The attacking player
     * @param victim     The victim entity
     * @param weaponData The weapon data
     */
    private void applyPoisonEffect(Player attacker, org.bukkit.entity.Entity victim,
            WeaponManager.WeaponData weaponData) {
        if (!(victim instanceof LivingEntity)) {
            return;
        }

        LivingEntity livingVictim = (LivingEntity) victim;
        int durationTicks = weaponData.getIntExtra("poison-duration-ticks", 100); // 預設 5 秒
        int amplifier = weaponData.getIntExtra("poison-level", 1); // 預設中毒 II

        // 套用中毒效果（造成持續傷害）
        livingVictim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.POISON, durationTicks, amplifier, false, true));

        // 套用虛弱效果來模擬裝甲減少（減少傷害輸出，但主要是中毒本身）
        // 或者使用凋零效果來穿透裝甲造成傷害
        int armorReductionLevel = weaponData.getIntExtra("poison-armor-reduction-level", 0);
        if (armorReductionLevel > 0) {
            livingVictim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.WITHER, durationTicks, armorReductionLevel - 1, false, true));
        }

        // 視覺效果：毒性粒子
        victim.getWorld().spawnParticle(Particle.SMOKE, victim.getLocation().add(0, 1.0, 0), 20, 0.3, 0.6, 0.3);
        victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.0, 0), 10, 0.3, 0.6, 0.3);

        // 音效
        attacker.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_SPIDER_HURT, 1.0f, 0.8f);

        double seconds = durationTicks / 20.0;
        attacker.sendMessage(ChatColor.GREEN + "☠ 中毒效果! (中毒 " + (amplifier + 1) + ", " + String.format("%.1f", seconds) + " 秒)");
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

    // ═══════════════════════════════════════════════════════════════
    //  法杖右鍵普通攻擊 — 射出魔法彈
    // ═══════════════════════════════════════════════════════════════

    @EventHandler
    public void onStaffRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        // 判斷是否為法杖類別武器
        String weaponKey = weaponManager.getWeaponKey(item);
        WeaponManager.WeaponData weaponData = null;
        WeaponManager.WeaponCategory category;

        if (weaponKey != null) {
            weaponData = weaponManager.getWeaponData(weaponKey);
            if (weaponData == null) return;
            category = weaponData.getCategory();
        } else {
            // 非自訂武器：根據 Material 判斷
            category = WeaponManager.WeaponCategory.detectFromMaterial(item.getType(), false);
        }

        if (category != WeaponManager.WeaponCategory.STAFF) return;

        // 等級需求檢查
        com.customrpg.players.PlayerStats playerStats = statsManager.getStats(player);
        if (weaponData != null && playerStats.getLevel() < weaponData.getMinLevel()) {
            player.sendMessage(ChatColor.RED + "你必須達到等級 " + weaponData.getMinLevel() + " 才能使用此武器！");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // 冷卻檢查
        long now = System.currentTimeMillis();
        Long lastAttack = staffAttackCooldown.get(player.getUniqueId());
        if (lastAttack != null && (now - lastAttack) < STAFF_ATTACK_COOLDOWN_MS) {
            // 還在冷卻中 — 計算剩餘時間
            double remaining = (STAFF_ATTACK_COOLDOWN_MS - (now - lastAttack)) / 1000.0;
            player.sendActionBar(ChatColor.GRAY + "法杖冷卻中... " + String.format("%.1f", remaining) + "s");
            return;
        }
        staffAttackCooldown.put(player.getUniqueId(), now);

        // 取消右鍵原本的行為（例如放置方塊）
        event.setCancelled(true);

        // 計算傷害
        double baseDamage = 5.0; // 預設法杖基礎傷害
        if (weaponData != null) {
            double weaponBase = weaponData.getDoubleExtra("base-damage", 0.0);
            if (weaponBase > 0) baseDamage = weaponBase;
        }

        // 屬性加成（法杖主要縮放 Magic）
        double magicBonus = playerStats.getTotalMagic() * 0.4;
        double spiritBonus = playerStats.getTotalSpirit() * 0.1;
        baseDamage += magicBonus + spiritBonus;

        // 武器倍率
        if (weaponData != null) {
            baseDamage *= weaponData.getDamageMultiplier();
        }

        // 種族武器加成
        com.customrpg.races.RaceManager raceManager = plugin.getRaceManager();
        if (raceManager != null && raceManager.hasRace(player)) {
            double raceBonus = raceManager.getWeaponDamageBonus(player, "STAFF");
            baseDamage *= raceBonus;
        }

        // 決定元素類型和粒子
        String element = "none";
        if (weaponData != null) {
            element = weaponData.getSpecialEffect();
        }

        // 發射魔法彈
        launchMagicBolt(player, baseDamage, element, weaponData);

        // 音效
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.5f);
    }

    /**
     * 發射魔法彈投射物（純粒子效果）
     */
    private void launchMagicBolt(Player player, double damage, String element, WeaponManager.WeaponData weaponData) {
        Location startLoc = player.getEyeLocation().clone();
        Vector direction = startLoc.getDirection().normalize();
        World world = player.getWorld();

        // 決定粒子顏色和類型 (根據元素)
        final Particle mainParticle;
        final Particle trailParticle;
        final Particle.DustOptions dustOptions;
        final Sound hitSound;

        switch (element.toLowerCase()) {
            case "burn":
                mainParticle = Particle.FLAME;
                trailParticle = Particle.SMOKE;
                dustOptions = null;
                hitSound = Sound.ENTITY_BLAZE_HURT;
                break;
            case "ice":
                mainParticle = Particle.SNOWFLAKE;
                trailParticle = Particle.CLOUD;
                dustOptions = null;
                hitSound = Sound.BLOCK_GLASS_BREAK;
                break;
            case "lightning":
                mainParticle = Particle.ELECTRIC_SPARK;
                trailParticle = Particle.END_ROD;
                dustOptions = null;
                hitSound = Sound.ENTITY_LIGHTNING_BOLT_IMPACT;
                break;
            case "poison":
                mainParticle = Particle.ITEM_SLIME;
                trailParticle = Particle.ITEM_SLIME;
                dustOptions = null;
                hitSound = Sound.ENTITY_SLIME_SQUISH;
                break;
            // ── 新增元素 ──
            case "wind":
                mainParticle = Particle.CLOUD;
                trailParticle = Particle.SWEEP_ATTACK;
                dustOptions = null;
                hitSound = Sound.ENTITY_PHANTOM_FLAP;
                break;
            case "light":
                mainParticle = Particle.END_ROD;
                trailParticle = Particle.ENCHANTED_HIT;
                dustOptions = null;
                hitSound = Sound.ENTITY_PLAYER_LEVELUP;
                break;
            case "dark":
                mainParticle = Particle.DRAGON_BREATH;
                trailParticle = Particle.LARGE_SMOKE;
                dustOptions = null;
                hitSound = Sound.ENTITY_WITHER_HURT;
                break;
            case "nature":
                mainParticle = Particle.HAPPY_VILLAGER;
                trailParticle = Particle.COMPOSTER;
                dustOptions = null;
                hitSound = Sound.BLOCK_GRASS_BREAK;
                break;
            case "life":
            case "earth":
                mainParticle = Particle.TOTEM_OF_UNDYING;
                trailParticle = Particle.HEART;
                dustOptions = null;
                hitSound = Sound.ENTITY_PLAYER_LEVELUP;
                break;
            default:
                // 默認：紫色魔法彈
                mainParticle = Particle.DUST;
                trailParticle = Particle.ENCHANT;
                dustOptions = new Particle.DustOptions(Color.fromRGB(160, 80, 255), 1.2f);
                hitSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                break;
        }

        // 最大射程
        double maxRange = 25.0;
        double speed = 1.5; // 每 tick 移動的格數
        int maxTicks = (int) (maxRange / speed);
        final double finalDamage = damage;

        new BukkitRunnable() {
            Location current = startLoc.clone().add(direction.clone().multiply(0.5)); // 從玩家眼前稍微前方開始
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > maxTicks) {
                    // 超出射程 — 消散效果
                    world.spawnParticle(Particle.POOF, current, 5, 0.1, 0.1, 0.1, 0.02);
                    cancel();
                    return;
                }

                // 移動投射物
                current.add(direction.clone().multiply(speed));

                // 碰撞偵測：檢查是否撞到方塊
                if (current.getBlock().getType().isSolid()) {
                    // 撞到方塊 — 爆破粒子效果
                    spawnHitEffect(world, current, mainParticle, dustOptions);
                    world.playSound(current, hitSound, 0.8f, 1.2f);
                    cancel();
                    return;
                }

                // 產生飛行粒子
                if (dustOptions != null) {
                    world.spawnParticle(mainParticle, current, 3, 0.05, 0.05, 0.05, 0, dustOptions);
                } else {
                    world.spawnParticle(mainParticle, current, 3, 0.05, 0.05, 0.05, 0.01);
                }
                world.spawnParticle(trailParticle, current, 1, 0.08, 0.08, 0.08, 0.01);

                // 碰撞偵測：檢查是否命中敵人
                for (Entity entity : world.getNearbyEntities(current, 0.8, 0.8, 0.8)) {
                    if (entity == player) continue;
                    if (!(entity instanceof LivingEntity target)) continue;
                    if (target.isDead()) continue;

                    // 命中！
                    target.damage(finalDamage, player);

                    // 元素附加效果
                    applyStaffElementEffect(target, element, weaponData, player, finalDamage);

                    // 命中粒子特效
                    spawnHitEffect(world, target.getLocation().add(0, 1, 0), mainParticle, dustOptions);
                    world.playSound(target.getLocation(), hitSound, 0.8f, 1.0f);
                    world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 1.0f);

                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 法杖元素附加效果
     * @param target     被命中的實體
     * @param element    元素類型 (burn / ice / lightning / poison / wind / light / dark / nature / life / earth)
     * @param weaponData 武器資料（可為 null）
     * @param caster     施法玩家（用於 AoE / 吸血）
     * @param damage     本次命中基礎傷害
     */
    private void applyStaffElementEffect(LivingEntity target, String element,
            WeaponManager.WeaponData weaponData, Player caster, double damage) {
        switch (element.toLowerCase()) {

            // ══ 火焰：疊層灼燒 DOT ══
            case "burn": {
                UUID tid = target.getUniqueId();
                int maxStacks = weaponData != null
                        ? weaponData.getIntExtra("burn-max-stacks", BURN_MAX_STACKS_DEFAULT)
                        : BURN_MAX_STACKS_DEFAULT;
                int current = burnStacks.getOrDefault(tid, 0);
                int newStacks = Math.min(current + 1, maxStacks);
                burnStacks.put(tid, newStacks);
                burnLastHit.put(tid, System.currentTimeMillis());
                target.setFireTicks(0); // 防止原生火焰造成雙重傷害
                target.getWorld().spawnParticle(Particle.FLAME,
                        target.getLocation().add(0, 1, 0), newStacks * 3, 0.3, 0.6, 0.3, 0.02);
                if (caster != null) {
                    caster.sendMessage("§c🔥 燃燒疊加: " + newStacks + "/" + maxStacks);
                }
                break;
            }

            // ══ 冰霜：緩速 ══
            case "ice": {
                int iceDuration = weaponData != null
                        ? weaponData.getIntExtra("ice-duration-ticks", 60) : 60;
                target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, iceDuration, 1, false, true));
                target.setFreezeTicks(iceDuration);
                target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        target.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
                break;
            }

            // ══ 雷電：閃電 + 機率暈眩 ══
            case "lightning": {
                double strikeChance = weaponData != null
                        ? weaponData.getDoubleExtra("lightning-chance", 0.3) : 0.3;
                double stunChance = weaponData != null
                        ? weaponData.getDoubleExtra("stun-chance", 0.25) : 0.25;
                if (random.nextDouble() < strikeChance) {
                    target.getWorld().strikeLightningEffect(target.getLocation());
                }
                if (random.nextDouble() < stunChance) {
                    // 暈眩：噁心 + 強力緩速 + 挖掘疲勞，持續 2 秒
                    target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.NAUSEA, 40, 1, false, true));
                    target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 4, false, true));
                    target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.MINING_FATIGUE, 40, 2, false, true));
                    target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                            target.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.05);
                    if (caster != null) caster.sendMessage("§e⚡ 暈眩觸發！");
                }
                break;
            }

            // ══ 毒素 ══
            case "poison": {
                int poisonTicks = 60;
                int poisonLevel = 1;
                if (weaponData != null) {
                    poisonTicks = weaponData.getIntExtra("poison-duration-ticks", 60);
                    poisonLevel = weaponData.getIntExtra("poison-level", 1);
                }
                target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.POISON, poisonTicks, poisonLevel - 1, false, true));
                break;
            }

            // ══ 風：群體傷害 ══
            case "wind": {
                double aoeRadius = weaponData != null
                        ? weaponData.getDoubleExtra("aoe-radius", 3.5) : 3.5;
                double aoeDmg = damage * 0.75;
                target.getWorld().spawnParticle(Particle.CLOUD,
                        target.getLocation().add(0, 1, 0), 30, aoeRadius * 0.4, 0.3, aoeRadius * 0.4, 0.05);
                target.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                        target.getLocation().add(0, 1, 0), 6, aoeRadius * 0.3, 0.2, aoeRadius * 0.3, 0.02);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.2f);
                // 傷害範圍內所有附近實體
                for (org.bukkit.entity.Entity nearby : target.getWorld()
                        .getNearbyEntities(target.getLocation(), aoeRadius, aoeRadius, aoeRadius)) {
                    if (nearby == target || nearby == caster) continue;
                    if (!(nearby instanceof LivingEntity nearLe) || nearLe.isDead()) continue;
                    nearLe.damage(aoeDmg, caster);
                }
                break;
            }

            // ══ 光：群體傷害 + 緩速 ══
            case "light": {
                double aoeRadius = weaponData != null
                        ? weaponData.getDoubleExtra("aoe-radius", 3.5) : 3.5;
                double aoeDmg = damage * 0.70;
                int slowDur = weaponData != null
                        ? weaponData.getIntExtra("ice-duration-ticks", 60) : 60;
                target.getWorld().spawnParticle(Particle.END_ROD,
                        target.getLocation().add(0, 1, 0), 30, aoeRadius * 0.4, 0.3, aoeRadius * 0.4, 0.02);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                // 主目標附加緩速
                target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, slowDur, 1, false, true));
                // 傷害並緩速周圍實體
                for (org.bukkit.entity.Entity nearby : target.getWorld()
                        .getNearbyEntities(target.getLocation(), aoeRadius, aoeRadius, aoeRadius)) {
                    if (nearby == target || nearby == caster) continue;
                    if (!(nearby instanceof LivingEntity nearLe) || nearLe.isDead()) continue;
                    nearLe.damage(aoeDmg, caster);
                    nearLe.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, slowDur, 1, false, true));
                }
                break;
            }

            // ══ 黑暗：純粹傷害（短暫黑暗視野） ══
            case "dark": {
                // 施加短暫黑暗效果（視覺上強調純粹黑暗傷害）
                target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.DARKNESS, 60, 0, false, true));
                target.getWorld().spawnParticle(Particle.DRAGON_BREATH,
                        target.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.03);
                break;
            }

            // ══ 自然：純粹傷害（20%機率微量回血） ══
            case "nature": {
                target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                        target.getLocation().add(0, 1, 0), 10, 0.3, 0.6, 0.3, 0.02);
                if (caster != null && random.nextDouble() < 0.20) {
                    double heal = Math.max(1.0, damage * 0.05);
                    org.bukkit.attribute.AttributeInstance maxHpAttr =
                            caster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
                    caster.setHealth(Math.min(maxHp, caster.getHealth() + heal));
                    caster.getWorld().spawnParticle(Particle.HEART,
                            caster.getLocation().add(0, 2, 0), 3, 0.2, 0.3, 0.2, 0.01);
                }
                break;
            }

            // ══ 生命 / 大地：攻擊吸血 ══
            case "life":
            case "earth": {
                double pct = weaponData != null
                        ? weaponData.getDoubleExtra("life-steal-percent", 15.0) : 15.0;
                double heal = damage * (pct / 100.0);
                if (caster != null && heal > 0) {
                    org.bukkit.attribute.AttributeInstance maxHpAttr =
                            caster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
                    caster.setHealth(Math.min(maxHp, caster.getHealth() + heal));
                    caster.getWorld().spawnParticle(Particle.HEART,
                            caster.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0.01);
                    caster.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                            caster.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
                    caster.sendMessage("§a❤ 吸血 +" + String.format("%.1f", heal) + " HP");
                }
                break;
            }
        }
    }

    /**
     * 生成命中特效
     */
    private void spawnHitEffect(World world, Location loc, Particle particle, Particle.DustOptions dustOptions) {
        // 爆破粒子
        if (dustOptions != null) {
            world.spawnParticle(particle, loc, 15, 0.3, 0.3, 0.3, 0, dustOptions);
        } else {
            world.spawnParticle(particle, loc, 15, 0.3, 0.3, 0.3, 0.05);
        }
        // 通用命中粒子
        world.spawnParticle(Particle.ENCHANTED_HIT, loc, 10, 0.3, 0.3, 0.3, 0.1);
    }
}

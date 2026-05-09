package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.MobManager;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.managers.WeaponManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * MobListener - Handles custom mob behavior events
 *
 * This listener implements special behaviors for custom mobs such as
 * projectile attacks, death mechanics, and combat modifications.
 *
 * Handles:
 * - Snow Zombie: Throws snowballs periodically
 * - Fire Skeleton: Shoots fire arrows
 * - Giant Slime: Splits into smaller slimes on death
 */
public class MobListener implements Listener {

    private final CustomRPG plugin;
    private final MobManager mobManager;
    private final PlayerStatsManager statsManager;
    private final WeaponManager weaponManager;
    private final Random random;

    // ══ 熔核巨獸 (boss_magma_core) 階段狀態追蹤 ══
    private final Map<UUID, MagmaCoreState> magmaCoreStates = new HashMap<>();

    private static class MagmaCoreState {
        int phase = 1;          // 1=初始, 2=75%分裂完, 3=50%分裂完, 4=25%分裂完
        boolean inSplitPhase = false;
        long splitMergeDeadline = 0L;
        final List<UUID> activeSplits = new ArrayList<>();
        long lastSlamMs    = 0L;   // 冷卻 14s (狂暴 8s)
        long lastEruptMs   = 0L;   // 冷卻 10s (狂暴 6s)
        long lastAbsorbMs  = 0L;   // 冷卻 25s
        long lastWaveMs    = 0L;   // 冷卻 15s (狂暴 8s)
        boolean berserkActive = false;
    }

    /**
     * Constructor for MobListener
     * @param plugin Main plugin instance
     * @param mobManager MobManager instance
     */
    public MobListener(CustomRPG plugin, MobManager mobManager, PlayerStatsManager statsManager, WeaponManager weaponManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
        this.statsManager = statsManager;
        this.weaponManager = weaponManager;
        this.random = new Random();
        startCustomMobBehaviors();
    }

    /**
     * Start periodic behaviors for custom mobs
     */
    private void startCustomMobBehaviors() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        String mobKey = mobManager.getCustomMobKey(entity);
                        if (mobKey == null) continue;

                        MobManager.MobData mobData = mobManager.getMobData(mobKey);
                        if (mobData == null) continue;

                        applyPeriodicBehavior(entity, mobData);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 60L);
    }

    /**
     * Apply periodic behaviors to custom mobs
     * @param entity The mob entity
     * @param mobData The mob data
     */
    private void applyPeriodicBehavior(Entity entity, MobManager.MobData mobData) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }

        LivingEntity mob = (LivingEntity) entity;
        String behavior = mobData.getSpecialBehavior().toLowerCase();

        switch (behavior) {
            case "snowball_thrower" -> {
                if (random.nextDouble() < 0.3) {
                    throwSnowball(mob);
                }
            }
            case "snowball_mage" -> {
                // 骷髏法師：丟出傷害性雪球
                if (random.nextDouble() < 0.4) {
                    throwDamagingSnowball(mob);
                }
            }
            case "shadow_teleport" -> {
                // 影襲蜘蛛：暗影粒子 + 短距離瞬移
                // 持續噴出粒子
                Location loc = mob.getLocation().add(0, 0.5, 0);
                mob.getWorld().spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, loc, 2, 0.3, 0.3, 0.3, 0);
                mob.getWorld().spawnParticle(Particle.SQUID_INK, loc, 1, 0.2, 0.2, 0.2, 0.01);

                // 15% 機率瞬移到附近玩家身邊
                if (random.nextDouble() < 0.15) {
                    shadowTeleport(mob);
                }
            }
            case "charge_attack" -> {
                // 狂暴野豬：直線衝撞
                if (random.nextDouble() < 0.2) {
                    chargeAttack(mob);
                }
            }
            case "flee" -> {
                // 逃跑型：看到玩家就跑
                fleeFromPlayer(mob);
            }
            case "stalker_stealth" -> {
                // 潛伏蜘蛛：靠近玩家時隱形
                stalkerStealth(mob);
            }
            case "plague_aura" -> {
                // 瘟疫散播者：周圍持續毒氣
                plagueAura(mob);
            }
            case "charge_beast" -> {
                // 衝鋒野獸：鎖定後直線衝刺
                if (random.nextDouble() < 0.2) {
                    chargeBeastAttack(mob);
                }
            }
            case "berserker_rage" -> {
                // 狂戰殭屍：低血量時發紅光粒子
                double hpPercent = mob.getHealth() / mob.getMaxHealth();
                if (hpPercent < 0.3) {
                    mob.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, mob.getLocation().add(0, 1, 0),
                            3, 0.3, 0.3, 0.3, 0.02);
                }
            }
            // ═══ 野外 Boss 行為 ═══
            case "boss_pumpkin_knight" -> {
                // 南瓜騎士：Boss 光環粒子
                mob.getWorld().spawnParticle(Particle.FLAME, mob.getLocation().add(0, 2.2, 0),
                        3, 0.2, 0.1, 0.2, 0.01);
            }
            case "boss_scissor_frenzy" -> {
                // 瘋狂剪刀手：快速揮砍粒子 + 加速
                mob.getWorld().spawnParticle(Particle.SWEEP_ATTACK, mob.getLocation().add(0, 1, 0),
                        1, 0.5, 0.3, 0.5, 0);
                if (!mob.hasPotionEffect(PotionEffectType.SPEED)) {
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, false, false));
                }
            }
            case "boss_zombie_beast" -> {
                // 豬屍獸：衝撞（10秒冷卻）+ Boss 粒子
                mob.getWorld().spawnParticle(Particle.LAVA, mob.getLocation().add(0, 1, 0),
                        1, 0.3, 0.3, 0.3, 0);
                bossZombieBeastCharge(mob);
            }
            // ══ 熔核巨獸系列 ══
            case "boss_magma_core"   -> magmaCoreBehavior(mob);
            case "magma_core_medium" -> magmaMediumBehavior(mob);
            case "magma_core_small"  -> magmaSmallBehavior(mob);
        }
    }

    /**
     * Handle custom mob death events
     * @param event EntityDeathEvent
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMobDeath(EntityDeathEvent event) {
        // 排除玩家死亡
        if (event.getEntity() instanceof Player) {
            return;
        }

        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            // 支援野獸擊殺：如果 killer 為 null，檢查 beast_kill_owner metadata
            if (event.getEntity().hasMetadata("beast_kill_owner")) {
                try {
                    String ownerId = event.getEntity().getMetadata("beast_kill_owner").get(0).asString();
                    Player owner = Bukkit.getPlayer(UUID.fromString(ownerId));
                    if (owner != null && owner.isOnline()) {
                        killer = owner;
                    }
                } catch (Exception ignored) {}
            }
        }

        String mobKey = mobManager.getCustomMobKey(event.getEntity());

        // 如果是自製怪物
        if (mobKey != null) {
            MobManager.MobData mobData = mobManager.getMobData(mobKey);
            if (mobData == null) {
                return;
            }

            // 沒有擊殺者（例如跌落死亡），跳過獎勵
            if (killer == null) {
                return;
            }

            int mobLevel = mobManager.getMobLevel(event.getEntity());

            // Handle custom mob drops or effects
            // For example, splitting slimes
            if (mobData.getSpecialBehavior().equalsIgnoreCase("split_on_death")) {
                splitSlime(event.getEntity(), mobData);
            }

            // 黏液爆破者：延遲爆炸
            if (mobData.getSpecialBehavior().equalsIgnoreCase("delayed_explosion")) {
                delayedExplosion(event.getEntity().getLocation(), mobLevel);
            }

            // 熔核巨獸：Boss 死亡 → 清理狀態 + 公告
            if (mobData.getSpecialBehavior().equalsIgnoreCase("boss_magma_core")) {
                MagmaCoreState bossState = magmaCoreStates.remove(event.getEntity().getUniqueId());
                if (bossState != null) {
                    for (UUID splitUuid : bossState.activeSplits) {
                        Entity e = plugin.getServer().getEntity(splitUuid);
                        if (e != null && !e.isDead()) e.remove();
                    }
                }
                Location dl = event.getEntity().getLocation();
                dl.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, dl.clone().add(0, 2, 0), 4, 2, 2, 2, 0);
                dl.getWorld().playSound(dl, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 0.5f);
                for (Player p : dl.getWorld().getPlayers()) {
                    if (p.getLocation().distance(dl) <= 100) {
                        p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[熔核巨獸] " + ChatColor.YELLOW + "熔岩之核冷卻沉眠... 已被討伐！");
                        p.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "BOSS 擊敗！", ChatColor.WHITE + "✦ 熔核巨獸 ✦", 10, 80, 20);
                    }
                }
            }

            // 熔核分裂體：死亡 → 分裂成 2 隻小型碎體
            if (mobData.getSpecialBehavior().equalsIgnoreCase("magma_core_medium")) {
                spawnSmallSplits(event.getEntity(), 2);
            }

            // 清除預設掉落物（如果有自定義掉落物）
            if (!mobData.getVanillaDrops().isEmpty() || !mobData.getWeaponDrops().isEmpty() || !mobData.getEquipmentDrops().isEmpty()) {
                event.getDrops().clear();

                // 掉落原版物品
                for (MobManager.DropItem dropItem : mobData.getVanillaDrops()) {
                    if (random.nextDouble() < dropItem.getChance()) {
                        int amount = dropItem.getMinAmount();
                        if (dropItem.getMaxAmount() > dropItem.getMinAmount()) {
                            amount = random.nextInt(dropItem.getMaxAmount() - dropItem.getMinAmount() + 1) + dropItem.getMinAmount();
                        }
                        event.getDrops().add(new ItemStack(dropItem.getMaterial(), amount));
                    }
                }

                // 掉落自定義武器
                for (MobManager.WeaponDrop weaponDrop : mobData.getWeaponDrops()) {
                    if (random.nextDouble() < weaponDrop.getChance()) {
                        ItemStack weapon = weaponManager.createWeapon(weaponDrop.getWeaponKey());
                        if (weapon != null) {
                            event.getDrops().add(weapon);
                            killer.sendMessage(ChatColor.GOLD + "★ 獲得稀有武器: " + weapon.getItemMeta().getDisplayName());
                        }
                    }
                }

                // 掉落自定義裝備 (飾品 / 裝甲)
                for (MobManager.EquipmentDrop equipDrop : mobData.getEquipmentDrops()) {
                    if (random.nextDouble() < equipDrop.getChance()) {
                        ItemStack dropItem = null;
                        if ("armor".equalsIgnoreCase(equipDrop.getType())) {
                            // 裝甲掉落
                            com.customrpg.equipment.ArmorManager armorMgr = plugin.getArmorManager();
                            if (armorMgr != null) {
                                com.customrpg.equipment.ArmorData armor = armorMgr.createArmor(equipDrop.getEquipmentKey());
                                if (armor != null) {
                                    dropItem = armor.toItemStack();
                                }
                            }
                        } else {
                            // 飾品掉落
                            com.customrpg.equipment.EquipmentManager equipMgr = plugin.getEquipmentManager();
                            if (equipMgr != null) {
                                com.customrpg.equipment.EquipmentData equip = equipMgr.createEquipment(equipDrop.getEquipmentKey());
                                if (equip != null) {
                                    dropItem = equip.toItemStack();
                                }
                            }
                        }
                        if (dropItem != null) {
                            event.getDrops().add(dropItem);
                            String itemName = dropItem.hasItemMeta() && dropItem.getItemMeta().hasDisplayName()
                                    ? dropItem.getItemMeta().getDisplayName()
                                    : equipDrop.getEquipmentKey();
                            killer.sendMessage(ChatColor.LIGHT_PURPLE + "★ 獲得稀有裝備: " + itemName);
                        }
                    }
                }
            }

            // 給予經驗值（基於等級 + 階級倍率 + 等級差距調整）
            int baseExpReward = mobData.calculateExp(mobLevel);

            // 階級經驗倍率：普通 x1, 精英 x2.5, Boss x5
            String mobTier = mobManager.getMobTier(event.getEntity());
            double tierMultiplier = switch (mobTier) {
                case "ELITE" -> 2.5;
                case "BOSS" -> 5.0;
                default -> 1.0;
            };

            // 等級差距調整：怪物等級比玩家高 → 加成，比玩家低太多 → 減少
            com.customrpg.players.PlayerStats killerStats = statsManager.getStats(killer);
            int playerLevel = killerStats.getLevel();
            int levelDiff = mobLevel - playerLevel;
            double levelMultiplier;
            if (levelDiff >= 5) {
                levelMultiplier = 1.5;  // 怪物比玩家高 5 級以上 → +50%
            } else if (levelDiff >= 0) {
                levelMultiplier = 1.0 + (levelDiff * 0.05); // 每高 1 級 +5%
            } else if (levelDiff >= -5) {
                levelMultiplier = 1.0;  // 低 1~5 級 → 正常
            } else if (levelDiff >= -10) {
                levelMultiplier = 0.5;  // 低 6~10 級 → 50%
            } else {
                levelMultiplier = 0.1;  // 低 10 級以上 → 10%（防止刷低等怪）
            }

            int finalExp = Math.max(1, (int) (baseExpReward * tierMultiplier * levelMultiplier));
            if (finalExp > 0) {
                statsManager.addExp(killer, finalExp);

                // 根據階級顯示不同顏色的訊息
                String tierTag = switch (mobTier) {
                    case "ELITE" -> ChatColor.GOLD + "[精英] ";
                    case "BOSS" -> ChatColor.RED + "[BOSS] ";
                    default -> "";
                };

                killer.sendMessage(ChatColor.YELLOW + "擊敗 " + tierTag +
                    ChatColor.YELLOW + "[Lv." + mobLevel + "] " +
                    ChatColor.stripColor(mobData.getName()) + " 獲得 " +
                    ChatColor.GREEN + finalExp + ChatColor.YELLOW + " 經驗值" +
                    (tierMultiplier > 1.0 ? ChatColor.GOLD + " (x" + tierMultiplier + ")" : ""));
            }

            // 移除 BossBar
            com.customrpg.managers.BossBarManager bossBarMgr = plugin.getBossBarManager();
            if (bossBarMgr != null) {
                bossBarMgr.onMobDeath(event.getEntity().getUniqueId());
            }

            com.customrpg.managers.BossZoneManager bossZoneManager = plugin.getBossZoneManager();
            if (bossZoneManager != null) {
                bossZoneManager.handleEntityDeath(event.getEntity());
            }
        } else {
            // 普通原版怪物：依類型給予不同經驗值
            if (killer == null) return; // 無擊殺者（跌落、其他原因死亡）
            int vanillaExp = switch (event.getEntity().getType()) {
                case ENDER_DRAGON -> 500;
                case WITHER -> 300;
                case ELDER_GUARDIAN -> 80;
                case WARDEN -> 100;
                case RAVAGER -> 50;
                case EVOKER, VINDICATOR -> 20;
                case GHAST, BLAZE, GUARDIAN -> 15;
                case ENDERMAN, PIGLIN_BRUTE -> 10;
                case CREEPER, SKELETON, ZOMBIE, SPIDER -> 5;
                case SLIME, MAGMA_CUBE -> 3;
                default -> 2;
            };
            statsManager.addExp(killer, vanillaExp);
            killer.sendMessage(ChatColor.YELLOW + "獲得 " + vanillaExp + " 經驗值");
        }
    }

    /**
     * Handle custom mob damage events (level-scaled damage)
     * 處理玩家攻擊 BlockDisplay 偽裝系統的情況
     * @param event EntityDamageByEntityEvent
     */
    @EventHandler
    public void onMobAttack(EntityDamageByEntityEvent event) {
        // === 情況 1：玩家攻擊 BlockDisplay 或名稱標籤 ArmorStand ===
        // BlockDisplay 和 ArmorStand 不是 LivingEntity，需要找到對應的隱形核心
        if (event.getDamager() instanceof Player player) {
            Entity target = event.getEntity();

            // 檢查是否攻擊到 BlockDisplay
            if (target instanceof org.bukkit.entity.BlockDisplay) {
                event.setCancelled(true); // 取消對 BlockDisplay 的傷害

                // 從 BlockDisplay 的 PersistentData 獲取核心 UUID
                String coreUuidString = target.getPersistentDataContainer().get(
                    new org.bukkit.NamespacedKey(plugin, "disguise_blockdisplay"),
                    org.bukkit.persistence.PersistentDataType.STRING
                );

                if (coreUuidString != null) {
                    java.util.UUID coreUuid = java.util.UUID.fromString(coreUuidString);
                    // 在同一世界中尋找核心實體
                    for (Entity entity : target.getWorld().getEntities()) {
                        if (entity.getUniqueId().equals(coreUuid) && entity instanceof LivingEntity core) {
                            // 找到核心，對其造成傷害

                            // 計算傷害（考慮玩家的武器）
                            double damage = 1.0;
                            ItemStack weapon = player.getInventory().getItemInMainHand();
                            if (weapon != null && !weapon.getType().isAir()) {
                                // 這裡可以加入自定義武器傷害計算
                                damage = weaponManager.getBaseDamage(weapon);
                            }

                            core.damage(damage, player);
                            plugin.getLogger().fine("玩家攻擊 BlockDisplay，轉移 " + damage + " 傷害到核心");
                            return;
                        }
                    }
                }
            }

            // 檢查是否攻擊到名稱標籤 ArmorStand
            if (target instanceof ArmorStand armorStand) {
                String coreUuidString = armorStand.getPersistentDataContainer().get(
                    new org.bukkit.NamespacedKey(plugin, "disguise_nametag"),
                    org.bukkit.persistence.PersistentDataType.STRING
                );

                if (coreUuidString != null) {
                    event.setCancelled(true); // 取消對 ArmorStand 的傷害

                    java.util.UUID coreUuid = java.util.UUID.fromString(coreUuidString);
                    // 在同一世界中尋找核心實體
                    for (Entity entity : target.getWorld().getEntities()) {
                        if (entity.getUniqueId().equals(coreUuid) && entity instanceof LivingEntity core) {
                            // 找到核心，對其造成傷害

                            // 計算傷害
                            double damage = 1.0;
                            ItemStack weapon = player.getInventory().getItemInMainHand();
                            if (weapon != null && !weapon.getType().isAir()) {
                                damage = weaponManager.getBaseDamage(weapon);
                            }

                            core.damage(damage, player);
                            plugin.getLogger().fine("玩家攻擊名稱標籤，轉移 " + damage + " 傷害到核心");
                            return;
                        }
                    }
                }
            }
        }

        // === 情況 1.5：玩家攻擊自訂 Boss 怪物（特殊防禦機制）===
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity target) {
            String targetMobKey = mobManager.getCustomMobKey(target);
            if (targetMobKey != null) {
                MobManager.MobData targetData = mobManager.getMobData(targetMobKey);
                if (targetData != null) {
                    // 南瓜騎士：35% 格擋 → 減傷60%
                    if (targetData.getSpecialBehavior().equalsIgnoreCase("boss_pumpkin_knight")) {
                        if (random.nextDouble() < 0.35) {
                            event.setDamage(event.getDamage() * 0.4);
                            target.getWorld().spawnParticle(Particle.ENCHANTED_HIT, target.getLocation().add(0, 1, 0),
                                    10, 0.3, 0.3, 0.3, 0.05);
                            target.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
                            player.sendMessage(ChatColor.YELLOW + "🛡 南瓜騎士格擋了你的攻擊！");
                        }
                    }
                }

                // BossBar 顯示：玩家攻擊自訂怪物時顯示血量條
                com.customrpg.managers.BossBarManager bossBarMgr = plugin.getBossBarManager();
                if (bossBarMgr != null) {
                    // 延遲1tick更新，讓傷害先生效
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!target.isDead()) {
                                bossBarMgr.onMobDamaged(target);
                            }
                        }
                    }.runTaskLater(plugin, 1L);
                }
            }
        }

        // === 情況 2：自定義生物攻擊玩家（等級化傷害）===
        // 檢查是否為自定義生物攻擊玩家
        if (!(event.getDamager() instanceof LivingEntity)) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) event.getDamager();
        String mobKey = mobManager.getCustomMobKey(attacker);

        if (mobKey == null) {
            return;
        }

        MobManager.MobData mobData = mobManager.getMobData(mobKey);
        if (mobData == null) {
            return;
        }

        // 獲取生物等級並計算傷害
        int mobLevel = mobManager.getMobLevel(attacker);
        double scaledDamage = mobData.calculateDamage(mobLevel);

        // 狂戰殭屍：HP < 30% → +50% 傷害
        if (mobData.getSpecialBehavior().equalsIgnoreCase("berserker_rage")) {
            double hpPercent = attacker.getHealth() / attacker.getMaxHealth();
            if (hpPercent < 0.3) {
                scaledDamage *= 1.5;
                attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, attacker.getLocation().add(0, 1.5, 0),
                        8, 0.3, 0.3, 0.3, 0.05);
                attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.4f, 1.5f);
            }
        }

        // 潛伏蜘蛛：隱形狀態下第一擊 +80% 傷害
        if (mobData.getSpecialBehavior().equalsIgnoreCase("stalker_stealth")) {
            if (attacker.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                scaledDamage *= 1.8;
                attacker.removePotionEffect(PotionEffectType.INVISIBILITY);
                attacker.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1, 0),
                        15, 0.3, 0.3, 0.3, 0.1);
                attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
            }
        }

        // 設置等級化傷害（對於偽裝生物，確保傷害大於0）
        if (scaledDamage < 1.0) {
            scaledDamage = 1.0; // 最低傷害為 1
        }
        event.setDamage(scaledDamage);

        // 冰霜殭屍：攻擊附帶緩速
        if (mobData.getSpecialBehavior().equalsIgnoreCase("frost_attack")) {
            if (event.getEntity() instanceof Player victim) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1)); // 3秒 緩速II
                victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0),
                        10, 0.3, 0.5, 0.3, 0.02);
                victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 1.8f);
            }
        }

        // ═══ 南瓜騎士：30% 重擊（2倍傷害 + 擊退） ═══
        if (mobData.getSpecialBehavior().equalsIgnoreCase("boss_pumpkin_knight")) {
            if (random.nextDouble() < 0.3) {
                scaledDamage *= 2.0;
                event.setDamage(scaledDamage);
                if (event.getEntity() instanceof Player victim) {
                    Vector knockback = attacker.getLocation().getDirection().normalize().multiply(1.0).setY(0.4);
                    victim.setVelocity(knockback);
                    attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.6f);
                    attacker.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0),
                            20, 0.3, 0.3, 0.3, 0.1);
                    victim.sendMessage(ChatColor.RED + "⚔ 南瓜騎士使出了重擊！");
                }
            }
        }

        // ═══ 瘋狂剪刀手：每次攻擊附帶流血（凋零 1 秒）+ 雙重攻擊（40% 機率連擊）═══
        if (mobData.getSpecialBehavior().equalsIgnoreCase("boss_scissor_frenzy")) {
            if (event.getEntity() instanceof Player victim) {
                // 流血
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 20, 0, false, true)); // 1秒凋零
                attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, victim.getLocation().add(0, 1, 0),
                        5, 0.2, 0.3, 0.2, 0.02);
                attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);

                // 40% 連擊（延遲 5 tick 再打一下）
                if (random.nextDouble() < 0.4) {
                    final double comboDamage = scaledDamage * 0.6;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (attacker.isDead() || victim.isDead()) return;
                            if (attacker.getLocation().distance(victim.getLocation()) > 4) return;
                            victim.damage(comboDamage, attacker);
                            attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0, 1, 0),
                                    3, 0.3, 0.3, 0.3, 0);
                            attacker.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.8f);
                        }
                    }.runTaskLater(plugin, 5L);
                }
            }
        }

        // Debug 訊息
        plugin.getLogger().info("Custom mob " + mobData.getName() + " (Lv." + mobLevel + ") attacked player with " + scaledDamage + " damage");
    }

    /**
     * Split a giant slime into smaller ones
     * @param entity The dying entity
     * @param mobData The mob data
     */
    private void splitSlime(LivingEntity entity, MobManager.MobData mobData) {
        Location location = entity.getLocation();

        for (int i = 0; i < 4; i++) {
            Slime smallSlime = (Slime) location.getWorld().spawnEntity(location, EntityType.SLIME);
            smallSlime.setSize(1);

            Vector velocity = new Vector(
                random.nextDouble() - 0.5,
                0.5,
                random.nextDouble() - 0.5
            ).normalize().multiply(0.5);
            smallSlime.setVelocity(velocity);
        }
    }

    /**
     * Handle projectile launch events for custom mobs
     * @param event ProjectileLaunchEvent
     */
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof LivingEntity)) {
            return;
        }

        LivingEntity mob = (LivingEntity) shooter;
        String mobKey = mobManager.getCustomMobKey(mob);
        if (mobKey == null) {
            return;
        }

        MobManager.MobData mobData = mobManager.getMobData(mobKey);
        if (mobData == null) {
            return;
        }

        if (mobData.getSpecialBehavior().equalsIgnoreCase("fire_arrows")) {
            if (event.getEntity() instanceof Arrow) {
                Arrow arrow = (Arrow) event.getEntity();
                arrow.setFireTicks(1000);
            }
        }

        // 爆裂骷髏：標記箭矢為爆炸箭
        if (mobData.getSpecialBehavior().equalsIgnoreCase("explosive_arrow")) {
            if (event.getEntity() instanceof Arrow arrow) {
                arrow.getPersistentDataContainer().set(
                        new org.bukkit.NamespacedKey(plugin, "explosive_arrow"),
                        org.bukkit.persistence.PersistentDataType.BOOLEAN, true
                );
                // 火焰軌跡視覺提示
                arrow.setFireTicks(200);
            }
        }
    }

    /**
     * Throw a snowball from the mob
     * @param mob The mob entity
     */
    private void throwSnowball(LivingEntity mob) {
        Player nearestPlayer = getNearestPlayer(mob, 15.0);
        if (nearestPlayer == null) {
            return;
        }

        Location mobLocation = mob.getEyeLocation();
        Location playerLocation = nearestPlayer.getEyeLocation();

        Vector direction = playerLocation.toVector().subtract(mobLocation.toVector()).normalize();

        Snowball snowball = (Snowball) mob.getWorld().spawnEntity(mobLocation, EntityType.SNOWBALL);
        snowball.setVelocity(direction.multiply(1.5));
        snowball.setShooter(mob);
    }

    /**
     * Get the nearest player to a mob within range
     * @param mob The mob entity
     * @param range The search range
     * @return The nearest player or null
     */
    private Player getNearestPlayer(LivingEntity mob, double range) {
        Player nearest = null;
        double nearestDistance = range;

        for (Entity entity : mob.getNearbyEntities(range, range, range)) {
            if (entity instanceof Player player) {
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                double distance = mob.getLocation().distance(player.getLocation());
                if (distance < nearestDistance) {
                    nearest = player;
                    nearestDistance = distance;
                }
            }
        }

        return nearest;
    }

    // ═══════════════════════════════════════
    //  新增特殊行為
    // ═══════════════════════════════════════

    /**
     * 黏液爆破者：死亡2秒後小範圍爆炸
     */
    private void delayedExplosion(Location deathLocation, int mobLevel) {
        // 生成粒子警告
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 40) { // 2秒 = 40 ticks
                    // 爆炸！
                    deathLocation.getWorld().spawnParticle(Particle.EXPLOSION, deathLocation, 3, 0.5, 0.5, 0.5, 0);
                    deathLocation.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, deathLocation, 30, 1.5, 1.0, 1.5, 0.1);
                    deathLocation.getWorld().playSound(deathLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);

                    // 對範圍內玩家造成傷害（半徑3格）
                    double damage = 5.0 + mobLevel * 1.5;
                    for (Entity entity : deathLocation.getWorld().getNearbyEntities(deathLocation, 3, 3, 3)) {
                        if (entity instanceof Player player) {
                            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
                            player.damage(damage);
                            player.sendMessage(ChatColor.RED + "💥 你被黏液爆炸波及了！");
                        }
                    }
                    cancel();
                    return;
                }
                // 警告粒子 (越來越密集)
                int particleCount = 3 + (ticks / 10);
                deathLocation.getWorld().spawnParticle(Particle.FLAME, deathLocation.clone().add(0, 0.5, 0),
                        particleCount, 0.3, 0.3, 0.3, 0.02);
                if (ticks % 10 == 0) {
                    deathLocation.getWorld().playSound(deathLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f + (ticks / 40f));
                }
                ticks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /**
     * 影襲蜘蛛：短距離瞬移到玩家附近
     */
    private void shadowTeleport(LivingEntity mob) {
        Player target = getNearestPlayer(mob, 12.0);
        if (target == null) return;

        Location playerLoc = target.getLocation();
        // 隨機在玩家周圍 2~4 格瞬移
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 2.0 + random.nextDouble() * 2.0;
        Location teleportLoc = playerLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

        // 確保目標位置安全
        teleportLoc.setY(teleportLoc.getWorld().getHighestBlockYAt(teleportLoc) + 1);

        // 起點粒子效果
        Location fromLoc = mob.getLocation();
        fromLoc.getWorld().spawnParticle(Particle.SQUID_INK, fromLoc.clone().add(0, 0.5, 0), 15, 0.3, 0.5, 0.3, 0.05);
        fromLoc.getWorld().spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, fromLoc.clone().add(0, 1, 0), 10, 0.4, 0.6, 0.4, 0);
        fromLoc.getWorld().playSound(fromLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.5f);

        // 瞬移
        mob.teleport(teleportLoc);

        // 終點粒子效果
        teleportLoc.getWorld().spawnParticle(Particle.SQUID_INK, teleportLoc.clone().add(0, 0.5, 0), 15, 0.3, 0.5, 0.3, 0.05);
        teleportLoc.getWorld().spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, teleportLoc.clone().add(0, 1, 0), 10, 0.4, 0.6, 0.4, 0);
        teleportLoc.getWorld().playSound(teleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.8f);
    }

    /**
     * 狂暴野豬：直線衝撞
     */
    private void chargeAttack(LivingEntity mob) {
        Player target = getNearestPlayer(mob, 10.0);
        if (target == null) return;

        double distance = mob.getLocation().distance(target.getLocation());
        if (distance < 3.0 || distance > 10.0) return; // 太近或太遠不衝

        // 衝撞方向
        Vector direction = target.getLocation().toVector()
                .subtract(mob.getLocation().toVector()).normalize();

        // 播放音效
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.5f, 1.5f);

        // 衝撞動畫 (粒子軌跡)
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 10 || mob.isDead()) {
                    cancel();
                    return;
                }
                mob.setVelocity(direction.clone().multiply(1.2).setY(0.05));
                mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation().add(0, 0.3, 0),
                        5, 0.2, 0.1, 0.2, 0.02);

                // 碰到玩家就擊退
                for (Entity nearby : mob.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (nearby instanceof Player player) {
                        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
                        Vector knockback = direction.clone().multiply(0.8).setY(0.4);
                        player.setVelocity(knockback);
                        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
                        cancel();
                        return;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * 逃跑型：遠離玩家
     */
    private void fleeFromPlayer(LivingEntity mob) {
        Player nearest = getNearestPlayer(mob, 8.0);
        if (nearest == null) return;

        // 計算遠離方向
        Vector away = mob.getLocation().toVector()
                .subtract(nearest.getLocation().toVector()).normalize();
        away.setY(0).normalize().multiply(0.5);
        if (random.nextDouble() < 0.3) {
            away.setY(0.2); // 偶爾跳一下
        }
        mob.setVelocity(away);
    }

    /**
     * 骷髏法師：丟出傷害性雪球（標記為魔法雪球）
     */
    private void throwDamagingSnowball(LivingEntity mob) {
        Player target = getNearestPlayer(mob, 15.0);
        if (target == null) return;

        Location mobLoc = mob.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector()
                .subtract(mobLoc.toVector()).normalize();

        Snowball snowball = mob.getWorld().spawn(mobLoc, Snowball.class);
        snowball.setVelocity(direction.multiply(1.2));
        snowball.setShooter(mob);

        // 標記這顆雪球為魔法雪球
        snowball.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "magic_snowball"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true
        );

        // 紫色粒子
        mob.getWorld().spawnParticle(Particle.WITCH, mobLoc, 5, 0.2, 0.2, 0.2, 0.05);
        mob.getWorld().playSound(mobLoc, Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.6f);
    }

    /**
     * 處理雪球命中事件 — 骷髏法師的魔法雪球造成傷害
     */
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        // === 爆裂骷髏：爆炸箭 ===
        if (event.getEntity() instanceof Arrow arrow) {
            boolean isExplosive = arrow.getPersistentDataContainer().has(
                    new org.bukkit.NamespacedKey(plugin, "explosive_arrow"),
                    org.bukkit.persistence.PersistentDataType.BOOLEAN
            );
            if (isExplosive) {
                Location hitLoc = arrow.getLocation();
                // 小範圍爆炸 (不破壞方塊)
                hitLoc.getWorld().spawnParticle(Particle.EXPLOSION, hitLoc, 2, 0.3, 0.3, 0.3, 0);
                hitLoc.getWorld().spawnParticle(Particle.FLAME, hitLoc, 15, 0.5, 0.5, 0.5, 0.05);
                hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.5f);

                // 計算傷害
                double damage = 5.0;
                if (arrow.getShooter() instanceof LivingEntity shooter) {
                    String mobKey = mobManager.getCustomMobKey(shooter);
                    if (mobKey != null) {
                        MobManager.MobData data = mobManager.getMobData(mobKey);
                        int level = mobManager.getMobLevel(shooter);
                        if (data != null) {
                            damage = data.calculateDamage(level) * 0.4;
                        }
                    }
                }

                // 半徑2.5格範圍傷害
                for (Entity nearby : hitLoc.getWorld().getNearbyEntities(hitLoc, 2.5, 2.5, 2.5)) {
                    if (nearby instanceof Player player) {
                        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
                        player.damage(damage);
                    }
                }
                arrow.remove();
                return;
            }
        }

        // === 骷髏法師：魔法雪球 ===
        if (!(event.getEntity() instanceof Snowball snowball)) return;

        // 檢查是否為魔法雪球
        boolean isMagic = snowball.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(plugin, "magic_snowball"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN
        );
        if (!isMagic) return;

        // 如果命中實體
        if (event.getHitEntity() instanceof Player player) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

            // 計算傷害 (根據射擊者等級)
            double damage = 4.0;
            if (snowball.getShooter() instanceof LivingEntity shooter) {
                String mobKey = mobManager.getCustomMobKey(shooter);
                if (mobKey != null) {
                    MobManager.MobData mobData = mobManager.getMobData(mobKey);
                    int level = mobManager.getMobLevel(shooter);
                    if (mobData != null) {
                        damage = mobData.calculateDamage(level) * 0.6;
                    }
                }
            }

            player.damage(damage);
            player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0),
                    10, 0.3, 0.5, 0.3, 0.05);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.2f);
        }
    }

    /**
     * 潛伏蜘蛛：靠近玩家後隱形（距離8格以內）
     */
    private void stalkerStealth(LivingEntity mob) {
        Player nearest = getNearestPlayer(mob, 12.0);
        if (nearest == null) return;

        double distance = mob.getLocation().distance(nearest.getLocation());

        // 8格以內且尚未隱形 → 進入隱形
        if (distance <= 8.0 && distance > 2.0 && !mob.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false)); // 5秒隱形
            mob.getWorld().spawnParticle(Particle.SMOKE, mob.getLocation().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3, 0.02);
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.5f, 1.5f);
        }
    }

    /**
     * 瘟疫散播者：周圍持續毒氣（半徑4格）
     */
    private void plagueAura(LivingEntity mob) {
        Location loc = mob.getLocation();

        // 毒氣粒子效果
        mob.getWorld().spawnParticle(Particle.ITEM_SLIME, loc.clone().add(0, 0.5, 0), 5, 1.5, 0.5, 1.5, 0.01);
        mob.getWorld().spawnParticle(Particle.ENTITY_EFFECT, loc.clone().add(0, 0.3, 0), 3, 1.0, 0.3, 1.0, 0);

        // 對4格內的玩家施加中毒
        for (Entity nearby : mob.getNearbyEntities(4, 4, 4)) {
            if (nearby instanceof Player player) {
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                if (!player.hasPotionEffect(PotionEffectType.POISON)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0)); // 4秒中毒I
                }
            }
        }
    }

    /**
     * 衝鋒野獸：鎖定後直線衝刺 (更猛烈的衝撞)
     */
    private void chargeBeastAttack(LivingEntity mob) {
        Player target = getNearestPlayer(mob, 15.0);
        if (target == null) return;

        double distance = mob.getLocation().distance(target.getLocation());
        if (distance < 4.0 || distance > 15.0) return;

        Vector direction = target.getLocation().toVector()
                .subtract(mob.getLocation().toVector()).normalize();

        // 蓄力警告 (1秒)
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.8f);
        mob.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, mob.getLocation().add(0, 1.5, 0), 5, 0.3, 0.3, 0.3, 0);

        new BukkitRunnable() {
            int warmup = 20; // 1秒蓄力
            int chargeTicks = 0;
            @Override
            public void run() {
                if (mob.isDead()) { cancel(); return; }

                // 蓄力階段
                if (warmup > 0) {
                    warmup -= 2;
                    mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation().add(0, 0.5, 0),
                            2, 0.2, 0.1, 0.2, 0.01);
                    return;
                }

                // 衝刺階段
                if (chargeTicks >= 15) { cancel(); return; }

                mob.setVelocity(direction.clone().multiply(1.5).setY(0.05));
                mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation().add(0, 0.3, 0),
                        8, 0.3, 0.1, 0.3, 0.03);
                mob.getWorld().spawnParticle(Particle.BLOCK, mob.getLocation(),
                        5, 0.3, 0.1, 0.3, 0.1, org.bukkit.Material.DIRT.createBlockData());

                // 碰撞檢測
                for (Entity nearby : mob.getNearbyEntities(1.8, 1.8, 1.8)) {
                    if (nearby instanceof Player player) {
                        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
                        // 衝撞傷害 + 擊飛
                        Vector knockback = direction.clone().multiply(1.2).setY(0.6);
                        player.setVelocity(knockback);
                        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1.0f, 0.5f);
                        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0),
                                15, 0.3, 0.3, 0.3, 0.1);
                        cancel();
                        return;
                    }
                }
                chargeTicks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ═══════════════════════════════════════
    //  野外 Boss 專用行為
    // ═══════════════════════════════════════

    /**
     * 豬屍獸：衝撞攻擊（10秒冷卻）
     * 使用 PersistentDataContainer 儲存上次衝撞時間
     */
    private void bossZombieBeastCharge(LivingEntity mob) {
        Player target = getNearestPlayer(mob, 18.0);
        if (target == null) return;

        double distance = mob.getLocation().distance(target.getLocation());
        if (distance < 4.0 || distance > 18.0) return;

        // 檢查冷卻 (10秒 = 10000ms)
        org.bukkit.NamespacedKey cdKey = new org.bukkit.NamespacedKey(plugin, "beast_charge_cd");
        Long lastCharge = mob.getPersistentDataContainer().get(cdKey, org.bukkit.persistence.PersistentDataType.LONG);
        long now = System.currentTimeMillis();
        if (lastCharge != null && (now - lastCharge) < 10000) return;

        // 設定冷卻
        mob.getPersistentDataContainer().set(cdKey, org.bukkit.persistence.PersistentDataType.LONG, now);

        Vector direction = target.getLocation().toVector()
                .subtract(mob.getLocation().toVector()).normalize();

        // 蓄力警告 (1.5秒)
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.5f);
        mob.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, mob.getLocation().add(0, 2, 0), 8, 0.4, 0.3, 0.4, 0);

        // 通知附近玩家
        for (Entity nearby : mob.getNearbyEntities(18, 18, 18)) {
            if (nearby instanceof Player p) {
                p.sendMessage(ChatColor.RED + "⚠ 豬屍獸正在蓄力衝撞！");
            }
        }

        new BukkitRunnable() {
            int warmup = 30; // 1.5秒蓄力
            int chargeTicks = 0;
            boolean hit = false;
            @Override
            public void run() {
                if (mob.isDead()) { cancel(); return; }

                // 蓄力階段 — 身體泛紅
                if (warmup > 0) {
                    warmup -= 2;
                    mob.getWorld().spawnParticle(Particle.SMOKE, mob.getLocation().add(0, 0.5, 0),
                            3, 0.3, 0.2, 0.3, 0.02);
                    mob.getWorld().spawnParticle(Particle.LAVA, mob.getLocation().add(0, 1, 0),
                            2, 0.2, 0.2, 0.2, 0);
                    return;
                }

                // 衝刺階段
                if (chargeTicks >= 20 || hit) { cancel(); return; }

                mob.setVelocity(direction.clone().multiply(1.8).setY(0.05));
                mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation().add(0, 0.3, 0),
                        10, 0.4, 0.1, 0.4, 0.04);
                mob.getWorld().spawnParticle(Particle.BLOCK, mob.getLocation(),
                        8, 0.4, 0.1, 0.4, 0.1, org.bukkit.Material.NETHERRACK.createBlockData());

                // 碰撞檢測 — 範圍更大、傷害更高
                for (Entity nearby : mob.getNearbyEntities(2.0, 2.0, 2.0)) {
                    if (nearby instanceof Player player) {
                        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
                        // 衝撞傷害 = 怪物傷害 * 2.5
                        int level = mobManager.getMobLevel(mob);
                        String key = mobManager.getCustomMobKey(mob);
                        double chargeDmg = 10.0;
                        if (key != null) {
                            MobManager.MobData data = mobManager.getMobData(key);
                            if (data != null) chargeDmg = data.calculateDamage(level) * 2.5;
                        }
                        player.damage(chargeDmg, mob);

                        // 擊飛
                        Vector knockback = direction.clone().multiply(1.5).setY(0.7);
                        player.setVelocity(knockback);
                        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.5f);
                        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0),
                                3, 0.3, 0.3, 0.3, 0);
                        player.sendMessage(ChatColor.RED + "💥 你被豬屍獸衝撞擊飛了！");
                        hit = true;
                        cancel();
                        return;
                    }
                }

                // 衝撞音效
                if (chargeTicks % 3 == 0) {
                    mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_STEP, 1.0f, 0.8f);
                }

                chargeTicks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * 防止自訂怪物被陽光燃燒
     * 殭屍、骷髏等不死生物在白天會著火，取消此行為
     */
    @EventHandler
    public void onCustomMobCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        String mobKey = mobManager.getCustomMobKey(event.getEntity());
        if (mobKey != null) {
            // 是自訂怪物 → 取消燃燒
            event.setCancelled(true);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  熔核巨獸 Boss 行為系統
    // ══════════════════════════════════════════════════════════

    /** 主 Boss 每 3 秒執行一次 */
    private void magmaCoreBehavior(LivingEntity mob) {
        // 設定超大體型（只在需要時設定，避免覆蓋血量）
        if (mob instanceof MagmaCube cube && cube.getSize() < 8) {
            double maxHp = mob.getMaxHealth();
            double curHp = mob.getHealth();
            cube.setSize(8);
            org.bukkit.attribute.AttributeInstance attr = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (attr != null) attr.setBaseValue(maxHp);
            mob.setHealth(Math.min(curHp, maxHp));
        }

        MagmaCoreState state = magmaCoreStates.computeIfAbsent(mob.getUniqueId(), k -> new MagmaCoreState());
        double maxHp  = mob.getMaxHealth();
        double curHp  = mob.getHealth();
        double hpPct  = curHp / maxHp;
        long   now    = System.currentTimeMillis();
        Location loc  = mob.getLocation();

        // 固定粒子光環
        loc.getWorld().spawnParticle(Particle.LAVA,  loc.clone().add(0, 2.5, 0), 8, 1.8, 1.0, 1.8, 0);
        loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1.0, 0), 5, 1.0, 0.8, 1.0, 0.02);
        if (hpPct < 0.20) {
            loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 3.5, 0), 12, 1.2, 1.2, 1.2, 0.05);
        }

        // ── 分裂階段管理 ──────────────────────────────────────
        if (state.inSplitPhase) {
            // 清理已死亡分裂體
            state.activeSplits.removeIf(uuid -> {
                Entity e = plugin.getServer().getEntity(uuid);
                return e == null || e.isDead();
            });

            if (state.activeSplits.isEmpty()) {
                // 玩家勝利：所有分裂體被消滅
                state.inSplitPhase = false;
                broadcast(loc, 40, ChatColor.GREEN + "[熔核巨獸] 所有分裂體被消滅！熔核巨獸受到重創！");
            } else if (now > state.splitMergeDeadline) {
                // 時間到：強制融合，恢復 15% HP
                for (UUID uid : state.activeSplits) {
                    Entity e = plugin.getServer().getEntity(uid);
                    if (e != null && !e.isDead()) e.remove();
                }
                state.activeSplits.clear();
                state.inSplitPhase = false;
                double heal = maxHp * 0.15;
                mob.setHealth(Math.min(curHp + heal, maxHp));
                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 2, 0), 20, 2, 2, 2, 0.1);
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.35f);
                broadcast(loc, 60, ChatColor.DARK_RED + "" + ChatColor.BOLD + "[熔核巨獸] 熔核再生！" + ChatColor.RED + "分裂體重新融合，回復 15% 生命值！");
            } else {
                // 倒計時警告
                long remain = (state.splitMergeDeadline - now) / 1000;
                if (remain == 20 || remain == 10 || remain == 5) {
                    broadcast(loc, 50, ChatColor.YELLOW + "[熔核巨獸] 分裂體融合倒計時：" + ChatColor.RED + remain + "秒！");
                    loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                }
            }
            return; // 分裂期間不使用技能
        }

        // ── 血量門檻觸發分裂 ─────────────────────────────────
        if (hpPct <= 0.75 && state.phase == 1) { state.phase = 2; triggerSplit(mob, state); return; }
        if (hpPct <= 0.50 && state.phase == 2) { state.phase = 3; triggerSplit(mob, state); return; }
        if (hpPct <= 0.25 && state.phase == 3) { state.phase = 4; triggerSplit(mob, state); return; }

        // ── 狂暴模式（≤20%）────────────────────────────────
        if (hpPct <= 0.20 && !state.berserkActive) {
            state.berserkActive = true;
            mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,  Integer.MAX_VALUE, 2, false, false));
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,     Integer.MAX_VALUE, 1, false, false));
            loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.4f);
            broadcast(loc, 80, ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚠ [熔核巨獸] 進入狂暴！！！");
        }

        // 第三/四階段：隨機火柱
        if (state.phase >= 3 && random.nextDouble() < 0.35) {
            spawnFirePillar(mob);
        }

        // ── 技能冷卻（狂暴時縮短）─────────────────────────
        long slamCd  = state.berserkActive ?  8_000L : 14_000L;
        long eruptCd = state.berserkActive ?  6_000L : 10_000L;
        long absorbCd = 25_000L;
        long waveCd  = state.berserkActive ?  8_000L : 15_000L;

        List<Runnable> ready = new ArrayList<>();
        if (now - state.lastSlamMs   > slamCd)  ready.add(() -> magmaLavaSlam(mob, state));
        if (now - state.lastEruptMs  > eruptCd) ready.add(() -> magmaErupt(mob, state));
        if (state.phase >= 2 && now - state.lastAbsorbMs > absorbCd) ready.add(() -> magmaAbsorb(mob, state));
        if (state.phase >= 3 && now - state.lastWaveMs   > waveCd)   ready.add(() -> magmaWave(mob, state));

        if (!ready.isEmpty() && random.nextDouble() < 0.60) {
            ready.get(random.nextInt(ready.size())).run();
        }
    }

    /** 觸發分裂：生成 4 隻中型分裂體，倒計時 30 秒 */
    private void triggerSplit(LivingEntity boss, MagmaCoreState state) {
        Location loc = boss.getLocation();
        state.inSplitPhase = true;
        state.splitMergeDeadline = System.currentTimeMillis() + 30_000L;
        state.activeSplits.clear();

        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 2, 0), 15, 2, 2, 2, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.3f);
        broadcast(loc, 60, ChatColor.DARK_RED + "" + ChatColor.BOLD + "[熔核巨獸] 熔核分裂！§c在 30 秒內消滅所有分裂體！");
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distance(loc) <= 60) {
                p.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "⚠ 熔核分裂！", ChatColor.YELLOW + "在 30 秒內擊殺所有分裂體！", 5, 70, 10);
            }
        }

        int level = mobManager.getMobLevel(boss);
        int splitLevel = Math.max(1, level - 5);
        for (int i = 0; i < 4; i++) {
            double angle = (Math.PI * 2.0 / 4) * i;
            Location spawnLoc = loc.clone().add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
            spawnLoc.setY(spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) + 1);

            LivingEntity split = mobManager.spawnCustomMobWithLevel("magma_core_medium", spawnLoc, splitLevel);
            if (split != null) {
                split.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "magma_parent_uuid"),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    boss.getUniqueId().toString());
                state.activeSplits.add(split.getUniqueId());
            }
        }
    }

    /** 中型分裂體死亡後，再生成 2 隻小型碎體 */
    private void spawnSmallSplits(LivingEntity medium, int count) {
        Location loc = medium.getLocation();
        String parentUuidStr = medium.getPersistentDataContainer().get(
            new org.bukkit.NamespacedKey(plugin, "magma_parent_uuid"),
            org.bukkit.persistence.PersistentDataType.STRING);

        int level = Math.max(1, mobManager.getMobLevel(medium) - 3);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            Location spawnLoc = loc.clone().add(Math.cos(angle) * 2, 0, Math.sin(angle) * 2);
            spawnLoc.setY(spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) + 1);

            LivingEntity small = mobManager.spawnCustomMobWithLevel("magma_core_small", spawnLoc, level);
            if (small != null && parentUuidStr != null) {
                small.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "magma_parent_uuid"),
                    org.bukkit.persistence.PersistentDataType.STRING, parentUuidStr);
                try {
                    MagmaCoreState bossState = magmaCoreStates.get(UUID.fromString(parentUuidStr));
                    if (bossState != null) bossState.activeSplits.add(small.getUniqueId());
                } catch (Exception ignored) {}
            }
        }
    }

    // ── 技能 1：熔岩重壓 ─────────────────────────────────────
    private void magmaLavaSlam(LivingEntity mob, MagmaCoreState state) {
        state.lastSlamMs = System.currentTimeMillis();
        Location loc = mob.getLocation();
        broadcast(loc, 25, ChatColor.RED + "⚠ 熔核巨獸正在蓄力重壓！");
        mob.getWorld().playSound(loc, Sound.ENTITY_MAGMA_CUBE_JUMP, 2.0f, 0.5f);
        mob.setVelocity(new Vector(0, 1.4, 0));
        loc.getWorld().spawnParticle(Particle.LAVA, loc.clone().add(0, 1, 0), 20, 1.5, 0.5, 1.5, 0);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (mob.isDead()) { cancel(); return; }
                Location landLoc = mob.getLocation();
                landLoc.getWorld().playSound(landLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.35f);

                // 震波粒子環
                for (double r = 1; r <= 7; r += 0.8) {
                    for (double a = 0; a < Math.PI * 2; a += 0.4) {
                        Location pl = landLoc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                        landLoc.getWorld().spawnParticle(Particle.LAVA, pl, 1, 0, 0, 0, 0);
                    }
                }

                // 地面點火
                for (int x = -5; x <= 5; x++) {
                    for (int z = -5; z <= 5; z++) {
                        if (random.nextDouble() < 0.35) {
                            org.bukkit.block.Block above = landLoc.clone().add(x, 0, z).getBlock();
                            org.bukkit.block.Block below = landLoc.clone().add(x, -1, z).getBlock();
                            if (above.getType().isAir() && below.getType().isSolid() && !below.isLiquid()) {
                                above.setType(org.bukkit.Material.FIRE);
                            }
                        }
                    }
                }

                // 擊退 + 傷害玩家
                for (Entity e : landLoc.getWorld().getNearbyEntities(landLoc, 7, 5, 7)) {
                    if (!(e instanceof Player p) || p.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
                    Vector kb = p.getLocation().toVector().subtract(landLoc.toVector()).normalize().multiply(1.8).setY(0.6);
                    p.setVelocity(kb);
                    p.damage(18.0, mob);
                    p.sendMessage(ChatColor.RED + "💥 熔核重壓震波！");
                }
                cancel();
            }
        }.runTaskLater(plugin, 32L);
    }

    // ── 技能 2：熔核噴發 ─────────────────────────────────────
    private void magmaErupt(LivingEntity mob, MagmaCoreState state) {
        state.lastEruptMs = System.currentTimeMillis();
        Location loc = mob.getLocation().add(0, 2.5, 0);
        mob.getWorld().playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.4f);
        broadcast(mob.getLocation(), 25, ChatColor.RED + "⚠ 熔核噴發！");

        int shots = state.berserkActive ? 12 : 8;
        for (int i = 0; i < shots; i++) {
            double angle = (Math.PI * 2.0 / shots) * i;
            Vector dir = new Vector(Math.cos(angle), 0.25 + random.nextDouble() * 0.25, Math.sin(angle)).normalize().multiply(1.6);
            SmallFireball fb = mob.getWorld().spawn(loc, SmallFireball.class);
            fb.setVelocity(dir);
            fb.setShooter(mob);
            fb.setIsIncendiary(true);
            fb.setYield(2.0f);
        }
    }

    // ── 技能 3：熔岩吞噬 ─────────────────────────────────────
    private void magmaAbsorb(LivingEntity mob, MagmaCoreState state) {
        state.lastAbsorbMs = System.currentTimeMillis();
        Location loc = mob.getLocation();
        broadcast(loc, 30, ChatColor.GOLD + "⚠ 熔核吞噬！摧毀附近的熔岩阻止回血！");
        mob.getWorld().playSound(loc, Sound.BLOCK_LAVA_AMBIENT, 2.0f, 0.5f);

        // 在周圍放 3 塊熔岩（玩家可以清除）
        List<org.bukkit.block.Block> lavaBlocks = new ArrayList<>();
        for (int t = 0; t < 3; t++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 4 + random.nextInt(4);
            org.bukkit.block.Block above = loc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist).getBlock();
            org.bukkit.block.Block below = above.getRelative(org.bukkit.block.BlockFace.DOWN);
            if (above.getType().isAir() && below.getType().isSolid() && !below.isLiquid()) {
                above.setType(org.bukkit.Material.LAVA);
                lavaBlocks.add(above);
            }
        }

        // 吸收粒子動畫
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (mob.isDead() || ticks >= 20) { cancel(); return; }
                Location center = mob.getLocation().add(0, 1, 0);
                for (int k = 0; k < 8; k++) {
                    double px = center.getX() + (random.nextDouble() - 0.5) * 12;
                    double py = center.getY() + (random.nextDouble() - 0.5) * 4;
                    double pz = center.getZ() + (random.nextDouble() - 0.5) * 12;
                    center.getWorld().spawnParticle(Particle.DRIPPING_LAVA, new Location(center.getWorld(), px, py, pz), 1, 0, 0, 0, 0);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 5L);

        // 5 秒後判定
        new BukkitRunnable() {
            @Override
            public void run() {
                if (mob.isDead()) { lavaBlocks.forEach(b -> { if (b.getType() == org.bukkit.Material.LAVA) b.setType(org.bukkit.Material.AIR); }); return; }
                long remaining = lavaBlocks.stream().filter(b -> b.getType() == org.bukkit.Material.LAVA).count();
                if (remaining > 0) {
                    double heal = mob.getMaxHealth() * 0.05 * remaining;
                    mob.setHealth(Math.min(mob.getHealth() + heal, mob.getMaxHealth()));
                    broadcast(mob.getLocation(), 35, ChatColor.RED + "[熔核巨獸] 吸收了熔岩！回復 " + ChatColor.YELLOW + (int) heal + ChatColor.RED + " 生命值！");
                    mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0, 3, 0), 10, 0.5, 0.5, 0.5, 0.1);
                } else {
                    broadcast(mob.getLocation(), 35, ChatColor.GREEN + "[熔核巨獸] 熔岩被全數摧毀！回血被阻止！");
                }
                lavaBlocks.forEach(b -> { if (b.getType() == org.bukkit.Material.LAVA) b.setType(org.bukkit.Material.AIR); });
            }
        }.runTaskLater(plugin, 100L);
    }

    // ── 技能 4：岩漿浪潮 ─────────────────────────────────────
    private void magmaWave(LivingEntity mob, MagmaCoreState state) {
        state.lastWaveMs = System.currentTimeMillis();
        Player target = getNearestPlayer(mob, 28.0);
        if (target == null) return;

        Location mobLoc = mob.getLocation();
        Vector toward = target.getLocation().toVector().subtract(mobLoc.toVector()).normalize();
        mob.getWorld().playSound(mobLoc, Sound.ENTITY_MAGMA_CUBE_SQUISH_SMALL, 2.0f, 0.35f);
        broadcast(mobLoc, 28, ChatColor.RED + "⚠ 岩漿浪潮！");

        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (wave >= 9 || mob.isDead()) { cancel(); return; }
                double dist = 2.5 + wave * 1.8;
                // 扇形粒子
                for (double a = -Math.PI / 3; a <= Math.PI / 3; a += 0.25) {
                    double sx = Math.cos(a) * toward.getX() - Math.sin(a) * toward.getZ();
                    double sz = Math.sin(a) * toward.getX() + Math.cos(a) * toward.getZ();
                    Location pl = mobLoc.clone().add(sx * dist, 0.5, sz * dist);
                    mobLoc.getWorld().spawnParticle(Particle.LAVA, pl, 2, 0.1, 0.2, 0.1, 0);
                    mobLoc.getWorld().spawnParticle(Particle.FLAME, pl, 1, 0, 0, 0, 0.04);
                }
                // 波浪中心傷害
                Location waveCenter = mobLoc.clone().add(toward.clone().multiply(dist)).add(0, 0.5, 0);
                for (Entity e : waveCenter.getWorld().getNearbyEntities(waveCenter, 3, 2, 3)) {
                    if (!(e instanceof Player p) || p.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
                    p.setVelocity(toward.clone().multiply(1.5).setY(0.35));
                    p.setFireTicks(80);
                    p.damage(8.0, mob);
                    p.sendMessage(ChatColor.RED + "🌊 岩漿浪潮衝擊！");
                }
                wave++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // ── 第三/四階段：火柱 ────────────────────────────────────
    private void spawnFirePillar(LivingEntity mob) {
        Player target = getNearestPlayer(mob, 28.0);
        if (target == null) return;
        Location pillarLoc = target.getLocation().clone().add(
            (random.nextDouble() - 0.5) * 8, 0, (random.nextDouble() - 0.5) * 8);
        pillarLoc.setY(pillarLoc.getWorld().getHighestBlockYAt(pillarLoc));
        final Location finalLoc = pillarLoc;

        // 0.5s 警告後火柱噴發
        new BukkitRunnable() {
            @Override
            public void run() {
                finalLoc.getWorld().spawnParticle(Particle.SMOKE, finalLoc.clone().add(0, 0.5, 0), 6, 0.3, 0, 0.3, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 5L);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 30 || mob.isDead()) { cancel(); return; }
                for (int h = 0; h <= 5; h++) {
                    finalLoc.getWorld().spawnParticle(Particle.FLAME, finalLoc.clone().add(0, h, 0), 4, 0.3, 0, 0.3, 0.06);
                    finalLoc.getWorld().spawnParticle(Particle.LAVA,  finalLoc.clone().add(0, h, 0), 1, 0.2, 0, 0.2, 0);
                }
                for (Entity e : finalLoc.getWorld().getNearbyEntities(finalLoc, 1.5, 6, 1.5)) {
                    if (!(e instanceof Player p) || p.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
                    p.setFireTicks(60);
                    p.damage(5.0, mob);
                }
                ticks += 5;
            }
        }.runTaskLater(plugin, 10L);
    }

    // ── 中型分裂體行為 ────────────────────────────────────────
    private void magmaMediumBehavior(LivingEntity mob) {
        if (mob instanceof MagmaCube cube && cube.getSize() < 3) {
            double maxHp = mob.getMaxHealth();
            double curHp = mob.getHealth();
            cube.setSize(3);
            org.bukkit.attribute.AttributeInstance attr = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (attr != null) attr.setBaseValue(maxHp);
            mob.setHealth(Math.min(curHp, maxHp));
        }
        mob.getWorld().spawnParticle(Particle.LAVA, mob.getLocation().add(0, 1, 0), 3, 0.5, 0.3, 0.5, 0);
        // 25% 機率朝最近玩家跳躍
        if (random.nextDouble() < 0.25) {
            Player t = getNearestPlayer(mob, 16.0);
            if (t != null) {
                Vector leap = t.getLocation().toVector().subtract(mob.getLocation().toVector())
                    .normalize().multiply(1.0).setY(0.9);
                mob.setVelocity(leap);
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_MAGMA_CUBE_JUMP, 1.0f, 0.7f);
            }
        }
    }

    // ── 小型碎體行為 ─────────────────────────────────────────
    private void magmaSmallBehavior(LivingEntity mob) {
        if (mob instanceof MagmaCube cube && cube.getSize() != 1) {
            double maxHp = mob.getMaxHealth();
            double curHp = mob.getHealth();
            cube.setSize(1);
            org.bukkit.attribute.AttributeInstance attr = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (attr != null) attr.setBaseValue(maxHp);
            mob.setHealth(Math.min(curHp, maxHp));
        }
        if (!mob.hasPotionEffect(PotionEffectType.SPEED)) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 3, false, false));
        }
        mob.getWorld().spawnParticle(Particle.FLAME, mob.getLocation().add(0, 0.3, 0), 2, 0.2, 0.2, 0.2, 0.02);
    }

    /** 向 Boss 周圍玩家廣播訊息 */
    private void broadcast(Location center, double radius, String msg) {
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distance(center) <= radius) {
                p.sendMessage(msg);
            }
        }
    }
}

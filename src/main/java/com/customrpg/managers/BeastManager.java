package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BeastManager — 野獸系天賦管理器
 *
 * 負責：
 * 1. 馴服野獸（召喚寵物，擊殺歸屬玩家）
 * 2. 化身野獸（變身系統，屬性加成）
 */
public class BeastManager implements Listener {

    private final CustomRPG plugin;

    // 玩家召喚的野獸 Map<PlayerUUID, List<EntityUUID>>
    private final Map<UUID, List<UUID>> playerBeasts = new ConcurrentHashMap<>();
    // 野獸→主人映射 Map<EntityUUID, PlayerUUID>
    private final Map<UUID, UUID> beastOwners = new ConcurrentHashMap<>();

    // 玩家當前變身狀態 Map<PlayerUUID, BeastFormData>
    private final Map<UUID, BeastFormData> activeForms = new ConcurrentHashMap<>();

    // 每個玩家最多召喚幾隻野獸
    private static final int MAX_BEASTS_PER_PLAYER = 5;

    public BeastManager(CustomRPG plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // 定時清理死掉的野獸
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupDeadBeasts();
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    // ═══════════════════════════════════════
    //  馴服野獸 — 召喚系統
    // ═══════════════════════════════════════

    /**
     * 召喚野獸
     * @param player 召喚者
     * @param entityType 野獸類型 (WOLF, POLAR_BEAR, etc.)
     * @param name 顯示名稱
     * @param health 生命值
     * @param damage 攻擊力
     * @param duration 持續時間(秒)，0=永久
     * @param count 召喚數量
     * @return 是否成功
     */
    public boolean summonBeast(Player player, EntityType entityType, String name,
                               double health, double damage, int duration, int count) {
        UUID pid = player.getUniqueId();
        List<UUID> existing = playerBeasts.getOrDefault(pid, new ArrayList<>());

        // 清理已死亡的
        existing.removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            return e == null || e.isDead();
        });

        if (existing.size() + count > MAX_BEASTS_PER_PLAYER) {
            player.sendMessage("§c你已經召喚了太多野獸！(上限 " + MAX_BEASTS_PER_PLAYER + " 隻)");
            return false;
        }

        Location spawnLoc = player.getLocation().add(
                player.getLocation().getDirection().setY(0).normalize().multiply(2));

        for (int i = 0; i < count; i++) {
            // 每隻稍微偏移位置
            Location loc = spawnLoc.clone().add(
                    (Math.random() - 0.5) * 3, 0, (Math.random() - 0.5) * 3);

            LivingEntity beast = (LivingEntity) player.getWorld().spawnEntity(loc, entityType);

            // 設定名稱
            setBeastNameWithHealth(beast, player, name);

            // 設定血量
            if (beast.getAttribute(Attribute.MAX_HEALTH) != null) {
                beast.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
                beast.setHealth(health);
            }

            // 設定攻擊力
            if (beast.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                beast.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(damage);
            }

            // 標記為玩家的野獸
            beast.setMetadata("beast_owner", new FixedMetadataValue(plugin, pid.toString()));
            beast.setMetadata("beast_damage", new FixedMetadataValue(plugin, damage));
            beast.setMetadata("beast_base_name", new FixedMetadataValue(plugin, name));

            // 如果是可馴服的動物（如狼），設定主人
            if (beast instanceof Tameable tameable) {
                tameable.setTamed(true);
                tameable.setOwner(player);
            }

            // 不會被其他機制清除
            beast.setRemoveWhenFarAway(false);
            beast.setPersistent(true);

            // 記錄
            existing.add(beast.getUniqueId());
            beastOwners.put(beast.getUniqueId(), pid);

            // 生成粒子
            beast.getWorld().spawnParticle(Particle.HEART, beast.getLocation().add(0, 1.5, 0), 5, 0.3, 0.3, 0.3, 0);
        }

        playerBeasts.put(pid, existing);

        // 定時消失
        if (duration > 0) {
            List<UUID> beastIds = new ArrayList<>(existing);
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (UUID beastId : beastIds) {
                        Entity e = Bukkit.getEntity(beastId);
                        if (e != null && !e.isDead()) {
                            e.getWorld().spawnParticle(Particle.SMOKE, e.getLocation().add(0, 0.5, 0), 15, 0.3, 0.5, 0.3, 0.02);
                            e.remove();
                        }
                        beastOwners.remove(beastId);
                    }
                    List<UUID> current = playerBeasts.get(pid);
                    if (current != null) {
                        current.removeAll(beastIds);
                    }
                }
            }.runTaskLater(plugin, duration * 20L);
        }

        return true;
    }

    /**
     * 移除玩家的所有野獸
     */
    public void removeAllBeasts(Player player) {
        UUID pid = player.getUniqueId();
        List<UUID> beasts = playerBeasts.remove(pid);
        if (beasts == null) return;
        for (UUID beastId : beasts) {
            Entity e = Bukkit.getEntity(beastId);
            if (e != null && !e.isDead()) {
                e.getWorld().spawnParticle(Particle.SMOKE, e.getLocation().add(0, 0.5, 0), 10, 0.3, 0.5, 0.3, 0.02);
                e.remove();
            }
            beastOwners.remove(beastId);
        }
    }

    /**
     * 獲取玩家當前召喚的野獸數量
     */
    public int getBeastCount(Player player) {
        List<UUID> beasts = playerBeasts.get(player.getUniqueId());
        if (beasts == null) return 0;
        beasts.removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            return e == null || e.isDead();
        });
        return beasts.size();
    }

    /**
     * 直接將已生成的野獸實體登錄給玩家（不受上限限制）
     * 用於有特殊邏輯的技能（如蜂巢誓盟）
     */
    public void registerExistingBeast(Player player, LivingEntity beast, double damage) {
        UUID pid = player.getUniqueId();
        beast.setMetadata("beast_owner", new FixedMetadataValue(plugin, pid.toString()));
        beast.setMetadata("beast_damage", new FixedMetadataValue(plugin, damage));
        // 記錄基礎名稱（去掉顏色前綴的純文字，用於健康名稱更新）
        String rawName = beast.getCustomName();
        if (rawName == null) rawName = beast.getType().name();
        beast.setMetadata("beast_base_name", new FixedMetadataValue(plugin, rawName));
        beast.setRemoveWhenFarAway(false);
        beast.setPersistent(true);

        List<UUID> existing = playerBeasts.computeIfAbsent(pid, k -> new ArrayList<>());
        existing.add(beast.getUniqueId());
        beastOwners.put(beast.getUniqueId(), pid);
    }

    // ═══════════════════════════════════════
    //  化身野獸 — 變身系統
    // ═══════════════════════════════════════

    /**
     * 啟動變身
     * @param player 玩家
     * @param formType 變身類型 (wolf, polar_bear, etc.)
     * @param duration 持續時間(秒)
     * @param strengthBonus 力量加成
     * @param healthBonus 生命值加成
     * @param speedLevel 速度等級
     * @param damageBoost 傷害提升等級
     */
    public boolean activateBeastForm(Player player, String formType, int duration,
                                     double strengthBonus, double healthBonus,
                                     int speedLevel, int damageBoost) {
        UUID pid = player.getUniqueId();

        // 如果已經變身，先取消
        if (activeForms.containsKey(pid)) {
            deactivateBeastForm(player);
        }

        BeastFormData data = new BeastFormData();
        data.formType = formType;
        data.originalMaxHealth = player.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        data.healthBonus = healthBonus;
        data.strengthBonus = strengthBonus;

        activeForms.put(pid, data);

        // 增加最大生命值
        if (healthBonus > 0) {
            double newMax = data.originalMaxHealth + healthBonus;
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(newMax);
            player.setHealth(Math.min(player.getHealth() + healthBonus, newMax));
        }

        // 增加力量
        player.setMetadata("beast_form_strength", new FixedMetadataValue(plugin, strengthBonus));

        // 藥水效果
        if (speedLevel > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration * 20, speedLevel - 1, true, false));
        }
        if (damageBoost > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration * 20, damageBoost - 1, true, false));
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration * 20, 0, true, false));

        // 變身視覺提示
        String formName = getFormDisplayName(formType);
        player.sendMessage("§6§l[野獸化身] §a你變身為 §e" + formName + " §a！持續 §f" + duration + " §a秒");
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.3);
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.5f, 0.8f);

        // ActionBar 提示
        new BukkitRunnable() {
            int remaining = duration;
            @Override
            public void run() {
                if (remaining <= 0 || !activeForms.containsKey(pid)) {
                    if (activeForms.containsKey(pid)) {
                        deactivateBeastForm(player);
                    }
                    cancel();
                    return;
                }
                player.sendActionBar("§6§l[" + formName + "] §e剩餘 " + remaining + " 秒 §7| §c力量+" + (int) strengthBonus + " §a生命+" + (int) healthBonus);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        return true;
    }

    /**
     * 取消變身
     */
    public void deactivateBeastForm(Player player) {
        UUID pid = player.getUniqueId();
        BeastFormData data = activeForms.remove(pid);
        if (data == null) return;

        // 恢復最大生命值
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(data.originalMaxHealth);
        if (player.getHealth() > data.originalMaxHealth) {
            player.setHealth(data.originalMaxHealth);
        }

        player.removeMetadata("beast_form_strength", plugin);

        player.sendMessage("§6§l[野獸化身] §7變身效果已結束");
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.05);
    }

    /**
     * 玩家是否在變身狀態
     */
    public boolean isInBeastForm(Player player) {
        return activeForms.containsKey(player.getUniqueId());
    }

    /**
     * 獲取變身的力量加成
     */
    public double getBeastFormStrengthBonus(Player player) {
        BeastFormData data = activeForms.get(player.getUniqueId());
        return data != null ? data.strengthBonus : 0;
    }

    // ═══════════════════════════════════════
    //  事件監聽
    // ═══════════════════════════════════════

    /**
     * 野獸擊殺怪物時，歸屬給主人（用於經驗值/掉落）
     * 使用 LOWEST 確保在 MobListener 處理死亡前先設置 metadata
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBeastKill(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Entity killer = victim.getKiller();

        // 如果已經有玩家擊殺者，跳過
        if (killer instanceof Player) return;

        // 檢查最後傷害來源是否是玩家的野獸
        if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent dmgEvent) {
            Entity damager = dmgEvent.getDamager();
            if (damager.hasMetadata("beast_owner")) {
                String ownerStr = damager.getMetadata("beast_owner").get(0).asString();
                UUID ownerId = UUID.fromString(ownerStr);
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner != null && owner.isOnline()) {
                    // 模擬玩家擊殺：設定經驗值掉落來源
                    // Bukkit 無法直接 setKiller，但可以透過傷害事件讓 MobListener 處理
                    victim.setMetadata("beast_kill_owner", new FixedMetadataValue(plugin, ownerId.toString()));
                }
            }
        }
    }

    /**
     * 讓野獸攻擊時附帶主人的部分屬性加成
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBeastAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!damager.hasMetadata("beast_owner")) return;

        // 野獸的基礎傷害已在生成時設定
        // 這裡可以加入額外效果（如毒、緩速等）

        // 讓野獸的目標與玩家一致
        if (event.getEntity() instanceof Player) {
            // 野獸不應該攻擊玩家
            String ownerStr = damager.getMetadata("beast_owner").get(0).asString();
            UUID ownerId = UUID.fromString(ownerStr);
            if (event.getEntity().getUniqueId().equals(ownerId)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 防止野獸攻擊自己的主人
     * 蜜蜂額外：玩家打到蜜蜂時重置生氣狀態
     */
    @EventHandler
    public void onBeastTarget(EntityTargetEvent event) {
        Entity entity = event.getEntity();
        Entity target = event.getTarget();
        if (entity.hasMetadata("beast_owner") && target instanceof Player player) {
            String ownerStr = entity.getMetadata("beast_owner").get(0).asString();
            if (player.getUniqueId().toString().equals(ownerStr)) {
                event.setCancelled(true);
                // 如果是蜜蜂，清除目標（EntityTargetEvent 會阻止後續攻擊）
                if (entity instanceof org.bukkit.entity.Bee bee) {
                    bee.setTarget(null);
                }
            }
        }
    }

    /**
     * 防止攻擊蜂 stinger 傷害主人；守護蜂永不攻擊任何人
     * （EntityDamageByEntityEvent 已在 onBeastAttack 中覆蓋，這裡補充蜜蜂場景）
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBeeAttackOwner(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof org.bukkit.entity.Bee bee)) return;
        if (!bee.hasMetadata("beast_owner")) return;
        // 守護蜂絕對不攻擊任何人
        if (bee.hasMetadata("bee_companion")) {
            event.setCancelled(true);
            bee.setTarget(null);
            return;
        }
        // 攻擊蜂不攻擊主人
        if (event.getEntity() instanceof Player victim) {
            String ownerStr = bee.getMetadata("beast_owner").get(0).asString();
            if (victim.getUniqueId().toString().equals(ownerStr)) {
                event.setCancelled(true);
                bee.setTarget(null);
            }
        }
    }

    /**
     * 阻止召喚蜜蜂對主人產生生氣（使用 Paper 的 EntityTargetEvent 就足夠了，
     * 但若伺服器支援 EntityAngerEvent 也攔截）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBeeAnger(org.bukkit.event.entity.EntityTargetEvent event) {
        // EntityTargetEvent 已在 onBeastTarget 中處理，此為備援
        Entity entity = event.getEntity();
        Entity target = event.getTarget();
        if (!(entity instanceof org.bukkit.entity.Bee bee)) return;
        if (!bee.hasMetadata("beast_owner")) return;
        // 守護蜂永不攻擊任何人
        if (bee.hasMetadata("bee_companion")) {
            event.setCancelled(true);
            return;
        }
        // 攻擊蜂不攻擊主人
        if (target instanceof Player player) {
            String ownerStr = bee.getMetadata("beast_owner").get(0).asString();
            if (player.getUniqueId().toString().equals(ownerStr)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 玩家離線時清除所有野獸和變身
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeAllBeasts(event.getPlayer());
        if (activeForms.containsKey(event.getPlayer().getUniqueId())) {
            deactivateBeastForm(event.getPlayer());
        }
    }

    /**
     * 玩家攻擊怪物時，讓其北極熊與攻擊蜂自動協助攻擊同一目標
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttackMob(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (target instanceof Player) return; // 不協助攻擊玩家
        // 忽略攻擊到自己的野獸（避免讓野獸互打）
        if (target.hasMetadata("beast_owner")) return;

        UUID pid = player.getUniqueId();
        List<UUID> beasts = playerBeasts.get(pid);
        if (beasts == null) return;
        for (UUID beastId : beasts) {
            Entity e = Bukkit.getEntity(beastId);
            if (e == null || e.isDead()) continue;
            if (e instanceof PolarBear bear) {
                bear.setTarget(target);
            } else if (e instanceof org.bukkit.entity.Bee bee) {
                // 只有攻擊蜂跟隨目標（守護蜂不攻擊）
                if (!e.hasMetadata("bee_companion")) {
                    // 用 metadata 記錄目標 UUID，讓手動控制 loop 讀取
                    bee.setMetadata("bee_attack_target",
                            new FixedMetadataValue(plugin, target.getUniqueId().toString()));
                }
            }
        }
    }

    /**
     * 召喚時名稱加上血量
     */
    private void setBeastNameWithHealth(LivingEntity beast, Player player, String name) {
        int hp = (int) beast.getHealth();
        int maxHp = (int) beast.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        beast.setCustomName("§a[" + player.getName() + "的] §f" + name + " §c❤" + hp + "/" + maxHp);
        beast.setCustomNameVisible(true);
    }

    // 受傷時自動更新名稱
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBeastHurt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity beast)) return;
        if (!beast.hasMetadata("beast_owner")) return;
        Player owner = null;
        try {
            owner = Bukkit.getPlayer(UUID.fromString(beast.getMetadata("beast_owner").get(0).asString()));
        } catch (Exception ignored) {}
        if (owner == null) return;
        // 使用 metadata 記錄的基礎名稱，避免重複疊加玩家名前綴
        String baseName = beast.hasMetadata("beast_base_name")
                ? beast.getMetadata("beast_base_name").get(0).asString()
                : beast.getType().name();
        setBeastNameWithHealth(beast, owner, baseName);
    }

    // 寵物攻擊怪物時顯示傷害浮空字
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBeastDealDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!damager.hasMetadata("beast_owner")) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        double damage = event.getFinalDamage();
        // 顯示浮空字
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1.2, 0), 8, 0.2, 0.2, 0.2, 0.1);
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1.5, 0), 1, 0, 0, 0, 0,
            new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.1, 0), 2, 0.1, 0.1, 0.1, 0.05);
        // 顯示傷害數字（用自定義名稱閃現）
        String oldName = target.getCustomName();
        target.setCustomName("§c-" + (int)damage);
        target.setCustomNameVisible(true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            target.setCustomName(oldName);
            if (oldName == null) target.setCustomNameVisible(false);
        }, 16L);
        // 更新寵物名稱血量（僅用 metadata 基礎名稱，不做 regex 剝除）
        if (damager instanceof LivingEntity beast) {
            Player owner = null;
            try {
                owner = Bukkit.getPlayer(UUID.fromString(beast.getMetadata("beast_owner").get(0).asString()));
            } catch (Exception ignored) {}
            if (owner != null) {
                String baseName = beast.hasMetadata("beast_base_name")
                        ? beast.getMetadata("beast_base_name").get(0).asString()
                        : beast.getType().name();
                setBeastNameWithHealth(beast, owner, baseName);
            }
        }
    }

    // ═══════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════

    private void cleanupDeadBeasts() {
        for (Map.Entry<UUID, List<UUID>> entry : playerBeasts.entrySet()) {
            entry.getValue().removeIf(uuid -> {
                Entity e = Bukkit.getEntity(uuid);
                if (e == null || e.isDead()) {
                    beastOwners.remove(uuid);
                    return true;
                }
                // 定期重置蜜蜂狀態（守護蜂永遠不攻擊，攻擊蜂持續維持憤怒值）
                if (e instanceof org.bukkit.entity.Bee bee) {
                    if (e.hasMetadata("bee_companion")) {
                        // 守護蜂永遠清除目標與憤怒
                        bee.setTarget(null);
                        bee.setAnger(0);
                    } else {
                        // 攻擊蜂：若有存活目標則刷新憤怒計時，否則清除目標
                        LivingEntity currentTarget = bee.getTarget();
                        if (currentTarget != null && !currentTarget.isDead()) {
                            bee.setAnger(600); // 重置憤怒計時 30秒
                        } else {
                            bee.setTarget(null);
                            bee.setAnger(0);
                        }
                    }
                }
                return false;
            });
        }
    }

    private String getFormDisplayName(String formType) {
        return switch (formType.toLowerCase()) {
            case "wolf" -> "§7狂狼";
            case "polar_bear" -> "§f巨熊";
            case "fox" -> "§6靈狐";
            case "ravager" -> "§4狂暴獸";
            case "apex" -> "§c§l極致掠食者";
            default -> formType;
        };
    }

    public void shutdown() {
        // 清除所有召喚的野獸
        for (Map.Entry<UUID, List<UUID>> entry : playerBeasts.entrySet()) {
            for (UUID beastId : entry.getValue()) {
                Entity e = Bukkit.getEntity(beastId);
                if (e != null) e.remove();
            }
        }
        playerBeasts.clear();
        beastOwners.clear();
        activeForms.clear();
    }

    // 變身數據
    private static class BeastFormData {
        String formType;
        double originalMaxHealth;
        double healthBonus;
        double strengthBonus;
    }
}


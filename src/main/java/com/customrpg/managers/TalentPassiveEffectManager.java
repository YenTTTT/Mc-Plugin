package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.Talent;
import com.customrpg.talents.TalentBranch;
import com.customrpg.talents.TalentTree;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TalentPassiveEffectManager - 天賦被動效果管理器
 *
 * 功能：
 * - 應用天賦提供的被動效果
 * - 處理天賦觸發效果
 * - 管理持續性效果
 * - 整合戰鬥系統
 */
public class TalentPassiveEffectManager implements Listener {

    private final CustomRPG plugin;
    private final TalentManager talentManager;
    private final PlayerStatsManager statsManager;

    // 快取玩家的天賦加成效果
    private final Map<UUID, Map<String, Double>> playerBonusCache;

    public TalentPassiveEffectManager(CustomRPG plugin, TalentManager talentManager, PlayerStatsManager statsManager) {
        this.plugin = plugin;
        this.talentManager = talentManager;
        this.statsManager = statsManager;
        this.playerBonusCache = new HashMap<>();
    }

    /**
     * 當玩家加入時應用天賦效果
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 延遲應用效果，確保玩家完全載入
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            applyTalentEffects(player);
        }, 20L); // 1秒後執行
    }

    /**
     * 處理攻擊事件的天賦效果
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        PlayerTalents playerTalents = talentManager.getPlayerTalents(attacker);

        // 應用攻擊相關的天賦效果
        applyAttackTalentEffects(attacker, event, playerTalents);
    }

    /**
     * 處理受到傷害事件的天賦效果
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        PlayerTalents playerTalents = talentManager.getPlayerTalents(player);

        // 應用防禦相關的天賦效果
        applyDefenseTalentEffects(player, event, playerTalents);
    }

    /**
     * 應用玩家的所有天賦效果
     */
    public void applyTalentEffects(Player player) {
        PlayerTalents playerTalents = talentManager.getPlayerTalents(player);
        Map<String, Double> bonuses = calculateTalentBonuses(player, playerTalents);

        // 快取玩家的加成效果
        playerBonusCache.put(player.getUniqueId(), bonuses);

        // 應用屬性加成
        applyAttributeBonuses(player, bonuses);

        // 應用持續效果
        applyContinuousEffects(player, bonuses);
    }

    /**
     * 計算玩家的所有天賦加成
     */
    private Map<String, Double> calculateTalentBonuses(Player player, PlayerTalents playerTalents) {
        Map<String, Double> totalBonuses = new HashMap<>();

        for (TalentBranch branch : TalentBranch.values()) {
            TalentTree tree = talentManager.getTalentTree(branch);
            if (tree == null) continue;

            for (Talent talent : tree.getAllTalents().values()) {
                int level = playerTalents.getTalentLevel(talent.getId());
                if (level > 0) {
                    // 累加天賦效果
                    Talent.TalentLevelData levelData = talent.getLevelData(level);
                    if (levelData != null) {
                        for (Map.Entry<String, Double> entry : levelData.effects.entrySet()) {
                            totalBonuses.merge(entry.getKey(), entry.getValue(), Double::sum);
                        }
                    }
                }
            }
        }

        return totalBonuses;
    }

    /**
     * 應用屬性加成
     */
    private void applyAttributeBonuses(Player player, Map<String, Double> bonuses) {
        // 這些效果會在傷害計算時使用，這裡只是記錄
        // 實際的傷害修正會在戰鬥事件中處理

        // 應用最大血量加成
        double healthBonus = bonuses.getOrDefault("max-health", 0.0);
        if (healthBonus > 0) {
            double newMaxHealth = player.getMaxHealth() + healthBonus;
            player.setMaxHealth(Math.min(2048.0, newMaxHealth)); // Minecraft 限制
        }

        // 應用速度效果
        double speedBonus = bonuses.getOrDefault("speed", 0.0);
        if (speedBonus > 0) {
            int amplifier = (int) (speedBonus / 20); // 每20%為一級
            if (amplifier > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, amplifier - 1, true, false));
            }
        }
    }

    /**
     * 應用持續效果
     */
    private void applyContinuousEffects(Player player, Map<String, Double> bonuses) {
        // 生命回復效果
        double healthRegen = bonuses.getOrDefault("health-regen", 0.0);
        if (healthRegen > 0) {
            // 開始生命回復任務 (如果還沒有的話)
            startHealthRegenTask(player, healthRegen);
        }

        // 法力回復加成
        double manaRegenBonus = bonuses.getOrDefault("mana-regen", 0.0);
        if (manaRegenBonus > 0) {
            // 這個效果會在 ManaManager 中處理
        }
    }

    /**
     * 應用攻擊相關的天賦效果
     */
    private void applyAttackTalentEffects(Player attacker, EntityDamageByEntityEvent event, PlayerTalents playerTalents) {
        Map<String, Double> bonuses = playerBonusCache.getOrDefault(attacker.getUniqueId(), new HashMap<>());

        double originalDamage = event.getDamage();
        double finalDamage = originalDamage;

        // 傷害加成
        double damageBonus = bonuses.getOrDefault("damage-bonus", 0.0);
        if (damageBonus > 0) {
            finalDamage *= (1.0 + damageBonus / 100.0);
        }

        // 暴擊檢查
        double critChance = bonuses.getOrDefault("crit-chance", 0.0);
        boolean isCritical = Math.random() * 100 < critChance;

        if (isCritical) {
            double critDamage = bonuses.getOrDefault("crit-damage", 50.0); // 預設50%暴擊傷害
            finalDamage *= (1.0 + critDamage / 100.0);

            // 顯示暴擊效果
            attacker.sendMessage("§c§l暴擊！");
        }

        // 應用最終傷害
        event.setDamage(finalDamage);

        // 狂戰士效果 (血量越低傷害越高)
        double berserkerBonus = bonuses.getOrDefault("berserker-bonus", 0.0);
        if (berserkerBonus > 0) {
            double healthPercent = attacker.getHealth() / attacker.getMaxHealth();
            double berserkerMultiplier = 1.0 + ((1.0 - healthPercent) * berserkerBonus);
            event.setDamage(event.getDamage() * berserkerMultiplier);
        }
    }

    /**
     * 應用防禦相關的天賦效果
     */
    private void applyDefenseTalentEffects(Player player, EntityDamageEvent event, PlayerTalents playerTalents) {
        Map<String, Double> bonuses = playerBonusCache.getOrDefault(player.getUniqueId(), new HashMap<>());

        // 傷害減免
        double damageReduction = bonuses.getOrDefault("damage-reduction", 0.0);
        if (damageReduction > 0) {
            double multiplier = 1.0 - (damageReduction / 100.0);
            event.setDamage(event.getDamage() * multiplier);
        }

        // 格擋機率
        double blockChance = bonuses.getOrDefault("block-chance", 0.0);
        if (blockChance > 0 && Math.random() * 100 < blockChance) {
            double blockReduction = bonuses.getOrDefault("block-reduction", 50.0);
            double multiplier = 1.0 - (blockReduction / 100.0);
            event.setDamage(event.getDamage() * multiplier);

            player.sendMessage("§9§l格擋成功！");
        }

        // 第二春效果
        if (event.getFinalDamage() >= player.getHealth()) {
            double healPercent = bonuses.getOrDefault("heal-percent", 0.0);
            if (healPercent > 0) {
                // 檢查冷卻時間等邏輯...
                double healAmount = player.getMaxHealth() * (healPercent / 100.0);
                player.setHealth(Math.min(player.getMaxHealth(), healAmount));
                player.sendMessage("§a§l第二春發動！血量回復！");
                event.setCancelled(true);
            }
        }
    }

    /**
     * 開始生命回復任務
     */
    private void startHealthRegenTask(Player player, double regenPerSecond) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) return;

            double currentHealth = player.getHealth();
            double maxHealth = player.getMaxHealth();

            if (currentHealth < maxHealth) {
                double newHealth = Math.min(maxHealth, currentHealth + regenPerSecond);
                player.setHealth(newHealth);
            }
        }, 0L, 20L); // 每秒執行一次
    }

    /**
     * 獲取玩家的天賦加成
     */
    public double getPlayerBonus(Player player, String effectName) {
        Map<String, Double> bonuses = playerBonusCache.get(player.getUniqueId());
        return bonuses != null ? bonuses.getOrDefault(effectName, 0.0) : 0.0;
    }

    /**
     * 刷新玩家的天賦效果快取
     */
    public void refreshPlayerEffects(Player player) {
        applyTalentEffects(player);
    }

    /**
     * 清理玩家快取
     */
    public void clearPlayerCache(UUID uuid) {
        playerBonusCache.remove(uuid);
    }

    /**
     * 關閉管理器
     */
    public void shutdown() {
        playerBonusCache.clear();
    }
}

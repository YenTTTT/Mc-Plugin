package com.customrpg.listeners;

import com.customrpg.managers.DamageDisplayManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * DamageDisplayListener - 監聽傷害事件並顯示傷害數字
 */
public class DamageDisplayListener implements Listener {

    private final DamageDisplayManager damageDisplayManager;

    /**
     * Constructor for DamageDisplayListener
     * @param damageDisplayManager DamageDisplayManager instance
     */
    public DamageDisplayListener(DamageDisplayManager damageDisplayManager) {
        this.damageDisplayManager = damageDisplayManager;
    }

    /**
     * 監聽玩家攻擊事件，顯示傷害訊息
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        // 取得攻擊者（可能是玩家直接攻擊，或是玩家發射的彈射物）
        Player attacker = getAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }

        // 檢查被攻擊者是否為生物（不是玩家）
        if (!(event.getEntity() instanceof LivingEntity) || event.getEntity() instanceof Player) {
            return;
        }

        LivingEntity target = (LivingEntity) event.getEntity();
        double finalDamage = event.getFinalDamage();

        // 確保傷害值有效
        if (finalDamage <= 0) {
            return;
        }

        // 檢查是否為暴擊（可以通過多種方式判斷）
        // 這裡使用簡單的判斷：玩家在空中攻擊 = 暴擊（僅限近戰）
        boolean isCritical = false;
        if (event.getDamager() instanceof Player) {
            isCritical = !attacker.isOnGround() && attacker.getFallDistance() > 0.0F;
        }

        // 創建 final 變數給 lambda 使用
        final Player finalAttacker = attacker;
        final LivingEntity finalTarget = target;
        final double displayDamage = finalDamage;
        final boolean displayCritical = isCritical;

        // 延遲 1 tick 顯示傷害，確保傷害已經計算完成
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            org.bukkit.Bukkit.getPluginManager().getPlugin("CustomRPG"),
            () -> damageDisplayManager.showDamage(finalAttacker, finalTarget, displayDamage, displayCritical),
            1L
        );
    }

    /**
     * 取得攻擊者玩家（處理直接攻擊和彈射物攻擊）
     * @param damager 傷害來源實體
     * @return 攻擊的玩家，如果不是玩家攻擊則返回 null
     */
    private Player getAttacker(Entity damager) {
        // 直接是玩家攻擊
        if (damager instanceof Player) {
            return (Player) damager;
        }

        // 彈射物攻擊（如弓箭、雪球等）
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }

        return null;
    }
}


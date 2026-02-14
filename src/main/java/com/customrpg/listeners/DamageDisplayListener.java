package com.customrpg.listeners;

import com.customrpg.managers.DamageDisplayManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

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
     * 監聽玩家攻擊事件，顯示傷害數字
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        // 檢查攻擊者是否為玩家
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        // 檢查被攻擊者是否為生物（不是玩家）
        if (!(event.getEntity() instanceof LivingEntity) || event.getEntity() instanceof Player) {
            return;
        }

        Player attacker = (Player) event.getDamager();
        double finalDamage = event.getFinalDamage();

        // 檢查是否為暴擊（可以通過多種方式判斷）
        // 這裡使用簡單的判斷：玩家在空中攻擊 = 暴擊
        boolean isCritical = !attacker.isOnGround() && attacker.getFallDistance() > 0.0F;

        // 延遲 1 tick 顯示傷害，確保傷害已經計算完成
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            org.bukkit.Bukkit.getPluginManager().getPlugin("CustomRPG"),
            () -> damageDisplayManager.showDamage(attacker, finalDamage, isCritical),
            1L
        );
    }
}


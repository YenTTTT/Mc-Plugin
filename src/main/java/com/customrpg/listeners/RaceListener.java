package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.races.RaceManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

/**
 * RaceListener - 種族系統事件監聽器
 *
 * 處理：
 * - 玩家加入 → 載入種族資料 & 套用種族屬性
 * - 玩家離開 → 儲存種族資料
 */
public class RaceListener implements Listener {

    private final CustomRPG plugin;
    private final RaceManager raceManager;
    private static final NamespacedKey BOW_BONUS=new NamespacedKey("customrpg","race_bow_multiplier");
    private static final NamespacedKey BOW_CRIT=new NamespacedKey("customrpg","race_bow_crit_chance");
    private static final NamespacedKey BOW_CRIT_DAMAGE=new NamespacedKey("customrpg","race_bow_crit_damage");

    public RaceListener(CustomRPG plugin, RaceManager raceManager) {
        this.plugin = plugin;
        this.raceManager = raceManager;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 載入種族資料
        raceManager.loadPlayerRace(player.getUniqueId());
        raceManager.markCombat(player); // Rejoining must not bypass the recovery combat delay.

        // Equipment and base stats load first; also clear obsolete bonuses for players without a valid definition.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) raceManager.applyRaceStats(player);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        raceManager.detach(event.getPlayer());
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Player player=event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin,()->{ if(player.isOnline()) raceManager.applyRaceStats(player); },1L);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void onCombat(EntityDamageEvent event) {
        if(event.isCancelled()&&!(event.getEntity() instanceof org.bukkit.entity.LivingEntity living
                &&com.customrpg.managers.MobManager.hasVirtualHP(living))) return;
        if(event.getEntity() instanceof Player player) raceManager.markCombat(player);
        if(event.getDamageSource().getCausingEntity() instanceof Player player) raceManager.markCombat(player);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBowShot(EntityShootBowEvent event) {
        if(!(event.getEntity() instanceof Player player)||!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        var race=raceManager.getPlayerRaceData(player); if(race==null) return;
        var data=arrow.getPersistentDataContainer();
        data.set(BOW_BONUS,PersistentDataType.DOUBLE,race.getWeaponBonus("BOW"));
        data.set(BOW_CRIT,PersistentDataType.DOUBLE,race.getBonusCritChance());
        data.set(BOW_CRIT_DAMAGE,PersistentDataType.DOUBLE,1.5+race.getBonusCritDamage());
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if(plugin.getCombatBalance()!=null) return; // v2 projectile snapshot already includes racial crit/affinity.
        if(!(event.getDamager() instanceof AbstractArrow arrow)) return;
        var data=arrow.getPersistentDataContainer();
        Double multiplier=data.get(BOW_BONUS,PersistentDataType.DOUBLE);
        if(multiplier==null||!Double.isFinite(multiplier)||multiplier<=0) return;
        double damage=event.getDamage()*multiplier;
        Double chance=data.get(BOW_CRIT,PersistentDataType.DOUBLE), critical=data.get(BOW_CRIT_DAMAGE,PersistentDataType.DOUBLE);
        if(chance!=null&&critical!=null&&Double.isFinite(chance)&&Double.isFinite(critical)&&critical>=1
                &&Math.random()*100<Math.min(100,Math.max(0,chance))) damage*=critical;
        event.setDamage(damage);
    }
}


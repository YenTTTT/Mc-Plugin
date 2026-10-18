package com.customrpg.balance;

import com.customrpg.managers.MobManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** RPG HP is authoritative; native HP only projects its fraction and retains Paper's death/credit pipeline. */
public final class VirtualHealthProjection {
    private static final NamespacedKey CURRENT = new NamespacedKey("customrpg",MobManager.VIRTUAL_CURRENT_HP_KEY);
    private final Map<EntityDamageEvent,Double> damageScale = new IdentityHashMap<>();
    private final Map<EntityRegainHealthEvent,Double> healScale = new IdentityHashMap<>();

    @SuppressWarnings("deprecation")
    public void projectDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)||!MobManager.hasVirtualHP(mob)) return;
        double rpg=event.getFinalDamage(), max=MobManager.getVirtualMaxHP(mob);
        if (!Double.isFinite(rpg)||rpg<=0||max<=0) return;
        double scale=mob.getMaxHealth()/max;
        double absorbed=event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)
                ?Math.max(0,-event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION)):0;
        // All mitigation was already resolved in RPG units. Keep absorption in RPG units for its native consumption.
        for (var modifier : EntityDamageEvent.DamageModifier.values()) {
            if (modifier!=EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier)) event.setDamage(modifier,0);
        }
        event.setDamage(EntityDamageEvent.DamageModifier.BASE,rpg*scale+absorbed);
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)) event.setDamage(EntityDamageEvent.DamageModifier.ABSORPTION,-absorbed);
        damageScale.put(event,scale);
    }

    /** Observe final cancellation only; never cancel, setHealth, or synthesize another lethal hit. */
    public void commitDamage(EntityDamageEvent event) {
        Double scale=damageScale.remove(event);
        if (scale==null||event.isCancelled()||event.getFinalDamage()<=0) return;
        LivingEntity mob=(LivingEntity)event.getEntity();
        double next=Math.max(0,MobManager.getVirtualCurrentHP(mob)-event.getFinalDamage()/scale);
        mob.getPersistentDataContainer().set(CURRENT,PersistentDataType.DOUBLE,next);
    }

    public void projectHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)||!MobManager.hasVirtualHP(mob)) return;
        if (!Double.isFinite(event.getAmount())||event.getAmount()<=0) return;
        double scale=mob.getMaxHealth()/MobManager.getVirtualMaxHP(mob);
        event.setAmount(event.getAmount()*scale);
        healScale.put(event,scale);
    }
    public void commitHeal(EntityRegainHealthEvent event) {
        Double scale=healScale.remove(event);
        if (scale==null||event.isCancelled()||event.getAmount()<=0) return;
        LivingEntity mob=(LivingEntity)event.getEntity();
        mob.getPersistentDataContainer().set(CURRENT,PersistentDataType.DOUBLE,
                Math.min(MobManager.getVirtualMaxHP(mob),MobManager.getVirtualCurrentHP(mob)+event.getAmount()/scale));
    }
}

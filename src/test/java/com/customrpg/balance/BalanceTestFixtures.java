package com.customrpg.balance;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;

final class BalanceTestFixtures {
    private BalanceTestFixtures() {}
    static PersistentDataContainer pdc(Map<String,Object> values) {
        return mock(PersistentDataContainer.class,invocation->{
            Object[] args=invocation.getArguments();
            String key=args.length>0&&args[0] instanceof NamespacedKey name?name.getKey():"";
            return switch(invocation.getMethod().getName()) {
                case "get" -> values.get(key);
                case "getOrDefault" -> values.getOrDefault(key,args[2]);
                case "set" -> { values.put(key,args[2]); yield null; }
                case "remove" -> { values.remove(key); yield null; }
                case "has" -> values.containsKey(key);
                case "isEmpty" -> values.isEmpty();
                case "getKeys" -> Set.of();
                default -> null;
            };
        });
    }
    @SuppressWarnings("deprecation")
    static EntityDamageByEntityEvent damage(Entity source,LivingEntity target,double amount) {
        var event=mock(EntityDamageByEntityEvent.class);
        var modifiers=new EnumMap<EntityDamageEvent.DamageModifier,Double>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE,amount);
        modifiers.put(EntityDamageEvent.DamageModifier.ARMOR,0.0);
        modifiers.put(EntityDamageEvent.DamageModifier.MAGIC,0.0);
        modifiers.put(EntityDamageEvent.DamageModifier.RESISTANCE,0.0);
        modifiers.put(EntityDamageEvent.DamageModifier.ABSORPTION,0.0);
        AtomicBoolean cancelled=new AtomicBoolean();
        when(event.getDamager()).thenReturn(source); when(event.getEntity()).thenReturn(target);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        when(event.getDamage()).thenAnswer(i->modifiers.get(EntityDamageEvent.DamageModifier.BASE));
        when(event.getOriginalDamage(EntityDamageEvent.DamageModifier.BASE)).thenReturn(amount);
        when(event.getDamage(any(EntityDamageEvent.DamageModifier.class))).thenAnswer(i->modifiers.getOrDefault(i.getArgument(0),0.0));
        when(event.getFinalDamage()).thenAnswer(i->modifiers.values().stream().mapToDouble(Double::doubleValue).sum());
        when(event.isApplicable(any())).thenAnswer(i->modifiers.containsKey(i.getArgument(0)));
        when(event.isCancelled()).thenAnswer(i->cancelled.get());
        doAnswer(i->{cancelled.set(i.getArgument(0)); return null;}).when(event).setCancelled(anyBoolean());
        doAnswer(i->{modifiers.put(EntityDamageEvent.DamageModifier.BASE,i.getArgument(0)); return null;}).when(event).setDamage(anyDouble());
        doAnswer(i->{modifiers.put(i.getArgument(0),i.getArgument(1)); return null;}).when(event).setDamage(any(EntityDamageEvent.DamageModifier.class),anyDouble());
        return event;
    }
}

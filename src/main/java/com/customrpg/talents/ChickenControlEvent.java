package com.customrpg.talents;
import org.bukkit.entity.*;
import org.bukkit.event.*;

/** Region integrations may reject one control or supply without changing the underlying damage. */
public final class ChickenControlEvent extends Event implements Cancellable {
    public enum Kind {SLOW,LURE,KNOCKBACK,INTERRUPT,CLEANSE,SUPPLY}
    private static final HandlerList HANDLERS=new HandlerList();
    private final Player caster;private final LivingEntity target;private final Kind kind;private final double amount;private final int ticks;private boolean cancelled;
    public ChickenControlEvent(Player caster,LivingEntity target,Kind kind,double amount,int ticks){this.caster=caster;this.target=target;this.kind=kind;this.amount=amount;this.ticks=ticks;}
    public Player getCaster(){return caster;}public LivingEntity getTarget(){return target;}public Kind getKind(){return kind;}public double getAmount(){return amount;}public int getTicks(){return ticks;}
    public boolean isCancelled(){return cancelled;}public void setCancelled(boolean value){cancelled=value;}public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}

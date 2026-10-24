package com.customrpg.talents;

import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityEvent;

/** Cancellable pure control, without fake zero-damage attacks. */
public final class EarthControlEvent extends EntityEvent implements Cancellable {
    public enum Kind {SLOW,PULL,KNOCKBACK}
    private static final HandlerList HANDLERS=new HandlerList();private boolean cancelled;
    private final Player source;private final Kind kind;private final double amount;private final int duration;
    public EarthControlEvent(Player source,LivingEntity target,Kind kind,double amount,int duration){super(target);this.source=source;this.kind=kind;this.amount=amount;this.duration=duration;}
    public Player getSource(){return source;}public Kind getKind(){return kind;}public double getAmount(){return amount;}public int getDurationTicks(){return duration;}
    public boolean isCancelled(){return cancelled;}public void setCancelled(boolean value){cancelled=value;}
    public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}

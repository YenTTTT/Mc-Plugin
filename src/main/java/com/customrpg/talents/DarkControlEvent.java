package com.customrpg.talents;

import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityEvent;

/** Cancellable curses and slows without manufacturing zero-damage attacks. */
public final class DarkControlEvent extends EntityEvent implements Cancellable {
    public enum Kind{SLOW,OUTGOING_DAMAGE,HEALING}
    private static final HandlerList HANDLERS=new HandlerList();private boolean cancelled;private final Player source;private final Kind kind;private final double amount;private final int duration;
    public DarkControlEvent(Player source,LivingEntity target,Kind kind,double amount,int duration){super(target);this.source=source;this.kind=kind;this.amount=amount;this.duration=duration;}
    public Player getSource(){return source;}public Kind getKind(){return kind;}public double getAmount(){return amount;}public int getDurationTicks(){return duration;}
    public boolean isCancelled(){return cancelled;}public void setCancelled(boolean v){cancelled=v;}public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}

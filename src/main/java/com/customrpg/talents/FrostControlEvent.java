package com.customrpg.talents;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityEvent;

/** Cancellable control-only event for region/PvP integrations; no synthetic potion or damage event. */
public final class FrostControlEvent extends EntityEvent implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final Player source;private final double slow;private final int ticks;private final boolean root;
    private boolean cancelled;
    public FrostControlEvent(Player source,LivingEntity target,double slow,int ticks,boolean root){super(target);this.source=source;this.slow=slow;this.ticks=ticks;this.root=root;}
    public Player getSource(){return source;}public double getSlowPercent(){return slow;}public int getDurationTicks(){return ticks;}public boolean isRoot(){return root;}
    public boolean isCancelled(){return cancelled;}public void setCancelled(boolean cancelled){this.cancelled=cancelled;}
    public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}

package com.customrpg.worldevent.core;

import com.customrpg.CustomRPG;
import com.customrpg.managers.MobManager;
import com.customrpg.worldevent.mob.EventMobSpawner;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import java.io.IOException;
import java.util.*;

/** No independent tasks. All protection, identity and contribution routing goes through the manager. */
public final class WorldEventListener implements Listener {
    private final CustomRPG plugin;
    private final WorldEventManager manager;
    private final Map<EntityDamageEvent,Double> beforeDamage=new IdentityHashMap<>();
    public WorldEventListener(CustomRPG plugin,WorldEventManager manager) { this.plugin=plugin; this.manager=manager; }
    private boolean protectedBlock(Block block) { return manager.at(block.getLocation())!=null; }
    private void revoke(Block block) {
        if(manager.provenance()==null) return;
        try { manager.provenance().revoke(block.getWorld().getUID(),block.getX()>>4,block.getZ()>>4); }
        catch(IOException e) { plugin.getLogger().severe("[WorldEvents] Terrain provenance disabled: "+e.getMessage()); }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void populate(ChunkPopulateEvent event) {
        if(manager.provenance()==null||event.getWorld().getEnvironment()!=World.Environment.NORMAL) return;
        try { manager.provenance().populated(event.getWorld().getUID(),event.getChunk().getX(),event.getChunk().getZ()); }
        catch(IOException e) { plugin.getLogger().severe("[WorldEvents] Cannot record natural chunk: "+e.getMessage()); }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent event) { if(protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void trackPlace(BlockPlaceEvent event) {
        revoke(event.getBlock());
        if(event instanceof BlockMultiPlaceEvent multi) multi.getReplacedBlockStates().forEach(state->revoke(state.getBlock()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void multiPlace(BlockMultiPlaceEvent event) {
        if(event.getReplacedBlockStates().stream().anyMatch(state->protectedBlock(state.getBlock()))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent event) { if(protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void trackBreak(BlockBreakEvent event) { revoke(event.getBlock()); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucket(PlayerBucketEmptyEvent event) {
        if(protectedBlock(event.getBlock())||protectedBlock(event.getBlockClicked())) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void trackBucket(PlayerBucketEmptyEvent event) { revoke(event.getBlock()); revoke(event.getBlockClicked()); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fill(PlayerBucketFillEvent event) { if(protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void trackFill(PlayerBucketFillEvent event) { revoke(event.getBlock()); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void flow(BlockFromToEvent event) { if(protectedBlock(event.getBlock())||protectedBlock(event.getToBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void physics(BlockPhysicsEvent event) { if(protectedBlock(event.getBlock())||protectedBlock(event.getSourceBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void grow(BlockGrowEvent event) { if(protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spread(BlockSpreadEvent event) { if(protectedBlock(event.getBlock())||protectedBlock(event.getSource())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void burn(BlockBurnEvent event) { if(protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void ignite(BlockIgniteEvent event) { if(protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void form(BlockFormEvent event) { if(protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fade(BlockFadeEvent event) { if(protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fertilize(BlockFertilizeEvent event) {
        if(event.getBlocks().stream().anyMatch(state->protectedBlock(state.getBlock()))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void structureGrow(StructureGrowEvent event) {
        if(event.getBlocks().stream().anyMatch(state->protectedBlock(state.getBlock()))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void piston(BlockPistonExtendEvent event) {
        if(protectedBlock(event.getBlock())||event.getBlocks().stream().anyMatch(b->protectedBlock(b)||protectedBlock(b.getRelative(event.getDirection())))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void trackPiston(BlockPistonExtendEvent event) { event.getBlocks().forEach(b->{revoke(b); revoke(b.getRelative(event.getDirection()));}); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void retract(BlockPistonRetractEvent event) {
        if(protectedBlock(event.getBlock())||event.getBlocks().stream().anyMatch(b->protectedBlock(b)||protectedBlock(b.getRelative(event.getDirection()))
                ||protectedBlock(b.getRelative(event.getDirection().getOppositeFace())))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void trackRetract(BlockPistonRetractEvent event) { event.getBlocks().forEach(b->{revoke(b); revoke(b.getRelative(event.getDirection())); revoke(b.getRelative(event.getDirection().getOppositeFace()));}); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(EntityExplodeEvent event) { event.blockList().removeIf(this::protectedBlock); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void trackExplosion(EntityExplodeEvent event) { event.blockList().forEach(this::revoke); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void blockExplode(BlockExplodeEvent event) { event.blockList().removeIf(this::protectedBlock); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void trackBlockExplosion(BlockExplodeEvent event) { event.blockList().forEach(this::revoke); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void change(EntityChangeBlockEvent event) { if(protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void trackChange(EntityChangeBlockEvent event) { revoke(event.getBlock()); }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void core(PlayerInteractEvent event) {
        if(event.getAction()!=Action.RIGHT_CLICK_BLOCK||event.getClickedBlock()==null) return;
        WorldEventInstance e=manager.at(event.getClickedBlock().getLocation());
        if(e==null) return;
        event.setCancelled(true); // Also block hoe/shovel/bucket/tool use; cancelled placement alone is insufficient.
        if(event.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND) return;
        Material material=event.getClickedBlock().getType();
        if(Math.abs(event.getClickedBlock().getX()-e.x)<=5&&Math.abs(event.getClickedBlock().getZ()-e.z)<=5
                &&Set.of(Material.OBSIDIAN,Material.CRYING_OBSIDIAN,Material.BLACKSTONE,Material.MAGMA_BLOCK).contains(material)) {
            event.setCancelled(true); event.getPlayer().sendMessage(manager.claim(e.id,event.getPlayer().getUniqueId()));
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void move(PlayerMoveEvent event) { guardMove(event); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event) { guardMove(event); }
    private void guardMove(PlayerMoveEvent event) {
        WorldEventInstance e=manager.at(event.getTo());
        if(e!=null&&(e.state==WorldEventState.IMPACTING||e.state==WorldEventState.CLEANING)
                &&e.contains(event.getTo().getWorld().getUID(),event.getTo().getBlockX(),event.getTo().getBlockZ(),1)) {
            if(e.contains(event.getFrom().getWorld().getUID(),event.getFrom().getBlockX(),event.getFrom().getBlockZ(),1)) {
                Location edge=manager.safeEdge(e,event.getTo().getWorld()); if(edge!=null) event.setTo(edge); else event.setCancelled(true);
            } else event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void damageBefore(EntityDamageEvent event) {
        WorldEventInstance e=manager.find(EventMobSpawner.eventId(event.getEntity()));
        if(e==null) return;
        if(e.state!=WorldEventState.ACTIVE) { event.setCancelled(true); return; }
        if(event.getEntity() instanceof LivingEntity living) beforeDamage.put(event,MobManager.hasVirtualHP(living)?MobManager.getVirtualCurrentHP(living):living.getHealth());
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void damageAfter(EntityDamageEvent event) {
        Double before=beforeDamage.remove(event); if(before==null) return;
        Entity source=event.getDamageSource().getCausingEntity();
        UUID player=source instanceof Player p?p.getUniqueId():source instanceof Tameable pet&&pet.getOwner()!=null?pet.getOwner().getUniqueId():null;
        if(player==null) return;
        double amount;
        if(event.isCancelled()) {
            if(!(event.getEntity() instanceof LivingEntity living)||!MobManager.hasVirtualHP(living)) return;
            amount=before-MobManager.getVirtualCurrentHP(living); // Existing virtual-HP listener owns this cancellation.
        } else amount=Math.min(before,Math.max(0,event.getFinalDamage()));
        manager.damage(event.getEntity(),player,amount);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void death(EntityDeathEvent event) {
        if(EventMobSpawner.eventId(event.getEntity())!=null) {
            manager.death(event.getEntity()); event.getDrops().clear(); event.setDroppedExp(0);
        }
    }
    @EventHandler public void load(EntitiesLoadEvent event) { event.getEntities().forEach(manager::onEntityLoad); }
    @EventHandler public void unload(EntitiesUnloadEvent event) { event.getEntities().forEach(manager::onEntityUnload); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void target(EntityTargetLivingEntityEvent event) {
        WorldEventInstance e=manager.find(EventMobSpawner.eventId(event.getEntity()));
        if(e!=null&&event.getTarget()!=null&&(e.state!=WorldEventState.ACTIVE||!e.contains(event.getTarget().getWorld().getUID(),
                event.getTarget().getLocation().getBlockX(),event.getTarget().getLocation().getBlockZ(),0))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void mobTeleport(EntityTeleportEvent event) {
        WorldEventInstance e=manager.find(EventMobSpawner.eventId(event.getEntity()));
        if(e!=null&&event.getTo()!=null&&!e.contains(event.getTo().getWorld().getUID(),event.getTo().getBlockX(),event.getTo().getBlockZ(),2)) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void mobMove(io.papermc.paper.event.entity.EntityMoveEvent event) {
        WorldEventInstance e=manager.find(EventMobSpawner.eventId(event.getEntity()));
        if(e!=null&&!e.contains(event.getTo().getWorld().getUID(),event.getTo().getBlockX(),event.getTo().getBlockZ(),2)) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void arenaSpawn(CreatureSpawnEvent event) {
        WorldEventInstance e=manager.at(event.getLocation());
        if(e!=null&&!e.id.equals(EventMobSpawner.eventId(event.getEntity()))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void portal(PortalCreateEvent event) {
        if(event.getBlocks().stream().anyMatch(state->protectedBlock(state.getBlock()))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void worldUnload(WorldUnloadEvent event) {
        for(WorldEventInstance instance:manager.events()) if(instance.world.equals(event.getWorld().getUID())&&instance.reserved()) manager.stop(instance.id);
    }
}

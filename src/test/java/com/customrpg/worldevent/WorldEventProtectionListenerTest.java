package com.customrpg.worldevent;

import com.customrpg.CustomRPG;
import com.customrpg.worldevent.core.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldEventProtectionListenerTest {
    @Test void movementBelowIslandIsFreeWhileVerticalEntryDuringConstructionIsBlocked() {
        var manager=mock(WorldEventManager.class); var listener=new WorldEventListener(mock(CustomRPG.class),manager);
        var meteor=WorldEventLifecycleTest.event(); meteor.generatedArena=true; meteor.state=WorldEventState.IMPACTING;
        World world=mock(World.class); when(world.getUID()).thenReturn(meteor.world);
        when(manager.at(any())).thenAnswer(i->WorldEventManager.occupies(meteor,i.getArgument(0),4)?meteor:null);
        Location below=new Location(world,meteor.x,meteor.y-15,meteor.z);
        var walk=mock(PlayerMoveEvent.class); when(walk.getFrom()).thenReturn(below); when(walk.getTo()).thenReturn(below.clone().add(0.5,0,0));
        listener.move(walk); verify(walk,never()).setTo(any()); verify(walk,never()).setCancelled(true);
        var enter=mock(PlayerMoveEvent.class); when(enter.getFrom()).thenReturn(below); when(enter.getTo()).thenReturn(new Location(world,meteor.x,meteor.y,meteor.z));
        listener.move(enter); verify(enter).setTo(any(Location.class));
    }
    @Test void offhandToolsCannotMutateReservedTerrainOrClaimTwice() {
        var manager=mock(WorldEventManager.class); var listener=new WorldEventListener(mock(CustomRPG.class),manager);
        var interact=mock(PlayerInteractEvent.class); Block block=mock(Block.class);
        when(interact.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK); when(interact.getClickedBlock()).thenReturn(block);
        when(interact.getHand()).thenReturn(org.bukkit.inventory.EquipmentSlot.OFF_HAND);
        when(manager.at(any())).thenReturn(WorldEventLifecycleTest.event());
        listener.core(interact); verify(interact).setCancelled(true); verify(manager,never()).claim(any(),any());
    }
    @Test void arenaRejectsOrdinaryNaturalMobsAndUnrelatedCustomSpawns() {
        var manager=mock(WorldEventManager.class); var listener=new WorldEventListener(mock(CustomRPG.class),manager);
        var event=mock(CreatureSpawnEvent.class); LivingEntity entity=mock(LivingEntity.class);
        when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class)); when(event.getEntity()).thenReturn(entity);
        when(manager.at(any())).thenReturn(WorldEventLifecycleTest.event());
        listener.arenaSpawn(event); verify(event).setCancelled(true);
    }
    @Test void constructionNeverCancelsCameraRotationOrTrapsAnInsidePlayerWithoutAnExit() {
        var manager=mock(WorldEventManager.class); var listener=new WorldEventListener(mock(CustomRPG.class),manager);
        var meteor=WorldEventLifecycleTest.event(); meteor.state=WorldEventState.IMPACTING;
        World world=mock(World.class); when(world.getUID()).thenReturn(meteor.world);
        Location from=new Location(world,meteor.x+0.5,meteor.y,meteor.z+0.5,0,0);
        Location look=from.clone(); look.setYaw(90); look.setPitch(30);
        var rotation=mock(PlayerMoveEvent.class); when(rotation.getFrom()).thenReturn(from); when(rotation.getTo()).thenReturn(look);
        listener.move(rotation); verify(rotation,never()).setCancelled(true); verify(rotation,never()).setTo(any());
        Location step=look.clone().add(0.2,0,0);
        var movement=mock(PlayerMoveEvent.class); when(movement.getFrom()).thenReturn(from); when(movement.getTo()).thenReturn(step);
        when(manager.at(step)).thenReturn(meteor);
        listener.move(movement); verify(movement,never()).setCancelled(true); verify(movement,never()).setTo(any());
    }
    @Test void blockedEntryPreservesCameraRotation() {
        var manager=mock(WorldEventManager.class); var listener=new WorldEventListener(mock(CustomRPG.class),manager);
        var meteor=WorldEventLifecycleTest.event(); meteor.state=WorldEventState.CLEANING;
        World world=mock(World.class); when(world.getUID()).thenReturn(meteor.world);
        Location from=new Location(world,meteor.x+meteor.radius+8,meteor.y,meteor.z,0,0);
        Location to=new Location(world,meteor.x,meteor.y,meteor.z,125,-15);
        var movement=mock(PlayerMoveEvent.class); when(movement.getFrom()).thenReturn(from); when(movement.getTo()).thenReturn(to);
        when(manager.at(to)).thenReturn(meteor);
        listener.move(movement);
        var destination=org.mockito.ArgumentCaptor.forClass(Location.class);
        verify(movement).setTo(destination.capture());
        assertEquals(from.getX(),destination.getValue().getX());
        assertEquals(125,destination.getValue().getYaw());
        assertEquals(-15,destination.getValue().getPitch());
        verify(movement,never()).setCancelled(true);
    }
}

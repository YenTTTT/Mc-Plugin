package com.customrpg.worldevent;

import com.customrpg.CustomRPG;
import com.customrpg.worldevent.core.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class WorldEventProtectionListenerTest {
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
}

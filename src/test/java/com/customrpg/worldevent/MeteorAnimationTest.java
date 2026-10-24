package com.customrpg.worldevent;

import com.customrpg.worldevent.meteor.MeteorAnimationService;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeteorAnimationTest {
    @Test void oneDisplayFallsAndIsRemovedExactlyOnceWithoutIndependentScheduler() {
        World world=mock(World.class); BlockDisplay display=mock(BlockDisplay.class);
        when(world.getMaxHeight()).thenReturn(320); when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(display.isValid()).thenReturn(true); when(display.teleport(any(Location.class))).thenReturn(true);
        when(world.spawn(any(Location.class),eq(BlockDisplay.class),any(Consumer.class))).thenAnswer(i->{
            Consumer<BlockDisplay> initializer=i.getArgument(2); initializer.accept(display); return display;
        });
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.createBlockData(Material.MAGMA_BLOCK)).thenReturn(mock(BlockData.class));
            AtomicInteger tags=new AtomicInteger();
            var animation=new MeteorAnimationService(null,WorldEventLifecycleTest.event(),world,4,80,true,false,false,entity->tags.incrementAndGet());
            assertFalse(animation.tick()); assertFalse(animation.tick()); assertFalse(animation.tick()); assertTrue(animation.tick());
            animation.close();
            assertEquals(1,tags.get()); verify(display).remove(); verify(display,times(4)).teleport(any(Location.class));
            bukkit.verify(Bukkit::getScheduler,never());
        }
    }
    @Test void unloadedChunkAbortsAnimationAndCloseRemovesDisplay() {
        World world=mock(World.class); BlockDisplay display=mock(BlockDisplay.class);
        when(world.getMaxHeight()).thenReturn(320); when(display.isValid()).thenReturn(true);
        when(world.spawn(any(Location.class),eq(BlockDisplay.class),any(Consumer.class))).thenReturn(display);
        try(var bukkit=mockStatic(Bukkit.class)) {
            var animation=new MeteorAnimationService(null,WorldEventLifecycleTest.event(),world,4,80,true,false,false,entity->{});
            assertThrows(IllegalStateException.class,animation::tick); animation.close();
            verify(display).remove(); bukkit.verify(Bukkit::getScheduler,never());
        }
    }
    @Test void talentCanReuseTheSameDisplayWithoutCreatingAWorldEvent() {
        World world=mock(World.class); BlockDisplay display=mock(BlockDisplay.class);
        when(world.getMaxHeight()).thenReturn(320); when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(display.isValid()).thenReturn(true); when(display.teleport(any(Location.class))).thenReturn(true);
        when(world.spawn(any(Location.class),eq(BlockDisplay.class),any(Consumer.class))).thenAnswer(i->{
            Consumer<BlockDisplay> initializer=i.getArgument(2); initializer.accept(display); return display;
        });
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.createBlockData(Material.MAGMA_BLOCK)).thenReturn(mock(BlockData.class));
            var animation=new MeteorAnimationService(new Location(world,10,70,20),15,30,false,false);
            var start=org.mockito.ArgumentCaptor.forClass(Location.class);
            verify(world).spawn(start.capture(),eq(BlockDisplay.class),any(Consumer.class));
            assertEquals(99,start.getValue().getY());
            for(int tick=1;tick<15;tick++) assertFalse(animation.tick());
            assertTrue(animation.tick()); animation.close();
            var positions=org.mockito.ArgumentCaptor.forClass(Location.class);
            verify(display,times(15)).teleport(positions.capture());
            assertEquals(70,positions.getAllValues().getLast().getY());
            verify(display).remove();
        }
    }
}

package com.customrpg.worldevent.location;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.customrpg.worldevent.core.WorldEventInstance;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.EventBus;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.lang.reflect.InvocationTargetException;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.biome.BiomeType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaweTerrainHookTest {
    @Test void asyncEditsCannotTouchReservedAreaButCanEditElsewhere() throws Exception {
        var plugin=mock(CustomRPG.class); var server=mock(Server.class); var plugins=mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(plugins);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.getSafeZoneManager()).thenReturn(mock(SafeZoneManager.class));
        when(plugin.getProtectionAreaManager()).thenReturn(mock(ProtectionAreaManager.class));
        when(plugin.getBossZoneManager()).thenReturn(mock(BossZoneManager.class));
        when(plugins.isPluginEnabled("FastAsyncWorldEdit")).thenReturn(true);
        var editor=mock(WorldEdit.class); var bus=new EventBus(); when(editor.getEventBus()).thenReturn(bus);
        var world=mock(World.class); when(world.getName()).thenReturn("world");
        var weWorld=mock(com.sk89q.worldedit.world.World.class); when(weWorld.getName()).thenReturn("world");
        var event=new WorldEventInstance(UUID.randomUUID(),"meteor",UUID.randomUUID(),"world",
                1000,65,1000,12,8,20,15,42,1000,61_000);
        var delegate=mock(Extent.class); var block=mock(BlockState.class);
        BlockVector3 inside=BlockVector3.at(1000,65,1000),outside=BlockVector3.at(1020,65,1000);
        var entityLocation=new com.sk89q.worldedit.util.Location(weWorld,1000,65,1000);
        var baseEntity=mock(BaseEntity.class);
        when(delegate.setBlock(outside,block)).thenReturn(true);
        try(var singleton=mockStatic(WorldEdit.class)) {
            singleton.when(WorldEdit::getInstance).thenReturn(editor);
            var validator=new ProtectionValidator(plugin);
            validator.reserve(event,4);
            validator.enableIntegrations(null,ignored->true);
            assertNull(validator.rejection(new Location(world,1000,65,1000)));
            for(var stage:new EditSession.Stage[]{EditSession.Stage.BEFORE_CHANGE,EditSession.Stage.BEFORE_HISTORY}) {
                var session=new EditSessionEvent(weWorld,null,-1,stage); session.setExtent(delegate); bus.post(session);
                assertThrows(IllegalStateException.class,()->session.getExtent().setBlock(inside,block));
                assertRejected(()->session.getExtent().getClass()
                        .getMethod("setBlock",int.class,int.class,int.class,BlockStateHolder.class)
                        .invoke(session.getExtent(),1000,65,1000,block));
                assertThrows(IllegalStateException.class,()->session.getExtent().setBiome(inside,null));
                assertRejected(()->session.getExtent().getClass()
                        .getMethod("setBiome",int.class,int.class,int.class,BiomeType.class)
                        .invoke(session.getExtent(),1000,65,1000,null));
                assertThrows(IllegalStateException.class,()->session.getExtent().createEntity(entityLocation,baseEntity));
                assertRejected(()->session.getExtent().getClass()
                        .getMethod("createEntity",com.sk89q.worldedit.util.Location.class,BaseEntity.class,UUID.class)
                        .invoke(session.getExtent(),entityLocation,baseEntity,UUID.randomUUID()));
                assertTrue(session.getExtent().setBlock(outside,block));
                assertEquals(true,session.getExtent().getClass()
                        .getMethod("setBlock",int.class,int.class,int.class,BlockStateHolder.class)
                        .invoke(session.getExtent(),1020,65,1000,block));
            }
            verify(delegate,never()).setBlock(inside,block);
            verify(delegate,times(4)).setBlock(outside,block);
            validator.release(event.id);
            var released=new EditSessionEvent(weWorld,null,-1,EditSession.Stage.BEFORE_CHANGE);
            released.setExtent(delegate); bus.post(released);
            released.getExtent().setBlock(inside,block);
            verify(delegate).setBlock(inside,block);
            validator.close();
            assertThrows(IllegalStateException.class,()->released.getExtent().setBlock(outside,block));
        }
    }
    private static void assertRejected(org.junit.jupiter.api.function.Executable edit) {
        var failure=assertThrows(InvocationTargetException.class,edit);
        assertInstanceOf(IllegalStateException.class,failure.getCause());
    }
    @Test void failedFaweRegistrationStillBlocksNormalEvents() {
        var plugin=mock(CustomRPG.class); var server=mock(Server.class); var plugins=mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(plugins);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.getSafeZoneManager()).thenReturn(mock(SafeZoneManager.class));
        when(plugin.getProtectionAreaManager()).thenReturn(mock(ProtectionAreaManager.class));
        when(plugin.getBossZoneManager()).thenReturn(mock(BossZoneManager.class));
        when(plugins.isPluginEnabled("FastAsyncWorldEdit")).thenReturn(true);
        var editor=mock(WorldEdit.class); var bus=mock(EventBus.class); when(editor.getEventBus()).thenReturn(bus);
        doThrow(new IllegalArgumentException("incompatible FAWE API")).when(bus).register(any(FaweTerrainHook.class));
        try(var singleton=mockStatic(WorldEdit.class)) {
            singleton.when(WorldEdit::getInstance).thenReturn(editor);
            var validator=new ProtectionValidator(plugin); validator.enableIntegrations(null,ignored->true);
            var site=new Location(mock(World.class),1000,65,1000);
            assertTrue(validator.rejection(site).contains("incompatible FAWE API"));
            assertNull(validator.rejectionForTesting(site));
            verify(bus).unregister(any(FaweTerrainHook.class));
            validator.close();
        }
    }
}

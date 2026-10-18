package com.customrpg.worldevent.location;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.EventBus;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.*;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldEditTerrainHookTest {
    @TempDir Path directory;
    World world;
    com.sk89q.worldedit.world.World weWorld;
    NaturalTerrainRegistry terrain;
    Extent delegate;
    BlockState block;
    BlockVector3 position=BlockVector3.at(32,65,48);
    MockedStatic<BukkitAdapter> adapters;
    MockedStatic<Bukkit> bukkit;

    @BeforeEach void setup() throws Exception {
        world=mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        weWorld=mock(com.sk89q.worldedit.world.World.class);
        terrain=new NaturalTerrainRegistry(directory.resolve("chunks.yml")); terrain.populated(world.getUID(),2,3);
        delegate=mock(Extent.class); block=mock(BlockState.class);
        adapters=mockStatic(BukkitAdapter.class); adapters.when(()->BukkitAdapter.adapt(weWorld)).thenReturn(world);
        bukkit=mockStatic(Bukkit.class); bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
    }
    @AfterEach void teardown() { bukkit.close(); adapters.close(); }
    private EditSessionEvent event() {
        var event=new EditSessionEvent(weWorld,null,-1,EditSession.Stage.BEFORE_CHANGE); event.setExtent(delegate); return event;
    }
    @Test void editorWriteDurablyRevokesNaturalChunkBeforeDelegating() throws Exception {
        when(delegate.setBlock(position,block)).thenAnswer(invocation->{
            assertFalse(new NaturalTerrainRegistry(directory.resolve("chunks.yml")).eligible(world.getUID(),2,3)); return true;
        });
        var hook=new WorldEditTerrainHook(terrain,ignored->true); var event=event(); hook.onEditSession(event);
        assertTrue(event.getExtent().setBlock(position,block)); verify(delegate).setBlock(position,block);
    }
    @Test void reservedEventAreaAndAsyncWritesAreRejectedWithoutWorldMutation() throws Exception {
        var denied=new WorldEditTerrainHook(terrain,ignored->false); var event=event(); denied.onEditSession(event);
        assertThrows(IllegalStateException.class,()->event.getExtent().setBlock(position,block));
        var allowed=new WorldEditTerrainHook(terrain,ignored->true); var async=event(); allowed.onEditSession(async);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        assertThrows(IllegalStateException.class,()->async.getExtent().setBlock(position,block));
        verify(delegate,never()).setBlock(any(),any(BlockState.class)); assertTrue(terrain.eligible(world.getUID(),2,3));
    }
    @Test void failedRevocationSavePreventsEditorFromWriting() throws Exception {
        UUID worldId=world.getUID(); var failing=spy(terrain); doThrow(new IOException("disk full")).when(failing).revoke(worldId,2,3);
        var hook=new WorldEditTerrainHook(failing,ignored->true); var event=event(); hook.onEditSession(event);
        assertThrows(IllegalStateException.class,()->event.getExtent().setBlock(position,block));
        verify(delegate,never()).setBlock(any(),any(BlockState.class));
    }
    @Test void failedWorldAdaptationDisablesNewCandidatesAndBlocksTheEdit() throws Exception {
        adapters.when(()->BukkitAdapter.adapt(weWorld)).thenThrow(new IllegalArgumentException("unknown world"));
        var hook=new WorldEditTerrainHook(terrain,ignored->true); var event=event(); hook.onEditSession(event);
        assertFalse(hook.active());
        assertThrows(IllegalStateException.class,()->event.getExtent().setBlock(position,block));
        verify(delegate,never()).setBlock(any(),any(BlockState.class));
    }
    @Test void eventBusRegistrationAndCloseRemoveListenerAndDisableOldSession() throws Exception {
        var editor=mock(WorldEdit.class); EventBus bus=new EventBus(); when(editor.getEventBus()).thenReturn(bus);
        try(var singleton=mockStatic(WorldEdit.class)) {
            singleton.when(WorldEdit::getInstance).thenReturn(editor);
            var hook=new WorldEditTerrainHook(terrain,ignored->true); hook.register();
            var event=event(); bus.post(event); assertNotSame(delegate,event.getExtent()); hook.close();
            assertThrows(IllegalStateException.class,()->event.getExtent().setBlock(position,block));
            var next=event(); bus.post(next); assertSame(delegate,next.getExtent());
            verify(delegate,never()).setBlock(any(),any(BlockState.class));
        }
    }
    @Test void standardWorldEditAutomaticallyConnectsWhileFaweStillFailsClosed() {
        var plugin=mock(CustomRPG.class); Server server=mock(Server.class); var plugins=mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(plugins);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.getSafeZoneManager()).thenReturn(mock(SafeZoneManager.class));
        when(plugin.getProtectionAreaManager()).thenReturn(mock(ProtectionAreaManager.class));
        when(plugin.getBossZoneManager()).thenReturn(mock(BossZoneManager.class));
        when(plugins.isPluginEnabled("WorldEdit")).thenReturn(true);
        var editor=mock(WorldEdit.class); var bus=mock(EventBus.class); when(editor.getEventBus()).thenReturn(bus);
        try(var singleton=mockStatic(WorldEdit.class)) {
            singleton.when(WorldEdit::getInstance).thenReturn(editor);
            var validator=new ProtectionValidator(plugin); validator.enableIntegrations(terrain,ignored->true);
            assertNull(validator.rejection(new Location(world,32,65,48))); verify(bus).register(any(WorldEditTerrainHook.class));
            validator.close(); verify(bus).unregister(any(WorldEditTerrainHook.class));
            when(plugins.isPluginEnabled("FastAsyncWorldEdit")).thenReturn(true);
            var fawe=new ProtectionValidator(plugin); fawe.enableIntegrations(terrain,ignored->true);
            var location=new Location(world,32,65,48);
            assertTrue(fawe.rejection(location).contains("FastAsyncWorldEdit (FAWE) is enabled"));
            assertTrue(fawe.diagnostic().contains("asynchronous terrain edit tracking is not supported"));
            assertNull(fawe.rejectionForTesting(location)); fawe.close();
        }
    }
    @Test void failedRegistrationReportsCauseAndVersionWhileAllowingClaimCheckedAdminTest() {
        var plugin=mock(CustomRPG.class); var server=mock(Server.class); var plugins=mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(plugins);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.getSafeZoneManager()).thenReturn(mock(SafeZoneManager.class));
        when(plugin.getProtectionAreaManager()).thenReturn(mock(ProtectionAreaManager.class));
        when(plugin.getBossZoneManager()).thenReturn(mock(BossZoneManager.class));
        when(plugins.isPluginEnabled("WorldEdit")).thenReturn(true);
        var installed=mock(org.bukkit.plugin.Plugin.class);
        when(installed.getDescription()).thenReturn(new org.bukkit.plugin.PluginDescriptionFile("WorldEdit","7.3.9","test.WorldEdit"));
        when(plugins.getPlugin("WorldEdit")).thenReturn(installed);
        var editor=mock(WorldEdit.class); var bus=mock(EventBus.class); when(editor.getEventBus()).thenReturn(bus);
        doThrow(new IllegalArgumentException("incompatible event subscriber")).when(bus).register(any());
        try(var singleton=mockStatic(WorldEdit.class)) {
            singleton.when(WorldEdit::getInstance).thenReturn(editor);
            var validator=new ProtectionValidator(plugin); validator.enableIntegrations(terrain,ignored->true);
            var location=new Location(world,32,65,48);
            assertTrue(validator.rejection(location).contains("IllegalArgumentException: incompatible event subscriber"));
            assertTrue(validator.diagnostic().contains("WorldEdit 7.3.9"));
            assertTrue(validator.diagnostic().contains("incompatible event subscriber"));
            verify(bus).unregister(any(WorldEditTerrainHook.class));
            assertNull(validator.rejectionForTesting(location));
            when(plugins.isPluginEnabled("WorldGuard")).thenReturn(true);
            validator.register("WorldGuard",ignored->false);
            assertEquals("WorldGuard denied terrain edits",validator.rejectionForTesting(location));
            validator.close();
        }
    }
}

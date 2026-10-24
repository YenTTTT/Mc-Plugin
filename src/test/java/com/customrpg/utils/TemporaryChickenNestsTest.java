package com.customrpg.utils;
import com.customrpg.CustomRPG;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemporaryChickenNestsTest {
    @TempDir Path folder;CustomRPG plugin;World world;Server server;Player player;Block block;PluginManager events;TemporaryChickenNests nests;MockedStatic<Bukkit> bukkit;BlockData air;Location at;AtomicReference<Material> material;
    @BeforeEach void setup(){
        plugin=mock(CustomRPG.class);server=mock(Server.class);world=mock(World.class);player=mock(Player.class);block=mock(Block.class);events=mock(PluginManager.class);when(plugin.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(events);when(plugin.getDataFolder()).thenReturn(folder.toFile());when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());UUID id=UUID.randomUUID();when(world.getUID()).thenReturn(id);when(server.getWorld(id)).thenReturn(world);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);var border=mock(WorldBorder.class);when(world.getWorldBorder()).thenReturn(border);when(border.isInside(any())).thenReturn(true);
        at=new Location(world,2.5,64,2.5);when(world.getBlockAt(any(Location.class))).thenReturn(block);when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);when(block.getWorld()).thenReturn(world);when(block.getX()).thenReturn(2);when(block.getY()).thenReturn(64);when(block.getZ()).thenReturn(2);when(block.getLocation()).thenReturn(at);when(block.getState()).thenReturn(mock(BlockState.class));var below=mock(Block.class);when(block.getRelative(BlockFace.DOWN)).thenReturn(below);when(below.isSolid()).thenReturn(true);
        material=new AtomicReference<>(Material.AIR);when(block.getType()).thenAnswer(i->material.get());doAnswer(i->{material.set(i.getArgument(0));return null;}).when(block).setType(any(),eq(false));air=mock(BlockData.class);when(air.getAsString()).thenReturn("minecraft:air");when(air.getMaterial()).thenReturn(Material.AIR);when(block.getBlockData()).thenReturn(air);doAnswer(i->{material.set(((BlockData)i.getArgument(0)).getMaterial());return null;}).when(block).setBlockData(any(),eq(false));bukkit=mockStatic(Bukkit.class);bukkit.when(()->Bukkit.createBlockData("minecraft:air")).thenReturn(air);nests=manager();nests.start();
    }
    TemporaryChickenNests manager(){return new TemporaryChickenNests(plugin,p->false,()->mock(ItemStack.class));}
    @AfterEach void close(){bukkit.close();}
    @Test void placementHasCollisionAndWritesJournalBeforeWorldMutationThenRestoresExactlyOnce()throws Exception{
        doAnswer(i->{assertTrue(Files.readString(folder.resolve("chicken-nests.yml")).contains("minecraft:air"));material.set(Material.COMPOSTER);return null;}).when(block).setType(Material.COMPOSTER,false);
        var lease=nests.place(player,at);assertNotNull(lease);assertTrue(lease.active());assertEquals(Material.COMPOSTER,material.get());lease.close();lease.close();assertEquals(Material.AIR,material.get());verify(block,times(1)).setBlockData(air,false);assertFalse(Files.readString(folder.resolve("chicken-nests.yml")).contains("minecraft:air"));
    }
    @Test void protectedPlaceEventOrOccupiedPositionCannotConsumeTerrain()throws Exception{
        doAnswer(i->{if(i.getArgument(0) instanceof BlockPlaceEvent e){assertEquals(Material.COMPOSTER,e.getBlock().getType());e.setCancelled(true);}return null;}).when(events).callEvent(any());assertNull(nests.place(player,at));assertEquals(Material.AIR,material.get());verify(block).setBlockData(air,false);reset(events);material.set(Material.STONE);assertNull(nests.place(player,at));assertEquals(Material.STONE,material.get());
    }
    @Test void crashJournalRestoresAComposterAtNextStartup()throws Exception{
        assertNotNull(nests.place(player,at));assertEquals(Material.COMPOSTER,material.get());var restarted=manager();restarted.start();assertEquals(Material.AIR,material.get());assertFalse(Files.readString(folder.resolve("chicken-nests.yml")).contains("minecraft:air"));
    }
    @Test void blockEditedByAnotherSystemIsNeverOverwrittenOnCleanup(){var lease=nests.place(player,at);material.set(Material.DIAMOND_BLOCK);lease.close();assertEquals(Material.DIAMOND_BLOCK,material.get());verify(block,never()).setBlockData(any(),anyBoolean());}
    @Test void chunkUnloadRestoresAndNeverLoadsAnotherChunk(){nests.place(player,at);var chunk=mock(Chunk.class);when(chunk.getX()).thenReturn(0);when(chunk.getZ()).thenReturn(0);var event=mock(ChunkUnloadEvent.class);when(event.getWorld()).thenReturn(world);when(event.getChunk()).thenReturn(chunk);nests.unload(event);assertEquals(Material.AIR,material.get());verify(world,never()).getChunkAt(anyInt(),anyInt());}
    @Test void breakInteractionAndExplosionCannotFarmTheTemporaryBlock(){nests.place(player,at);var breaking=mock(BlockBreakEvent.class);when(breaking.getBlock()).thenReturn(block);nests.breakBlock(breaking);verify(breaking).setCancelled(true);var event=mock(BlockExplodeEvent.class);var affected=new ArrayList<>(List.of(block));when(event.blockList()).thenReturn(affected);nests.explode(event);assertTrue(affected.isEmpty());}
    @Test void shutdownRestoresAllActiveBlocks(){nests.place(player,at);nests.shutdown();assertEquals(Material.AIR,material.get());}
    @Test void malformedRestorationJournalIsPreservedAndPlacementRemainsDisabled()throws Exception{String broken="blocks: [invalid";Files.writeString(folder.resolve("chicken-nests.yml"),broken);var restarted=manager();restarted.start();assertNull(restarted.place(player,at));restarted.shutdown();assertEquals(broken,Files.readString(folder.resolve("chicken-nests.yml")));}
    @Test void pendingRestorationWaitsForItsChunkWithoutForcingALoad(){nests.place(player,at);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);var restarted=manager();restarted.start();assertEquals(Material.COMPOSTER,material.get());verify(world,never()).getChunkAt(anyInt(),anyInt());when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);var chunk=mock(Chunk.class);when(chunk.getX()).thenReturn(0);when(chunk.getZ()).thenReturn(0);var event=mock(ChunkLoadEvent.class);when(event.getWorld()).thenReturn(world);when(event.getChunk()).thenReturn(chunk);restarted.load(event);assertEquals(Material.AIR,material.get());}

    @Test void closingAnOldLeaseCannotEraseANewNestAtTheSameCoordinates(){var old=nests.place(player,at);old.close();var current=nests.place(player,at);assertNotNull(current);old.close();assertTrue(current.active());assertEquals(Material.COMPOSTER,material.get());current.close();assertEquals(Material.AIR,material.get());}

}

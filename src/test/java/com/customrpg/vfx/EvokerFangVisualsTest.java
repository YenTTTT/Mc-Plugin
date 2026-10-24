package com.customrpg.vfx;

import com.customrpg.CustomRPG;
import com.customrpg.vfx.VfxRuntime.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.*;
import org.bukkit.scheduler.*;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvokerFangVisualsTest {
    CustomRPG plugin;Server server;World world;Player owner,viewer;BukkitVfxService service;Runnable clock;BukkitTask task;
    final List<EvokerFangs> spawned=new ArrayList<>();final List<Location> locations=new ArrayList<>();boolean reject;
    @BeforeEach @SuppressWarnings("unchecked") void setup(){
        plugin=mock(CustomRPG.class);server=mock(Server.class);world=mock(World.class);owner=player();viewer=player();
        when(plugin.getServer()).thenReturn(server);when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(server.getWorlds()).thenReturn(List.of(world));when(world.getUID()).thenReturn(UUID.randomUUID());when(server.getWorld(world.getUID())).thenReturn(world);when(server.getPlayer(owner.getUniqueId())).thenReturn(owner);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        var border=mock(WorldBorder.class);when(world.getWorldBorder()).thenReturn(border);when(border.isInside(any())).thenReturn(true);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->{int x=i.getArgument(0),y=i.getArgument(1),z=i.getArgument(2);var b=mock(Block.class);when(b.isPassable()).thenReturn(y>=64);when(b.getBoundingBox()).thenReturn(new BoundingBox(x,y,z,x+1,y+1,z+1));return b;});
        when(world.getNearbyPlayers(any(Location.class),eq(32d))).thenReturn(List.of(owner,viewer));
        when(world.spawn(any(Location.class),eq(EvokerFangs.class),eq(CreatureSpawnEvent.SpawnReason.CUSTOM),any())).thenAnswer(i->{
            var fang=mock(EvokerFangs.class);when(fang.getUniqueId()).thenReturn(UUID.randomUUID());when(fang.getWorld()).thenReturn(world);var pdc=mock(PersistentDataContainer.class);when(fang.getPersistentDataContainer()).thenReturn(pdc);var tag=new AtomicBoolean();
            doAnswer(x->{tag.set(true);return null;}).when(pdc).set(eq(EvokerFangVisuals.VISUAL),eq(PersistentDataType.BYTE),eq((byte)1));when(pdc.has(EvokerFangVisuals.VISUAL,PersistentDataType.BYTE)).thenAnswer(x->tag.get());
            var valid=new AtomicBoolean(!reject);when(fang.isValid()).thenAnswer(x->valid.get());doAnswer(x->{valid.set(false);return null;}).when(fang).remove();
            Consumer<EvokerFangs> consumer=i.getArgument(3);consumer.accept(fang);assertTrue(tag.get(),"Tag must exist before the spawn returns");spawned.add(fang);locations.add(((Location)i.getArgument(0)).clone());return fang;
        });
        var scheduler=mock(BukkitScheduler.class);task=mock(BukkitTask.class);when(server.getScheduler()).thenReturn(scheduler);when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),eq(1L),eq(1L))).thenAnswer(i->{clock=i.getArgument(1);return task;});
        service=new BukkitVfxService(plugin);service.start();
    }
    Player player(){var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getWorld()).thenAnswer(i->world);when(p.isOnline()).thenReturn(true);when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);when(p.getLocation()).thenAnswer(i->new Location(world,0,64,0));when(p.getEyeLocation()).thenAnswer(i->new Location(world,0,65.6,0));when(p.canSee(any(Player.class))).thenReturn(true);return p;}
    boolean preview(){return service.preview(owner,"necro_lance",0);}
    @Test void previewCreatesSevenNativeJawsWithStaggeredWarmupVisibleOnlyToItsOwner(){
        assertTrue(preview());assertEquals(7,spawned.size());assertEquals(7,service.fangCount());
        for(int i=0;i<7;i++){var e=spawned.get(i);verify(e).setOwner(null);verify(e).setAttackDelay(5+i);verify(e).setPersistent(false);verify(e).setVisibleByDefault(false);verify(e).setSilent(true);verify(owner).showEntity(plugin,e);assertEquals(64.01,locations.get(i).getY(),1e-8);assertEquals(i*2+1,locations.get(i).getZ(),1e-8);}
        verify(viewer,never()).showEntity(any(),any());verify(owner,never()).damage(anyDouble());verify(owner,never()).setHealth(anyDouble());
    }
    @Test void bothDamageGuardsBlockTaggedNativeBitesAndLeaveOrdinaryEvokerFangsAlone(){
        preview();var hit=mock(EntityDamageByEntityEvent.class);when(hit.getDamager()).thenReturn(spawned.get(0));service.preventFangDamage(hit);service.enforceFangDamage(hit);verify(hit,times(2)).setCancelled(true);
        var wild=mock(EvokerFangs.class);when(wild.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));var nativeHit=mock(EntityDamageByEntityEvent.class);when(nativeHit.getDamager()).thenReturn(wild);service.preventFangDamage(nativeHit);verify(nativeHit,never()).setCancelled(anyBoolean());
        var damage=mock(EntityDamageEvent.class);var source=mock(org.bukkit.damage.DamageSource.class);when(damage.getDamageSource()).thenReturn(source);when(source.getDirectEntity()).thenReturn(spawned.get(0));service.preventFangDamage(damage);verify(damage).setCancelled(true);
    }
    @Test void previewStopClearsOnlyPreviewWhileCombatUsesTheSameNativeJaws(){
        preview();service.stopPreview(owner);assertEquals(0,service.fangCount());assertTrue(spawned.stream().noneMatch(Entity::isValid));
        assertNotNull(service.necro(owner,"necro_lance",owner.getEyeLocation(),new Vector(0,0,1),new NecroVfx.Lance(14),Mode.COMBAT,null,0));assertEquals(7,service.fangCount());verify(viewer,atLeastOnce()).showEntity(eq(plugin),any());service.stopPreview(owner);assertEquals(7,service.fangCount());service.cancelCombat(owner);assertEquals(0,service.fangCount());
    }
    @Test void runtimeCancellationWorldChangeAndShutdownRemoveTemporaryEntities(){
        preview();service.runtime().cancel(owner.getUniqueId(),Mode.PREVIEW);clock.run();assertEquals(0,service.fangCount());
        preview();var other=mock(World.class);when(other.getUID()).thenReturn(UUID.randomUUID());when(owner.getWorld()).thenReturn(other);clock.run();assertEquals(0,service.fangCount());when(owner.getWorld()).thenReturn(world);
        preview();service.shutdown();assertEquals(0,service.fangCount());verify(task).cancel();assertTrue(spawned.stream().noneMatch(Entity::isValid));
    }
    @Test void lowQualityReducesOnlyJawsAndOwnerLimitRejectsExcessPreviews(){
        service.quality(owner,Quality.LOW);assertTrue(preview());assertEquals(4,service.fangCount());service.stopPreview(owner);service.quality(owner,Quality.HIGH);assertTrue(preview());assertTrue(preview());assertFalse(preview());assertEquals(14,service.fangCount());assertEquals(2,service.runtime().stats().active());
    }
    @Test void wallsUnloadedChunksAndRejectedSpawnsCannotCreateStrayJaws(){
        when(world.rayTraceBlocks(any(Location.class),any(Vector.class),anyDouble(),any(),anyBoolean())).thenAnswer(i->(double)i.getArgument(2)>2?new RayTraceResult(new Vector(0,65.6,2),mock(Block.class),org.bukkit.block.BlockFace.NORTH):null);
        assertTrue(preview());assertEquals(1,service.fangCount());service.stopPreview(owner);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);assertFalse(preview());verify(world,never()).getChunkAt(anyInt(),anyInt());
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);reject=true;assertFalse(preview());assertEquals(0,service.fangCount());assertTrue(spawned.stream().noneMatch(Entity::isValid));
    }
    @Test void missingGroundCannotSpawnFloatingJawsAndTimeoutAlwaysCleansUp(){
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->{var b=mock(Block.class);when(b.isPassable()).thenReturn(true);return b;});assertFalse(preview());assertEquals(0,spawned.size());
    }
    @Test void deadlineAndQuitRemoveEveryJawWithoutExtraSchedulerTasks(){
        preview();for(int i=0;i<48;i++)clock.run();assertEquals(0,service.fangCount());preview();var quit=mock(PlayerQuitEvent.class);when(quit.getPlayer()).thenReturn(owner);service.quit(quit);assertEquals(0,service.fangCount());verify(server.getScheduler(),times(1)).runTaskTimer(eq(plugin),any(Runnable.class),eq(1L),eq(1L));
    }
    @Test void globalCapAppliesAcrossOwnersAndCleanupLeavesNoEntities(){
        var fangs=new EvokerFangVisuals(plugin);for(int i=0;i<30;i++)fangs.play(UUID.randomUUID(),player(),owner.getEyeLocation(),new Vector(0,0,1),14,Mode.PREVIEW,Quality.HIGH,0);assertEquals(128,fangs.count());fangs.clear(null);assertEquals(0,fangs.count());assertTrue(spawned.stream().noneMatch(Entity::isValid));
    }
}

package com.customrpg.vfx;

import com.customrpg.CustomRPG;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitVfxServiceTest {
    CustomRPG plugin;Server server;World world;Player player;BukkitVfxService service;
    @BeforeEach void setup(){
        plugin=mock(CustomRPG.class);server=mock(Server.class);world=mock(World.class);player=mock(Player.class);
        UUID owner=UUID.randomUUID(),worldId=UUID.randomUUID();
        when(plugin.getServer()).thenReturn(server);when(server.getPlayer(owner)).thenReturn(player);when(server.getWorld(worldId)).thenReturn(world);
        when(world.getUID()).thenReturn(worldId);when(player.getUniqueId()).thenReturn(owner);when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(true);when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);when(player.canSee(player)).thenReturn(true);
        when(player.getLocation()).thenAnswer(call->new Location(world,0,64,0));when(player.getEyeLocation()).thenAnswer(call->new Location(world,0,65.6,0));
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        var scheduler=mock(BukkitScheduler.class);when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),eq(1L),eq(1L))).thenReturn(mock(BukkitTask.class));
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        service=new BukkitVfxService(plugin);service.start();service.quality(player,VfxRuntime.Quality.HIGH);
    }
    @Test void previewsUseTypedDustAndCountZeroDirectionalParticlesOnlyForTheOwner(){
        assertTrue(service.preview(player,VfxEffects.SLASH,0));for(int i=0;i<4;i++)service.runtime().tick();
        verify(player,atLeastOnce()).spawnParticle(eq(Particle.DUST),any(Location.class),eq(1),eq(0d),eq(0d),eq(0d),eq(0d),isA(Particle.DustOptions.class));
        verify(player,atLeastOnce()).spawnParticle(eq(Particle.DUST_COLOR_TRANSITION),any(Location.class),eq(1),eq(0d),eq(0d),eq(0d),eq(0d),isA(Particle.DustTransition.class));
        verify(player,atLeastOnce()).spawnParticle(eq(Particle.CRIT),any(Location.class),eq(0),anyDouble(),anyDouble(),anyDouble(),eq(.12));
        verify(world,never()).getNearbyPlayers(any(Location.class),anyDouble());
        verify(player,never()).damage(anyDouble());verify(player,never()).setHealth(anyDouble());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(value=Material.class,names={"PACKED_ICE","STONE"})
    void nativeParticlesUseTheCorrectBlockDataAndZeroRandomOffsets(Material block){
        var data=mock(org.bukkit.block.data.BlockData.class);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.createBlockData(block)).thenReturn(data);
            var effect=new VfxRuntime.Effect(){public int duration(){return 1;}public void frame(int age,VfxRuntime.Canvas c){
                for(var kind:List.of(VfxRuntime.ParticleKind.SNOWFLAKE,VfxRuntime.ParticleKind.END_ROD,block==Material.STONE?VfxRuntime.ParticleKind.STONE_BLOCK:VfxRuntime.ParticleKind.ICE_BLOCK,VfxRuntime.ParticleKind.CLOUD))
                    c.point(VfxRuntime.Layer.OUTLINE,new VfxRuntime.Paint(kind,0,0,1,com.customrpg.vfx.VfxMath.V.ZERO,0),com.customrpg.vfx.VfxMath.V.ZERO);
            }};
            assertNotNull(service.frost(player,"frost_shard",player.getLocation(),new org.bukkit.util.Vector(0,0,1),effect,VfxRuntime.Mode.PREVIEW,null,0));service.runtime().tick();
            verify(player).spawnParticle(eq(Particle.BLOCK),any(Location.class),eq(1),eq(0d),eq(0d),eq(0d),eq(0d),same(data));
            for(var particle:List.of(Particle.SNOWFLAKE,Particle.END_ROD,Particle.CLOUD))verify(player).spawnParticle(eq(particle),any(Location.class),eq(1),eq(0d),eq(0d),eq(0d),eq(0d));
        }
    }
    @Test void successfulTeleportOrLogoutClearsActiveAndQueuedPreviewsAndQuality(){
        assertTrue(service.preview(player,VfxEffects.VOID,80));assertEquals(1,service.runtime().stats().active());
        var teleport=mock(PlayerTeleportEvent.class);when(teleport.getPlayer()).thenReturn(player);service.teleport(teleport);assertEquals(0,service.runtime().stats().active());
        assertTrue(service.preview(player,VfxEffects.SLASH,0));var quit=mock(PlayerQuitEvent.class);when(quit.getPlayer()).thenReturn(player);service.quit(quit);
        assertEquals(0,service.runtime().stats().active());assertEquals(VfxRuntime.Quality.MEDIUM,service.quality(player));
    }
    @Test void worldChangeAndUnloadedChunksCannotSendOldAnimationOrLoadChunks(){
        assertTrue(service.preview(player,VfxEffects.SLASH,0));
        var other=mock(World.class);when(other.getUID()).thenReturn(UUID.randomUUID());when(player.getWorld()).thenReturn(other);
        service.runtime().tick();assertEquals(0,service.runtime().stats().active());verify(player,never()).spawnParticle(any(Particle.class),any(Location.class),anyInt());
        when(player.getWorld()).thenReturn(world);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertFalse(service.preview(player,VfxEffects.SLASH,0));verify(world,never()).getChunkAt(anyInt(),anyInt());
    }
    @Test void nativeChickenPreviewIsRejectedAtomicallyIfBodyControllerCannotStart(){
        var talents=mock(com.customrpg.managers.TalentSkillManager.class);var chickens=mock(com.customrpg.managers.ChickenTalentSkills.class);when(plugin.getTalentSkillManager()).thenReturn(talents);when(talents.getChicken()).thenReturn(chickens);
        assertFalse(service.preview(player,"chicken_god",0));assertEquals(0,service.runtime().stats().active());verify(chickens).preview(eq(player),eq(com.customrpg.talents.ChickenSkill.GOD),any(UUID.class),any(Location.class),any(org.bukkit.util.Vector.class));verify(chickens,never()).cast(any(),any(),anyInt());
    }
    @Test void stoppingNativeChickenPreviewAlsoCallsTheBodyCleanup(){
        var talents=mock(com.customrpg.managers.TalentSkillManager.class);var chickens=mock(com.customrpg.managers.ChickenTalentSkills.class);when(plugin.getTalentSkillManager()).thenReturn(talents);when(talents.getChicken()).thenReturn(chickens);when(chickens.preview(any(),any(),any(),any(),any())).thenReturn(true);
        assertTrue(service.preview(player,"chicken_rooster",0));assertEquals(1,service.runtime().stats().active());service.stopPreview(player);assertEquals(0,service.runtime().stats().active());verify(chickens).cancelPreviews(player);verify(chickens,never()).cast(any(),any(),anyInt());
    }

    @Test void growthAndHeartRenderAsSupportedJavaParticlesWithExactPositions(){var effect=new VfxRuntime.Effect(){public int duration(){return 1;}public void frame(int age,VfxRuntime.Canvas c){for(var kind:List.of(VfxRuntime.ParticleKind.GROWTH,VfxRuntime.ParticleKind.HEART))c.point(VfxRuntime.Layer.OUTLINE,new VfxRuntime.Paint(kind,0,0,1,com.customrpg.vfx.VfxMath.V.ZERO,0),com.customrpg.vfx.VfxMath.V.ZERO);}};assertNotNull(service.chicken(player,"chicken_feed",player.getLocation(),new org.bukkit.util.Vector(0,0,1),effect,VfxRuntime.Mode.PREVIEW,null,0));service.runtime().tick();for(var particle:List.of(Particle.HAPPY_VILLAGER,Particle.HEART))verify(player).spawnParticle(eq(particle),any(Location.class),eq(1),eq(0d),eq(0d),eq(0d),eq(0d));}

}

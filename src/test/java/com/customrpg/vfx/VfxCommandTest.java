package com.customrpg.vfx;

import org.bukkit.Server;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.*;
import com.customrpg.CustomRPG;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class VfxCommandTest {
    @Test void unauthorizedUsersCannotStartOrInspectPreviews(){
        var service=mock(BukkitVfxService.class);var player=mock(Player.class);var command=new VfxCommand(service);
        command.onCommand(player,mock(Command.class),"rpgvfx",new String[]{"preview","w01"});verifyNoInteractions(service);
        assertTrue(command.onTabComplete(player,mock(Command.class),"rpgvfx",new String[]{""}).isEmpty());
    }
    @Test void previewAliasesResolveToTheExistingSkillWithoutCastingIt(){
        var service=mock(BukkitVfxService.class);var player=mock(Player.class);when(player.hasPermission("customrpg.vfx.admin")).thenReturn(true);
        when(service.preview(player,VfxEffects.SLASH,0)).thenReturn(true);
        new VfxCommand(service).onCommand(player,mock(Command.class),"rpgvfx",new String[]{"preview","w01"});
        verify(service).preview(player,VfxEffects.SLASH,0);verifyNoMoreInteractions(service);
    }
    @Test void showcaseUsesTheRuntimeQueueAndStopOnlyStopsTheIssuersPreviews(){
        var service=mock(BukkitVfxService.class);var player=mock(Player.class);when(player.hasPermission("customrpg.vfx.admin")).thenReturn(true);
        var command=new VfxCommand(service);command.onCommand(player,mock(Command.class),"rpgvfx",new String[]{"showcase"});
        verify(service).preview(player,VfxEffects.SLASH,0);verify(service).preview(player,VfxEffects.THUNDER,30);verify(service).preview(player,VfxEffects.VOID,80);
        command.onCommand(player,mock(Command.class),"rpgvfx",new String[]{"stop"});verify(service,times(2)).stopPreview(player);
        verify(service,never()).clearCombat();
    }
    @Test void oneBukkitTaskIsSharedAndShutdownCancelsIt(){
        var plugin=mock(CustomRPG.class);var server=mock(Server.class);var scheduler=mock(BukkitScheduler.class);var task=mock(BukkitTask.class);
        when(plugin.getServer()).thenReturn(server);when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),eq(1L),eq(1L))).thenReturn(task);
        var service=new BukkitVfxService(plugin);service.start();service.start();
        verify(scheduler,times(1)).runTaskTimer(eq(plugin),any(Runnable.class),eq(1L),eq(1L));service.shutdown();verify(task).cancel();assertEquals(0,service.runtime().stats().active());
    }
}

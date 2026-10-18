package com.customrpg.worldevent;

import com.customrpg.worldevent.core.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;

class WorldEventCommandTest {
    @Test void ordinaryPlayersCannotStartStopOrDebugEvents() {
        var manager=mock(WorldEventManager.class); var handler=new WorldEventCommand(manager); Command command=mock(Command.class);
        when(command.getName()).thenReturn("mmorpg"); Player player=mock(Player.class);
        handler.onCommand(player,command,"mmorpg",new String[]{"event","start","meteor","here"});
        verifyNoInteractions(manager); verify(player).sendMessage(contains("權限"));
    }
    @Test void invalidEventIdReturnsMessageInsteadOfCrashing() {
        var manager=mock(WorldEventManager.class); var handler=new WorldEventCommand(manager); Command command=mock(Command.class);
        when(command.getName()).thenReturn("events"); Player player=mock(Player.class);
        handler.onCommand(player,command,"events",new String[]{"claim","invalid"});
        verify(player).sendMessage(contains("無效")); verifyNoInteractions(manager);
    }
    @Test void adminReceivesLaterSearchFailureThroughTheCommand() {
        var manager=mock(WorldEventManager.class); var handler=new WorldEventCommand(manager); Command command=mock(Command.class);
        when(command.getName()).thenReturn("mmorpg"); Player player=mock(Player.class);
        when(player.hasPermission("customrpg.event.admin")).thenReturn(true);
        List<Consumer<String>> callbacks=new ArrayList<>();
        when(manager.requestStart(eq("meteor"),isNull(),any())).thenAnswer(invocation->{
            callbacks.add(invocation.getArgument(2)); return "選址已開始";
        });
        handler.onCommand(player,command,"mmorpg",new String[]{"event","start","meteor"});
        verify(player).sendMessage("選址已開始");
        callbacks.getFirst().accept("§c世界事件選址已取消：未找到安全地形");
        verify(player).sendMessage("§c世界事件選址已取消：未找到安全地形");
    }
    @Test void adminTestUsesPlayersCurrentLocationAndFailureCallback() {
        var manager=mock(WorldEventManager.class); var handler=new WorldEventCommand(manager); Command command=mock(Command.class);
        when(command.getName()).thenReturn("mmorpg"); Player player=mock(Player.class);
        when(player.hasPermission("customrpg.event.admin")).thenReturn(true);
        var location=new org.bukkit.Location(mock(org.bukkit.World.class),100,65,100); when(player.getLocation()).thenReturn(location);
        when(manager.requestTestStart(eq("meteor"),eq(location),any())).thenReturn("測試已開始");
        handler.onCommand(player,command,"mmorpg",new String[]{"event","test","meteor","here"});
        verify(manager).requestTestStart(eq("meteor"),eq(location),any()); verify(player).sendMessage("測試已開始");
        verify(manager,never()).requestStart(anyString(),any(),any());
    }
    @Test void testRequiresAdminPermissionAndAPlayerWithAnExplicitHereArgument() {
        var manager=mock(WorldEventManager.class); var handler=new WorldEventCommand(manager); Command command=mock(Command.class);
        when(command.getName()).thenReturn("mmorpg"); Player player=mock(Player.class);
        handler.onCommand(player,command,"mmorpg",new String[]{"event","test","meteor","here"});
        verify(player).sendMessage(contains("權限"));
        var console=mock(ConsoleCommandSender.class); when(console.hasPermission("customrpg.event.admin")).thenReturn(true);
        handler.onCommand(console,command,"mmorpg",new String[]{"event","test","meteor","here"});
        verify(console).sendMessage(contains("必須由玩家"));
        when(player.hasPermission("customrpg.event.admin")).thenReturn(true);
        handler.onCommand(player,command,"mmorpg",new String[]{"event","test","meteor"});
        verify(player).sendMessage("/mmorpg event test meteor here"); verifyNoInteractions(manager);
    }
}

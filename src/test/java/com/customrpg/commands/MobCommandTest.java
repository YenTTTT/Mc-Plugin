package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.managers.MobManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobCommandTest {
    MobManager mobs; Player player; MobCommand command;
    @BeforeEach void setup() {
        mobs = mock(MobManager.class); player = mock(Player.class); command = new MobCommand(mock(CustomRPG.class), mobs);
        when(player.hasPermission("customrpg.mob")).thenReturn(true);
        var data = mock(MobManager.MobData.class); when(data.getMinLevel()).thenReturn(8); when(data.getMaxLevel()).thenReturn(60); when(mobs.getMobData("boss")).thenReturn(data);
        World world = mock(World.class); when(world.getDifficulty()).thenReturn(Difficulty.NORMAL); when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenAnswer(i -> new Location(world, 200, 64, 0));
    }
    @Test void explicitLevelUsesTheLevelledSpawnAPIForRepeatableTesting() {
        var location = player.getLocation(); when(mobs.findSafeSpawnLocation(any())).thenReturn(location);
        LivingEntity boss = mock(LivingEntity.class); when(mobs.spawnCustomMobWithLevel("boss", location, 20)).thenReturn(boss);
        assertTrue(command.onCommand(player, null, "custommob", new String[]{"spawn", "boss", "20"}));
        verify(mobs).spawnCustomMobWithLevel("boss", location, 20); verify(mobs, never()).spawnCustomMob(any(), any());
    }
    @ParameterizedTest @ValueSource(strings = {"abc", "0", "61"})
    void invalidOrOutOfTemplateRangeLevelCannotSpawn(String level) {
        command.onCommand(player, null, "custommob", new String[]{"spawn", "boss", level});
        verify(mobs, never()).findSafeSpawnLocation(any()); verify(mobs, never()).spawnCustomMobWithLevel(any(), any(), anyInt());
    }
}

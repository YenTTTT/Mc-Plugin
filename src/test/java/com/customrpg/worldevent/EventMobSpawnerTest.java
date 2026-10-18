package com.customrpg.worldevent;

import com.customrpg.CustomRPG;
import com.customrpg.managers.MobManager;
import com.customrpg.worldevent.core.*;
import com.customrpg.worldevent.meteor.MeteorImpactEvent;
import com.customrpg.worldevent.mob.EventMobSpawner;
import com.customrpg.worldevent.persistence.WorldEventRepository;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventMobSpawnerTest {
    @TempDir Path directory;
    @Test void defaultPlanHasExactlyNineteenUniqueZoneLevelMobs() throws Exception {
        CustomRPG plugin=mock(CustomRPG.class); MobManager mobs=mock(MobManager.class); when(plugin.getMobManager()).thenReturn(mobs);
        when(mobs.getMobData(anyString())).thenReturn(mock(MobManager.MobData.class));
        YamlConfiguration config=new YamlConfiguration(); config.load(Path.of("src/main/resources/config/world_events.yml").toFile());
        var definition=new MeteorImpactEvent(config.getConfigurationSection("world-events.meteor"));
        var repo=new WorldEventRepository(directory); var e=WorldEventLifecycleTest.event(); repo.events.put(e.id,e);
        var spawner=new EventMobSpawner(plugin,repo); spawner.plan(e,definition);
        assertEquals(19,e.mobs.size()); assertEquals(19,e.mobs.keySet().stream().distinct().count());
        assertTrue(e.mobs.values().stream().allMatch(m->m.level==15&&m.state==WorldEventInstance.MobState.PLANNED));
        assertEquals(1,e.mobs.values().stream().filter(m->m.rank.equals("BOSS")).count());
        assertThrows(IllegalStateException.class,()->spawner.plan(e,definition));
    }
    @Test void identityIsSavedBeforeSpawnAndMobsAreNeverSpawnedTwice() throws Exception {
        CustomRPG plugin=mock(CustomRPG.class); MobManager mobs=mock(MobManager.class); when(plugin.getMobManager()).thenReturn(mobs);
        var repo=spy(new WorldEventRepository(directory)); var e=WorldEventLifecycleTest.event(); e.state=WorldEventState.IMPACTING;
        var record=new WorldEventInstance.EventMob(UUID.randomUUID(),"meteor_parasite","NORMAL",15); e.mobs.put(record.id,record); repo.events.put(e.id,e);
        World world=mock(World.class); Location spawn=new Location(world,1000,65,-1000);
        var spawner=spy(new EventMobSpawner(plugin,repo)); doReturn(spawn).when(spawner).findSpawn(e,world,0);
        LivingEntity entity=mock(LivingEntity.class); when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class)); when(entity.isValid()).thenReturn(true);
        doAnswer(invocation->{
            assertEquals(WorldEventInstance.MobState.SPAWNING,record.state);
            var persisted=new WorldEventRepository(directory); persisted.load();
            assertEquals(WorldEventInstance.MobState.SPAWNING,persisted.events.get(e.id).mobs.get(record.id).state);
            Consumer<LivingEntity> initializer=invocation.getArgument(3); initializer.accept(entity);
            return entity;
        }).when(mobs).spawnEventMob(eq(record.template),eq(spawn),eq(15),any());
        assertTrue(spawner.spawnNext(e,world)); assertFalse(spawner.spawnNext(e,world));
        assertEquals(WorldEventInstance.MobState.ALIVE,record.state); assertEquals(entity.getUniqueId(),record.entity);
        record.state=WorldEventInstance.MobState.UNLOADED; assertFalse(spawner.spawnNext(e,world));
        record.state=WorldEventInstance.MobState.DEAD; assertFalse(spawner.spawnNext(e,world));
        verify(mobs,times(1)).spawnEventMob(anyString(),any(),anyInt(),any());
    }
    @Test void reservationSaveFailurePreventsEntitySpawn() throws Exception {
        CustomRPG plugin=mock(CustomRPG.class); MobManager mobs=mock(MobManager.class); when(plugin.getMobManager()).thenReturn(mobs);
        var repo=spy(new WorldEventRepository(directory)); doThrow(new IOException("disk full")).when(repo).save();
        var e=WorldEventLifecycleTest.event(); var record=new WorldEventInstance.EventMob(UUID.randomUUID(),"meteor_parasite","NORMAL",15); e.mobs.put(record.id,record);
        World world=mock(World.class); var spawner=spy(new EventMobSpawner(plugin,repo));
        doReturn(new Location(world,1000,65,-1000)).when(spawner).findSpawn(e,world,0);
        assertThrows(IOException.class,()->spawner.spawnNext(e,world));
        verifyNoInteractions(mobs); assertFalse(spawner.spawnNext(e,world));
    }
}

package com.customrpg.worldevent.location;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.FailedLoadRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.index.HashMapIndex;
import com.sk89q.worldguard.protection.managers.storage.RegionDatabase;
import com.sk89q.worldguard.protection.regions.*;
import org.bukkit.*;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldGuardProtectionAdapterTest {
    @TempDir Path directory;
    World world;
    RegionManager regions;
    WorldGuardProtectionAdapter adapter;
    Location location;

    @BeforeEach void setup() {
        world=mock(World.class); when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        regions=new RegionManager(mock(RegionDatabase.class),name->new HashMapIndex(),new SimpleFlagRegistry());
        adapter=new WorldGuardProtectionAdapter(ignored->regions); location=new Location(world,10,65,10);
    }
    @Test void wildernessIsAllowedEvenWhenOtherPartsOfWorldHaveRegions() {
        regions.addRegion(new ProtectedCuboidRegion("town",BlockVector3.at(100,0,100),BlockVector3.at(200,319,200)));
        assertTrue(adapter.test(location));
    }
    @Test void undergroundAndHighAltitudeClaimsAreDeniedEvenWithExplicitBuildAllow() {
        for(int y:List.of(-50,180)) {
            var claim=new ProtectedCuboidRegion("claim",BlockVector3.at(0,y,0),BlockVector3.at(20,y+10,20));
            claim.setFlag(Flags.BUILD,StateFlag.State.ALLOW); regions.addRegion(claim);
            assertFalse(adapter.test(location));
        }
    }
    @Test void globalBuildBlockAndMobDenialsAreRespectedOutsideClaims() {
        for(var flag:List.of(Flags.BUILD,Flags.BLOCK_BREAK,Flags.BLOCK_PLACE,Flags.MOB_SPAWNING)) {
            var global=new GlobalProtectedRegion("__global__"); global.setFlag(flag,StateFlag.State.DENY);
            regions.addRegion(global); assertFalse(adapter.test(location),flag.getName());
        }
    }
    @Test void unrelatedGlobalFlagsDoNotTreatWholeWorldAsClaimed() {
        var global=new GlobalProtectedRegion("__global__"); global.setFlag(Flags.PVP,StateFlag.State.DENY);
        regions.addRegion(global); assertTrue(adapter.test(location));
    }
    @Test void unavailableOrVirtualRegionDataIsNeverTreatedAsWilderness() {
        assertThrows(IllegalStateException.class,()->new WorldGuardProtectionAdapter(ignored->null).test(location));
        var failed=mock(RegionManager.class);
        when(failed.getApplicableRegions(any(ProtectedRegion.class))).thenReturn(FailedLoadRegionSet.getInstance());
        assertThrows(IllegalStateException.class,()->new WorldGuardProtectionAdapter(ignored->failed).test(location));
    }
    private CustomRPG plugin() {
        var plugin=mock(CustomRPG.class); Server server=mock(Server.class); when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.getSafeZoneManager()).thenReturn(mock(SafeZoneManager.class));
        when(plugin.getProtectionAreaManager()).thenReturn(mock(ProtectionAreaManager.class));
        when(plugin.getBossZoneManager()).thenReturn(mock(BossZoneManager.class)); return plugin;
    }
    @Test void enabledWorldGuardAutomaticallyRegistersAndLookupFailureDenies() throws Exception {
        var plugin=plugin(); when(plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")).thenReturn(true);
        var wg=mock(WorldGuard.class,RETURNS_DEEP_STUBS);
        var weWorld=mock(com.sk89q.worldedit.world.World.class);
        try(var singleton=mockStatic(WorldGuard.class); var bukkit=mockStatic(BukkitAdapter.class)) {
            singleton.when(WorldGuard::getInstance).thenReturn(wg);
            bukkit.when(()->BukkitAdapter.adapt(world)).thenReturn(weWorld);
            when(wg.getPlatform().getRegionContainer().get(weWorld)).thenReturn(regions);
            var validator=new ProtectionValidator(plugin);
            validator.enableIntegrations(new NaturalTerrainRegistry(directory.resolve("chunks.yml")),ignored->true);
            assertNull(validator.rejection(location));
            when(wg.getPlatform().getRegionContainer().get(weWorld)).thenReturn(null);
            assertEquals("WorldGuard protection lookup failed",validator.rejection(location)); validator.close();
        }
    }
    @Test void missingOptionalPluginClassesDoNotPreventValidatorStartup() throws Exception {
        var plugin=plugin(); String target=ProtectionValidator.class.getName();
        try(var loader=new URLClassLoader(new java.net.URL[]{Path.of("target/classes").toUri().toURL()},getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.startsWith("com.sk89q.")) throw new ClassNotFoundException("Optional plugin absent: "+name);
                if(name.equals(target)) {
                    Class<?> type=findLoadedClass(name); if(type==null) type=findClass(name);
                    if(resolve) resolveClass(type); return type;
                }
                return super.loadClass(name,resolve);
            }
        }) {
            Class<?> type=loader.loadClass(target); Object validator=type.getConstructor(CustomRPG.class).newInstance(plugin);
            type.getMethod("enableIntegrations",NaturalTerrainRegistry.class,Predicate.class)
                    .invoke(validator,new NaturalTerrainRegistry(directory.resolve("absent.yml")),(Predicate<Location>)ignored->true);
            assertNull(type.getMethod("rejection",Location.class).invoke(validator,location));
            type.getMethod("close").invoke(validator);
        }
    }
}

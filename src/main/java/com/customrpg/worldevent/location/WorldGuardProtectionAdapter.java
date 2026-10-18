package com.customrpg.worldevent.location;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.function.Function;
import java.util.function.Predicate;

/** Loaded only when WorldGuard is enabled. Event terrain must remain outside all defined regions. */
public final class WorldGuardProtectionAdapter implements Predicate<Location> {
    private final Function<World,RegionManager> managers;

    public WorldGuardProtectionAdapter() {
        this(world->WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world)));
    }

    WorldGuardProtectionAdapter(Function<World,RegionManager> managers) { this.managers=managers; }

    @Override public boolean test(Location location) {
        World world=location.getWorld();
        if(world==null) return false;
        RegionManager manager=managers.apply(world);
        // Null also means failed region loading, not necessarily wilderness.
        if(manager==null) throw new IllegalStateException("WorldGuard region data unavailable");
        int x=location.getBlockX(), z=location.getBlockZ();
        var column=new ProtectedCuboidRegion("customrpg_event_probe",
                BlockVector3.at(x,world.getMinHeight(),z),BlockVector3.at(x,world.getMaxHeight()-1,z));
        var regions=manager.getApplicableRegions(column);
        if(regions==null||regions.isVirtual()) throw new IllegalStateException("WorldGuard region query unavailable");
        // Check the entire column, including claims below the surface; OP/member bypass never applies.
        return regions.getRegions().isEmpty()
                &&regions.testState(ignored->Association.NON_MEMBER,Flags.BUILD,Flags.BLOCK_BREAK,Flags.BLOCK_PLACE,Flags.MOB_SPAWNING);
    }
}

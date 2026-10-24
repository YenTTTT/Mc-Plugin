package com.customrpg.worldevent.location;

import com.customrpg.worldevent.core.*;
import com.customrpg.worldevent.core.WorldEventDefinition.*;
import com.customrpg.worldevent.terrain.TerrainSnapshot;
import org.bukkit.*;
import org.bukkit.block.Block;
import java.util.*;

/** Bounded, loaded-chunk-only validation of the entire footprint plus a physics/protection margin. */
public final class EventLocationService {
    public record Site(Location center, Map<Column,Integer> heights,boolean testMode) {
        public Site(Location center,Map<Column,Integer> heights) { this(center,heights,false); }
    }
    private final ProtectionValidator protection;
    private final int padding;
    public String lastRejection = "No candidate tested";

    public EventLocationService(ProtectionValidator protection,int padding) {
        this.protection=protection; this.padding=padding;
    }
    public Site validate(Location requested, Requirements requirements, Collection<WorldEventInstance> events) {
        return validate(requested,requirements,events,false);
    }
    public Site validateForTesting(Location requested,Requirements requirements,Collection<WorldEventInstance> events) {
        return validate(requested,requirements,events,true);
    }
    public Site validateArena(Location requested,Requirements requirements,Collection<WorldEventInstance> events,boolean testMode,int clearance) {
        World world=requested.getWorld();
        if(world==null||world.getEnvironment()!=World.Environment.NORMAL||world.getDifficulty()==Difficulty.PEACEFUL)
            return reject("Overworld in non-peaceful difficulty required");
        int x=requested.getBlockX(),z=requested.getBlockZ(),guard=requirements.radius()+padding;
        for(WorldEventInstance event:events) if(event.reserved()&&event.contains(world.getUID(),x,z,guard+padding))
            return reject("Overlapping event/recovery area");
        for(int cx=(x-guard)>>4;cx<=(x+guard)>>4;cx++) for(int cz=(z-guard)>>4;cz<=(z+guard)>>4;cz++)
            if(!world.isChunkLoaded(cx,cz)) return reject("Candidate touches unloaded chunk");
        int highest=world.getMinHeight();
        for(int dx=-guard;dx<=guard;dx++) for(int dz=-guard;dz<=guard;dz++) {
            int bx=x+dx,bz=z+dz;
            int top=world.getHighestBlockYAt(bx,bz,HeightMap.WORLD_SURFACE);
            Location surface=new Location(world,bx+0.5,top+1,bz+0.5);
            if(!world.getWorldBorder().isInside(surface)) return reject("World border");
            String denial=testMode?protection.rejectionForTesting(surface):protection.rejection(surface);
            if(denial!=null) return reject(denial);
            if(Math.abs(dx)<=requirements.radius()&&Math.abs(dz)<=requirements.radius()) highest=Math.max(highest,top);
        }
        int y=highest+clearance+requirements.depth()+1;
        if(y+requirements.headroom()+3>=world.getMaxHeight()) return reject("Build height unsuitable");
        lastRejection="Accepted";
        return new Site(new Location(world,x+0.5,y,z+0.5),Map.of(),testMode);
    }
    private Site validate(Location requested,Requirements requirements,Collection<WorldEventInstance> events,boolean testMode) {
        World world=requested.getWorld();
        if(world==null||world.getEnvironment()!=World.Environment.NORMAL||world.getDifficulty()==Difficulty.PEACEFUL) return reject("Overworld in non-peaceful difficulty required");
        int x=requested.getBlockX(), z=requested.getBlockZ(), guard=requirements.radius()+padding;
        for(WorldEventInstance event:events) if(event.reserved() && event.contains(world.getUID(),x,z,guard+padding)) return reject("Overlapping event/recovery area");
        for(int cx=(x-guard)>>4;cx<=(x+guard)>>4;cx++) for(int cz=(z-guard)>>4;cz<=(z+guard)>>4;cz++) {
            if(!world.isChunkLoaded(cx,cz)) return reject("Candidate touches unloaded chunk");
        }
        int centerY=world.getHighestBlockYAt(x,z,HeightMap.OCEAN_FLOOR);
        for(org.bukkit.entity.Entity entity:world.getNearbyEntities(new Location(world,x+0.5,centerY,z+0.5),requirements.radius()+1,
                world.getMaxHeight()-world.getMinHeight(),requirements.radius()+1)) {
            if(entity instanceof org.bukkit.entity.Player) continue;
            if(entity instanceof org.bukkit.entity.Hanging || entity instanceof org.bukkit.entity.ArmorStand
                    || entity instanceof org.bukkit.entity.Display || entity instanceof org.bukkit.entity.Vehicle
                    || entity instanceof org.bukkit.entity.Villager || entity instanceof org.bukkit.entity.Tameable tameable&&tameable.isTamed()
                    ||entity.getCustomName()!=null) return reject("Player/NPC/decorative entity in footprint");
        }
        Map<Column,Integer> heights=new LinkedHashMap<>();
        for(int dx=-guard;dx<=guard;dx++) for(int dz=-guard;dz<=guard;dz++) {
            int bx=x+dx,bz=z+dz;
            int height=world.getHighestBlockYAt(bx,bz,HeightMap.OCEAN_FLOOR);
            Location surface=new Location(world,bx+0.5,height+1,bz+0.5);
            if(!world.getWorldBorder().isInside(surface)) return reject("World border");
            String denial=testMode?protection.rejectionForTesting(surface):protection.rejection(surface);
            if(denial!=null) return reject(denial);
            // The padding protects nearby claims and physics, but the crater never edits its blocks.
            if(Math.abs(dx)>requirements.radius()||Math.abs(dz)>requirements.radius()) continue;
            if(height-requirements.depth()-2<world.getMinHeight() || height+requirements.headroom()+3>=world.getMaxHeight()
                    ||centerY+requirements.headroom()+3>=world.getMaxHeight()
                    ||centerY-requirements.depth()-2<world.getMinHeight()) return reject("Build height unsuitable");
            heights.put(new Column(bx,bz),height);
        }
        lastRejection="Accepted";
        return new Site(new Location(world,x+0.5,centerY+1,z+0.5),Collections.unmodifiableMap(heights),testMode);
    }
    private Site reject(String reason) { lastRejection=reason; return null; }

    public TerrainSnapshot snapshot(UUID id,WorldEventDefinition definition,Site site,long seed) {
        Location center=site.center();
        Map<TerrainSnapshot.Position,String> plan=definition.generate(site.heights(),center.getBlockX(),center.getBlockY(),center.getBlockZ(),seed);
        List<TerrainSnapshot.BlockChange> blocks=new ArrayList<>();
        plan.forEach((position,after) -> {
            Block block=center.getWorld().getBlockAt(position.x(),position.y(),position.z());
            if(definition.generatedArena()) {
                if(!block.isEmpty()) throw new IllegalArgumentException("Arena volume is no longer empty");
            } else if(!TerrainValidator.canTemporarilyReplace(block)) return;
            String before=block.getBlockData().getAsString();
            if(!before.equals(after)) blocks.add(new TerrainSnapshot.BlockChange(position,before,after));
        });
        return new TerrainSnapshot(id,center.getWorld().getUID(),center.getBlockX(),center.getBlockY(),center.getBlockZ(),definition.requirements().radius(),blocks);
    }
}

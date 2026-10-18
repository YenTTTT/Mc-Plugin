package com.customrpg.worldevent.location;

import com.customrpg.worldevent.core.*;
import com.customrpg.worldevent.core.WorldEventDefinition.*;
import com.customrpg.worldevent.terrain.TerrainSnapshot;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import java.util.*;

/** Bounded, loaded-chunk-only validation of the entire footprint plus a physics/protection margin. */
public final class EventLocationService {
    public record Site(Location center, Map<Column,Integer> heights) {}
    private final NaturalTerrainRegistry provenance;
    private final ProtectionValidator protection;
    private final int padding;
    public String lastRejection = "No candidate tested";

    public EventLocationService(NaturalTerrainRegistry provenance, ProtectionValidator protection,int padding) {
        this.provenance=provenance; this.protection=protection; this.padding=padding;
    }
    public Site validate(Location requested, Requirements requirements, Collection<WorldEventInstance> events) {
        return validate(requested,requirements,events,false);
    }
    public Site validateForTesting(Location requested,Requirements requirements,Collection<WorldEventInstance> events) {
        return validate(requested,requirements,events,true);
    }
    private Site validate(Location requested,Requirements requirements,Collection<WorldEventInstance> events,boolean testMode) {
        World world=requested.getWorld();
        if(world==null||world.getEnvironment()!=World.Environment.NORMAL||world.getDifficulty()==Difficulty.PEACEFUL) return reject("Overworld in non-peaceful difficulty required");
        int x=requested.getBlockX(), z=requested.getBlockZ(), guard=requirements.radius()+padding;
        for(WorldEventInstance event:events) if(event.reserved() && event.contains(world.getUID(),x,z,guard+padding)) return reject("Overlapping event/recovery area");
        for(int cx=(x-guard)>>4;cx<=(x+guard)>>4;cx++) for(int cz=(z-guard)>>4;cz<=(z+guard)>>4;cz++) {
            if(!world.isChunkLoaded(cx,cz)) return reject("Candidate touches unloaded chunk");
            if(!testMode&&!provenance.eligible(world.getUID(),cx,cz)) return reject("Unknown or modified chunk; new observed natural terrain required");
            if(!testMode&&!world.getChunkAt(cx,cz).getStructures().isEmpty()) return reject("Vanilla structure/village intersects candidate chunks");
        }
        int centerY=world.getHighestBlockYAt(x,z,HeightMap.OCEAN_FLOOR);
        for(org.bukkit.entity.Entity entity:world.getNearbyEntities(new Location(world,x+0.5,centerY,z+0.5),guard,
                requirements.depth()+requirements.headroom()+8,guard)) {
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
            if(height-requirements.depth()-2<world.getMinHeight() || height+requirements.headroom()+3>=world.getMaxHeight()
                    ||Math.abs(height-centerY)>(testMode?Math.max(12,requirements.maxSlope()):requirements.maxSlope())) return reject("Height variation / build height unsuitable");
            Location surface=new Location(world,bx+0.5,height+1,bz+0.5);
            if(!world.getWorldBorder().isInside(surface)) return reject("World border");
            String denial=testMode?protection.rejectionForTesting(surface):protection.rejection(surface);
            if(denial!=null) return reject(denial);
            if(!testMode) {
                String biome=world.getBiome(bx,height,bz).getKey().getKey();
                if(biome.contains("ocean")||biome.contains("river")||biome.contains("swamp")||biome.contains("beach")) return reject("Water biome");
            }
            // Check a continuous vertical volume, not only GRASS_BLOCK. Reject trees, containers, portals, fluids, gravity and multi-block plants.
            for(int y=Math.min(centerY,height)-requirements.depth()-2;y<=Math.max(centerY,height)+requirements.headroom()+3;y++) {
                Block block=world.getBlockAt(bx,y,bz);
                // These exact vanilla types have no tile/fluid/gravity/waterlogged/multi-block state.
                // Avoid allocating thousands of BlockState snapshots simply to reject unsupported types.
                if(!(testMode?TerrainValidator.acceptsForTesting(block.getType()):TerrainValidator.accepts(block.getType()))) return reject("Unsafe block / block state: " + block.getType());
                if(testMode&&block.getBlockData() instanceof Waterlogged data&&data.isWaterlogged()) return reject("Unsafe block / block state: waterlogged " + block.getType());
            }
            Material ground=world.getBlockAt(bx,height,bz).getType();
            if(!(testMode?TerrainValidator.stableGroundForTesting(ground):TerrainValidator.stableGround(ground))) return reject("No stable ground");
            if(Math.abs(dx)<=requirements.radius()&&Math.abs(dz)<=requirements.radius()) heights.put(new Column(bx,bz),height);
        }
        lastRejection="Accepted";
        return new Site(new Location(world,x+0.5,centerY+1,z+0.5),Collections.unmodifiableMap(heights));
    }
    private Site reject(String reason) { lastRejection=reason; return null; }

    public TerrainSnapshot snapshot(UUID id,WorldEventDefinition definition,Site site,long seed) {
        Location center=site.center();
        Map<TerrainSnapshot.Position,String> plan=definition.generate(site.heights(),center.getBlockX(),center.getBlockY(),center.getBlockZ(),seed);
        List<TerrainSnapshot.BlockChange> blocks=new ArrayList<>();
        plan.forEach((position,after) -> blocks.add(new TerrainSnapshot.BlockChange(position,
                center.getWorld().getBlockAt(position.x(),position.y(),position.z()).getBlockData().getAsString(),after)));
        return new TerrainSnapshot(id,center.getWorld().getUID(),center.getBlockX(),center.getBlockY(),center.getBlockZ(),definition.requirements().radius(),blocks);
    }
}

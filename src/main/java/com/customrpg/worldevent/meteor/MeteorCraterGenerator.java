package com.customrpg.worldevent.meteor;

import com.customrpg.worldevent.core.WorldEventDefinition.Column;
import com.customrpg.worldevent.terrain.TerrainSnapshot.Position;
import java.util.*;

/** Pure seeded geometry, shared by snapshot creation and regression tests. No world access. */
public final class MeteorCraterGenerator {
    public Map<Position,String> generate(Map<Column,Integer> heights,int x,int y,int z,int radius,int depth,int coreRadius,long seed) {
        Map<Position,String> plan=new LinkedHashMap<>();
        Random random=new Random(seed);
        String[] floor={"minecraft:basalt[axis=y]","minecraft:blackstone","minecraft:tuff"};
        for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            double angle=Math.atan2(dz,dx);
            double boundary=radius-0.8+0.55*Math.sin(angle*5+seed%37)+0.25*Math.sin(angle*9+seed%19);
            double distance=Math.hypot(dx,dz);
            if(distance>boundary) continue;
            int surface=Objects.requireNonNull(heights.get(new Column(x+dx,z+dz)),"Missing height sample");
            int dig=Math.max(0,(int)Math.round(depth*Math.pow(1-distance/boundary,1.3)+random.nextDouble()*0.7-0.3));
            int bottom=surface-dig;
            for(int by=bottom+1;by<=Math.max(surface+2,y+coreRadius+2);by++) plan.put(new Position(x+dx,by,z+dz),"minecraft:air");
            plan.put(new Position(x+dx,bottom,z+dz),floor[random.nextInt(floor.length)]);
            if(distance>coreRadius+1 && random.nextDouble()<0.035) plan.put(new Position(x+dx,bottom+1,z+dz),"minecraft:blackstone");
        }
        // An irregular, recognizable obsidian body, bounded within the crater footprint.
        int coreY=heights.get(new Column(x,z))-depth+coreRadius;
        String[] core={"minecraft:obsidian","minecraft:crying_obsidian","minecraft:blackstone","minecraft:magma_block"};
        for(int dx=-coreRadius;dx<=coreRadius;dx++) for(int dy=-coreRadius;dy<=coreRadius;dy++) for(int dz=-coreRadius;dz<=coreRadius;dz++) {
            double normalized=(dx*dx+dz*dz+dy*dy*1.25)/(double)(coreRadius*coreRadius);
            if(normalized<=0.8+random.nextDouble()*0.4) plan.put(new Position(x+dx,coreY+dy,z+dz),core[random.nextInt(core.length)]);
        }
        return Collections.unmodifiableMap(plan);
    }
}

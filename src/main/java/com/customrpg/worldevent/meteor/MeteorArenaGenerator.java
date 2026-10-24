package com.customrpg.worldevent.meteor;

import com.customrpg.worldevent.terrain.TerrainSnapshot.Position;
import java.util.*;

/** A self-contained island: only adds solid blocks; never digs into the host world. */
public final class MeteorArenaGenerator {
    public Map<Position,String> generate(int x,int y,int z,int radius,int thickness,int coreRadius,long seed) {
        Map<Position,String> plan=new LinkedHashMap<>();
        Random random=new Random(seed);
        String[] floor={"minecraft:basalt[axis=y]","minecraft:blackstone","minecraft:tuff"};
        for(int layer=thickness;layer>=1;layer--) {
            double outline=radius-(layer-1)*0.55;
            for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
                double distance=Math.hypot(dx,dz);
                if(distance>outline) continue;
                plan.put(new Position(x+dx,y-layer,z+dz),floor[random.nextInt(floor.length)]);
                // Two-block rim keeps mobs and players on the platform, with a broad flat interior.
                if(layer==1&&distance>radius-1.4) {
                    plan.put(new Position(x+dx,y,z+dz),"minecraft:polished_blackstone_bricks");
                    plan.put(new Position(x+dx,y+1,z+dz),"minecraft:polished_blackstone_bricks");
                }
            }
        }
        String[] core={"minecraft:obsidian","minecraft:crying_obsidian","minecraft:blackstone"};
        for(int dy=0;dy<=coreRadius*2;dy++) for(int dx=-coreRadius;dx<=coreRadius;dx++) for(int dz=-coreRadius;dz<=coreRadius;dz++) {
            double shape=(dx*dx+dz*dz+Math.pow(dy-coreRadius,2)*0.8)/(double)(coreRadius*coreRadius);
            if(shape<=0.9+random.nextDouble()*0.2)
                plan.put(new Position(x+dx,y+dy,z+dz),core[random.nextInt(core.length)]);
        }
        return Collections.unmodifiableMap(plan);
    }
}

package com.customrpg.utils;
import org.bukkit.*;
import org.bukkit.util.*;
import java.util.function.Predicate;

/** Bounded, block-only checks: no chunk loads, entity collisions or edits to world terrain. */
public final class ChickenSafety {
    public static final double GOD_WIDTH=4.2,GOD_HEIGHT=7.1,DROP_HEIGHT=8;
    private ChickenSafety(){}
    public static boolean clearVolume(Location feet,double width,double height,Predicate<Location> protectedAt){
        if(width<=0||width>5||height<=0||height>17)return false;var w=feet.getWorld();double r=width/2;
        if(feet.getY()<w.getMinHeight()||feet.getY()+height>w.getMaxHeight())return false;
        for(double x:new double[]{-r,r})for(double z:new double[]{-r,r}){var edge=feet.clone().add(x,0,z);if(!w.getWorldBorder().isInside(edge)||protectedAt.test(edge))return false;}
        int minX=(int)Math.floor(feet.getX()-r),maxX=(int)Math.floor(feet.getX()+r),minZ=(int)Math.floor(feet.getZ()-r),maxZ=(int)Math.floor(feet.getZ()+r);
        for(int x=minX>>4;x<=maxX>>4;x++)for(int z=minZ>>4;z<=maxZ>>4;z++)if(!w.isChunkLoaded(x,z))return false;
        var body=new BoundingBox(feet.getX()-r,feet.getY()+.015,feet.getZ()-r,feet.getX()+r,feet.getY()+height,feet.getZ()+r);
        for(int x=minX;x<=maxX;x++)for(int y=feet.getBlockY();y<=Math.floor(feet.getY()+height);y++)for(int z=minZ;z<=maxZ;z++){
            var block=w.getBlockAt(x,y,z);if(block.isLiquid()||!block.isPassable()&&block.getBoundingBox().overlaps(body))return false;
        }
        return true;
    }
    public static boolean landing(Location feet,Predicate<Location> protectedAt){
        if(!clearVolume(feet,GOD_WIDTH,GOD_HEIGHT+DROP_HEIGHT,protectedAt))return false;var w=feet.getWorld();
        double r=GOD_WIDTH/2;for(int x=(int)Math.floor(feet.getX()-r);x<=Math.floor(feet.getX()+r);x++)for(int z=(int)Math.floor(feet.getZ()-r);z<=Math.floor(feet.getZ()+r);z++){var floor=w.getBlockAt(x,(int)Math.floor(feet.getY()-.08),z);if(floor.isPassable()||floor.isLiquid()||Math.abs(floor.getBoundingBox().getMaxY()-feet.getY())>.15)return false;}
        return true;
    }
    public static boolean swept(Location from,Location to,double width,double height,Predicate<Location> protectedAt){
        if(!from.getWorld().equals(to.getWorld()))return false;var delta=to.toVector().subtract(from.toVector());int steps=(int)Math.ceil(delta.length()/.4);if(steps>32)return false;
        for(int i=0;i<=steps;i++)if(!clearVolume(from.clone().add(delta.clone().multiply(steps==0?0:i/(double)steps)),width,height,protectedAt))return false;return true;
    }
}

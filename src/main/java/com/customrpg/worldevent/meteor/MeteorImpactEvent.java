package com.customrpg.worldevent.meteor;

import com.customrpg.worldevent.core.*;
import com.customrpg.worldevent.terrain.TerrainSnapshot.Position;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.Consumer;

public final class MeteorImpactEvent implements WorldEventDefinition {
    private final ConfigurationSection config;
    private final Requirements requirements;
    private final int coreRadius;
    private final List<MobSpec> mobs=new ArrayList<>();
    private final Map<String,Long> rewards=new LinkedHashMap<>();
    public MeteorImpactEvent(ConfigurationSection config) {
        this.config=Objects.requireNonNull(config,"meteor configuration");
        int radius=config.getInt("arena.radius",config.getInt("crater.radius",12)),depth=config.getInt("arena.thickness",3);
        coreRadius=config.getInt("core.radius",3);
        requirements=new Requirements(radius,depth,coreRadius*2+3,config.getInt("crater.max-height-difference",5));
        if(arenaClearance()<3||arenaClearance()>32) throw new IllegalArgumentException("Invalid arena clearance");
        if(radius<6||radius>24||depth<1||depth>8||coreRadius<1||coreRadius>radius/2
                ||requirements.maxSlope()<0||requirements.maxSlope()>8) throw new IllegalArgumentException("Invalid meteor geometry");
        for(Map<?,?> row:config.getMapList("mobs")) {
            int count=((Number)Objects.requireNonNull(row.get("count"))).intValue();
            String template=Objects.requireNonNull(row.get("template")).toString(),rank=Objects.requireNonNull(row.get("rank")).toString();
            if(count<1||count>32||!Set.of("NORMAL","TANK","ELITE","BOSS").contains(rank)) throw new IllegalArgumentException("Invalid meteor mob group");
            mobs.add(new MobSpec(template,count,rank));
        }
        if(mobs.isEmpty()||mobs.stream().mapToInt(MobSpec::count).sum()>64) throw new IllegalArgumentException("Meteor requires 1..64 mobs");
        ConfigurationSection rewardConfig=Objects.requireNonNull(config.getConfigurationSection("rewards"));
        for(String key:rewardConfig.getKeys(false)) {
            long count=rewardConfig.getLong(key);
            if(!key.matches("[a-z0-9_]+")||count<1||count>1_000_000) throw new IllegalArgumentException("Invalid meteor reward");
            rewards.put(key,count);
        }
        if(durationMillis()<60_000||durationMillis()>86_400_000||cleanupDelayMillis()<1000||cleanupDelayMillis()>3_600_000
                ||!Double.isFinite(minimumDamage())||minimumDamage()<1||!Double.isFinite(minimumShare())||minimumShare()<0||minimumShare()>1
                ||config.getInt("animation.falling-duration-ticks",100)<1||config.getInt("animation.falling-duration-ticks",100)>1200
                ||config.getInt("animation.starting-height",80)<10||config.getInt("animation.starting-height",80)>256) throw new IllegalArgumentException("Invalid meteor timing/contribution");
    }
    @Override public String type() { return "meteor"; }
    @Override public Requirements requirements() { return requirements; }
    @Override public boolean generatedArena() { return true; }
    @Override public int arenaClearance() { return config.getInt("arena.clearance",6); }
    @Override public long durationMillis() { return config.getLong("duration-minutes",20)*60_000; }
    @Override public long cleanupDelayMillis() { return config.getLong("cleanup-delay-seconds",120)*1000; }
    @Override public List<MobSpec> mobs() { return List.copyOf(mobs); }
    @Override public Map<String,Long> rewards() { return Map.copyOf(rewards); }
    @Override public double minimumDamage() { return config.getDouble("contribution.minimum-damage",1); }
    @Override public double minimumShare() { return config.getDouble("contribution.minimum-share",0.01); }
    @Override public Map<Position,String> generate(Map<Column,Integer> heights,int x,int y,int z,long seed) {
        return new MeteorArenaGenerator().generate(x,y,z,requirements.radius(),requirements.depth(),coreRadius,seed);
    }
    @Override public Animation animation(JavaPlugin plugin,WorldEventInstance event,World world,Consumer<Entity> tag) {
        return new MeteorAnimationService(plugin,event,world,config.getInt("animation.falling-duration-ticks",100),config.getInt("animation.starting-height",80),
                config.getBoolean("animation.enabled",true),config.getBoolean("animation.particles",true),config.getBoolean("animation.sounds",true),tag);
    }
}

package com.customrpg.vfx;

import com.customrpg.vfx.VfxMath.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Pure, single-thread runtime. It can request only particles/sounds; it has no combat or resource services. */
public final class VfxRuntime {
    public enum Mode { COMBAT,PREVIEW }
    public enum Layer { WARNING,IMPACT,OUTLINE,BODY,DECORATION;
        boolean critical(){return ordinal()<=OUTLINE.ordinal();}
        int group(){return this==WARNING?0:critical()?1:this==BODY?2:3;}
    }
    public enum Quality { LOW(96),MEDIUM(160),HIGH(240);
        final int budget;Quality(int budget){this.budget=budget;}
        int stride(Layer layer){
            if(this==LOW)return switch(layer){case WARNING,IMPACT->1;case OUTLINE->2;case BODY->4;case DECORATION->0;};
            if(this==MEDIUM)return layer==Layer.BODY?2:layer==Layer.DECORATION?4:1;
            return 1;
        }
    }
    public enum ParticleKind { DUST,TRANSITION,DIRECTIONAL_SPARK,SNOWFLAKE,END_ROD,ICE_BLOCK,STONE_BLOCK,CLOUD,GROWTH,HEART }
    public record Paint(ParticleKind kind,int color,int endColor,float size,V direction,double speed) {
        public Paint {
            Objects.requireNonNull(kind);Objects.requireNonNull(direction);
            if(size<.1||size>4||!Float.isFinite(size)||speed<0||speed>1||!Double.isFinite(speed))throw new IllegalArgumentException("Unsafe particle style");
        }
        public static Paint dust(int color,float size){return new Paint(ParticleKind.DUST,color,color,size,V.ZERO,0);}
        public static Paint fade(int from,int to,float size){return new Paint(ParticleKind.TRANSITION,from,to,size,V.ZERO,0);}
        public static Paint spark(V direction){return new Paint(ParticleKind.DIRECTIONAL_SPARK,0,0,1,direction.unit(),.12);}
    }
    public record Pose(UUID owner,UUID world,V origin,V direction,double yaw,UUID target) {
        public Pose {Objects.requireNonNull(owner);Objects.requireNonNull(world);Objects.requireNonNull(origin);Objects.requireNonNull(direction);if(!Double.isFinite(yaw))throw new IllegalArgumentException("Invalid yaw");}
        public Frame frame(){return Frame.facing(origin,direction,yaw);}
    }
    public record Viewer(UUID id,UUID world,V position,Quality quality) {}
    public record Limits(int instances,int perOwner,int samples,int viewerSends,int globalSends,int deliveryChecks,int sounds,double distance) {
        public Limits {if(instances<1||instances>128||perOwner<1||perOwner>16||samples<128||samples>16384||viewerSends<24||viewerSends>512||globalSends<64||globalSends>16000||deliveryChecks<128||deliveryChecks>64000||sounds<1||sounds>64||distance<8||distance>48)throw new IllegalArgumentException("Unsafe VFX limits");}
        public static Limits defaults(){return new Limits(64,6,8192,240,4000,16000,24,32);}
    }
    public interface Transport {
        boolean valid(Pose pose);
        List<Viewer> viewers(Pose pose,Mode mode,double distance);
        boolean particle(UUID viewer,UUID world,V at,Paint paint);
        boolean sound(UUID viewer,UUID world,V at,String sound,float volume,float pitch);
        default void failed(String id,RuntimeException error) {}
    }
    public interface Effect {
        int duration();
        default V offset(){return V.ZERO;}
        default V direction(){return null;}
        default double extent(){return 20;}
        default int sampleBudget(){return 384;}
        void frame(int age,Canvas canvas);
    }
    private record Sample(Instance instance,Layer layer,Paint paint,V at,int index) {}
    private record Cue(Instance instance,Layer layer,V at,String sound,float volume,float pitch) {}
    private static final class Instance {
        final UUID id=UUID.randomUUID();final String skill;final Pose anchor;Pose pose;final Mode mode;final Effect effect;final long seed,start,deadline,order;final int priority;final boolean mirrored;
        Instance(String skill,Pose pose,Mode mode,Effect effect,long seed,long start,long deadline,long order,int priority,boolean mirrored){this.skill=skill;this.anchor=pose;this.pose=pose;this.mode=mode;this.effect=effect;this.seed=seed;this.start=start;this.deadline=deadline;this.order=order;this.priority=priority;this.mirrored=mirrored;}
    }
    public record Stats(int active,int sampled,int sent,int rejectedSamples,int rejectedSends,int deliveryChecks,int soundSends,long frameNanos,long totalSent) {}
    private final Transport transport;private final Limits limits;private final LongSupplier clock;
    private final Map<UUID,Instance> active=new LinkedHashMap<>();
    private final List<Sample> critical=new ArrayList<>(),normal=new ArrayList<>();private final List<Cue> cues=new ArrayList<>();
    private long tick,sequence,totalSent;private boolean closed;private int rejectedSamples;
    private Stats stats=new Stats(0,0,0,0,0,0,0,0,0);
    public VfxRuntime(Transport transport,Limits limits,LongSupplier clock){this.transport=Objects.requireNonNull(transport);this.limits=Objects.requireNonNull(limits);this.clock=clock;}
    public Limits limits(){return limits;}
    public Stats stats(){return new Stats(active.size(),stats.sampled,stats.sent,stats.rejectedSamples,stats.rejectedSends,stats.deliveryChecks,stats.soundSends,stats.frameNanos,totalSent);}
    public UUID play(String skill,Pose pose,Mode mode,Effect effect,long seed,boolean mirrored,int delay,int priority){
        if(closed||delay<0||delay>200||effect.duration()<1||effect.duration()>660||effect.sampleBudget()<1||effect.sampleBudget()>384||!Double.isFinite(effect.extent())||effect.extent()<1||effect.extent()>32||!valid(pose))return null;
        Instance replaced=null;
        if(active.values().stream().filter(i->i.pose.owner.equals(pose.owner)).count()>=limits.perOwner){
            replaced=active.values().stream().filter(i->i.pose.owner.equals(pose.owner)&&i.priority<priority)
                    .min(Comparator.comparingInt(i->i.priority)).orElse(null);
            if(replaced==null)return null;
        }
        if(active.size()>=limits.instances&&replaced==null){
            replaced=active.values().stream().filter(i->i.priority<priority).min(Comparator.comparingInt(i->i.priority)).orElse(null);
            if(replaced==null)return null;
        }
        if(replaced!=null)active.remove(replaced.id);
        var instance=new Instance(skill,pose,mode,effect,seed,tick+delay,clock.getAsLong()+(delay+effect.duration())*50L+2000,sequence++,priority,mirrored);
        active.put(instance.id,instance);return instance.id;
    }
    public void cancel(UUID owner,Mode mode){active.values().removeIf(i->i.pose.owner.equals(owner)&&(mode==null||mode==i.mode));}
    public void cancelInstance(UUID id){active.remove(id);}
    public boolean active(UUID id){return active.containsKey(id);}
    public void clear(Mode mode){active.values().removeIf(i->mode==null||i.mode==mode);}
    public void shutdown(){closed=true;active.clear();critical.clear();normal.clear();cues.clear();}
    public void tick(){
        if(closed)return;long began=System.nanoTime();critical.clear();normal.clear();cues.clear();rejectedSamples=0;
        var instances=new ArrayList<>(active.values());int count=Math.max(1,instances.size());
        instances.sort(Comparator.<Instance>comparingInt(i->i.priority).reversed().thenComparingLong(i->(i.order+tick)%count));
        for(var instance:instances){
            int age=(int)(tick-instance.start);
            try {var at=instance.anchor.origin.add(instance.effect.offset());instance.pose=new Pose(instance.anchor.owner,instance.anchor.world,at,instance.effect.direction()==null?instance.anchor.direction:instance.effect.direction(),instance.anchor.yaw,instance.anchor.target);}
            catch(RuntimeException error){active.remove(instance.id);transport.failed(instance.skill,error);continue;}
            if(clock.getAsLong()>instance.deadline||!valid(instance.pose)||age>=instance.effect.duration()){active.remove(instance.id);continue;}
            if(age<0)continue;
            try{instance.effect.frame(age,new Canvas(instance));}
            catch(RuntimeException error){active.remove(instance.id);critical.removeIf(s->s.instance==instance);normal.removeIf(s->s.instance==instance);cues.removeIf(c->c.instance==instance);transport.failed(instance.skill,error);}
        }
        List<Sample> samples=new ArrayList<>(critical);samples.addAll(normal);
        samples.sort(Comparator.<Sample>comparingInt(s->s.layer.group()).thenComparingInt(s->-s.instance.priority).thenComparingInt(s->s.layer.ordinal()));
        Map<UUID,List<Viewer>> audiences=new HashMap<>();Map<UUID,Integer> sentTo=new HashMap<>();
        int sent=0,checks=0,rejected=0,soundSends=0;
        for(var sample:samples){
            if(sent>=limits.globalSends||checks>=limits.deliveryChecks){rejected++;continue;}
            for(var viewer:audiences.computeIfAbsent(sample.instance.id,id->audience(sample.instance))){
                if(checks>=limits.deliveryChecks||sent>=limits.globalSends){rejected++;break;}checks++;
                double distance=viewer.position.sub(sample.at).length();if(distance>limits.distance)continue;
                int stride=viewer.quality.stride(sample.layer);if(stride==0)continue;
                if(distance>16)stride*=2;
                if(sample.index%stride!=0)continue;
                int budget=Math.min(limits.viewerSends,viewer.quality.budget);
                if(sentTo.getOrDefault(viewer.id,0)>=budget){rejected++;continue;}
                try{if(!transport.particle(viewer.id,sample.instance.pose.world,sample.at,sample.paint))continue;}
                catch(RuntimeException error){transport.failed(sample.instance.skill,error);continue;}
                sent++;sentTo.merge(viewer.id,1,Integer::sum);
            }
        }
        cues.sort(Comparator.comparingInt(c->c.layer.ordinal()));
        soundLoop:for(var cue:cues)for(var viewer:audiences.computeIfAbsent(cue.instance.id,id->audience(cue.instance))){
            if(soundSends>=limits.sounds||checks>=limits.deliveryChecks)break soundLoop;checks++;
            if(viewer.position.sub(cue.at).length()>limits.distance||viewer.quality==Quality.LOW&&cue.layer==Layer.DECORATION)continue;
            try{if(transport.sound(viewer.id,cue.instance.pose.world,cue.at,cue.sound,cue.volume,cue.pitch))soundSends++;}
            catch(RuntimeException error){transport.failed(cue.instance.skill,error);}
        }
        totalSent+=sent;stats=new Stats(active.size(),samples.size(),sent,rejectedSamples,rejected,checks,soundSends,System.nanoTime()-began,totalSent);tick++;
    }
    private List<Viewer> audience(Instance instance){
        try{return transport.viewers(instance.pose,instance.mode,limits.distance).stream().filter(v->v.world.equals(instance.pose.world)).limit(64).toList();}
        catch(RuntimeException error){transport.failed(instance.skill,error);return List.of();}
    }
    private boolean valid(Pose pose){
        try{return transport.valid(pose);}catch(RuntimeException error){transport.failed("pose",error);return false;}
    }
    public final class Canvas {
        private final Instance instance;private final Frame frame;private final EnumMap<Layer,Integer> indexes=new EnumMap<>(Layer.class);private int attempts;
        Canvas(Instance instance){this.instance=instance;this.frame=instance.pose.frame();}
        public long seed(){return instance.seed;}
        public boolean mirrored(){return instance.mirrored;}
        public V origin(){return instance.pose.origin;}
        public void point(Layer layer,Paint paint,V local){
            if(++attempts>instance.effect.sampleBudget()){rejectedSamples++;return;}
            V at=frame.point(local);Paint worldPaint=paint.kind==ParticleKind.DIRECTIONAL_SPARK?new Paint(paint.kind,paint.color,paint.endColor,paint.size,frame.vector(paint.direction),paint.speed):paint;
            int index=indexes.merge(layer,1,Integer::sum)-1;
            if(at.sub(instance.pose.origin).length()>instance.effect.extent()){rejectedSamples++;return;}
            var destination=layer.critical()?critical:normal;
            if(destination.size()>=limits.samples/2){rejectedSamples++;return;}
            destination.add(new Sample(instance,layer,worldPaint,at,index));
        }
        public void line(Layer layer,Paint paint,V from,V to,int samples){for(V point:VfxMath.line(from,to,samples))point(layer,paint,point);}
        public void polyline(Layer layer,Paint paint,List<V> points,int samples){if(points.size()>64||(long)points.size()*samples>384)throw new IllegalArgumentException("Polyline sample limit");for(int i=1;i<points.size();i++)line(layer,paint,points.get(i-1),points.get(i),samples);}
        public void sound(Layer layer,String sound,float volume,float pitch){
            soundAt(layer,V.ZERO,sound,volume,pitch);
        }
        public void soundAt(Layer layer,V local,String sound,float volume,float pitch){
            if(cues.size()<limits.instances*3&&volume>=0&&volume<=1&&pitch>=.5&&pitch<=2)cues.add(new Cue(instance,layer,frame.point(local),sound,volume,pitch));
        }
    }
}

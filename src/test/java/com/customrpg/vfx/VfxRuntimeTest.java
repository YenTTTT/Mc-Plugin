package com.customrpg.vfx;

import com.customrpg.vfx.VfxMath.V;
import com.customrpg.vfx.VfxRuntime.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class VfxRuntimeTest {
    static final UUID WORLD=UUID.randomUUID(),OWNER=UUID.randomUUID(),VIEWER=UUID.randomUUID();
    static record Emission(UUID viewer,V at,Paint paint) {}
    static class Scene implements Transport {
        List<Viewer> audience=new ArrayList<>(List.of(new Viewer(VIEWER,WORLD,V.ZERO,Quality.HIGH)));
        List<Emission> particles=new ArrayList<>();List<String> sounds=new ArrayList<>();
        Set<UUID> invalidOwners=new HashSet<>(),invalidTargets=new HashSet<>();int failures;
        public boolean valid(Pose p){return !invalidOwners.contains(p.owner())&&!invalidTargets.contains(p.target());}
        public List<Viewer> viewers(Pose p,Mode mode,double distance){return audience;}
        public boolean particle(UUID viewer,UUID world,V at,Paint paint){particles.add(new Emission(viewer,at,paint));return true;}
        public boolean sound(UUID viewer,UUID world,V at,String sound,float volume,float pitch){sounds.add(sound);return true;}
        public void failed(String id,RuntimeException error){failures++;}
    }
    static Pose pose(){return new Pose(OWNER,WORLD,V.ZERO,V.FORWARD,0,null);}
    static Effect pointEffect(int duration){return new Effect(){public int duration(){return duration;}public void frame(int age,VfxRuntime.Canvas c){c.point(Layer.OUTLINE,Paint.dust(0xFFFFFF,1),new V(age,0,1));}};}

    @Test void durationDelayStopAndShutdownCleanEveryInstanceIncludingQueuedPreviews(){
        var scene=new Scene();var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);
        runtime.play("cast",pose(),Mode.COMBAT,pointEffect(3),1,false,0,20);
        runtime.play("later",pose(),Mode.PREVIEW,pointEffect(3),1,false,5,10);
        runtime.tick();assertEquals(1,scene.particles.size());runtime.cancel(OWNER,Mode.PREVIEW);
        for(int i=0;i<8;i++)runtime.tick();assertEquals(3,scene.particles.size());assertEquals(0,runtime.stats().active());
        runtime.play("late",pose(),Mode.PREVIEW,pointEffect(3),1,false,10,10);runtime.shutdown();runtime.tick();
        assertEquals(0,runtime.stats().active());assertNull(runtime.play("closed",pose(),Mode.PREVIEW,pointEffect(3),1,false,0,0));
    }
    @Test void wallTimeExpiryDoesNotCatchUpOrProduceABurstAfterLag(){
        var scene=new Scene();var clock=new AtomicLong();var runtime=new VfxRuntime(scene,Limits.defaults(),clock::get);
        runtime.play("lag",pose(),Mode.PREVIEW,pointEffect(30),1,false,0,0);runtime.tick();clock.set(10000);runtime.tick();
        assertEquals(1,scene.particles.size());assertEquals(0,runtime.stats().active());
    }
    @Test void ownerWorldOrTargetInvalidationStopsFutureFrames(){
        for(boolean target:new boolean[]{false,true}){
            var scene=new Scene();var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);UUID entity=UUID.randomUUID();
            var pose=new Pose(OWNER,WORLD,V.ZERO,V.FORWARD,0,target?entity:null);
            runtime.play("life",pose,Mode.COMBAT,pointEffect(20),1,false,0,0);runtime.tick();
            if(target)scene.invalidTargets.add(entity);else scene.invalidOwners.add(OWNER);
            runtime.tick();assertEquals(1,scene.particles.size());assertEquals(0,runtime.stats().active());
        }
    }
    @Test void effectsThatFailDoNotKeepResourcesOrStopOtherAnimations(){
        var scene=new Scene();var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);
        Effect broken=new Effect(){public int duration(){return 10;}public void frame(int age,VfxRuntime.Canvas c){throw new IllegalStateException("bad effect");}};
        runtime.play("broken",pose(),Mode.PREVIEW,broken,1,false,0,0);runtime.play("good",pose(),Mode.PREVIEW,pointEffect(2),1,false,0,0);
        runtime.tick();assertEquals(1,scene.failures);assertEquals(1,scene.particles.size());assertEquals(1,runtime.stats().active());
    }
    @Test void globalViewerSamplingWorkAndSoundLimitsHoldUnderSixtyFourConcurrentCasters(){
        var scene=new Scene();scene.audience.clear();for(int i=0;i<20;i++)scene.audience.add(new Viewer(UUID.randomUUID(),WORLD,V.ZERO,Quality.HIGH));
        var limits=new Limits(64,6,128,24,64,128,2,32);var runtime=new VfxRuntime(scene,limits,()->0);
        Effect crowded=new Effect(){public int duration(){return 2;}public void frame(int age,VfxRuntime.Canvas c){
            for(int i=0;i<100;i++)c.point(i%2==0?Layer.OUTLINE:Layer.DECORATION,Paint.dust(0xFFFFFF,1),new V(0,i*.01,1));c.sound(Layer.OUTLINE,"a",.5f,1);
        }};
        for(int i=0;i<64;i++)assertNotNull(runtime.play("load",new Pose(UUID.randomUUID(),WORLD,V.ZERO,V.FORWARD,0,null),Mode.COMBAT,crowded,i,false,0,1));
        assertNull(runtime.play("full",pose(),Mode.COMBAT,crowded,1,false,0,1));runtime.tick();
        var stats=runtime.stats();assertTrue(stats.sampled()<=128);assertTrue(stats.sent()<=64);assertTrue(stats.deliveryChecks()<=128);assertTrue(stats.soundSends()<=2);
        for(var viewer:scene.audience)assertTrue(scene.particles.stream().filter(p->p.viewer.equals(viewer.id())).count()<=24);
        assertTrue(stats.rejectedSamples()>0);assertTrue(stats.rejectedSends()>0);
    }
    @ParameterizedTest @EnumSource(Quality.class)
    void warningAndImpactHavePriorityOverDecorationAndQualityNeverChangesTheTimeline(Quality quality){
        var scene=new Scene();scene.audience=List.of(new Viewer(VIEWER,WORLD,V.ZERO,quality));
        var runtime=new VfxRuntime(scene,new Limits(4,4,128,24,64,1024,4,32),()->0);var ages=new ArrayList<Integer>();
        Effect effect=new Effect(){public int duration(){return 3;}public void frame(int age,VfxRuntime.Canvas c){ages.add(age);
            for(int i=0;i<40;i++)c.point(Layer.DECORATION,Paint.dust(1,1),new V(i*.02,0,1));
            for(int i=0;i<4;i++)c.point(Layer.WARNING,Paint.dust(2,1),new V(0,i*.03,1));
            c.point(Layer.IMPACT,Paint.dust(3,1),new V(0,0,1));
        }};
        runtime.play("priorities",pose(),Mode.COMBAT,effect,1,false,0,1);for(int i=0;i<4;i++)runtime.tick();
        assertEquals(List.of(0,1,2),ages);assertEquals(12,scene.particles.stream().filter(p->p.paint.color()==2).count());
        assertEquals(3,scene.particles.stream().filter(p->p.paint.color()==3).count());
        if(quality==Quality.LOW)assertEquals(0,scene.particles.stream().filter(p->p.paint.color()==1).count());
    }
    @Test void farViewersGetLodAndOtherWorldsOrOutOfRangeViewersGetNothing(){
        var scene=new Scene();UUID far=UUID.randomUUID(),outside=UUID.randomUUID(),other=UUID.randomUUID();
        scene.audience=List.of(new Viewer(VIEWER,WORLD,V.ZERO,Quality.HIGH),new Viewer(far,WORLD,new V(0,0,24),Quality.HIGH),
                new Viewer(outside,WORLD,new V(0,0,80),Quality.HIGH),new Viewer(other,UUID.randomUUID(),V.ZERO,Quality.HIGH));
        var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);
        Effect many=new Effect(){public int duration(){return 1;}public void frame(int age,VfxRuntime.Canvas c){for(int i=0;i<100;i++)c.point(Layer.OUTLINE,Paint.dust(1,1),new V(i*.01,0,0));}};
        runtime.play("lod",pose(),Mode.COMBAT,many,1,false,0,1);runtime.tick();
        assertEquals(100,scene.particles.stream().filter(p->p.viewer.equals(VIEWER)).count());assertEquals(50,scene.particles.stream().filter(p->p.viewer.equals(far)).count());
        assertEquals(0,scene.particles.stream().filter(p->p.viewer.equals(outside)||p.viewer.equals(other)).count());
    }
    @Test void theThreeEffectsHaveDifferentGeometryMotionRhythmAndSignaturesWithoutUsingColor(){
        var designs=VfxEffects.designs().stream().toList();
        for(int i=0;i<designs.size();i++)for(int j=i+1;j<designs.size();j++){
            var a=designs.get(i);var b=designs.get(j);assertNotEquals(a.silhouette(),b.silhouette());assertNotEquals(a.motion(),b.motion());assertNotEquals(a.rhythm(),b.rhythm());assertNotEquals(a.signature(),b.signature());
        }
        Map<String,Double> heights=new HashMap<>();Map<String,Integer> durations=new HashMap<>();
        for(var design:designs){
            var scene=new Scene();var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);Effect effect=VfxEffects.create(design.id(),42);
            durations.put(design.id(),effect.duration());runtime.play(design.id(),pose(),Mode.PREVIEW,effect,42,false,0,1);
            for(int tick=0;tick<effect.duration()+1;tick++)runtime.tick();
            assertFalse(scene.particles.isEmpty());assertEquals(0,runtime.stats().active());
            double min=scene.particles.stream().mapToDouble(p->p.at.y()).min().orElseThrow(),max=scene.particles.stream().mapToDouble(p->p.at.y()).max().orElseThrow();
            heights.put(design.id(),max-min);
        }
        assertTrue(new HashSet<>(durations.values()).size()>=6);
        assertTrue(heights.get(VfxEffects.THUNDER)>7);assertTrue(heights.get(VfxEffects.SLASH)<1);assertTrue(heights.get(VfxEffects.VOID)>1);
    }
    @Test void directionVectorsRotateWithTheSameLocalFrameAsTheBlade(){
        var scene=new Scene();var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);
        Effect spark=new Effect(){public int duration(){return 1;}public void frame(int age,VfxRuntime.Canvas c){c.point(Layer.IMPACT,Paint.spark(V.FORWARD),V.ZERO);}};
        runtime.play("direction",new Pose(OWNER,WORLD,V.ZERO,new V(-1,0,0),Math.PI/2,null),Mode.COMBAT,spark,1,false,0,1);runtime.tick();
        assertEquals(-1,scene.particles.getFirst().paint.direction().x(),1e-9);assertEquals(0,scene.particles.getFirst().paint.direction().z(),1e-9);
    }
    @Test void aSecondMainSlashCanReplaceAnImpactInsteadOfLosingItsSilhouette(){
        var scene=new Scene();var runtime=new VfxRuntime(scene,new Limits(4,2,128,96,128,1024,4,32),()->0);
        assertNotNull(runtime.play("main",pose(),Mode.COMBAT,pointEffect(12),1,false,0,30));
        assertNotNull(runtime.play("hit",pose(),Mode.COMBAT,VfxEffects.impact(),1,false,0,20));
        assertNotNull(runtime.play("return",pose(),Mode.COMBAT,pointEffect(12),1,true,0,30));
        assertEquals(2,runtime.stats().active());runtime.tick();assertEquals(2,scene.particles.size());
        assertNull(runtime.play("preview",pose(),Mode.PREVIEW,pointEffect(12),1,false,0,10));
    }
    @Test void anIndividualSkillCannotExceedItsDeclaredSampleBudget(){
        var scene=new Scene();var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);
        Effect effect=new Effect(){public int duration(){return 1;}public int sampleBudget(){return 16;}public void frame(int age,VfxRuntime.Canvas c){for(int i=0;i<100;i++)c.point(Layer.OUTLINE,Paint.dust(1,1),new V(0,0,1));}};
        runtime.play("budget",pose(),Mode.COMBAT,effect,1,false,0,1);runtime.tick();
        assertEquals(16,runtime.stats().sampled());assertEquals(84,runtime.stats().rejectedSamples());
        for(var design:VfxEffects.designs())assertEquals(design.sampleBudget(),VfxEffects.create(design.id(),42).sampleBudget());
    }
}

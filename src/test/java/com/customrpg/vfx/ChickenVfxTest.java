package com.customrpg.vfx;

import com.customrpg.talents.ChickenSkill;
import com.customrpg.vfx.VfxRuntime.*;
import com.customrpg.vfx.VfxMath.V;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChickenVfxTest {
    @Test void eightDifferentStoryboardsRenderForEveryFacingWithinSampleLimits(){var classes=new HashSet<Class<?>>();for(var s:ChickenSkill.values())for(V d:List.of(V.FORWARD,new V(1,0,0),V.UP,new V(0,-1,0))){var scene=new VfxRuntimeTest.Scene();var r=new VfxRuntime(scene,Limits.defaults(),()->0);var effect=ChickenVfx.preview(s.id,42);classes.add(effect.getClass());assertNotNull(r.play(s.id,new Pose(VfxRuntimeTest.OWNER,VfxRuntimeTest.WORLD,V.ZERO,d,1,null),Mode.PREVIEW,effect,42,false,0,10));for(int i=0;i<=effect.duration();i++){r.tick();assertEquals(0,r.stats().rejectedSamples(),s.id+" tick "+i);}assertFalse(scene.particles.isEmpty());assertEquals(0,scene.failures);assertEquals(0,r.stats().active());assertEquals(s.id,VfxEffects.resolve(s.id));}assertEquals(8,classes.size());for(var list:List.of(ChickenVfx.designs().stream().map(VfxEffects.Dna::silhouette).toList(),ChickenVfx.designs().stream().map(VfxEffects.Dna::motion).toList(),ChickenVfx.designs().stream().map(VfxEffects.Dna::signature).toList()))assertEquals(8,new HashSet<>(list).size());}
    @Test void godLowQualityKeepsLandingWarning(){var scene=new VfxRuntimeTest.Scene();scene.audience=List.of(new Viewer(VfxRuntimeTest.VIEWER,VfxRuntimeTest.WORLD,V.ZERO,Quality.LOW));var r=new VfxRuntime(scene,Limits.defaults(),()->0);r.play("chicken_god",VfxRuntimeTest.pose(),Mode.PREVIEW,ChickenVfx.preview("chicken_god",42),42,false,0,100);for(int i=0;i<31;i++)r.tick();assertTrue(scene.particles.stream().anyMatch(p->p.at().y()<.1&&p.at().length()>6));assertTrue(scene.particles.stream().anyMatch(p->p.at().y()>6));}
    @Test void sixtyFourChickenAnimationsRespectGlobalLimitsAndFinishWithoutInstances(){var scene=new VfxRuntimeTest.Scene();var r=new VfxRuntime(scene,Limits.defaults(),()->0);for(int i=0;i<64;i++){var s=ChickenSkill.values()[i%8];assertNotNull(r.play(s.id,new Pose(UUID.randomUUID(),VfxRuntimeTest.WORLD,V.ZERO,V.FORWARD,0,null),Mode.COMBAT,ChickenVfx.preview(s.id,i),i,false,0,s==ChickenSkill.GOD?100:40));}for(int t=0;t<660;t++){r.tick();assertTrue(r.stats().sampled()<=8192);assertTrue(r.stats().sent()<=4000);assertTrue(r.stats().deliveryChecks()<=16000);scene.particles.clear();}assertEquals(0,r.stats().active());}
    @Test void blessingEggsUseTheGodsFinalPositionInsteadOfItsOriginalLanding(){
        var scene=new VfxRuntimeTest.Scene();var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);var a=new ChickenVfx.Anchor();runtime.play("chicken_god",VfxRuntimeTest.pose(),Mode.COMBAT,new ChickenVfx.God(a),42,false,0,100);for(int i=0;i<631;i++)runtime.tick();scene.particles.clear();a.offset=new V(4,0,3);runtime.tick();assertFalse(scene.particles.isEmpty());assertTrue(scene.particles.stream().allMatch(p->p.at().x()>3&&p.at().x()<5&&Math.abs(p.at().z()-3)<.1));assertEquals(0,runtime.stats().rejectedSamples());
    }

    @Test void unlimitedParticipationNeverExpandsTheDecorativeSamplingBudget(){var scene=new VfxRuntimeTest.Scene();var r=new VfxRuntime(scene,Limits.defaults(),()->0);r.play("chicken_stampede",VfxRuntimeTest.pose(),Mode.COMBAT,new ChickenVfx.Stampede(10000),1,false,0,40);for(int i=0;i<37;i++){r.tick();assertEquals(0,r.stats().rejectedSamples());}assertEquals(0,r.stats().active());}

    @Test void eggTrailRendersAtItsActualWorldPositionWithItsChangingFlightTangent(){
        var scene=new VfxRuntimeTest.Scene();var r=new VfxRuntime(scene,Limits.defaults(),()->0);var a=new ChickenVfx.Anchor();a.offset=new V(4,1,-2);a.travel=new V(1,1,0).unit();r.play("chicken_egg",VfxRuntimeTest.pose(),Mode.COMBAT,new ChickenVfx.Trail(a),42,false,0,40);r.tick();assertTrue(scene.particles.get(0).at().sub(a.offset).length()<1e-9);assertTrue(scene.particles.get(2).at().sub(a.offset.sub(a.travel.mul(.24))).length()<1e-9);scene.particles.clear();a.offset=new V(5,0,-2);a.travel=new V(-1,-1,1).unit();r.tick();assertTrue(scene.particles.get(0).at().sub(a.offset).length()<1e-9);assertTrue(scene.particles.get(2).at().sub(a.offset.sub(a.travel.mul(.24))).length()<1e-9);
    }

}

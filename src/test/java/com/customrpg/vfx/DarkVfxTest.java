package com.customrpg.vfx;

import com.customrpg.talents.DarkSkill;
import com.customrpg.vfx.VfxRuntime.*;
import com.customrpg.vfx.VfxMath.V;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DarkVfxTest {
    @Test void eightDifferentStoryboardsRenderForEveryFacingWithinSampleLimits(){var classes=new HashSet<Class<?>>();for(var s:DarkSkill.values())for(V d:List.of(V.FORWARD,new V(1,0,0),V.UP,new V(0,-1,0))){var scene=new VfxRuntimeTest.Scene();var r=new VfxRuntime(scene,Limits.defaults(),()->0);var effect=DarkVfx.preview(s.id,42);classes.add(effect.getClass());assertNotNull(r.play(s.id,new Pose(VfxRuntimeTest.OWNER,VfxRuntimeTest.WORLD,V.ZERO,d,1,null),Mode.PREVIEW,effect,42,false,0,10));for(int i=0;i<=effect.duration();i++){r.tick();assertEquals(0,r.stats().rejectedSamples(),s.id+" tick "+i);}assertFalse(scene.particles.isEmpty());assertEquals(0,scene.failures);assertEquals(0,r.stats().active());assertEquals(s.id,VfxEffects.resolve(s.id));}assertEquals(8,classes.size());for(var list:List.of(DarkVfx.designs().stream().map(VfxEffects.Dna::silhouette).toList(),DarkVfx.designs().stream().map(VfxEffects.Dna::motion).toList(),DarkVfx.designs().stream().map(VfxEffects.Dna::signature).toList()))assertEquals(8,new HashSet<>(list).size());}
    @Test void eclipseLowQualityKeepsGroundWarningsAndTheBrokenSunOverhead(){var scene=new VfxRuntimeTest.Scene();scene.audience=List.of(new Viewer(VfxRuntimeTest.VIEWER,VfxRuntimeTest.WORLD,V.ZERO,Quality.LOW));var r=new VfxRuntime(scene,Limits.defaults(),()->0);r.play("dark_eclipse",VfxRuntimeTest.pose(),Mode.PREVIEW,new DarkVfx.Eclipse(42),42,false,0,100);for(int i=0;i<25;i++)r.tick();assertTrue(scene.particles.stream().anyMatch(p->p.at().y()<.1&&p.at().length()>6));assertTrue(scene.particles.stream().anyMatch(p->p.at().y()>7));}
    @Test void sixtyFourDarkAnimationsRespectGlobalLimitsAndFinishWithoutInstances(){var scene=new VfxRuntimeTest.Scene();var r=new VfxRuntime(scene,Limits.defaults(),()->0);for(int i=0;i<64;i++){var s=DarkSkill.values()[i%8];assertNotNull(r.play(s.id,new Pose(UUID.randomUUID(),VfxRuntimeTest.WORLD,V.ZERO,V.FORWARD,0,null),Mode.COMBAT,DarkVfx.preview(s.id,i),i,false,0,s==DarkSkill.ECLIPSE?100:40));}for(int t=0;t<150;t++){r.tick();assertTrue(r.stats().sampled()<=8192);assertTrue(r.stats().sent()<=4000);assertTrue(r.stats().deliveryChecks()<=16000);scene.particles.clear();}assertEquals(0,r.stats().active());}
    @Test void previewFoldAndEchoAreAutomaticAndHaveNoCombatService(){for(String id:List.of("dark_fracture","dark_echo","dark_servant")){var scene=new VfxRuntimeTest.Scene();var r=new VfxRuntime(scene,Limits.defaults(),()->0);r.play(id,VfxRuntimeTest.pose(),Mode.PREVIEW,DarkVfx.preview(id,42),42,false,0,10);for(int i=0;i<100;i++)r.tick();assertFalse(scene.particles.isEmpty());if(id.equals("dark_fracture"))assertTrue(scene.sounds.contains("block.respawn_anchor.deplete"));}}
}

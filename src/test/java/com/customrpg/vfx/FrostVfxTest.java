package com.customrpg.vfx;

import com.customrpg.talents.FrostSkill;
import com.customrpg.vfx.VfxMath.V;
import com.customrpg.vfx.VfxRuntime.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FrostVfxTest {
    @Test void mirrorPreviewShowsItsCounterSignatureWithoutAnyAttackOrCombatService() {
        var scene=new VfxRuntimeTest.Scene();var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);
        runtime.play("frost_mirror",VfxRuntimeTest.pose(),Mode.PREVIEW,FrostVfx.preview("frost_mirror",42),42,false,0,10);
        for(int i=0;i<46;i++)runtime.tick();assertTrue(scene.sounds.contains("block.glass.break"));
        assertTrue(scene.particles.stream().anyMatch(p->p.at().z()>3));
    }
    @Test void eightDifferentAnimationClassesRenderInEveryDirectionWithinTheirBudgets() {
        Set<Class<?>> classes=new HashSet<>();
        for(var skill:FrostSkill.values())for(V direction:List.of(V.FORWARD,new V(1,0,0),new V(-1,0,-1),V.UP,new V(0,-1,0))) {
            var scene=new VfxRuntimeTest.Scene();var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);var effect=FrostVfx.preview(skill.id,42);classes.add(effect.getClass());
            var pose=new Pose(VfxRuntimeTest.OWNER,VfxRuntimeTest.WORLD,V.ZERO,direction,Math.PI/3,null);
            assertNotNull(runtime.play(skill.id,pose,Mode.PREVIEW,effect,42,false,0,10));
            for(int i=0;i<=effect.duration();i++){runtime.tick();assertEquals(0,runtime.stats().rejectedSamples(),skill.id+" tick "+i);assertTrue(runtime.stats().sampled()<=effect.sampleBudget());}
            assertFalse(scene.particles.isEmpty(),skill.id);assertEquals(0,scene.failures,skill.id);assertEquals(0,runtime.stats().active());
        }
        assertEquals(8,classes.size());
    }
    @Test void lowQualityKeepsCrownDangerWarningAndUpperCrystalSilhouette() {
        var scene=new VfxRuntimeTest.Scene();scene.audience=List.of(new Viewer(VfxRuntimeTest.VIEWER,VfxRuntimeTest.WORLD,V.ZERO,Quality.LOW));
        var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);runtime.play("frost_crown",VfxRuntimeTest.pose(),Mode.PREVIEW,new FrostVfx.Crown(),42,false,0,40);
        for(int i=0;i<30;i++)runtime.tick();
        assertTrue(scene.particles.stream().anyMatch(p->p.at().y()<.1&&p.at().length()>5));
        assertTrue(scene.particles.stream().anyMatch(p->p.at().y()>5));
    }
    @Test void mirrorFollowsItsAnchorWithoutAccumulatingOffsetAndExplicitCancellationCleansIt() {
        var scene=new VfxRuntimeTest.Scene();var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);var anchor=new FrostVfx.Anchor();
        var id=runtime.play("mirror",VfxRuntimeTest.pose(),Mode.COMBAT,new FrostVfx.Mirror(anchor),1,false,0,30);
        for(int i=0;i<8;i++)runtime.tick();scene.particles.clear();anchor.offset=new V(5,0,0);runtime.tick();runtime.tick();
        assertTrue(scene.particles.stream().allMatch(p->p.at().x()>3&&p.at().x()<7));
        runtime.cancelInstance(id);assertFalse(runtime.active(id));assertEquals(0,runtime.stats().active());
    }
    @Test void allFrostPreviewsHaveDistinctDesignDataAndResolveByIdAndShortName() {
        for(var skill:FrostSkill.values()) {
            assertEquals(skill.id,VfxEffects.resolve(skill.id));assertEquals(skill.id,VfxEffects.resolve(skill.name().toLowerCase(Locale.ROOT)));
            var dna=VfxEffects.design(skill.id);assertNotNull(dna);assertEquals(skill.title,dna.name());
            assertEquals(dna.sampleBudget(),VfxEffects.create(skill.id,42).sampleBudget());
        }
        assertEquals(43,VfxEffects.designs().size());
    }
    @Test void sixtyFourConcurrentFrostCastersRespectGlobalDeliveryAndLifecycleLimits() {
        var scene=new VfxRuntimeTest.Scene();var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);
        for(int i=0;i<64;i++) {var skill=FrostSkill.values()[i%8];var pose=new Pose(UUID.randomUUID(),VfxRuntimeTest.WORLD,V.ZERO,V.FORWARD,0,null);
            assertNotNull(runtime.play(skill.id,pose,Mode.COMBAT,FrostVfx.preview(skill.id,i),i,false,0,40));}
        for(int i=0;i<134;i++){runtime.tick();var stats=runtime.stats();assertTrue(stats.sampled()<=8192);assertTrue(stats.sent()<=4000);assertTrue(stats.deliveryChecks()<=16000);scene.particles.clear();}
        assertEquals(0,runtime.stats().active());
    }
}

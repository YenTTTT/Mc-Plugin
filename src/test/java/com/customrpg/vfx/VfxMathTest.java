package com.customrpg.vfx;

import com.customrpg.vfx.VfxMath.*;
import com.customrpg.vfx.VfxTimeline.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class VfxMathTest {
    @Test void groundFramesNeverTurnUpsideDownWhenTheCasterYawDiffersFromTheEffectDirection(){
        for(double yaw:new double[]{0,90,180,270})for(var direction:java.util.List.of(V.FORWARD,new V(1,0,0),new V(0,0,-1),new V(-1,0,0)))
            assertEquals(1,Frame.facing(V.ZERO,direction,Math.toRadians(yaw)).up().y(),1e-9);
    }
    @ParameterizedTest @CsvSource({"0,0,1,0","-1,0,0,90","0,0,-1,180","1,0,0,270","0,1,0,0","0,-1,0,180","1,1,1,315","-1,-1,-1,135"})
    void localFramesStayOrthonormalAtEveryYawIncludingStraightUpAndDown(double x,double y,double z,double yaw){
        var frame=Frame.facing(new V(4,5,6),new V(x,y,z),Math.toRadians(yaw));
        assertEquals(0,frame.right().dot(frame.up()),1e-9);assertEquals(0,frame.right().dot(frame.forward()),1e-9);
        assertEquals(0,frame.forward().dot(frame.up()),1e-9);
        assertEquals(1,frame.up().length(),1e-9);assertEquals(1,frame.right().length(),1e-9);assertEquals(1,frame.forward().length(),1e-9);
        assertEquals(1,frame.right().cross(frame.up()).dot(frame.forward()),1e-9);
        assertEquals(1,frame.point(new V(0,0,1)).sub(frame.origin()).length(),1e-9);
    }
    @Test void interpolationPassesItsControlEndpoints(){
        V a=new V(-2,3,4),b=new V(1,0,1),c=new V(3,4,1),d=new V(6,3,2);
        assertEquals(a,VfxMath.bezier(a,b,c,d,0));assertEquals(d,VfxMath.bezier(a,b,c,d,1));
        assertEquals(b,VfxMath.catmullRom(a,b,c,d,0));assertEquals(c,VfxMath.catmullRom(a,b,c,d,1));
        assertTrue(VfxMath.bezier(a,b,c,d,.5).y()>0);
    }
    @Test void rotationAndNonUniformTransformsPreserveExpectedGeometry(){
        V rotated=VfxMath.rotate(new V(1,0,0),V.UP,Math.PI/2);
        assertEquals(0,rotated.x(),1e-9);assertEquals(-1,rotated.z(),1e-9);
        var transform=new Transform(new V(4,2,3),new V(2,1,.5),V.UP,Math.PI/2);
        V point=transform.apply(new V(1,0,0));assertEquals(4,point.x(),1e-9);assertEquals(1,point.z(),1e-9);
    }
    @Test void seededBranchesAndFragmentsAreReproducibleAndRespectEndpoints(){
        var a=VfxMath.jagged(V.ZERO,new V(0,8,2),10,.7,41);
        assertEquals(a,VfxMath.jagged(V.ZERO,new V(0,8,2),10,.7,41));
        assertNotEquals(a,VfxMath.jagged(V.ZERO,new V(0,8,2),10,.7,42));
        assertEquals(V.ZERO,a.getFirst());assertEquals(new V(0,8,2),a.getLast());
        assertEquals(VfxMath.polygon(4,.5,41),VfxMath.polygon(4,.5,41));
        assertTrue(VfxMath.polygon(4,.5,41).stream().anyMatch(v->Math.abs(v.z())>.01));
    }
    @Test void ribbonSamplingAndAllPublicSampleCountsAreBounded(){
        assertEquals(60,VfxMath.ribbon(t->new V(t,0,0),t->new V(t,1,0),20,3).size());
        assertThrows(IllegalArgumentException.class,()->VfxMath.ribbon(t->V.ZERO,t->V.UP,1000,1000));
        assertThrows(IllegalArgumentException.class,()->VfxMath.ribbon(t->V.ZERO,t->V.UP,Integer.MAX_VALUE,Integer.MAX_VALUE));
        assertEquals(12,VfxMath.surface((u,v)->new V(u,v,u*v),4,3).size());
        assertThrows(IllegalArgumentException.class,()->VfxMath.line(V.ZERO,V.UP,1000));
        assertThrows(IllegalArgumentException.class,()->new V(Double.NaN,0,0));
        assertEquals(64,VfxMath.helix(2,3,new V(2,1,.5),64).size());
    }
    @Test void independentTracksClampHoldAndEaseWithoutInventingExtraPhases(){
        for(var ease:Ease.values()){
            var track=new Track(4,12,ease);assertEquals(0,track.at(-10));assertEquals(1,track.at(99));
            double prior=0;for(int tick=0;tick<20;tick++){double next=track.at(tick);assertTrue(next>=prior);prior=next;}
        }
        assertTrue(Ease.IN_CUBIC.apply(.25)<.25);assertTrue(Ease.OUT_CUBIC.apply(.25)>.25);
        assertEquals(.5,Ease.IN_OUT_CUBIC.apply(.5),1e-9);
        assertFalse(new Track(4,12,Ease.LINEAR).active(3));assertFalse(new Track(4,12,Ease.LINEAR).active(12));
    }
}

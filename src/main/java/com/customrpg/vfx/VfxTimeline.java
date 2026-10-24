package com.customrpg.vfx;

import com.customrpg.vfx.VfxMath.V;

/** Independent bounded tracks; effects choose their own phases rather than inheriting a common sequence. */
public final class VfxTimeline {
    private VfxTimeline() {}
    public enum Ease {
        LINEAR {double curve(double t){return t;}},
        IN_CUBIC {double curve(double t){return t*t*t;}},
        OUT_CUBIC {double curve(double t){return 1-Math.pow(1-t,3);}},
        IN_OUT_CUBIC {double curve(double t){return t<.5?4*t*t*t:1-Math.pow(-2*t+2,3)/2;}};
        abstract double curve(double t);
        public double apply(double t){return curve(Math.clamp(t,0,1));}
    }
    public record Track(int start,int end,Ease ease) {
        public Track {if(start<0||end<=start||ease==null)throw new IllegalArgumentException("Invalid timeline track");}
        public double at(double tick){return ease.apply((tick-start)/(double)(end-start));}
        public boolean active(int tick){return tick>=start&&tick<end;}
    }
    public record Transform(V translation,V scale,V axis,double rotation) {
        public V apply(V point){return VfxMath.rotate(point.scale(scale),axis,rotation).add(translation);}
        public static Transform between(Transform a,Transform b,double progress){
            double t=Math.clamp(progress,0,1);
            return new Transform(a.translation.lerp(b.translation,t),a.scale.lerp(b.scale,t),a.axis.lerp(b.axis,t),a.rotation+(b.rotation-a.rotation)*t);
        }
    }
}

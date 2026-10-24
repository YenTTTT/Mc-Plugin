package com.customrpg.vfx;

import java.util.*;
import java.util.function.DoubleFunction;
import java.util.function.BiFunction;

/** Pure geometry in a right/up/forward local frame. No Bukkit objects or world access. */
public final class VfxMath {
    private VfxMath() {}
    public record V(double x,double y,double z) {
        public V { if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Non-finite vector"); }
        public static final V ZERO=new V(0,0,0),UP=new V(0,1,0),FORWARD=new V(0,0,1);
        public V add(V b){return new V(x+b.x,y+b.y,z+b.z);}
        public V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}
        public V mul(double s){return new V(x*s,y*s,z*s);}
        public V scale(V b){return new V(x*b.x,y*b.y,z*b.z);}
        public double dot(V b){return x*b.x+y*b.y+z*b.z;}
        public V cross(V b){return new V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
        public double length(){return Math.sqrt(dot(this));}
        public V unit(){double length=length();return length<1e-9?FORWARD:mul(1/length);}
        public V lerp(V b,double t){return mul(1-t).add(b.mul(t));}
    }
    public record Frame(V origin,V right,V up,V forward) {
        public V point(V local){return origin.add(vector(local));}
        public V vector(V local){return right.mul(local.x).add(up.mul(local.y)).add(forward.mul(local.z));}
        public static Frame facing(V origin,V direction,double yawRadians) {
            V forward=direction.unit();
            V right=V.UP.cross(forward);
            if(right.length()<1e-7){right=new V(Math.cos(yawRadians),0,Math.sin(yawRadians));right=right.sub(forward.mul(right.dot(forward)));}
            if(right.length()<1e-7)right=new V(1,0,0);
            right=right.unit();
            return new Frame(origin,right,forward.cross(right).unit(),forward);
        }
    }
    public static V rotate(V vector,V axis,double radians){
        if(axis.length()<1e-9)return vector;
        V n=axis.unit();double c=Math.cos(radians),s=Math.sin(radians);
        return vector.mul(c).add(n.cross(vector).mul(s)).add(n.mul(n.dot(vector)*(1-c)));
    }
    public static V bezier(V a,V b,V c,V d,double t){
        double q=1-t;return a.mul(q*q*q).add(b.mul(3*q*q*t)).add(c.mul(3*q*t*t)).add(d.mul(t*t*t));
    }
    public static V catmullRom(V a,V b,V c,V d,double t){
        return b.mul(2).add(c.sub(a).mul(t)).add(a.mul(2).sub(b.mul(5)).add(c.mul(4)).sub(d).mul(t*t))
                .add(a.mul(-1).add(b.mul(3)).sub(c.mul(3)).add(d).mul(t*t*t)).mul(.5);
    }
    public static List<V> line(V a,V b,int samples){return curve(t->a.lerp(b,t),samples);}
    public static List<V> curve(DoubleFunction<V> function,int samples){
        if(samples<2||samples>256)throw new IllegalArgumentException("Curve sample limit");
        List<V> points=new ArrayList<>(samples);for(int i=0;i<samples;i++)points.add(function.apply(i/(double)(samples-1)));return List.copyOf(points);
    }
    public static List<V> ribbon(DoubleFunction<V> edgeA,DoubleFunction<V> edgeB,int length,int width){
        if(length<2||width<2||(long)length*width>512)throw new IllegalArgumentException("Surface sample limit");
        List<V> points=new ArrayList<>(length*width);
        for(int i=0;i<length;i++){double t=i/(double)(length-1);V a=edgeA.apply(t),b=edgeB.apply(t);for(int j=0;j<width;j++)points.add(a.lerp(b,j/(double)(width-1)));}
        return List.copyOf(points);
    }
    public static List<V> surface(BiFunction<Double,Double,V> surface,int rows,int columns){
        if(rows<2||columns<2||(long)rows*columns>512)throw new IllegalArgumentException("Surface sample limit");
        List<V> points=new ArrayList<>();
        for(int i=0;i<rows;i++)for(int j=0;j<columns;j++)points.add(surface.apply(i/(double)(rows-1),j/(double)(columns-1)));
        return List.copyOf(points);
    }
    public static V blade(double angle,double radius,double thickness,double across){
        double r=radius-thickness*(1-across);
        return new V(Math.sin(angle)*r,Math.sin(angle*2)*.22+Math.sin(across*Math.PI)*.12,Math.cos(angle)*r);
    }
    public static List<V> helix(double turns,double height,V scale,int samples){
        return curve(t->new V(Math.cos(t*Math.PI*2*turns),t*height,Math.sin(t*Math.PI*2*turns)).scale(scale),samples);
    }
    public static List<V> polygon(int vertices,double radius,long seed){
        if(vertices<3||vertices>12)throw new IllegalArgumentException("Polygon vertex limit");
        var random=new SplittableRandom(seed);List<V> points=new ArrayList<>();
        for(int i=0;i<vertices;i++){double a=Math.PI*2*i/vertices;double r=radius*random.nextDouble(.65,1.15);points.add(new V(Math.cos(a)*r,Math.sin(a)*r,random.nextDouble(-.15,.15)));}
        return List.copyOf(points);
    }
    public static List<V> jagged(V from,V to,int segments,double displacement,long seed){
        if(segments<2||segments>32)throw new IllegalArgumentException("Lightning segment limit");
        var random=new SplittableRandom(seed);V direction=to.sub(from).unit();
        V side=Math.abs(direction.y)>.9?new V(1,0,0):V.UP.cross(direction).unit();V other=direction.cross(side).unit();
        List<V> points=new ArrayList<>();points.add(from);
        for(int i=1;i<segments;i++){double t=i/(double)segments,envelope=Math.sin(Math.PI*t)*displacement;
            points.add(from.lerp(to,t).add(side.mul(random.nextDouble(-1,1)*envelope)).add(other.mul(random.nextDouble(-1,1)*envelope)));}
        points.add(to);return List.copyOf(points);
    }
}

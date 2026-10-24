package com.customrpg.vfx;

import com.customrpg.talents.FrostSkill;
import com.customrpg.vfx.VfxMath.V;
import com.customrpg.vfx.VfxRuntime.*;
import java.util.*;

/** Eight independent silhouettes and choreographies. Only crystal edge sampling and paints are shared. */
public final class FrostVfx {
    private FrostVfx() {}
    public static List<VfxEffects.Dna> designs(){return List.of(
        new VfxEffects.Dna("frost_shard","極冰穿心","單體穿刺","聚晶冰槍","尖銳冰槍貫穿","長錐體與菱形槍尾","六碎晶聚合、高速直進、定向碎裂","0–4聚合，5–10飛行，11–17碎裂","沿槍尖方向拉出長碎片","雙交錯霜痕","短促三叉戟破風","不是球形念彈或平面光束",192),
        new VfxEffects.Dna("frost_nova","寒棘追獵","直線地面攻擊","冰棘生長","參差冰牙逐段破土","不等高四稜尖錐與分叉裂紋","近至遠生成、早至晚崩碎","0–5裂地，6–13生長，14–23碎裂","每名敵人最多一次命中","稀疏裂紋枝線","低沉玻璃碎裂","地表向上；不使用環形爆發",256),
        new VfxEffects.Dna("frost_step","霜影瞬步","機動與逃生","寒霜殘影","兩道人形輪廓留在身後","雙人形骨架與平行鋸齒霜痕","平移拉伸、由上而下消散","0–2霜化，3–13殘影，霜痕保留40tick","無直接傷害；霜痕首次踏入減速","較淡下墜碎霜","短促滑步","輪廓為人形，沒有攻擊爆炸",224),
        new VfxEffects.Dna("frost_mirror","凝晶護鏡","防禦反擊","斜向懸浮晶鏡","一面護鏡碎裂反擊","三面獨立菱形鏡片","跟隨玩家、緩慢偏轉、首擊裂鏡","0–6生成，維持100tick，首次有效直接受擊反射","碎鏡後射出尖晶","內部斜向晶紋","共鳴與破風","不形成球形護罩",192),
        new VfxEffects.Dna("frost_lance","冰縛十字","單體控制","交叉晶鎖","四道晶片夾住下半身","分裂十字與雙X鎖片","四向向內收束","0–4鎖定，5–9收攏，10–26依凝霜延長","凝霜時鎖片亮起","稀疏晶點","玻璃封結短聲","主運動向內；不向外爆破",192),
        new VfxEffects.Dna("frost_blizzard","寒潮霧域","持續區域控制","低空寒流","四條高低交織的霧帶","三維Bezier流帶與鋸齒邊界","緩慢形變流動、末段拉淡","0–8生成，9–120流動，121–131消散","每40tick傷害；每目標每次施放只一層印記","低密度地面霜紋","壓低的雪地聲","沒有傳統圓圈漩渦主體",256),
        new VfxEffects.Dna("frost_prism","冰棱折光","彈射連鎖","旋轉折射晶體","八面體折出不規則光路","雙軸八面體與三段折線","邊線逐現、首次射出、間隔跳接","0–7聚晶，12首次，18及24後續命中","只沿實際成功命中鏈畫節點","短閃菱形折射點","逐跳提高音高的晶響","光束有晶體源頭且逐段跳躍",256),
        new VfxEffects.Dna("frost_crown","零度王權","大範圍爆發封鎖","六角冰晶王冠","天空王冠收束後十二方向崩裂","六枚立體尖晶與分叉六角地紋","聚合、停頓、塌縮、定向碎裂","0–12預警，13–30凝冠；30tick判傷，37–52碎裂，餘域80tick","一般敵人冰封；Boss只減速；不疊被動定身","向上漂浮細碎結晶","預警低鳴與高潮碎晶","上空至地面；預警與王冠優先保留",384)
    );}
    public static final Paint ICE=Paint.fade(0x83DEFF,0xEFFAFF,.7f), EDGE=Paint.dust(0xDCF8FF,.8f), FAINT=Paint.dust(0x699BB8,.5f);
    private static Paint nativePaint(ParticleKind kind){return new Paint(kind,0,0,1,V.ZERO,0);}
    private static final Paint SNOW=nativePaint(ParticleKind.SNOWFLAKE), ROD=nativePaint(ParticleKind.END_ROD), SHARD=nativePaint(ParticleKind.ICE_BLOCK), CLOUD=nativePaint(ParticleKind.CLOUD);
    public static final class Anchor { public V offset=V.ZERO; public boolean shattered; }
    private static V p(double x,double y,double z){return new V(x,y,z);}
    private static double ease(double t){return 1-Math.pow(1-Math.clamp(t,0,1),3);}
    private static void diamond(Canvas c,V center,V a,V b,Paint paint,Layer layer) {
        V[] v={center.add(a),center.add(b),center.sub(a),center.sub(b)};
        for(int i=0;i<4;i++)c.line(layer,paint,v[i],v[(i+1)%4],5);
    }
    private static void crystal(Canvas c,V base,V tip,double width,Layer layer) {
        for(int i=0;i<4;i++){double a=i*Math.PI/2;V foot=base.add(p(Math.cos(a)*width,0,Math.sin(a)*width));c.line(layer,EDGE,foot,tip,7);}
    }
    public static Effect preview(String id,long seed) {
        var skill=FrostSkill.of(id);if(skill==null)throw new IllegalArgumentException(id);
        return switch(skill){
            case PIERCER->new Piercer(14);case FANGS->new Fangs(defaultFangs(seed));case STEP->new PhantomStep(p(0,0,5));
            case MIRROR->new Mirror(new Anchor(),true);case CROSS->new Cross(new Anchor(),true);
            case MIST->new Mist(3.5);case PRISM->new Prism(new ArrayList<>(List.of(p(0,1,7),p(-3,1,9),p(1,1,12))));case CROWN->new Crown();
        };
    }
    public static List<V> defaultFangs(long seed){var r=new SplittableRandom(seed);var result=new ArrayList<V>();for(int i=1;i<=8;i++)result.add(p((r.nextDouble()-.5)*.2,0,i));return List.copyOf(result);}

    public static final class Piercer implements Effect {
        private final double distance;public Piercer(double distance){this.distance=Math.clamp(distance,1,14);}
        public int duration(){return 18;} public int sampleBudget(){return 192;}
        public void frame(int age,Canvas c){
            if(age<5){double q=1-ease(age/4.0);for(int k=0;k<6;k++){double a=k*Math.PI/3;V center=p(Math.cos(a)*q,Math.sin(a)*q,1);
                diamond(c,center,p(.08,0,.12),p(0,.1,0),ICE,Layer.OUTLINE);}}
            else if(age<=10){double z=1+(distance-1)*(age-4)/6.0;
                V tip=p(0,0,z),tail=p(0,0,z-1.7);
                for(int k=0;k<4;k++){double a=k*Math.PI/2;V side=p(Math.cos(a)*.19,Math.sin(a)*.19,z-1.1);c.line(Layer.OUTLINE,EDGE,tail,side,6);c.line(Layer.OUTLINE,EDGE,side,tip,9);}
                for(int k=0;k<2;k++)for(int i=0;i<14;i++){double t=i/13.0,a=t*7+age*.8+k*Math.PI;c.point(Layer.BODY,ICE,p(Math.cos(a)*.12,Math.sin(a)*.12,z-1.7-t*2));}
                c.point(Layer.DECORATION,ROD,tip);
                if(age==5)c.sound(Layer.OUTLINE,"entity.trident.throw",.65f,1.7f);
            }else{for(int k=0;k<7;k++){double a=k*2.4;V end=p(Math.cos(a)*.55,Math.sin(a)*.55,distance+.2);V direction=end.sub(p(0,0,distance-1)).mul((age-10)*.13);c.line(Layer.IMPACT,ICE,end.add(direction),end.add(direction).add(p(.07,.1,.5)),5);}}
        }
    }
    public static final class Fangs implements Effect {
        private final List<V> ground;public Fangs(List<V> ground){this.ground=List.copyOf(ground);}
        public int duration(){return 24;}public int sampleBudget(){return 256;}
        public void frame(int age,Canvas c){
            for(int i=0;i<ground.size();i++){V base=ground.get(i);int born=6+i;
                if(age<6&&i<=age+2){c.line(Layer.WARNING,FAINT,base,base.add(p((i%2==0?1:-1)*.6,.02,-.6)),5);if(i>0)c.line(Layer.WARNING,EDGE,ground.get(i-1),base,5);}
                else if(age>=born&&age<14+i){double grow=ease((age-born+1)/3.0),height=(1.2+(i%3)*.35)*grow;crystal(c,base,base.add(p((i%2==0?.35:-.3),height,.2)),.25,Layer.OUTLINE);}
                else if(age>=14+i&&age<20+i){if(age==14+i)c.point(Layer.DECORATION,SHARD,base.add(p(0,.8,0)));double d=(age-14-i)*.09;c.line(Layer.DECORATION,FAINT,base.add(p(d,.8-d,d)),base.add(p(d+.1,1.1-d,d+.2)),5);}
            }if(age==6)c.sound(Layer.OUTLINE,"block.glass.break",.8f,.6f);
        }
    }
    public static final class PhantomStep implements Effect {
        private final V end;public PhantomStep(V end){this.end=end;}
        public int duration(){return 44;}public int sampleBudget(){return 224;}
        public void frame(int age,Canvas c){
            if(age<14)for(int j=0;j<2;j++){V center=end.mul(j*.45);double stretch=age<3?1+age*.2:1;double drop=Math.max(0,age-8)*.1;
                c.line(Layer.OUTLINE,ICE,center.add(p(-.3,1.4-drop,0)),center.add(p(.3,1.4-drop,0)),6);
                c.line(Layer.BODY,FAINT,center.add(p(0,1.65-drop,0)),center.add(p(0,.6-drop,-.2*stretch)),8);
                for(int side:new int[]{-1,1}){c.line(Layer.OUTLINE,ICE,center.add(p(0,.7-drop,0)),center.add(p(side*.24,0,-.4*stretch)),7);c.line(Layer.OUTLINE,ICE,center.add(p(side*.3,1.3-drop,0)),center.add(p(side*.5,.85-drop,-.4)),6);}}
            if(age>=3)for(int side:new int[]{-1,1})for(int i=0;i<18;i++){double t=i/17.0;V at=end.mul(t).add(p(side*(.25+(i%2)*.1),.04,0));c.point(Layer.OUTLINE,age>35?FAINT:ICE,at);}
            if(age>8&&age<16)for(int k=0;k<4;k++)c.point(Layer.DECORATION,SNOW,p((k-1.5)*.3,1.5-(age-8)*.15,0));
            if(age==3)c.sound(Layer.OUTLINE,"entity.player.small_fall",.5f,1.8f);
        }
    }
    public static final class Mirror implements Effect {
        private final Anchor anchor;private final boolean demonstration;
        public Mirror(Anchor anchor){this(anchor,false);}public Mirror(Anchor anchor,boolean demonstration){this.anchor=anchor;this.demonstration=demonstration;}
        public V offset(){return anchor.offset;}public int duration(){return 100;}public int sampleBudget(){return 192;}
        public void frame(int age,Canvas c){
            if(demonstration&&age==40){anchor.shattered=true;c.sound(Layer.IMPACT,"block.glass.break",.5f,1.8f);}
            if(demonstration&&age>=40&&age<46){V tip=p(0,1.3,(age-39)*.8);c.line(Layer.IMPACT,EDGE,tip.add(p(-.12,.1,-.4)),tip,7);c.line(Layer.IMPACT,ICE,tip,tip.add(p(.1,-.1,-.5)),7);}
            double grow=ease(age/6.0);for(int k=0;k<3;k++){double a=k*2.1+age*.015;V center=p(Math.cos(a)*.8,1.35+Math.sin(a)*.2,Math.sin(a)*.6);
                if(anchor.shattered&&k==0){for(int j=0;j<3;j++)c.line(Layer.IMPACT,FAINT,center.add(p(j*.15,.2,0)),center.add(p(.2+j*.2,-.25,.25)),5);continue;}
                V axis=p(Math.sin(a)*.38,.58,Math.cos(a)*.2).mul(grow),side=p(Math.cos(a)*.29,0,-Math.sin(a)*.29).mul(grow);
                diamond(c,center,axis,side,EDGE,Layer.OUTLINE);c.line(Layer.BODY,ICE,center.sub(side.mul(.5)),center.add(axis.mul(.7)),5);
            }if(age==6)c.sound(Layer.OUTLINE,"block.amethyst_block.resonate",.6f,1.1f);
        }
    }
    /** The mirror's immediate fractured shard has no spear formation or full-sized lance silhouette. */
    public static final class MirrorShard implements Effect {
        private final double distance;public MirrorShard(double distance){this.distance=Math.clamp(distance,.5,24);}
        public int duration(){return 8;}public int sampleBudget(){return 64;}public double extent(){return 28;}
        public void frame(int age,Canvas c){
            double z=distance*Math.min(1,(age+1)/6.0);
            V tip=p(0,0,z);c.line(Layer.OUTLINE,EDGE,tip.add(p(-.13,.09,-.4)),tip,7);c.line(Layer.OUTLINE,ICE,tip,tip.add(p(.08,-.11,-.6)),7);
            c.point(Layer.DECORATION,SNOW,tip.add(p(0,0,-.7)));if(age==0)c.sound(Layer.IMPACT,"block.glass.break",.5f,1.8f);
        }
    }
    public static final class Cross implements Effect {
        private final Anchor anchor;private final boolean condensed;
        public Cross(Anchor anchor,boolean condensed){this.anchor=anchor;this.condensed=condensed;}
        public V offset(){return anchor.offset;}public int duration(){return condensed?26:17;}public int sampleBudget(){return 192;}
        public void frame(int age,Canvas c){
            if(age<5){for(int k=0;k<4;k++){double a=k*Math.PI/2;V outside=p(Math.cos(a)*1.4,.05,Math.sin(a)*1.4);c.line(Layer.OUTLINE,ICE,outside,outside.mul(1-ease(age/5.0)),8);}}
            else{double q=age<10?1-ease((age-5)/4.0):0;for(int side:new int[]{-1,1})for(int z:new int[]{-1,1}){
                V a=p(-.6-q,.15,z*.25),b=p(.6+q,1.15,z*.25);if(side<0){a=p(.6+q,.15,z*.25);b=p(-.6-q,1.15,z*.25);}c.line(condensed?Layer.IMPACT:Layer.OUTLINE,age>16?FAINT:EDGE,a,b,13);}}
            if(age==8)c.point(Layer.DECORATION,ROD,p(0,.8,0));
            if(age==8)c.sound(Layer.IMPACT,"block.glass.place",.65f,.7f);
        }
    }
    public static final class Mist implements Effect {
        private final double radius;public Mist(double radius){this.radius=radius;}
        public int duration(){return 132;}public int sampleBudget(){return 256;}
        public void frame(int age,Canvas c){
            double formation=ease(age/9.0),fade=age>120?(132-age)/12.0:1;
            for(int band=0;band<4;band++)for(int i=0;i<25;i++){double t=i/24.0;V a=p(-radius,0,(band-1.5)*1.35),d=p(radius,.25,(1.5-band)*1.15);
                V b=p(-1,1.2+band*.15,Math.sin(age*.04+band)*2),e=p(1,.1,Math.cos(age*.03+band)*2);
                V at=VfxMath.bezier(a,b,e,d,t).mul(formation).add(p(0,Math.sin(t*9+age*.04)*.12,0));
                if(at.x()*at.x()+at.z()*at.z()<=radius*radius)c.point(Layer.BODY,age>120?FAINT:ICE,at.scale(p(1,fade,1)));}
            for(int i=0;i<18;i++){double a=i*Math.PI*2/18,r=radius*(i%2==0?1:.88);c.point(Layer.WARNING,FAINT,p(Math.cos(a)*r,.03,Math.sin(a)*r));}
            if(age==8)c.point(Layer.DECORATION,ROD,p(0,.8,0));
            if(age%4==0)for(int k=0;k<2;k++)c.point(Layer.DECORATION,CLOUD,p(Math.sin(age*.1+k)*2,.25,Math.cos(age*.12+k)*2));
            if(age%6==0)for(int k=0;k<4;k++)c.point(Layer.DECORATION,SNOW,p(Math.sin(k*2.4)*2,.7,Math.cos(k*2.4)*2));
            if(age==8)c.sound(Layer.BODY,"block.powder_snow.step",.5f,.5f);
        }
    }
    public static final class Prism implements Effect {
        private final List<V> nodes;public Prism(List<V> nodes){this.nodes=nodes;}
        public int duration(){return 29;}public int sampleBudget(){return 256;}
        public double extent(){return 24;}
        public void frame(int age,Canvas c){
            V center=p(0,1.2,1.1);double r=ease(age/7.0)*.45;List<V> corners=new ArrayList<>();
            for(V v:List.of(p(r,0,0),p(0,0,r),p(-r,0,0),p(0,0,-r),p(0,r*1.6,0),p(0,-r*1.6,0))) corners.add(VfxMath.rotate(VfxMath.rotate(v,V.UP,age*.07),p(1,0,0),age*.035).add(center));
            if(age<=12)for(int k=0;k<4;k++){c.line(Layer.OUTLINE,EDGE,corners.get(k),corners.get((k+1)%4),5);c.line(Layer.OUTLINE,EDGE,corners.get(k),corners.get(4),5);c.line(Layer.OUTLINE,EDGE,corners.get(k),corners.get(5),5);}
            for(int hop=0;hop<Math.min(3,nodes.size());hop++){int hit=12+hop*6;if(age>=hit&&age<hit+4){V from=hop==0?center:nodes.get(hop-1),to=nodes.get(hop);c.line(Layer.IMPACT,EDGE,from,to,22);diamond(c,to,p(.2,0,0),p(0,.3,0),ICE,Layer.IMPACT);if(age==hit)c.sound(Layer.IMPACT,"block.amethyst_block.hit",.6f,1.2f+hop*.25f);}}
        }
    }
    public static final class Crown implements Effect {
        public int duration(){return 132;}public int sampleBudget(){return 384;}
        public void frame(int age,Canvas c){
            // These six branching spokes are the real radius-six warning; they survive decorative LOD.
            if(age<=110&&(age<=52||age%4==0))for(int arm=0;arm<6;arm++){double a=arm*Math.PI/3;V direction=p(Math.cos(a),0,Math.sin(a));double length=age<13?6*ease((age+1)/13.0):6;
                c.line(Layer.WARNING,ICE,p(0,.04,0),direction.mul(length).add(p(0,.04,0)),7);
                for(int fork=0;fork<2;fork++){V root=direction.mul(2.5+fork*1.4).add(p(0,.05,0));for(int side:new int[]{-1,1})c.line(Layer.DECORATION,FAINT,root,root.add(VfxMath.rotate(direction,V.UP,side*.8).mul(.8)),3);}}
            if(age>=13&&age<=36){double q=ease((age-13)/17.0),collapse=age<31?0:ease((age-31)/5.0);
                for(int k=0;k<6;k++){double a=k*Math.PI/3+(age<30?(30-age)*.02:0),r=(4.5-2*q)*(1-collapse*.75);V center=p(Math.cos(a)*r,5-collapse*3,Math.sin(a)*r);
                    V tip=center.add(p(Math.cos(a)*.3,1.5*(1-collapse*.6),Math.sin(a)*.3));crystal(c,center,tip,.5,Layer.BODY);
                    c.line(Layer.OUTLINE,EDGE,center.add(p(-.4,0,0)),tip,5);c.line(Layer.OUTLINE,EDGE,tip,center.add(p(.4,0,0)),5);
                    V next=p(Math.cos(a+Math.PI/3)*r,5-collapse*3,Math.sin(a+Math.PI/3)*r);c.line(Layer.OUTLINE,EDGE,center,next,3);}
            }
            if(age>=37&&age<=52)for(int k=0;k<12;k++){double a=k*Math.PI/6,t=(age-36)/16.0;V from=p(Math.cos(a)*1.3,2,Math.sin(a)*1.3),to=p(Math.cos(a)*7,Math.sin(k*2.4)*.5+1,Math.sin(a)*7);
                V tip=VfxMath.bezier(from,from.add(p(-.5,2,.3)),to.add(p(.5,-1,-.4)),to,t);c.line(Layer.IMPACT,EDGE,tip,tip.add(p(Math.cos(a)*.65,.4,Math.sin(a)*.65)),7);}
            if(age<13)for(int k=0;k<6;k++){double a=k*Math.PI/3,r=6-age*.2;c.point(Layer.DECORATION,SNOW,p(Math.cos(a)*r,.4,Math.sin(a)*r));}
            if(age>36&&age<53)for(int k=0;k<3;k++)c.point(Layer.DECORATION,SHARD,p(Math.sin(k*2.4)*(age-36)*.2,1,Math.cos(k*2.4)*(age-36)*.2));
            if(age==30)c.sound(Layer.WARNING,"block.beacon.deactivate",.85f,.55f);
            if(age==30)c.sound(Layer.IMPACT,"block.glass.break",1f,.55f);
            if(age>52)for(int k=0;k<12;k++){double a=k*2.4,r=1+k%5;c.point(Layer.DECORATION,FAINT,p(Math.cos(a)*r,(age-52)*.009,Math.sin(a)*r));}
        }
    }
}

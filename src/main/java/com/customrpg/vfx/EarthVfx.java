package com.customrpg.vfx;

import com.customrpg.talents.EarthSkill;
import com.customrpg.vfx.VfxMath.V;
import com.customrpg.vfx.VfxRuntime.*;
import java.util.*;

/** Independent Earth storyboards. None of these structures edits terrain or has collision. */
public final class EarthVfx {
    private EarthVfx(){}
    public static List<VfxEffects.Dna> designs(){return List.of(
        new VfxEffects.Dna("earth_shockwave","斷岩重擊","前線扇形打擊","方向性斷層","不對稱V形斷岩同時翻起碎石","三條地裂及拋物線岩片","手臂聚岩後向前開裂、碎片拋落","0–4聚力，5–8裂地，9–15拋落","沿真實扇面單次傷害與短擊退","少量岩屑","沉重石擊及碎石下墜","沒有環形地震、冰棘或霜槍",224),
        new VfxEffects.Dna("earth_guard","山紋壁盾","方向性承傷","三面活動山壁","正面岩板局部後震、金色山紋亮起","三片不規則厚岩板","垂直升起、跟隨面向、局部承擊震動","0–6升起，7–80守護，有效受擊短震","正面承擊才抖動最近岩板","細金地脈","低石摩擦與短盾擊","主體是活動正面多邊形牆，不是懸浮晶鏡",288),
        new VfxEffects.Dna("earth_grasp","地脈牽引","聚怪控位","地底岩根","多條粗糙根脈同步縮回三角核心","不規則三角核心與高低折線根","先延伸至真實敵人再向中心收束","0–5核心，6–13伸根，14–20收束","Boss留在原位，其他敵人安全牽引","斷裂岩根末端","由遠而近的石裂聲","朝地面中心拉動，沒有冰縛交叉夾片",256),
        new VfxEffects.Dna("earth_bastion","沉岩陷域","持續地面壓制","錯層沉岩","不同板塊交錯下沉後從高處重組","不等高多邊形拼接地板","各板塊不同相位下沉與重組","0–8龜裂，9–100下沉，101–109平息","首次進入及相隔40tick的真實傷害","縫隙低密度粉塵","斷續的低沉砂礫","垂直錯層運動，沒有寒霧曲帶或旋轉環",256),
        new VfxEffects.Dna("earth_oath","守護岩契","隊友承傷","雙人契約地脈","受擊能量沿岩契回傳守護者肩部","雙端三角菱形符記與分節石鏈","兩端連接、脈衝由盟友返回自己","0–8建立，維持120tick，實際轉移時回流","只在實際承受轉移傷害時傳導","暗金流動小片","雙端契約低鳴","不是恆亮雷射；跨兩人且受真實轉移驅動",256),
        new VfxEffects.Dna("earth_rush","震步援護","機動支援護盾","踏岩援護","連續岩踏板崩裂後盟友岩甲合攏","傾斜矩形踏板與肩胸菱形甲","沿安全路徑分段踏出、到達合甲","0–3踏地，4–10踏板，11–24組甲","實際到達才發給盟友吸收盾","向後散落小岩片","短石踏及合甲鈍響","不是人形殘影或直線粒子拖尾",256),
        new VfxEffects.Dna("earth_reprisal","岩心反震","首擊防禦反制","壓縮岩心","胸前六面岩心破裂為單向楔形反震","緊縮六面體與定向厚楔","低頻呼吸、受擊瞬間單側破裂","0–6凝心，等待100tick，首擊後10tick楔爆","僅首個有效敵方直接攻擊消耗反制","極少細小石屑","壓低脈動與重岩破裂","爆發向攻擊者；沒有完整圓圈擴散",256),
        new VfxEffects.Dna("earth_titan","泰坦鎮界・萬岳降臨","團體防禦鎮壓","四岳守護要塞","四座切角方碑同亮、山岳符文收束","四座不等高立體方碑與山岳地紋","四向裂地、逐段拔起、鎮壓、沉穩維持","0–12預警，13–25升碑，25tick判傷，守護160tick，186–200消散","真實敵人傷害擊退、隊友受擊使鄰碑短震","稀疏金色碑紋","地脈低鳴、四碑重擊與靜態守護","四角岩石要塞，與上空六角冰冠完全不同",384)
    );}
    public static final Paint ROCK=Paint.dust(0x8E795B,.75f),EDGE=Paint.dust(0xD1B786,.8f),GOLD=Paint.fade(0xF4CC65,0x805B28,.65f),DARK=Paint.dust(0x60523F,.55f);
    private static final Paint DEBRIS=new Paint(ParticleKind.STONE_BLOCK,0,0,1,V.ZERO,0),CLOUD=new Paint(ParticleKind.CLOUD,0,0,1,V.ZERO,0);
    public static final class Anchor {public V offset=V.ZERO,end=new V(0,0,5);public double yaw,strength=.5;public V impact=V.ZERO;public int pulse,stacks;public boolean triggered;}
    private static V p(double x,double y,double z){return new V(x,y,z);}
    private static double ease(double q){return 1-Math.pow(1-Math.clamp(q,0,1),3);}
    private static V turn(V at,double yaw){return p(at.x()*Math.cos(yaw)+at.z()*Math.sin(yaw),at.y(),at.z()*Math.cos(yaw)-at.x()*Math.sin(yaw));}
    private static void polygon(Canvas c,List<V> points,Paint paint,Layer layer,int samples){for(int i=0;i<points.size();i++)c.line(layer,paint,points.get(i),points.get((i+1)%points.size()),samples);}
    private static void mark(Canvas c,V center,double radius,Layer layer){polygon(c,List.of(center.add(p(0,.03,radius)),center.add(p(-radius,.03,-radius*.6)),center.add(p(radius,.03,-radius*.6))),GOLD,layer,4);}
    private static void plate(Canvas c,V center,double size,double yaw,Layer layer){
        var shape=List.of(p(-.45,-.65,0),p(.35,-.7,.02),p(.55,.3,0),p(.2,.7,.03),p(-.4,.55,0));
        polygon(c,shape.stream().map(v->center.add(turn(v.mul(size),yaw))).toList(),EDGE,layer,5);
        c.line(Layer.BODY,ROCK,center.add(turn(p(-.3,-.5,.16).mul(size),yaw)),center.add(turn(p(.3,.5,.16).mul(size),yaw)),5);
    }
    public static Effect preview(String id,long seed){var s=EarthSkill.of(id);if(s==null)throw new IllegalArgumentException(id);return switch(s){
        case STRIKE->new Strike(seed);case AEGIS->new Aegis(new Anchor(),true);case GRASP->new Grasp(List.of(p(-2,0,1),p(2,0,-1),p(0,0,2.5)));
        case SINK->new Sinking(seed);case OATH->new Oath(new Anchor(),true);case RUSH->new Rush(p(0,0,6),true);case REPRISAL->new Reprisal(new Anchor(),true);case TITAN->new Titan(new Anchor());};}
    public static final class Strike implements Effect {
        private final List<V> fragments=new ArrayList<>();public Strike(long seed){var r=new SplittableRandom(seed);for(int i=0;i<5;i++)fragments.add(p(r.nextDouble(-1,1),r.nextDouble(.7,1.4),r.nextDouble(1.5,3.5)));}
        public int duration(){return 16;}public int sampleBudget(){return 224;}
        public void frame(int age,Canvas c){
            if(age<5)for(int i=0;i<4;i++)c.line(Layer.BODY,ROCK,p(.4+i*.08,.1+i*.2,.5),p(.25,1.1+age*.04,.8),4);
            if(age>=5){double growth=ease((age-4)/4.0);for(int side=-1;side<=1;side++)for(int j=0;j<4;j++){
                V a=p(side*j*.36,.04,j*.9),b=p(side*(j+1)*.36+.08*Math.sin(j*7),.05,(j+1)*.9);c.line(Layer.OUTLINE,EDGE,a.mul(growth),b.mul(growth),5);}}
            if(age>=9)for(int i=0;i<fragments.size();i++){V d=fragments.get(i);double q=(age-8)/7.0;V at=p(d.x()+q*.3,d.y()*4*q*(1-q),d.z()+q*.8);plate(c,at,.15+i*.015,q*3,Layer.BODY);}
            if(age==5)c.sound(Layer.IMPACT,"block.anvil.land",.45f,.65f);
        }
    }
    public static final class Aegis implements Effect {
        private final Anchor anchor;private final boolean demo;public Aegis(Anchor a,boolean demo){anchor=a;this.demo=demo;}
        public int duration(){return 80;}public int sampleBudget(){return 288;}public V offset(){return anchor.offset;}
        public void frame(int age,Canvas c){boolean hit=anchor.pulse>0||(demo&&age>=35&&age<40);double rise=ease(age/6.0);
            for(int i=0;i<3;i++){V center=turn(p((i-1)*.75,.95*rise+Math.sin(age*.08+i)*.04,1.2-(hit&&i==1?.08+.14*anchor.strength:0)),anchor.yaw);plate(c,center,rise,anchor.yaw+(i-1)*-.2,Layer.OUTLINE);c.line(Layer.BODY,GOLD,center.add(p(0,-.4,0)),center.add(p(.1,.4,0)),4);}
            if(hit)c.point(Layer.IMPACT,DEBRIS,turn(p(0,1.1,1.3),anchor.yaw));if(age==6)c.sound(Layer.OUTLINE,"block.stone.place",.75f,.6f);
        }
    }
    public static final class Grasp implements Effect {
        private final List<V> targets;public Grasp(List<V> targets){this.targets=List.copyOf(targets).stream().limit(8).toList();}
        public int duration(){return 24;}public int sampleBudget(){return 256;}
        public void frame(int age,Canvas c){mark(c,V.ZERO,.5,Layer.OUTLINE);int index=0;for(V to:targets){double q=age<14?ease((age-5)/8.0):1-ease((age-14)/7.0);V last=V.ZERO;
            for(int j=1;j<=5;j++){double t=j/5.0;V at=to.mul(t*q).add(p(Math.sin(j*9+index)*.13,Math.sin(j*2.1)*.2+.15,0));c.line(Layer.OUTLINE,ROCK,last,at,4);last=at;}index++;}
            if(age==14)c.sound(Layer.IMPACT,"block.rooted_dirt.break",.7f,.65f);
        }
    }
    public static final class Sinking implements Effect {
        private final List<List<V>> plates=new ArrayList<>();public Sinking(long seed){var r=new SplittableRandom(seed);for(int i=0;i<7;i++){double angle=i*2.4,rad=i==0?0:1.8;V center=p(Math.cos(angle)*rad,0,Math.sin(angle)*rad);var poly=new ArrayList<V>();for(int j=0;j<5;j++){double a=j*Math.PI*2/5;poly.add(center.add(p(Math.cos(a)*r.nextDouble(.6,1),0,Math.sin(a)*r.nextDouble(.6,1))));}plates.add(poly);}}
        public int duration(){return 110;}public int sampleBudget(){return 256;}
        public void frame(int age,Canvas c){for(int i=0;i<plates.size();i++){double phase=(age*.012+i*.17)%1;double drop=age<9?0:age>100?0:phase*.45;
            V offset=p(0,.12-drop,0);polygon(c,plates.get(i).stream().map(v->v.add(offset)).toList(),age>100?DARK:EDGE,Layer.OUTLINE,4);
            if(age%8==i)c.point(Layer.DECORATION,CLOUD,plates.get(i).get(0).add(p(0,.1,0)));}if(age==8)c.sound(Layer.BODY,"block.gravel.break",.6f,.5f);}
    }
    public static final class Oath implements Effect {
        private final Anchor anchor;private final boolean demo;public Oath(Anchor a,boolean demo){anchor=a;this.demo=demo;}
        public int duration(){return 120;}public int sampleBudget(){return 256;}public V offset(){return anchor.offset;}public double extent(){return 12;}
        public void frame(int age,Canvas c){V end=anchor.end;mark(c,V.ZERO,.55,Layer.OUTLINE);mark(c,end,.55,Layer.OUTLINE);
            double growth=ease(age/8.0);for(int j=0;j<10;j++){double t=j/10.0;V at=end.mul(t*growth).add(p(Math.sin(j*3)*.12,.08,0));c.line(Layer.BODY,ROCK,at,at.add(end.mul(.05)),3);}
            boolean pulse=anchor.pulse>0||demo&&age>=40&&age<50;if(pulse){double t=demo?1-(age-40)/10.0:Math.clamp(anchor.pulse/10.0,0,1);V at=end.mul(t).add(p(0,.35,0));plate(c,at,.25,0,Layer.IMPACT);}
            if(age==8)c.sound(Layer.OUTLINE,"block.lodestone.place",.6f,.8f);
        }
    }
    public static final class Rush implements Effect {
        private final V end;private final boolean arrived;public Rush(V end,boolean arrived){this.end=end;this.arrived=arrived;}
        public int duration(){return 25;}public int sampleBudget(){return 256;}
        public void frame(int age,Canvas c){for(int i=0;i<7;i++){int born=3+i;if(age<born||age>born+8)continue;V at=end.mul(i/7.0);double q=(age-born)/8.0;
            polygon(c,List.of(at.add(p(-.3,.1-q*.15,-.2)),at.add(p(.3,.15-q*.15,-.2)),at.add(p(.3,.35-q*.2,.2)),at.add(p(-.3,.3-q*.2,.2))),EDGE,Layer.OUTLINE,4);}
            if(arrived&&age>=11){double q=1-ease((age-11)/8.0);for(int i=0;i<3;i++)plate(c,end.add(p((i-1)*(.3+q),1.1+(i==1?.2:0),0)),.3,0,Layer.IMPACT);}
            if(age==4)c.sound(Layer.OUTLINE,"block.stone.step",.7f,.65f);
        }
    }
    public static final class Reprisal implements Effect {
        private final Anchor anchor;private final boolean demo;private int burst=-1;public Reprisal(Anchor a,boolean demo){anchor=a;this.demo=demo;}
        public int duration(){return 110;}public int sampleBudget(){return 256;}public V offset(){return anchor.offset;}
        public void frame(int age,Canvas c){if(burst<0&&(anchor.triggered||demo&&age==40))burst=age;
            if(burst<0&&age<100){double r=(.2+.025*Math.sin(age*.25))*ease(age/6.0);V center=p(0,1.15,.45);for(int i=0;i<4;i++){double a=i*Math.PI/2;V at=center.add(p(Math.cos(a)*r,0,Math.sin(a)*r));c.line(Layer.OUTLINE,GOLD,center.add(p(0,r,0)),at,4);c.line(Layer.OUTLINE,ROCK,at,center.add(p(0,-r,0)),4);}}
            else if(burst>=0&&age-burst<10){double q=(age-burst+1)/10.0;V dir=anchor.end.sub(p(0,1,0)).unit();for(int i=-1;i<=1;i++){V tip=dir.mul(.4+q*4).add(p(i*.5,1,0)),base=tip.sub(dir.mul(.9));polygon(c,List.of(tip,base.add(p(-.3,.35,0)),base.add(p(.3,-.25,0))),EDGE,Layer.IMPACT,6);}if(age==burst)c.sound(Layer.IMPACT,"block.deepslate.break",.9f,.6f);}
        }
    }
    public static final class Titan implements Effect {
        private final Anchor anchor;public Titan(Anchor a){anchor=a;}public int duration(){return 200;}public int sampleBudget(){return 384;}
        public void frame(int age,Canvas c){
            // Sparse warning deliberately leaves LOW budget for all four tall monument silhouettes.
            if(age<185)for(int i=0;i<4;i++){double a=i*Math.PI/2;V end=p(Math.cos(a)*7,.04,Math.sin(a)*7);c.line(Layer.WARNING,GOLD,V.ZERO,end.mul(ease((age+1)/12.0)),5);V side=p(-Math.sin(a)*.45,0,Math.cos(a)*.45);c.line(Layer.WARNING,EDGE,end.sub(side),end.add(side),3);}
            if(age>=13){double rise=ease((age-12)/13.0),fade=age>185?1-(age-185)/15.0:1;for(int i=0;i<4;i++){
                double a=i*Math.PI/2;V base=p(Math.cos(a)*6,0,Math.sin(a)*6);double h=(4.2+(i%2)*.6)*rise*fade;
                V[] feet={base.add(p(-.55,0,-.45)),base.add(p(.55,0,-.45)),base.add(p(.45,0,.45)),base.add(p(-.5,0,.45))};
                V[] tops={feet[0].add(p(.12,h,0)),feet[1].add(p(-.08,h*.86,.04)),feet[2].add(p(0,h*.95,-.1)),feet[3].add(p(.06,h*.79,0))};
                for(int j=0;j<4;j++){c.line(Layer.OUTLINE,EDGE,feet[j],tops[j],3);c.line(Layer.OUTLINE,ROCK,tops[j],tops[(j+1)%4],3);}
                c.line(Layer.BODY,GOLD,base.add(p(0,.2,0)),base.add(p(.15,h-.1,0)),5);
                if(anchor.pulse>0&&base.sub(anchor.impact).length()<7)c.point(Layer.IMPACT,DEBRIS,base.add(p(0,h*.5,0)));
            }}
            if(age>=25&&age<185){double contraction=age<34?1-(age-25)*.04:1;polygon(c,List.of(p(-2,.06,0),p(-.5,.06,2),p(0,.06,.7),p(.8,.06,2),p(2,.06,0)),age<34?GOLD:DARK,Layer.OUTLINE,4);if(age<34)c.line(Layer.IMPACT,GOLD,p(-2*contraction,.08,0),p(2*contraction,.08,0),10);}
            if(age==25)c.sound(Layer.IMPACT,"entity.iron_golem.repair",1,.5f);
        }
    }
    public static final class Resilience implements Effect {
        private final Anchor anchor;public Resilience(Anchor a){anchor=a;}public int duration(){return 160;}public int sampleBudget(){return 96;}public V offset(){return anchor.offset;}
        public void frame(int age,Canvas c){int count=Math.clamp(anchor.stacks,0,3);for(int i=0;i<count;i++){double angle=i*Math.PI*2/3+age*.015;plate(c,p(Math.cos(angle)*.55,1.25+Math.sin(age*.08+i)*.05,Math.sin(angle)*.4),.2,angle,Layer.BODY);}}
    }
    public static final class Fortify implements Effect {
        public int duration(){return 12;}public int sampleBudget(){return 96;}
        public void frame(int age,Canvas c){double q=1-ease(age/10.0);for(int i=0;i<3;i++)plate(c,p((i-1)*(.3+q*.7),1.1+(i==1?.2:0),q*.4),.25,0,Layer.IMPACT);if(age==1)c.sound(Layer.IMPACT,"block.gilded_blackstone.step",.7f,.8f);}
    }
}

package com.customrpg.vfx;

import com.customrpg.talents.NecroSkill;
import com.customrpg.vfx.VfxMath.V;
import com.customrpg.vfx.VfxRuntime.*;
import java.util.*;

/** Anatomical strokes are shared; each ability owns its silhouette and choreography. No entities or combat. */
public final class NecroVfx {
    private NecroVfx(){}
    public static List<VfxEffects.Dna> designs(){return List.of(
        new VfxEffects.Dna("necro_lance","骨脊裂矛","兩目標穿刺","喚魔者尖牙","瞄準路線地面依序張合七對原版尖牙","成列的雙瓣尖牙夾顎","從近到遠破土張口後閉合","6至12tick依序甦醒，原版顎部收合後消失","真命中仍由原穿刺判定，尖牙原版傷害取消","命中時少量碎骨","原版喚魔者尖牙聲","主體改用原版EvokerFangs，不再飛行脊矛",224),
        new VfxEffects.Dna("necro_summon","墓土喚骸","建立兩名可受傷僕從","墓穴甦醒","左右墓穴先伸手再爬出骨架","不對稱墓裂與出土手掌","手掌前臂肩膀頭骨逐段上升","0至8裂土，9至16出土，17至24集結","真實僕從攻擊後短骨斬","零星幽綠魂火","泥土低音與骨架卡合","不是暗黑系漂浮雙爪影獸",288),
        new VfxEffects.Dna("necro_command","喪鐘號令","集火與兩次強化攻擊","亡鐘指揮","鐘體傾斜敲響三道半月聲波","古鐘與垂直鐘槌","鐘槌擺動及斷續弧波向指定目標","0至5浮鐘，6至9鐘響，10至15骷髏標記","指定敵人與僕從魂眼呼應","少量碎骨紋","一次低沉鐘聲","主角是可辨識鐘體而非連鎖光束",256),
        new VfxEffects.Dna("necro_prison","骨棺禁錮","單體定身","胸腔牢籠","四組巨肋向內閉合","弧形肋骨胸腔與交叉脊鎖","由地表向上生長然後向內合攏","0至5定位，6至12長肋，24解除後碎落","一般敵人定身，Boss僅減速","少量下落骨粉","骨頭拉伸與斷裂聲","不同於冰縛十字的平面X鎖片",288),
        new VfxEffects.Dna("necro_lantern","冥燈庇魂","自身吸收盾及僕從防護","提燈與魂線","六角燈框中的魂火連接僕從","肩側六角框架與懸掛提把","火焰不規則晃動與柔曲魂線","0至7燈框，8至14連線，100後熄燈","僕從真正受擊才亮起魂線","兩片骨肩甲","微弱魂火及熄滅聲","非環繞護鏡或三面暗晶板",288),
        new VfxEffects.Dna("necro_sacrifice","亡骸祭爆","消耗普通僕從的範圍爆發","失控骨刺增生","骨架增生不等長刺束後沿直線崩裂","非均勻白骨刺球","先骨刺伸長再長刺平射碎骨拋落","0至5異變，6至10增生，11傷害，14至22落骨","實際位置半徑3且每敵一次","灰白骨粉","骨頭折斷後短促衝擊","不是黑煙或均勻球形爆炸",288),
        new VfxEffects.Dna("necro_colossus","骸骨縫合・葬骨巨衛","兩僕融合及一次承傷","禁忌縫骨","兩具骨架被魂線縫合成持盾巨衛","大型胸腔雙腿骨盾及骨槌","左右拆解向心組裝最後盾面前擋","0至6拆骨，7至14縫合，15至22成形，8秒守衛","實際承傷才繪盾面裂痕","殘留綠色縫線","沉重骨踏與盾裂","與墓土出土相反，從空中骨片重組",384),
        new VfxEffects.Dna("necro_gate","冥府開門・百骸行軍","三波矩形鎮壓及兩精銳","白骨拱門與亡軍","高七格骨門後軍隊列陣三波前衝","扭曲骨柱肋拱與多排持劍骨兵","門框向上拼接，軍隊逐排向前，門由上崩解","30鎖方向，43及53及63傷害，71精銳，211至225閉門","每波重新判定牆壁與敵人位置","疏落墓手與魂火","亡者號角及三波骨踏","矩形軍勢而非黯日塌縮或冰冠爆發",384)
    );}
    public static final Paint BONE=Paint.dust(0xDED6BD,.65f),EDGE=Paint.dust(0xFFF3D4,.65f),SOUL=Paint.fade(0x6FB59C,0x29453F,.55f),ASH=Paint.dust(0x68665E,.45f);
    public static final class Anchor {public V offset=V.ZERO,direction=null,end=new V(0,1,7);public List<V> companions=List.of();public int pulse;public boolean ending;}
    private static V p(double x,double y,double z){return new V(x,y,z);}
    private static double ease(double q){return 1-Math.pow(1-Math.clamp(q,0,1),3);}
    private static void bone(Canvas c,V a,V b,Layer layer){c.line(layer,BONE,a,b,4);V bump=p(.055,.025,0);c.line(layer,EDGE,a.sub(bump),a.add(bump),2);}
    private static void rib(Canvas c,V base,double side,double height,double growth,Layer layer){for(int i=1;i<=7;i++){double t=i/7.0;if(t>growth)break;c.line(layer,BONE,base.add(VfxMath.bezier(V.ZERO,p(side*.8,height*.45,-.2),p(side*.7,height,.2),p(side*.08,height,.1),(i-1)/7.0)),base.add(VfxMath.bezier(V.ZERO,p(side*.8,height*.45,-.2),p(side*.7,height,.2),p(side*.08,height,.1),t)),2);}}
    private static void skull(Canvas c,V at,double size,Layer layer){V[] n={p(-.18,.1,0),p(-.23,.38,0),p(0,.5,.03),p(.23,.38,0),p(.18,.1,0),p(.1,0,0),p(-.1,0,0),p(-.18,.1,0)};for(int i=1;i<n.length;i++)c.line(layer,BONE,at.add(n[i-1].mul(size)),at.add(n[i].mul(size)),2);for(int side:new int[]{-1,1})c.point(layer,SOUL,at.add(p(side*.09,.28,.03).mul(size)));}
    private static void soldier(Canvas c,V at,double size,double growth,double pose,Layer layer){
        if(growth>.15){bone(c,at,at.add(p(-.22,.75,0).mul(size)),layer);bone(c,at.add(p(.35,0,.1).mul(size)),at.add(p(-.22,.75,0).mul(size)),layer);}
        if(growth>.45){bone(c,at.add(p(-.22,.75,0).mul(size)),at.add(p(0,1.4,0).mul(size)),layer);bone(c,at.add(p(-.4,1.35,0).mul(size)),at.add(p(.4,1.35,0).mul(size)),layer);bone(c,at.add(p(.4,1.35,0).mul(size)),at.add(p(.6,.8,pose).mul(size)),layer);bone(c,at.add(p(-.4,1.35,0).mul(size)),at.add(p(-.6,.9,pose).mul(size)),layer);}
        if(growth>.7){skull(c,at.add(p(0,1.45,0).mul(size)),size,layer);for(int j=0;j<2;j++)bone(c,at.add(p(-.22,1+j*.16,0).mul(size)),at.add(p(.22,1+j*.16,.03).mul(size)),layer);}
    }
    private static void hand(Canvas c,V at,double growth){bone(c,at,at.add(p(0,.5*growth,0)),Layer.OUTLINE);for(int j=0;j<4;j++)bone(c,at.add(p((j-1.5)*.055,.5*growth,0)),at.add(p((j-1.5)*.09,(.7+(j%2)*.1)*growth,.06)),Layer.BODY);}
    public static Effect preview(String id,long seed){var s=NecroSkill.of(id);if(s==null)throw new IllegalArgumentException(id);var a=new Anchor();a.companions=List.of(p(-1,0,.5),p(1,0,.5));return switch(s){case LANCE->new Lance(14);case SUMMON->new Summoning();case COMMAND->new Command(a);case PRISON->new Prison();case LANTERN->new Lantern(a,true);case SACRIFICE->new Sacrifice(seed);case COLOSSUS->new Fusion();case GATE->new Gate(a);};}
    /** Pure jaw geometry for headless checks; the Bukkit adapter renders native EvokerFangs instead. */
    public static final class Lance implements Effect {
        private final double range;public Lance(double range){this.range=range;}public double range(){return range;}public int duration(){return 48;}public int sampleBudget(){return 224;}
        public void frame(int age,Canvas c){for(int j=0;j<7;j++){double z=j*2+1;if(z>range)break;int t=age-6-j;if(t<0||t>20)continue;double open=Math.sin(Math.PI*Math.clamp(t/16.0,0,1));for(int side:new int[]{-1,1}){V base=p(side*.45,-1.48,z),hinge=p(side*.2,-1.45,z-.3),tip=p(side*.3*(1-open),-1.45+open*.95,z+.08);c.line(Layer.OUTLINE,BONE,base,hinge,3);c.line(Layer.OUTLINE,BONE,hinge,tip,3);c.line(Layer.OUTLINE,BONE,tip,base,3);}if(t==0)c.sound(Layer.OUTLINE,"entity.evoker_fangs.attack",.45f,1);}}
    }
    public static final class Summoning implements Effect {
        public int duration(){return 25;}public int sampleBudget(){return 288;}
        public void frame(int age,Canvas c){for(int side:new int[]{-1,1}){V at=p(side*1.2,0,.4);for(int j=0;j<3;j++){V root=at.add(p((j-1)*.15,.03,0));c.line(Layer.OUTLINE,age<9?ASH:SOUL,root,root.add(p(side*.5,.01,.25+j*.1)),4);}int t=age-(side>0?3:0);if(t>=8&&t<18)hand(c,at.add(p(-.25,0,.15)),ease((t-7)/5.0));if(t>=10)soldier(c,at.add(p(0,-(1-ease((t-9)/7.0)),0)),1,ease((t-9)/7.0),.2,Layer.OUTLINE);}if(age==8)c.sound(Layer.OUTLINE,"block.rooted_dirt.break",.6f,.7f);}
    }
    public static final class Command implements Effect {
        private final Anchor a;public Command(Anchor a){this.a=a;}public int duration(){return 19;}public int sampleBudget(){return 256;}
        public void frame(int age,Canvas c){double swing=age<6?0:Math.sin((age-5)*.65)*.2;for(int side:new int[]{-1,1}){bone(c,p(side*.35+swing,2.8,-.5),p(side*.6+swing,2,-.5),Layer.OUTLINE);bone(c,p(side*.6+swing,2,-.5),p(swing,1.95,-.5),Layer.OUTLINE);}bone(c,p(swing,2.65,-.5),p(swing+Math.sin(age*.6)*.22,2.1,-.5),Layer.OUTLINE);if(age>=6){for(int j=0;j<3;j++){double q=Math.clamp((age-6-j*2)/5.0,0,1);V at=a.end.mul(q);for(int i=0;i<10;i++){double t=-1+i*2.0/9;c.point(Layer.BODY,BONE,at.add(p(t*.5,.18*(1-t*t),-.5)));}}}if(age>=10)skull(c,a.end.add(p(0,.6,0)),.8,Layer.IMPACT);if(age==6)c.sound(Layer.OUTLINE,"block.bell.use",.6f,.5f);}
    }
    public static final class Prison implements Effect {
        public int duration(){return 40;}public int sampleBudget(){return 288;}
        public void frame(int age,Canvas c){double grow=ease((age-4)/8.0),fade=age<24?1:1-ease((age-24)/15.0);for(int j=0;j<4;j++){double z=(j-1.5)*.38;for(int side:new int[]{-1,1})rib(c,p(side*.2,(1-fade)*-.8,z),side,2*fade,grow,Layer.OUTLINE);}if(age>=12&&age<25)for(int side:new int[]{-1,1})bone(c,p(side*.6,.4,-.2),p(-side*.5,1.8,.2),Layer.BODY);if(age==12)c.sound(Layer.OUTLINE,"entity.skeleton.ambient",.4f,.6f);}
    }
    public static final class Lantern implements Effect {
        private final Anchor a;private final boolean demo;public Lantern(Anchor a,boolean demo){this.a=a;this.demo=demo;}public V offset(){return a.offset;}public int duration(){return 110;}public int sampleBudget(){return 288;}
        public void frame(int age,Canvas c){V at=p(-.8,2,0);double g=ease(age/7.0)*(age<100?1:1-ease((age-100)/9.0));for(int j=0;j<6;j++){double u=j*Math.PI/3,v=(j+1)*Math.PI/3;V lo=at.add(p(Math.cos(u)*.24*g,-.3*g,Math.sin(u)*.24*g)),hi=lo.add(p(0,.6*g,0));bone(c,lo,hi,Layer.OUTLINE);bone(c,hi,at.add(p(Math.cos(v)*.24*g,.3*g,Math.sin(v)*.24*g)),Layer.OUTLINE);bone(c,lo,at.add(p(Math.cos(v)*.24*g,-.3*g,Math.sin(v)*.24*g)),Layer.OUTLINE);}c.line(Layer.BODY,SOUL,at,at.add(p(.05*Math.sin(age*.8),(.2+.08*Math.sin(age*.37))*g,0)),6);if(age>7&&age<100)for(V target:a.companions)for(int i=0;i<10;i++){double q=i/9.0;c.point(a.pulse>0||demo&&age%30<3?Layer.IMPACT:Layer.BODY,SOUL,VfxMath.bezier(at,at.add(p(0,-.5,0)),target.add(p(0,1.7,0)),target.add(p(0,1,0)),q));}if(age==8)c.sound(Layer.OUTLINE,"block.soul_sand.step",.4f,.7f);}
    }
    public static final class Sacrifice implements Effect {
        private final List<V> spikes=new ArrayList<>();public Sacrifice(long seed){var r=new SplittableRandom(seed);for(int i=0;i<13;i++){double angle=i*2.39996,y=r.nextDouble(-.6,1);spikes.add(p(Math.cos(angle),y,Math.sin(angle)).unit().mul(r.nextDouble(.8,1.7)));}}public int duration(){return 23;}public int sampleBudget(){return 288;}
        public void frame(int age,Canvas c){V center=p(0,.9,0);if(age<11){soldier(c,V.ZERO,1,1,0,Layer.BODY);for(V spike:spikes)bone(c,center,center.add(spike.mul(ease(age/10.0))),Layer.OUTLINE);}else for(int j=0;j<spikes.size();j++){V d=spikes.get(j),at=center.add(d.mul(1+(age-11)*.2));if(j%2==0)at=at.add(p(0,-.03*(age-11)*(age-11),0));bone(c,at,at.add(d.mul(.22)),Layer.IMPACT);}if(age==11)c.sound(Layer.IMPACT,"entity.skeleton.death",.6f,.5f);}
    }
    public static final class Fusion implements Effect {
        public int duration(){return 24;}public int sampleBudget(){return 384;}
        public void frame(int age,Canvas c){if(age<7)for(int side:new int[]{-1,1})soldier(c,p(side*(1-age*.1),age*.05,0),1,1,.1,Layer.OUTLINE);if(age>=7){double g=ease((age-6)/15.0);soldier(c,V.ZERO,1.8,g,.2,Layer.OUTLINE);for(int j=0;j<3;j++)c.line(Layer.BODY,SOUL,p(-1+.2*j,1+j*.4,0),p(.9-.2*j,1.3+j*.4,0),6);if(age>14)shield(c,p(-.85,.9,.4),ease((age-14)/7.0),false);if(age==22)c.sound(Layer.OUTLINE,"entity.iron_golem.step",.6f,.65f);}}
    }
    private static void shield(Canvas c,V at,double scale,boolean crack){V[] n={p(-.5,0,0),p(-.6,1.3,0),p(0,1.55,.05),p(.6,1.3,0),p(.5,0,0),p(0,-.3,0),p(-.5,0,0)};for(int i=1;i<n.length;i++)bone(c,at.add(n[i-1].mul(scale)),at.add(n[i].mul(scale)),Layer.OUTLINE);if(crack)for(int j=0;j<3;j++)c.line(Layer.IMPACT,EDGE,at.add(p(0,.7,0)),at.add(p((j-1)*.5,.2+j*.5,.1)),5);}
    public static final class Companion implements Effect {
        private final Anchor a;private final int duration;private final boolean giant;public Companion(Anchor a,int duration,boolean giant){this.a=a;this.duration=duration;this.giant=giant;}public int duration(){return duration;}public int sampleBudget(){return giant?320:80;}public V offset(){return a.offset;}
        public void frame(int age,Canvas c){if(giant){soldier(c,V.ZERO,1.8,1,.2,Layer.OUTLINE);shield(c,p(-.85,.9,.4),1,a.pulse>0);bone(c,p(.8,1.4,.2),p(1.05,.2,.4),Layer.BODY);for(int j=0;j<3;j++)rib(c,p(.85+j*.1,.15,.35),.35,.45,1,Layer.BODY);}else for(int side:new int[]{-1,1})c.point(Layer.OUTLINE,SOUL,p(side*.1,1.65,.18));if(a.pulse>0)c.line(Layer.IMPACT,EDGE,p(-.4,1.3,.3),p(.5,.7,1.4),9);}
    }
    public static final class Fragments implements Effect {
        public int duration(){return 12;}public int sampleBudget(){return 96;}
        public void frame(int age,Canvas c){for(int j=0;j<7;j++){double angle=j*2.4;V at=p(Math.cos(angle)*age*.06,1-age*age*.008,Math.sin(angle)*age*.06);bone(c,at,at.add(p(.08,.15,.02)),Layer.IMPACT);}if(age==0)c.sound(Layer.IMPACT,"block.bone_block.break",.35f,1.2f);}
    }
    private static void armySoldier(Canvas c,V at,double size,double pose,Layer layer){
        V[] pairs={p(-.22,0,0),p(0,.7,0),p(.25,0,.1),p(0,.7,0),p(0,.7,0),p(0,1.4,0),p(-.4,1.3,0),p(.4,1.3,0),p(-.4,1.3,0),p(-.6,.9,pose),p(.4,1.3,0),p(.6,.9,pose),p(.6,.8,pose),p(.6,1.7,pose)};
        for(int j=0;j<pairs.length;j+=2)c.line(layer,BONE,at.add(pairs[j].mul(size)),at.add(pairs[j+1].mul(size)),2);
        c.point(layer,BONE,at.add(p(-.14,1.65,0).mul(size)));c.point(layer,BONE,at.add(p(.14,1.85,0).mul(size)));c.point(layer,SOUL,at.add(p(.1,1.65,.05).mul(size)));
    }
    public static final class Gate implements Effect {
        private final Anchor a;public Gate(Anchor a){this.a=a;}public V offset(){return a.offset;}public V direction(){return a.direction;}public int duration(){return 226;}public int sampleBudget(){return 384;}
        public void frame(int age,Canvas c){
            // Rectangular warning precedes every decorative sample, including at LOW quality.
            if(age<=63){V[] corners={p(-4.5,.04,0),p(4.5,.04,0),p(4.5,.04,14),p(-4.5,.04,14),p(-4.5,.04,0)};for(int j=1;j<corners.length;j++)c.line(Layer.WARNING,SOUL,corners[j-1],corners[j],9);}
            double grow=ease((age-12)/18.0),fade=age<211?1:1-ease((age-211)/14.0);if(age>=13){for(int side:new int[]{-1,1}){for(int j=0;j<7;j++){double y=j*grow*fade;c.line(Layer.OUTLINE,BONE,p(side*(2.35+.1*Math.sin(j*2)),y,0),p(side*(2.35+.1*Math.sin((j+1)*2)),(j+1)*grow*fade,0),3);}rib(c,p(side*2.4,5.2*grow*fade,0),-side*2.6,1.8*grow*fade,grow,Layer.OUTLINE);}}
            if(age<13)for(int j=0;j<4;j++)hand(c,p((j-1.5)*1.4,0,1+j),ease(age/12.0));
            if(age>=31&&age<43){for(int row=0;row<3;row++)for(int j=0;j<5;j++)if(age>=31+row*2)armySoldier(c,p((j-2)*.8,0,.3-row*.6),.85+row*.05,.3,Layer.BODY);skull(c,p(0,6.5,0),1.2,Layer.BODY);}
            for(int wave=0;wave<3;wave++){int start=43+wave*10;if(age<start||age>=start+10)continue;double q=ease((age-start)/9.0);for(int j=0;j<(wave==2?2:5);j++){V at=p((j-(wave==2?.5:2))*(wave==2?2.1:1.5),0,q*14+.15*Math.sin(j));if(wave==2)soldier(c,at,1.4,1,1,Layer.IMPACT);else armySoldier(c,at,1,1,Layer.IMPACT);c.line(Layer.DECORATION,ASH,at,at.add(p(0,.1,-1.5)),4);}if(age==start)c.sound(Layer.IMPACT,"entity.skeleton.step",.6f,.5f+wave*.15f);}
            if(age>=71&&age<90)for(int side:new int[]{-1,1})soldier(c,p(side*1.2,0,1.2),1.15,1,.2,Layer.BODY);
            if(age==30)c.sound(Layer.OUTLINE,"item.goat_horn.sound.2",.65f,.65f);
        }
    }
}

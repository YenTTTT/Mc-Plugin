package com.customrpg.vfx;

import com.customrpg.talents.DarkSkill;
import com.customrpg.vfx.VfxMath.V;
import com.customrpg.vfx.VfxRuntime.*;
import java.util.*;

/** Eight independent Eternal Night silhouettes. Only math, paints and primitive strokes are shared. */
public final class DarkVfx {
    private DarkVfx(){}
    public static List<VfxEffects.Dna> designs(){return List.of(
        new VfxEffects.Dna("dark_rend","裂影裁決","單體雙段魔法","左右影刃裁決","左右裂刃交錯成不對稱X後沉入影子","兩道有厚薄變化的斜向曲刃","影子外拉、左右依次切入、碎線下沉","0–4拉影，5及9tick命中，10–18影痕沉降","兩段相隔4tick，整次最多一層烙印","稀疏下墜碎影","兩次方向性短切音","不是月牙整圈、冰槍或球形爆炸",224),
        new VfxEffects.Dna("dark_stitch","縫魂咒紋","詛咒與能力削弱","破文縫魂","三枚斷裂古文被鋸齒紅線縫合","三角折字與不對稱縫線鎖圖","逐節刻印、紅線穿梭、低頻收縮","0–5顯字，6–12縫合，維持80tick後斷線","命中後才附速度與輸出削弱","偶發節點紅閃","壓低的幽魂與縫線聲","主要是文字形折線，沒有旋轉法陣",256),
        new VfxEffects.Dna("dark_step","換影步","位移逃脫","消失與影中重組","原地人影被吞噬而另一處從地面凝回人形","離體人形、目的地細長影紋","分離、下聚、目的地由腿至肩重組","0–4分影，5–8崩影，9傳送，14–20原影坍縮","無傷害、無無敵，僅合法安全位移","幽紫色緩慢碎片","短促潛影與重組音","不是霜影的平移雙殘影或岩石踏板",224),
        new VfxEffects.Dna("dark_curtain","斷夜帷幕","區域穿越封鎖","垂直破夜裂幕","敵人穿越時附近裂紋向交點收束","寬六高三鋸齒裂幕與垂直摺痕","垂直撕開、下流、雙側閉合","0–7裂空，8–15展開，維持100tick，再閉合","按實際跨平面事件每敵只一次傷害","短亮暗紅裂縫","低聲空間撕裂與閉合","直立面狀封鎖，不是地面霧域或圓圈",256),
        new VfxEffects.Dna("dark_echo","孽影回聲","記錄與延遲爆發","背後痛苦複寫","滿裂紋的人形暗影向前撞回目標","肩臂胸人形影子與逐次傷痕","附著、刻裂、後拉再衝入、由頭碎滅","0–6複寫，記錄80tick，80後撞入消散","僅記錄真實直接生命損失，不記錄回聲或烙印","裂紋隨記錄增加","悶重記錄聲與撞入音","主體是目標背後的人影，不是召喚獸",256),
        new VfxEffects.Dna("dark_servant","烏影僕從","無實體暗影召喚","雙爪幽影獸","彎曲影獸伸出三線巨爪後迅速收回","紅眼、細長雙鉤爪、彎曲軀體與碎尾","由地面成形、側後漂浮、伸爪巡獵","0–16成形，24tick間隔最多四爪，120結束","依真實6格可見敵人播放攻擊爪","稀疏下墜暗色碎片","低吟與短促爪擊音","頭部與雙爪辨識，不是玩家分身或煙團",288),
        new VfxEffects.Dna("dark_fracture","深淵折盾","一次防禦與反削弱","折板深淵吞噬","三塊異形暗晶板折疊封成空心多面體","不規則五六邊晶板與內陷裂口","緩慢傾斜、朝受擊點折疊、向內消失","0–6生成，等待80tick，首擊折合後紅裂反噬","首擊減傷40%且最多8，成功後近敵弱化","晶板表面細紅裂線","短裂板聲與深淵吸入音","封閉向內吞噬，不是岩壁承擊或碎鏡投射",288),
        new VfxEffects.Dna("dark_eclipse","黯日墜界・永夜裁決","終極詛咒與侵蝕","破碎黯日壓界","高空破碎彎月黯日塌成核心並垂直壓下","不連續彎月弧段、尖刺與破碎六芒地文","異高碎片聚合、瞬間向心、垂直釋放、地裂侵蝕","0–10預警，11–25黯日，25判傷，殘域100tick，131–145歸寂","爆發13與殘域三次2分開結算，整次單烙印","少量灰黑向心碎片","低沉永夜預示、核心留白與下壓重音","與六角冰冠及四座岩碑不同；沒有黑煙爆炸",384)
    );}
    public static final Paint EDGE=Paint.dust(0x8657BC,.75f),INK=Paint.dust(0x302638,.65f),RED=Paint.fade(0x9E2948,0x301E35,.6f),ASH=Paint.dust(0x605A70,.5f),BLUE=Paint.dust(0x515B91,.6f);
    public static final class Anchor {public V offset=V.ZERO,end=new V(0,1,5),impact=new V(0,1.1,.4);public double yaw,energy;public int pulse,cracks,layers;public boolean triggered;}
    private static V p(double x,double y,double z){return new V(x,y,z);}
    private static double ease(double q){return 1-Math.pow(1-Math.clamp(q,0,1),3);}
    private static V turn(V at,double a){return p(at.x()*Math.cos(a)+at.z()*Math.sin(a),at.y(),at.z()*Math.cos(a)-at.x()*Math.sin(a));}
    private static void poly(Canvas c,List<V> nodes,Paint paint,Layer layer,int n){for(int i=1;i<nodes.size();i++)c.line(layer,paint,nodes.get(i-1),nodes.get(i),n);}
    private static void glyph(Canvas c,V at,double size,int variant,Layer layer){var nodes=List.of(p(-.3,0,0),p(-.1,.5,.04),p(.25,.3,0),p(-.05,.1,.03),p(.4,-.2,0),p(.1,-.45,.04));for(int i=1;i<nodes.size();i++)if(i!=(variant%3+1))c.line(layer,variant==2?RED:EDGE,at.add(nodes.get(i-1).mul(size)),at.add(nodes.get(i).mul(size)),4);}
    private static void human(Canvas c,V base,double growth,double collapse,Paint paint,Layer layer){
        V[] joints={p(-.2,0,0),p(0,.65,0),p(.2,0,0),p(0,.65,0),p(0,1.35,0),p(-.4,1.25,0),p(-.55,.85,.1),p(-.4,1.25,0),p(.4,1.25,0),p(.5,.85,.1)};
        for(int i=1;i<joints.length;i++){V a=joints[i-1],b=joints[i];if(Math.max(a.y(),b.y())>growth*1.7)continue;c.line(layer,paint,base.add(a.mul(1-collapse)),base.add(b.mul(1-collapse)),3);}
        if(growth>.9){c.line(layer,paint,base.add(p(-.12,1.5,0).mul(1-collapse)),base.add(p(.12,1.7,0).mul(1-collapse)),4);}
    }
    public static Effect preview(String id,long seed){var s=DarkSkill.of(id);if(s==null)throw new IllegalArgumentException(id);var a=new Anchor();return switch(s){case REND->new Rend(a);case STITCH->new Stitch(a);case STEP->new Transposition(p(0,0,5));case CURTAIN->new Curtain(a);case ECHO->new Echo(a,true);case SERVANT->new Servant(a,true);case FRACTURE->new Fracture(a,true);case ECLIPSE->new Eclipse(seed);};}
    public static final class Rend implements Effect {
        private final Anchor anchor;public Rend(Anchor a){anchor=a;}public V offset(){return anchor.offset;}public int duration(){return 19;}public int sampleBudget(){return 224;}
        public void frame(int age,Canvas c){if(age<5)for(int side:new int[]{-1,1})c.line(Layer.OUTLINE,INK,p(0,.03,0),p(side*(.5+age*.18),.02,.2),8);
            for(int side:new int[]{-1,1}){int start=side==-1?5:9;double progress=ease((age-start+1)/3.0);if(age<start)continue;
                for(int i=0;i<18;i++){double t=i/17.0;if(t>progress)continue;V at=VfxMath.bezier(p(side*1.6,2.1,-.4),p(side*1.2,1.7,.6),p(-side*.6,.5,.4),p(-side*1.2,.1,-.15),t);if(age>12)at=at.mul(1-(age-12)*.06).add(p(0,-(age-12)*.1,0));c.point(age<12?Layer.IMPACT:Layer.BODY,age<12?EDGE:ASH,at);if(age<12)c.point(Layer.OUTLINE,RED,at.add(p(.06,0,.07)));}
                if(age==start)c.sound(Layer.IMPACT,"entity.player.attack.sweep",.55f,side<0?.8f:1.2f);
            }}
    }
    public static final class Stitch implements Effect {
        private final Anchor a;public Stitch(Anchor a){this.a=a;}public V offset(){return a.offset;}public int duration(){return 88;}public int sampleBudget(){return 256;}
        public void frame(int age,Canvas c){double size=age<6?ease(age/5.0):age>80?1-(age-80)/8.0:1-.04*Math.sin(age*.12);
            for(int j=0;j<3;j++)glyph(c,p((j-1)*.55,1.2+j*.12,-.5-j*.04),size,j,Layer.OUTLINE);
            if(age>=6)for(int j=0;j<5;j++){V from=p(-.75,1.8-j*.3,-.4),to=p(.6,.4+j*.23,.2);double q=ease((age-5)/7.0);V middle=from.add(to.sub(from).mul(q));c.line(Layer.BODY,RED,from,middle.add(p(.1*(j%2==0?1:-1),.05,0)),5);}
            if(age==6)c.sound(Layer.OUTLINE,"entity.evoker.prepare_attack",.35f,.65f);
        }
    }
    public static final class Transposition implements Effect {
        private final V end;public Transposition(V end){this.end=end;}public int duration(){return 22;}public int sampleBudget(){return 224;}
        public void frame(int age,Canvas c){if(age<14)human(c,p(0,0,-age*.015),1,age>=5?Math.min(.7,(age-5)*.06):0,INK,Layer.OUTLINE);else human(c,V.ZERO,1,ease((age-14)/7.0),ASH,Layer.BODY);
            if(age>=9){double growth=ease((age-8)/5.0);human(c,end,growth,0,EDGE,Layer.OUTLINE);for(int i=0;i<4;i++)c.line(Layer.BODY,BLUE,end.add(p((i-1.5)*.15,.02,-.25)),end.add(p((i-1.5)*.15,.02,.3)),4);}
            if(age==9)c.sound(Layer.OUTLINE,"entity.enderman.teleport",.4f,.7f);
        }
    }
    public static final class Curtain implements Effect {
        private final Anchor a;public Curtain(Anchor a){this.a=a;}public int duration(){return 113;}public int sampleBudget(){return 256;}
        public void frame(int age,Canvas c){double width=age<8?ease(age/7.0):age>100?1-ease((age-100)/12.0):1;double h=ease((age+1)/8.0);
            var edge=List.of(p(-3*width,0,0),p(-2.9*width,1.2*h,.05),p(-3.1*width,2.8*h,-.08),p(-1.2*width,3*h,0),p(.2*width,2.8*h,.05),p(2.6*width,3*h,0),p(3*width,1.4*h,.05),p(2.9*width,0,0));poly(c,edge,EDGE,Layer.OUTLINE,4);
            for(int j=0;j<7;j++)for(int i=0;i<8;i++){double y=i*3.0/7;double x=(j-3)*.8*width+Math.sin(y*2+age*.04+j)*.08;c.point(Layer.BODY,age%21<3&&j==age%7?RED:INK,p(x,y*h,.1*Math.sin(y+age*.02)));}
            if(a.pulse>0)for(int j=-1;j<=1;j++)c.line(Layer.IMPACT,RED,p(a.impact.x()+j*.4,2.4,0),p(a.impact.x(),1,0),5);
            if(age==8)c.sound(Layer.OUTLINE,"block.sculk_shrieker.shriek",.25f,.6f);
        }
    }
    public static final class Echo implements Effect {
        private final Anchor a;private final boolean demo;public Echo(Anchor a,boolean demo){this.a=a;this.demo=demo;}public V offset(){return a.offset;}public int duration(){return 100;}public int sampleBudget(){return 256;}
        public void frame(int age,Canvas c){int cracks=demo?Math.min(7,age/10):a.cracks;double z=age<80?-.8:age<85?-.8-(age-80)*.16:-1.6+ease((age-85)/5.0)*1.6;
            human(c,p(0,age>90?-(age-90)*.06:0,z),ease(age/6.0),age>90?ease((age-90)/9.0):0,INK,Layer.OUTLINE);
            for(int i=0;i<Math.min(8,cracks);i++){double y=.3+i*.17;V at=p(Math.sin(i*8)*.3,y,z);c.line(age>=80?Layer.IMPACT:Layer.BODY,RED,at,at.add(p(.2,-.12,.03)),4);}
            if(age==85)c.sound(Layer.IMPACT,"entity.warden.sonic_charge",.3f,.6f);
        }
    }
    public static final class Servant implements Effect {
        private final Anchor a;private final boolean demo;public Servant(Anchor a,boolean demo){this.a=a;this.demo=demo;}public V offset(){return a.offset;}public int duration(){return 126;}public int sampleBudget(){return 288;}
        public void frame(int age,Canvas c){V root=p(-1,0,-.7);double grow=ease(age/16.0),fade=age>118?Math.max(0,1-(age-118)/8.0):1;
            for(int i=0;i<18;i++){double t=i/17.0;V at=VfxMath.bezier(p(-.1,0,0),p(-.6,.7,.3),p(.3,1.8,-.1),p(.1,2.1,0),t);if(t>grow)continue;c.point(Layer.OUTLINE,EDGE,root.add(at.mul(fade)).add(p(Math.sin(age*.08)*.04,0,0)));}
            for(int side:new int[]{-1,1}){V shoulder=root.add(p(side*.35,1.5*grow,0));poly(c,List.of(shoulder,shoulder.add(p(side*.45,-.35,.15)),shoulder.add(p(side*.5,-.8,.35)),shoulder.add(p(side*.25,-.7,.4))),INK,Layer.OUTLINE,5);if(age>9&&age<123)c.point(Layer.IMPACT,RED,root.add(p(side*.12,1.95*grow,.06)));}
            boolean attack=a.pulse>0||demo&&age>=24&&age<=100&&age%24<8;
            if(attack){double q=demo?(age%24+1)/8.0:(9-a.pulse)/8.0;V tip=VfxMath.bezier(root.add(p(.3,1.4,0)),root.add(p(.8,1.8,2)),a.end.add(p(-.4,.8,-.2)),a.end,q);for(int j=-1;j<=1;j++)c.line(Layer.IMPACT,EDGE,tip.add(p(j*.16,.15,-.5)),tip.add(p(j*.16,-.2,.3)),7);}
            if(age==12)c.sound(Layer.OUTLINE,"entity.vex.ambient",.3f,.65f);
        }
    }
    public static final class Fracture implements Effect {
        private final Anchor a;private final boolean demo;private int folded=-1;public Fracture(Anchor a,boolean demo){this.a=a;this.demo=demo;}public V offset(){return a.offset;}public int duration(){return 96;}public int sampleBudget(){return 288;}
        public void frame(int age,Canvas c){if(folded<0&&(a.triggered||demo&&age==40))folded=age;double fold=folded<0?0:ease((age-folded)/6.0);if(folded<0&&age>=80)return;
            if(fold<1)for(int j=0;j<3;j++){double angle=j*Math.PI*2/3+age*.006;V center=p(Math.cos(angle)*.9,1.2+Math.sin(angle)*.25,Math.sin(angle)*.7).mul(1-fold).add(a.impact.mul(fold));var nodes=new ArrayList<V>();for(int i=0;i<5+j%2;i++){double r=.45+(i%2)*.1,theta=i*Math.PI*2/(5+j%2);nodes.add(center.add(turn(p(Math.cos(theta)*r,Math.sin(theta)*.65,.12*Math.sin(theta*2)),angle+fold*1.5).mul(ease(age/6.0)*(1-fold*.5))));}nodes.add(nodes.get(0));poly(c,nodes,EDGE,Layer.OUTLINE,4);}
            if(folded>=0&&age-folded>=6&&age-folded<14){double q=(age-folded-5)/8.0;V end=a.end.mul(q);c.line(Layer.IMPACT,RED,p(0,1.1,.4),end,14);glyph(c,end,.6,2,Layer.IMPACT);}
            if(folded==age)c.sound(Layer.IMPACT,"block.respawn_anchor.deplete",.35f,.55f);
        }
    }
    public static final class Eclipse implements Effect {
        private final List<V> fragments=new ArrayList<>();public Eclipse(long seed){var r=new SplittableRandom(seed);for(int i=0;i<10;i++){double a=i*2.4;fragments.add(p(Math.cos(a)*r.nextDouble(2.8,4.5),r.nextDouble(4,8),Math.sin(a)*r.nextDouble(.6,2)));}}
        public int duration(){return 146;}public int sampleBudget(){return 384;}
        public void frame(int age,Canvas c){
            if(age<=125)for(int j=0;j<6;j++){double a=j*Math.PI/3;V from=p(Math.cos(a)*.8,.04,Math.sin(a)*.8),to=p(Math.cos(a)*7,.04,Math.sin(a)*7);V mid=p(Math.cos(a+.15)*3.4,.04,Math.sin(a+.15)*3.4);poly(c,List.of(from,mid,to),age<25?RED:ASH,Layer.WARNING,3);}
            if(age>=11&&age<=35){double grow=ease((age-10)/15.0),collapse=age>25?1-ease((age-25)/9.0):1;
                for(int j=0;j<6;j++)for(int i=0;i<6;i++){double angle=j*Math.PI/3+.1+i*.12;double r=(2.1+(j%2)*.3)*grow*collapse;V at=p(Math.cos(angle)*r,6+Math.sin(angle)*r,Math.sin(angle*2+j)*.35*collapse);c.point(Layer.OUTLINE,EDGE,at);if(i==5)c.line(Layer.BODY,INK,at,at.add(p(Math.cos(angle)*.5,Math.sin(angle)*.5,.1)).mul(collapse).add(p(0,6*(1-collapse),0)),3);}
                for(V f:fragments)c.line(Layer.DECORATION,INK,f.mul(1-grow*.7).add(p(0,4*grow,0)),f.mul(1-grow*.7).add(p(.12,4*grow+.2,0)),3);
            }
            if(age>=25&&age<36){double q=(age-25)/10.0;for(int i=-1;i<=1;i++)c.line(Layer.IMPACT,RED,p(i*.15,6*(1-q),0),p(i*.3,.1,0),8);}
            if(age>=26)for(int j=0;j<5;j++){double a=j*2.4;double shrink=age>130?1-ease((age-130)/15.0):1;poly(c,List.of(p(Math.cos(a)*.6,.05,Math.sin(a)*.6),p(Math.cos(a+.2)*3*shrink,.07,Math.sin(a+.2)*3*shrink),p(Math.cos(a-.08)*6*shrink,.05,Math.sin(a-.08)*6*shrink)),(age==45||age==85||age==125)?RED:INK,Layer.OUTLINE,4);}
            if(age==11)c.sound(Layer.WARNING,"entity.wither.ambient",.35f,.5f);if(age==25)c.sound(Layer.IMPACT,"entity.warden.sonic_boom",.45f,.6f);
        }
    }
    public static final class Brand implements Effect {
        private final Anchor a;public Brand(Anchor a){this.a=a;}public V offset(){return a.offset;}public int duration(){return 140;}public int sampleBudget(){return 128;}
        public void frame(int age,Canvas c){for(int i=0;i<Math.clamp(a.layers,0,3);i++)glyph(c,p((i-1)*.3,1.05+i*.12,-.4),.6+i*.1,i,Layer.BODY);if(a.layers==3)c.line(Layer.BODY,RED,p(-.55,.8,-.42),p(.5,1.5,-.42),6);}
    }
    public static final class BrandBurst implements Effect {
        public int duration(){return 10;}public int sampleBudget(){return 96;}
        public void frame(int age,Canvas c){double q=1-ease(age/8.0);for(int j=0;j<4;j++){V at=p((j-1.5)*q*.5,1.1+(j%2)*.3,-.3);c.line(Layer.IMPACT,age<5?EDGE:ASH,at,at.add(p(.3*q,.4*q,0)),5);}if(age==1)c.sound(Layer.IMPACT,"block.sculk_catalyst.bloom",.4f,.7f);}
    }
}

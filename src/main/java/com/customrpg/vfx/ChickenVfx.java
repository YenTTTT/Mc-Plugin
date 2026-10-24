package com.customrpg.vfx;

import com.customrpg.talents.ChickenSkill;
import com.customrpg.vfx.VfxMath.V;
import com.customrpg.vfx.VfxRuntime.*;
import java.util.*;

/** Eight independent storyboards. Rooster and God bodies are real chickens, supplied by the controller. */
public final class ChickenVfx {
    private ChickenVfx(){}
    public static List<VfxEffects.Dna> designs(){return List.of(
        new VfxEffects.Dna("chicken_egg","丟雞蛋","精神主屬性投擲與30%孵化","原版飛蛋與小雞","撞擊地面破殼孵化，命中生物不加特效","原版蛋形與地面兩片裂殼","拋物線飛行，地面開殼","飛行到真實碰撞；生物命中停止自訂粒子","2＋總精神×0.12物理基礎傷害","僅飛行白尾與地面孵化羽毛","短拋蛋聲","命中生物不疊爆裂粒子",192),
        new VfxEffects.Dna("chicken_feed","撒米開飯","普通怪引誘與玩家減速","生長中心","飼料中心冒出骨粉般生長綠點","中心HAPPY_VILLAGER粒子簇","中心間歇向上浮動","每四tick一小簇，80tick結束","無傷害，單敵引誘最多40tick","少量中心外圍綠點","輕微草地聲","主體是中心生長粒子，沒有米堆或圓環",32),
        new VfxEffects.Dna("chicken_supply","營養雙黃蛋","兩隻成人雞持續補給","跟隨治療雞","兩隻真正成年雞跟在主人身後發出愛心","成對原版成年Chicken及HEART","身後跟隨及間歇愛心上浮","20tick一輪補給，200tick共十輪","每隻每秒1生命及2魔力，附近3格隊友同受益","原版HEART愛心","不持續刷音效","是成對跟隨雞，不再是可拾取地面雙黃蛋",96),
        new VfxEffects.Dna("chicken_dawn","破曉雞鳴","一般引導中斷與解除一種減速","大公雞破曉","紅冠公雞昂首三道扇形聲波","高雞冠長尾及向外展翅","挺胸張翼後聲波分三拍外掃","0至6登場，7至12啼鳴，13至20散羽","Boss受2魔法，一般怪只中斷可中斷引導","隊友身上短金羽","低音雞鳴及扇形破風","不是單體鬥雞或無方向的球形爆炸",288),
        new VfxEffects.Dna("chicken_nest","母雞護巢","暫時實體方塊攔截一次投射物","堆肥桶護巢","真正堆肥桶攔箭後立刻還原","一格寬原版COMPOSTER方塊","放置固定後攔截即還原","最多120tick，首次攔截立即結束","不擋近戰或所有法術，具有真實方塊碰撞","僅攔截瞬間少量白羽","不額外循環音效","真方塊取代粒子母雞與稻草巢",32),
        new VfxEffects.Dna("chicken_rooster","鬥雞突襲","單體三連啄","暴躁成年公雞","真正的成年雞追上目標連啄三下","原版Chicken與尖銳V形喙痕","疾跑追擊及間隔十tick啄擊","登場後追擊，三次間隔10tick，最多80tick","每啄2物理，第三次成功才減速20%","紅怒符及爪印速度線","短急雞鳴及三下啄擊","原版雞是主體，粒子只有輔助痕跡",224),
        new VfxEffects.Dna("chicken_stampede","雞群衝鋒","全部普通小雞參戰與精神加傷","無上限雞群洪流","十二道雞影及全部普通小雞同時衝鋒","寬五格多排雞影與不限數量真幼雞","不同跳躍相位的水平奔流","0至14集結，15至25衝撞，35消耗參戰幼雞","6＋總精神×0.25＋全部參戰小雞數物理，每敵一次","雞影受渲染預算限制，小雞參戰與加傷不限數量","口哨與雞群踏步","水平群體奔跑與大雞垂降明顯不同",384),
        new VfxEffects.Dna("chicken_god","雞神降世・神雞天墜","精神主屬性巨型真實召喚","神雞天墜與三十秒暴走","真正七格高雞神由天空落下後長時間追擊","SCALE十倍Chicken與金色雞爪預警","懸浮加速垂降後三十秒地面追擊","11出生，30落地，70至630每40tick啄擊，631賜福","落地16＋精神×0.6，最多15啄各2＋精神×0.1","少量金羽與正常離場三顆祝福蛋","低雞鳴與重落地聲","是真正放大Chicken留場作戰，不是粒子巨雞",384)
    );}
    public static final Paint WHITE=Paint.dust(0xF4F0DC,.65f),GOLD=Paint.fade(0xF4CF52,0xB58032,.65f),RED=Paint.dust(0xC54A3C,.65f),STRAW=Paint.dust(0x9F8051,.5f);
    public static final class Anchor {public V offset=V.ZERO,end=new V(0,1,3),travel=V.FORWARD;public int pulse,remaining=3;public boolean triggered;}
    private static V p(double x,double y,double z){return new V(x,y,z);}
    private static double ease(double q){return 1-Math.pow(1-Math.clamp(q,0,1),3);}
    private static void feather(Canvas c,V at,double size,double angle,Layer layer){V d=p(Math.cos(angle)*size,size*.3,Math.sin(angle)*size);c.line(layer,WHITE,at,at.add(d),3);c.line(layer,WHITE,at.add(d.mul(.4)),at.add(d.mul(.65)).add(p(.06,0,-.04)),2);}
    private static void egg(Canvas c,V at,double size,Paint paint,Layer layer){for(int i=0;i<12;i++){double a=i*Math.PI/6;double r=.22*(1-.2*Math.sin(a));c.point(layer,paint,at.add(p(Math.cos(a)*r,Math.sin(a)*.32+.32,Math.sin(a*2)*.04).mul(size)));}}
    private static void claw(Canvas c,V at,double size,Layer layer){for(int j=-1;j<=1;j++)c.line(layer,GOLD,at,at.add(p(j*.25,.02,.5).mul(size)),3);c.line(layer,GOLD,at,at.add(p(0,.02,-.22).mul(size)),3);}
    private static void bird(Canvas c,V at,double size,double wings,Layer layer){
        V[] body={p(-.3,.25,0),p(-.38,.6,0),p(0,.8,0),p(.35,.6,0),p(.22,.28,0),p(-.3,.25,0)};
        for(int i=1;i<body.length;i++)c.line(layer,WHITE,at.add(body[i-1].mul(size)),at.add(body[i].mul(size)),2);
        c.line(layer,GOLD,at.add(p(.25,.65,0).mul(size)),at.add(p(.5,.6,0).mul(size)),2);
        c.line(layer,RED,at.add(p(.06,.8,0).mul(size)),at.add(p(.13,1,0).mul(size)),2);
        for(int side:new int[]{-1,1}){c.line(layer,WHITE,at.add(p(side*.15,.5,0).mul(size)),at.add(p(side*(.5+wings*.2),.4+wings*.3,0).mul(size)),2);c.point(layer,GOLD,at.add(p(side*.15,.1,.05).mul(size)));}
    }
    public static Effect preview(String id,long seed){var s=ChickenSkill.of(id);if(s==null)throw new IllegalArgumentException(id);var a=new Anchor();return switch(s){case EGG->new Toss();case FEED->new Feeding();case SUPPLY->new Supply(a,true);case DAWN->new Dawn();case NEST->new Nest(a,true);case ROOSTER->new Rooster(a,true);case STAMPEDE->new Stampede(3);case GOD->new God();};}
    public static final class Toss implements Effect {
        public int duration(){return 30;}public int sampleBudget(){return 192;}
        public void frame(int age,Canvas c){double q=Math.clamp(age/14.0,0,1);V at=p(0,1.1+2*q-3*q*q,q*7);if(age<15){egg(c,at,.7,WHITE,Layer.OUTLINE);for(int j=0;j<4;j++)c.point(Layer.DECORATION,WHITE,at.add(p(.03*j,0,-.15*j)));}else for(int side:new int[]{-1,1})c.line(Layer.IMPACT,WHITE,p(side*.1,0,7),p(side*.6,-(age-15)*.01,7+.2),6);if(age==0)c.sound(Layer.OUTLINE,"entity.egg.throw",.45f,1.1f);}
    }
    public static final class Trail implements Effect {
        private final Anchor a;public Trail(Anchor a){this.a=a;}public V offset(){return a.offset;}public V direction(){return a.travel;}public int duration(){return 40;}public int sampleBudget(){return 32;}
        public void frame(int age,Canvas c){for(int j=0;j<3;j++)c.point(Layer.DECORATION,WHITE,p(0,0,-j*.12));if(age==0)c.sound(Layer.OUTLINE,"entity.egg.throw",.45f,1.1f);}
    }
    /** One impact at the actual accepted hit; independent of the moving bird's origin. */
    public static final class Peck implements Effect {
        private final boolean giant;public Peck(boolean giant){this.giant=giant;}public int duration(){return 7;}public int sampleBudget(){return 64;}
        public void frame(int age,Canvas c){double size=giant?.7:.3;V hit=p(0,-.2,0);c.line(Layer.IMPACT,GOLD,hit.add(p(-size,size,-size*.5)),hit,5);c.line(Layer.IMPACT,GOLD,hit,hit.add(p(size,size,-size*.5)),5);if(age<4)for(int j=0;j<3;j++)feather(c,hit.add(p((j-1)*size,.1+age*.04,j*.08)),.15,j,Layer.BODY);if(age==0)c.sound(Layer.IMPACT,"entity.chicken.hurt",.35f,giant?.65f:1.5f);}
    }
    public static final class SupplyBurst implements Effect {
        public int duration(){return 12;}public int sampleBudget(){return 96;}
        public void frame(int age,Canvas c){for(int j=0;j<2;j++){double q=ease(age/10.0);V at=p((j-.5)*.5*(1-q),.2+q*1.2,.2*(1-q));egg(c,at,.35,GOLD,Layer.IMPACT);feather(c,at,.15,j,Layer.BODY);}if(age==0)c.sound(Layer.IMPACT,"entity.experience_orb.pickup",.45f,1.3f);}
    }
    public static final class Shell implements Effect {
        private final boolean hatch;public Shell(boolean hatch){this.hatch=hatch;}public int duration(){return 18;}public int sampleBudget(){return 96;}
        public void frame(int age,Canvas c){for(int side:new int[]{-1,1}){double q=ease(age/10.0);c.line(Layer.IMPACT,WHITE,p(side*.08,.15,0),p(side*(.15+q*.45),.2-q*.3,.15),5);}if(hatch)for(int j=0;j<5;j++){double a=j*1.26;feather(c,p(Math.cos(a)*.35,.8+age*.025,Math.sin(a)*.35),.13,a,Layer.BODY);}if(age==0)c.sound(Layer.IMPACT,hatch?"entity.chicken.ambient":"block.calcite.break",.45f,hatch?1.8f:1.3f);}
    }
    public static final class Feeding implements Effect {
        private static final Paint GROWTH=new Paint(ParticleKind.GROWTH,0,0,1,V.ZERO,0);
        public int duration(){return 80;}public int sampleBudget(){return 32;}
        public void frame(int age,Canvas c){if(age%4==0)for(int i=0;i<5;i++){double angle=i*2.4+age*.1;c.point(Layer.OUTLINE,GROWTH,p(Math.cos(angle)*.25,.12+(i%3)*.15,Math.sin(angle)*.25));}}
    }
    /** Hearts track the real adult healers; the demo draws two small outlines for headless review. */
    public static final class Supply implements Effect {
        private final Anchor a;private final boolean demo;public Supply(Anchor a,boolean demo){this.a=a;this.demo=demo;}public int duration(){return 201;}public int sampleBudget(){return 96;}public V offset(){return demo?V.ZERO:a.offset;}
        public void frame(int age,Canvas c){if(age%10==0)for(int i=0;i<(demo?2:1);i++)c.point(Layer.OUTLINE,new Paint(ParticleKind.HEART,0,0,1,V.ZERO,0),demo?p((i-.5)*1.3,1.1,-1):p(0,1.1,0));}
    }
    public static final class Dawn implements Effect {
        public int duration(){return 25;}public int sampleBudget(){return 288;}
        public void frame(int age,Canvas c){if(age<19){bird(c,p(0,1,-.9),1.6,ease((age-5)/4.0),Layer.OUTLINE);for(int j=0;j<4;j++)feather(c,p(-.4-j*.1,2+j*.16,-1),.35,j*.2,Layer.BODY);}for(int wave=0;wave<3;wave++){int t=age-7-wave*2;if(t<0||t>10)continue;double r=(t+1)*.55;for(int j=0;j<15;j++){double angle=-1.2+j*2.4/14;c.point(Layer.IMPACT,GOLD,p(Math.sin(angle)*r,.8+wave*.1,Math.cos(angle)*r));}}if(age==7)c.sound(Layer.OUTLINE,"entity.chicken.ambient",.7f,.5f);}
    }
    public static final class Nest implements Effect {
        private final Anchor a;private final boolean demo;public Nest(Anchor a,boolean demo){this.a=a;this.demo=demo;}public int duration(){return 121;}public int sampleBudget(){return 32;}
        public void frame(int age,Canvas c){if(a.triggered||demo&&age==65)for(int j=0;j<5;j++)feather(c,p(Math.cos(j)*.4,.8,Math.sin(j)*.4),.15,j,Layer.IMPACT);}
    }
    public static final class Rooster implements Effect {
        private final Anchor a;private final boolean demo;public Rooster(Anchor a,boolean demo){this.a=a;this.demo=demo;}public V offset(){return demo?V.ZERO:a.offset;}public int duration(){return 81;}public int sampleBudget(){return 224;}
        public void frame(int age,Canvas c){V at=demo?p(0,0,Math.min(6,age*.4)):V.ZERO;if(age<6)egg(c,at,1.2,RED,Layer.OUTLINE);if(age<35)for(int j=0;j<3;j++)claw(c,at.add(p((j%2==0?-.1:.1),.03,-j*.3)),.2,Layer.BODY);boolean peck=a.pulse>0||demo&&age>=15&&age<=38&&age%10<5;if(peck){V hit=at.add(p(0,.45,.7));c.line(Layer.IMPACT,GOLD,hit.add(p(-.2,.2,-.2)),hit,5);c.line(Layer.IMPACT,GOLD,hit,hit.add(p(.2,.2,-.2)),5);}if(age==3)c.sound(Layer.OUTLINE,"entity.chicken.ambient",.5f,1.4f);}
    }
    public static final class Stampede implements Effect {
        private final int babies;public Stampede(int babies){this.babies=Math.max(0,babies);}public int duration(){return 36;}public int sampleBudget(){return 384;}
        public void frame(int age,Canvas c){if(age<7){for(int j=-1;j<=1;j++)claw(c,p(j*.5,.03,.2),.6,Layer.OUTLINE);}else if(age<30){double run=age<15?0:ease((age-15)/10.0)*10;for(int j=0;j<12;j++){double x=(j%4-1.5)*1.05,z=(j/4)*-.65+run;double hop=Math.max(0,Math.sin(age*.6+j)*.22);bird(c,p(x,hop,z),.75+(j%3)*.08,Math.sin(age*.5+j),Layer.OUTLINE);}for(int j=0;j<Math.min(8,babies);j++)claw(c,p((j-3.5)*.5,.04,run+.6),.35,Layer.BODY);}if(age>=25)for(int j=0;j<8;j++)feather(c,p(Math.cos(j*2.4)*2,.3-(age-25)*.015,8+j*.2),.2,j,Layer.BODY);if(age==1)c.sound(Layer.OUTLINE,"entity.parrot.ambient",.5f,1.7f);}
    }
    public static final class God implements Effect {
        private final Anchor a;public God(){this(new Anchor());}public God(Anchor a){this.a=a;}
        public int duration(){return 651;}public int sampleBudget(){return 384;}
        public void frame(int age,Canvas c){if(age<=30){for(int j=0;j<24;j++){double angle=j*Math.PI/12;c.point(Layer.WARNING,GOLD,p(Math.cos(angle)*7,.04,Math.sin(angle)*7));}claw(c,p(0,.04,-1),2.8,Layer.WARNING);}if(age>=11&&age<30)for(int j=0;j<6;j++)feather(c,p(Math.cos(j)*2,8-(age>22?Math.pow((age-22)/8.0,2)*8:0)+Math.sin(j),Math.sin(j)*2),.4,j,Layer.OUTLINE);if(age>=30&&age<43){double r=(age-29)*.55;for(int j=0;j<18;j++){double angle=j*Math.PI/9;feather(c,p(Math.cos(angle)*r,.1+Math.sin(j)*.1,Math.sin(angle)*r),.3,angle,Layer.IMPACT);}claw(c,p(0,.04,0),3,Layer.OUTLINE);}if(age>=631){for(int j=0;j<3;j++)if(age<634+j*6)egg(c,a.offset.add(p((j-1)*.65,.05,0)),1,GOLD,Layer.OUTLINE);}if(age==1)c.sound(Layer.WARNING,"entity.chicken.ambient",.65f,.5f);if(age==30)c.sound(Layer.IMPACT,"entity.generic.explode",.7f,.65f);}
    }
}

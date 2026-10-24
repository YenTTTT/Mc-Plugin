package com.customrpg.vfx;

import com.customrpg.vfx.VfxMath.V;
import com.customrpg.vfx.VfxRuntime.*;
import com.customrpg.vfx.VfxTimeline.*;
import java.util.*;

/** Three independent choreographies. Only math, canvas and transport are shared. */
public final class VfxEffects {
    private VfxEffects() {}
    public static final String SLASH="v3_weapon_crescent_slash",THUNDER="demo_thunder_judgment",VOID="demo_void_collapse";
    public record Dna(String id,String name,String role,String theme,String signature,String silhouette,String motion,
                      String rhythm,String impact,String decoration,String audio,String difference,int sampleBudget) {}
    private static final Map<String,Dna> DESIGNS;
    static {
        var designs=new LinkedHashMap<String,Dna>();
        designs.put(SLASH,new Dna(SLASH,"弧月斬","即時扇區近戰","銳利切割",
                "刃尖超前、刃腹拉寬與局部斷裂","100度立體帶狀刃面","單向掃切與錯位殘影",
                "0至4tick掃切，2至11tick較淡殘留；不增加戰鬥前搖","實際扣血才畫雙切口",
                "少量切向火星","一次短破風與命中切割聲","不使用地裂、突進長線或旋轉整圈；W09第二段反向",192));
        designs.put(THUNDER,new Dna(THUNDER,"雷霆審判・預覽","無傷害落雷／連鎖樣本","斷續電流",
                "垂直主幹與三條不規則側枝","高細折線及離散支路","突現落雷、間歇閃爍與跳接",
                "0至5tick細預示，6tick高潮，14及22tick跳接，38tick結束","合成端點放電，不代表真實命中",
                "少量電暈","低音預示、短雷聲、弱連鎖","不是E03換色直線，也未替換奇犽或E03的戰鬥VFX",256));
        designs.put(VOID,new Dna(VOID,"虛空崩解・預覽","無傷害延遲區域樣本","逆向空間碎裂",
                "懸停後空心核心消失，再向外翻碎","七個不對稱三維碎片與斷裂方形符文","不同角速度向心、停頓、外翻",
                "0至28tick收束，29至35tick懸停與留白，36至57tick非球形釋放","碎片斷層，不送傷害／控制",
                "少量逆向塵屑","低音、高潮前靜默、破碎短聲","不同於重力圓環或火焰爆炸；未遷移A04及舊虛空技能",256));
        for(var frost:FrostVfx.designs())designs.put(frost.id(),frost);
        for(var earth:EarthVfx.designs())designs.put(earth.id(),earth);
        for(var chicken:ChickenVfx.designs())designs.put(chicken.id(),chicken);
        for(var necro:NecroVfx.designs())designs.put(necro.id(),necro);
        for(var dark:DarkVfx.designs())designs.put(dark.id(),dark);
        DESIGNS=Collections.unmodifiableMap(designs);
    }
    public static Collection<Dna> designs(){return DESIGNS.values();}
    public static String resolve(String id){
        if(id==null)return null;String key=id.toLowerCase(Locale.ROOT);
        var frost=com.customrpg.talents.FrostSkill.resolve(key);if(frost!=null)return frost;
        var earth=com.customrpg.talents.EarthSkill.resolve(key);if(earth!=null)return earth;
        var dark=com.customrpg.talents.DarkSkill.resolve(key);if(dark!=null)return dark;
        var necro=com.customrpg.talents.NecroSkill.resolve(key);if(necro!=null)return necro;
        var chicken=com.customrpg.talents.ChickenSkill.resolve(key);if(chicken!=null)return chicken;
        if(key.equals("w01"))return SLASH;
        if(key.equals("thunder_judgment"))return THUNDER;
        if(key.equals("void_collapse"))return VOID;
        return DESIGNS.containsKey(key)?key:null;
    }
    public static Dna design(String id){return DESIGNS.get(id);}
    public static Effect create(String id,long seed){if(com.customrpg.talents.ChickenSkill.handles(id))return ChickenVfx.preview(id,seed);if(com.customrpg.talents.NecroSkill.handles(id))return NecroVfx.preview(id,seed);if(com.customrpg.talents.DarkSkill.handles(id))return DarkVfx.preview(id,seed);if(com.customrpg.talents.EarthSkill.handles(id))return EarthVfx.preview(id,seed);if(com.customrpg.talents.FrostSkill.handles(id))return FrostVfx.preview(id,seed);return switch(id){case SLASH->new CrescentSlash();case THUNDER->new ThunderJudgment(seed);case VOID->new VoidCollapse(seed);default->throw new IllegalArgumentException("Unknown VFX "+id);};}
    public static Effect impact(){return new SlashImpact();}

    private static final class CrescentSlash implements Effect {
        private final Track sweep=new Track(0,5,Ease.OUT_CUBIC);
        public int duration(){return 12;}
        public int sampleBudget(){return design(SLASH).sampleBudget();}
        public void frame(int age,VfxRuntime.Canvas c){
            double sign=c.mirrored()?-1:1;
            if(age<5){
                double head=sweep.at(age+.65),tail=Math.max(0,head-.34);
                for(int i=0;i<26;i++){
                    double u=tail+(head-tail)*i/25;double angle=Math.toRadians(-50+100*u);
                    // Two small breaks and a displaced edge give the blade an asymmetric silhouette.
                    if(i==9||i==18)continue;
                    double thickness=.22+.72*Math.sin(Math.PI*u);
                    for(int side=0;side<3;side++){
                        V p=VfxMath.blade(angle,4.25,thickness,side/2.0);
                        p=new V(p.x()*sign,p.y()+.13*Math.sin(u*7),p.z());
                        c.point(side==1?Layer.BODY:Layer.OUTLINE,side==1?Paint.fade(0xFFF1B5,0x786C88,.65f):Paint.dust(0xF2F6FF,.72f),p);
                    }
                }
                double a=Math.toRadians(-50+100*head);V tip=VfxMath.blade(a,4.25,0,1);tip=new V(tip.x()*sign,tip.y(),tip.z());
                c.line(Layer.OUTLINE,Paint.dust(0xFFFFFF,.9f),tip,tip.add(new V(-.25*sign,.18,-.16)),5);
                if(age==2)for(int i=0;i<4;i++)c.point(Layer.DECORATION,Paint.spark(new V(sign,.2,-.3)),tip.add(new V(0,i*.04,0)));
            }
            if(age>=2){
                double fade=(age-2)/10.0;
                for(int shard=0;shard<3;shard++)for(int i=0;i<9;i++){
                    double u=.08+shard*.29+i*.018;double a=Math.toRadians(-50+100*u);
                    V p=VfxMath.blade(a,3.8-fade*.25,.3,1);
                    p=new V(p.x()*sign+shard*.06*sign,p.y()-.1-shard*.055-fade*.12,p.z()-.2-fade*.16);
                    c.point(Layer.BODY,Paint.fade(0xB8C1DC,0x34313F,(float)(.65-fade*.3)),p);
                }
            }
            if(age==0)c.sound(Layer.OUTLINE,"entity.player.attack.sweep",.65f,c.mirrored()?1.35f:1.05f);
        }
    }
    private static final class SlashImpact implements Effect {
        public int duration(){return 5;}
        public int sampleBudget(){return 32;}
        public void frame(int age,VfxRuntime.Canvas c){
            double length=.55+age*.025;Paint paint=Paint.fade(0xFFF4C4,0x51475C,(float)(.8-age*.1));
            c.line(Layer.IMPACT,paint,new V(-length,-length*.45,0),new V(length,length*.45,0),13);
            c.line(Layer.IMPACT,paint,new V(-.18,.4,.08),new V(.18,-.4,.08),9);
            if(age==0)c.sound(Layer.IMPACT,"entity.player.attack.crit",.4f,1.5f);
        }
    }
    private static final class ThunderJudgment implements Effect {
        private final List<V> trunk;private final List<List<V>> branches=new ArrayList<>(),chain=new ArrayList<>();
        ThunderJudgment(long seed){
            trunk=VfxMath.jagged(new V(0,8,6),new V(0,0,6),10,.65,seed);
            for(int i=0;i<3;i++){
                V start=trunk.get(3+i*2);branches.add(VfxMath.jagged(start,start.add(new V(i%2==0?-1.5:1.3,-1.8,.7)),4,.3,seed+11+i));
            }
            chain.add(VfxMath.jagged(new V(0,0,6),new V(2,.5,7.5),7,.4,seed+41));
            chain.add(VfxMath.jagged(new V(2,.5,7.5),new V(-1,.8,9),7,.4,seed+42));
        }
        public int duration(){return 38;}
        public int sampleBudget(){return design(THUNDER).sampleBudget();}
        public void frame(int age,VfxRuntime.Canvas c){
            if(age<6){
                double h=.25+age*.12;
                c.line(Layer.WARNING,Paint.dust(0xD5E5FF,.55f),new V(-.7,h,6),new V(0,0,6),9);
                c.line(Layer.WARNING,Paint.dust(0xD5E5FF,.55f),new V(.7,h,6),new V(0,0,6),9);
            }
            if(age>=6&&age<=12&&(age<9||age%2==0)){
                c.polyline(Layer.OUTLINE,Paint.dust(age==6?0xFFFFFF:0xA3CAFF,.75f),trunk,5);
                for(var branch:branches)c.polyline(Layer.DECORATION,Paint.fade(0xD2ECFF,0x5A74A1,.55f),branch,4);
                if(age==6)for(int i=0;i<4;i++)c.point(Layer.IMPACT,Paint.spark(new V(i%2==0?1:-1,.2,0)),new V(0,i*.07,6));
            }
            for(int leg=0;leg<2;leg++)if(age>=14+leg*8&&age<17+leg*8){
                c.polyline(Layer.OUTLINE,Paint.dust(0xE9F4FF,.7f),chain.get(leg),5);
                V at=chain.get(leg).getLast();c.line(Layer.IMPACT,Paint.dust(0xFFFFFF,.65f),at.add(new V(-.25,0,0)),at.add(new V(.25,0,0)),6);
            }
            if(age==0)c.sound(Layer.WARNING,"block.beacon.power_select",.35f,.65f);
            if(age==6)c.soundAt(Layer.OUTLINE,new V(0,0,6),"entity.lightning_bolt.impact",.65f,1.2f);
            if(age==14||age==22)c.soundAt(Layer.IMPACT,chain.get(age==14?0:1).getLast(),"block.amethyst_block.hit",.3f,1.7f);
        }
    }
    private static final class VoidCollapse implements Effect {
        private final List<List<V>> polygons=new ArrayList<>();private final double[] radius=new double[7],height=new double[7],angle=new double[7],spin=new double[7];
        private final Track inward=new Track(0,29,Ease.IN_CUBIC),outward=new Track(36,58,Ease.OUT_CUBIC);
        VoidCollapse(long seed){var random=new SplittableRandom(seed);for(int i=0;i<7;i++){
            polygons.add(VfxMath.polygon(3+i%2,.35+random.nextDouble()*.28,seed+i));radius[i]=random.nextDouble(2.5,4);height[i]=random.nextDouble(-.65,2.1);
            angle[i]=i*Math.PI*2/7+random.nextDouble(-.15,.15);spin[i]=random.nextDouble(.025,.065)*(i%2==0?1:-1);
        }}
        public int duration(){return 58;}
        public int sampleBudget(){return design(VOID).sampleBudget();}
        public void frame(int age,VfxRuntime.Canvas c){
            double p=inward.at(age),release=outward.at(age);
            boolean pause=age>=29&&age<36;
            if(age<32||age>=36)for(int i=0;i<7;i++){
                double motionAge=pause?29:age;
                double a=angle[i]+spin[i]*motionAge*(age<36?1:1-release*.5);
                double r=age<36?radius[i]*(1-.92*p):.2+radius[i]*release*1.2;
                V center=new V(Math.cos(a)*r,height[i]*(age<36?1-p:release)+.3,6+Math.sin(a)*r*.72);
                double scale=age<36?1-.55*p:.4+release*.7;
                var shape=polygons.get(i);List<V> points=new ArrayList<>();
                for(V vertex:shape)points.add(VfxMath.rotate(vertex.scale(new V(scale,scale*.8,scale)),new V(1,.4,.2),angle[i]+motionAge*.09).add(center));
                points.add(points.getFirst());
                c.polyline(Layer.OUTLINE,Paint.fade(age<36?0xC9B7F4:0xF0E5FF,0x2B243F,.65f),points,4);
                if(age<29&&i%2==0)c.line(Layer.DECORATION,Paint.dust(0x78648D,.45f),center,center.lerp(new V(0,.3,6),.15),4);
            }
            if(age<29){
                double s=.55*(1-p);V center=new V(0,.3,6);
                V[] corners={new V(-s,-s,0),new V(s,-s,0),new V(s,s,0),new V(-s,s,0)};
                for(int i=0;i<4;i++)c.line(Layer.BODY,Paint.dust(0xEEE7FF,.65f),center.add(corners[i]),center.add(corners[(i+1)%4].mul(.85)),7);
            }
            if(age>=36&&age<45){double spread=.2+release*3.8;
                c.line(Layer.IMPACT,Paint.fade(0xF1E9FF,0x544365,.75f),new V(-spread,-.2,6-spread*.4),new V(spread,.8,6+spread*.4),20);
            }
            if(age==0)c.sound(Layer.OUTLINE,"block.portal.ambient",.3f,.55f);
            if(age==36)c.soundAt(Layer.IMPACT,new V(0,.3,6),"block.amethyst_block.break",.6f,.65f);
        }
    }
}

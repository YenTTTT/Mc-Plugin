import com.customrpg.talents.DarkSkill;
import com.customrpg.vfx.*;
import com.customrpg.vfx.VfxMath.V;
import com.customrpg.vfx.VfxRuntime.*;
import java.util.*;
import java.nio.file.*;

/** Headless Java runtime counters only: no Paper world, packets, client drawing, FPS or TPS. */
public final class DarkLoadProbe {
    static final UUID WORLD=new UUID(0,1);
    static final class Scene implements Transport {
        final List<Viewer> viewers=new ArrayList<>();
        Scene(int count){for(int i=0;i<count;i++)viewers.add(new Viewer(new UUID(2,i),WORLD,new V(i%4,1.6,i/4),Quality.HIGH));}
        public boolean valid(Pose pose){return true;}
        public List<Viewer> viewers(Pose pose,Mode mode,double distance){return viewers;}
        public boolean particle(UUID viewer,UUID world,V at,Paint paint){return true;}
        public boolean sound(UUID viewer,UUID world,V at,String sound,float volume,float pitch){return true;}
    }
    public static void main(String[] args)throws Exception {
        var rows=new ArrayList<String>();rows.add("scenario,requested,admitted,peak_instances,peak_samples,peak_sends,peak_delivery_checks,runtime_cpu_ms,active_after");
        for(int population:new int[]{1,32,64}) {
            var scene=new Scene(population);var runtime=new VfxRuntime(scene,Limits.defaults(),()->System.nanoTime()/1_000_000);
            int admitted=0,peakInstances=0,samples=0,sends=0,checks=0;long cpu=0;
            int length=population==1?8*150:150;
            for(int tick=0;tick<length;tick++) {
                if(population==1&&tick%150==0) {
                    var skill=DarkSkill.values()[tick/150];if(runtime.play(skill.id,new Pose(new UUID(1,0),WORLD,V.ZERO,V.FORWARD,0,null),Mode.COMBAT,DarkVfx.preview(skill.id,42),42,false,0,40)!=null)admitted++;
                } else if(population>1&&tick==0)for(int caster=0;caster<population;caster++) {
                    var skill=DarkSkill.values()[caster%8];if(runtime.play(skill.id,new Pose(new UUID(1,caster),WORLD,V.ZERO,V.FORWARD,0,null),Mode.COMBAT,DarkVfx.preview(skill.id,42+caster),42+caster,false,0,40)!=null)admitted++;
                }
                peakInstances=Math.max(peakInstances,runtime.stats().active());runtime.tick();var stats=runtime.stats();
                samples=Math.max(samples,stats.sampled());sends=Math.max(sends,stats.sent());checks=Math.max(checks,stats.deliveryChecks());cpu+=stats.frameNanos();
                if(stats.sampled()>8192||stats.sent()>4000||stats.deliveryChecks()>16000)throw new AssertionError("Budget exceeded");
            }
            rows.add(String.format(Locale.ROOT,"%s,%d,%d,%d,%d,%d,%d,%.3f,%d",population==1?"single_each_skill":"simultaneous_"+population,population==1?8:population,admitted,peakInstances,samples,sends,checks,cpu/1_000_000.0,runtime.stats().active()));
            if(runtime.stats().active()!=0)throw new AssertionError("Leaked animation instance");runtime.shutdown();
        }
        Path output=Path.of("target/dark-vfx-load-report.csv");Files.write(output,rows);rows.forEach(System.out::println);
    }
}

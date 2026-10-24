package com.customrpg.vfx;

import com.customrpg.vfx.VfxRuntime.*;
import com.customrpg.vfx.VfxMath.V;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Counts and CPU time for a headless transport, never an FPS/TPS or network benchmark. */
class VfxLoadTest {
    @Test void recordNormalAndDenseTransportLoadsWithoutViolatingBudgets() throws Exception {
        List<String> report=new ArrayList<>(List.of("mode,casters,viewers,ticks,admitted,rejected,max_active,max_samples,max_sends,max_work,mean_runtime_ms,max_runtime_ms,total_sends"));
        for(int population:new int[]{1,32}){
            var scene=new VfxRuntimeTest.Scene(){
                public boolean particle(UUID viewer,UUID world,V at,Paint paint){return true;}
                public boolean sound(UUID viewer,UUID world,V at,String sound,float volume,float pitch){return true;}
            };
            scene.audience.clear();for(int i=0;i<population;i++)scene.audience.add(new Viewer(UUID.nameUUIDFromBytes(("viewer"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)),VfxRuntimeTest.WORLD,new V(i%4,0,i/4),Quality.HIGH));
            var runtime=new VfxRuntime(scene,Limits.defaults(),()->0);List<UUID> owners=new ArrayList<>();for(int i=0;i<population;i++)owners.add(UUID.randomUUID());
            int admitted=0,rejected=0,maxActive=0,maxSamples=0,maxSends=0,maxWork=0;long totalNanos=0,maxNanos=0;int ticks=160;
            String[] skills={VfxEffects.SLASH,VfxEffects.THUNDER,VfxEffects.VOID};
            for(int tick=0;tick<ticks;tick++){
                if(tick%20==0)for(int i=0;i<population;i++){
                    String id=skills[(i+tick/20)%3];var pose=new Pose(owners.get(i),VfxRuntimeTest.WORLD,V.ZERO,V.FORWARD,0,null);
                    if(runtime.play(id,pose,Mode.COMBAT,VfxEffects.create(id,42+i),42+i,false,0,20)!=null)admitted++;else rejected++;
                }
                runtime.tick();var stats=runtime.stats();
                maxActive=Math.max(maxActive,stats.active());maxSamples=Math.max(maxSamples,stats.sampled());maxSends=Math.max(maxSends,stats.sent());maxWork=Math.max(maxWork,stats.deliveryChecks());
                totalNanos+=stats.frameNanos();maxNanos=Math.max(maxNanos,stats.frameNanos());
                assertTrue(stats.active()<=64);assertTrue(stats.sampled()<=8192);assertTrue(stats.sent()<=4000);assertTrue(stats.deliveryChecks()<=16000);
            }
            runtime.clear(null);assertEquals(0,runtime.stats().active());
            report.add(String.format(Locale.ROOT,"HEADLESS_COUNTS_NOT_TPS,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.6f,%.6f,%d",population,population,ticks,admitted,rejected,maxActive,maxSamples,maxSends,maxWork,totalNanos/(ticks*1_000_000.0),maxNanos/1_000_000.0,runtime.stats().totalSent()));
        }
        Files.createDirectories(Path.of("target"));Files.write(Path.of("target/vfx-load-report.csv"),report);
        System.out.println("VFX headless counts and local CPU report: target/vfx-load-report.csv (not server TPS/FPS/network measurements)");
    }
}

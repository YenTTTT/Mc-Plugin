package com.customrpg.managers;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DarkBrandsTest {
    @Test void thirdLayerOnlyArmsAndTheNextDirectHitMayConsume(){var b=new DarkBrands();var p=UUID.randomUUID();var t=UUID.randomUUID();for(int i=0;i<3;i++)b.add(p,t,i);assertEquals(3,b.layers(p,t,3));assertTrue(b.ready(p,t,3));assertTrue(b.consume(p,t,3));assertEquals(0,b.layers(p,t,3));assertFalse(b.ready(p,t,3));}
    @Test void immunityIsSharedByVictimWhileStacksBelongToTheirCaster(){var b=new DarkBrands();var a=UUID.randomUUID();var c=UUID.randomUUID();var target=UUID.randomUUID();for(int i=0;i<3;i++){b.add(a,target,0);b.add(c,target,0);}assertTrue(b.consume(a,target,0));assertFalse(b.ready(c,target,100));for(int i=0;i<3;i++)b.add(c,target,150);assertTrue(b.ready(c,target,200));}
    @Test void sevenSecondExpiryRefreshAndOwnerTargetCleanupAreExact(){var b=new DarkBrands();var p=UUID.randomUUID();var t=UUID.randomUUID();b.add(p,t,0);b.add(p,t,100);assertEquals(2,b.layers(p,t,239));assertEquals(0,b.layers(p,t,240));b.add(p,t,250);b.owner(p);assertEquals(0,b.layers(p,t,250));b.add(p,t,260);b.target(t);assertEquals(0,b.layers(p,t,260));b.clear();}
}

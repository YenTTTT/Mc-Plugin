package com.customrpg.managers;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EarthMechanicsTest {
    @Test void resilienceRefreshesEightSecondsCapsAtThreeAndRetainsTwoSecondGainInterval(){var r=new EarthResilience();var id=UUID.randomUUID();assertTrue(r.gain(id,0));assertFalse(r.gain(id,39));assertTrue(r.gain(id,40));assertTrue(r.gain(id,80));assertEquals(3,r.stacks(id,200));assertTrue(r.consume(id,200));assertFalse(r.consume(id,200));assertTrue(r.gain(id,201));assertEquals(0,r.stacks(id,361));r.clear();}
    @Test void absorptionExpiryNeverRestoresSpentShieldsOrDeletesExternalAbsorption(){var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());var value=new double[]{6};var capacity=new double[]{0};var access=new EarthShields.Access(){public double amount(Player x){return value[0];}public void amount(Player x,double n){value[0]=n;}public void capacity(Player x,double n){capacity[0]=n;}};var s=new EarthShields(access);var owner=UUID.randomUUID();s.grant(p,owner,"rush",4,80);assertEquals(10,value[0]);value[0]-=3;s.tick(1);s.tick(80);assertEquals(6,value[0]);assertEquals(0,capacity[0]);s.grant(p,owner,"rush",4,160);value[0]=5;s.tick(100);s.tick(160);assertEquals(5,value[0]);}
    @Test void differentCasterShieldsCleanSeparatelyAndRefreshingOneDoesNotMultiplyIt(){var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());var v=new double[]{0};var s=new EarthShields(new EarthShields.Access(){public double amount(Player x){return v[0];}public void amount(Player x,double n){v[0]=n;}public void capacity(Player x,double n){}});var a=UUID.randomUUID();var b=UUID.randomUUID();s.grant(p,a,"rush",4,80);s.grant(p,a,"rush",4,90);assertEquals(4,v[0]);s.grant(p,b,"rush",4,80);assertEquals(8,v[0]);s.owner(a);assertEquals(4,v[0]);s.clear();assertEquals(0,v[0]);}
}

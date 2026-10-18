package com.customrpg.listeners;
import com.customrpg.CustomRPG;
import com.customrpg.races.*;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RaceListenerTest {
    RaceManager races; RaceListener listener; Player player; AbstractArrow arrow; Map<NamespacedKey,Object> tags;
    @BeforeEach void setup() {
        races=mock(RaceManager.class); listener=new RaceListener(mock(CustomRPG.class),races); player=mock(Player.class); arrow=mock(AbstractArrow.class);
        tags=new HashMap<>(); var data=mock(PersistentDataContainer.class); when(arrow.getPersistentDataContainer()).thenReturn(data);
        doAnswer(i->{tags.put(i.getArgument(0),i.getArgument(2)); return null;}).when(data).set(any(),any(),any());
        when(data.get(any(NamespacedKey.class),eq(PersistentDataType.DOUBLE))).thenAnswer(i->tags.get(i.getArgument(0)));
    }
    void shoot(double chance) {
        var race=new RaceData.Builder("archer").weaponBonuses(Map.of("BOW",1.25)).bonusCritChance(chance).bonusCritDamage(0.2).build();
        when(races.getPlayerRaceData(player)).thenReturn(race);
        var shot=mock(EntityShootBowEvent.class); when(shot.getEntity()).thenReturn(player); when(shot.getProjectile()).thenReturn(arrow); listener.onBowShot(shot);
    }
    @Test void arrowRetainsLaunchRaceAffinityAfterRaceOrWeaponChanges() {
        shoot(0); when(races.getPlayerRaceData(player)).thenReturn(new RaceData.Builder("other").build());
        var hit=mock(EntityDamageByEntityEvent.class); when(hit.getDamager()).thenReturn(arrow); when(hit.getDamage()).thenReturn(10.0);
        listener.onArrowDamage(hit); verify(hit).setDamage(12.5);
        verify(races,times(1)).getPlayerRaceData(player);
    }
    @Test void racialCriticalIsAppliedToArrowDamage() {
        shoot(100); var hit=mock(EntityDamageByEntityEvent.class); when(hit.getDamager()).thenReturn(arrow); when(hit.getDamage()).thenReturn(10.0);
        listener.onArrowDamage(hit); var damage=org.mockito.ArgumentCaptor.forClass(Double.class); verify(hit).setDamage(damage.capture()); assertEquals(21.25,damage.getValue(),1e-9);
    }
    @Test void untaggedAndInvalidArrowMultipliersDoNotModifyDamage() {
        var hit=mock(EntityDamageByEntityEvent.class); when(hit.getDamager()).thenReturn(arrow);
        listener.onArrowDamage(hit); tags.put(new NamespacedKey("customrpg","race_bow_multiplier"),Double.NaN); listener.onArrowDamage(hit);
        verify(hit,never()).setDamage(anyDouble());
    }
}

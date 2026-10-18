package com.customrpg.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistanceZoneTest {
    @Test void interpolatesAndClampsWithoutDependingOnHeightOrPlayerLevel() {
        DistanceZone zone = new DistanceZone("starter", "Starter", 150, 700, 1, 10);
        assertEquals(1, zone.baseLevel(150));
        assertEquals(6, zone.baseLevel(425));
        assertEquals(10, zone.baseLevel(700));
        assertEquals(1, zone.baseLevel(-100));
        assertEquals(10, zone.baseLevel(50_000));
        assertEquals(1, zone.clampLevel(-10));
        assertEquals(10, zone.clampLevel(100));
    }

    @Test void rejectsInvalidZones() {
        assertThrows(IllegalArgumentException.class, () -> new DistanceZone("x", "x", 150, 150, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> new DistanceZone("x", "x", 0, Double.NaN, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> new DistanceZone("x", "x", 0, 100, 10, 1));
    }
}

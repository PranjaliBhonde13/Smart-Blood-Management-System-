package com.sben.common.util;

import com.sben.common.util.GeoUtils.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the Haversine distance and bounding-box prefilter maths. */
class GeoUtilsTest {

    @Test
    void haversineIsZeroForSamePoint() {
        assertEquals(0.0, GeoUtils.haversineKm(18.52, 73.85, 18.52, 73.85), 0.0001);
    }

    @Test
    void haversinePuneToMumbaiIsAboutRealDistance() {
        // Pune (18.5204, 73.8567) ↔ Mumbai (19.0760, 72.8777) ≈ 118 km.
        double d = GeoUtils.haversineKm(18.5204, 73.8567, 19.0760, 72.8777);
        assertTrue(d > 110 && d < 125, "expected ~118 km but was " + d);
    }

    @Test
    void boundingBoxContainsCentreAndHasPositiveSpan() {
        BoundingBox box = GeoUtils.boundingBox(18.52, 73.85, 10);
        assertTrue(box.minLat() < 18.52 && box.maxLat() > 18.52);
        assertTrue(box.minLon() < 73.85 && box.maxLon() > 73.85);
    }

    @Test
    void largerRadiusProducesWiderBox() {
        BoundingBox small = GeoUtils.boundingBox(18.52, 73.85, 5);
        BoundingBox large = GeoUtils.boundingBox(18.52, 73.85, 50);
        assertTrue((large.maxLat() - large.minLat()) > (small.maxLat() - small.minLat()));
    }
}

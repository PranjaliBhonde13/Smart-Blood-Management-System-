package com.sben.common.util;

import java.math.BigDecimal;

/**
 * Geospatial helpers for radius search.
 *
 * <p>Strategy (see docs/03-DATABASE.md §5): compute a lat/long bounding box to
 * prefilter rows using the indexed columns cheaply, then refine with the exact
 * Haversine great-circle distance. This avoids a full-table scan while staying
 * accurate.
 */
public final class GeoUtils {

    private GeoUtils() {}

    private static final double EARTH_RADIUS_KM = 6371.0;

    /** Great-circle distance in kilometres between two coordinates. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static double haversineKm(BigDecimal lat1, BigDecimal lon1, double lat2, double lon2) {
        return haversineKm(lat1.doubleValue(), lon1.doubleValue(), lat2, lon2);
    }

    /** A square bounding box (in degrees) around a point for a given radius. */
    public static BoundingBox boundingBox(double lat, double lon, double radiusKm) {
        double latDelta = Math.toDegrees(radiusKm / EARTH_RADIUS_KM);
        double lonDelta = Math.toDegrees(radiusKm / (EARTH_RADIUS_KM * Math.cos(Math.toRadians(lat))));
        return new BoundingBox(lat - latDelta, lat + latDelta, lon - lonDelta, lon + lonDelta);
    }

    /** Inclusive lat/long bounds used in the prefilter query. */
    public record BoundingBox(double minLat, double maxLat, double minLon, double maxLon) {}
}

package com.example.hackathon.utils;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

/**
 * Shared geographic helpers for route matching and navigation ETA math.
 */
public final class GeoUtils {

    public static final double EARTH_RADIUS_METERS = 6_371_000.0;
    /** How close a report must be to a path segment to count as "on route". */
    public static final double ON_ROUTE_RADIUS_METERS = 25.0;
    /** Walking speed used for ETA estimates (km/h). */
    public static final double WALK_KMH = 4.5;

    private GeoUtils() {
    }

    public static double distanceMeters(LatLng a, LatLng b) {
        return haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude);
    }

    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    public static double distanceToSegmentMeters(
            double pLat, double pLng,
            double aLat, double aLng,
            double bLat, double bLng
    ) {
        double abLat = bLat - aLat;
        double abLng = bLng - aLng;
        double apLat = pLat - aLat;
        double apLng = pLng - aLng;
        double abLenSq = abLat * abLat + abLng * abLng;
        double t = abLenSq == 0 ? 0 : (apLat * abLat + apLng * abLng) / abLenSq;
        t = Math.max(0, Math.min(1, t));
        return haversineMeters(pLat, pLng, aLat + t * abLat, aLng + t * abLng);
    }

    public static boolean isNearPath(double lat, double lng, List<LatLng> path, double radiusMeters) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.size() == 1) {
            return distanceMeters(path.get(0), new LatLng(lat, lng)) <= radiusMeters;
        }
        for (int i = 0; i < path.size() - 1; i++) {
            LatLng a = path.get(i);
            LatLng b = path.get(i + 1);
            if (distanceToSegmentMeters(lat, lng, a.latitude, a.longitude, b.latitude, b.longitude)
                    <= radiusMeters) {
                return true;
            }
        }
        return false;
    }

    public static int walkMinutesForMeters(double meters) {
        int minutes = (int) Math.round((meters / 1000.0) / WALK_KMH * 60.0);
        return Math.max(1, minutes);
    }

    public static int walkSecondsForMeters(double meters) {
        return Math.max(60, (int) Math.round((meters / 1000.0) / WALK_KMH * 3600.0));
    }

    /** Formats minutes as "24 min", "1 hr 5 min", or "3 hrs". */
    public static String formatDurationMinutes(int totalMinutes) {
        int minutes = Math.max(0, totalMinutes);
        if (minutes < 60) {
            return minutes + " min";
        }
        int hours = minutes / 60;
        int rem = minutes % 60;
        if (rem == 0) {
            return hours + (hours == 1 ? " hr" : " hrs");
        }
        return hours + (hours == 1 ? " hr " : " hrs ") + rem + " min";
    }

    public static String formatMeters(int meters) {
        if (meters >= 1000) {
            return String.format(java.util.Locale.US, "%.1f km", meters / 1000.0);
        }
        return meters + " m";
    }
}

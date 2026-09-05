package com.example.hackathon;

import com.example.hackathon.models.AccessibilityReport;

import java.util.List;

/**
 * Matches community obstacle reports against candidate routes and scores
 * each route based on which reports fall near its path. This replaces the
 * old Firestore/Obstacle-based scoring — it now reads directly from
 * ObstacleReportStore's live report data.
 */
public class RouteAccessibilityAnalyzer {

    // How close (in meters) a report must be to a route's path to count
    // as "on" that route. Tune based on GPS/report accuracy during testing.
    private static final double MATCH_RADIUS_METERS = 25.0;

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    /**
     * Scores every route by checking each report against every segment of
     * the route's polyline. Mutates the RouteOption objects in place
     * (attaches matched reports + final score) and returns the same list,
     * sorted best-first.
     */
    public List<RouteOption> analyze(List<RouteOption> routes, List<AccessibilityReport> reports) {
        for (RouteOption route : routes) {
            int score = 100;
            for (AccessibilityReport report : reports) {
                if (isReportOnRoute(report, route)) {
                    route.addReport(report);
                    score -= report.penaltyPoints();
                }
            }
            route.setAccessibilityScore(Math.max(score, 0));
        }

        routes.sort((a, b) -> {
            if (a.hasBlocker() != b.hasBlocker()) {
                return a.hasBlocker() ? 1 : -1;
            }
            return Integer.compare(b.getAccessibilityScore(), a.getAccessibilityScore());
        });

        return routes;
    }

    private boolean isReportOnRoute(AccessibilityReport report, RouteOption route) {
        // Reports with no real coordinates (legacy data) can't be matched.
        if (report.getLat() == 0 && report.getLng() == 0) {
            return false;
        }
        List<double[]> points = route.getPoints();
        for (int i = 0; i < points.size() - 1; i++) {
            double dist = distanceToSegmentMeters(
                    report.getLat(), report.getLng(),
                    points.get(i)[0], points.get(i)[1],
                    points.get(i + 1)[0], points.get(i + 1)[1]);
            if (dist <= MATCH_RADIUS_METERS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Approximate shortest distance from a point to a line segment, treating
     * lat/lng as locally flat (fine at city scale) then converting to meters
     * via the haversine formula for the final distance.
     */
    private double distanceToSegmentMeters(double pLat, double pLng,
                                           double aLat, double aLng,
                                           double bLat, double bLng) {
        double abLat = bLat - aLat;
        double abLng = bLng - aLng;
        double apLat = pLat - aLat;
        double apLng = pLng - aLng;

        double abLenSq = abLat * abLat + abLng * abLng;
        double t = abLenSq == 0 ? 0 : (apLat * abLat + apLng * abLng) / abLenSq;
        t = Math.max(0, Math.min(1, t));

        double closestLat = aLat + t * abLat;
        double closestLng = aLng + t * abLng;

        return haversineMeters(pLat, pLng, closestLat, closestLng);
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
package com.example.hackathon;

import java.util.List;

/**
 * Matches known obstacles against candidate routes and scores each route
 * based on which obstacles fall near its path. This is the layer that
 * compensates for the Directions API having no wheelchair-aware walking
 * mode: we take Google's plain walking routes and evaluate them ourselves.
 */
public class RouteAccessibilityAnalyzer {

    // How close (in meters) an obstacle must be to a route's path to count
    // as "on" that route. Tune based on GPS/report accuracy during testing.
    private static final double MATCH_RADIUS_METERS = 25.0;

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    /**
     * Scores every route by checking each obstacle against every segment of
     * the route's polyline. Mutates the RouteOption objects in place
     * (attaches matched obstacles + final score) and returns the same list,
     * sorted best-first.
     */
    public List<com.example.hackathon.RouteOption> analyze(List<RouteOption> routes, List<Obstacle> obstacles) {
        for (RouteOption route : routes) {
            int score = 100;
            for (Obstacle obstacle : obstacles) {
                if (isObstacleOnRoute(obstacle, route)) {
                    route.addObstacle(obstacle);
                    score -= obstacle.penaltyPoints();
                }
            }
            route.setAccessibilityScore(Math.max(score, 0));
        }

        routes.sort((a, b) -> {
            // Routes with a hard blocker always rank below routes without one.
            if (a.hasBlocker() != b.hasBlocker()) {
                return a.hasBlocker() ? 1 : -1;
            }
            return Integer.compare(b.getAccessibilityScore(), a.getAccessibilityScore());
        });

        return routes;
    }

    private boolean isObstacleOnRoute(Obstacle obstacle, RouteOption route) {
        List<double[]> points = route.getPoints();
        for (int i = 0; i < points.size() - 1; i++) {
            double dist = distanceToSegmentMeters(
                    obstacle.getLat(), obstacle.getLng(),
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
        // Project onto the segment using simple planar approximation.
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
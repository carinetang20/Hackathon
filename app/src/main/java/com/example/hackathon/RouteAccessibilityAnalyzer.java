package com.example.hackathon;

import com.example.hackathon.models.AccessibilityReport;
import com.example.hackathon.utils.GeoUtils;

import java.util.List;

/**
 * Matches community obstacle reports against candidate routes and scores
 * each route based on which reports fall near its path.
 */
public class RouteAccessibilityAnalyzer {

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
        if (report.getLat() == 0 && report.getLng() == 0) {
            return false;
        }
        List<double[]> points = route.getPoints();
        for (int i = 0; i < points.size() - 1; i++) {
            double dist = GeoUtils.distanceToSegmentMeters(
                    report.getLat(), report.getLng(),
                    points.get(i)[0], points.get(i)[1],
                    points.get(i + 1)[0], points.get(i + 1)[1]);
            if (dist <= GeoUtils.ON_ROUTE_RADIUS_METERS) {
                return true;
            }
        }
        return false;
    }
}

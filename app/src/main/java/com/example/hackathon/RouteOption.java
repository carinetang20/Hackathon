package com.example.hackathon;

import java.util.ArrayList;
import java.util.List;

/**
 * A single candidate walking route returned by the Directions API,
 * decoded into a list of lat/lng points. Accessibility scoring and
 * obstacle matching are attached after the route is fetched.
 */
public class RouteOption {

    private final List<double[]> points; // each entry is {lat, lng}
    private final String summary;
    private final int durationSeconds;
    private final int distanceMeters;

    private final List<Obstacle> obstaclesOnRoute = new ArrayList<>();
    private int accessibilityScore = 100;

    public RouteOption(List<double[]> points, String summary, int durationSeconds, int distanceMeters) {
        this.points = points;
        this.summary = summary;
        this.durationSeconds = durationSeconds;
        this.distanceMeters = distanceMeters;
    }

    public List<double[]> getPoints() {
        return points;
    }

    public String getSummary() {
        return summary;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getDistanceMeters() {
        return distanceMeters;
    }

    public List<Obstacle> getObstaclesOnRoute() {
        return obstaclesOnRoute;
    }

    public void addObstacle(Obstacle obstacle) {
        obstaclesOnRoute.add(obstacle);
    }

    public int getAccessibilityScore() {
        return accessibilityScore;
    }

    public void setAccessibilityScore(int accessibilityScore) {
        this.accessibilityScore = accessibilityScore;
    }

    public boolean hasBlocker() {
        for (Obstacle o : obstaclesOnRoute) {
            if (Obstacle.SEVERITY_BLOCKER.equals(o.getSeverity())) {
                return true;
            }
        }
        return false;
    }
}
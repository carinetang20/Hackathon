package com.example.hackathon.utils;

import com.google.android.gms.maps.model.LatLng;

/**
 * Known MMU Cyberjaya campus pathway landmarks for scan guidance.
 */
public final class CampusLocator {

    public static final LatLng CAMPUS_CENTER = new LatLng(2.9213, 101.6559);

    public static final class Place {
        public final String name;
        public final String pathwayHint;
        public final LatLng position;

        public Place(String name, String pathwayHint, LatLng position) {
            this.name = name;
            this.pathwayHint = pathwayHint;
            this.position = position;
        }
    }

    private static final Place[] PLACES = {
            new Place(
                    "Campus Library",
                    "walkway toward the library entrance, watch for the ramp and stairs",
                    new LatLng(2.9209, 101.6550)
            ),
            new Place(
                    "Dewan Tun Canselor",
                    "main plaza walkway, stairs may be nearby",
                    new LatLng(2.9220, 101.6552)
            ),
            new Place(
                    "STAD Building",
                    "faculty walkway beside STAD, keep to the paved path",
                    new LatLng(2.9206, 101.6565)
            ),
            new Place(
                    "MMU Garden",
                    "garden pathway, uneven ground and plant obstacles possible",
                    new LatLng(2.9218, 101.6568)
            ),
            new Place(
                    "Institute for Postgraduate Studies",
                    "covered walkway, watch for blocked tactile paving",
                    new LatLng(2.9210, 101.6560)
            ),
            new Place(
                    "Persiaran Newron",
                    "campus road-edge sidewalk, listen before crossing",
                    new LatLng(2.9215, 101.6545)
            )
    };

    private CampusLocator() { }

    public static boolean isOnCampus(double lat, double lng) {
        return distanceMeters(lat, lng, CAMPUS_CENTER.latitude, CAMPUS_CENTER.longitude) <= 900;
    }

    public static Place nearestPlace(double lat, double lng) {
        Place best = PLACES[0];
        double bestDist = Double.MAX_VALUE;
        for (Place place : PLACES) {
            double d = distanceMeters(lat, lng, place.position.latitude, place.position.longitude);
            if (d < bestDist) {
                bestDist = d;
                best = place;
            }
        }
        // Only claim a specific place if reasonably close (~120m)
        if (bestDist <= 120) {
            return best;
        }
        return null;
    }

    public static String campusAreaDescription(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return "MMU Cyberjaya campus";
        }
        if (!isOnCampus(lat, lng)) {
            return "near campus area";
        }
        Place nearest = nearestPlace(lat, lng);
        if (nearest != null) {
            return "near " + nearest.name;
        }
        return "on MMU Cyberjaya campus";
    }

    public static String pathwayHint(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return "outdoor campus walkway or stairs";
        }
        Place nearest = nearestPlace(lat, lng);
        if (nearest != null) {
            return nearest.pathwayHint;
        }
        return "outdoor campus walkway or stairs";
    }

    private static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double earth = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earth * c;
    }
}

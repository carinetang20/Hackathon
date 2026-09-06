package com.example.hackathon;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal client for the Google Maps Directions API (walking mode, with
 * alternatives). Uses plain HttpURLConnection so there's no extra Gradle
 * dependency beyond org.json, which ships with Android.
 *
 * NOTE: the Directions API's walking mode has no wheelchair/step-free
 * parameter. This client only fetches candidate routes; obstacle-aware
 * filtering happens afterwards in RouteAccessibilityAnalyzer.
 */
public class DirectionsApiClient {

    public interface RoutesCallback {
        void onSuccess(List<RouteOption> routes);
        void onError(Exception e);
    }

    private final String apiKey;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DirectionsApiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Fetches walking routes between two coordinates.
     */
    public void fetchWalkingRoutes(double originLat, double originLng,
                                   double destLat, double destLng,
                                   RoutesCallback callback) {
        executor.execute(() -> {
            try {
                List<RouteOption> routes = fetchWalkingRoutesSync(originLat, originLng, destLat, destLng);
                mainHandler.post(() -> callback.onSuccess(routes));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private List<RouteOption> fetchWalkingRoutesSync(double originLat, double originLng,
                                                     double destLat, double destLng) throws Exception {
        String origin = originLat + "," + originLng;
        String destination = destLat + "," + destLng;

        String urlStr = "https://maps.googleapis.com/maps/api/directions/json"
                + "?origin=" + URLEncoder.encode(origin, "UTF-8")
                + "&destination=" + URLEncoder.encode(destination, "UTF-8")
                + "&mode=walking"
                + "&alternatives=true"
                + "&key=" + apiKey;

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        java.io.InputStream inputStream = (responseCode >= 200 && responseCode < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        StringBuilder responseBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
        } finally {
            conn.disconnect();
        }

        return parseRoutes(responseBuilder.toString());
    }

    private List<RouteOption> parseRoutes(String json) throws Exception {
        List<RouteOption> result = new ArrayList<>();
        JSONObject root = new JSONObject(json);

        String status = root.optString("status", "UNKNOWN_ERROR");
        if (!"OK".equals(status)) {
            String errorMessage = root.optString("error_message", "(no additional error message)");
            throw new RuntimeException("Directions API returned status: " + status + " - " + errorMessage);
        }

        JSONArray routesArray = root.getJSONArray("routes");
        for (int i = 0; i < routesArray.length(); i++) {
            JSONObject routeObj = routesArray.getJSONObject(i);
            String summary = routeObj.optString("summary", "Route " + (i + 1));

            JSONArray legs = routeObj.getJSONArray("legs");
            int totalDuration = 0;
            int totalDistance = 0;
            List<RouteOption.Step> steps = new ArrayList<>();
            for (int j = 0; j < legs.length(); j++) {
                JSONObject leg = legs.getJSONObject(j);
                totalDuration += leg.getJSONObject("duration").getInt("value");
                totalDistance += leg.getJSONObject("distance").getInt("value");

                JSONArray stepArr = leg.optJSONArray("steps");
                if (stepArr != null) {
                    for (int s = 0; s < stepArr.length(); s++) {
                        JSONObject step = stepArr.getJSONObject(s);
                        String html = step.optString("html_instructions", "Continue");
                        String plain = html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
                        JSONObject start = step.getJSONObject("start_location");
                        steps.add(new RouteOption.Step(
                                plain,
                                step.optString("maneuver", ""),
                                step.getJSONObject("distance").getInt("value"),
                                step.getJSONObject("duration").getInt("value"),
                                start.getDouble("lat"),
                                start.getDouble("lng")
                        ));
                    }
                }
            }

            String encodedPolyline = routeObj.getJSONObject("overview_polyline").getString("points");
            List<double[]> points = decodePolyline(encodedPolyline);

            result.add(new RouteOption(points, summary, totalDuration, totalDistance, steps));
        }
        return result;
    }

    /**
     * Decodes Google's encoded polyline algorithm format into lat/lng points.
     * Standard implementation — see:
     * https://developers.google.com/maps/documentation/utilities/polylinealgorithm
     */
    private List<double[]> decodePolyline(String encoded) {
        List<double[]> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lng += dlng;

            poly.add(new double[]{lat / 1E5, lng / 1E5});
        }
        return poly;
    }
}
package com.example.hackathon;

import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 200;

    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationClient;
    private DirectionsApiClient directionsApiClient;
    private FirestoreAccessibilityRepository repository;
    private final RouteAccessibilityAnalyzer analyzer = new RouteAccessibilityAnalyzer();
    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();

    private EditText currentLocationInput;
    private EditText destinationInput;

    // Set once the user taps "use my location"; null means they're typing an address instead.
    private double[] currentUserLocation;

    private interface GeocodeCallback {
        void onGeocoded(double lat, double lng);
        void onError(String message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        currentLocationInput = findViewById(R.id.currentLocationInput);
        destinationInput = findViewById(R.id.destinationInput);
        ImageButton useMyLocationButton = findViewById(R.id.useMyLocationButton);
        ImageButton backButton = findViewById(R.id.backButton);
        Button findRouteButton = findViewById(R.id.findRouteButton);

        backButton.setOnClickListener(v -> finish());
        useMyLocationButton.setOnClickListener(v -> checkLocationPermissionAndFetch());
        findRouteButton.setOnClickListener(v -> onFindRouteClicked());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        directionsApiClient = new DirectionsApiClient(getGoogleMapsApiKey());
        repository = new FirestoreAccessibilityRepository();

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            map.setMyLocationEnabled(true);
        }

        // Default camera position so the map isn't blank before a route is searched.
        LatLng defaultLocation = new LatLng(2.9213, 101.6559);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12));
    }

    /**
     * Reads the same Maps API key already declared in AndroidManifest.xml's
     * com.google.android.geo.API_KEY meta-data, so it only needs to live in one place.
     */
    private String getGoogleMapsApiKey() {
        try {
            ApplicationInfo appInfo = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            return appInfo.metaData.getString("com.google.android.geo.API_KEY");
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        fetchCurrentLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE
                && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation();
            if (map != null && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                map.setMyLocationEnabled(true);
            }
        } else {
            Toast.makeText(this, "Location permission is needed to use your current location", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentUserLocation = new double[]{location.getLatitude(), location.getLongitude()};
                currentLocationInput.setText("Current Location");
                if (map != null) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(location.getLatitude(), location.getLongitude()), 15));
                }
            } else {
                Toast.makeText(this, "Could not get current location. Make sure GPS is enabled.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onFindRouteClicked() {
        String destinationText = destinationInput.getText().toString().trim();
        if (TextUtils.isEmpty(destinationText)) {
            Toast.makeText(this, "Please enter a destination", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUserLocation != null) {
            geocodeAddress(destinationText, new GeocodeCallback() {
                @Override
                public void onGeocoded(double destLat, double destLng) {
                    planRoute(currentUserLocation[0], currentUserLocation[1], destLat, destLng);
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MapActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        String originText = currentLocationInput.getText().toString().trim();
        if (TextUtils.isEmpty(originText)) {
            Toast.makeText(this, "Enter a starting location or tap the location icon", Toast.LENGTH_SHORT).show();
            return;
        }

        geocodeAddress(originText, new GeocodeCallback() {
            @Override
            public void onGeocoded(double originLat, double originLng) {
                geocodeAddress(destinationText, new GeocodeCallback() {
                    @Override
                    public void onGeocoded(double destLat, double destLng) {
                        planRoute(originLat, originLng, destLat, destLng);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(MapActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MapActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Converts a typed address into coordinates using Android's built-in Geocoder. */
    private void geocodeAddress(String address, GeocodeCallback callback) {
        geocodeExecutor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> results = geocoder.getFromLocationName(address, 1);
                if (results != null && !results.isEmpty()) {
                    Address result = results.get(0);
                    runOnUiThread(() -> callback.onGeocoded(result.getLatitude(), result.getLongitude()));
                } else {
                    runOnUiThread(() -> callback.onError("Could not find location: " + address));
                }
            } catch (IOException e) {
                runOnUiThread(() -> callback.onError("Geocoding failed. Check your internet connection."));
            }
        });
    }

    private void planRoute(double originLat, double originLng, double destLat, double destLng) {
        Toast.makeText(this, "Finding accessible route...", Toast.LENGTH_SHORT).show();

        directionsApiClient.fetchWalkingRoutes(originLat, originLng, destLat, destLng,
                new DirectionsApiClient.RoutesCallback() {
                    @Override
                    public void onSuccess(List<RouteOption> routes) {
                        double[] bounds = computeBounds(routes, originLat, originLng, destLat, destLng);

                        repository.getObstaclesInBounds(bounds[0], bounds[1], bounds[2], bounds[3],
                                new FirestoreAccessibilityRepository.ObstaclesCallback() {
                                    @Override
                                    public void onSuccess(List<Obstacle> obstacles) {
                                        List<RouteOption> ranked = analyzer.analyze(routes, obstacles);
                                        displayRoute(ranked.get(0), originLat, originLng, destLat, destLng);
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        // Firestore unavailable — still show the plain walking route.
                                        displayRoute(routes.get(0), originLat, originLng, destLat, destLng);
                                    }
                                });
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(MapActivity.this,
                                "Could not find a route: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private double[] computeBounds(List<RouteOption> routes,
                                   double originLat, double originLng,
                                   double destLat, double destLng) {
        double minLat = Math.min(originLat, destLat);
        double maxLat = Math.max(originLat, destLat);
        double minLng = Math.min(originLng, destLng);
        double maxLng = Math.max(originLng, destLng);

        for (RouteOption route : routes) {
            for (double[] point : route.getPoints()) {
                minLat = Math.min(minLat, point[0]);
                maxLat = Math.max(maxLat, point[0]);
                minLng = Math.min(minLng, point[1]);
                maxLng = Math.max(maxLng, point[1]);
            }
        }

        double margin = 0.01; // roughly 1km
        return new double[]{minLat - margin, maxLat + margin, minLng - margin, maxLng + margin};
    }

    private void displayRoute(RouteOption route,
                              double originLat, double originLng,
                              double destLat, double destLng) {
        if (map == null) {
            return;
        }
        map.clear();

        PolylineOptions polylineOptions = new PolylineOptions()
                .color(Color.parseColor("#2A6DF4"))
                .width(12f);

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (double[] point : route.getPoints()) {
            LatLng latLng = new LatLng(point[0], point[1]);
            polylineOptions.add(latLng);
            boundsBuilder.include(latLng);
        }
        map.addPolyline(polylineOptions);

        map.addMarker(new MarkerOptions()
                .position(new LatLng(originLat, originLng))
                .title("Start"));

        map.addMarker(new MarkerOptions()
                .position(new LatLng(destLat, destLng))
                .title("Destination")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        for (Obstacle obstacle : route.getObstaclesOnRoute()) {
            map.addMarker(new MarkerOptions()
                    .position(new LatLng(obstacle.getLat(), obstacle.getLng()))
                    .title(obstacle.getType())
                    .snippet(obstacle.getSeverity())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
        }

        map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120));

        String verdict = route.getAccessibilityScore() >= 60
                ? "Likely accessible (score: " + route.getAccessibilityScore() + "/100)"
                : "May be difficult for wheelchair users (score: " + route.getAccessibilityScore() + "/100)";
        Toast.makeText(this, verdict, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        geocodeExecutor.shutdown();
    }
}
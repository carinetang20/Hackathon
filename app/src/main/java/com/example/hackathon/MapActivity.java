package com.example.hackathon;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.hackathon.models.AccessibilityReport;
import com.example.hackathon.utils.ObstacleReportStore;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int REQUEST_LOCATION = 1001;
    private static final LatLng CAMPUS_CENTER = new LatLng(2.9213, 101.6559);

    private GoogleMap map;
    private final Map<Marker, String> markerReportIds = new HashMap<>();
    private final List<CampusPlace> campusPlaces = new ArrayList<>();

    private FusedLocationProviderClient fusedLocationClient;

    private EditText searchInput;
    private LinearLayout locationSheet;
    private TextView sheetTitle;
    private TextView sheetSubtitle;
    private MaterialButton reportObstacleButton;
    private MaterialButton viewReportsButton;
    private FloatingActionButton myLocationButton;

    private EditText currentLocationInput;
    private EditText destinationInput;
    private View searchPanel;
    private View routePanel;
    private MaterialButton directionsButton;

    private String selectedReportId;

    // Coordinates of whatever is currently shown in the bottom sheet, so
    // "Report obstruction" can attach a real location to the new report.
    private Double selectedLat;
    private Double selectedLng;

    // --- Route-finding additions ---
    private DirectionsApiClient directionsApiClient;
    private final RouteAccessibilityAnalyzer analyzer = new RouteAccessibilityAnalyzer();
    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();

    private Marker selectedLocationMarker;

    // Set once the user taps "use my location" for the ROUTE origin specifically.
    private double[] routeOriginLocation;
    private boolean pendingRouteOriginFetch = false;

    private interface GeocodeCallback {
        void onGeocoded(double lat, double lng);
        void onError(String message);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        currentLocationInput = findViewById(R.id.currentLocationInput);
        destinationInput = findViewById(R.id.destinationInput);
        searchPanel = findViewById(R.id.searchPanel);
        routePanel = findViewById(R.id.routePanel);
        directionsButton = findViewById(R.id.directionsButton);
        ImageButton backButton = findViewById(R.id.backButton);
        ImageButton findRouteButton = findViewById(R.id.findRouteButton);
        searchInput = findViewById(R.id.searchInput);
        locationSheet = findViewById(R.id.locationSheet);
        sheetTitle = findViewById(R.id.sheetTitle);
        sheetSubtitle = findViewById(R.id.sheetSubtitle);
        reportObstacleButton = findViewById(R.id.reportObstacleButton);
        viewReportsButton = findViewById(R.id.viewReportsButton);
        myLocationButton = findViewById(R.id.myLocationButton);

        directionsApiClient = new DirectionsApiClient(getGoogleMapsApiKey());

        // Keep the report list fresh as Firestore syncs, since obstacle pins
        // and route scoring both depend on it.
        ObstacleReportStore.getInstance(this).addListener(() -> {
            if (map != null) {
                showCampusAndObstacles();
            }
        });

        seedCampusPlaces();

        backButton.setOnClickListener(v -> {
            if (routePanel.getVisibility() == View.VISIBLE) {
                showSearchPanel();
            } else {
                finish();
            }
        });

        reportObstacleButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReportActivity.class);
            if (selectedLat != null && selectedLng != null) {
                intent.putExtra(ReportActivity.EXTRA_LAT, selectedLat);
                intent.putExtra(ReportActivity.EXTRA_LNG, selectedLng);
            }
            startActivity(intent);
        });

        viewReportsButton.setOnClickListener(v -> {
            if (selectedReportId != null) {
                Intent intent = new Intent(this, ReportDetailActivity.class);
                intent.putExtra(ReportDetailActivity.EXTRA_REPORT_ID, selectedReportId);
                startActivity(intent);
            } else {
                startActivity(new Intent(this, MyReportsActivity.class));
            }
        });

        myLocationButton.setOnClickListener(v -> centerOnUserOrCampus());

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSearch = actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (isSearch) {
                searchPlaces(searchInput.getText().toString());
                return true;
            }
            return false;
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.length() >= 2) {
                    searchPlaces(s.toString());
                }
            }
        });

        // --- Route-finding wiring ---
        directionsButton.setOnClickListener(v -> showRoutePanel());

        findRouteButton.setOnClickListener(v -> onFindRouteClicked());
        destinationInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSearch = actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (isSearch) {
                onFindRouteClicked();
                return true;
            }
            return false;
        });

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setMapToolbarEnabled(false);

        map.setOnMapClickListener(latLng -> {
            selectedReportId = null;
            selectedLat = latLng.latitude;
            selectedLng = latLng.longitude;

            if (selectedLocationMarker != null) {
                selectedLocationMarker.remove();
            }
            selectedLocationMarker = map.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("Selected location"));

            showSheet(
                    "Selected location",
                    String.format(Locale.US, "%.5f, %.5f · tap Report to warn others",
                            latLng.latitude, latLng.longitude)
            );
        });

        map.setOnMarkerClickListener(marker -> {
            String reportId = markerReportIds.get(marker);
            selectedReportId = reportId;
            selectedLat = marker.getPosition().latitude;
            selectedLng = marker.getPosition().longitude;

            if (reportId != null) {
                AccessibilityReport report = ObstacleReportStore.getInstance(this).getById(reportId);
                if (report != null) {
                    showSheet(
                            report.getIssueType(),
                            report.getLocationName() + " · " + report.getStatus()
                                    + " · Still there " + report.getStillThereCount()
                    );
                }
            } else {
                showSheet(marker.getTitle(), marker.getSnippet());
            }
            return false;
        });

        map.setOnInfoWindowClickListener(marker -> {
            String reportId = markerReportIds.get(marker);
            if (reportId != null) {
                Intent intent = new Intent(MapActivity.this, ReportDetailActivity.class);
                intent.putExtra(ReportDetailActivity.EXTRA_REPORT_ID, reportId);
                startActivity(intent);
            }
        });

        enableMyLocationIfPermitted();
        showCampusAndObstacles();
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(CAMPUS_CENTER, 16f));

        selectedLat = CAMPUS_CENTER.latitude;
        selectedLng = CAMPUS_CENTER.longitude;
        showSheet(
                "Multimedia University — MMU Cyberjaya",
                "Live campus map · red pins are community obstacle reports"
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            showCampusAndObstacles();
        }
    }

    private void showCampusAndObstacles() {
        map.clear();
        markerReportIds.clear();

        for (CampusPlace place : campusPlaces) {
            map.addMarker(new MarkerOptions()
                    .position(place.position)
                    .title(place.name)
                    .snippet(place.category)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        }

        List<AccessibilityReport> reports =
                ObstacleReportStore.getInstance(this).getActiveCommunityReports();

        int fallbackIndex = 0;
        for (AccessibilityReport report : reports) {
            LatLng position;
            if (report.getLat() != 0 || report.getLng() != 0) {
                // Real coordinates captured at report time.
                position = new LatLng(report.getLat(), report.getLng());
            } else {
                // Legacy report with no coordinates — scatter near campus
                // center so it's still visible rather than dropped silently.
                double latOffset = ((fallbackIndex % 3) - 1) * 0.0009;
                double lngOffset = ((fallbackIndex / 3) % 3 - 1) * 0.0009;
                position = new LatLng(
                        CAMPUS_CENTER.latitude + latOffset,
                        CAMPUS_CENTER.longitude + lngOffset
                );
                fallbackIndex++;
            }

            float hue = AccessibilityReport.STATUS_CONFIRMED.equals(report.getStatus())
                    ? BitmapDescriptorFactory.HUE_ORANGE
                    : BitmapDescriptorFactory.HUE_RED;

            Marker marker = map.addMarker(new MarkerOptions()
                    .position(position)
                    .title(report.getIssueType())
                    .snippet(report.getLocationName() + " · " + report.getStatus())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));

            if (marker != null) {
                markerReportIds.put(marker, report.getId());
            }
        }
    }

    private void seedCampusPlaces() {
        campusPlaces.clear();
        campusPlaces.add(new CampusPlace("MMU Cyberjaya", "University", CAMPUS_CENTER));
        campusPlaces.add(new CampusPlace(
                "Dewan Tun Canselor", "Hall",
                new LatLng(2.9220, 101.6552)));
        campusPlaces.add(new CampusPlace(
                "STAD Building", "Faculty",
                new LatLng(2.9206, 101.6565)));
        campusPlaces.add(new CampusPlace(
                "MMU Garden", "Landmark",
                new LatLng(2.9218, 101.6568)));
        campusPlaces.add(new CampusPlace(
                "Campus Library", "Facility",
                new LatLng(2.9209, 101.6550)));
    }

    private void searchPlaces(String query) {
        if (map == null || query == null) {
            return;
        }
        String q = query.trim().toLowerCase(Locale.US);
        if (q.isEmpty()) {
            return;
        }

        for (CampusPlace place : campusPlaces) {
            if (place.name.toLowerCase(Locale.US).contains(q)
                    || place.category.toLowerCase(Locale.US).contains(q)) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(place.position, 17f));
                selectedReportId = null;
                selectedLat = place.position.latitude;
                selectedLng = place.position.longitude;
                showSheet(place.name, place.category + " · MMU Cyberjaya");
                return;
            }
        }

        List<AccessibilityReport> reports =
                ObstacleReportStore.getInstance(this).getActiveCommunityReports();
        for (AccessibilityReport report : reports) {
            if (report.getLocationName().toLowerCase(Locale.US).contains(q)
                    || report.getIssueType().toLowerCase(Locale.US).contains(q)) {
                selectedReportId = report.getId();
                selectedLat = report.getLat();
                selectedLng = report.getLng();
                showSheet(
                        report.getIssueType(),
                        report.getLocationName() + " · " + report.getStatus()
                );
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(CAMPUS_CENTER, 16.5f));
                return;
            }
        }

        Toast.makeText(this, "No matching place on campus", Toast.LENGTH_SHORT).show();
    }

    private void showSheet(String title, String subtitle) {
        sheetTitle.setText(title);
        sheetSubtitle.setText(subtitle);
        locationSheet.setVisibility(View.VISIBLE);
    }

    private void enableMyLocationIfPermitted() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            map.setMyLocationEnabled(true);
            map.getUiSettings().setMyLocationButtonEnabled(false);
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION
            );
        }
    }

    private void centerOnUserOrCampus() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            enableMyLocationIfPermitted();
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(CAMPUS_CENTER, 16f));
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null && map != null) {
                        LatLng me = new LatLng(location.getLatitude(), location.getLongitude());
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 17f));
                        selectedLat = me.latitude;
                        selectedLng = me.longitude;
                        showSheet("Your location", "Centered on your current position");
                    } else if (map != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(CAMPUS_CENTER, 16f));
                        showSheet("MMU Cyberjaya", "Showing campus center");
                    }
                })
                .addOnFailureListener(e -> {
                    if (map != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(CAMPUS_CENTER, 16f));
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && map != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                map.setMyLocationEnabled(true);
            }
            if (pendingRouteOriginFetch) {
                pendingRouteOriginFetch = false;
                fetchRouteOriginLocation();
            }
        }
    }

    private static class CampusPlace {
        final String name;
        final String category;
        final LatLng position;

        CampusPlace(String name, String category, LatLng position) {
            this.name = name;
            this.category = category;
            this.position = position;
        }
    }

    // ================== Route-finding additions ==================

    private void showRoutePanel() {
        String prefillDestination = sheetTitle.getText() != null ? sheetTitle.getText().toString() : "";
        if (!TextUtils.isEmpty(prefillDestination)) {
            destinationInput.setText(prefillDestination);
        }
        hideSheet();
        searchPanel.setVisibility(View.GONE);
        routePanel.setVisibility(View.VISIBLE);

        if (routeOriginLocation == null
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchRouteOriginLocation();
        }
    }

    private void hideSheet() {
        locationSheet.setVisibility(View.GONE);
    }

    private void showSearchPanel() {
        routePanel.setVisibility(View.GONE);
        searchPanel.setVisibility(View.VISIBLE);
    }

    private String getGoogleMapsApiKey() {
        try {
            ApplicationInfo appInfo = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            return appInfo.metaData.getString("com.google.android.geo.API_KEY");
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void checkLocationPermissionAndFetchOrigin() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            pendingRouteOriginFetch = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION);
            return;
        }
        fetchRouteOriginLocation();
    }

    private void fetchRouteOriginLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                routeOriginLocation = new double[]{location.getLatitude(), location.getLongitude()};
                currentLocationInput.setText("Current Location");
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

        if (routeOriginLocation != null) {
            geocodeAddress(destinationText, new GeocodeCallback() {
                @Override
                public void onGeocoded(double destLat, double destLng) {
                    planRoute(routeOriginLocation[0], routeOriginLocation[1], destLat, destLng);
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

    /**
     * Scores candidate routes using live community reports from
     * ObstacleReportStore (Firestore-backed) instead of the old, separate
     * Firestore obstacle collection — so a reported obstacle actually
     * affects the routes people are shown.
     */
    private void planRoute(double originLat, double originLng, double destLat, double destLng) {
        Toast.makeText(this, "Finding accessible route...", Toast.LENGTH_SHORT).show();

        directionsApiClient.fetchWalkingRoutes(originLat, originLng, destLat, destLng,
                new DirectionsApiClient.RoutesCallback() {
                    @Override
                    public void onSuccess(List<RouteOption> routes) {
                        List<AccessibilityReport> reports =
                                ObstacleReportStore.getInstance(MapActivity.this).getActiveCommunityReports();
                        List<RouteOption> ranked = analyzer.analyze(routes, reports);
                        displayRoute(ranked.get(0), originLat, originLng, destLat, destLng);
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(MapActivity.this,
                                "Could not find a route: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Switches the map into "route mode": clears the campus/report markers and
     * draws the found route instead. Re-entering MapActivity (or clearing the
     * destination and searching campus places again) returns to browse mode
     * via showCampusAndObstacles().
     */
    private void displayRoute(RouteOption route,
                              double originLat, double originLng,
                              double destLat, double destLng) {
        if (map == null) {
            return;
        }
        map.clear();
        markerReportIds.clear();

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

        for (AccessibilityReport report : route.getReportsOnRoute()) {
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(new LatLng(report.getLat(), report.getLng()))
                    .title(report.getIssueType())
                    .snippet(report.getStatus())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
            if (marker != null) {
                markerReportIds.put(marker, report.getId());
            }
        }

        map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120));

        selectedReportId = null;
        selectedLat = destLat;
        selectedLng = destLng;
        String verdict = route.getAccessibilityScore() >= 60
                ? "Likely accessible (score: " + route.getAccessibilityScore() + "/100)"
                : "May be difficult for wheelchair users (score: " + route.getAccessibilityScore() + "/100)";
        showSheet("Route found", verdict);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        geocodeExecutor.shutdown();
    }
}
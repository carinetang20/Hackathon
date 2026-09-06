package com.example.hackathon;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.hackathon.models.AccessibilityReport;
import com.example.hackathon.utils.ObstacleReportStore;
import com.example.hackathon.utils.ReportTimeFormat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int REQUEST_LOCATION = 1001;
    private static final LatLng CAMPUS_CENTER = new LatLng(2.9213, 101.6559);
    /** Alert when an on-route obstacle is about ~1 min walk ahead (~80 m). */
    private static final double OBSTACLE_ALERT_METERS = 80.0;
    private static final double OBSTACLE_ON_ROUTE_METERS = 30.0;

    private GoogleMap map;
    private final Map<Marker, String> markerReportIds = new HashMap<>();
    private final List<CampusPlace> campusPlaces = new ArrayList<>();

    private FusedLocationProviderClient fusedLocationClient;

    private EditText searchInput;
    private LinearLayout locationSheet;
    private TextView sheetTitle;
    private TextView sheetSubtitle;
    private TextView sheetEtaText;
    private TextView sheetArriveText;
    private TextView sheetRouteMeta;
    private TextView modeWalkChip;
    private TextView modeDriveChip;
    private ImageView sheetPlacePhoto;
    private MaterialButton reportObstacleButton;
    private MaterialButton viewReportsButton;
    private MaterialButton startDirectionsButton;
    private FloatingActionButton myLocationButton;

    private EditText currentLocationInput;
    private EditText destinationInput;
    private View searchPanel;
    private View routePanel;
    private MaterialButton directionsButton;

    private String selectedReportId;
    private LatLng selectedDestination;

    // Navigation mode
    private View navigationOverlay;
    private TextView navTurnIcon;
    private TextView navStepDistance;
    private TextView navStepStreet;
    private TextView navThenText;
    private TextView navEtaText;
    private TextView navMetaText;
    private MaterialButton navExitButton;
    private View obstacleAlertCard;
    private TextView obstacleAlertMessage;
    private MaterialButton obstacleKeepRouteButton;
    private MaterialButton obstacleAvoidButton;
    private boolean navigating;
    private boolean obstaclePromptVisible;
    private RouteOption activeRoute;
    private List<LatLng> navigationPath = new ArrayList<>();
    private int navigationIndex;
    private int navigationStepIndex;
    private Marker navigationMarker;
    private Polyline navigationPolyline;
    private double navDestLat;
    private double navDestLng;
    private final List<Obstacle> journeyObstacles = new ArrayList<>();
    private final Set<String> dismissedObstacleAlerts = new HashSet<>();
    private Obstacle pendingObstacleAlert;
    private final Handler navigationHandler = new Handler(Looper.getMainLooper());
    private final Runnable navigationTick = new Runnable() {
        @Override
        public void run() {
            if (!navigating || obstaclePromptVisible) {
                return;
            }
            advanceNavigation();
            if (navigating && !obstaclePromptVisible) {
                navigationHandler.postDelayed(this, 1200);
            }
        }
    };



    // --- Route-finding additions ---
    private DirectionsApiClient directionsApiClient;
    private FirestoreAccessibilityRepository repository;
    private final RouteAccessibilityAnalyzer analyzer = new RouteAccessibilityAnalyzer();
    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();

    private Marker selectedLocationMarker;

    // Set once the user taps "use my location" for the ROUTE origin specifically.
    // Kept separate from the general map-centering behavior of myLocationButton.
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
        sheetEtaText = findViewById(R.id.sheetEtaText);
        sheetArriveText = findViewById(R.id.sheetArriveText);
        sheetRouteMeta = findViewById(R.id.sheetRouteMeta);
        modeWalkChip = findViewById(R.id.modeWalkChip);
        modeDriveChip = findViewById(R.id.modeDriveChip);
        sheetPlacePhoto = findViewById(R.id.sheetPlacePhoto);
        reportObstacleButton = findViewById(R.id.reportObstacleButton);
        viewReportsButton = findViewById(R.id.viewReportsButton);
        startDirectionsButton = findViewById(R.id.startDirectionsButton);
        myLocationButton = findViewById(R.id.myLocationButton);

        navigationOverlay = findViewById(R.id.navigationOverlay);
        navTurnIcon = findViewById(R.id.navTurnIcon);
        navStepDistance = findViewById(R.id.navStepDistance);
        navStepStreet = findViewById(R.id.navStepStreet);
        navThenText = findViewById(R.id.navThenText);
        navEtaText = findViewById(R.id.navEtaText);
        navMetaText = findViewById(R.id.navMetaText);
        navExitButton = findViewById(R.id.navExitButton);
        obstacleAlertCard = findViewById(R.id.obstacleAlertCard);
        obstacleAlertMessage = findViewById(R.id.obstacleAlertMessage);
        obstacleKeepRouteButton = findViewById(R.id.obstacleKeepRouteButton);
        obstacleAvoidButton = findViewById(R.id.obstacleAvoidButton);

        directionsApiClient = new DirectionsApiClient(getGoogleMapsApiKey());
        repository = new FirestoreAccessibilityRepository();

        seedCampusPlaces();

        backButton.setOnClickListener(v -> {
            if (navigating) {
                exitNavigation();
            } else if (routePanel.getVisibility() == View.VISIBLE) {
                showSearchPanel();
            } else {
                finish();
            }
        });

        reportObstacleButton.setOnClickListener(v ->
                startActivity(new Intent(this, ReportActivity.class)));

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
        navExitButton.setOnClickListener(v -> exitNavigation());
        obstacleKeepRouteButton.setOnClickListener(v -> keepCurrentRouteDespiteObstacle());
        obstacleAvoidButton.setOnClickListener(v -> avoidDetectedObstacle());

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
        startDirectionsButton.setOnClickListener(v -> startJourney());

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
            selectedDestination = latLng;

            if (selectedLocationMarker != null) {
                selectedLocationMarker.remove();
            }
            selectedLocationMarker = map.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("Selected location"));

            showPlaceSheet(
                    "Selected location",
                    String.format(Locale.US, "%.5f, %.5f", latLng.latitude, latLng.longitude),
                    latLng,
                    R.drawable.sample_report_corridor
            );
        });

        map.setOnMarkerClickListener(marker -> {
            String reportId = markerReportIds.get(marker);
            selectedReportId = reportId;
            selectedDestination = marker.getPosition();
            if (reportId != null) {
                AccessibilityReport report = ObstacleReportStore.getInstance(this).getById(reportId);
                if (report != null) {
                    showPlaceSheet(
                            report.getIssueType(),
                            report.getLocationName() + " · " + report.getStatus()
                                    + " · " + ReportTimeFormat.postedBanner(report.getTimestamp()),
                            marker.getPosition(),
                            photoForReport(report)
                    );
                }
            } else {
                showPlaceSheet(
                        marker.getTitle() != null ? marker.getTitle() : "Place",
                        marker.getSnippet() != null ? marker.getSnippet() : "Nearby",
                        marker.getPosition(),
                        photoForPlaceName(marker.getTitle())
                );
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

        showPlaceSheet(
                "Nearby places",
                "Red pins are community obstacle reports",
                CAMPUS_CENTER,
                R.drawable.campus_pathway_demo
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

        int index = 0;
        for (AccessibilityReport report : reports) {
            double latOffset = ((index % 3) - 1) * 0.0009;
            double lngOffset = ((index / 3) % 3 - 1) * 0.0009;
            LatLng position = new LatLng(
                    CAMPUS_CENTER.latitude + latOffset,
                    CAMPUS_CENTER.longitude + lngOffset
            );

            float hue = AccessibilityReport.STATUS_CONFIRMED.equals(report.getStatus())
                    ? BitmapDescriptorFactory.HUE_ORANGE
                    : BitmapDescriptorFactory.HUE_RED;

            Marker marker = map.addMarker(new MarkerOptions()
                    .position(position)
                    .title(report.getIssueType())
                    .snippet(ReportTimeFormat.postedBanner(report.getTimestamp())
                            + " · " + report.getLocationName())
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));

            if (marker != null) {
                markerReportIds.put(marker, report.getId());
            }
            index++;
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
                "Garden", "Landmark",
                new LatLng(2.9218, 101.6568)));
        campusPlaces.add(new CampusPlace(
                "Library", "Facility",
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
                selectedDestination = place.position;
                showPlaceSheet(place.name, place.category + " · nearby", place.position,
                        photoForPlaceName(place.name));
                return;
            }
        }

        List<AccessibilityReport> reports =
                ObstacleReportStore.getInstance(this).getActiveCommunityReports();
        for (AccessibilityReport report : reports) {
            if (report.getLocationName().toLowerCase(Locale.US).contains(q)
                    || report.getIssueType().toLowerCase(Locale.US).contains(q)) {
                selectedReportId = report.getId();
                selectedDestination = CAMPUS_CENTER;
                showPlaceSheet(
                        report.getIssueType(),
                        report.getLocationName() + " · " + report.getStatus()
                                + " · " + ReportTimeFormat.postedBanner(report.getTimestamp()),
                        CAMPUS_CENTER,
                        photoForReport(report)
                );
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(CAMPUS_CENTER, 16.5f));
                return;
            }
        }

        Toast.makeText(this, "No matching place found", Toast.LENGTH_SHORT).show();
    }

    private void showSheet(String title, String subtitle) {
        showPlaceSheet(
                title,
                subtitle,
                selectedDestination != null ? selectedDestination : CAMPUS_CENTER,
                photoForPlaceName(title)
        );
    }

    private void showPlaceSheet(String title, String subtitle, LatLng destination, int photoRes) {
        sheetTitle.setText(title);
        sheetSubtitle.setText(subtitle != null ? subtitle : "");
        sheetPlacePhoto.setImageResource(photoRes);
        selectedDestination = destination;

        int walkMinutes = estimateWalkMinutes(destination);
        int driveMinutes = Math.max(1, walkMinutes / 4);
        String walkLabel = formatDurationMinutes(walkMinutes);

        sheetEtaText.setText(walkLabel);
        modeWalkChip.setText("Walk · " + walkLabel);
        modeDriveChip.setText("Drive · " + formatDurationMinutes(driveMinutes));

        long arriveAt = System.currentTimeMillis() + walkMinutes * 60_000L;
        java.text.SimpleDateFormat timeFmt =
                new java.text.SimpleDateFormat("h:mm a", Locale.getDefault());
        sheetArriveText.setText("Arrive " + timeFmt.format(new java.util.Date(arriveAt)));

        LatLng originForMeta = navigationOriginOrCampus();
        double km = distanceMeters(originForMeta, destination) / 1000.0;
        if (km < 0.05) {
            sheetRouteMeta.setText("Nearby · explore the area");
        } else {
            sheetRouteMeta.setText(String.format(Locale.US,
                    "%.1f km · fastest walking route", Math.max(0.1, km)));
        }

        locationSheet.setVisibility(View.VISIBLE);
    }

    private int photoForPlaceName(String name) {
        if (name == null) {
            return R.drawable.sample_report_corridor;
        }
        String n = name.toLowerCase(Locale.US);
        if (n.contains("library") || n.contains("elevator") || n.contains("lift")) {
            return R.drawable.sample_report_elevator;
        }
        if (n.contains("stair") || n.contains("stad") || n.contains("hall") || n.contains("dewan")) {
            return R.drawable.sample_report_stairs;
        }
        if (n.contains("garden") || n.contains("pathway") || n.contains("campus") || n.contains("mmu")) {
            return R.drawable.campus_pathway_demo;
        }
        return R.drawable.sample_report_corridor;
    }

    private int photoForReport(AccessibilityReport report) {
        if (report == null) {
            return R.drawable.sample_report_corridor;
        }
        return photoForPlaceName(report.getLocationName() + " " + report.getIssueType());
    }

    private int estimateWalkMinutes(LatLng destination) {
        double meters = distanceMeters(navigationOriginOrCampus(), destination);
        int minutes = (int) Math.round((meters / 1000.0) / 4.5 * 60.0);
        return Math.max(1, minutes);
    }

    /**
     * Prefer GPS when it is near campus; otherwise use campus center so demo
     * ETAs stay realistic on emulators / overseas devices.
     */
    private LatLng navigationOriginOrCampus() {
        if (routeOriginLocation != null) {
            LatLng gps = new LatLng(routeOriginLocation[0], routeOriginLocation[1]);
            if (distanceMeters(gps, CAMPUS_CENTER) <= 25_000) {
                return gps;
            }
        }
        return CAMPUS_CENTER;
    }

    /** Formats minutes as "24 min", "1 hr 5 min", or "3 hr". */
    private static String formatDurationMinutes(int totalMinutes) {
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

    private static double distanceMeters(LatLng a, LatLng b) {
        double earth = 6371000;
        double dLat = Math.toRadians(b.latitude - a.latitude);
        double dLng = Math.toRadians(b.longitude - a.longitude);
        double x = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earth * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x));
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
                        showPlaceSheet("Your location", "Centered on your current position", me,
                                R.drawable.sample_report_corridor);
                    } else if (map != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(CAMPUS_CENTER, 16f));
                        showPlaceSheet("Map center", "Showing your area", CAMPUS_CENTER,
                                R.drawable.campus_pathway_demo);
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
            // If the user tapped "use my location" for the route origin before
            // permission was granted, fetch it now that we have access.
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

    /**
     * Swaps the top search bar for the two-field route panel, called when
     * "Directions" is tapped in the bottom sheet. Prefills the destination
     * with whatever place/report is currently shown in the sheet, and tries
     * to auto-fill the origin from GPS if permission is already granted.
     */
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
                    LatLng origin = navigationOriginOrCampus();
                    planRoute(origin.latitude, origin.longitude, destLat, destLng);
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

        double margin = 0.01;
        return new double[]{minLat - margin, maxLat + margin, minLng - margin, maxLng + margin};
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

        for (Obstacle obstacle : route.getObstaclesOnRoute()) {
            map.addMarker(new MarkerOptions()
                    .position(new LatLng(obstacle.getLat(), obstacle.getLng()))
                    .title(obstacle.getType())
                    .snippet(obstacle.getSeverity())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
        }

        map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120));

        selectedReportId = null;
        selectedDestination = new LatLng(destLat, destLng);
        beginNavigation(route, originLat, originLng, destLat, destLng);
    }

    /**
     * Called by "Start directions" — fetch a walking route (or fall back to a
     * local demo path) and enter Google Maps–style turn-by-turn mode.
     */
    private void startJourney() {
        LatLng destination = selectedDestination != null ? selectedDestination : CAMPUS_CENTER;
        String destName = sheetTitle.getText() != null ? sheetTitle.getText().toString() : "Destination";

        dismissedObstacleAlerts.clear();
        Toast.makeText(this, "Starting journey…", Toast.LENGTH_SHORT).show();

        Runnable withOrigin = () -> {
            LatLng origin = navigationOriginOrCampus();
            fetchRouteForNavigation(
                    origin.latitude, origin.longitude,
                    destination.latitude, destination.longitude, destName
            );
        };

        if (routeOriginLocation == null
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    routeOriginLocation = new double[]{location.getLatitude(), location.getLongitude()};
                }
                withOrigin.run();
            }).addOnFailureListener(e -> withOrigin.run());
        } else {
            withOrigin.run();
        }
    }

    private void fetchRouteForNavigation(
            double originLat,
            double originLng,
            double destLat,
            double destLng,
            String destName
    ) {
        directionsApiClient.fetchWalkingRoutes(originLat, originLng, destLat, destLng,
                new DirectionsApiClient.RoutesCallback() {
                    @Override
                    public void onSuccess(List<RouteOption> routes) {
                        if (routes == null || routes.isEmpty()) {
                            beginNavigation(
                                    buildFallbackRoute(originLat, originLng, destLat, destLng, destName),
                                    originLat, originLng, destLat, destLng
                            );
                            return;
                        }
                        beginNavigation(routes.get(0), originLat, originLng, destLat, destLng);
                    }

                    @Override
                    public void onError(Exception e) {
                        beginNavigation(
                                buildFallbackRoute(originLat, originLng, destLat, destLng, destName),
                                originLat, originLng, destLat, destLng
                        );
                    }
                });
    }

    private RouteOption buildFallbackRoute(
            double originLat,
            double originLng,
            double destLat,
            double destLng,
            String destName
    ) {
        List<double[]> points = new ArrayList<>();
        // Intermediate points so the camera can animate along a path
        for (int i = 0; i <= 20; i++) {
            double t = i / 20.0;
            points.add(new double[]{
                    originLat + (destLat - originLat) * t,
                    originLng + (destLng - originLng) * t
            });
        }
        double meters = distanceMeters(
                new LatLng(originLat, originLng),
                new LatLng(destLat, destLng)
        );
        int seconds = Math.max(60, (int) Math.round((meters / 1000.0) / 4.5 * 3600.0));
        List<RouteOption.Step> steps = new ArrayList<>();
        steps.add(new RouteOption.Step(
                "Head toward " + destName,
                "depart",
                (int) Math.max(30, meters * 0.4),
                seconds / 3,
                originLat,
                originLng
        ));
        steps.add(new RouteOption.Step(
                "Continue on pathway",
                "",
                (int) Math.max(30, meters * 0.35),
                seconds / 3,
                originLat + (destLat - originLat) * 0.4,
                originLng + (destLng - originLng) * 0.4
        ));
        steps.add(new RouteOption.Step(
                "Arrive at " + destName,
                "arrive",
                (int) Math.max(20, meters * 0.25),
                seconds / 3,
                destLat,
                destLng
        ));
        return new RouteOption(points, destName, seconds, (int) meters, steps);
    }

    private void beginNavigation(
            RouteOption route,
            double originLat,
            double originLng,
            double destLat,
            double destLng
    ) {
        if (map == null || route == null || route.getPoints().isEmpty()) {
            Toast.makeText(this, "Could not start navigation", Toast.LENGTH_SHORT).show();
            return;
        }

        activeRoute = route;
        navigationPath.clear();
        for (double[] p : route.getPoints()) {
            navigationPath.add(new LatLng(p[0], p[1]));
        }
        navigationIndex = 0;
        navigationStepIndex = 0;
        navigating = true;
        navDestLat = destLat;
        navDestLng = destLng;
        hideObstacleAlert();
        prepareJourneyObstacles(route);

        // Hide browse UI
        locationSheet.setVisibility(View.GONE);
        searchPanel.setVisibility(View.GONE);
        routePanel.setVisibility(View.GONE);
        myLocationButton.setVisibility(View.GONE);
        navigationOverlay.setVisibility(View.VISIBLE);

        map.clear();
        markerReportIds.clear();

        PolylineOptions polylineOptions = new PolylineOptions()
                .color(Color.parseColor("#1A73E8"))
                .width(18f);
        for (LatLng point : navigationPath) {
            polylineOptions.add(point);
        }
        navigationPolyline = map.addPolyline(polylineOptions);

        map.addMarker(new MarkerOptions()
                .position(new LatLng(destLat, destLng))
                .title("Destination")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        for (Obstacle obstacle : journeyObstacles) {
            map.addMarker(new MarkerOptions()
                    .position(new LatLng(obstacle.getLat(), obstacle.getLng()))
                    .title(obstacle.getType())
                    .snippet("Reported obstacle")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
        }

        navigationMarker = map.addMarker(new MarkerOptions()
                .position(navigationPath.get(0))
                .title("You")
                .flat(true)
                .anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        updateNavigationUi();
        moveNavigationCamera(navigationPath.get(0), bearingBetween(
                navigationPath.get(0),
                navigationPath.get(Math.min(1, navigationPath.size() - 1))
        ));

        navigationHandler.removeCallbacks(navigationTick);
        navigationHandler.postDelayed(navigationTick, 1200);
    }

    private void prepareJourneyObstacles(RouteOption route) {
        journeyObstacles.clear();

        List<AccessibilityReport> reports =
                ObstacleReportStore.getInstance(this).getActiveCommunityReports();
        int index = 0;
        for (AccessibilityReport report : reports) {
            LatLng pos = positionForCommunityReport(index);
            journeyObstacles.add(new Obstacle(
                    report.getId(),
                    pos.latitude,
                    pos.longitude,
                    report.getIssueType(),
                    Obstacle.SEVERITY_BLOCKER,
                    "community",
                    report.getTimestamp(),
                    true
            ));
            index++;
        }

        // Ensure at least one obstacle sits on the path so the demo alert fires mid-journey
        boolean anyOnRoute = false;
        for (Obstacle o : journeyObstacles) {
            if (isObstacleNearPath(o.getLat(), o.getLng(), navigationPath, OBSTACLE_ON_ROUTE_METERS)) {
                anyOnRoute = true;
                break;
            }
        }
        if (!anyOnRoute && navigationPath.size() > 4) {
            int mid = Math.max(2, (int) (navigationPath.size() * 0.45));
            LatLng midPoint = navigationPath.get(mid);
            journeyObstacles.add(new Obstacle(
                    "demo-path-obstacle",
                    midPoint.latitude,
                    midPoint.longitude,
                    "Blocked pathway",
                    Obstacle.SEVERITY_BLOCKER,
                    "demo",
                    System.currentTimeMillis(),
                    true
            ));
        }

        if (route != null) {
            for (Obstacle o : route.getObstaclesOnRoute()) {
                boolean already = false;
                for (Obstacle existing : journeyObstacles) {
                    if (existing.getId() != null && existing.getId().equals(o.getId())) {
                        already = true;
                        break;
                    }
                }
                if (!already) {
                    journeyObstacles.add(o);
                }
            }
        }
    }

    private LatLng positionForCommunityReport(int index) {
        double latOffset = ((index % 3) - 1) * 0.0009;
        double lngOffset = ((index / 3) % 3 - 1) * 0.0009;
        return new LatLng(
                CAMPUS_CENTER.latitude + latOffset,
                CAMPUS_CENTER.longitude + lngOffset
        );
    }

    private void advanceNavigation() {
        if (!navigating || navigationPath.isEmpty()) {
            return;
        }
        if (navigationIndex >= navigationPath.size() - 1) {
            navStepStreet.setText("You have arrived");
            navStepDistance.setText("0 m");
            navTurnIcon.setText("✓");
            navThenText.setVisibility(View.GONE);
            navEtaText.setText("0 min");
            Toast.makeText(this, "Arrived at destination", Toast.LENGTH_SHORT).show();
            navigationHandler.removeCallbacks(navigationTick);
            return;
        }

        navigationIndex++;
        LatLng current = navigationPath.get(navigationIndex);
        LatLng next = navigationPath.get(Math.min(navigationIndex + 1, navigationPath.size() - 1));

        if (navigationMarker != null) {
            navigationMarker.setPosition(current);
        }
        moveNavigationCamera(current, bearingBetween(current, next));

        // Advance step when close to next step start
        if (activeRoute != null && !activeRoute.getSteps().isEmpty()) {
            int nextStep = Math.min(navigationStepIndex + 1, activeRoute.getSteps().size() - 1);
            if (nextStep > navigationStepIndex) {
                RouteOption.Step upcoming = activeRoute.getSteps().get(nextStep);
                double d = distanceMeters(current, new LatLng(upcoming.startLat, upcoming.startLng));
                if (d < 35 || navigationIndex > (navigationPath.size() * (nextStep + 1) / activeRoute.getSteps().size())) {
                    navigationStepIndex = nextStep;
                }
            }
        }
        updateNavigationUi();
        checkUpcomingObstacleAlert();
    }

    private void checkUpcomingObstacleAlert() {
        if (!navigating || obstaclePromptVisible || navigationPath.isEmpty()) {
            return;
        }
        if (navigationIndex >= navigationPath.size() - 1) {
            return;
        }

        LatLng current = navigationPath.get(navigationIndex);
        Obstacle nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Obstacle obstacle : journeyObstacles) {
            if (obstacle.getId() != null && dismissedObstacleAlerts.contains(obstacle.getId())) {
                continue;
            }
            if (!isObstacleOnRemainingPath(obstacle)) {
                continue;
            }
            double dist = distanceMeters(current, new LatLng(obstacle.getLat(), obstacle.getLng()));
            if (dist <= OBSTACLE_ALERT_METERS && dist < nearestDist) {
                nearest = obstacle;
                nearestDist = dist;
            }
        }

        if (nearest != null) {
            showObstacleAlert(nearest, (int) Math.round(nearestDist));
        }
    }

    private boolean isObstacleOnRemainingPath(Obstacle obstacle) {
        if (navigationPath.isEmpty() || navigationIndex >= navigationPath.size() - 1) {
            return false;
        }
        List<LatLng> remaining = navigationPath.subList(navigationIndex, navigationPath.size());
        return isObstacleNearPath(obstacle.getLat(), obstacle.getLng(), remaining, OBSTACLE_ON_ROUTE_METERS);
    }

    private static boolean isObstacleNearPath(
            double lat,
            double lng,
            List<LatLng> path,
            double radiusMeters
    ) {
        if (path == null || path.size() < 2) {
            if (path != null && path.size() == 1) {
                return distanceMeters(path.get(0), new LatLng(lat, lng)) <= radiusMeters;
            }
            return false;
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

    private static double distanceToSegmentMeters(
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
        double closestLat = aLat + t * abLat;
        double closestLng = aLng + t * abLng;
        return distanceMeters(new LatLng(pLat, pLng), new LatLng(closestLat, closestLng));
    }

    private void showObstacleAlert(Obstacle obstacle, int distanceMeters) {
        pendingObstacleAlert = obstacle;
        obstaclePromptVisible = true;
        navigationHandler.removeCallbacks(navigationTick);

        String type = obstacle.getType() != null ? obstacle.getType() : "Obstacle";
        String prettyType = type.replace('_', ' ');
        obstacleAlertMessage.setText(String.format(Locale.US,
                "%s is about %d m ahead on your route (~1 min). "
                        + "We found a safer path that avoids it. Switch routes?",
                prettyType,
                Math.max(1, distanceMeters)));
        obstacleAlertCard.setVisibility(View.VISIBLE);

        Toast.makeText(this, "Obstacle ahead — choose a route", Toast.LENGTH_SHORT).show();
    }

    private void hideObstacleAlert() {
        obstaclePromptVisible = false;
        pendingObstacleAlert = null;
        if (obstacleAlertCard != null) {
            obstacleAlertCard.setVisibility(View.GONE);
        }
    }

    private void keepCurrentRouteDespiteObstacle() {
        if (pendingObstacleAlert != null && pendingObstacleAlert.getId() != null) {
            dismissedObstacleAlerts.add(pendingObstacleAlert.getId());
        }
        hideObstacleAlert();
        Toast.makeText(this, "Keeping current route", Toast.LENGTH_SHORT).show();
        if (navigating) {
            navigationHandler.removeCallbacks(navigationTick);
            navigationHandler.postDelayed(navigationTick, 800);
        }
    }

    private void avoidDetectedObstacle() {
        Obstacle obstacle = pendingObstacleAlert;
        if (obstacle == null || navigationPath.isEmpty()) {
            hideObstacleAlert();
            return;
        }
        if (obstacle.getId() != null) {
            dismissedObstacleAlerts.add(obstacle.getId());
        }

        LatLng current = navigationPath.get(Math.min(navigationIndex, navigationPath.size() - 1));
        hideObstacleAlert();
        Toast.makeText(this, "Finding safer route…", Toast.LENGTH_SHORT).show();

        RouteOption avoidRoute = buildAvoidanceRoute(
                current.latitude,
                current.longitude,
                obstacle.getLat(),
                obstacle.getLng(),
                navDestLat,
                navDestLng,
                obstacle.getType()
        );
        beginNavigation(avoidRoute, current.latitude, current.longitude, navDestLat, navDestLng);
        Toast.makeText(this, "Switched to route that avoids the obstacle", Toast.LENGTH_LONG).show();
    }

    /**
     * Builds a walking detour that swings around the obstacle, then continues
     * to the destination — used when Directions API can't provide a via-point.
     */
    private RouteOption buildAvoidanceRoute(
            double originLat,
            double originLng,
            double obstacleLat,
            double obstacleLng,
            double destLat,
            double destLng,
            String obstacleLabel
    ) {
        // Perpendicular offset (~70 m) so the path skirts the obstacle
        double dLat = destLat - originLat;
        double dLng = destLng - originLng;
        double len = Math.sqrt(dLat * dLat + dLng * dLng);
        double offsetLat;
        double offsetLng;
        if (len < 1e-9) {
            offsetLat = 0.0006;
            offsetLng = 0.0006;
        } else {
            // ~70 m in degrees at equator-ish; fine for campus scale
            double metersToDeg = 70.0 / 111_320.0;
            offsetLat = (-dLng / len) * metersToDeg;
            offsetLng = (dLat / len) * metersToDeg;
        }
        double viaLat = obstacleLat + offsetLat;
        double viaLng = obstacleLng + offsetLng;

        List<double[]> points = new ArrayList<>();
        appendInterpolated(points, originLat, originLng, viaLat, viaLng, 10);
        appendInterpolated(points, viaLat, viaLng, destLat, destLng, 12);

        double meters = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            meters += distanceMeters(
                    new LatLng(points.get(i)[0], points.get(i)[1]),
                    new LatLng(points.get(i + 1)[0], points.get(i + 1)[1])
            );
        }
        int seconds = Math.max(60, (int) Math.round((meters / 1000.0) / 4.5 * 3600.0));
        String label = obstacleLabel != null ? obstacleLabel.replace('_', ' ') : "obstacle";

        List<RouteOption.Step> steps = new ArrayList<>();
        steps.add(new RouteOption.Step(
                "Detour around " + label,
                "turn-right",
                (int) Math.max(40, meters * 0.45),
                seconds / 2,
                originLat,
                originLng
        ));
        steps.add(new RouteOption.Step(
                "Continue to destination",
                "",
                (int) Math.max(40, meters * 0.4),
                seconds / 3,
                viaLat,
                viaLng
        ));
        steps.add(new RouteOption.Step(
                "Arrive at destination",
                "arrive",
                (int) Math.max(20, meters * 0.15),
                seconds / 6,
                destLat,
                destLng
        ));
        return new RouteOption(points, "Avoids " + label, seconds, (int) meters, steps);
    }

    private static void appendInterpolated(
            List<double[]> points,
            double fromLat,
            double fromLng,
            double toLat,
            double toLng,
            int segments
    ) {
        int start = points.isEmpty() ? 0 : 1;
        for (int i = start; i <= segments; i++) {
            double t = i / (double) segments;
            points.add(new double[]{
                    fromLat + (toLat - fromLat) * t,
                    fromLng + (toLng - fromLng) * t
            });
        }
    }

    private void updateNavigationUi() {
        if (activeRoute == null) {
            return;
        }

        List<RouteOption.Step> steps = activeRoute.getSteps();
        if (!steps.isEmpty()) {
            RouteOption.Step step = steps.get(Math.min(navigationStepIndex, steps.size() - 1));
            navStepStreet.setText(shortStreetName(step.instruction));
            navStepDistance.setText(formatMeters(remainingStepMeters(step)));
            navTurnIcon.setText(maneuverIcon(step.maneuver, step.instruction));
            if (navigationStepIndex + 1 < steps.size()) {
                RouteOption.Step then = steps.get(navigationStepIndex + 1);
                navThenText.setVisibility(View.VISIBLE);
                navThenText.setText("Then " + maneuverIcon(then.maneuver, then.instruction)
                        + " " + shortStreetName(then.instruction));
            } else {
                navThenText.setVisibility(View.VISIBLE);
                navThenText.setText("Then ✓ Arrive");
            }
        } else {
            navStepStreet.setText(activeRoute.getSummary());
            navStepDistance.setText(formatMeters(remainingPathMeters()));
            navTurnIcon.setText("↑");
            navThenText.setText("Then ✓ Arrive");
        }

        int remainMeters = remainingPathMeters();
        int remainMinutes = remainingMinutesFromRoute(remainMeters);
        if (navigationIndex >= navigationPath.size() - 1) {
            remainMinutes = 0;
        }
        navEtaText.setText(formatDurationMinutes(remainMinutes));

        long arriveAt = System.currentTimeMillis() + remainMinutes * 60_000L;
        java.text.SimpleDateFormat timeFmt =
                new java.text.SimpleDateFormat("h:mm a", Locale.getDefault());
        String distanceLabel = remainMeters >= 1000
                ? String.format(Locale.US, "%.1f km", remainMeters / 1000.0)
                : remainMeters + " m";
        navMetaText.setText(distanceLabel + " · " + timeFmt.format(new java.util.Date(arriveAt)));
    }

    private int remainingMinutesFromRoute(int remainMeters) {
        if (activeRoute == null) {
            return 1;
        }
        int totalMeters = activeRoute.getDistanceMeters();
        int totalSeconds = activeRoute.getDurationSeconds();
        if (totalMeters > 0 && totalSeconds > 0) {
            double fraction = Math.min(1.0, Math.max(0.0, remainMeters / (double) totalMeters));
            return Math.max(1, (int) Math.round(totalSeconds * fraction / 60.0));
        }
        return Math.max(1, (int) Math.round((remainMeters / 1000.0) / 4.5 * 60.0));
    }

    private int remainingPathMeters() {
        if (navigationPath.isEmpty() || navigationIndex >= navigationPath.size() - 1) {
            return 0;
        }
        double total = 0;
        for (int i = navigationIndex; i < navigationPath.size() - 1; i++) {
            total += distanceMeters(navigationPath.get(i), navigationPath.get(i + 1));
        }
        return (int) Math.round(total);
    }

    private int remainingStepMeters(RouteOption.Step step) {
        // Approximate remaining for current step using path remainder fraction
        int remain = remainingPathMeters();
        if (activeRoute.getSteps().isEmpty()) {
            return remain;
        }
        return Math.max(10, Math.min(step.distanceMeters, remain));
    }

    private static String formatMeters(int meters) {
        if (meters >= 1000) {
            return String.format(Locale.US, "%.1f km", meters / 1000.0);
        }
        return meters + " m";
    }

    private static String shortStreetName(String instruction) {
        if (instruction == null || instruction.isEmpty()) {
            return "Continue";
        }
        // Prefer last clause after "onto"/"on"/"toward"
        String lower = instruction.toLowerCase(Locale.US);
        int onto = lower.lastIndexOf(" onto ");
        if (onto >= 0) {
            return instruction.substring(onto + 6).trim();
        }
        int toward = lower.lastIndexOf(" toward ");
        if (toward >= 0) {
            return instruction.substring(toward + 8).trim();
        }
        if (instruction.length() > 42) {
            return instruction.substring(0, 42) + "…";
        }
        return instruction;
    }

    private static String maneuverIcon(String maneuver, String instruction) {
        String m = (maneuver != null ? maneuver : "").toLowerCase(Locale.US);
        String i = (instruction != null ? instruction : "").toLowerCase(Locale.US);
        if (m.contains("left") || i.contains("left")) {
            return "↰";
        }
        if (m.contains("right") || i.contains("right")) {
            return "↱";
        }
        if (m.contains("uturn") || i.contains("u-turn")) {
            return "↩";
        }
        if (m.contains("arrive") || i.contains("arrive")) {
            return "✓";
        }
        if (m.contains("roundabout") || i.contains("roundabout")) {
            return "⟲";
        }
        return "↑";
    }

    private void moveNavigationCamera(LatLng target, float bearing) {
        if (map == null) {
            return;
        }
        CameraPosition position = new CameraPosition.Builder()
                .target(target)
                .zoom(18f)
                .tilt(55f)
                .bearing(bearing)
                .build();
        map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 900, null);
    }

    private static float bearingBetween(LatLng from, LatLng to) {
        double lat1 = Math.toRadians(from.latitude);
        double lat2 = Math.toRadians(to.latitude);
        double dLng = Math.toRadians(to.longitude - from.longitude);
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
    }

    private void exitNavigation() {
        navigating = false;
        navigationHandler.removeCallbacks(navigationTick);
        hideObstacleAlert();
        dismissedObstacleAlerts.clear();
        journeyObstacles.clear();
        activeRoute = null;
        navigationPath.clear();
        navigationIndex = 0;
        navigationStepIndex = 0;
        navigationMarker = null;
        navigationPolyline = null;

        navigationOverlay.setVisibility(View.GONE);
        myLocationButton.setVisibility(View.VISIBLE);
        showSearchPanel();

        if (map != null) {
            showCampusAndObstacles();
            map.moveCamera(CameraUpdateFactory.newCameraPosition(
                    new CameraPosition.Builder()
                            .target(selectedDestination != null ? selectedDestination : CAMPUS_CENTER)
                            .zoom(16f)
                            .tilt(0f)
                            .bearing(0f)
                            .build()
            ));
            showPlaceSheet(
                    sheetTitle.getText() != null ? sheetTitle.getText().toString() : "Nearby places",
                    "Journey ended",
                    selectedDestination != null ? selectedDestination : CAMPUS_CENTER,
                    photoForPlaceName(sheetTitle.getText() != null ? sheetTitle.getText().toString() : null)
            );
        }
    }

    @Override
    protected void onDestroy() {
        navigating = false;
        navigationHandler.removeCallbacks(navigationTick);
        super.onDestroy();
        geocodeExecutor.shutdown();
    }
}
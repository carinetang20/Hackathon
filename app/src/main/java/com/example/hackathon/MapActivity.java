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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;




import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.hackathon.models.AccessibilityReport;
import com.example.hackathon.utils.GeoUtils;
import com.example.hackathon.utils.ObstacleReportStore;
import com.example.hackathon.utils.ReportTimeFormat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.OnMapsSdkInitializedCallback;
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


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

public class MapActivity extends AppCompatActivity
        implements OnMapReadyCallback, OnMapsSdkInitializedCallback,
        ObstacleReportStore.ReportsListener {

    private static final int REQUEST_LOCATION = 1001;
    private static final LatLng CAMPUS_CENTER = new LatLng(2.9213, 101.6559);
    /** Alert when an on-route obstacle is about ~1 min walk ahead (~80 m). */
    private static final double OBSTACLE_ALERT_METERS = 80.0;

    private GoogleMap map;
    private final Map<Marker, String> markerReportIds = new HashMap<>();
    private final List<CampusPlace> campusPlaces = new ArrayList<>();

    private FusedLocationProviderClient fusedLocationClient;

    private EditText searchInput;
    private ListView searchPredictionsList;
    private final List<SearchSuggestion> searchSuggestions = new ArrayList<>();
    private SearchSuggestionAdapter searchSuggestionAdapter;
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
    private View mapControls;

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
    private final List<AccessibilityReport> journeyObstacles = new ArrayList<>();
    private final Set<String> dismissedObstacleAlerts = new HashSet<>();
    private AccessibilityReport pendingObstacleAlert;
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

    // Coordinates of whatever is currently shown in the bottom sheet, so
    // "Report obstruction" can attach a real location to the new report.
    private Double selectedLat;
    private Double selectedLng;

    // --- Route-finding additions ---
    private DirectionsApiClient directionsApiClient;
    private final RouteAccessibilityAnalyzer analyzer = new RouteAccessibilityAnalyzer();
    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();

    private Marker selectedLocationMarker;

    // Tracks the currently-drawn route so a new search replaces it instead
    // of stacking multiple polylines/pins on top of each other.
    private Polyline currentRoutePolyline;
    private Marker currentRouteOriginMarker;
    private Marker currentRouteDestinationMarker;

    // Set once the user taps "use my location" for the ROUTE origin specifically.
    // Kept separate from the general map-centering behavior of myLocationButton.
    private double[] routeOriginLocation;
    private boolean pendingRouteOriginFetch = false;

    private boolean pendingInitialCameraMove = false;

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
        searchPredictionsList = findViewById(R.id.searchPredictionsList);
        searchSuggestionAdapter = new SearchSuggestionAdapter();
        searchPredictionsList.setAdapter(searchSuggestionAdapter);
        searchPredictionsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < searchSuggestions.size()) {
                selectSearchSuggestion(searchSuggestions.get(position));
            }
        });
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
        mapControls = findViewById(R.id.mapControls);

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

        directionsApiClient = new DirectionsApiClient(
                getGoogleMapsApiKey() != null ? getGoogleMapsApiKey() : "");

        seedCampusPlaces();
        ObstacleReportStore.getInstance(this).addListener(this);

        backButton.setOnClickListener(v -> {
            if (navigating) {
                exitNavigation();
            } else if (routePanel.getVisibility() == View.VISIBLE) {
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
        navExitButton.setOnClickListener(v -> exitNavigation());
        obstacleKeepRouteButton.setOnClickListener(v -> keepCurrentRouteDespiteObstacle());
        obstacleAvoidButton.setOnClickListener(v -> avoidDetectedObstacle());

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSearch = actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (isSearch) {
                commitSearch(searchInput.getText() != null ? searchInput.getText().toString() : "");
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
                String query = s != null ? s.toString() : "";
                if (query.trim().length() >= 1) {
                    updateSearchSuggestions(query);
                } else {
                    hideSearchSuggestions();
                }
            }
        });

        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && searchInput.getText() != null
                    && searchInput.getText().toString().trim().length() >= 1) {
                updateSearchSuggestions(searchInput.getText().toString());
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

        // Legacy renderer avoids blank-tile maps on many emulators / devices.
        MapsInitializer.initialize(getApplicationContext(), MapsInitializer.Renderer.LEGACY, this);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapsSdkInitialized(@NonNull MapsInitializer.Renderer renderer) {
        // No-op: LEGACY is requested above; callback confirms init completed.
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setAllGesturesEnabled(true);

        // Always show campus first so tiles / pins appear immediately.
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(CAMPUS_CENTER, 16f));
        selectedLat = CAMPUS_CENTER.latitude;
        selectedLng = CAMPUS_CENTER.longitude;
        selectedDestination = CAMPUS_CENTER;

        enableMyLocationIfPermitted();
        showCampusAndObstacles();
        centerCameraOnUserLocationOrCampus();

        map.setOnMapClickListener(latLng -> {
            hideSearchSuggestions();
            selectedReportId = null;
            selectedLat = latLng.latitude;
            selectedLng = latLng.longitude;

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
            selectedLat = marker.getPosition().latitude;
            selectedLng = marker.getPosition().longitude;

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

        findViewById(R.id.zoomInButton).setOnClickListener(v ->
                map.animateCamera(CameraUpdateFactory.zoomIn()));
        findViewById(R.id.zoomOutButton).setOnClickListener(v ->
                map.animateCamera(CameraUpdateFactory.zoomOut()));

        showPlaceSheet(
                "Nearby places",
                "Orange = obstacles · Green = facilities",
                CAMPUS_CENTER,
                R.drawable.campus_pathway_demo
        );
    }

    @Override
    public void onReportsChanged() {
        runOnUiThread(() -> {
            if (map != null && !navigating) {
                showCampusAndObstacles();
            }
        });
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
                "Faculty of Computing & Informatics", "Faculty",
                new LatLng(2.9208, 101.6562)));
        campusPlaces.add(new CampusPlace(
                "Faculty of Engineering", "Faculty",
                new LatLng(2.9204, 101.6555)));
        campusPlaces.add(new CampusPlace(
                "Garden", "Landmark",
                new LatLng(2.9218, 101.6568)));
        campusPlaces.add(new CampusPlace(
                "Library", "Facility",
                new LatLng(2.9209, 101.6550)));
        campusPlaces.add(new CampusPlace(
                "Persiaran Newron", "Road",
                new LatLng(2.9215, 101.6562)));
        campusPlaces.add(new CampusPlace(
                "Institute for Postgraduate Studies", "Faculty",
                new LatLng(2.9210, 101.6569)));
        campusPlaces.add(new CampusPlace(
                "Student Center", "Facility",
                new LatLng(2.9216, 101.6554)));
        campusPlaces.add(new CampusPlace(
                "Main Gate", "Entrance",
                new LatLng(2.9225, 101.6548)));
        campusPlaces.add(new CampusPlace(
                "Sports Complex", "Facility",
                new LatLng(2.9198, 101.6558)));
        campusPlaces.add(new CampusPlace(
                "Cafeteria", "Food",
                new LatLng(2.9212, 101.6556)));
        campusPlaces.add(new CampusPlace(
                "Admin Building", "Facility",
                new LatLng(2.9219, 101.6551)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh pins when returning, but never wipe an active navigation route.
        if (map != null && !navigating) {
            showCampusAndObstacles();
        }
    }

    private void showCampusAndObstacles() {
        if (map == null) {
            return;
        }
        map.clear();
        markerReportIds.clear();
        selectedLocationMarker = null;

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
                position = new LatLng(report.getLat(), report.getLng());
            } else {
                double latOffset = ((fallbackIndex % 3) - 1) * 0.0009;
                double lngOffset = ((fallbackIndex / 3) % 3 - 1) * 0.0009;
                position = new LatLng(
                        CAMPUS_CENTER.latitude + latOffset,
                        CAMPUS_CENTER.longitude + lngOffset
                );
                fallbackIndex++;
            }

            float hue = report.isFacility()
                    ? BitmapDescriptorFactory.HUE_GREEN
                    : BitmapDescriptorFactory.HUE_ORANGE;

            Marker marker = map.addMarker(new MarkerOptions()
                    .position(position)
                    .title(report.getIssueType())
                    .snippet((report.isFacility() ? "Facility" : "Obstacle")
                            + " · " + report.getLocationName()
                            + " · " + ReportTimeFormat.postedBanner(report.getTimestamp()))
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));

            if (marker != null) {
                markerReportIds.put(marker, report.getId());
            }
        }
    }



    private void updateSearchSuggestions(String query) {
        if (searchPredictionsList == null) {
            return;
        }
        List<SearchSuggestion> matches = filterSuggestions(query);
        searchSuggestions.clear();
        searchSuggestions.addAll(matches);
        searchSuggestionAdapter.notifyDataSetChanged();

        if (matches.isEmpty()) {
            searchPredictionsList.setVisibility(View.GONE);
        } else {
            searchPredictionsList.setVisibility(View.VISIBLE);
            searchPredictionsList.bringToFront();
        }
    }

    private List<SearchSuggestion> filterSuggestions(String rawQuery) {
        List<SearchSuggestion> results = new ArrayList<>();
        if (rawQuery == null) {
            return results;
        }
        String trimmed = rawQuery.trim();
        String q = trimmed.toLowerCase(Locale.US);
        if (q.isEmpty()) {
            return results;
        }

        // Always offer a worldwide lookup so any typed place can be opened.
        results.add(SearchSuggestion.worldSearch(trimmed));

        for (CampusPlace place : campusPlaces) {
            if (place.name.toLowerCase(Locale.US).contains(q)
                    || place.category.toLowerCase(Locale.US).contains(q)) {
                results.add(SearchSuggestion.place(place));
            }
        }

        List<AccessibilityReport> reports =
                ObstacleReportStore.getInstance(this).getActiveCommunityReports();
        for (AccessibilityReport report : reports) {
            String location = report.getLocationName() != null ? report.getLocationName() : "";
            String issue = report.getIssueType() != null ? report.getIssueType() : "";
            if (location.toLowerCase(Locale.US).contains(q)
                    || issue.toLowerCase(Locale.US).contains(q)) {
                results.add(SearchSuggestion.report(report));
            }
        }

        if (results.size() > 12) {
            return new ArrayList<>(results.subList(0, 12));
        }
        return results;
    }

    private void commitSearch(String query) {
        String originalQuery = query != null ? query.trim() : "";
        if (originalQuery.isEmpty()) {
            hideSearchSuggestions();
            return;
        }
        // Prefer exact campus / report match if the user typed one, otherwise go worldwide.
        List<SearchSuggestion> matches = filterSuggestions(originalQuery);
        for (SearchSuggestion suggestion : matches) {
            if (!suggestion.worldSearch
                    && suggestion.title.toLowerCase(Locale.US)
                    .equals(originalQuery.toLowerCase(Locale.US))) {
                selectSearchSuggestion(suggestion);
                return;
            }
        }
        goToWorldLocation(originalQuery);
    }

    private void goToWorldLocation(String query) {
        Toast.makeText(this, "Searching for \"" + query + "\"…", Toast.LENGTH_SHORT).show();
        geocodeAddress(query, new GeocodeCallback() {
            @Override
            public void onGeocoded(double lat, double lng) {
                if (map == null) {
                    return;
                }
                LatLng latLng = new LatLng(lat, lng);
                hideSearchSuggestions();
                hideKeyboard();
                selectedReportId = null;
                selectedLat = lat;
                selectedLng = lng;
                selectedDestination = latLng;
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
                if (selectedLocationMarker != null) {
                    selectedLocationMarker.remove();
                }
                selectedLocationMarker = map.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title(query)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                showPlaceSheet(query, "Searched location", latLng, photoForPlaceName(query));
            }

            @Override
            public void onError(String message) {
                hideSearchSuggestions();
                Toast.makeText(MapActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void selectSearchSuggestion(SearchSuggestion suggestion) {
        if (suggestion == null) {
            return;
        }
        if (suggestion.worldSearch) {
            goToWorldLocation(suggestion.title);
            return;
        }
        if (map == null) {
            return;
        }
        hideSearchSuggestions();
        hideKeyboard();
        searchInput.setText(suggestion.title);
        searchInput.clearFocus();

        selectedReportId = suggestion.reportId;
        selectedLat = suggestion.position.latitude;
        selectedLng = suggestion.position.longitude;
        selectedDestination = suggestion.position;

        if (selectedLocationMarker != null) {
            selectedLocationMarker.remove();
            selectedLocationMarker = null;
        }

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(suggestion.position, 17f));
        showPlaceSheet(
                suggestion.title,
                suggestion.subtitle,
                suggestion.position,
                suggestion.photoRes
        );
    }

    private void hideSearchSuggestions() {
        if (searchPredictionsList != null) {
            searchPredictionsList.setVisibility(View.GONE);
        }
        searchSuggestions.clear();
        if (searchSuggestionAdapter != null) {
            searchSuggestionAdapter.notifyDataSetChanged();
        }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) {
            focus = searchInput;
        }
        if (focus != null) {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        }
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
        String walkLabel = GeoUtils.formatDurationMinutes(walkMinutes);

        sheetEtaText.setText(walkLabel);
        modeWalkChip.setText("Walk · " + walkLabel);
        modeDriveChip.setText("Drive · " + GeoUtils.formatDurationMinutes(driveMinutes));

        long arriveAt = System.currentTimeMillis() + walkMinutes * 60_000L;
        java.text.SimpleDateFormat timeFmt =
                new java.text.SimpleDateFormat("h:mm a", Locale.getDefault());
        sheetArriveText.setText("Arrive " + timeFmt.format(new java.util.Date(arriveAt)));

        LatLng originForMeta = navigationOriginOrCampus();
        double km = GeoUtils.distanceMeters(originForMeta, destination) / 1000.0;
        if (km < 0.05) {
            sheetRouteMeta.setText("Nearby · explore the area");
        } else {
            sheetRouteMeta.setText(String.format(Locale.US,
                    "%.1f km · fastest walking route", Math.max(0.1, km)));
        }

        locationSheet.setVisibility(View.VISIBLE);
        updateMapControlsPosition();
    }

    /** Keep +/- and my-location FABs sitting just above the white bottom sheet. */
    private void updateMapControlsPosition() {
        if (mapControls == null) {
            return;
        }
        mapControls.post(() -> {
            int sheetHeight = 0;
            if (locationSheet.getVisibility() == View.VISIBLE) {
                sheetHeight = locationSheet.getHeight();
                if (sheetHeight <= 0) {
                    locationSheet.measure(
                            View.MeasureSpec.makeMeasureSpec(locationSheet.getWidth() > 0
                                            ? locationSheet.getWidth()
                                            : getResources().getDisplayMetrics().widthPixels,
                                    View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    );
                    sheetHeight = locationSheet.getMeasuredHeight();
                }
            }
            float density = getResources().getDisplayMetrics().density;
            int marginBottom = Math.round(16 * density) + sheetHeight;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mapControls.getLayoutParams();
            params.bottomMargin = marginBottom;
            mapControls.setLayoutParams(params);
            mapControls.bringToFront();
        });
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
        return GeoUtils.walkMinutesForMeters(
                GeoUtils.distanceMeters(navigationOriginOrCampus(), destination));
    }

    /**
     * Prefer GPS when it is near campus; otherwise use campus center so demo
     * ETAs stay realistic on emulators / overseas devices.
     */
    private LatLng navigationOriginOrCampus() {
        if (routeOriginLocation != null) {
            LatLng gps = new LatLng(routeOriginLocation[0], routeOriginLocation[1]);
            if (GeoUtils.distanceMeters(gps, CAMPUS_CENTER) <= 25_000) {
                return gps;
            }
        }
        return CAMPUS_CENTER;
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
                        selectedLat = me.latitude;
                        selectedLng = me.longitude;
                        showSheet("Your location", "Centered on your current position");
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

    private void centerCameraOnUserLocationOrCampus() {
        if (map == null) {
            return;
        }
        // Prefer campus when GPS is far away (common on emulators).
        LatLng target = navigationOriginOrCampus();
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 16f));
        selectedLat = target.latitude;
        selectedLng = target.longitude;
        selectedDestination = target;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            pendingInitialCameraMove = true;
        }
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
            if (pendingInitialCameraMove) {
                pendingInitialCameraMove = false;
                centerOnUserOrCampus();
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

    private static final class SearchSuggestion {
        final String title;
        final String subtitle;
        final LatLng position;
        final String reportId;
        final int photoRes;
        final boolean worldSearch;

        private SearchSuggestion(
                String title,
                String subtitle,
                LatLng position,
                String reportId,
                int photoRes,
                boolean worldSearch
        ) {
            this.title = title;
            this.subtitle = subtitle;
            this.position = position;
            this.reportId = reportId;
            this.photoRes = photoRes;
            this.worldSearch = worldSearch;
        }

        static SearchSuggestion worldSearch(String query) {
            return new SearchSuggestion(
                    query,
                    "Search anywhere on the map",
                    null,
                    null,
                    R.drawable.campus_pathway_demo,
                    true
            );
        }

        static SearchSuggestion place(CampusPlace place) {
            return new SearchSuggestion(
                    place.name,
                    place.category + " · campus place",
                    place.position,
                    null,
                    R.drawable.campus_pathway_demo,
                    false
            );
        }

        static SearchSuggestion report(AccessibilityReport report) {
            LatLng pos = (report.getLat() != 0 || report.getLng() != 0)
                    ? new LatLng(report.getLat(), report.getLng())
                    : CAMPUS_CENTER;
            return new SearchSuggestion(
                    report.getIssueType(),
                    report.getLocationName() + " · " + report.getStatus(),
                    pos,
                    report.getId(),
                    R.drawable.sample_report_corridor,
                    false
            );
        }
    }

    private final class SearchSuggestionAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return searchSuggestions.size();
        }

        @Override
        public SearchSuggestion getItem(int position) {
            return searchSuggestions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(MapActivity.this)
                        .inflate(R.layout.item_search_suggestion, parent, false);
            }
            SearchSuggestion item = getItem(position);
            TextView title = row.findViewById(R.id.suggestionTitle);
            TextView subtitle = row.findViewById(R.id.suggestionSubtitle);
            title.setText(item.title);
            subtitle.setText(item.subtitle);
            return row;
        }
    }

    // ================== Route-finding additions ==================

    private void showRoutePanel() {
        hideSearchSuggestions();
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
        updateMapControlsPosition();
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
        String originText = currentLocationInput.getText() != null
                ? currentLocationInput.getText().toString().trim() : "";
        String destinationText = destinationInput.getText() != null
                ? destinationInput.getText().toString().trim() : "";

        if (TextUtils.isEmpty(destinationText)) {
            Toast.makeText(this, "Please enter a destination", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(originText) && routeOriginLocation == null) {
            // Default to campus / GPS so the user can still find a route
            originText = "Current Location";
            currentLocationInput.setText(originText);
        }

        Toast.makeText(this, "Finding accessible route…", Toast.LENGTH_SHORT).show();

        resolveLocationName(originText, true, new GeocodeCallback() {
            @Override
            public void onGeocoded(double originLat, double originLng) {
                resolveLocationName(destinationText, false, new GeocodeCallback() {
                    @Override
                    public void onGeocoded(double destLat, double destLng) {
                        planRoute(originLat, originLng, destLat, destLng);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(MapActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MapActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Resolves a typed place name to coordinates:
     * 1) Current Location / GPS / campus
     * 2) Known campus places & community reports
     * 3) Currently selected map destination (if the text matches the sheet)
     * 4) Android Geocoder as last resort
     */
    private void resolveLocationName(String rawName, boolean isOrigin, GeocodeCallback callback) {
        String name = rawName != null ? rawName.trim() : "";
        if (name.isEmpty()) {
            callback.onError(isOrigin ? "Enter a starting location" : "Enter a destination");
            return;
        }

        String lower = name.toLowerCase(Locale.US);
        if (isOrigin && (lower.equals("current location")
                || lower.equals("my location")
                || lower.equals("current")
                || lower.equals("me"))) {
            LatLng origin = navigationOriginOrCampus();
            callback.onGeocoded(origin.latitude, origin.longitude);
            return;
        }

        // Exact / partial match against campus catalog
        CampusPlace bestPlace = null;
        int bestScore = Integer.MAX_VALUE;
        for (CampusPlace place : campusPlaces) {
            String placeLower = place.name.toLowerCase(Locale.US);
            if (placeLower.equals(lower)) {
                callback.onGeocoded(place.position.latitude, place.position.longitude);
                return;
            }
            if (placeLower.contains(lower) || lower.contains(placeLower)) {
                int score = Math.abs(placeLower.length() - lower.length());
                if (score < bestScore) {
                    bestScore = score;
                    bestPlace = place;
                }
            } else if (place.category.toLowerCase(Locale.US).contains(lower)) {
                if (bestPlace == null) {
                    bestPlace = place;
                }
            }
        }
        if (bestPlace != null) {
            callback.onGeocoded(bestPlace.position.latitude, bestPlace.position.longitude);
            return;
        }

        // Community reports by location / issue name
        List<AccessibilityReport> reports =
                ObstacleReportStore.getInstance(this).getActiveCommunityReports();
        for (AccessibilityReport report : reports) {
            String location = report.getLocationName() != null ? report.getLocationName() : "";
            String issue = report.getIssueType() != null ? report.getIssueType() : "";
            if (location.toLowerCase(Locale.US).contains(lower)
                    || issue.toLowerCase(Locale.US).contains(lower)
                    || lower.contains(location.toLowerCase(Locale.US))) {
                double lat = report.getLat();
                double lng = report.getLng();
                if (lat == 0 && lng == 0) {
                    lat = CAMPUS_CENTER.latitude;
                    lng = CAMPUS_CENTER.longitude;
                }
                callback.onGeocoded(lat, lng);
                return;
            }
        }

        // If the destination field still shows the bottom-sheet title, use that pin
        if (!isOrigin && selectedDestination != null) {
            String sheet = sheetTitle.getText() != null ? sheetTitle.getText().toString().trim() : "";
            if (!sheet.isEmpty() && (sheet.equalsIgnoreCase(name)
                    || lower.equals("nearby places")
                    || lower.equals("selected location")
                    || lower.equals("route found"))) {
                callback.onGeocoded(selectedDestination.latitude, selectedDestination.longitude);
                return;
            }
        }

        // Last resort: system geocoder (works for real-world addresses)
        geocodeAddress(name, callback);
    }

    private void geocodeAddress(String address, GeocodeCallback callback) {
        geocodeExecutor.execute(() -> {
            try {
                double[] coords = geocodeWithGoogleApi(address);
                if (coords == null && Geocoder.isPresent()) {
                    Geocoder geocoder = new Geocoder(MapActivity.this, Locale.getDefault());
                    // Unrestricted worldwide lookup — no region bias.
                    List<Address> results = geocoder.getFromLocationName(address, 1);
                    if (results != null && !results.isEmpty()) {
                        coords = new double[]{
                                results.get(0).getLatitude(),
                                results.get(0).getLongitude()
                        };
                    }
                }
                if (coords != null) {
                    double lat = coords[0];
                    double lng = coords[1];
                    runOnUiThread(() -> callback.onGeocoded(lat, lng));
                } else {
                    runOnUiThread(() -> callback.onError(
                            "Could not find \"" + address + "\". Check the spelling or try another place."));
                }
            } catch (Exception e) {
                runOnUiThread(() -> callback.onError(
                        "Looking up \"" + address + "\" failed. Check your internet connection."));
            }
        });
    }

    /** Worldwide Geocoding API lookup (no area restriction). */
    private double[] geocodeWithGoogleApi(String address) {
        String apiKey = getGoogleMapsApiKey();
        if (apiKey == null || apiKey.isEmpty() || address == null || address.trim().isEmpty()) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            String urlStr = "https://maps.googleapis.com/maps/api/geocode/json"
                    + "?address=" + URLEncoder.encode(address.trim(), "UTF-8")
                    + "&key=" + apiKey;
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int code = conn.getResponseCode();
            java.io.InputStream stream = (code >= 200 && code < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            if (stream == null) {
                return null;
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            JSONObject root = new JSONObject(body.toString());
            if (!"OK".equals(root.optString("status"))) {
                return null;
            }
            JSONArray results = root.optJSONArray("results");
            if (results == null || results.length() == 0) {
                return null;
            }
            JSONObject location = results.getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONObject("location");
            return new double[]{location.getDouble("lat"), location.getDouble("lng")};
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Scores candidate routes using live community reports, then draws the
     * best one. If Directions API is unavailable, falls back to a local path
     * so the demo still works.
     */
    private void planRoute(double originLat, double originLng, double destLat, double destLng) {
        directionsApiClient.fetchWalkingRoutes(originLat, originLng, destLat, destLng,
                new DirectionsApiClient.RoutesCallback() {
                    @Override
                    public void onSuccess(List<RouteOption> routes) {
                        if (routes == null || routes.isEmpty()) {
                            displayRoute(
                                    buildFallbackRoute(originLat, originLng, destLat, destLng, "Destination"),
                                    originLat, originLng, destLat, destLng
                            );
                            showSearchPanel();
                            return;
                        }
                        List<AccessibilityReport> reports =
                                ObstacleReportStore.getInstance(MapActivity.this).getActiveCommunityReports();
                        List<RouteOption> ranked = analyzer.analyze(routes, reports);
                        displayRoute(ranked.get(0), originLat, originLng, destLat, destLng);
                        showSearchPanel();
                    }

                    @Override
                    public void onError(Exception e) {
                        // Directions API often blocked on demo keys — still show a usable path.
                        RouteOption fallback = buildFallbackRoute(
                                originLat, originLng, destLat, destLng, "Destination");
                        List<AccessibilityReport> reports =
                                ObstacleReportStore.getInstance(MapActivity.this).getActiveCommunityReports();
                        analyzer.analyze(java.util.Collections.singletonList(fallback), reports);
                        displayRoute(fallback, originLat, originLng, destLat, destLng);
                        showSearchPanel();
                        Toast.makeText(MapActivity.this,
                                "Showing local route (live Directions unavailable)",
                                Toast.LENGTH_SHORT).show();
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

        // Clear only the PREVIOUS route's polyline/pins — leave campus
        // places and all obstacle pins exactly as they are.
        if (currentRoutePolyline != null) {
            currentRoutePolyline.remove();
        }
        if (currentRouteOriginMarker != null) {
            currentRouteOriginMarker.remove();
        }
        if (currentRouteDestinationMarker != null) {
            currentRouteDestinationMarker.remove();
        }
        PolylineOptions polylineOptions = new PolylineOptions()
                .color(Color.parseColor("#2A6DF4"))
                .width(12f);

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (double[] point : route.getPoints()) {
            LatLng latLng = new LatLng(point[0], point[1]);
            polylineOptions.add(latLng);
            boundsBuilder.include(latLng);
        }
        currentRoutePolyline = map.addPolyline(polylineOptions);

        currentRouteOriginMarker = map.addMarker(new MarkerOptions()
                .position(new LatLng(originLat, originLng))
                .title("Start"));

        currentRouteDestinationMarker = map.addMarker(new MarkerOptions()
                .position(new LatLng(destLat, destLng))
                .title("Destination")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Obstacles already have pins from showCampusAndObstacles() — no
        // need to re-add markers for the ones matched on this route, they're
        // already visible on the map.

        map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120));

        selectedReportId = null;
        selectedLat = destLat;
        selectedLng = destLng;
        String verdict = route.getAccessibilityScore() >= 60
                ? "Likely accessible (score: " + route.getAccessibilityScore() + "/100)"
                : "May be difficult for wheelchair users (score: " + route.getAccessibilityScore() + "/100)";
        selectedDestination = new LatLng(destLat, destLng);
        showPlaceSheet("Route found", verdict, selectedDestination, photoForPlaceName("Route found"));
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
        double meters = GeoUtils.distanceMeters(
                new LatLng(originLat, originLng),
                new LatLng(destLat, destLng)
        );
        int seconds = GeoUtils.walkSecondsForMeters(meters);
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
        if (mapControls != null) {
            mapControls.setVisibility(View.GONE);
        }
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

        for (AccessibilityReport obstacle : journeyObstacles) {
            map.addMarker(new MarkerOptions()
                    .position(new LatLng(obstacle.getLat(), obstacle.getLng()))
                    .title(obstacle.getIssueType())
                    .snippet(obstacle.getLocationName())
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
        int fallbackIndex = 0;
        for (AccessibilityReport report : reports) {
            if (!report.isObstacle()) {
                continue;
            }
            AccessibilityReport positioned = reportWithMapPosition(report, fallbackIndex);
            if (report.getLat() == 0 && report.getLng() == 0) {
                fallbackIndex++;
            }
            journeyObstacles.add(positioned);
        }

        if (route != null) {
            for (AccessibilityReport report : route.getReportsOnRoute()) {
                if (!report.isObstacle()) {
                    continue;
                }
                boolean already = false;
                for (AccessibilityReport existing : journeyObstacles) {
                    if (existing.getId() != null && existing.getId().equals(report.getId())) {
                        already = true;
                        break;
                    }
                }
                if (!already) {
                    journeyObstacles.add(report);
                }
            }
        }

        // Ensure at least one obstacle sits on the path so the demo alert fires mid-journey.
        boolean anyOnRoute = false;
        for (AccessibilityReport report : journeyObstacles) {
            if (GeoUtils.isNearPath(
                    report.getLat(), report.getLng(), navigationPath, GeoUtils.ON_ROUTE_RADIUS_METERS)) {
                anyOnRoute = true;
                break;
            }
        }
        if (!anyOnRoute && navigationPath.size() > 4
                && !dismissedObstacleAlerts.contains("demo-path-obstacle")) {
            int mid = Math.max(2, (int) (navigationPath.size() * 0.45));
            LatLng midPoint = navigationPath.get(mid);
            long now = System.currentTimeMillis();
            journeyObstacles.add(new AccessibilityReport(
                    "demo-path-obstacle",
                    "Pathway",
                    midPoint.latitude,
                    midPoint.longitude,
                    "Blocked pathway",
                    AccessibilityReport.CATEGORY_OBSTACLE,
                    "Temporary blockage on the walking path",
                    now,
                    0L,
                    null,
                    0,
                    0,
                    AccessibilityReport.STATUS_ACTIVE,
                    false,
                    null
            ));
        }
    }

    /** Prefer real report coordinates; scatter only for legacy reports missing lat/lng. */
    private AccessibilityReport reportWithMapPosition(AccessibilityReport report, int fallbackIndex) {
        if (report.getLat() != 0 || report.getLng() != 0) {
            return report;
        }
        double latOffset = ((fallbackIndex % 3) - 1) * 0.0009;
        double lngOffset = ((fallbackIndex / 3) % 3 - 1) * 0.0009;
        return new AccessibilityReport(
                report.getId(),
                report.getLocationName(),
                CAMPUS_CENTER.latitude + latOffset,
                CAMPUS_CENTER.longitude + lngOffset,
                report.getIssueType(),
                report.getCategory(),
                report.getDescription(),
                report.getTimestamp(),
                report.getLastVerifiedAt(),
                report.getPhotoPath(),
                report.getStillThereCount(),
                report.getNotThereCount(),
                report.getStatus(),
                report.isSubmittedByMe(),
                report.getReporterId()
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
                double d = GeoUtils.distanceMeters(current, new LatLng(upcoming.startLat, upcoming.startLng));
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
        AccessibilityReport nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (AccessibilityReport obstacle : journeyObstacles) {
            if (obstacle.getId() != null && dismissedObstacleAlerts.contains(obstacle.getId())) {
                continue;
            }
            if (!isObstacleOnRemainingPath(obstacle)) {
                continue;
            }
            double dist = GeoUtils.distanceMeters(
                    current, new LatLng(obstacle.getLat(), obstacle.getLng()));
            if (dist <= OBSTACLE_ALERT_METERS && dist < nearestDist) {
                nearest = obstacle;
                nearestDist = dist;
            }
        }

        if (nearest != null) {
            showObstacleAlert(nearest, (int) Math.round(nearestDist));
        }
    }

    private boolean isObstacleOnRemainingPath(AccessibilityReport obstacle) {
        if (navigationPath.isEmpty() || navigationIndex >= navigationPath.size() - 1) {
            return false;
        }
        List<LatLng> remaining = navigationPath.subList(navigationIndex, navigationPath.size());
        return GeoUtils.isNearPath(
                obstacle.getLat(), obstacle.getLng(), remaining, GeoUtils.ON_ROUTE_RADIUS_METERS);
    }

    private void showObstacleAlert(AccessibilityReport obstacle, int distanceMetersAhead) {
        pendingObstacleAlert = obstacle;
        obstaclePromptVisible = true;
        navigationHandler.removeCallbacks(navigationTick);

        String type = obstacle.getIssueType() != null ? obstacle.getIssueType() : "Obstacle";
        obstacleAlertMessage.setText(String.format(Locale.US,
                "%s is about %d m ahead on your route (~1 min). "
                        + "We found a safer path that avoids it. Switch routes?",
                type,
                Math.max(1, distanceMetersAhead)));
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
        AccessibilityReport obstacle = pendingObstacleAlert;
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
                obstacle.getIssueType()
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
            meters += GeoUtils.distanceMeters(
                    new LatLng(points.get(i)[0], points.get(i)[1]),
                    new LatLng(points.get(i + 1)[0], points.get(i + 1)[1])
            );
        }
        int seconds = GeoUtils.walkSecondsForMeters(meters);
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
            navStepDistance.setText(GeoUtils.formatMeters(remainingStepMeters(step)));
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
            navStepDistance.setText(GeoUtils.formatMeters(remainingPathMeters()));
            navTurnIcon.setText("↑");
            navThenText.setText("Then ✓ Arrive");
        }

        int remainMeters = remainingPathMeters();
        int remainMinutes = remainingMinutesFromRoute(remainMeters);
        if (navigationIndex >= navigationPath.size() - 1) {
            remainMinutes = 0;
        }
        navEtaText.setText(GeoUtils.formatDurationMinutes(remainMinutes));

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
            total += GeoUtils.distanceMeters(navigationPath.get(i), navigationPath.get(i + 1));
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
        if (mapControls != null) {
            mapControls.setVisibility(View.VISIBLE);
        }
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
        ObstacleReportStore.getInstance(this).removeListener(this);
        navigating = false;
        navigationHandler.removeCallbacks(navigationTick);
        geocodeExecutor.shutdown();
        super.onDestroy();
    }
}
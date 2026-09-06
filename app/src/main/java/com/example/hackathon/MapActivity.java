package com.example.hackathon;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
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
import com.example.hackathon.utils.ReportTimeFormat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private ImageButton backButton;

    private String selectedReportId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        searchInput = findViewById(R.id.searchInput);
        locationSheet = findViewById(R.id.locationSheet);
        sheetTitle = findViewById(R.id.sheetTitle);
        sheetSubtitle = findViewById(R.id.sheetSubtitle);
        reportObstacleButton = findViewById(R.id.reportObstacleButton);
        viewReportsButton = findViewById(R.id.viewReportsButton);
        myLocationButton = findViewById(R.id.myLocationButton);
        backButton = findViewById(R.id.backButton);

        seedCampusPlaces();

        backButton.setOnClickListener(v -> finish());

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

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
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
            showSheet(
                    "Selected location",
                    String.format(Locale.US, "%.5f, %.5f · tap Report to warn others",
                            latLng.latitude, latLng.longitude)
            );
        });

        map.setOnMarkerClickListener(marker -> {
            String reportId = markerReportIds.get(marker);
            selectedReportId = reportId;
            if (reportId != null) {
                AccessibilityReport report = ObstacleReportStore.getInstance(this).getById(reportId);
                if (report != null) {
                    showSheet(
                            report.getIssueType(),
                            ReportTimeFormat.postedBanner(report.getTimestamp())
                                    + "\n"
                                    + report.getLocationName()
                                    + " · "
                                    + report.getStatus()
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

        showSheet(
                "Nearby places",
                "Live map · red pins are community obstacle reports"
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
                showSheet(place.name, place.category + " · nearby");
                return;
            }
        }

        List<AccessibilityReport> reports =
                ObstacleReportStore.getInstance(this).getActiveCommunityReports();
        for (AccessibilityReport report : reports) {
            if (report.getLocationName().toLowerCase(Locale.US).contains(q)
                    || report.getIssueType().toLowerCase(Locale.US).contains(q)) {
                selectedReportId = report.getId();
                showSheet(
                        report.getIssueType(),
                        ReportTimeFormat.postedBanner(report.getTimestamp())
                                + "\n"
                                + report.getLocationName()
                                + " · "
                                + report.getStatus()
                );
                // Approximate camera to campus center for demo markers
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(CAMPUS_CENTER, 16.5f));
                return;
            }
        }

        Toast.makeText(this, "No matching place found", Toast.LENGTH_SHORT).show();
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
                        showSheet("Your location", "Centered on your current position");
                    } else if (map != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(CAMPUS_CENTER, 16f));
                        showSheet("Map center", "Showing your area");
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
}

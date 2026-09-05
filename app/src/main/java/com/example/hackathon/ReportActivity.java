package com.example.hackathon;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.hackathon.utils.ObstacleReportStore;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class ReportActivity extends AppCompatActivity {

    /** Pass these when launching from a screen that already has coordinates (e.g. MapActivity). */
    public static final String EXTRA_LAT = "extra_lat";
    public static final String EXTRA_LNG = "extra_lng";

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 300;

    private TextInputEditText locationInput;
    private TextInputEditText descriptionInput;
    private AutoCompleteTextView issueSpinner;
    private MaterialButton submitButton;
    private ImageButton backButton;

    private FusedLocationProviderClient fusedLocationClient;

    // Coordinates to attach to the report. Set either from Intent extras
    // (tapped on the map) or fetched from GPS as a fallback.
    private Double reportLat;
    private Double reportLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_report);

        locationInput = findViewById(R.id.locationInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        issueSpinner = findViewById(R.id.issueSpinner);
        submitButton = findViewById(R.id.submitButton);
        backButton = findViewById(R.id.backButton);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (getIntent().hasExtra(EXTRA_LAT) && getIntent().hasExtra(EXTRA_LNG)) {
            reportLat = getIntent().getDoubleExtra(EXTRA_LAT, 0);
            reportLng = getIntent().getDoubleExtra(EXTRA_LNG, 0);
        } else {
            // No coordinates were passed in — fall back to the device's current location.
            fetchCurrentLocationAsFallback();
        }

        String[] obstacleTypes = {
                "Illegal Parking",
                "Pothole",
                "Construction",
                "Overgrown Vegetation",
                "Blocked Ramp",
                "Blocked Tactile Path",
                "Broken Crossing",
                "Open Drain",
                "Temporary Barrier",
                "Debris / Obstacle",
                "Other"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                obstacleTypes
        );

        issueSpinner.setAdapter(adapter);

        backButton.setOnClickListener(v -> finish());

        submitButton.setOnClickListener(v -> {
            String location = locationInput.getText() != null
                    ? locationInput.getText().toString().trim() : "";
            String description = descriptionInput.getText() != null
                    ? descriptionInput.getText().toString().trim() : "";
            String issueType = issueSpinner.getText() != null
                    ? issueSpinner.getText().toString().trim() : "";

            if (location.isEmpty() || description.isEmpty() || issueType.isEmpty()) {
                Toast.makeText(
                        ReportActivity.this,
                        "Please fill in all fields",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            if (reportLat == null || reportLng == null) {
                Toast.makeText(
                        ReportActivity.this,
                        "Still finding location, please try again in a moment",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            ObstacleReportStore.getInstance(this)
                    .addReport(location, reportLat, reportLng, issueType, description);

            Toast.makeText(
                    ReportActivity.this,
                    "Obstacle reported — thank you for helping the community!",
                    Toast.LENGTH_LONG
            ).show();

            finish();
        });
    }

    private void fetchCurrentLocationAsFallback() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                reportLat = location.getLatitude();
                reportLng = location.getLongitude();
            } else {
                Toast.makeText(this, "Could not determine location for this report", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE
                && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocationAsFallback();
        } else {
            Toast.makeText(this, "Location permission is needed to submit a report", Toast.LENGTH_SHORT).show();
        }
    }
}
package com.example.hackathon;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.hackathon.models.AccessibilityReport;
import com.example.hackathon.utils.ObstacleReportStore;
import com.example.hackathon.utils.ReportTimeFormat;
import com.example.hackathon.utils.SampleReportPhotos;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ReportActivity extends AppCompatActivity {

    /** Pass these when launching from a screen that already has coordinates (e.g. MapActivity). */
    public static final String EXTRA_LAT = "extra_lat";
    public static final String EXTRA_LNG = "extra_lng";

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 300;

    private static final String[] OBSTACLE_TYPES = {
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

    private static final String[] FACILITY_TYPES = {
            "Ramp",
            "Tactile Pavement",
            "Elevator",
            "Accessible Restroom",
            "Accessible Parking",
            "Handrail",
            "Other Facility"
    };

    private TextInputEditText locationInput;
    private TextInputEditText descriptionInput;
    private TextInputLayout issueTypeLayout;
    private AutoCompleteTextView issueSpinner;
    private MaterialButton submitButton;
    private MaterialButton takePhotoButton;
    private MaterialButton choosePhotoButton;
    private ImageButton backButton;
    private ImageView photoPreview;
    private TextView photoPlaceholder;
    private TextView photoStatusText;

    private Uri cameraCaptureUri;
    private Uri selectedPhotoUri;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchCamera();
                } else {
                    Toast.makeText(this, "Camera permission is needed to take a photo", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                seedGalleryPhotos();
                openGalleryPicker();
            });

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraCaptureUri != null) {
                    selectedPhotoUri = cameraCaptureUri;
                    showPhotoPreview(selectedPhotoUri);
                } else {
                    Toast.makeText(this, "Photo not captured", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedPhotoUri = uri;
                    showPhotoPreview(uri);
                }
            });
    private MaterialButtonToggleGroup categoryToggle;
    private TextView screenTitle;
    private TextView screenSubtitle;
    private TextView screenDescription;

    private FusedLocationProviderClient fusedLocationClient;

    // Coordinates to attach to the report. Set either from Intent extras
    // (tapped on the map) or fetched from GPS as a fallback.
    private Double reportLat;
    private Double reportLng;

    // Which report category is currently selected in the toggle.
    private String selectedCategory = AccessibilityReport.CATEGORY_OBSTACLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_report);

        locationInput = findViewById(R.id.locationInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        issueTypeLayout = findViewById(R.id.issueTypeLayout);
        issueSpinner = findViewById(R.id.issueSpinner);
        submitButton = findViewById(R.id.submitButton);
        takePhotoButton = findViewById(R.id.takePhotoButton);
        choosePhotoButton = findViewById(R.id.choosePhotoButton);
        backButton = findViewById(R.id.backButton);
        photoPreview = findViewById(R.id.photoPreview);
        photoPlaceholder = findViewById(R.id.photoPlaceholder);
        photoStatusText = findViewById(R.id.photoStatusText);

        // Put sample photos into device gallery so Gallery → Photos can pick them
        seedGalleryPhotos();
        categoryToggle = findViewById(R.id.categoryToggle);
        screenTitle = findViewById(R.id.screenTitle);
        screenSubtitle = findViewById(R.id.screenSubtitle);
        screenDescription = findViewById(R.id.screenDescription);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (getIntent().hasExtra(EXTRA_LAT) && getIntent().hasExtra(EXTRA_LNG)) {
            reportLat = getIntent().getDoubleExtra(EXTRA_LAT, 0);
            reportLng = getIntent().getDoubleExtra(EXTRA_LNG, 0);
        } else {
            // No coordinates were passed in — fall back to the device's current location.
            fetchCurrentLocationAsFallback();
        }

        applyCategory(AccessibilityReport.CATEGORY_OBSTACLE);

        categoryToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.facilityToggleButton) {
                applyCategory(AccessibilityReport.CATEGORY_FACILITY);
            } else {
                applyCategory(AccessibilityReport.CATEGORY_OBSTACLE);
            }
        });

        backButton.setOnClickListener(v -> finish());
        takePhotoButton.setOnClickListener(v -> ensureCameraThenCapture());
        choosePhotoButton.setOnClickListener(v -> onGalleryClicked());

        submitButton.setOnClickListener(v -> submitReport());
    }

    private void seedGalleryPhotos() {
        // On Android 10+ MediaStore insert does not need storage permission.
        // On older APIs, request WRITE_EXTERNAL_STORAGE once if needed.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        SampleReportPhotos.ensureInGallery(this);
    }

    private void onGalleryClicked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        seedGalleryPhotos();
        openGalleryPicker();
    }

    private void openGalleryPicker() {
        pickImageLauncher.launch("image/*");
    }

    private void ensureCameraThenCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "report_photos");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(this, "Could not prepare photo storage", Toast.LENGTH_SHORT).show();
                return;
            }
            File photoFile = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            cameraCaptureUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photoFile
            );
            takePictureLauncher.launch(cameraCaptureUri);
        } catch (Exception e) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) == null) {
                Toast.makeText(this, "No camera app available — use Gallery instead", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Could not open camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showPhotoPreview(Uri uri) {
        photoPreview.setImageURI(uri);
        photoPreview.setVisibility(View.VISIBLE);
        photoPlaceholder.setVisibility(View.GONE);
        photoStatusText.setText("Photo attached");
        photoStatusText.setTextColor(0xFF059669);
        photoStatusText.setVisibility(View.VISIBLE);
    }

    private void submitReport() {
        String location = locationInput.getText() != null
                ? locationInput.getText().toString().trim() : "";
        String description = descriptionInput.getText() != null
                ? descriptionInput.getText().toString().trim() : "";
        String issueType = issueSpinner.getText() != null
                ? issueSpinner.getText().toString().trim() : "";

        if (location.isEmpty() || description.isEmpty() || issueType.isEmpty()) {
            Toast.makeText(this, "Please fill in location, type, and description", Toast.LENGTH_SHORT).show();
            return;
        }

        if (reportLat == null || reportLng == null) {
            Toast.makeText(this, "Still finding location, please try again in a moment", Toast.LENGTH_SHORT).show();
            return;
        }

        String savedPhotoPath = null;
        if (selectedPhotoUri != null) {
            savedPhotoPath = persistPhoto(selectedPhotoUri);
            if (savedPhotoPath == null) {
                Toast.makeText(this, "Could not save photo — try again or submit without it", Toast.LENGTH_LONG).show();
                return;
            }
        }

        AccessibilityReport created = ObstacleReportStore.getInstance(this)
                .addReport(location, reportLat, reportLng, issueType, selectedCategory, description, savedPhotoPath);

        String thankYouMessage = AccessibilityReport.CATEGORY_FACILITY.equals(selectedCategory)
                ? "Facility reported — thank you for helping the community!"
                : "Report submitted · " + ReportTimeFormat.postedBanner(created.getTimestamp());
        Toast.makeText(this, thankYouMessage, Toast.LENGTH_LONG).show();
        finish();
    }

    private String persistPhoto(Uri source) {
        try {
            File dir = new File(getFilesDir(), "report_photos");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File dest = new File(dir, "report_" + System.currentTimeMillis() + ".jpg");
            try (InputStream in = getContentResolver().openInputStream(source);
                 OutputStream out = new FileOutputStream(dest)) {
                if (in == null) {
                    return null;
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            return dest.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Switches the dropdown options, hint text, and header copy between the
     * obstacle-reporting and facility-reporting flows.
     */
    private void applyCategory(String category) {
        selectedCategory = category;
        boolean isFacility = AccessibilityReport.CATEGORY_FACILITY.equals(category);

        String[] types = isFacility ? FACILITY_TYPES : OBSTACLE_TYPES;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                types
        );
        issueSpinner.setAdapter(adapter);
        issueSpinner.setText("", false); // clear any previously selected value

        issueTypeLayout.setHint(isFacility ? "Facility type" : "Obstacle type");
        screenTitle.setText(isFacility ? "Report a Facility" : "Report an Obstacle");
        screenSubtitle.setText(isFacility
                ? "Community Facility Reporting"
                : "Community Obstacle Reporting");
        screenDescription.setText(isFacility
                ? "Let other wheelchair and visually impaired users know about ramps, elevators, and accessible facilities nearby."
                : "Warn other visually impaired users about barriers you encounter while walking.");
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
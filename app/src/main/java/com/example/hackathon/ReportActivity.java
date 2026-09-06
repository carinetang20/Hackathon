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
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.hackathon.models.AccessibilityReport;
import com.example.hackathon.utils.ObstacleReportStore;
import com.example.hackathon.utils.ReportTimeFormat;
import com.example.hackathon.utils.SampleReportPhotos;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ReportActivity extends AppCompatActivity {

    private TextInputEditText locationInput;
    private TextInputEditText descriptionInput;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        locationInput = findViewById(R.id.locationInput);
        descriptionInput = findViewById(R.id.descriptionInput);
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

        issueSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                obstacleTypes
        ));

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

        String savedPhotoPath = null;
        if (selectedPhotoUri != null) {
            savedPhotoPath = persistPhoto(selectedPhotoUri);
            if (savedPhotoPath == null) {
                Toast.makeText(this, "Could not save photo — try again or submit without it", Toast.LENGTH_LONG).show();
                return;
            }
        }

        AccessibilityReport created = ObstacleReportStore.getInstance(this)
                .addReport(location, issueType, description, savedPhotoPath);

        Toast.makeText(
                this,
                "Report submitted · " + ReportTimeFormat.postedBanner(created.getTimestamp()),
                Toast.LENGTH_LONG
        ).show();
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
}

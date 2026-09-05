package com.example.hackathon;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.hackathon.utils.CampusLocator;
import com.example.hackathon.utils.NavigationGuidance;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Campus pathway scan: camera + GPS campus landmarks + spoken guidance
 * for MMU walkways and stairs (not indoor rooms).
 */
public class ScanAssistActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int REQUEST_CAMERA = 2001;
    private static final int REQUEST_LOCATION = 2002;

    private PreviewView previewView;
    private MaterialButton scanButton;
    private MaterialButton repeatButton;
    private TextView guidanceTitle;
    private TextView guidanceText;
    private TextView detectedLabels;
    private ImageButton backButton;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ImageLabeler labeler;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String lastSpoken = "";

    private FusedLocationProviderClient fusedLocationClient;
    private Double lastLat;
    private Double lastLng;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_assist);

        previewView = findViewById(R.id.previewView);
        scanButton = findViewById(R.id.scanButton);
        repeatButton = findViewById(R.id.repeatButton);
        guidanceTitle = findViewById(R.id.guidanceTitle);
        guidanceText = findViewById(R.id.guidanceText);
        detectedLabels = findViewById(R.id.detectedLabels);
        backButton = findViewById(R.id.backButton);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        cameraExecutor = Executors.newSingleThreadExecutor();
        labeler = ImageLabeling.getClient(
                new ImageLabelerOptions.Builder()
                        .setConfidenceThreshold(0.50f)
                        .build()
        );
        tts = new TextToSpeech(this, this);

        backButton.setOnClickListener(v -> finish());

        scanButton.setOnClickListener(v -> {
            speak("Scanning the campus pathway.");
            refreshLocationThenScan();
        });

        repeatButton.setOnClickListener(v -> {
            if (!lastSpoken.isEmpty()) {
                speak(lastSpoken);
            }
        });

        if (hasCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA
            );
        }

        ensureLocationPermission();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            int result = tts.setLanguage(Locale.US);
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED;
            tts.setSpeechRate(0.9f);
            if (ttsReady && hasCameraPermission()) {
                speak("MMU campus pathway scan ready. Point at a campus walkway or stairs, then tap Scan campus pathway.");
            }
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureLocationPermission() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION
            );
        } else {
            refreshLocationOnly();
        }
    }

    private void refreshLocationOnly() {
        if (!hasLocationPermission()) {
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this::storeLocation);
    }

    private void storeLocation(Location location) {
        if (location != null) {
            lastLat = location.getLatitude();
            lastLng = location.getLongitude();
        }
    }

    private void refreshLocationThenScan() {
        if (!hasLocationPermission()) {
            captureAndAnalyze();
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    storeLocation(location);
                    captureAndAnalyze();
                })
                .addOnFailureListener(e -> captureAndAnalyze());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture
                );
            } catch (Exception e) {
                Toast.makeText(this, "Unable to start camera", Toast.LENGTH_LONG).show();
                guidanceTitle.setText("Camera unavailable");
                guidanceText.setText("Allow camera access and try again.");
                speak("Camera unavailable. Allow camera access and try again.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureAndAnalyze() {
        if (imageCapture == null) {
            speak("Camera is not ready yet.");
            return;
        }

        scanButton.setEnabled(false);
        guidanceTitle.setText("Scanning campus…");
        guidanceText.setText(R.string.scan_listening);

        File photoFile = new File(getCacheDir(), "dislocator_scan.jpg");
        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputOptions,
                cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        try {
                            InputImage image = InputImage.fromFilePath(
                                    ScanAssistActivity.this,
                                    Uri.fromFile(photoFile)
                            );
                            analyzeImage(image);
                        } catch (IOException e) {
                            NavigationGuidance.Result result = buildCampusGuidance(new ArrayList<>());
                            mainHandler.post(() -> applyGuidance(result, new ArrayList<>()));
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        mainHandler.post(() -> {
                            scanButton.setEnabled(true);
                            guidanceTitle.setText("Scan failed");
                            guidanceText.setText("Could not capture image. Try again.");
                            speak("Scan failed. Please try again.");
                        });
                    }
                }
        );
    }

    private void analyzeImage(InputImage image) {
        labeler.process(image)
                .addOnSuccessListener(labels -> {
                    List<String> names = new ArrayList<>();
                    for (ImageLabel label : labels) {
                        names.add(label.getText());
                    }
                    NavigationGuidance.Result result = buildCampusGuidance(names);
                    mainHandler.post(() -> applyGuidance(result, names));
                })
                .addOnFailureListener(e -> {
                    NavigationGuidance.Result result = buildCampusGuidance(new ArrayList<>());
                    mainHandler.post(() -> applyGuidance(result, new ArrayList<>()));
                });
    }

    private NavigationGuidance.Result buildCampusGuidance(List<String> names) {
        // This app is for MMU campus pathways. Prefer GPS place when on campus;
        // otherwise still guide as MMU Cyberjaya campus (demo / nearby).
        String area;
        String hint;
        if (lastLat != null && lastLng != null && CampusLocator.isOnCampus(lastLat, lastLng)) {
            area = CampusLocator.campusAreaDescription(lastLat, lastLng);
            hint = CampusLocator.pathwayHint(lastLat, lastLng);
        } else {
            area = "on MMU Cyberjaya campus";
            hint = "outdoor campus walkway or stairs";
        }
        return NavigationGuidance.build(names, area, hint);
    }

    private void applyGuidance(NavigationGuidance.Result result, List<String> labels) {
        scanButton.setEnabled(true);
        guidanceTitle.setText(result.summary);
        guidanceText.setText(result.spoken);
        lastSpoken = result.spoken;
        repeatButton.setVisibility(View.VISIBLE);

        if (labels != null && !labels.isEmpty()) {
            detectedLabels.setVisibility(View.VISIBLE);
            detectedLabels.setText("Detected: " + String.join(", ", labels));
        } else {
            detectedLabels.setVisibility(View.GONE);
        }

        speak(result.spoken);
    }

    private void speak(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        lastSpoken = message;
        if (ttsReady && tts != null) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "dislocator_guide");
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
                speak("Camera ready. Point at a campus walkway or stairs, then tap Scan campus pathway.");
            } else {
                guidanceTitle.setText("Camera permission needed");
                guidanceText.setText("Camera access is needed to scan campus walkways and stairs.");
                speak("Camera permission is needed to scan the campus pathway.");
            }
        } else if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                refreshLocationOnly();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (labeler != null) {
            labeler.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        super.onDestroy();
    }
}

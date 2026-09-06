package com.example.hackathon;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hackathon.utils.NavigationGuidance;
import com.google.android.material.button.MaterialButton;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 360° look-around of the campus walkway with spoken scan guidance.
 */
public class ScanAssistActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "ScanAssist";
    private static final int PREVIEW_RES = R.drawable.campus_360;
    private static final int SCAN_RES = R.drawable.campus_pathway_demo;

    private enum LookDirection { LEFT, CENTER, RIGHT, BEHIND }

    private ImageView campusPreviewImage;
    private MaterialButton scanButton;
    private MaterialButton repeatButton;
    private MaterialButton turnLeftButton;
    private MaterialButton turnRightButton;
    private TextView guidanceTitle;
    private TextView guidanceText;
    private TextView detectedLabels;
    private TextView lookDirectionText;

    private ExecutorService analyzeExecutor;
    private ImageLabeler labeler;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String lastSpoken = "";
    private String pendingSpeech;
    private AudioManager audioManager;

    private final Matrix imageMatrix = new Matrix();
    private float scale = 1f;
    private float maxTransX;
    private float currentTransX;
    private float lastTouchX;
    private boolean panning;
    private float bmpW;
    private float bmpH;
    private LookDirection lookDirection = LookDirection.CENTER;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_assist);

        campusPreviewImage = findViewById(R.id.campusPreviewImage);
        scanButton = findViewById(R.id.scanButton);
        repeatButton = findViewById(R.id.repeatButton);
        turnLeftButton = findViewById(R.id.turnLeftButton);
        turnRightButton = findViewById(R.id.turnRightButton);
        guidanceTitle = findViewById(R.id.guidanceTitle);
        guidanceText = findViewById(R.id.guidanceText);
        detectedLabels = findViewById(R.id.detectedLabels);
        lookDirectionText = findViewById(R.id.lookDirectionText);
        ImageButton backButton = findViewById(R.id.backButton);

        analyzeExecutor = Executors.newSingleThreadExecutor();
        labeler = ImageLabeling.getClient(
                new ImageLabelerOptions.Builder()
                        .setConfidenceThreshold(0.45f)
                        .build()
        );

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        ensureAudibleVolume();
        initTts();

        campusPreviewImage.setImageResource(PREVIEW_RES);
        setupLookAround();

        backButton.setOnClickListener(v -> finish());
        turnLeftButton.setOnClickListener(v -> {
            nudgeLook(+0.22f);
            speak("Turning left.");
        });
        turnRightButton.setOnClickListener(v -> {
            nudgeLook(-0.22f);
            speak("Turning right.");
        });
        scanButton.setOnClickListener(v -> {
            speak("Scanning what you are facing.");
            scanSurroundings();
        });
        repeatButton.setOnClickListener(v -> {
            if (!lastSpoken.isEmpty()) {
                speak(lastSpoken);
            } else {
                speak("Nothing to repeat yet. Tap Scan first.");
            }
        });
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(this, this, "com.google.android.tts");
        } catch (Exception e) {
            tts = new TextToSpeech(this, this);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupLookAround() {
        campusPreviewImage.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        campusPreviewImage.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        fitImageForPan();
                        setTranslation(-maxTransX / 2f);
                        updateLookDirectionFromPan();
                    }
                }
        );

        campusPreviewImage.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    panning = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!panning) {
                        return false;
                    }
                    float dx = event.getX() - lastTouchX;
                    lastTouchX = event.getX();
                    setTranslation(currentTransX + dx);
                    updateLookDirectionFromPan();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    panning = false;
                    updateLookDirectionFromPan();
                    return true;
                default:
                    return false;
            }
        });
    }

    private void fitImageForPan() {
        int viewW = campusPreviewImage.getWidth();
        int viewH = campusPreviewImage.getHeight();
        if (viewW == 0 || viewH == 0) {
            return;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), PREVIEW_RES, opts);
        bmpW = opts.outWidth;
        bmpH = opts.outHeight;
        if (bmpW <= 0 || bmpH <= 0) {
            campusPreviewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            campusPreviewImage.setImageResource(SCAN_RES);
            return;
        }

        campusPreviewImage.setScaleType(ImageView.ScaleType.MATRIX);
        float scaleY = viewH / bmpH;
        float minScaleX = (viewW * 2.4f) / bmpW;
        scale = Math.max(scaleY, minScaleX);
        maxTransX = Math.max(0f, bmpW * scale - viewW);
        currentTransX = -maxTransX / 2f;
        applyMatrix();
    }

    private void applyMatrix() {
        if (bmpW <= 0 || bmpH <= 0) {
            return;
        }
        imageMatrix.reset();
        imageMatrix.postScale(scale, scale);
        float ty = (campusPreviewImage.getHeight() - bmpH * scale) / 2f;
        imageMatrix.postTranslate(currentTransX, ty);
        campusPreviewImage.setImageMatrix(imageMatrix);
    }

    private void setTranslation(float transX) {
        if (maxTransX <= 0f) {
            return;
        }
        float wrapped = transX;
        while (wrapped > 0) {
            wrapped -= maxTransX;
        }
        while (wrapped < -maxTransX) {
            wrapped += maxTransX;
        }
        currentTransX = wrapped;
        applyMatrix();
    }

    private void nudgeLook(float fractionOfWidth) {
        if (maxTransX <= 0f) {
            campusPreviewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            campusPreviewImage.setImageResource(SCAN_RES);
            return;
        }
        setTranslation(currentTransX + fractionOfWidth * campusPreviewImage.getWidth());
        updateLookDirectionFromPan();
    }

    private void updateLookDirectionFromPan() {
        if (maxTransX <= 0f) {
            lookDirection = LookDirection.CENTER;
        } else {
            float progress = Math.abs(currentTransX) / maxTransX;
            if (progress < 0.2f) {
                lookDirection = LookDirection.LEFT;
            } else if (progress < 0.4f) {
                lookDirection = LookDirection.CENTER;
            } else if (progress < 0.65f) {
                lookDirection = LookDirection.RIGHT;
            } else {
                lookDirection = LookDirection.BEHIND;
            }
        }
        updateLookUi();
    }

    private void updateLookUi() {
        switch (lookDirection) {
            case LEFT:
                lookDirectionText.setText("Looking left · Surau / pillars");
                break;
            case RIGHT:
                lookDirectionText.setText("Looking right · courtyard & stairs");
                break;
            case BEHIND:
                lookDirectionText.setText("Looking around · keep turning to the path");
                break;
            case CENTER:
            default:
                lookDirectionText.setText("Looking ahead · path to courtyard");
                break;
        }
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            Log.e(TAG, "Google TTS init failed, trying default engine");
            tts = new TextToSpeech(this, status2 -> {
                if (status2 == TextToSpeech.SUCCESS) {
                    configureTtsAndSpeakWelcome();
                } else {
                    Toast.makeText(this,
                            "Voice guidance unavailable — enable Text-to-Speech in device settings",
                            Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        configureTtsAndSpeakWelcome();
    }

    private void configureTtsAndSpeakWelcome() {
        if (tts == null) {
            return;
        }

        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());

        int lang = tts.setLanguage(Locale.US);
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            lang = tts.setLanguage(Locale.ENGLISH);
        }
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            lang = tts.setLanguage(Locale.getDefault());
        }

        ttsReady = lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED;
        tts.setSpeechRate(0.9f);
        tts.setPitch(1.0f);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "TTS started: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) { }

            @Override
            public void onError(String utteranceId) {
                mainHandler.post(() ->
                        Toast.makeText(ScanAssistActivity.this,
                                "Could not play voice — check emulator/device volume",
                                Toast.LENGTH_SHORT).show());
            }
        });

        if (!ttsReady) {
            Toast.makeText(this, "Install English Text-to-Speech data for voice guidance",
                    Toast.LENGTH_LONG).show();
            return;
        }

        ensureAudibleVolume();
        if (pendingSpeech != null) {
            String queued = pendingSpeech;
            pendingSpeech = null;
            speakNow(queued);
        } else {
            speakNow(getString(R.string.scan_welcome));
        }
    }

    private void ensureAudibleVolume() {
        if (audioManager == null) {
            return;
        }
        try {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (max > 0 && current < Math.max(1, max / 3)) {
                audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        Math.max(1, max * 2 / 3),
                        AudioManager.FLAG_SHOW_UI
                );
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not adjust volume", e);
        }
    }

    private void speak(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        lastSpoken = message;
        if (!ttsReady || tts == null) {
            pendingSpeech = message;
            return;
        }
        speakNow(message);
    }

    private void speakNow(String message) {
        ensureAudibleVolume();
        try {
            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
            int result = tts.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    params,
                    "dislocator_" + System.currentTimeMillis()
            );
            if (result == TextToSpeech.ERROR) {
                Toast.makeText(this, "Voice error — turn up media volume", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "TTS speak failed", e);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void scanSurroundings() {
        scanButton.setEnabled(false);
        guidanceTitle.setText(R.string.scan_scanning);
        guidanceText.setText(R.string.scan_listening);

        final LookDirection facing = lookDirection;
        analyzeExecutor.execute(() -> {
            try {
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), SCAN_RES);
                if (bitmap == null) {
                    mainHandler.post(() -> applyDirectionalGuidance(facing, new ArrayList<>()));
                    return;
                }
                InputImage image = InputImage.fromBitmap(bitmap, 0);
                labeler.process(image)
                        .addOnSuccessListener(labels -> {
                            List<String> mlNames = new ArrayList<>();
                            for (ImageLabel label : labels) {
                                mlNames.add(label.getText());
                            }
                            List<String> names = enrichForDirection(facing, mlNames);
                            mainHandler.post(() -> applyDirectionalGuidance(facing, names));
                        })
                        .addOnFailureListener(e ->
                                mainHandler.post(() -> applyDirectionalGuidance(facing, new ArrayList<>())));
            } catch (Exception e) {
                mainHandler.post(() -> applyDirectionalGuidance(facing, new ArrayList<>()));
            }
        });
    }

    private List<String> enrichForDirection(LookDirection facing, List<String> mlLabels) {
        List<String> names = new ArrayList<>();
        if (mlLabels != null) {
            names.addAll(mlLabels);
        }
        addIfMissing(names, "Walkway");
        addIfMissing(names, "Building");
        addIfMissing(names, "Pillar");
        switch (facing) {
            case LEFT:
                addIfMissing(names, "Sign");
                break;
            case RIGHT:
                addIfMissing(names, "Courtyard");
                addIfMissing(names, "Stairs");
                addIfMissing(names, "Chair");
                break;
            case BEHIND:
                addIfMissing(names, "Walkway");
                break;
            case CENTER:
            default:
                addIfMissing(names, "Courtyard");
                addIfMissing(names, "Stairs");
                break;
        }
        return names;
    }

    private void addIfMissing(List<String> names, String label) {
        for (String existing : names) {
            if (existing != null && existing.equalsIgnoreCase(label)) {
                return;
            }
        }
        names.add(label);
    }

    private void applyDirectionalGuidance(LookDirection facing, List<String> labels) {
        String area;
        String hint;
        String turnCue;
        switch (facing) {
            case LEFT:
                area = "on a covered walkway, facing left toward a nearby sign";
                hint = "pillars on your left; a sign is marked nearby";
                turnCue = "You are looking left. ";
                break;
            case RIGHT:
                area = "on a covered walkway, facing right toward the courtyard";
                hint = "courtyard seating ahead, with stairs farther ahead";
                turnCue = "You are looking right. ";
                break;
            case BEHIND:
                area = "on a covered walkway, looking around";
                hint = "turn back toward the bright courtyard path";
                turnCue = "Keep turning to face the courtyard path. ";
                break;
            case CENTER:
            default:
                area = "on a covered walkway, looking straight ahead";
                hint = "tiled walkway toward the bright courtyard and stairs";
                turnCue = "You are facing straight ahead. ";
                break;
        }

        NavigationGuidance.Result result = NavigationGuidance.build(labels, area, hint);
        applyGuidance(new NavigationGuidance.Result(
                turnCue + result.spoken,
                result.summary,
                result.hazardDetected
        ), labels);
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

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (labeler != null) {
            labeler.close();
        }
        if (analyzeExecutor != null) {
            analyzeExecutor.shutdown();
        }
        super.onDestroy();
    }
}

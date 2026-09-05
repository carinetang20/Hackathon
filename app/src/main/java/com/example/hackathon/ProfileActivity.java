package com.example.hackathon;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hackathon.models.AccessibilityReport;
import com.example.hackathon.utils.ObstacleReportStore;
import com.example.hackathon.utils.TrustCalculator;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private View navHome, navReport, navMyReports, navProfile;
    private View bubbleHome, bubbleReport, bubbleMyReports, bubbleProfile;
    private ImageView iconHome, iconReport, iconMyReports, iconProfile;

    private LinearLayout settingsRow;
    private MaterialButton logoutButton;

    private TextView reportsCountText;
    private TextView confirmedCountText;
    private TextView clearedCountText;
    private TextView trustScoreText;

    private static final int COLOR_ACTIVE = 0xFF2A2A2A;
    private static final int COLOR_INACTIVE = 0xFFFFFFFF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_profile);

        navHome = findViewById(R.id.navHome);
        navReport = findViewById(R.id.navReport);
        navMyReports = findViewById(R.id.navMyReports);
        navProfile = findViewById(R.id.navProfile);

        bubbleHome = findViewById(R.id.bubbleHome);
        bubbleReport = findViewById(R.id.bubbleReport);
        bubbleMyReports = findViewById(R.id.bubbleMyReports);
        bubbleProfile = findViewById(R.id.bubbleProfile);

        iconHome = findViewById(R.id.iconHome);
        iconReport = findViewById(R.id.iconReport);
        iconMyReports = findViewById(R.id.iconMyReports);
        iconProfile = findViewById(R.id.iconProfile);

        settingsRow = findViewById(R.id.settingsRow);
        logoutButton = findViewById(R.id.logoutButton);

        reportsCountText = findViewById(R.id.reportsCountText);
        confirmedCountText = findViewById(R.id.confirmedCountText);
        clearedCountText = findViewById(R.id.clearedCountText);
        trustScoreText = findViewById(R.id.trustScoreText);

        setActiveTab(bubbleProfile, iconProfile);

        navHome.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, MainActivity.class));
            finish();
        });

        navReport.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, ReportActivity.class));
            finish();
        });

        navMyReports.setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, MyReportsActivity.class)));

        navProfile.setOnClickListener(v -> { /* already here */ });

        settingsRow.setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, SettingsActivity.class)));

        logoutButton.setOnClickListener(v -> {
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(ProfileActivity.this, MainActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshImpactStats();
    }

    private void refreshImpactStats() {
        ObstacleReportStore store = ObstacleReportStore.getInstance(this);
        List<AccessibilityReport> mine = store.getMyReports();
        List<AccessibilityReport> all = store.getAllReports();

        int myReports = mine.size();
        int stillThereVotes = 0;
        int cleared = 0;
        int totalStill = 0;
        int totalNot = 0;

        for (AccessibilityReport report : all) {
            stillThereVotes += report.getStillThereCount();
            totalStill += report.getStillThereCount();
            totalNot += report.getNotThereCount();
            if (AccessibilityReport.STATUS_CLEARED.equals(report.getStatus())) {
                cleared++;
            }
        }

        reportsCountText.setText(String.valueOf(myReports > 0 ? myReports : all.size()));
        confirmedCountText.setText(String.valueOf(stillThereVotes));
        clearedCountText.setText(String.valueOf(cleared));
        trustScoreText.setText(TrustCalculator.calculateTrust(
                Math.max(totalStill, 1),
                totalNot
        ));
    }

    private void setActiveTab(View activeBubble, ImageView activeIcon) {
        bubbleHome.setVisibility(View.INVISIBLE);
        bubbleReport.setVisibility(View.INVISIBLE);
        bubbleMyReports.setVisibility(View.INVISIBLE);
        bubbleProfile.setVisibility(View.INVISIBLE);

        iconHome.setColorFilter(COLOR_INACTIVE);
        iconReport.setColorFilter(COLOR_INACTIVE);
        iconMyReports.setColorFilter(COLOR_INACTIVE);
        iconProfile.setColorFilter(COLOR_INACTIVE);

        activeBubble.setVisibility(View.VISIBLE);
        activeIcon.setColorFilter(COLOR_ACTIVE);
    }
}

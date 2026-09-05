package com.example.hackathon;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private View navHome, navReport, navMyReports, navProfile;
    private View bubbleHome, bubbleReport, bubbleMyReports, bubbleProfile;
    private ImageView iconHome, iconReport, iconMyReports, iconProfile;

    private ImageView mapPreviewImage;

    private static final int COLOR_ACTIVE = 0xFF2A2A2A;
    private static final int COLOR_INACTIVE = 0xFFFFFFFF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

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

        mapPreviewImage = findViewById(R.id.mapPreviewImage);

        navHome.setOnClickListener(v -> {
            setActiveTab(bubbleHome, iconHome);
        });

        navReport.setOnClickListener(v -> {
            setActiveTab(bubbleReport, iconReport);
            startActivity(new Intent(MainActivity.this, ReportActivity.class));
        });

        navMyReports.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, MyReportsActivity.class));
        });

        navProfile.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ProfileActivity.class));
        });

        mapPreviewImage.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, MapActivity.class));
        });
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
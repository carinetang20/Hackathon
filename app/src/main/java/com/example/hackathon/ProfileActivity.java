package com.example.hackathon;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class ProfileActivity extends AppCompatActivity {

    private View navHome, navReport, navProfile;
    private View bubbleHome, bubbleReport, bubbleProfile;
    private ImageView iconHome, iconReport, iconProfile;

    private LinearLayout myReportsRow, notificationsRow, settingsRow;
    private MaterialButton logoutButton;

    private static final int COLOR_ACTIVE = 0xFF2A2A2A;
    private static final int COLOR_INACTIVE = 0xFFFFFFFF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_profile);

        navHome = findViewById(R.id.navHome);

        navReport = findViewById(R.id.navReport);
        navProfile = findViewById(R.id.navProfile);

        bubbleHome = findViewById(R.id.bubbleHome);

        bubbleReport = findViewById(R.id.bubbleReport);
        bubbleProfile = findViewById(R.id.bubbleProfile);

        iconHome = findViewById(R.id.iconHome);

        iconReport = findViewById(R.id.iconReport);
        iconProfile = findViewById(R.id.iconProfile);

        myReportsRow = findViewById(R.id.myReportsRow);
        notificationsRow = findViewById(R.id.notificationsRow);
        settingsRow = findViewById(R.id.settingsRow);
        logoutButton = findViewById(R.id.logoutButton);

        // Profile tab is already active on this screen
        setActiveTab(bubbleProfile, iconProfile);

        navHome.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, MainActivity.class));
            finish();
        });



        navReport.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, ReportActivity.class));
            finish();
        });
        myReportsRow.setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, MyReportsActivity.class)));

        navProfile.setOnClickListener(v -> {
            // already here, no-op
        });

        myReportsRow.setOnClickListener(v ->
                Toast.makeText(this, "My Reports coming soon", Toast.LENGTH_SHORT).show());

        notificationsRow.setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, com.example.hackathon.NotificationsActivity.class)));

        settingsRow.setOnClickListener(v ->
                Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show());

        logoutButton.setOnClickListener(v -> {
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(ProfileActivity.this, MainActivity.class));
            finish();
        });
    }

    private void setActiveTab(View activeBubble, ImageView activeIcon) {
        bubbleHome.setVisibility(View.INVISIBLE);

        bubbleReport.setVisibility(View.INVISIBLE);
        bubbleProfile.setVisibility(View.INVISIBLE);

        iconHome.setColorFilter(COLOR_INACTIVE);

        iconReport.setColorFilter(COLOR_INACTIVE);
        iconProfile.setColorFilter(COLOR_INACTIVE);

        activeBubble.setVisibility(View.VISIBLE);
        activeIcon.setColorFilter(COLOR_ACTIVE);
    }
}
package com.example.hackathon;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ImageButton backButton = findViewById(R.id.backButton);
        SwitchMaterial notificationsSwitch = findViewById(R.id.notificationsSwitch);
        SwitchMaterial locationSwitch = findViewById(R.id.locationSwitch);
        SwitchMaterial darkModeSwitch = findViewById(R.id.darkModeSwitch);
        LinearLayout editProfileRow = findViewById(R.id.editProfileRow);
        LinearLayout changePasswordRow = findViewById(R.id.changePasswordRow);
        LinearLayout privacyRow = findViewById(R.id.privacyRow);

        backButton.setOnClickListener(v -> finish());

        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                Toast.makeText(this,
                        isChecked ? "Notifications on" : "Notifications off",
                        Toast.LENGTH_SHORT).show());

        locationSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                Toast.makeText(this,
                        isChecked ? "Location access on" : "Location access off",
                        Toast.LENGTH_SHORT).show());

        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                Toast.makeText(this,
                        "Dark mode coming soon",
                        Toast.LENGTH_SHORT).show());

        editProfileRow.setOnClickListener(v ->
                Toast.makeText(this, "Edit profile coming soon", Toast.LENGTH_SHORT).show());

        changePasswordRow.setOnClickListener(v ->
                Toast.makeText(this, "Change password coming soon", Toast.LENGTH_SHORT).show());

        privacyRow.setOnClickListener(v ->
                Toast.makeText(this, "Privacy policy coming soon", Toast.LENGTH_SHORT).show());
    }
}

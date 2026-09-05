package com.example.hackathon;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hackathon.utils.ObstacleReportStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class ReportActivity extends AppCompatActivity {

    private TextInputEditText locationInput;
    private TextInputEditText descriptionInput;
    private AutoCompleteTextView issueSpinner;
    private MaterialButton submitButton;
    private ImageButton backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_report);

        locationInput = findViewById(R.id.locationInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        issueSpinner = findViewById(R.id.issueSpinner);
        submitButton = findViewById(R.id.submitButton);
        backButton = findViewById(R.id.backButton);

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

            ObstacleReportStore.getInstance(this)
                    .addReport(location, issueType, description);

            Toast.makeText(
                    ReportActivity.this,
                    "Obstacle reported — thank you for helping the community!",
                    Toast.LENGTH_LONG
            ).show();

            finish();
        });
    }
}

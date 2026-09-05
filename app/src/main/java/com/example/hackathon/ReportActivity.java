package com.example.hackathon;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

        String[] issueTypes = {
                "Illegal Parking",
                "Pothole",
                "Construction",
                "Overgrown Vegetation",
                "Broken Ramp",
                "Blocked Tactile Path",
                "Broken Crossing",
                "Open Drain",
                "Other"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                issueTypes
        );

        issueSpinner.setAdapter(adapter);

        // BACK button
        backButton.setOnClickListener(v -> {
            finish();
        });

        // SUBMIT button
        submitButton.setOnClickListener(v -> {

            String location = locationInput.getText().toString();
            String description = descriptionInput.getText().toString();
            String issueType = issueSpinner.getText().toString();

            if (location.isEmpty() || description.isEmpty() || issueType.isEmpty()) {

                Toast.makeText(
                        ReportActivity.this,
                        "Please fill in all fields",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            Toast.makeText(
                    ReportActivity.this,
                    "Report submitted!",
                    Toast.LENGTH_SHORT
            ).show();

            // Return to MainActivity
            finish();
        });
    }
}
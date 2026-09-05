package com.example.hackathon;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ReportActivity extends AppCompatActivity {

    private EditText locationInput;
    private EditText descriptionInput;
    private Spinner issueSpinner;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_report);

        locationInput = findViewById(R.id.locationInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        issueSpinner = findViewById(R.id.issueSpinner);
        submitButton = findViewById(R.id.submitButton);

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
                android.R.layout.simple_spinner_item,
                issueTypes
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        issueSpinner.setAdapter(adapter);

        submitButton.setOnClickListener(v -> {

            String location = locationInput.getText().toString();
            String description = descriptionInput.getText().toString();
            String issueType = issueSpinner.getSelectedItem().toString();

            if (location.isEmpty() || description.isEmpty()) {

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

            // Go back to MainActivity
            Intent intent = new Intent(
                    ReportActivity.this,
                    MainActivity.class
            );

            startActivity(intent);
            finish();
        });
    }
}
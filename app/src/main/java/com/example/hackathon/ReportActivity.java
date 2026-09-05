package com.example.hackathon;

import android.os.Bundle;
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
        });
    }
}
package com.example.hackathon;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.example.hackathon.utils.TrustCalculator;
import androidx.appcompat.app.AppCompatActivity;

import com.example.hackathon.models.AccessibilityReport;

public class ReportDetailActivity extends AppCompatActivity {

    private TextView locationText;
    private TextView issueText;
    private TextView descriptionText;
    private TextView confirmationText;
    private TextView disputeText;
    private TextView trustText;

    private Button confirmButton;
    private Button disputeButton;

    private AccessibilityReport report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_report_detail);

        locationText = findViewById(R.id.locationText);
        issueText = findViewById(R.id.issueText);
        descriptionText = findViewById(R.id.descriptionText);
        confirmationText = findViewById(R.id.confirmationText);
        disputeText = findViewById(R.id.disputeText);
        trustText = findViewById(R.id.trustText);

        confirmButton = findViewById(R.id.confirmButton);
        disputeButton = findViewById(R.id.disputeButton);

        // Example report
        report = new AccessibilityReport(
                "R001",
                "Campus Library",
                "Ramp Blocked",
                "Construction is blocking the wheelchair ramp.",
                System.currentTimeMillis()
        );

        displayReport();

        confirmButton.setOnClickListener(v -> {

            report.confirm();

            displayReport();

            Toast.makeText(
                    this,
                    "Report confirmed!",
                    Toast.LENGTH_SHORT
            ).show();
        });

        disputeButton.setOnClickListener(v -> {

            report.dispute();

            displayReport();

            Toast.makeText(
                    this,
                    "Report disputed!",
                    Toast.LENGTH_SHORT
            ).show();
        });
    }

    private void displayReport() {

        locationText.setText(report.getLocationName());
        issueText.setText(report.getIssueType());
        descriptionText.setText(report.getDescription());

        confirmationText.setText(
                "Confirmed: " + report.getConfirmations()
        );

        disputeText.setText(
                "Disputed: " + report.getDisputes()
        );

        String trust = TrustCalculator.calculateTrust(
                report.getConfirmations(),
                report.getDisputes()
        );

        trustText.setText("Trust Level: " + trust);
    }
}
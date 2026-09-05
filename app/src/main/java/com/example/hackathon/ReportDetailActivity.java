package com.example.hackathon;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hackathon.models.AccessibilityReport;
import com.example.hackathon.utils.ObstacleReportStore;
import com.example.hackathon.utils.TrustCalculator;
import com.google.android.material.button.MaterialButton;

public class ReportDetailActivity extends AppCompatActivity {

    public static final String EXTRA_REPORT_ID = "report_id";

    private TextView locationText;
    private TextView issueText;
    private TextView descriptionText;
    private TextView confirmationText;
    private TextView disputeText;
    private TextView trustText;
    private TextView statusText;
    private TextView clearedBanner;

    private MaterialButton confirmButton;
    private MaterialButton disputeButton;
    private ImageButton backButton;

    private ObstacleReportStore store;
    private AccessibilityReport report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_report_detail);

        store = ObstacleReportStore.getInstance(this);

        locationText = findViewById(R.id.locationText);
        issueText = findViewById(R.id.issueText);
        descriptionText = findViewById(R.id.descriptionText);
        confirmationText = findViewById(R.id.confirmationText);
        disputeText = findViewById(R.id.disputeText);
        trustText = findViewById(R.id.trustText);
        statusText = findViewById(R.id.statusText);
        clearedBanner = findViewById(R.id.clearedBanner);

        confirmButton = findViewById(R.id.confirmButton);
        disputeButton = findViewById(R.id.disputeButton);
        backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());

        String reportId = getIntent().getStringExtra(EXTRA_REPORT_ID);
        report = store.getById(reportId);

        if (report == null) {
            Toast.makeText(this, "Report not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        displayReport();

        confirmButton.setOnClickListener(v -> {
            store.markStillThere(report.getId());
            report = store.getById(report.getId());
            displayReport();
            Toast.makeText(
                    this,
                    "Thanks — marked as still there",
                    Toast.LENGTH_SHORT
            ).show();
        });

        disputeButton.setOnClickListener(v -> {
            store.markNotThere(report.getId());
            report = store.getById(report.getId());
            displayReport();

            String message = AccessibilityReport.STATUS_CLEARED.equals(report.getStatus())
                    ? "Obstacle marked as cleared — community map updated"
                    : "Thanks — marked as not there";

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void displayReport() {
        locationText.setText(report.getLocationName());
        issueText.setText(report.getIssueType());
        descriptionText.setText(report.getDescription());

        confirmationText.setText("Still there: " + report.getStillThereCount());
        disputeText.setText("Not there: " + report.getNotThereCount());

        String trust = TrustCalculator.calculateTrust(
                report.getStillThereCount(),
                report.getNotThereCount()
        );
        trustText.setText("Trust Level: " + trust);

        String status = report.getStatus();
        statusText.setText(TrustCalculator.statusLabel(status));
        applyStatusColors(status);

        boolean cleared = AccessibilityReport.STATUS_CLEARED.equals(status);
        clearedBanner.setVisibility(cleared ? View.VISIBLE : View.GONE);
    }

    private void applyStatusColors(String status) {
        if (AccessibilityReport.STATUS_CLEARED.equals(status)) {
            statusText.setBackgroundColor(0xFFD1FAE5);
            statusText.setTextColor(0xFF059669);
        } else if (AccessibilityReport.STATUS_CONFIRMED.equals(status)) {
            statusText.setBackgroundColor(0xFFD1FAE5);
            statusText.setTextColor(0xFF059669);
        } else if (AccessibilityReport.STATUS_UNCERTAIN.equals(status)) {
            statusText.setBackgroundColor(0xFFFEE2E2);
            statusText.setTextColor(0xFFDC2626);
        } else {
            statusText.setBackgroundColor(0xFFFEF3C7);
            statusText.setTextColor(0xFFD97706);
        }
    }
}

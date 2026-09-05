package com.example.hackathon;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hackathon.models.MyReport;

import java.util.ArrayList;
import java.util.List;

public class MyReportsActivity extends AppCompatActivity {

    private ImageButton backButton;
    private LinearLayout reportContainer;
    private LinearLayout emptyState;

    private List<MyReport> reports = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_my_reports);

        backButton = findViewById(R.id.backButton);
        reportContainer = findViewById(R.id.reportContainer);
        emptyState = findViewById(R.id.emptyState);

        backButton.setOnClickListener(v -> finish());

        loadSampleReports();
        renderReports();
    }

    private void loadSampleReports() {
        reports.add(new MyReport(
                "Campus Library",
                "Ramp Blocked",
                "Construction is blocking the wheelchair ramp.",
                "Pending",
                3,
                0
        ));

        reports.add(new MyReport(
                "Persiaran Newron",
                "Broken Crossing",
                "The pedestrian crossing signal is not working.",
                "Confirmed",
                8,
                1
        ));

        reports.add(new MyReport(
                "Institute for Postgraduate Studies",
                "Blocked Tactile Path",
                "Tactile paving is covered by parked bicycles.",
                "Confirmed",
                5,
                0
        ));
    }

    private void renderReports() {
        reportContainer.removeAllViews();

        if (reports.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            return;
        }

        emptyState.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);

        for (MyReport report : reports) {
            View itemView = inflater.inflate(R.layout.item_my_report, reportContainer, false);

            TextView locationView = itemView.findViewById(R.id.reportLocation);
            TextView statusView = itemView.findViewById(R.id.reportStatus);
            TextView issueTypeView = itemView.findViewById(R.id.reportIssueType);
            TextView descriptionView = itemView.findViewById(R.id.reportDescription);
            TextView confirmedView = itemView.findViewById(R.id.reportConfirmed);
            TextView disputedView = itemView.findViewById(R.id.reportDisputed);

            locationView.setText(report.getLocation());
            statusView.setText(report.getStatus());
            issueTypeView.setText(report.getIssueType());
            descriptionView.setText(report.getDescription());
            confirmedView.setText("✓ " + report.getConfirmations() + " confirmed");
            disputedView.setText("✕ " + report.getDisputes() + " disputed");

            // Color the status pill based on status text
            if (report.getStatus().equalsIgnoreCase("Confirmed")) {
                statusView.setBackgroundColor(0xFFD1FAE5); // light green
                statusView.setTextColor(0xFF059669);        // green
            } else if (report.getStatus().equalsIgnoreCase("Disputed")) {
                statusView.setBackgroundColor(0xFFFEE2E2); // light red
                statusView.setTextColor(0xFFDC2626);        // red
            } else {
                statusView.setBackgroundColor(0xFFFEF3C7); // light amber
                statusView.setTextColor(0xFFD97706);        // amber
            }

            reportContainer.addView(itemView);
        }
    }
}
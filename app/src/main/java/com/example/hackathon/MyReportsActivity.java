package com.example.hackathon;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hackathon.models.AccessibilityReport;
import com.example.hackathon.utils.ObstacleReportStore;
import com.example.hackathon.utils.TrustCalculator;

import java.util.List;

public class MyReportsActivity extends AppCompatActivity {

    private ImageButton backButton;
    private LinearLayout reportContainer;
    private LinearLayout emptyState;
    private TextView titleText;
    private TextView subtitleText;

    private ObstacleReportStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_my_reports);

        store = ObstacleReportStore.getInstance(this);

        backButton = findViewById(R.id.backButton);
        reportContainer = findViewById(R.id.reportContainer);
        emptyState = findViewById(R.id.emptyState);
        titleText = findViewById(R.id.titleText);
        subtitleText = findViewById(R.id.subtitleText);

        if (titleText != null) {
            titleText.setText("Community reports");
        }
        if (subtitleText != null) {
            subtitleText.setVisibility(View.VISIBLE);
            subtitleText.setText("Tap a report to mark Still there or Not there");
        }

        backButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderReports();
    }

    private void renderReports() {
        reportContainer.removeAllViews();

        List<AccessibilityReport> reports = store.getAllReports();

        if (reports.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            return;
        }

        emptyState.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);

        for (AccessibilityReport report : reports) {
            View itemView = inflater.inflate(R.layout.item_my_report, reportContainer, false);

            TextView locationView = itemView.findViewById(R.id.reportLocation);
            TextView statusView = itemView.findViewById(R.id.reportStatus);
            TextView issueTypeView = itemView.findViewById(R.id.reportIssueType);
            TextView descriptionView = itemView.findViewById(R.id.reportDescription);
            TextView confirmedView = itemView.findViewById(R.id.reportConfirmed);
            TextView disputedView = itemView.findViewById(R.id.reportDisputed);

            locationView.setText(report.getLocationName());
            statusView.setText(TrustCalculator.statusLabel(report.getStatus()));
            issueTypeView.setText(report.getIssueType());
            descriptionView.setText(report.getDescription());
            confirmedView.setText("✓ " + report.getStillThereCount() + " still there");
            disputedView.setText("✕ " + report.getNotThereCount() + " not there");

            applyStatusColors(statusView, report.getStatus());

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MyReportsActivity.this, ReportDetailActivity.class);
                intent.putExtra(ReportDetailActivity.EXTRA_REPORT_ID, report.getId());
                startActivity(intent);
            });

            reportContainer.addView(itemView);
        }
    }

    private void applyStatusColors(TextView statusView, String status) {
        if (AccessibilityReport.STATUS_CLEARED.equals(status)
                || AccessibilityReport.STATUS_CONFIRMED.equals(status)) {
            statusView.setBackgroundColor(0xFFD1FAE5);
            statusView.setTextColor(0xFF059669);
        } else if (AccessibilityReport.STATUS_UNCERTAIN.equals(status)) {
            statusView.setBackgroundColor(0xFFFEE2E2);
            statusView.setTextColor(0xFFDC2626);
        } else {
            statusView.setBackgroundColor(0xFFFEF3C7);
            statusView.setTextColor(0xFFD97706);
        }
    }
}

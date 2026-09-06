package com.example.hackathon;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.hackathon.models.AccessibilityReport;
import com.example.hackathon.utils.ObstacleReportStore;
import com.example.hackathon.utils.ReportPhotoLoader;
import com.example.hackathon.utils.ReportTimeFormat;
import com.example.hackathon.utils.TrustCalculator;
import com.google.android.material.button.MaterialButton;

public class ReportDetailActivity extends AppCompatActivity {

    public static final String EXTRA_REPORT_ID = "report_id";

    private TextView locationText;
    private TextView issueText;
    private TextView descriptionText;
    private TextView trustText;
    private TextView statusText;
    private TextView clearedBanner;
    private TextView verifiedAtText;
    private TextView voteHintText;
    private TextView reportPhotoPlaceholder;
    private TextView photoEvidenceBadge;
    private TextView uploadedAtBanner;
    private ImageView reportPhoto;

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
        trustText = findViewById(R.id.trustText);
        statusText = findViewById(R.id.statusText);
        clearedBanner = findViewById(R.id.clearedBanner);
        verifiedAtText = findViewById(R.id.verifiedAtText);
        voteHintText = findViewById(R.id.voteHintText);
        reportPhoto = findViewById(R.id.reportPhoto);
        reportPhotoPlaceholder = findViewById(R.id.reportPhotoPlaceholder);
        photoEvidenceBadge = findViewById(R.id.photoEvidenceBadge);
        uploadedAtBanner = findViewById(R.id.uploadedAtBanner);

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

        confirmButton.setOnClickListener(v -> showConfirmDialog());
        disputeButton.setOnClickListener(v -> showDisputeDialog());
    }

    private void showConfirmDialog() {
        if (!canVote()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Still there?")
                .setMessage("Confirm that this obstacle is still blocking the path.")
                .setPositiveButton("Yes", (d, w) -> {
                    store.markStillThere(report.getId());
                    Toast.makeText(this, "Marked still there", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDisputeDialog() {
        if (!canVote()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Not there?")
                .setMessage("Confirm that this obstacle has been removed.")
                .setPositiveButton("Yes", (d, w) -> {
                    store.markNotThere(report.getId());
                    report = store.getById(report.getId());
                    displayReport();

                    String message = AccessibilityReport.STATUS_CLEARED.equals(report.getStatus())
                            ? "Obstacle cleared"
                            : "Marked not there";
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean canVote() {
        if (AccessibilityReport.STATUS_CLEARED.equals(report.getStatus())) {
            Toast.makeText(this, "This obstacle is already cleared", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (store.hasVoted(report.getId())) {
            Toast.makeText(this, "You already checked this report", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void displayReport() {
        locationText.setText(report.getLocationName());
        issueText.setText(report.getIssueType());
        descriptionText.setText(report.getDescription());

        String reliability = TrustCalculator.calculateReportReliability(report);
        trustText.setText("Reliability · " + reliability);
        applyReliabilityColors(reliability);

        uploadedAtBanner.setText(ReportTimeFormat.postedBanner(report.getTimestamp()));

        if (report.getLastVerifiedAt() > 0) {
            verifiedAtText.setText(
                    "Last checked · " + ReportTimeFormat.exact(report.getLastVerifiedAt())
            );
        } else {
            verifiedAtText.setText("Last checked · not yet");
        }

        bindPhoto(report.getPhotoPath());
        photoEvidenceBadge.setVisibility(View.GONE);

        String status = report.getStatus();
        statusText.setText(TrustCalculator.statusLabel(status));
        applyStatusColors(status);

        boolean cleared = AccessibilityReport.STATUS_CLEARED.equals(status);
        clearedBanner.setVisibility(cleared ? View.VISIBLE : View.GONE);

        boolean alreadyVoted = store.hasVoted(report.getId());
        confirmButton.setEnabled(!cleared && !alreadyVoted);
        disputeButton.setEnabled(!cleared && !alreadyVoted);
        confirmButton.setAlpha((!cleared && !alreadyVoted) ? 1f : 0.45f);
        disputeButton.setAlpha((!cleared && !alreadyVoted) ? 1f : 0.45f);

        if (cleared) {
            voteHintText.setVisibility(View.VISIBLE);
            voteHintText.setText("This report is cleared.");
        } else if (alreadyVoted) {
            voteHintText.setVisibility(View.VISIBLE);
            voteHintText.setText("You already checked this report.");
        } else {
            voteHintText.setVisibility(View.GONE);
        }
    }

    private boolean bindPhoto(String path) {
        Bitmap bitmap = ReportPhotoLoader.load(path, 1280);
        if (bitmap != null) {
            reportPhoto.setImageBitmap(bitmap);
            reportPhoto.setVisibility(View.VISIBLE);
            reportPhotoPlaceholder.setVisibility(View.GONE);
            return true;
        }
        reportPhoto.setVisibility(View.GONE);
        reportPhotoPlaceholder.setVisibility(View.VISIBLE);
        return false;
    }

    private void applyReliabilityColors(String reliability) {
        if ("HIGH".equals(reliability)) {
            trustText.setBackgroundColor(0xFFD1FAE5);
            trustText.setTextColor(0xFF047857);
        } else if ("MEDIUM".equals(reliability)) {
            trustText.setBackgroundColor(0xFFFEF3C7);
            trustText.setTextColor(0xFFB45309);
        } else if ("CLEARED".equals(reliability)) {
            trustText.setBackgroundColor(0xFFE5E7EB);
            trustText.setTextColor(0xFF374151);
        } else {
            trustText.setBackgroundColor(0xFFFEE2E2);
            trustText.setTextColor(0xFFB91C1C);
        }
    }

    private void applyStatusColors(String status) {
        if (AccessibilityReport.STATUS_CLEARED.equals(status)
                || AccessibilityReport.STATUS_CONFIRMED.equals(status)) {
            statusText.setBackgroundColor(0xFFD1FAE5);
            statusText.setTextColor(0xFF059669);
        } else if (AccessibilityReport.STATUS_UNCERTAIN.equals(status)) {
            statusText.setBackgroundColor(0xFFFEE2E2);
            statusText.setTextColor(0xFFDC2626);
        } else {
            statusText.setBackgroundColor(0xFFE5E7EB);
            statusText.setTextColor(0xFF374151);
        }
    }
}

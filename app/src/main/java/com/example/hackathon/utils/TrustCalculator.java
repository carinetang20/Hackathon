package com.example.hackathon.utils;

import com.example.hackathon.models.AccessibilityReport;

import java.util.concurrent.TimeUnit;

public class TrustCalculator {

    public static String calculateTrust(int stillThereCount, int notThereCount) {
        if (AccessibilityReport.STATUS_CLEARED.equals(
                statusFromVotes(stillThereCount, notThereCount))) {
            return "CLEARED";
        }

        if (stillThereCount >= 3 && notThereCount == 0) {
            return "HIGH";
        }

        if (stillThereCount > notThereCount) {
            return "MEDIUM";
        }

        return "LOW";
    }

    /**
     * Reliability for a single report: community votes + how fresh the
     * exact timestamps are + whether a photo was uploaded as evidence.
     */
    public static String calculateReportReliability(AccessibilityReport report) {
        if (report == null) {
            return "LOW";
        }
        if (AccessibilityReport.STATUS_CLEARED.equals(report.getStatus())) {
            return "CLEARED";
        }

        int score = 0;
        int still = report.getStillThereCount();
        int not = report.getNotThereCount();

        if (still >= 3 && not == 0) {
            score += 3;
        } else if (still > not && still >= 1) {
            score += 2;
        } else if (still > 0) {
            score += 1;
        }

        long reference = report.getLastVerifiedAt() > 0
                ? report.getLastVerifiedAt()
                : report.getTimestamp();
        long hoursOld = TimeUnit.MILLISECONDS.toHours(
                Math.max(0, System.currentTimeMillis() - reference)
        );
        if (hoursOld < 6) {
            score += 2;
        } else if (hoursOld < 24) {
            score += 1;
        } else if (hoursOld >= 72) {
            score -= 1;
        }

        if (report.hasPhoto()) {
            score += 1;
        }

        if (score >= 5) {
            return "HIGH";
        }
        if (score >= 3) {
            return "MEDIUM";
        }
        return "LOW";
    }

    public static String reliabilityExplanation(AccessibilityReport report) {
        if (report == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(report.getStillThereCount()).append(" confirm · ")
                .append(report.getNotThereCount()).append(" dispute");

        long reference = report.getLastVerifiedAt() > 0
                ? report.getLastVerifiedAt()
                : report.getTimestamp();
        sb.append(" · ").append(ReportTimeFormat.freshnessLabel(reference));

        if (report.hasPhoto()) {
            sb.append(" · photo evidence");
        } else {
            sb.append(" · no photo");
        }
        return sb.toString();
    }

    public static String statusFromVotes(int stillThereCount, int notThereCount) {
        if (notThereCount >= 2 && notThereCount > stillThereCount) {
            return AccessibilityReport.STATUS_CLEARED;
        }
        if (stillThereCount >= 2 && stillThereCount > notThereCount) {
            return AccessibilityReport.STATUS_CONFIRMED;
        }
        if (stillThereCount > 0 && notThereCount > 0) {
            return AccessibilityReport.STATUS_UNCERTAIN;
        }
        if (stillThereCount > 0) {
            return AccessibilityReport.STATUS_CONFIRMED;
        }
        return AccessibilityReport.STATUS_ACTIVE;
    }

    public static String statusLabel(String status) {
        if (AccessibilityReport.STATUS_CLEARED.equals(status)) {
            return "Cleared";
        }
        if (AccessibilityReport.STATUS_CONFIRMED.equals(status)) {
            return "Still there";
        }
        if (AccessibilityReport.STATUS_UNCERTAIN.equals(status)) {
            return "Uncertain";
        }
        return "Active";
    }
}

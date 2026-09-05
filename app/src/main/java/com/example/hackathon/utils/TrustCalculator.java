package com.example.hackathon.utils;

import com.example.hackathon.models.AccessibilityReport;

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

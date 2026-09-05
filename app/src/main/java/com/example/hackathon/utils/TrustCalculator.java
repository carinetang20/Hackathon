package com.example.hackathon.utils;

public class TrustCalculator {

    public static String calculateTrust(
            int confirmations,
            int disputes) {

        // HIGH trust
        // At least 3 people agree
        // and nobody disagrees
        if (confirmations >= 3 && disputes == 0) {
            return "HIGH";
        }

        // MEDIUM trust
        // More people agree than disagree
        if (confirmations > disputes) {
            return "MEDIUM";
        }

        // LOW trust
        // More people disagree
        // or there are no confirmations
        return "LOW";
    }
}
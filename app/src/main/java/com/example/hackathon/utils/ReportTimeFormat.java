package com.example.hackathon.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ReportTimeFormat {

    private ReportTimeFormat() { }

    /** Exact local timestamp including seconds — for reliability judgment. */
    public static String exact(long millis) {
        if (millis <= 0) {
            return "—";
        }
        return new SimpleDateFormat("EEE, dd MMM yyyy · HH:mm:ss", Locale.getDefault())
                .format(new Date(millis));
    }

    public static String postedBanner(long millis) {
        if (millis <= 0) {
            return "Posted: unknown date / time";
        }
        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(new Date(millis));
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date(millis));
        return "Posted: " + date + "  at  " + time;
    }

    public static String postedDetail(long millis) {
        if (millis <= 0) {
            return "Posted date/time unavailable";
        }
        String date = new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
                .format(new Date(millis));
        String time = new SimpleDateFormat("h:mm:ss a", Locale.getDefault())
                .format(new Date(millis));
        return "Posted on " + date + "\nat " + time;
    }

    /** Full upload stamp with date, time to the second. */
    public static String uploadedAt(long millis) {
        if (millis <= 0) {
            return "Uploaded at: —";
        }
        return "Uploaded at: " + exact(millis);
    }

    /** Readable date-time without seconds. */
    public static String absolute(long millis) {
        if (millis <= 0) {
            return "—";
        }
        return new SimpleDateFormat("dd MMM yyyy · h:mm a", Locale.getDefault())
                .format(new Date(millis));
    }

    public static String relative(long millis) {
        if (millis <= 0) {
            return "";
        }
        long diff = System.currentTimeMillis() - millis;
        if (diff < 0) {
            return exact(millis);
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        if (minutes < 1) {
            return "Just now";
        }
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " min ago" : " mins ago");
        }
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = TimeUnit.MILLISECONDS.toDays(diff);
        if (days < 7) {
            return days + (days == 1 ? " day ago" : " days ago");
        }
        return absolute(millis);
    }

    public static String freshnessLabel(long millis) {
        if (millis <= 0) {
            return "Unknown age";
        }
        long hours = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - millis);
        if (hours < 6) {
            return "Very recent";
        }
        if (hours < 24) {
            return "Recent";
        }
        if (hours < 72) {
            return "Somewhat old";
        }
        return "Possibly outdated";
    }
}

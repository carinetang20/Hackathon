package com.example.hackathon.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.hackathon.models.AccessibilityReport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Local persistence for community obstacle reports.
 * Survives app restarts so "Still there" / "Not there" votes stick.
 */
public class ObstacleReportStore {

    private static final String PREFS = "obstacle_reports";
    private static final String KEY_REPORTS = "reports";
    private static final String KEY_SEEDED = "seeded";
    private static final String KEY_VOTED = "voted_report_ids";

    private static ObstacleReportStore instance;

    private final SharedPreferences prefs;
    private final List<AccessibilityReport> reports = new ArrayList<>();

    private ObstacleReportStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        if (!prefs.getBoolean(KEY_SEEDED, false) && reports.isEmpty()) {
            seedSampleReports();
            prefs.edit().putBoolean(KEY_SEEDED, true).apply();
            save();
        }
    }

    public static synchronized ObstacleReportStore getInstance(Context context) {
        if (instance == null) {
            instance = new ObstacleReportStore(context);
        }
        return instance;
    }

    public List<AccessibilityReport> getAllReports() {
        List<AccessibilityReport> copy = new ArrayList<>(reports);
        Collections.sort(copy, Comparator.comparingLong(AccessibilityReport::getTimestamp).reversed());
        return copy;
    }

    public List<AccessibilityReport> getMyReports() {
        List<AccessibilityReport> mine = new ArrayList<>();
        for (AccessibilityReport report : getAllReports()) {
            if (report.isSubmittedByMe()) {
                mine.add(report);
            }
        }
        return mine;
    }

    public List<AccessibilityReport> getActiveCommunityReports() {
        List<AccessibilityReport> active = new ArrayList<>();
        for (AccessibilityReport report : getAllReports()) {
            if (!AccessibilityReport.STATUS_CLEARED.equals(report.getStatus())) {
                active.add(report);
            }
        }
        return active;
    }

    public AccessibilityReport getById(String id) {
        if (id == null) {
            return null;
        }
        for (AccessibilityReport report : reports) {
            if (id.equals(report.getId())) {
                return report;
            }
        }
        return null;
    }

    public AccessibilityReport addReport(
            String locationName,
            String issueType,
            String description
    ) {
        return addReport(locationName, issueType, description, null);
    }

    public AccessibilityReport addReport(
            String locationName,
            String issueType,
            String description,
            String photoPath
    ) {
        long now = System.currentTimeMillis();
        AccessibilityReport report = new AccessibilityReport(
                UUID.randomUUID().toString(),
                locationName,
                issueType,
                description,
                now,
                0L,
                photoPath,
                0,
                0,
                AccessibilityReport.STATUS_ACTIVE,
                true
        );
        reports.add(0, report);
        save();
        return report;
    }

    public boolean hasVoted(String reportId) {
        return getVotedIds().contains(reportId);
    }

    public void markStillThere(String id) {
        AccessibilityReport report = getById(id);
        if (report != null) {
            report.markStillThere();
            rememberVote(id);
            save();
        }
    }

    public void markNotThere(String id) {
        AccessibilityReport report = getById(id);
        if (report != null) {
            report.markNotThere();
            rememberVote(id);
            save();
        }
    }

    private void rememberVote(String id) {
        Set<String> voted = new HashSet<>(getVotedIds());
        voted.add(id);
        prefs.edit().putStringSet(KEY_VOTED, voted).apply();
    }

    private Set<String> getVotedIds() {
        Set<String> stored = prefs.getStringSet(KEY_VOTED, null);
        if (stored == null) {
            return new HashSet<>();
        }
        return new HashSet<>(stored);
    }

    private void seedSampleReports() {
        long now = System.currentTimeMillis();
        reports.add(new AccessibilityReport(
                "seed-1",
                "Library",
                "Blocked Ramp",
                "Construction materials are blocking the wheelchair ramp entrance.",
                now - 3600_000L,
                now - 1800_000L,
                null,
                3,
                0,
                AccessibilityReport.STATUS_CONFIRMED,
                false
        ));
        reports.add(new AccessibilityReport(
                "seed-2",
                "Persiaran Newron",
                "Broken Crossing",
                "The pedestrian crossing signal is not working.",
                now - 7200_000L,
                now - 5400_000L,
                null,
                2,
                1,
                AccessibilityReport.STATUS_UNCERTAIN,
                false
        ));
        reports.add(new AccessibilityReport(
                "seed-3",
                "Institute for Postgraduate Studies",
                "Blocked Tactile Path",
                "Tactile paving is covered by parked bicycles.",
                now - 10_800_000L,
                now - 9000_000L,
                null,
                5,
                0,
                AccessibilityReport.STATUS_CONFIRMED,
                false
        ));
    }

    private void load() {
        reports.clear();
        String raw = prefs.getString(KEY_REPORTS, null);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                reports.add(AccessibilityReport.fromJson(obj));
            }
        } catch (JSONException ignored) {
            reports.clear();
        }
    }

    private void save() {
        JSONArray array = new JSONArray();
        try {
            for (AccessibilityReport report : reports) {
                array.put(report.toJson());
            }
            prefs.edit().putString(KEY_REPORTS, array.toString()).apply();
        } catch (JSONException ignored) {
            // skip failed serialize
        }
    }
}

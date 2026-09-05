package com.example.hackathon.utils;

import android.content.Context;

import com.example.hackathon.models.AccessibilityReport;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared persistence for community obstacle reports, backed by Firestore
 * so reports are visible across every user's device, not just the one
 * that created them.
 *
 * Keeps the same public method names/signatures as the old SharedPreferences
 * version where possible, so MyReportsActivity, ReportDetailActivity, and
 * ReportActivity need minimal changes. The one required change: addReport()
 * now takes lat/lng, since that's what lets route scoring use these reports.
 */
public class ObstacleReportStore {

    private static final String COLLECTION = "obstacle_reports";

    public interface ReportsListener {
        void onReportsChanged();
    }

    private static ObstacleReportStore instance;

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final String deviceId;
    private final List<AccessibilityReport> reports = new ArrayList<>();
    private final List<ReportsListener> listeners = new ArrayList<>();

    private ObstacleReportStore(Context context) {
        deviceId = DeviceIdProvider.getDeviceId(context);
        listenForChanges();
    }

    public static synchronized ObstacleReportStore getInstance(Context context) {
        if (instance == null) {
            instance = new ObstacleReportStore(context);
        }
        return instance;
    }

    public void addListener(ReportsListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ReportsListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (ReportsListener listener : new ArrayList<>(listeners)) {
            listener.onReportsChanged();
        }
    }

    /** Live-syncs the local cache with Firestore so every device sees the same reports. */
    private void listenForChanges() {
        firestore.collection(COLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        return;
                    }
                    reports.clear();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        reports.add(fromDocument(doc));
                    }
                    notifyListeners();
                });
    }

    /** Returns whatever is currently cached locally — updates live as Firestore syncs. */
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

    /**
     * Creates a new report. lat/lng are required now so route scoring can
     * match this report against a walking path.
     */
    public AccessibilityReport addReport(
            String locationName,
            double lat,
            double lng,
            String issueType,
            String description
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("locationName", locationName);
        data.put("lat", lat);
        data.put("lng", lng);
        data.put("issueType", issueType);
        data.put("description", description);
        data.put("timestamp", System.currentTimeMillis());
        data.put("stillThereCount", 0);
        data.put("notThereCount", 0);
        data.put("status", AccessibilityReport.STATUS_ACTIVE);
        data.put("reporterId", deviceId);

        DocumentReference ref = firestore.collection(COLLECTION).document();
        ref.set(data);
        // The snapshot listener will pick this up and refresh the cache shortly;
        // we don't block on it here since the UI already shows a success toast.

        AccessibilityReport optimistic = new AccessibilityReport(
                ref.getId(), locationName, lat, lng, issueType, description,
                (Long) data.get("timestamp"), 0, 0, AccessibilityReport.STATUS_ACTIVE,
                true, deviceId);
        return optimistic;
    }

    public void markStillThere(String id) {
        if (id == null) {
            return;
        }
        AccessibilityReport report = getById(id);
        int newCount = (report != null ? report.getStillThereCount() : 0) + 1;
        firestore.collection(COLLECTION).document(id)
                .update("stillThereCount", newCount);
    }

    public void markNotThere(String id) {
        if (id == null) {
            return;
        }
        AccessibilityReport report = getById(id);
        int newCount = (report != null ? report.getNotThereCount() : 0) + 1;
        firestore.collection(COLLECTION).document(id)
                .update("notThereCount", newCount);
    }

    private AccessibilityReport fromDocument(DocumentSnapshot doc) {
        String reporterId = doc.getString("reporterId");
        Long timestamp = doc.getLong("timestamp");
        Long stillThere = doc.getLong("stillThereCount");
        Long notThere = doc.getLong("notThereCount");
        Double lat = doc.getDouble("lat");
        Double lng = doc.getDouble("lng");

        return new AccessibilityReport(
                doc.getId(),
                doc.getString("locationName"),
                lat != null ? lat : 0,
                lng != null ? lng : 0,
                doc.getString("issueType"),
                doc.getString("description"),
                timestamp != null ? timestamp : 0,
                stillThere != null ? stillThere.intValue() : 0,
                notThere != null ? notThere.intValue() : 0,
                doc.getString("status"),
                deviceId.equals(reporterId),
                reporterId
        );
    }
}
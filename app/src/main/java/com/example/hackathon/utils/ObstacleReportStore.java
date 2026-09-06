package com.example.hackathon.utils;

import android.content.Context;
import android.content.SharedPreferences;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared persistence for community obstacle reports, backed by Firestore
 * so reports are visible across devices. Vote history stays on-device so
 * each install can only confirm/dispute a report once.
 */
public class ObstacleReportStore {

    private static final String COLLECTION = "obstacle_reports";
    private static final String VOTE_PREFS = "obstacle_report_votes";
    private static final String KEY_VOTED = "voted_report_ids";

    public interface ReportsListener {
        void onReportsChanged();
    }

    private static ObstacleReportStore instance;

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final SharedPreferences votePrefs;
    private final String deviceId;
    private final List<AccessibilityReport> reports = new ArrayList<>();
    private final List<ReportsListener> listeners = new ArrayList<>();

    private ObstacleReportStore(Context context) {
        Context app = context.getApplicationContext();
        deviceId = DeviceIdProvider.getDeviceId(app);
        votePrefs = app.getSharedPreferences(VOTE_PREFS, Context.MODE_PRIVATE);
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
            double lat,
            double lng,
            String issueType,
            String category,
            String description
    ) {
        return addReport(locationName, lat, lng, issueType, category, description, null);
    }

    public AccessibilityReport addReport(
            String locationName,
            double lat,
            double lng,
            String issueType,
            String category,
            String description,
            String photoPath
    ) {
        long now = System.currentTimeMillis();
        Map<String, Object> data = new HashMap<>();
        data.put("locationName", locationName);
        data.put("lat", lat);
        data.put("lng", lng);
        data.put("issueType", issueType);
        data.put("category", category != null ? category : AccessibilityReport.CATEGORY_OBSTACLE);
        data.put("description", description);
        data.put("timestamp", now);
        data.put("lastVerifiedAt", 0L);
        data.put("photoPath", photoPath != null ? photoPath : "");
        data.put("stillThereCount", 0);
        data.put("notThereCount", 0);
        data.put("status", AccessibilityReport.STATUS_ACTIVE);
        data.put("reporterId", deviceId);

        DocumentReference ref = firestore.collection(COLLECTION).document();
        AccessibilityReport optimistic = new AccessibilityReport(
                ref.getId(),
                locationName,
                lat,
                lng,
                issueType,
                category,
                description,
                now,
                0L,
                photoPath,
                0,
                0,
                AccessibilityReport.STATUS_ACTIVE,
                true,
                deviceId
        );

        // Show on map immediately — don't wait for the Firestore snapshot round-trip.
        reports.add(0, optimistic);
        notifyListeners();

        ref.set(data);
        return optimistic;
    }

    public boolean hasVoted(String reportId) {
        return getVotedIds().contains(reportId);
    }

    public void markStillThere(String id) {
        if (id == null) {
            return;
        }
        rememberVote(id);
        AccessibilityReport report = getById(id);
        int still = (report != null ? report.getStillThereCount() : 0) + 1;
        int notThere = report != null ? report.getNotThereCount() : 0;
        pushVoteUpdate(id, still, notThere);
    }

    public void markNotThere(String id) {
        if (id == null) {
            return;
        }
        rememberVote(id);
        AccessibilityReport report = getById(id);
        int still = report != null ? report.getStillThereCount() : 0;
        int notThere = (report != null ? report.getNotThereCount() : 0) + 1;
        pushVoteUpdate(id, still, notThere);
    }

    private void pushVoteUpdate(String id, int stillThereCount, int notThereCount) {
        String status = deriveStatus(stillThereCount, notThereCount);
        Map<String, Object> updates = new HashMap<>();
        updates.put("stillThereCount", stillThereCount);
        updates.put("notThereCount", notThereCount);
        updates.put("lastVerifiedAt", System.currentTimeMillis());
        updates.put("status", status);
        firestore.collection(COLLECTION).document(id).update(updates);
    }

    private static String deriveStatus(int stillThereCount, int notThereCount) {
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

    private void rememberVote(String id) {
        Set<String> voted = new HashSet<>(getVotedIds());
        voted.add(id);
        votePrefs.edit().putStringSet(KEY_VOTED, voted).apply();
    }

    private Set<String> getVotedIds() {
        Set<String> stored = votePrefs.getStringSet(KEY_VOTED, null);
        if (stored == null) {
            return new HashSet<>();
        }
        return new HashSet<>(stored);
    }

    private AccessibilityReport fromDocument(DocumentSnapshot doc) {
        String reporterId = doc.getString("reporterId");
        Long timestamp = doc.getLong("timestamp");
        Long lastVerifiedAt = doc.getLong("lastVerifiedAt");
        Long stillThere = doc.getLong("stillThereCount");
        Long notThere = doc.getLong("notThereCount");
        Double lat = doc.getDouble("lat");
        Double lng = doc.getDouble("lng");
        String category = doc.getString("category");
        String photo = doc.getString("photoPath");
        if (photo != null && photo.isEmpty()) {
            photo = null;
        }

        return new AccessibilityReport(
                doc.getId(),
                doc.getString("locationName"),
                lat != null ? lat : 0,
                lng != null ? lng : 0,
                doc.getString("issueType"),
                category != null ? category : AccessibilityReport.CATEGORY_OBSTACLE,
                doc.getString("description"),
                timestamp != null ? timestamp : 0,
                lastVerifiedAt != null ? lastVerifiedAt : 0,
                photo,
                stillThere != null ? stillThere.intValue() : 0,
                notThere != null ? notThere.intValue() : 0,
                doc.getString("status"),
                deviceId.equals(reporterId),
                reporterId
        );
    }
}

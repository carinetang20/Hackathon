package com.example.hackathon.models;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A community obstacle / barrier report that helps warn
 * visually impaired users and can be verified over time.
 */
public class AccessibilityReport {

    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_CONFIRMED = "Still there";
    public static final String STATUS_CLEARED = "Cleared";
    public static final String STATUS_UNCERTAIN = "Uncertain";

    private String id;
    private String locationName;
    private String issueType;
    private String description;
    private long timestamp;
    private int stillThereCount;
    private int notThereCount;
    private String status;
    private boolean submittedByMe;

    public AccessibilityReport(
            String id,
            String locationName,
            String issueType,
            String description,
            long timestamp
    ) {
        this(id, locationName, issueType, description, timestamp, 0, 0, STATUS_ACTIVE, false);
    }

    public AccessibilityReport(
            String id,
            String locationName,
            String issueType,
            String description,
            long timestamp,
            int stillThereCount,
            int notThereCount,
            String status,
            boolean submittedByMe
    ) {
        this.id = id;
        this.locationName = locationName;
        this.issueType = issueType;
        this.description = description;
        this.timestamp = timestamp;
        this.stillThereCount = stillThereCount;
        this.notThereCount = notThereCount;
        this.status = status != null ? status : STATUS_ACTIVE;
        this.submittedByMe = submittedByMe;
        refreshStatus();
    }

    public String getId() {
        return id;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getIssueType() {
        return issueType;
    }

    public String getDescription() {
        return description;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getStillThereCount() {
        return stillThereCount;
    }

    public int getNotThereCount() {
        return notThereCount;
    }

    /** @deprecated Use {@link #getStillThereCount()} */
    public int getConfirmations() {
        return stillThereCount;
    }

    /** @deprecated Use {@link #getNotThereCount()} */
    public int getDisputes() {
        return notThereCount;
    }

    public String getStatus() {
        return status;
    }

    public boolean isSubmittedByMe() {
        return submittedByMe;
    }

    public void setSubmittedByMe(boolean submittedByMe) {
        this.submittedByMe = submittedByMe;
    }

    public void markStillThere() {
        stillThereCount++;
        refreshStatus();
    }

    public void markNotThere() {
        notThereCount++;
        refreshStatus();
    }

    /** @deprecated Use {@link #markStillThere()} */
    public void confirm() {
        markStillThere();
    }

    /** @deprecated Use {@link #markNotThere()} */
    public void dispute() {
        markNotThere();
    }

    /**
     * Updates status from community votes:
     * - Cleared when enough people say it is gone
     * - Still there when enough people confirm it remains
     * - Uncertain when votes conflict
     * - Active when newly reported / few votes
     */
    public void refreshStatus() {
        if (notThereCount >= 2 && notThereCount > stillThereCount) {
            status = STATUS_CLEARED;
        } else if (stillThereCount >= 2 && stillThereCount > notThereCount) {
            status = STATUS_CONFIRMED;
        } else if (stillThereCount > 0 && notThereCount > 0) {
            status = STATUS_UNCERTAIN;
        } else if (stillThereCount > 0) {
            status = STATUS_CONFIRMED;
        } else {
            status = STATUS_ACTIVE;
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("locationName", locationName);
        json.put("issueType", issueType);
        json.put("description", description);
        json.put("timestamp", timestamp);
        json.put("stillThereCount", stillThereCount);
        json.put("notThereCount", notThereCount);
        json.put("status", status);
        json.put("submittedByMe", submittedByMe);
        return json;
    }

    public static AccessibilityReport fromJson(JSONObject json) throws JSONException {
        return new AccessibilityReport(
                json.getString("id"),
                json.getString("locationName"),
                json.getString("issueType"),
                json.getString("description"),
                json.getLong("timestamp"),
                json.optInt("stillThereCount", json.optInt("confirmations", 0)),
                json.optInt("notThereCount", json.optInt("disputes", 0)),
                json.optString("status", STATUS_ACTIVE),
                json.optBoolean("submittedByMe", false)
        );
    }
}

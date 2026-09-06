package com.example.hackathon.models;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A community obstacle / barrier report that helps warn
 * visually impaired users and can be verified over time.
 *
 * Now includes lat/lng so reports can be matched against route
 * polylines for accessibility scoring, not just displayed by name.
 */
public class AccessibilityReport {

    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_CONFIRMED = "Still there";
    public static final String STATUS_CLEARED = "Cleared";
    public static final String STATUS_UNCERTAIN = "Uncertain";

    public static final String CATEGORY_OBSTACLE = "obstacle";
    public static final String CATEGORY_FACILITY = "facility";

    private String id;
    private String locationName;
    private double lat;
    private double lng;
    private String issueType;
    private String category;
    private String description;
    private long timestamp;
    private int stillThereCount;
    private int notThereCount;
    private String status;
    private boolean submittedByMe;
    private String reporterId;

    public AccessibilityReport(
            String id,
            String locationName,
            double lat,
            double lng,
            String issueType,
            String category,
            String description,
            long timestamp
    ) {
        this(id, locationName, lat, lng, issueType, category, description, timestamp,
                0, 0, STATUS_ACTIVE, false, null);
    }

    public AccessibilityReport(
            String id,
            String locationName,
            double lat,
            double lng,
            String issueType,
            String category,
            String description,
            long timestamp,
            int stillThereCount,
            int notThereCount,
            String status,
            boolean submittedByMe,
            String reporterId
    ) {
        this.id = id;
        this.locationName = locationName;
        this.lat = lat;
        this.lng = lng;
        this.issueType = issueType;
        this.category = category != null ? category : CATEGORY_OBSTACLE;
        this.description = description;
        this.timestamp = timestamp;
        this.stillThereCount = stillThereCount;
        this.notThereCount = notThereCount;
        this.status = status != null ? status : STATUS_ACTIVE;
        this.submittedByMe = submittedByMe;
        this.reporterId = reporterId;
        refreshStatus();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLocationName() {
        return locationName;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public String getIssueType() {
        return issueType;
    }

    public String getCategory() {
        return category;
    }

    public boolean isFacility() {
        return CATEGORY_FACILITY.equals(category);
    }

    public boolean isObstacle() {
        return CATEGORY_OBSTACLE.equals(category);
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

    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
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
     * Rough penalty this report should apply to a route's accessibility
     * score if it lies on the path. Facility reports (ramps, elevators,
     * tactile paving) never penalize a route — they're positive/informational
     * markers, not obstacles.
     */
    public int penaltyPoints() {
        if (isFacility()) {
            return 0;
        }
        if (STATUS_CLEARED.equals(status)) {
            return 0; // community confirmed it's gone — don't penalize routes for it
        }

        int basePenalty;
        switch (issueType == null ? "" : issueType) {
            case "Blocked Ramp":
            case "Blocked Tactile Path":
                basePenalty = 40;
                break;
            case "Broken Crossing":
            case "Open Drain":
                basePenalty = 30;
                break;
            case "Construction":
            case "Temporary Barrier":
                basePenalty = 25;
                break;
            case "Illegal Parking":
            case "Debris / Obstacle":
            case "Overgrown Vegetation":
                basePenalty = 15;
                break;
            case "Pothole":
                basePenalty = 10;
                break;
            default:
                basePenalty = 10;
        }

        // Uncertain reports (conflicting votes) count for less than confirmed ones.
        if (STATUS_UNCERTAIN.equals(status)) {
            return basePenalty / 2;
        }
        return basePenalty;
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
        json.put("lat", lat);
        json.put("lng", lng);
        json.put("issueType", issueType);
        json.put("category", category);
        json.put("description", description);
        json.put("timestamp", timestamp);
        json.put("stillThereCount", stillThereCount);
        json.put("notThereCount", notThereCount);
        json.put("status", status);
        json.put("submittedByMe", submittedByMe);
        json.put("reporterId", reporterId);
        return json;
    }

    public static AccessibilityReport fromJson(JSONObject json) throws JSONException {
        return new AccessibilityReport(
                json.getString("id"),
                json.getString("locationName"),
                json.optDouble("lat", 0),
                json.optDouble("lng", 0),
                json.getString("issueType"),
                json.optString("category", CATEGORY_OBSTACLE),
                json.getString("description"),
                json.getLong("timestamp"),
                json.optInt("stillThereCount", json.optInt("confirmations", 0)),
                json.optInt("notThereCount", json.optInt("disputes", 0)),
                json.optString("status", STATUS_ACTIVE),
                json.optBoolean("submittedByMe", false),
                json.optString("reporterId", null)
        );
    }
}
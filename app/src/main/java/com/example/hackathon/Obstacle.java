package com.example.hackathon;

/**
 * Represents a single accessibility obstacle report, e.g. a broken elevator,
 * missing ramp, or steep incline. Maps directly onto a document in the
 * Firestore "obstacles" collection.
 */
public class Obstacle {

    public static final String TYPE_BROKEN_ELEVATOR = "broken_elevator";
    public static final String TYPE_NO_RAMP = "no_ramp";
    public static final String TYPE_STEEP_INCLINE = "steep_incline";
    public static final String TYPE_CONSTRUCTION = "construction";
    public static final String TYPE_NARROW_PATH = "narrow_path";

    public static final String SEVERITY_BLOCKER = "blocker"; // route must avoid
    public static final String SEVERITY_WARNING = "warning";  // route can pass, but should warn user

    private String id;
    private double lat;
    private double lng;
    private String type;
    private String severity;
    private String reportedBy;
    private long timestamp;
    private boolean verified;

    // Firestore requires a no-arg constructor for automatic deserialization.
    public Obstacle() {
    }

    public Obstacle(String id, double lat, double lng, String type, String severity,
                    String reportedBy, long timestamp, boolean verified) {
        this.id = id;
        this.lat = lat;
        this.lng = lng;
        this.type = type;
        this.severity = severity;
        this.reportedBy = reportedBy;
        this.timestamp = timestamp;
        this.verified = verified;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(String reportedBy) {
        this.reportedBy = reportedBy;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    /**
     * Penalty points this obstacle should subtract from a route's
     * accessibility score if it lies on the path. Tune freely for your demo.
     */
    public int penaltyPoints() {
        switch (type == null ? "" : type) {
            case TYPE_BROKEN_ELEVATOR:
                return 40;
            case TYPE_NO_RAMP:
                return 40;
            case TYPE_STEEP_INCLINE:
                return 20;
            case TYPE_CONSTRUCTION:
                return 25;
            case TYPE_NARROW_PATH:
                return 15;
            default:
                return 10;
        }
    }
}
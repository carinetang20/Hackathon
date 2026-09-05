package com.example.hackathon.models;

/**
 * Lightweight display model kept for compatibility.
 * Prefer {@link AccessibilityReport} for new code.
 */
public class MyReport {

    private String id;
    private String location;
    private String issueType;
    private String description;
    private String status;
    private int confirmations;
    private int disputes;

    public MyReport(String location, String issueType, String description,
                    String status, int confirmations, int disputes) {
        this(null, location, issueType, description, status, confirmations, disputes);
    }

    public MyReport(String id, String location, String issueType, String description,
                    String status, int confirmations, int disputes) {
        this.id = id;
        this.location = location;
        this.issueType = issueType;
        this.description = description;
        this.status = status;
        this.confirmations = confirmations;
        this.disputes = disputes;
    }

    public static MyReport from(AccessibilityReport report) {
        return new MyReport(
                report.getId(),
                report.getLocationName(),
                report.getIssueType(),
                report.getDescription(),
                report.getStatus(),
                report.getStillThereCount(),
                report.getNotThereCount()
        );
    }

    public String getId() { return id; }
    public String getLocation() { return location; }
    public String getIssueType() { return issueType; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public int getConfirmations() { return confirmations; }
    public int getDisputes() { return disputes; }
}

package models;
public class AccessibilityReport {

    private String id;
    private String locationName;
    private String issueType;
    private String description;

    private long timestamp;

    private int confirmations;
    private int disputes;

    public AccessibilityReport(
            String id,
            String locationName,
            String issueType,
            String description,
            long timestamp
    ) {

        this.id = id;
        this.locationName = locationName;
        this.issueType = issueType;
        this.description = description;
        this.timestamp = timestamp;

        this.confirmations = 0;
        this.disputes = 0;
    }

    public void confirm() {
        confirmations++;
    }

    public void dispute() {
        disputes++;
    }

    public int getConfirmations() {
        return confirmations;
    }

    public int getDisputes() {
        return disputes;
    }
}
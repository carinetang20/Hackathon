package com.example.hackathon.models;

public class MyReport {

    private String location;
    private String issueType;
    private String description;
    private String status;
    private int confirmations;
    private int disputes;

    public MyReport(String location, String issueType, String description,
                    String status, int confirmations, int disputes) {
        this.location = location;
        this.issueType = issueType;
        this.description = description;
        this.status = status;
        this.confirmations = confirmations;
        this.disputes = disputes;
    }

    public String getLocation() { return location; }
    public String getIssueType() { return issueType; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public int getConfirmations() { return confirmations; }
    public int getDisputes() { return disputes; }
}
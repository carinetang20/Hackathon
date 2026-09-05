package com.example.hackathon.models;

public class NotificationItem {

    private String title;
    private String subtitle;
    private String timeAgo;
    private boolean unread;

    public NotificationItem(String title, String subtitle, String timeAgo, boolean unread) {
        this.title = title;
        this.subtitle = subtitle;
        this.timeAgo = timeAgo;
        this.unread = unread;
    }

    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getTimeAgo() { return timeAgo; }
    public boolean isUnread() { return unread; }

    public void markRead() { this.unread = false; }
}
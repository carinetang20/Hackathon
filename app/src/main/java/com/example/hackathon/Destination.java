package com.example.hackathon;

/**
 * Represents a destination place with its own accessibility attributes,
 * sourced first from the Places API (entrance/restroom/parking/elevator
 * fields) and then optionally overridden by crowdsourced Firestore reports.
 * Maps onto a document in the Firestore "destinations" collection.
 */
public class Destination {

    private String id;
    private String googlePlaceId;
    private String name;
    private double lat;
    private double lng;

    private Boolean hasAccessibleEntrance;
    private Boolean hasAccessibleRestroom;
    private Boolean hasWorkingElevator;
    private Boolean hasAccessibleParking;
    private long lastVerified;

    public Destination() {
    }

    public Destination(String id, String googlePlaceId, String name, double lat, double lng,
                       Boolean hasAccessibleEntrance, Boolean hasAccessibleRestroom,
                       Boolean hasWorkingElevator, Boolean hasAccessibleParking,
                       long lastVerified) {
        this.id = id;
        this.googlePlaceId = googlePlaceId;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.hasAccessibleEntrance = hasAccessibleEntrance;
        this.hasAccessibleRestroom = hasAccessibleRestroom;
        this.hasWorkingElevator = hasWorkingElevator;
        this.hasAccessibleParking = hasAccessibleParking;
        this.lastVerified = lastVerified;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public void setGooglePlaceId(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Boolean getHasAccessibleEntrance() {
        return hasAccessibleEntrance;
    }

    public void setHasAccessibleEntrance(Boolean hasAccessibleEntrance) {
        this.hasAccessibleEntrance = hasAccessibleEntrance;
    }

    public Boolean getHasAccessibleRestroom() {
        return hasAccessibleRestroom;
    }

    public void setHasAccessibleRestroom(Boolean hasAccessibleRestroom) {
        this.hasAccessibleRestroom = hasAccessibleRestroom;
    }

    public Boolean getHasWorkingElevator() {
        return hasWorkingElevator;
    }

    public void setHasWorkingElevator(Boolean hasWorkingElevator) {
        this.hasWorkingElevator = hasWorkingElevator;
    }

    public Boolean getHasAccessibleParking() {
        return hasAccessibleParking;
    }

    public void setHasAccessibleParking(Boolean hasAccessibleParking) {
        this.hasAccessibleParking = hasAccessibleParking;
    }

    public long getLastVerified() {
        return lastVerified;
    }

    public void setLastVerified(long lastVerified) {
        this.lastVerified = lastVerified;
    }

    /**
     * Scores the destination itself (independent of the route to reach it).
     * Starts at 100 and deducts points for each missing/unknown attribute.
     * Unknown (null) attributes are treated as a smaller penalty than a
     * confirmed "false", since we don't want to punish places nobody has
     * reported on yet as harshly as places confirmed inaccessible.
     */
    public int accessibilityScore() {
        int score = 100;
        score -= attributePenalty(hasAccessibleEntrance, 40, 10);
        score -= attributePenalty(hasWorkingElevator, 25, 5);
        score -= attributePenalty(hasAccessibleRestroom, 15, 3);
        score -= attributePenalty(hasAccessibleParking, 10, 2);
        return Math.max(score, 0);
    }

    private int attributePenalty(Boolean attribute, int falsePenalty, int unknownPenalty) {
        if (attribute == null) {
            return unknownPenalty;
        }
        return attribute ? 0 : falsePenalty;
    }
}
package com.example.hackathon;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps Firestore reads/writes for the "obstacles" and "destinations"
 * collections. Uses a simple bounding-box query for obstacles so we don't
 * pull the entire collection down for every route request.
 */
public class FirestoreAccessibilityRepository {

    public interface ObstaclesCallback {
        void onSuccess(List<Obstacle> obstacles);
        void onError(Exception e);
    }

    public interface DestinationCallback {
        void onSuccess(Destination destination);
        void onError(Exception e);
    }

    public interface WriteCallback {
        void onSuccess();
        void onError(Exception e);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Fetches obstacles inside a rough bounding box around the route, so we
     * only pay for reads relevant to the current trip. Pass a small margin
     * (e.g. 0.01 degrees, roughly 1km) beyond the route's own bounds.
     */
    public void getObstaclesInBounds(double minLat, double maxLat,
                                     double minLng, double maxLng,
                                     ObstaclesCallback callback) {
        CollectionReference obstaclesRef = db.collection("obstacles");

        // Firestore range queries only support inequality filters on one
        // field at a time, so we filter latitude server-side and longitude
        // client-side.
        Query query = obstaclesRef
                .whereGreaterThanOrEqualTo("lat", minLat)
                .whereLessThanOrEqualTo("lat", maxLat);

        query.get().addOnSuccessListener(snapshot -> {
            List<Obstacle> obstacles = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshot) {
                Obstacle obstacle = doc.toObject(Obstacle.class);
                obstacle.setId(doc.getId());
                if (obstacle.getLng() >= minLng && obstacle.getLng() <= maxLng) {
                    obstacles.add(obstacle);
                }
            }
            callback.onSuccess(obstacles);
        }).addOnFailureListener(callback::onError);
    }

    /**
     * Submits a new crowdsourced obstacle report.
     */
    public void reportObstacle(Obstacle obstacle, WriteCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("lat", obstacle.getLat());
        data.put("lng", obstacle.getLng());
        data.put("type", obstacle.getType());
        data.put("severity", obstacle.getSeverity());
        data.put("reportedBy", obstacle.getReportedBy());
        data.put("timestamp", obstacle.getTimestamp());
        data.put("verified", false); // new reports start unverified

        db.collection("obstacles")
                .add(data)
                .addOnSuccessListener(ref -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    /**
     * Looks up a destination's stored accessibility attributes by Google
     * Place ID. Returns null via onSuccess if no document exists yet — the
     * caller should fall back to Places API data or "unknown" attributes.
     */
    public void getDestinationByPlaceId(String googlePlaceId, DestinationCallback callback) {
        db.collection("destinations")
                .whereEqualTo("googlePlaceId", googlePlaceId)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onSuccess(null);
                        return;
                    }
                    QueryDocumentSnapshot doc = snapshot.getDocuments().isEmpty()
                            ? null
                            : (QueryDocumentSnapshot) snapshot.getDocuments().get(0);
                    Destination destination = doc.toObject(Destination.class);
                    destination.setId(doc.getId());
                    callback.onSuccess(destination);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Creates or updates a destination's accessibility attributes, e.g.
     * after a user reports "the elevator here is broken."
     */
    public void upsertDestination(Destination destination, WriteCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("googlePlaceId", destination.getGooglePlaceId());
        data.put("name", destination.getName());
        data.put("lat", destination.getLat());
        data.put("lng", destination.getLng());
        data.put("hasAccessibleEntrance", destination.getHasAccessibleEntrance());
        data.put("hasAccessibleRestroom", destination.getHasAccessibleRestroom());
        data.put("hasWorkingElevator", destination.getHasWorkingElevator());
        data.put("hasAccessibleParking", destination.getHasAccessibleParking());
        data.put("lastVerified", destination.getLastVerified());

        if (destination.getId() != null) {
            db.collection("destinations").document(destination.getId())
                    .set(data)
                    .addOnSuccessListener(unused -> callback.onSuccess())
                    .addOnFailureListener(callback::onError);
        } else {
            db.collection("destinations").add(data)
                    .addOnSuccessListener(ref -> callback.onSuccess())
                    .addOnFailureListener(callback::onError);
        }
    }
}
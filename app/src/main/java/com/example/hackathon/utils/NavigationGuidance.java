package com.example.hackathon.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Outdoor pathway guidance: walkways, stairs, ramps — not indoor rooms.
 * Uses GPS area (when available) + what the camera sees.
 */
public final class NavigationGuidance {

    private NavigationGuidance() { }

    public static class Result {
        public final String spoken;
        public final String summary;
        public final boolean hazardDetected;

        public Result(String spoken, String summary, boolean hazardDetected) {
            this.spoken = spoken;
            this.summary = summary;
            this.hazardDetected = hazardDetected;
        }
    }

    /**
     * @param labels camera labels from ML Kit
     * @param campusArea e.g. "near STAD Building" or "outdoors on a walkway"
     * @param pathwayHint place-specific pathway tip from {@link CampusLocator}
     */
    public static Result build(List<String> labels, String campusArea, String pathwayHint) {
        List<String> lower = new ArrayList<>();
        List<String> pathwayDisplay = new ArrayList<>();
        if (labels != null) {
            for (String label : labels) {
                if (label == null || label.trim().isEmpty()) {
                    continue;
                }
                String l = label.toLowerCase(Locale.US);
                lower.add(l);
                if (isPathwayRelevant(l)) {
                    pathwayDisplay.add(label);
                }
            }
        }

        boolean stairs = containsAny(lower, "stairs", "staircase", "step", "stair");
        boolean ramp = containsAny(lower, "ramp", "slope", "incline");
        boolean walkway = containsAny(lower,
                "sidewalk", "pavement", "walkway", "path", "pathway", "footpath",
                "plaza", "courtyard", "tile", "tiled");
        boolean ground = containsAny(lower, "road", "street", "asphalt", "concrete", "ground", "floor");
        boolean person = containsAny(lower, "person", "people", "pedestrian", "crowd", "student");
        boolean construction = containsAny(lower,
                "construction", "cone", "barrier", "fence", "scaffolding", "tape");
        boolean pole = containsAny(lower, "pole", "pillar", "column", "post", "sign", "lamp");
        boolean tree = containsAny(lower, "tree", "bush", "vegetation", "plant", "grass");
        boolean building = containsAny(lower,
                "building", "university", "campus", "hall", "library", "faculty");
        boolean bike = containsAny(lower, "bicycle", "bike", "scooter", "motorcycle");
        boolean vehicle = containsAny(lower, "car", "vehicle", "truck", "bus", "van");
        boolean sky = containsAny(lower, "sky", "cloud", "outdoor");
        boolean furniture = containsAny(lower, "chair", "table", "bench", "seating");
        boolean animal = containsAny(lower, "cat", "dog", "animal");
        boolean indoorRoom = containsAny(lower,
                "room", "ceiling", "indoor", "sofa", "bed", "desk",
                "carpet", "kitchen", "bedroom", "living room");

        boolean coveredWalkway = walkway || (pole && building) || containsAny(lower, "courtyard");

        String area = (campusArea == null || campusArea.isEmpty())
                ? "outdoors on a walkway"
                : campusArea;
        String hint = (pathwayHint == null || pathwayHint.isEmpty())
                ? "outdoor walkway or stairs"
                : pathwayHint;

        List<String> phrases = new ArrayList<>();
        boolean hazard = false;

        List<String> seen = !pathwayDisplay.isEmpty() ? pathwayDisplay : new ArrayList<>();
        if (seen.isEmpty() && labels != null) {
            for (String label : labels) {
                if (label != null && !label.trim().isEmpty()
                        && !isIndoorOnly(label.toLowerCase(Locale.US))) {
                    seen.add(label);
                    if (seen.size() >= 3) {
                        break;
                    }
                }
            }
        }

        phrases.add("You are " + area + ".");

        if (indoorRoom && !stairs && !coveredWalkway && !sky && !building && !pole) {
            phrases.add("The camera looks pointed at an indoor room.");
            phrases.add("Face the outdoor pathway — " + hint + " — then scan again.");
            return new Result(String.join(" ", phrases), "Face the pathway", false);
        }

        if (!seen.isEmpty()) {
            int limit = Math.min(3, seen.size());
            phrases.add("On the pathway ahead I see "
                    + String.join(", ", seen.subList(0, limit)) + ".");
        }

        if (stairs) {
            hazard = true;
            phrases.add("Stairs ahead toward the courtyard.");
            phrases.add("Continue straight along the tiled walkway.");
            phrases.add("When you reach the stairs, find the handrail and take one step at a time.");
        } else if (ramp) {
            hazard = true;
            phrases.add("A ramp or slope is ahead.");
            phrases.add("Go slowly up or down the ramp, staying in the center.");
        } else if (construction) {
            hazard = true;
            phrases.add("A barrier is blocking this walkway.");
            phrases.add("Stop. Turn right and follow the open path around it.");
        } else if (vehicle || bike) {
            hazard = true;
            phrases.add(vehicle
                    ? "Caution. A vehicle is near the path. Stay on the pedestrian walkway."
                    : "A bicycle or scooter is on the walkway. Keep left and continue when clear.");
        } else if (animal) {
            phrases.add("A small animal is on the walkway. Step carefully around it and continue straight.");
        } else if (furniture && coveredWalkway) {
            phrases.add("Seating is ahead in the courtyard area. Keep to the open tiled path.");
            phrases.add("Continue straight. Stairs may be on your right farther ahead.");
        } else if (pole && coveredWalkway) {
            phrases.add("Concrete pillars line the covered walkway. Stay in the center of the path.");
            phrases.add("Continue straight toward daylight in the courtyard. Stairs are ahead.");
        } else if (pole || tree) {
            hazard = true;
            phrases.add("Obstacle on the walkway. Slow down.");
            phrases.add("Turn slightly left, then continue straight along the path.");
        } else if (person) {
            phrases.add("People ahead on the pathway. Keep to your right and continue when clear.");
        } else if (coveredWalkway || walkway || ground || sky || building) {
            phrases.add("Covered walkway looks clear. Continue straight ahead.");
            phrases.add("Follow the " + hint + ".");
            if (building) {
                phrases.add("Buildings open into the courtyard ahead.");
            }
        } else if (!seen.isEmpty()) {
            phrases.add("Continue straight slowly on the pathway.");
            phrases.add("Scan again before stairs or any crossing.");
        } else {
            phrases.add("Point the camera at the outdoor walkway or stairs.");
            phrases.add("Try facing the " + hint + ", then scan again.");
        }

        String summary = hazard ? "Pathway hazard" : "Pathway guidance";
        return new Result(String.join(" ", phrases), summary, hazard);
    }

    /** Fallback when location is unknown. */
    public static Result build(List<String> labels) {
        return build(
                labels,
                "outdoors on a walkway",
                "outdoor walkway or stairs"
        );
    }

    private static boolean isIndoorOnly(String lower) {
        return containsAny(List.of(lower),
                "room", "ceiling", "furniture", "sofa", "bed", "desk", "carpet",
                "kitchen", "bedroom", "living room", "couch", "pillow");
    }

    private static boolean isPathwayRelevant(String lower) {
        return containsAny(List.of(lower),
                "stairs", "staircase", "step", "ramp", "sidewalk", "pavement", "walkway",
                "path", "pathway", "plaza", "courtyard", "building", "university", "campus",
                "tree", "grass", "sky", "person", "pedestrian", "pole", "pillar", "column",
                "fence", "cone", "barrier", "bicycle", "bike", "scooter", "car", "sign", "lamp",
                "outdoor", "concrete", "asphalt", "hall", "library", "chair", "tile", "floor");
    }

    private static boolean containsAny(List<String> labels, String... keys) {
        for (String label : labels) {
            for (String key : keys) {
                if (label.contains(key)) {
                    return true;
                }
            }
        }
        return false;
    }
}

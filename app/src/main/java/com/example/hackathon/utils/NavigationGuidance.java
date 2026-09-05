package com.example.hackathon.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MMU campus pathway guidance: walkways, stairs, ramps — not indoor rooms.
 * Uses GPS campus area (when available) + what the camera sees.
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
     * @param campusArea e.g. "near STAD Building" or "on MMU Cyberjaya campus"
     * @param pathwayHint place-specific pathway tip from {@link CampusLocator}
     */
    public static Result build(List<String> labels, String campusArea, String pathwayHint) {
        List<String> lower = new ArrayList<>();
        List<String> campusDisplay = new ArrayList<>();
        if (labels != null) {
            for (String label : labels) {
                if (label == null || label.trim().isEmpty()) {
                    continue;
                }
                String l = label.toLowerCase(Locale.US);
                lower.add(l);
                if (isCampusPathwayRelevant(l)) {
                    campusDisplay.add(label);
                }
            }
        }

        boolean stairs = containsAny(lower, "stairs", "staircase", "step", "stair");
        boolean ramp = containsAny(lower, "ramp", "slope", "incline");
        boolean walkway = containsAny(lower,
                "sidewalk", "pavement", "walkway", "path", "pathway", "footpath",
                "plaza", "courtyard");
        boolean ground = containsAny(lower, "road", "street", "asphalt", "concrete", "ground");
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
        boolean indoorRoom = containsAny(lower,
                "room", "ceiling", "indoor", "furniture", "sofa", "bed", "desk",
                "carpet", "kitchen", "bedroom", "living room");

        String area = (campusArea == null || campusArea.isEmpty())
                ? "MMU Cyberjaya campus"
                : campusArea;
        String hint = (pathwayHint == null || pathwayHint.isEmpty())
                ? "outdoor campus walkway or stairs"
                : pathwayHint;

        List<String> phrases = new ArrayList<>();
        boolean hazard = false;

        List<String> seen = !campusDisplay.isEmpty() ? campusDisplay : new ArrayList<>();
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

        // Always campus-framed
        phrases.add("You are " + area + ".");

        if (indoorRoom && !stairs && !walkway && !sky && !building) {
            phrases.add("The camera looks pointed at an indoor room.");
            phrases.add("Face the outdoor campus pathway — " + hint + " — then scan again.");
            return new Result(String.join(" ", phrases), "Face campus pathway", false);
        }

        if (!seen.isEmpty()) {
            int limit = Math.min(3, seen.size());
            phrases.add("On the campus pathway ahead I see "
                    + String.join(", ", seen.subList(0, limit)) + ".");
        }

        if (stairs) {
            hazard = true;
            phrases.add("Campus stairs ahead.");
            phrases.add("Stop. Find the handrail. Take one step at a time.");
            phrases.add("After the stairs, continue straight on the walkway toward campus buildings.");
        } else if (ramp) {
            hazard = true;
            phrases.add("A campus ramp or slope is ahead.");
            phrases.add("Go slowly up or down the ramp, staying in the center.");
        } else if (construction) {
            hazard = true;
            phrases.add("A barrier is blocking this campus walkway.");
            phrases.add("Stop. Turn right and follow the open path around it.");
        } else if (vehicle || bike) {
            hazard = true;
            phrases.add(vehicle
                    ? "Caution. A vehicle is near the campus path. Stay on the pedestrian walkway."
                    : "A bicycle or scooter is on the campus walkway. Keep left and continue when clear.");
        } else if (pole || tree) {
            hazard = true;
            phrases.add("Obstacle on the campus walkway. Slow down.");
            phrases.add("Turn slightly left, then continue straight along the path.");
        } else if (person) {
            phrases.add("People ahead on the campus pathway. Keep to your right and continue when clear.");
        } else if (walkway || ground || sky || building) {
            phrases.add("Campus walkway looks clear. Continue straight ahead.");
            phrases.add("Follow the " + hint + ".");
            if (building) {
                phrases.add("Campus buildings are beside the path.");
            }
        } else if (!seen.isEmpty()) {
            phrases.add("Continue straight slowly on the campus pathway.");
            phrases.add("Scan again before stairs or any crossing.");
        } else {
            phrases.add("Point the camera at the outdoor campus walkway or stairs.");
            phrases.add("Try facing the " + hint + ", then scan again.");
        }

        String summary = hazard ? "Campus pathway hazard" : "Campus pathway guidance";
        return new Result(String.join(" ", phrases), summary, hazard);
    }

    /** Fallback when location is unknown. */
    public static Result build(List<String> labels) {
        return build(
                labels,
                "on MMU Cyberjaya campus",
                "outdoor campus walkway or stairs"
        );
    }

    private static boolean isIndoorOnly(String lower) {
        return containsAny(List.of(lower),
                "room", "ceiling", "furniture", "sofa", "bed", "desk", "carpet",
                "kitchen", "bedroom", "living room", "couch", "pillow");
    }

    private static boolean isCampusPathwayRelevant(String lower) {
        return containsAny(List.of(lower),
                "stairs", "staircase", "step", "ramp", "sidewalk", "pavement", "walkway",
                "path", "pathway", "plaza", "courtyard", "building", "university", "campus",
                "tree", "grass", "sky", "person", "pedestrian", "pole", "pillar", "fence",
                "cone", "barrier", "bicycle", "bike", "scooter", "car", "sign", "lamp",
                "outdoor", "concrete", "asphalt", "hall", "library");
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

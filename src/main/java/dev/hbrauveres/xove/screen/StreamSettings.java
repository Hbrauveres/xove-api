package dev.hbrauveres.xove.screen;

import java.util.Set;

/**
 * What the sharer's stream is sent with (spec 0086): the best quality viewers can
 * get, and whether motion or detail comes first. The sharer can change it while
 * sharing; viewers' apps read it to offer the right qualities.
 *
 * @param quality "1080p", "720p" or "480p"
 * @param mode    "smooth" (motion first) or "sharp" (detail first)
 */
public record StreamSettings(String quality, String mode) {

    public static final StreamSettings DEFAULT = new StreamSettings("1080p", "smooth");

    private static final Set<String> QUALITIES = Set.of("1080p", "720p", "480p");
    private static final Set<String> MODES = Set.of("smooth", "sharp");

    static final String INVALID = "Pick a quality of 1080p, 720p or 480p, and a mode of smooth or sharp.";

    boolean isValid() {
        return quality != null && QUALITIES.contains(quality) && mode != null && MODES.contains(mode);
    }
}

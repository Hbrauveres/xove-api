package dev.hbrauveres.xove.stream;

import java.util.Set;

/**
 * What a stream is sent with (spec 0086): the best quality viewers can get, and
 * whether motion or detail comes first. Its person can change it while sharing;
 * viewers' apps read it to offer the right qualities.
 *
 * @param quality "1080p", "720p" or "480p"; a camera goes up to 720p (spec 0060)
 * @param mode    "smooth" (motion first) or "sharp" (detail first)
 */
public record StreamSettings(String quality, String mode) {

    private static final Set<String> SCREEN_QUALITIES = Set.of("1080p", "720p", "480p");
    private static final Set<String> CAMERA_QUALITIES = Set.of("720p", "480p");
    private static final Set<String> MODES = Set.of("smooth", "sharp");

    /** A screen starts at 1080p Smooth, a camera at 720p Smooth. */
    public static StreamSettings defaultFor(StreamKind kind) {
        return new StreamSettings(kind == StreamKind.CAMERA ? "720p" : "1080p", "smooth");
    }

    public static String invalidFor(StreamKind kind) {
        return kind == StreamKind.CAMERA
                ? "Pick a quality of 720p or 480p, and a mode of smooth or sharp."
                : "Pick a quality of 1080p, 720p or 480p, and a mode of smooth or sharp.";
    }

    boolean isValidFor(StreamKind kind) {
        Set<String> qualities = kind == StreamKind.CAMERA ? CAMERA_QUALITIES : SCREEN_QUALITIES;
        return quality != null && qualities.contains(quality) && mode != null && MODES.contains(mode);
    }
}

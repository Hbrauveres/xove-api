package dev.hbrauveres.xove.stream;

import java.util.Optional;

/**
 * The body of "start a stream": what it shows ("screen" or "camera"), where it comes
 * from (required), and what it's sent with (optional: a missing value takes the kind's
 * default, 1080p Smooth for a screen, 720p Smooth for a camera).
 */
record StartRequest(String kind, String participantSid, String trackSid, String quality, String mode) {

    Optional<StreamKind> streamKind() {
        return StreamKind.of(kind);
    }

    SharingConnection connection() {
        return new SharingConnection(participantSid, trackSid);
    }

    StreamSettings settingsFor(StreamKind kind) {
        StreamSettings defaults = StreamSettings.defaultFor(kind);
        return new StreamSettings(quality == null ? defaults.quality() : quality,
                mode == null ? defaults.mode() : mode);
    }
}

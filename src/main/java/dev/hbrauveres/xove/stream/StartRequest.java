package dev.hbrauveres.xove.stream;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;

/**
 * The body of "start a stream": what it shows ("screen" or "camera"), where it comes
 * from (required), and what it's sent with (optional: a missing value takes the kind's
 * default, 1080p Smooth for a screen, 720p Smooth for a camera).
 */
record StartRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"screen", "camera"}) String kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The LiveKit connection it comes from") String participantSid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The LiveKit track it is") String trackSid,
        @Schema(allowableValues = {"1080p", "720p", "480p"},
                description = "Missing: 1080p for a screen, 720p for a camera; a camera goes up to 720p") String quality,
        @Schema(allowableValues = {"smooth", "sharp"}, description = "Missing: smooth") String mode) {

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

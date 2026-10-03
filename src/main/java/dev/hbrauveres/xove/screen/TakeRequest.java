package dev.hbrauveres.xove.screen;

/**
 * The body of "take the screen": where the share comes from (required), and what
 * it's sent with (optional: a missing quality is 1080p, a missing mode Smooth).
 */
record TakeRequest(String participantSid, String trackSid, String quality, String mode) {

    SharingConnection connection() {
        return new SharingConnection(participantSid, trackSid);
    }

    StreamSettings settings() {
        return new StreamSettings(quality == null ? StreamSettings.DEFAULT.quality() : quality,
                mode == null ? StreamSettings.DEFAULT.mode() : mode);
    }
}

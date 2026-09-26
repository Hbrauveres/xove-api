package dev.hbrauveres.xove.livekit;

/** Everything the browser needs to join the LiveKit room. */
public record LiveKitAccess(String url, String room, String identity, String token) {
}

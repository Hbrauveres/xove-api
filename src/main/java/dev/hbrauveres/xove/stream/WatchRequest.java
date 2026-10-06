package dev.hbrauveres.xove.stream;

/** What's on someone's stage (spec 0104): a person's "screen" or "camera", or both null for an empty stage. */
public record WatchRequest(Long sharerId, String kind) {
}

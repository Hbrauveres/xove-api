package dev.hbrauveres.xove.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/** What's on someone's stage (spec 0104): a person's "screen" or "camera", or both null for an empty stage. */
public record WatchRequest(
        @Schema(description = "Whose stream is on my stage; leave both out for an empty stage") Long sharerId,
        @Schema(allowableValues = {"screen", "camera"}) String kind) {
}

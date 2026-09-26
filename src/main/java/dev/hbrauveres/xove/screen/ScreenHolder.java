package dev.hbrauveres.xove.screen;

import java.time.Instant;

/** Who is sharing right now, and since when. */
public record ScreenHolder(Long userId, String name, String avatarUrl, Instant since) {
}
package dev.hbrauveres.xove.stream;

import java.time.Instant;

/** One live stream: whose it is, what it shows, where it comes from, how it's sent, since when. */
public record Stream(Long userId, String name, String avatarUrl, StreamKind kind,
                     SharingConnection connection, StreamSettings settings, Instant since) {

    Stream with(SharingConnection connection, StreamSettings settings) {
        return new Stream(userId, name, avatarUrl, kind, connection, settings, since);
    }

    boolean isOf(Long userId, StreamKind kind) {
        return this.userId.equals(userId) && this.kind == kind;
    }
}

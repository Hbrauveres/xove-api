package dev.kryora.live.access;

import java.time.Instant;

/** What the admin page shows for each pending request. */
public record AccessRequestView(Long id, String email, String name, String avatarUrl,
                                String message, Instant createdAt) {

    static AccessRequestView from(AccessRequest request) {
        var user = request.getUser();
        return new AccessRequestView(request.getId(), user.getEmail(), user.getName(),
                user.getAvatarUrl(), request.getMessage(), request.getCreatedAt());
    }
}

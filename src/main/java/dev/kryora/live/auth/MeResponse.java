package dev.kryora.live.auth;

import dev.kryora.live.user.User;
import dev.kryora.live.user.UserStatus;

public record MeResponse(String email, String name, String avatarUrl, UserStatus status, boolean admin) {

    static MeResponse from(User user, boolean admin) {
        return new MeResponse(user.getEmail(), user.getName(), user.getAvatarUrl(), user.getStatus(), admin);
    }
}

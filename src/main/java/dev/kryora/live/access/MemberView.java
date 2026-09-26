package dev.kryora.live.access;

import dev.kryora.live.user.User;

public record MemberView(Long id, String email, String name, String avatarUrl, boolean admin) {

    static MemberView from(User user, boolean admin) {
        return new MemberView(user.getId(), user.getEmail(), user.getName(), user.getAvatarUrl(), admin);
    }
}

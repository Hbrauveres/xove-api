package dev.kryora.live.user;

import dev.kryora.live.config.KryoraProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final KryoraProperties properties;

    public UserService(UserRepository users, KryoraProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    /**
     * Called after every successful Google login: creates the user the first time,
     * refreshes name and picture afterwards. Admins are always members.
     */
    @Transactional
    public User recordGoogleLogin(String googleSubject, String email, String name, String avatarUrl) {
        User user = users.findByGoogleSubject(googleSubject)
                .or(() -> users.findByEmail(User.normalizeEmail(email)))
                .orElseGet(() -> new User(email, name, avatarUrl));

        user.linkGoogleAccount(googleSubject);
        user.updateProfile(name, avatarUrl);
        if (properties.isAdmin(user.getEmail())) {
            user.changeStatus(UserStatus.MEMBER);
        }
        return users.save(user);
    }

    public boolean isAdmin(User user) {
        return properties.isAdmin(user.getEmail());
    }
}

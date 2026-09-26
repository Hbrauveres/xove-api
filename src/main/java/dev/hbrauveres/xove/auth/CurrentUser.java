package dev.hbrauveres.xove.auth;

import dev.hbrauveres.xove.user.User;
import dev.hbrauveres.xove.user.UserRepository;
import dev.hbrauveres.xove.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Turns the signed-in Google account into our User row. */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User from(OidcUser google) {
        if (google == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return users.findByGoogleSubject(google.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    /** Same as {@link #from}, but only members get through: everyone else gets 403. */
    public User member(OidcUser google) {
        User user = from(google);
        if (user.getStatus() != UserStatus.MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Members only");
        }
        return user;
    }
}

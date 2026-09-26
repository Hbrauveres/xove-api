package dev.hbrauveres.xove.auth;

import dev.hbrauveres.xove.user.User;
import dev.hbrauveres.xove.user.UserRepository;
import dev.hbrauveres.xove.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MeController {

    private final UserRepository users;
    private final UserService userService;

    public MeController(UserRepository users, UserService userService) {
        this.users = users;
        this.userService = userService;
    }

    /** Who is signed in. Signed-out requests never get here: security answers 401. */
    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal OidcUser google, CsrfToken csrf) {
        csrf.getToken(); // makes Spring send the XSRF-TOKEN cookie the frontend needs for POSTs

        User user = users.findByGoogleSubject(google.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return MeResponse.from(user, userService.isAdmin(user));
    }
}

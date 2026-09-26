package dev.kryora.live.auth;

import dev.kryora.live.user.User;
import dev.kryora.live.user.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final CurrentUser currentUser;
    private final UserService userService;

    public MeController(CurrentUser currentUser, UserService userService) {
        this.currentUser = currentUser;
        this.userService = userService;
    }

    /** Who is signed in. Signed-out requests never get here: security answers 401. */
    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal OidcUser google, CsrfToken csrf) {
        csrf.getToken(); // makes Spring send the XSRF-TOKEN cookie the frontend needs for POSTs

        User user = currentUser.from(google);
        return MeResponse.from(user, userService.isAdmin(user));
    }
}

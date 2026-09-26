package dev.kryora.live.auth;

import dev.kryora.live.user.User;
import dev.kryora.live.user.UserService;
import dev.kryora.live.user.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Runs right after Google confirms who the person is.
 * Saves or updates the user, then sends them to the room or to the request-access page.
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;

    public LoginSuccessHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser google = (OidcUser) authentication.getPrincipal();

        if (!Boolean.TRUE.equals(google.getEmailVerified())) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            response.sendRedirect("/?error=email-not-verified");
            return;
        }

        User user = userService.recordGoogleLogin(
                google.getSubject(), google.getEmail(), google.getFullName(), google.getPicture());

        response.sendRedirect(user.getStatus() == UserStatus.MEMBER ? "/room" : "/request-access");
    }
}

package dev.hbrauveres.xove.access;

import dev.hbrauveres.xove.auth.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccessRequestController {

    public record NewAccessRequest(String message) {}

    private final AccessService accessService;
    private final CurrentUser currentUser;

    public AccessRequestController(AccessService accessService, CurrentUser currentUser) {
        this.accessService = accessService;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/access-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public void requestAccess(@AuthenticationPrincipal OidcUser google,
                              @RequestBody(required = false) NewAccessRequest body) {
        accessService.requestAccess(currentUser.from(google), body == null ? null : body.message());
    }
}

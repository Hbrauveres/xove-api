package dev.hbrauveres.xove.admin;

import dev.hbrauveres.xove.access.AccessRequestView;
import dev.hbrauveres.xove.access.AccessService;
import dev.hbrauveres.xove.access.MemberView;
import dev.hbrauveres.xove.auth.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

/** Everything under /api/admin requires ROLE_ADMIN (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AccessService accessService;
    private final CurrentUser currentUser;

    public AdminController(AccessService accessService, CurrentUser currentUser) {
        this.accessService = accessService;
        this.currentUser = currentUser;
    }

    @GetMapping("/access-requests")
    public List<AccessRequestView> pendingRequests(@AuthenticationPrincipal OidcUser google) {
        return accessService.pendingRequests(currentUser.from(google));
    }

    @PostMapping("/access-requests/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable Long id, @AuthenticationPrincipal OidcUser google) {
        accessService.approve(id, currentUser.from(google));
    }

    @PostMapping("/access-requests/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable Long id, @AuthenticationPrincipal OidcUser google) {
        accessService.decline(id, currentUser.from(google));
    }

    @GetMapping("/members")
    public List<MemberView> members(@AuthenticationPrincipal OidcUser google) {
        return accessService.members(currentUser.from(google));
    }

    @DeleteMapping("/members/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long id, @AuthenticationPrincipal OidcUser google) {
        accessService.removeMember(id, currentUser.from(google));
    }
}

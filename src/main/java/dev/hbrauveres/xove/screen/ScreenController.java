package dev.hbrauveres.xove.screen;

import dev.hbrauveres.xove.auth.CurrentUser;
import dev.hbrauveres.xove.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Who is sharing, take the screen, stop sharing. Members only. */
@RestController
@RequestMapping("/api/screen")
public class ScreenController {

    private final ScreenSlot slot;
    private final CurrentUser currentUser;

    public ScreenController(ScreenSlot slot, CurrentUser currentUser) {
        this.slot = slot;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ScreenState current(@AuthenticationPrincipal OidcUser google) {
        User me = currentUser.member(google);
        return ScreenState.of(slot, me.getId());
    }

    /** The body says which LiveKit connection and screen track the share comes from; both are required. */
    @PostMapping("/take")
    public ScreenState take(@AuthenticationPrincipal OidcUser google,
                            @RequestBody(required = false) SharingConnection connection) {
        User me = currentUser.member(google);
        if (connection == null || !connection.isComplete()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Your browser didn't say which video connection is sharing. Reload the page and try again.");
        }
        slot.take(me.getId(), me.getName(), me.getAvatarUrl(), connection);
        return ScreenState.of(slot, me.getId());
    }

    @PostMapping("/release")
    public ScreenState release(@AuthenticationPrincipal OidcUser google) {
        User me = currentUser.member(google);
        slot.release(me.getId());
        return ScreenState.of(slot, me.getId());
    }

    @ExceptionHandler(NotTheHolderException.class)
    ProblemDetail notTheHolder(NotTheHolderException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}

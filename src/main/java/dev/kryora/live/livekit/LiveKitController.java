package dev.kryora.live.livekit;

import dev.kryora.live.auth.CurrentUser;
import dev.kryora.live.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Hands members a token to join the video room. */
@RestController
public class LiveKitController {

    private final LiveKitTokens tokens;
    private final LiveKitProperties properties;
    private final CurrentUser currentUser;

    public LiveKitController(LiveKitTokens tokens, LiveKitProperties properties, CurrentUser currentUser) {
        this.tokens = tokens;
        this.properties = properties;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/livekit/token")
    public LiveKitAccess token(@AuthenticationPrincipal OidcUser google) {
        User me = currentUser.member(google);
        if (!tokens.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Video isn't set up on this server yet.");
        }
        String identity = identityOf(me);
        String name = me.getName() != null ? me.getName() : me.getEmail();
        return new LiveKitAccess(properties.url(), properties.room(), identity, tokens.create(identity, name));
    }

    /** Stable per person, so a reconnect replaces the old connection instead of duplicating it. */
    static String identityOf(User user) {
        return "user-" + user.getId();
    }
}

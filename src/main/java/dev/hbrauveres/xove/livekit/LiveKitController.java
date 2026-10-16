package dev.hbrauveres.xove.livekit;

import static dev.hbrauveres.xove.config.OpenApiConfig.PROBLEM_JSON;
import static dev.hbrauveres.xove.config.OpenApiConfig.PROBLEM_REF;

import dev.hbrauveres.xove.auth.CurrentUser;
import dev.hbrauveres.xove.room.RoomSeats;
import dev.hbrauveres.xove.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Hands members with a seat a token to join the video room (spec 0060: the room holds only them). */
@RestController
public class LiveKitController {

    private final LiveKitTokens tokens;
    private final LiveKitProperties properties;
    private final CurrentUser currentUser;
    private final RoomSeats seats;

    public LiveKitController(LiveKitTokens tokens, LiveKitProperties properties, CurrentUser currentUser,
                             RoomSeats seats) {
        this.tokens = tokens;
        this.properties = properties;
        this.currentUser = currentUser;
        this.seats = seats;
    }

    @Operation(summary = "A token to join the video room", description = "Members with a seat.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "409", description = "No seat: wait for your turn", content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @ApiResponse(responseCode = "503", description = "Video isn't set up on this server", content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @PostMapping("/api/livekit/token")
    public LiveKitAccess token(@AuthenticationPrincipal OidcUser google) {
        User me = currentUser.member(google);
        if (!tokens.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Video isn't set up on this server yet.");
        }
        if (!seats.isSeated(me.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wait for your turn: the room is full.");
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

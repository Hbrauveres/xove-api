package dev.hbrauveres.xove.access;

import static dev.hbrauveres.xove.config.OpenApiConfig.PROBLEM_JSON;
import static dev.hbrauveres.xove.config.OpenApiConfig.PROBLEM_REF;

import dev.hbrauveres.xove.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    @Operation(summary = "Ask for access, with an optional message", description = "Signed in, not a member yet.")
    @ApiResponse(responseCode = "400", description = "A message over 500 characters", content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @ApiResponse(responseCode = "409", description = "Already a member, or a request is already waiting", content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_REF)))
    @PostMapping("/api/access-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public void requestAccess(@AuthenticationPrincipal OidcUser google,
                              @RequestBody(required = false) NewAccessRequest body) {
        accessService.requestAccess(currentUser.from(google), body == null ? null : body.message());
    }
}

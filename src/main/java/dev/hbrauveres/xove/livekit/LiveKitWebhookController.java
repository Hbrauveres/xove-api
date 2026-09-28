package dev.hbrauveres.xove.livekit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.hbrauveres.xove.screen.ScreenSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * LiveKit calls this when something happens in the room. When the connection
 * that is sharing leaves, or its screen track stops, the slot is freed, so
 * nobody is left looking at a dead stage.
 *
 * Only LiveKit can call it: every request must carry LiveKit's signature
 * (see {@link LiveKitWebhookVerifier}); no login, no CSRF token.
 */
@RestController
public class LiveKitWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookController.class);

    private final LiveKitWebhookVerifier verifier;
    private final ScreenSlot slot;
    private final JsonMapper json;
    private final LiveKitProperties properties;

    public LiveKitWebhookController(LiveKitWebhookVerifier verifier, ScreenSlot slot, JsonMapper json,
                                    LiveKitProperties properties) {
        this.verifier = verifier;
        this.slot = slot;
        this.json = json;
        this.properties = properties;
    }

    @PostMapping("/api/livekit/webhook")
    public ResponseEntity<Void> receive(@RequestHeader(name = "Authorization", required = false) String authorization,
                                        @RequestBody byte[] body) {
        if (!verifier.verify(authorization, body)) {
            log.warn("webhook refused: missing or invalid LiveKit signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        WebhookEvent event = json.readValue(body, WebhookEvent.class);
        log.debug("webhook accepted: {}", event.event());
        // Each environment has its own room (spec 0044); anything else isn't ours.
        if (event.room() == null || !properties.room().equals(event.room().name())) {
            log.debug("webhook ignored: room {} isn't {}", event.room() == null ? null : event.room().name(), properties.room());
            return ResponseEntity.ok().build();
        }
        Long userId = event.participant() == null ? null : userIdOf(event.participant().identity());
        if (event.event() != null && userId != null) {
            handle(event, userId);
        }
        return ResponseEntity.ok().build();
    }

    private void handle(WebhookEvent event, Long userId) {
        String participantSid = event.participant().sid();
        switch (event.event()) {
            case "participant_left" -> {
                if (slot.connectionLeft(userId, participantSid)) {
                    log.info("slot freed: user {} left ({})", userId, participantSid);
                }
            }
            case "track_unpublished" -> {
                Track track = event.track();
                if (track != null && "SCREEN_SHARE".equals(track.source())
                        && slot.screenUnpublished(userId, participantSid, track.sid())) {
                    log.info("slot freed: user {} stopped the screen track ({})", userId, track.sid());
                }
            }
            default -> {
                // Other room events don't concern the slot.
            }
        }
    }

    /** The reverse of {@link LiveKitController#identityOf}: "user-42" → 42; anything else → null. */
    static Long userIdOf(String identity) {
        if (identity == null || !identity.startsWith("user-")) {
            return null;
        }
        try {
            return Long.valueOf(identity.substring("user-".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The parts of LiveKit's WebhookEvent we use. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record WebhookEvent(String event, Room room, Participant participant, Track track) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Room(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Participant(String identity, String sid) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Track(String sid, String source) {
    }
}

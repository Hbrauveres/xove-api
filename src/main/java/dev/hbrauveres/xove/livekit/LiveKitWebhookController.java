package dev.hbrauveres.xove.livekit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.hbrauveres.xove.room.RoomSeats;
import dev.hbrauveres.xove.stream.Streams;
import dev.hbrauveres.xove.stream.Watching;
import java.util.Set;
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
 * LiveKit calls this when something happens in the room. When a person's
 * connection leaves, their streams end; when a screen or camera track stops,
 * that stream ends. Nobody is left looking at a frozen picture (specs 0038, 0060).
 * Joining and leaving also tell the room's seats who is really there: a seat is
 * kept 30 seconds after a closed tab, 60 seconds after a dropped connection.
 *
 * Only LiveKit can call it: every request must carry LiveKit's signature
 * (see {@link LiveKitWebhookVerifier}); no login, no CSRF token.
 */
@RestController
public class LiveKitWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookController.class);

    /** The LiveKit track sources that are streams: their sound goes with the screen. */
    private static final Set<String> STREAM_SOURCES = Set.of("SCREEN_SHARE", "CAMERA");

    /** LiveKit's reason when the browser left on purpose (the tab closed); anything else is a drop. */
    private static final String CLOSED_TAB = "CLIENT_INITIATED";

    private final LiveKitWebhookVerifier verifier;
    private final Streams streams;
    private final RoomSeats seats;
    private final Watching watching;
    private final JsonMapper json;
    private final LiveKitProperties properties;

    public LiveKitWebhookController(LiveKitWebhookVerifier verifier, Streams streams, RoomSeats seats,
                                    Watching watching, JsonMapper json, LiveKitProperties properties) {
        this.verifier = verifier;
        this.streams = streams;
        this.seats = seats;
        this.watching = watching;
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
            case "participant_joined" -> seats.joined(userId, participantSid);
            case "participant_left" -> {
                if (streams.connectionLeft(userId, participantSid)) {
                    log.info("streams ended: user {} left ({})", userId, participantSid);
                }
                String reason = event.participant().disconnectReason();
                log.info("user {} left ({}), reason {}", userId, participantSid, reason);
                seats.left(userId, participantSid, CLOSED_TAB.equals(reason));
                // A page that's still there reports it again on its next poll (spec 0104).
                watching.clear(userId);
            }
            case "track_unpublished" -> {
                Track track = event.track();
                if (track != null && STREAM_SOURCES.contains(track.source())
                        && streams.trackUnpublished(userId, track.sid())) {
                    log.info("stream ended: user {} stopped a {} track ({})", userId, track.source(), track.sid());
                }
            }
            default -> {
                // Other room events don't concern the streams.
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
    record Participant(String identity, String sid, String disconnectReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Track(String sid, String source) {
    }
}

package dev.hbrauveres.xove.stream;

import dev.hbrauveres.xove.auth.CurrentUser;
import dev.hbrauveres.xove.room.RoomSeats;
import dev.hbrauveres.xove.user.User;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The live streams: who shares what, start one, change how it's sent, stop it. Members only. */
@RestController
@RequestMapping("/api/streams")
public class StreamsController {

    static final String UNKNOWN_KIND = "A stream is a screen or a camera.";

    private final Streams streams;
    private final RoomSeats seats;
    private final Watching watching;
    private final CurrentUser currentUser;

    public StreamsController(Streams streams, RoomSeats seats, Watching watching, CurrentUser currentUser) {
        this.streams = streams;
        this.seats = seats;
        this.watching = watching;
        this.currentUser = currentUser;
    }

    @GetMapping
    public StreamsView current(@AuthenticationPrincipal OidcUser google) {
        User me = currentUser.member(google);
        return view(me);
    }

    /**
     * The body says what the stream shows, which LiveKit connection and track it comes
     * from (both required), and optionally its quality and mode.
     */
    @PostMapping
    public StreamsView start(@AuthenticationPrincipal OidcUser google,
                             @RequestBody(required = false) StartRequest request) {
        User me = currentUser.member(google);
        if (request == null || !request.connection().isComplete()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Your browser didn't say which video connection is sharing. Reload the page and try again.");
        }
        StreamKind kind = request.streamKind()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, UNKNOWN_KIND));
        StreamSettings settings = request.settingsFor(kind);
        if (!settings.isValidFor(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, StreamSettings.invalidFor(kind));
        }
        if (!seats.isSeated(me.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Enter the room first.");
        }
        streams.start(me.getId(), me.getName(), me.getAvatarUrl(), kind, request.connection(), settings);
        return view(me);
    }

    /** Its person changes what a stream is sent with; everyone sees it on their next poll. */
    @PostMapping("/{kind}/settings")
    public StreamsView settings(@AuthenticationPrincipal OidcUser google, @PathVariable String kind,
                                @RequestBody(required = false) StreamSettings settings) {
        User me = currentUser.member(google);
        StreamKind streamKind = kindOf(kind);
        if (settings == null || !settings.isValidFor(streamKind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, StreamSettings.invalidFor(streamKind));
        }
        streams.changeSettings(me.getId(), streamKind, settings);
        return view(me);
    }

    @PostMapping("/{kind}/stop")
    public StreamsView stop(@AuthenticationPrincipal OidcUser google, @PathVariable String kind) {
        User me = currentUser.member(google);
        streams.stop(me.getId(), kindOf(kind));
        return view(me);
    }

    /**
     * What's on my stage (spec 0104): a person's screen or camera, or nothing ({@code {}}) for
     * an empty stage. Everyone sees it in the streams; only a seated person's is listed.
     */
    @PutMapping("/watching")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void watching(@AuthenticationPrincipal OidcUser google, @RequestBody WatchRequest request) {
        User me = currentUser.member(google);
        if (request.sharerId() == null && request.kind() == null) {
            watching.clear(me.getId());
            return;
        }
        if (request.sharerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say whose stream you're watching.");
        }
        StreamKind kind = kindOf(request.kind());
        // Only a seated person's watch is kept: anyone else's would never be listed anyway.
        if (seats.isSeated(me.getId())) {
            watching.watch(me.getId(), request.sharerId(), kind);
        } else {
            watching.clear(me.getId());
        }
    }

    private StreamsView view(User me) {
        boolean seated = seats.isSeated(me.getId());
        // Who's here, and since when, only for someone in the room (spec 0158).
        return StreamsView.of(streams, me.getId(), seated, seats.occupancy(),
                watching.current(seats::isSeated, streams), seated ? seats.arrivals() : Map.of());
    }

    private static StreamKind kindOf(String kind) {
        return StreamKind.of(kind == null ? "" : kind)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, UNKNOWN_KIND));
    }

    /** {@code reason: "full"} tells a page registering its stream again to stop it, rather than retry. */
    @ExceptionHandler(StreamsFullException.class)
    ProblemDetail full(StreamsFullException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("reason", "full");
        return problem;
    }

    @ExceptionHandler(NotYourStreamException.class)
    ProblemDetail notYours(NotYourStreamException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}

package dev.hbrauveres.xove.room;

import dev.hbrauveres.xove.auth.CurrentUser;
import dev.hbrauveres.xove.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Getting a seat in the room, or waiting for one (spec 0060). Members only.
 * The waiting screen calls "enter" every couple of seconds: it's also how the API
 * knows they're still there.
 */
@RestController
@RequestMapping("/api/room")
public class RoomController {

    private final RoomSeats seats;
    private final CurrentUser currentUser;

    public RoomController(RoomSeats seats, CurrentUser currentUser) {
        this.seats = seats;
        this.currentUser = currentUser;
    }

    /**
     * The body is optional: a page already connected to LiveKit sends its
     * {@code participantSid}, which confirms its seat after an API restart.
     */
    @PostMapping("/enter")
    public SeatStatus enter(@AuthenticationPrincipal OidcUser google,
                            @RequestBody(required = false) EnterRequest request) {
        return seats.enter(currentUser.member(google).getId(), request == null ? null : request.participantSid());
    }

    record EnterRequest(String participantSid) {
    }

    /** "Enter room" on the popup: takes the seat held for you. */
    @PostMapping("/accept")
    public SeatStatus accept(@AuthenticationPrincipal OidcUser google) {
        return seats.accept(currentUser.member(google).getId());
    }

    /** "Cancel" on the popup, or leaving the queue. */
    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal OidcUser google) {
        seats.cancel(currentUser.member(google).getId());
    }

    /** The waiting page is closing: the place is kept for 30 seconds. */
    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal OidcUser google) {
        User me = currentUser.member(google);
        seats.leave(me.getId());
    }

    @ExceptionHandler(NoSeatOfferException.class)
    ProblemDetail noOffer(NoSeatOfferException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}

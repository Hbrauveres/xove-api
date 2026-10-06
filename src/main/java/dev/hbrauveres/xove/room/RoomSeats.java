package dev.hbrauveres.xove.room;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The room's seats and the queue behind them (spec 0060). Plain Java: no Spring,
 * no HTTP, no database.
 *
 * Rules:
 * - a LiveKit token is only for someone with a seat, so the room never holds more
 *   than {@code capacity} people, sharers included;
 * - with a free seat and nobody waiting, you're in; otherwise you join the end of the queue;
 * - a seat whose person left is kept for them: 30 seconds after they closed the tab,
 *   60 seconds after their connection dropped; one never used, 60 seconds;
 * - a place in the queue is kept the same way: 30 seconds after "leave" (the tab
 *   closing), 90 seconds after the last poll (a tab in the background may poll only
 *   about once a minute in Chrome: 90 seconds leaves it a margin);
 * - a free seat is offered to the first in the queue, and held for them for 60 seconds,
 *   even while they're away; they accept or cancel; with no answer they go to the
 *   end of the queue and the seat is offered to the next.
 *
 * Nothing runs by itself: whatever has run out is tidied away each time someone asks,
 * by the clock. State lives in memory; after an API restart, the pages in the room
 * enter again within a poll.
 */
public class RoomSeats {

    static final Duration CLOSED_TAB = Duration.ofSeconds(30);
    static final Duration DROPPED = Duration.ofSeconds(60);
    /** A waiting page that stopped polling: Chrome lets a hidden tab poll about once a minute. */
    static final Duration QUEUE_SILENCE = Duration.ofSeconds(90);
    static final Duration NEVER_USED = Duration.ofSeconds(60);
    static final Duration OFFER = Duration.ofSeconds(60);

    private final int capacity;
    private final Clock clock;
    /** By user id. */
    private final Map<Long, Seat> seats = new HashMap<>();
    /** In order: the first is next. */
    private final List<Waiting> queue = new ArrayList<>();

    public RoomSeats(int capacity, Clock clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("The room needs at least one seat");
        }
        this.capacity = capacity;
        this.clock = clock;
    }

    /** Asks to be in the room, or polls the queue. Also brings back someone away, in time. */
    public synchronized SeatStatus enter(Long userId) {
        return enter(userId, null);
    }

    /**
     * Same, from a page already connected to LiveKit on {@code participantSid}: after an API
     * restart, LiveKit doesn't report that connection joining again, so the page says so.
     */
    public synchronized SeatStatus enter(Long userId, String participantSid) {
        Objects.requireNonNull(userId, "userId");
        Instant now = tidy();
        Seat seat = seats.get(userId);
        boolean connected = participantSid != null && !participantSid.isBlank();
        if (seat == null && connected && queue.isEmpty() && free() > 0) {
            seat = Seat.reserved(now);
            seats.put(userId, seat);
        }
        if (seat != null && connected) {
            seat.participantSid = participantSid;
            seat.until = null;
            return SeatStatus.in();
        }
        if (seat != null) {
            if (seat.participantSid == null || seat.until != null) {
                // Back from away, or still on the way in: they have a minute to join LiveKit.
                seat.participantSid = null;
                seat.until = now.plus(NEVER_USED);
            }
            return SeatStatus.in();
        }
        Waiting waiting = find(userId);
        if (waiting == null) {
            if (queue.isEmpty() && free() > 0) {
                seats.put(userId, Seat.reserved(now));
                return SeatStatus.in();
            }
            waiting = new Waiting(userId);
            queue.add(waiting);
            makeOffers(now);
        }
        waiting.lastSeen = now;
        waiting.awayUntil = null;
        return statusOf(waiting);
    }

    /** Takes the seat offered to this person. */
    public synchronized SeatStatus accept(Long userId) {
        Instant now = tidy();
        Waiting waiting = find(userId);
        if (waiting == null || waiting.offerUntil == null) {
            throw new NoSeatOfferException();
        }
        queue.remove(waiting);
        seats.put(userId, Seat.reserved(now));
        return SeatStatus.in();
    }

    /** Leaves the queue, or turns down the offer. Not being in the queue is fine. */
    public synchronized void cancel(Long userId) {
        Instant now = tidy();
        Waiting waiting = find(userId);
        if (waiting != null) {
            queue.remove(waiting);
            makeOffers(now);
        }
    }

    /** The waiting page is closing: the place is kept for 30 seconds. */
    public synchronized void leave(Long userId) {
        Instant now = tidy();
        Waiting waiting = find(userId);
        if (waiting != null) {
            waiting.awayUntil = now.plus(CLOSED_TAB);
        }
    }

    /** LiveKit says this person joined the room on this connection. */
    public synchronized void joined(Long userId, String participantSid) {
        tidy();
        Seat seat = seats.get(userId);
        if (seat != null) {
            seat.participantSid = participantSid;
            seat.until = null;
        }
    }

    /**
     * LiveKit says this person's connection left the room.
     *
     * @param closedTab true when they left on purpose (closed the tab); false when the connection dropped
     */
    public synchronized void left(Long userId, String participantSid, boolean closedTab) {
        Instant now = tidy();
        Seat seat = seats.get(userId);
        if (seat != null && participantSid != null && participantSid.equals(seat.participantSid)) {
            seat.until = now.plus(closedTab ? CLOSED_TAB : DROPPED);
        }
    }

    /** Has a seat, whether in the room, on the way in, or away for a moment. */
    public synchronized boolean isSeated(Long userId) {
        tidy();
        return seats.containsKey(userId);
    }

    /**
     * When this person's seat will be given up, if they're away or haven't joined LiveKit yet;
     * empty while they're connected, or without a seat.
     */
    public synchronized Optional<Instant> keptUntil(Long userId) {
        tidy();
        Seat seat = seats.get(userId);
        return seat == null ? Optional.empty() : Optional.ofNullable(seat.until);
    }

    /** How full the room is, for the people panel (spec 0104). */
    public synchronized Occupancy occupancy() {
        tidy();
        return new Occupancy(capacity, seats.size(), queue.size());
    }

    private int free() {
        long offers = queue.stream().filter(w -> w.offerUntil != null).count();
        return (int) (capacity - seats.size() - offers);
    }

    /** Drops what ran out, moves the unanswered offers to the end, and offers the free seats. */
    private Instant tidy() {
        Instant now = clock.instant();
        seats.values().removeIf(seat -> seat.until != null && !now.isBefore(seat.until));
        queue.removeIf(w -> (w.awayUntil != null && !now.isBefore(w.awayUntil))
                || !now.isBefore(w.lastSeen.plus(QUEUE_SILENCE)));
        List<Waiting> unanswered = queue.stream()
                .filter(w -> w.offerUntil != null && !now.isBefore(w.offerUntil))
                .toList();
        for (Waiting w : unanswered) {
            queue.remove(w);
            w.offerUntil = null;
            queue.add(w);
        }
        makeOffers(now);
        return now;
    }

    private void makeOffers(Instant now) {
        int free = free();
        for (Waiting w : queue) {
            if (free <= 0) {
                return;
            }
            if (w.offerUntil == null) {
                w.offerUntil = now.plus(OFFER);
                free--;
            }
        }
    }

    private SeatStatus statusOf(Waiting waiting) {
        return waiting.offerUntil != null
                ? SeatStatus.offered(waiting.offerUntil, clock.instant())
                : SeatStatus.waiting(queue.indexOf(waiting) + 1);
    }

    private Waiting find(Long userId) {
        return queue.stream().filter(w -> w.userId.equals(userId)).findFirst().orElse(null);
    }

    /** Empties the room and the queue. Only for tests, which share one across a Spring context. */
    public synchronized void clear() {
        seats.clear();
        queue.clear();
    }

    /** A seat: connected to LiveKit ({@code participantSid}, no deadline), or kept until {@code until}. */
    private static final class Seat {
        String participantSid;
        Instant until;

        static Seat reserved(Instant now) {
            Seat seat = new Seat();
            seat.until = now.plus(NEVER_USED);
            return seat;
        }
    }

    private static final class Waiting {
        final Long userId;
        Instant lastSeen;
        Instant awayUntil;
        Instant offerUntil;

        Waiting(Long userId) {
            this.userId = userId;
        }
    }
}

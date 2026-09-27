package dev.hbrauveres.xove.screen;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * The single screen slot. Plain Java: no Spring, no HTTP, no database.
 *
 * Rules:
 * - anyone can take the slot, even if someone else holds it (takeover);
 * - taking it again while already holding it changes nothing;
 * - only the holder can release it; releasing an empty slot is a no-op;
 * - taking it needs the LiveKit connection and screen track it's shared from;
 * - LiveKit reports free it only when they're about that connection or track.
 *
 * State lives in memory, so an API restart empties the slot. That's fine
 * until LiveKit becomes the source of truth.
 */
public class ScreenSlot {

    private final Clock clock;
    private ScreenHolder holder;
    private SharingConnection connection;

    public ScreenSlot(Clock clock) {
        this.clock = clock;
    }

    public synchronized Optional<ScreenHolder> current() {
        return Optional.ofNullable(holder);
    }

    /**
     * @param connection where the share comes from; both ids are required
     * @throws IllegalArgumentException when the connection or one of its ids is missing
     */
    public synchronized ScreenHolder take(Long userId, String name, String avatarUrl, SharingConnection connection) {
        Objects.requireNonNull(userId, "userId");
        if (connection == null || !connection.isComplete()) {
            throw new IllegalArgumentException("The sharing connection and track are required");
        }
        this.connection = connection;
        if (holder != null && holder.userId().equals(userId)) {
            return holder;
        }
        holder = new ScreenHolder(userId, name, avatarUrl, clock.instant());
        return holder;
    }

    public synchronized void release(Long userId) {
        if (holder == null) {
            return;
        }
        if (!holder.userId().equals(userId)) {
            throw new NotTheHolderException();
        }
        empty();
    }

    /**
     * LiveKit says one of this person's connections left the room.
     *
     * @return true if that freed the slot
     */
    public synchronized boolean connectionLeft(Long userId, String participantSid) {
        if (!isHolder(userId)) {
            return false;
        }
        if (!connection.participantSid().equals(participantSid)) {
            return false;
        }
        empty();
        return true;
    }

    /**
     * LiveKit says this person stopped publishing a screen share track.
     *
     * @return true if that freed the slot
     */
    public synchronized boolean screenUnpublished(Long userId, String participantSid, String trackSid) {
        if (!isHolder(userId)) {
            return false;
        }
        if (!connection.trackSid().equals(trackSid)) {
            return false;
        }
        empty();
        return true;
    }

    private boolean isHolder(Long userId) {
        return holder != null && holder.userId().equals(userId);
    }

    private void empty() {
        holder = null;
        connection = null;
    }

    /** Empties the slot. Only for tests, which share one slot across a Spring context. */
    synchronized void clear() {
        empty();
    }
}

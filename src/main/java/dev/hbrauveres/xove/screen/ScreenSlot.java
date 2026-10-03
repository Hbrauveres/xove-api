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
 * - the holder can change what the stream is sent with (spec 0086); it goes with the share;
 * - LiveKit reports free it only when they're about that connection or track.
 *
 * State lives in memory, so an API restart empties the slot. That's fine
 * until LiveKit becomes the source of truth.
 */
public class ScreenSlot {

    private final Clock clock;
    private ScreenHolder holder;
    private SharingConnection connection;
    private StreamSettings settings;

    public ScreenSlot(Clock clock) {
        this.clock = clock;
    }

    public synchronized Optional<ScreenHolder> current() {
        return Optional.ofNullable(holder);
    }

    /** What the current share is sent with; empty when nobody is sharing. */
    public synchronized Optional<StreamSettings> settings() {
        return Optional.ofNullable(settings);
    }

    /** Takes the slot with the default settings (1080p, Smooth). */
    public ScreenHolder take(Long userId, String name, String avatarUrl, SharingConnection connection) {
        return take(userId, name, avatarUrl, connection, StreamSettings.DEFAULT);
    }

    /**
     * @param connection where the share comes from; both ids are required
     * @throws IllegalArgumentException when the connection or one of its ids is missing
     */
    public synchronized ScreenHolder take(Long userId, String name, String avatarUrl, SharingConnection connection,
                                          StreamSettings settings) {
        Objects.requireNonNull(userId, "userId");
        if (connection == null || !connection.isComplete()) {
            throw new IllegalArgumentException("The sharing connection and track are required");
        }
        if (settings == null || !settings.isValid()) {
            throw new IllegalArgumentException(StreamSettings.INVALID);
        }
        this.connection = connection;
        this.settings = settings;
        if (holder != null && holder.userId().equals(userId)) {
            return holder;
        }
        holder = new ScreenHolder(userId, name, avatarUrl, clock.instant());
        return holder;
    }

    /** The holder sends their stream with new settings. */
    public synchronized void changeSettings(Long userId, StreamSettings settings) {
        if (!isHolder(userId)) {
            throw new NotTheHolderException("Only the person sharing can change how their screen is sent.");
        }
        if (settings == null || !settings.isValid()) {
            throw new IllegalArgumentException(StreamSettings.INVALID);
        }
        this.settings = settings;
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
        settings = null;
    }

    /** Empties the slot. Only for tests, which share one slot across a Spring context. */
    synchronized void clear() {
        empty();
    }
}

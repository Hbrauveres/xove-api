package dev.hbrauveres.xove.stream;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The live streams of the room (spec 0060). Plain Java: no Spring, no HTTP, no database.
 *
 * Rules:
 * - up to {@link #MAX} streams at once; each is one person's screen or camera;
 * - a person has at most one of each; starting the same kind again replaces their own
 *   (a reconnect, or registering again after an API restart) and keeps its start time;
 * - nobody is pushed out: when the places are full, starting is refused;
 * - only its person can change or stop a stream; stopping one you don't have is a no-op;
 * - LiveKit reports end a stream when they're about its track, or all of a person's
 *   streams when they're about its connection (spec 0038).
 *
 * State lives in memory, so an API restart empties it; browsers still sending
 * register their streams again.
 */
public class Streams {

    public static final int MAX = 6;

    private final Clock clock;
    private final List<Stream> streams = new ArrayList<>();

    public Streams(Clock clock) {
        this.clock = clock;
    }

    /** Every live stream, oldest first. */
    public synchronized List<Stream> all() {
        return streams.stream().sorted(Comparator.comparing(Stream::since)).toList();
    }

    public synchronized int free() {
        return MAX - streams.size();
    }

    /**
     * @throws IllegalArgumentException when the connection is incomplete or the settings don't fit the kind
     * @throws StreamsFullException     when all the places are taken by others
     */
    public synchronized Stream start(Long userId, String name, String avatarUrl, StreamKind kind,
                                     SharingConnection connection, StreamSettings settings) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(kind, "kind");
        if (connection == null || !connection.isComplete()) {
            throw new IllegalArgumentException("The stream's connection and track are required");
        }
        if (settings == null || !settings.isValidFor(kind)) {
            throw new IllegalArgumentException(StreamSettings.invalidFor(kind));
        }
        int mine = indexOf(userId, kind);
        if (mine >= 0) {
            Stream again = streams.get(mine).with(connection, settings);
            streams.set(mine, again);
            return again;
        }
        if (streams.size() >= MAX) {
            throw new StreamsFullException();
        }
        Stream stream = new Stream(userId, name, avatarUrl, kind, connection, settings, clock.instant());
        streams.add(stream);
        return stream;
    }

    /** Its person sends the stream with new settings. */
    public synchronized void changeSettings(Long userId, StreamKind kind, StreamSettings settings) {
        int mine = indexOf(userId, kind);
        if (mine < 0) {
            throw new NotYourStreamException();
        }
        if (settings == null || !settings.isValidFor(kind)) {
            throw new IllegalArgumentException(StreamSettings.invalidFor(kind));
        }
        Stream stream = streams.get(mine);
        streams.set(mine, stream.with(stream.connection(), settings));
    }

    public synchronized void stop(Long userId, StreamKind kind) {
        streams.removeIf(s -> s.isOf(userId, kind));
    }

    /**
     * LiveKit says one of this person's connections left the room.
     *
     * @return true if that ended any stream
     */
    public synchronized boolean connectionLeft(Long userId, String participantSid) {
        return streams.removeIf(s -> s.userId().equals(userId)
                && s.connection().participantSid().equals(participantSid));
    }

    /**
     * LiveKit says this person stopped publishing a track.
     *
     * @return true if that ended a stream
     */
    public synchronized boolean trackUnpublished(Long userId, String trackSid) {
        return streams.removeIf(s -> s.userId().equals(userId) && s.connection().trackSid().equals(trackSid));
    }

    private int indexOf(Long userId, StreamKind kind) {
        for (int i = 0; i < streams.size(); i++) {
            if (streams.get(i).isOf(userId, kind)) {
                return i;
            }
        }
        return -1;
    }

    /** Ends every stream. Only for tests, which share one list across a Spring context. */
    synchronized void clear() {
        streams.clear();
    }
}

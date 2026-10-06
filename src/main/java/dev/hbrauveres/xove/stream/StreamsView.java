package dev.hbrauveres.xove.stream;

import dev.hbrauveres.xove.room.Occupancy;
import java.time.Instant;
import java.util.List;

/**
 * What the frontend gets: every live stream, oldest first, how many places are free,
 * and whether the asking person has a seat in the room (after an API restart, their
 * page enters again). Also how full the room is and who watches which stream (spec 0104).
 * LiveKit connection ids stay in the API.
 */
public record StreamsView(List<Item> streams, int free, boolean seated, Seats seats, List<Watcher> watching) {

    /** One live stream; {@code mine} when it's the asking person's. */
    public record Item(String kind, Long userId, String name, String avatarUrl, Instant since,
                       StreamSettings settings, boolean mine) {
    }

    /** The room's seats: in total, taken (kept ones too), and how many people wait. */
    public record Seats(int total, int taken, int waiting) {
    }

    /** {@code userId} has {@code sharerId}'s {@code kind} on their stage; {@code mine} when it's the asking person. */
    public record Watcher(Long userId, Long sharerId, String kind, boolean mine) {
    }

    static StreamsView of(Streams streams, Long viewerId, boolean seated, Occupancy occupancy,
                          List<Watching.Watch> watches) {
        List<Item> items = streams.all().stream()
                .map(s -> new Item(s.kind().id(), s.userId(), s.name(), s.avatarUrl(), s.since(), s.settings(),
                        s.userId().equals(viewerId)))
                .toList();
        List<Watcher> watching = watches.stream()
                .map(w -> new Watcher(w.userId(), w.sharerId(), w.kind().id(), w.userId().equals(viewerId)))
                .toList();
        return new StreamsView(items, streams.free(), seated,
                new Seats(occupancy.total(), occupancy.taken(), occupancy.waiting()), watching);
    }
}

package dev.hbrauveres.xove.stream;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Who has which stream on their stage (spec 0104), so the people panel can say "Watching
 * Ana". Kept in memory like the streams: after a restart, each page reports it again.
 * Nothing runs on a timer: what's out of date is left out when it's read.
 */
public class Watching {

    /** {@code userId} has {@code sharerId}'s {@code kind} on their stage. */
    public record Watch(Long userId, Long sharerId, StreamKind kind) {
    }

    /** By the watching person's user id. */
    private final Map<Long, Watch> watches = new HashMap<>();

    public synchronized void watch(Long userId, Long sharerId, StreamKind kind) {
        watches.put(userId, new Watch(userId, sharerId, kind));
    }

    /** An empty stage, or someone who left. */
    public synchronized void clear(Long userId) {
        watches.remove(userId);
    }

    /** What seated people watch, of the streams still live; by user id. */
    public synchronized List<Watch> current(Predicate<Long> seated, Streams streams) {
        List<Stream> live = streams.all();
        return watches.values().stream()
                .filter(w -> seated.test(w.userId()))
                .filter(w -> live.stream().anyMatch(s -> s.userId().equals(w.sharerId()) && s.kind() == w.kind()))
                .sorted(Comparator.comparing(Watch::userId))
                .toList();
    }

    /** Forgets everyone. Only for tests, which share one across a Spring context. */
    public synchronized void reset() {
        watches.clear();
    }
}

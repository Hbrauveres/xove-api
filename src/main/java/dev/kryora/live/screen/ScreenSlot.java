package dev.kryora.live.screen;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * The single screen slot. Plain Java: no Spring, no HTTP, no database.
 *
 * Rules:
 * - anyone can take the slot, even if someone else holds it (takeover);
 * - taking it again while already holding it changes nothing;
 * - only the holder can release it; releasing an empty slot is a no-op.
 *
 * State lives in memory, so an API restart empties the slot. That's fine
 * until LiveKit becomes the source of truth.
 */
public class ScreenSlot {

    private final Clock clock;
    private ScreenHolder holder;

    public ScreenSlot(Clock clock) {
        this.clock = clock;
    }

    public synchronized Optional<ScreenHolder> current() {
        return Optional.ofNullable(holder);
    }

    public synchronized ScreenHolder take(Long userId, String name, String avatarUrl) {
        Objects.requireNonNull(userId, "userId");
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
        holder = null;
    }

    /** Empties the slot. Only for tests, which share one slot across a Spring context. */
    synchronized void clear() {
        holder = null;
    }
}

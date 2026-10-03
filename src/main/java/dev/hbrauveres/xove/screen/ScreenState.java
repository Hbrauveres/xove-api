package dev.hbrauveres.xove.screen;

/**
 * What the frontend gets: who is sharing (null when nobody is), whether that's
 * the person asking, and what the stream is sent with (null when nobody shares).
 */
public record ScreenState(ScreenHolder holder, boolean mine, StreamSettings settings) {

    static ScreenState of(ScreenSlot slot, Long viewerId) {
        ScreenHolder holder = slot.current().orElse(null);
        return new ScreenState(holder, holder != null && holder.userId().equals(viewerId),
                holder == null ? null : slot.settings().orElse(null));
    }
}

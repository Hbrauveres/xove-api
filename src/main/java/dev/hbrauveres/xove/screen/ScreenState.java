package dev.hbrauveres.xove.screen;

/**
 * What the frontend gets: who is sharing (null when nobody is),
 * and whether that's the person asking.
 */
public record ScreenState(ScreenHolder holder, boolean mine) {

    static ScreenState of(ScreenSlot slot, Long viewerId) {
        ScreenHolder holder = slot.current().orElse(null);
        return new ScreenState(holder, holder != null && holder.userId().equals(viewerId));
    }
}

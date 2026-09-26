package dev.hbrauveres.xove.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScreenSlotTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;

    private MutableClock clock;
    private ScreenSlot slot;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-26T20:00:00Z"));
        slot = new ScreenSlot(clock);
    }

    @Test
    void startsEmpty() {
        assertThat(slot.current()).isEmpty();
    }

    @Test
    void takingAnEmptySlotMakesYouTheHolder() {
        slot.take(ANA, "Ana", "https://img/ana");

        ScreenHolder holder = slot.current().orElseThrow();
        assertThat(holder.userId()).isEqualTo(ANA);
        assertThat(holder.name()).isEqualTo("Ana");
        assertThat(holder.since()).isEqualTo(clock.instant());
    }

    @Test
    void someoneElseCanTakeOver() {
        slot.take(ANA, "Ana", null);
        clock.advance(Duration.ofMinutes(5));

        slot.take(BRUNO, "Bruno", null);

        ScreenHolder holder = slot.current().orElseThrow();
        assertThat(holder.userId()).isEqualTo(BRUNO);
        assertThat(holder.since()).isEqualTo(clock.instant());
    }

    @Test
    void takingAgainWhileHoldingKeepsTheOriginalStartTime() {
        ScreenHolder first = slot.take(ANA, "Ana", null);
        clock.advance(Duration.ofMinutes(5));

        slot.take(ANA, "Ana", null);

        assertThat(slot.current().orElseThrow().since()).isEqualTo(first.since());
    }

    @Test
    void theHolderCanRelease() {
        slot.take(ANA, "Ana", null);

        slot.release(ANA);

        assertThat(slot.current()).isEmpty();
    }

    @Test
    void someoneElseCannotRelease() {
        slot.take(ANA, "Ana", null);

        assertThatThrownBy(() -> slot.release(BRUNO)).isInstanceOf(NotTheHolderException.class);
        assertThat(slot.current().orElseThrow().userId()).isEqualTo(ANA);
    }

    @Test
    void releasingAnEmptySlotDoesNothing() {
        slot.release(ANA);

        assertThat(slot.current()).isEmpty();
    }

    /** A clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
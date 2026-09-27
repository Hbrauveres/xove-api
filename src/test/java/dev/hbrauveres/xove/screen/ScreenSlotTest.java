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
    private static final SharingConnection ANA_LAPTOP = new SharingConnection("PA_laptop", "TR_screen1");
    private static final SharingConnection BRUNO_LAPTOP = new SharingConnection("PA_bruno", "TR_bruno1");

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
        slot.take(ANA, "Ana", "https://img/ana", ANA_LAPTOP);

        ScreenHolder holder = slot.current().orElseThrow();
        assertThat(holder.userId()).isEqualTo(ANA);
        assertThat(holder.name()).isEqualTo("Ana");
        assertThat(holder.since()).isEqualTo(clock.instant());
    }

    @Test
    void someoneElseCanTakeOver() {
        slot.take(ANA, "Ana", null, ANA_LAPTOP);
        clock.advance(Duration.ofMinutes(5));

        slot.take(BRUNO, "Bruno", null, BRUNO_LAPTOP);

        ScreenHolder holder = slot.current().orElseThrow();
        assertThat(holder.userId()).isEqualTo(BRUNO);
        assertThat(holder.since()).isEqualTo(clock.instant());
    }

    @Test
    void takingAgainWhileHoldingKeepsTheOriginalStartTime() {
        ScreenHolder first = slot.take(ANA, "Ana", null, ANA_LAPTOP);
        clock.advance(Duration.ofMinutes(5));

        slot.take(ANA, "Ana", null, ANA_LAPTOP);

        assertThat(slot.current().orElseThrow().since()).isEqualTo(first.since());
    }

    @Test
    void theHolderCanRelease() {
        slot.take(ANA, "Ana", null, ANA_LAPTOP);

        slot.release(ANA);

        assertThat(slot.current()).isEmpty();
    }

    @Test
    void someoneElseCannotRelease() {
        slot.take(ANA, "Ana", null, ANA_LAPTOP);

        assertThatThrownBy(() -> slot.release(BRUNO)).isInstanceOf(NotTheHolderException.class);
        assertThat(slot.current().orElseThrow().userId()).isEqualTo(ANA);
    }

    @Test
    void releasingAnEmptySlotDoesNothing() {
        slot.release(ANA);

        assertThat(slot.current()).isEmpty();
    }

    // --- LiveKit reports (spec 0038) ---


    @Test
    void theSharingConnectionLeavingFreesTheSlot() {
        slot.take(ANA, "Ana", null, ANA_LAPTOP);

        assertThat(slot.connectionLeft(ANA, "PA_laptop")).isTrue();
        assertThat(slot.current()).isEmpty();
    }

    @Test
    void anotherConnectionOfTheHolderLeavingKeepsTheSlot() {
        slot.take(ANA, "Ana", null, ANA_LAPTOP);

        assertThat(slot.connectionLeft(ANA, "PA_phone")).isFalse();
        assertThat(slot.current().orElseThrow().userId()).isEqualTo(ANA);
    }

    @Test
    void someoneElseLeavingKeepsTheSlot() {
        slot.take(ANA, "Ana", null, ANA_LAPTOP);

        assertThat(slot.connectionLeft(BRUNO, "PA_laptop")).isFalse();
        assertThat(slot.screenUnpublished(BRUNO, "PA_bruno", "TR_screen1")).isFalse();
        assertThat(slot.current().orElseThrow().userId()).isEqualTo(ANA);
    }

    @Test
    void leavingWhenNobodySharesDoesNothing() {
        assertThat(slot.connectionLeft(ANA, "PA_laptop")).isFalse();
        assertThat(slot.screenUnpublished(ANA, "PA_laptop", "TR_screen1")).isFalse();
        assertThat(slot.current()).isEmpty();
    }

    @Test
    void theSharedTrackBeingUnpublishedFreesTheSlot() {
        slot.take(ANA, "Ana", null, ANA_LAPTOP);

        assertThat(slot.screenUnpublished(ANA, "PA_laptop", "TR_screen1")).isTrue();
        assertThat(slot.current()).isEmpty();
    }

    @Test
    void aLateUnpublishOfAnEarlierShareKeepsTheNewOne() {
        slot.take(ANA, "Ana", null, ANA_LAPTOP);
        slot.release(ANA);
        slot.take(ANA, "Ana", null, new SharingConnection("PA_laptop", "TR_screen2"));

        assertThat(slot.screenUnpublished(ANA, "PA_laptop", "TR_screen1")).isFalse();
        assertThat(slot.current().orElseThrow().userId()).isEqualTo(ANA);
    }

    @Test
    void takingAgainWhileHoldingUpdatesTheConnection() {
        slot.take(ANA, "Ana", null, ANA_LAPTOP);
        slot.take(ANA, "Ana", null, new SharingConnection("PA_laptop", "TR_screen2"));

        assertThat(slot.screenUnpublished(ANA, "PA_laptop", "TR_screen1")).isFalse();
        assertThat(slot.screenUnpublished(ANA, "PA_laptop", "TR_screen2")).isTrue();
    }

    @Test
    void takingWithoutSayingWhichConnectionIsRefused() {
        assertThatThrownBy(() -> slot.take(ANA, "Ana", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> slot.take(ANA, "Ana", null, new SharingConnection("PA_laptop", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> slot.take(ANA, "Ana", null, new SharingConnection(" ", "TR_screen1")))
                .isInstanceOf(IllegalArgumentException.class);
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
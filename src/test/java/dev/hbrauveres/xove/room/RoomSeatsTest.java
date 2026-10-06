package dev.hbrauveres.xove.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hbrauveres.xove.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomSeatsTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;
    private static final Long CAIO = 3L;
    private static final Long DORA = 4L;

    private MutableClock clock;
    /** Two seats, so the queue is easy to reach. */
    private RoomSeats seats;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-03T20:00:00Z"));
        seats = new RoomSeats(2, clock);
    }

    private void wait(int seconds) {
        clock.advance(Duration.ofSeconds(seconds));
    }

    /** Ana and Bruno in the room, connected to LiveKit. */
    private void fillTheRoom() {
        seats.enter(ANA);
        seats.joined(ANA, "PA_ana");
        seats.enter(BRUNO);
        seats.joined(BRUNO, "PA_bruno");
    }

    // ---- occupancy (spec 0104) ----

    @Test
    void tellsHowManySeatsAreTakenAndHowManyWait() {
        assertThat(seats.occupancy()).isEqualTo(new Occupancy(2, 0, 0));

        fillTheRoom();
        seats.enter(CAIO);
        seats.enter(DORA);

        assertThat(seats.occupancy()).isEqualTo(new Occupancy(2, 2, 2));
    }

    @Test
    void aKeptSeatStillCountsAsTaken() {
        fillTheRoom();
        seats.left(ANA, "PA_ana", true);

        assertThat(seats.occupancy().taken()).isEqualTo(2);
        wait(31);
        assertThat(seats.occupancy().taken()).isEqualTo(1);
    }

    // ---- seats ----

    @Test
    void withAFreeSeatYoureIn() {
        assertThat(seats.enter(ANA)).isEqualTo(SeatStatus.in());
        assertThat(seats.isSeated(ANA)).isTrue();
    }

    @Test
    void enteringAgainChangesNothing() {
        seats.enter(ANA);
        seats.joined(ANA, "PA_ana");

        assertThat(seats.enter(ANA)).isEqualTo(SeatStatus.in());
        assertThat(seats.enter(BRUNO)).isEqualTo(SeatStatus.in());
    }

    @Test
    void whenTheRoomIsFullYouWaitInOrderOfArrival() {
        fillTheRoom();

        assertThat(seats.enter(CAIO)).isEqualTo(SeatStatus.waiting(1));
        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.waiting(2));
        assertThat(seats.isSeated(CAIO)).isFalse();
    }

    @Test
    void aSeatNeverUsedIsGivenUpAfterOneMinute() {
        seats.enter(ANA);

        wait(59);
        assertThat(seats.isSeated(ANA)).isTrue();
        wait(1);
        assertThat(seats.isSeated(ANA)).isFalse();
    }

    @Test
    void theSettingMustBeAtLeastOneSeat() {
        assertThatThrownBy(() -> new RoomSeats(0, clock)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- leaving the room keeps the seat for a while ----

    @Test
    void closingTheTabKeepsTheSeatFor30Seconds() {
        fillTheRoom();
        seats.enter(CAIO);

        seats.left(ANA, "PA_ana", true);
        wait(29);
        seats.enter(CAIO);
        assertThat(seats.isSeated(ANA)).isTrue();
        assertThat(seats.enter(CAIO)).isEqualTo(SeatStatus.waiting(1));

        wait(1);
        assertThat(seats.isSeated(ANA)).isFalse();
    }

    @Test
    void aDroppedConnectionKeepsTheSeatFor60Seconds() {
        fillTheRoom();

        seats.left(ANA, "PA_ana", false);
        wait(59);
        assertThat(seats.isSeated(ANA)).isTrue();
        wait(1);
        assertThat(seats.isSeated(ANA)).isFalse();
    }

    @Test
    void comingBackInTimeKeepsTheSameSeatAndNobodyMoves() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.left(ANA, "PA_ana", false);
        wait(50);

        assertThat(seats.enter(ANA)).isEqualTo(SeatStatus.in());
        seats.joined(ANA, "PA_ana2");
        wait(120);

        assertThat(seats.isSeated(ANA)).isTrue();
        assertThat(seats.enter(CAIO)).isEqualTo(SeatStatus.waiting(1));
    }

    @Test
    void anOldConnectionLeavingDoesNotStartTheCountdown() {
        seats.enter(ANA);
        seats.joined(ANA, "PA_new_tab");

        seats.left(ANA, "PA_old_tab", true);
        wait(120);

        assertThat(seats.isSeated(ANA)).isTrue();
    }

    // After an API restart, LiveKit doesn't say "joined" again: the room's page says it's connected.
    @Test
    void enteringFromALiveConnectionConfirmsTheSeat() {
        assertThat(seats.enter(ANA, "PA_ana")).isEqualTo(SeatStatus.in());
        wait(120);
        assertThat(seats.isSeated(ANA)).isTrue();

        seats.left(ANA, "PA_ana", true);
        wait(30);
        assertThat(seats.isSeated(ANA)).isFalse();
    }

    @Test
    void enteringAgainFromALiveConnectionConfirmsAReservedSeat() {
        seats.enter(ANA);
        wait(50);

        seats.enter(ANA, "PA_ana");
        wait(120);

        assertThat(seats.isSeated(ANA)).isTrue();
        assertThat(seats.keptUntil(ANA)).isEmpty();
    }

    @Test
    void anOfferSaysHowManySecondsAreLeft() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.left(ANA, "PA_ana", true);
        wait(30);
        seats.enter(CAIO);

        wait(15);

        assertThat(seats.enter(CAIO).seconds()).isEqualTo(45);
    }

    @Test
    void joiningWithoutASeatGivesNone() {
        seats.joined(ANA, "PA_ana");

        assertThat(seats.isSeated(ANA)).isFalse();
    }

    // ---- the offer ----

    @Test
    void whenASeatFreesTheFirstInTheQueueGetsAnOfferFor60Seconds() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.enter(DORA);
        seats.left(ANA, "PA_ana", true);
        wait(30);

        assertThat(seats.enter(CAIO)).isEqualTo(SeatStatus.offered(clock.instant().plusSeconds(60), clock.instant()));
        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.waiting(2));
    }

    @Test
    void acceptingTheOfferTakesTheSeat() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.enter(DORA);
        seats.left(ANA, "PA_ana", true);
        wait(30);
        seats.enter(CAIO);

        assertThat(seats.accept(CAIO)).isEqualTo(SeatStatus.in());
        assertThat(seats.isSeated(CAIO)).isTrue();
        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.waiting(1));
    }

    @Test
    void acceptingWithoutAnOfferIsRefused() {
        fillTheRoom();
        seats.enter(CAIO);

        assertThatThrownBy(() -> seats.accept(CAIO)).isInstanceOf(NoSeatOfferException.class);
        assertThatThrownBy(() -> seats.accept(DORA)).isInstanceOf(NoSeatOfferException.class);
    }

    @Test
    void noAnswerIn60SecondsMovesYouToTheEndAndOffersTheNext() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.enter(DORA);
        seats.left(ANA, "PA_ana", true);
        wait(30);
        seats.enter(CAIO);

        wait(59);
        seats.enter(CAIO);
        seats.enter(DORA);
        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.waiting(2));
        wait(1);

        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.offered(clock.instant().plusSeconds(60), clock.instant()));
        assertThat(seats.enter(CAIO)).isEqualTo(SeatStatus.waiting(2));
        assertThatThrownBy(() -> seats.accept(CAIO)).isInstanceOf(NoSeatOfferException.class);
    }

    @Test
    void theOfferWaitsEvenWhenTheFirstIsAway() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.enter(DORA);
        // Caio's connection drops: he stops polling, and keeps his place for 90 seconds.
        seats.left(ANA, "PA_ana", true);
        wait(30);

        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.waiting(2));
        wait(30);
        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.waiting(2));

        wait(30);
        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.offered(clock.instant().plusSeconds(60), clock.instant()));
    }

    @Test
    void cancellingLeavesTheQueueAndTheNextGetsTheOffer() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.enter(DORA);
        seats.left(ANA, "PA_ana", true);
        wait(30);
        seats.enter(CAIO);

        seats.cancel(CAIO);

        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.offered(clock.instant().plusSeconds(60), clock.instant()));
        // Coming back after cancelling is a new arrival, behind Dora.
        assertThat(seats.enter(CAIO)).isEqualTo(SeatStatus.waiting(2));
    }

    @Test
    void twoFreeSeatsMakeTwoOffers() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.enter(DORA);
        seats.left(ANA, "PA_ana", true);
        seats.left(BRUNO, "PA_bruno", true);
        wait(30);

        assertThat(seats.enter(CAIO)).isEqualTo(SeatStatus.offered(clock.instant().plusSeconds(60), clock.instant()));
        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.offered(clock.instant().plusSeconds(60), clock.instant()));
    }

    @Test
    void anOfferedSeatIsntGivenToSomeoneNew() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.left(ANA, "PA_ana", true);
        wait(30);
        seats.enter(CAIO);

        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.waiting(2));
    }

    // ---- leaving the queue keeps the place for a while ----

    @Test
    void closingTheWaitingTabKeepsThePlaceFor30Seconds() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.enter(DORA);

        seats.leave(CAIO);
        wait(29);
        seats.enter(DORA);
        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.waiting(2));
        wait(1);

        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.waiting(1));
    }

    @Test
    void withoutPollsThePlaceIsKeptFor90Seconds() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.enter(DORA);

        for (int i = 0; i < 44; i++) {
            wait(2);
            seats.enter(DORA);
        }
        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.waiting(2));
        wait(2);

        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.waiting(1));
    }

    // Chrome lets a tab hidden for 5 minutes poll about once a minute: it keeps its place.
    @Test
    void aBackgroundTabPollingOnceAMinuteKeepsItsPlace() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.enter(DORA);

        for (int i = 0; i < 10; i++) {
            wait(61);
            seats.enter(DORA);
            assertThat(seats.enter(CAIO)).isEqualTo(SeatStatus.waiting(1));
        }
    }

    @Test
    void comingBackToTheQueueInTimeKeepsTheSamePlace() {
        fillTheRoom();
        seats.enter(CAIO);
        seats.enter(DORA);
        seats.leave(CAIO);
        wait(20);

        assertThat(seats.enter(CAIO)).isEqualTo(SeatStatus.waiting(1));
        wait(40);
        seats.enter(CAIO);
        assertThat(seats.enter(DORA)).isEqualTo(SeatStatus.waiting(2));
    }

    @Test
    void leavingWhenNotInTheQueueDoesNothing() {
        seats.leave(ANA);
        seats.cancel(ANA);

        assertThat(seats.enter(ANA)).isEqualTo(SeatStatus.in());
    }
}

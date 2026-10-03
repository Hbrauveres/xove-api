package dev.hbrauveres.xove.stream;

import static dev.hbrauveres.xove.stream.StreamKind.CAMERA;
import static dev.hbrauveres.xove.stream.StreamKind.SCREEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hbrauveres.xove.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StreamsTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;
    private static final SharingConnection ANA_SCREEN = new SharingConnection("PA_ana", "TR_ana_screen");
    private static final SharingConnection ANA_CAMERA = new SharingConnection("PA_ana", "TR_ana_camera");

    private MutableClock clock;
    private Streams streams;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-03T20:00:00Z"));
        streams = new Streams(clock);
    }

    private Stream start(Long userId, StreamKind kind) {
        String sid = "PA_" + userId;
        return streams.start(userId, "User " + userId, null, kind,
                new SharingConnection(sid, "TR_" + userId + "_" + kind), StreamSettings.defaultFor(kind));
    }

    // ---- places ----

    @Test
    void startsEmptyWithSixFreePlaces() {
        assertThat(streams.all()).isEmpty();
        assertThat(streams.free()).isEqualTo(6);
    }

    @Test
    void sixStreamsThenFull() {
        for (long user = 1; user <= 3; user++) {
            start(user, SCREEN);
            start(user, CAMERA);
        }

        assertThat(streams.all()).hasSize(6);
        assertThat(streams.free()).isZero();
        assertThatThrownBy(() -> start(4L, SCREEN))
                .isInstanceOf(StreamsFullException.class)
                .hasMessage("The room already has 6 streams.");
        assertThat(streams.all()).hasSize(6);
    }

    @Test
    void screenAndCameraTakeTwoPlaces() {
        streams.start(ANA, "Ana", null, SCREEN, ANA_SCREEN, StreamSettings.defaultFor(SCREEN));
        streams.start(ANA, "Ana", null, CAMERA, ANA_CAMERA, StreamSettings.defaultFor(CAMERA));

        assertThat(streams.all()).extracting(Stream::kind).containsExactly(SCREEN, CAMERA);
        assertThat(streams.free()).isEqualTo(4);
    }

    @Test
    void startingTheSameKindAgainReplacesYourOwnAndKeepsItsStart() {
        Stream first = streams.start(ANA, "Ana", null, SCREEN, ANA_SCREEN, StreamSettings.defaultFor(SCREEN));
        clock.advance(Duration.ofMinutes(2));

        SharingConnection again = new SharingConnection("PA_ana2", "TR_ana_screen2");
        streams.start(ANA, "Ana", null, SCREEN, again, new StreamSettings("720p", "sharp"));

        assertThat(streams.all()).singleElement().satisfies(s -> {
            assertThat(s.connection()).isEqualTo(again);
            assertThat(s.settings()).isEqualTo(new StreamSettings("720p", "sharp"));
            assertThat(s.since()).isEqualTo(first.since());
        });
    }

    @Test
    void replacingYourOwnStreamWorksWhenTheRoomIsFull() {
        for (long user = 1; user <= 3; user++) {
            start(user, SCREEN);
            start(user, CAMERA);
        }

        start(1L, SCREEN);

        assertThat(streams.all()).hasSize(6);
    }

    @Test
    void theListIsInOrderOfStart() {
        start(BRUNO, SCREEN);
        clock.advance(Duration.ofSeconds(5));
        start(ANA, SCREEN);
        clock.advance(Duration.ofSeconds(5));
        start(BRUNO, CAMERA);

        assertThat(streams.all()).extracting(Stream::userId).containsExactly(BRUNO, ANA, BRUNO);
    }

    @Test
    void aStreamNeedsItsConnectionAndTrack() {
        assertThatThrownBy(() -> streams.start(ANA, "Ana", null, SCREEN, new SharingConnection("PA", " "),
                StreamSettings.defaultFor(SCREEN))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> streams.start(ANA, "Ana", null, SCREEN, null, StreamSettings.defaultFor(SCREEN)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- settings ----

    @Test
    void aCameraGoesUpTo720p() {
        assertThat(StreamSettings.defaultFor(CAMERA)).isEqualTo(new StreamSettings("720p", "smooth"));
        assertThat(StreamSettings.defaultFor(SCREEN)).isEqualTo(new StreamSettings("1080p", "smooth"));
        assertThat(new StreamSettings("1080p", "smooth").isValidFor(CAMERA)).isFalse();
        assertThat(new StreamSettings("480p", "sharp").isValidFor(CAMERA)).isTrue();
        assertThat(new StreamSettings("1080p", "sharp").isValidFor(SCREEN)).isTrue();
        assertThat(new StreamSettings("4k", "smooth").isValidFor(SCREEN)).isFalse();
        assertThat(new StreamSettings("720p", "blurry").isValidFor(SCREEN)).isFalse();

        assertThatThrownBy(() -> streams.start(ANA, "Ana", null, CAMERA, ANA_CAMERA, new StreamSettings("1080p", "smooth")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(StreamSettings.invalidFor(CAMERA));
    }

    @Test
    void youCanChangeTheSettingsOfYourOwnStream() {
        start(ANA, SCREEN);

        streams.changeSettings(ANA, SCREEN, new StreamSettings("480p", "sharp"));

        assertThat(streams.all()).singleElement()
                .extracting(Stream::settings).isEqualTo(new StreamSettings("480p", "sharp"));
    }

    @Test
    void youCannotChangeAStreamYouDontHave() {
        start(ANA, SCREEN);

        assertThatThrownBy(() -> streams.changeSettings(BRUNO, SCREEN, StreamSettings.defaultFor(SCREEN)))
                .isInstanceOf(NotYourStreamException.class);
        assertThatThrownBy(() -> streams.changeSettings(ANA, CAMERA, StreamSettings.defaultFor(CAMERA)))
                .isInstanceOf(NotYourStreamException.class);
    }

    @Test
    void aCameraCannotBeChangedTo1080p() {
        start(ANA, CAMERA);

        assertThatThrownBy(() -> streams.changeSettings(ANA, CAMERA, new StreamSettings("1080p", "smooth")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- stopping ----

    @Test
    void youStopOnlyYourOwnStream() {
        start(ANA, SCREEN);
        start(ANA, CAMERA);
        start(BRUNO, SCREEN);

        streams.stop(ANA, CAMERA);

        assertThat(streams.all()).extracting(Stream::userId, Stream::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ANA, SCREEN),
                        org.assertj.core.groups.Tuple.tuple(BRUNO, SCREEN));
    }

    @Test
    void stoppingAStreamYouDontHaveDoesNothing() {
        start(BRUNO, SCREEN);

        streams.stop(ANA, SCREEN);

        assertThat(streams.all()).hasSize(1);
    }

    @Test
    void aTrackStoppingEndsOnlyThatStream() {
        streams.start(ANA, "Ana", null, SCREEN, ANA_SCREEN, StreamSettings.defaultFor(SCREEN));
        streams.start(ANA, "Ana", null, CAMERA, ANA_CAMERA, StreamSettings.defaultFor(CAMERA));

        assertThat(streams.trackUnpublished(ANA, "TR_ana_camera")).isTrue();

        assertThat(streams.all()).extracting(Stream::kind).containsExactly(SCREEN);
    }

    @Test
    void aReportAboutAnotherTrackChangesNothing() {
        streams.start(ANA, "Ana", null, SCREEN, ANA_SCREEN, StreamSettings.defaultFor(SCREEN));

        assertThat(streams.trackUnpublished(ANA, "TR_old")).isFalse();
        assertThat(streams.trackUnpublished(BRUNO, "TR_ana_screen")).isFalse();

        assertThat(streams.all()).hasSize(1);
    }

    @Test
    void theConnectionLeavingEndsAllOfThatPersonsStreams() {
        streams.start(ANA, "Ana", null, SCREEN, ANA_SCREEN, StreamSettings.defaultFor(SCREEN));
        streams.start(ANA, "Ana", null, CAMERA, ANA_CAMERA, StreamSettings.defaultFor(CAMERA));
        start(BRUNO, SCREEN);

        assertThat(streams.connectionLeft(ANA, "PA_ana")).isTrue();

        assertThat(streams.all()).extracting(Stream::userId).containsExactly(BRUNO);
    }

    @Test
    void anOldConnectionLeavingDoesNotEndTheNewOne() {
        streams.start(ANA, "Ana", null, SCREEN, ANA_SCREEN, StreamSettings.defaultFor(SCREEN));

        assertThat(streams.connectionLeft(ANA, "PA_old_tab")).isFalse();

        assertThat(streams.all()).hasSize(1);
    }
}

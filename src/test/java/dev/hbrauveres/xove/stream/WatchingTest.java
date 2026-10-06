package dev.hbrauveres.xove.stream;

import static dev.hbrauveres.xove.stream.StreamKind.CAMERA;
import static dev.hbrauveres.xove.stream.StreamKind.SCREEN;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hbrauveres.xove.MutableClock;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Who has which stream on their stage (spec 0104). */
class WatchingTest {

    private static final Long ANA = 1L;
    private static final Long BRUNO = 2L;
    private static final Long CAIO = 3L;

    private Streams streams;
    private Watching watching;
    /** Everyone in these tests is seated, unless a test says otherwise. */
    private Set<Long> seated;

    @BeforeEach
    void setUp() {
        streams = new Streams(new MutableClock(Instant.parse("2026-10-06T20:00:00Z")));
        watching = new Watching();
        seated = Set.of(ANA, BRUNO, CAIO);
    }

    private void live(Long userId, StreamKind kind) {
        streams.start(userId, "User " + userId, null, kind,
                new SharingConnection("PA_" + userId, "TR_" + userId + kind), StreamSettings.defaultFor(kind));
    }

    @Test
    void startsEmpty() {
        assertThat(watching.current(seated::contains, streams)).isEmpty();
    }

    @Test
    void keepsWhatEachPersonWatches() {
        live(BRUNO, SCREEN);
        live(CAIO, CAMERA);

        watching.watch(ANA, BRUNO, SCREEN);
        watching.watch(BRUNO, CAIO, CAMERA);

        assertThat(watching.current(seated::contains, streams)).containsExactly(
                new Watching.Watch(ANA, BRUNO, SCREEN),
                new Watching.Watch(BRUNO, CAIO, CAMERA));
    }

    @Test
    void aNewPickReplacesTheOldOne() {
        live(BRUNO, SCREEN);
        live(CAIO, SCREEN);
        watching.watch(ANA, BRUNO, SCREEN);

        watching.watch(ANA, CAIO, SCREEN);

        assertThat(watching.current(seated::contains, streams))
                .containsExactly(new Watching.Watch(ANA, CAIO, SCREEN));
    }

    @Test
    void anEmptyStageClearsIt() {
        live(BRUNO, SCREEN);
        watching.watch(ANA, BRUNO, SCREEN);

        watching.clear(ANA);

        assertThat(watching.current(seated::contains, streams)).isEmpty();
    }

    @Test
    void onlyLiveStreamsCount() {
        live(BRUNO, SCREEN);
        watching.watch(ANA, BRUNO, SCREEN);
        watching.watch(CAIO, BRUNO, CAMERA);

        assertThat(watching.current(seated::contains, streams))
                .containsExactly(new Watching.Watch(ANA, BRUNO, SCREEN));

        streams.stop(BRUNO, SCREEN);
        assertThat(watching.current(seated::contains, streams)).isEmpty();
    }

    @Test
    void onlySeatedPeopleCount() {
        live(BRUNO, SCREEN);
        watching.watch(ANA, BRUNO, SCREEN);

        assertThat(watching.current(Set.of(BRUNO)::contains, streams)).isEmpty();
    }
}

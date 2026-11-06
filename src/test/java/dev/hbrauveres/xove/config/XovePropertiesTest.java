package dev.hbrauveres.xove.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** The room size, from XOVE_ROOM_SEATS (spec 0060), and its stream places, from XOVE_STREAM_PLACES (spec 0191). */
class XovePropertiesTest {

    private static XoveProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("xove", XoveProperties.class);
    }

    @Test
    void withoutTheSettingTheRoomHas20Seats() {
        assertThat(bind(Map.of()).room().seats()).isEqualTo(20);
    }

    @Test
    void theSettingSetsTheSeats() {
        assertThat(bind(Map.of("xove.room.seats", "4")).room().seats()).isEqualTo(4);
        assertThat(bind(Map.of("xove.room.seats", "1")).room().seats()).isEqualTo(1);
        assertThat(bind(Map.of("xove.room.seats", "20")).room().seats()).isEqualTo(20);
    }

    @Test
    void anythingButAWholeNumberFrom1To20StopsTheApi() {
        for (String wrong : new String[] {"0", "21", "-3"}) {
            assertThatThrownBy(() -> bind(Map.of("xove.room.seats", wrong)))
                    .as(wrong)
                    .isInstanceOf(BindException.class)
                    .rootCause().hasMessage("XOVE_ROOM_SEATS must be a whole number from 1 to 20, not " + wrong);
        }
        // Spring's own start-up report names the property and the value.
        for (String wrong : new String[] {"abc", "2.5"}) {
            assertThatThrownBy(() -> bind(Map.of("xove.room.seats", wrong)))
                    .as(wrong)
                    .isInstanceOf(BindException.class)
                    .hasMessageContaining("xove.room.seats");
        }
    }

    @Test
    void withoutTheSettingSixStreamsCanBeLive() {
        assertThat(bind(Map.of()).room().streamPlaces()).isEqualTo(6);
    }

    @Test
    void theSettingSetsTheStreamPlaces() {
        assertThat(bind(Map.of("xove.room.stream-places", "4")).room().streamPlaces()).isEqualTo(4);
        assertThat(bind(Map.of("xove.room.stream-places", "1")).room().streamPlaces()).isEqualTo(1);
    }

    @Test
    void streamPlacesOutside1To6StopTheApi() {
        for (String wrong : new String[] {"0", "7", "-1"}) {
            assertThatThrownBy(() -> bind(Map.of("xove.room.stream-places", wrong)))
                    .as(wrong)
                    .isInstanceOf(BindException.class)
                    .rootCause().hasMessage("XOVE_STREAM_PLACES must be a whole number from 1 to 6, not " + wrong);
        }
    }
}
